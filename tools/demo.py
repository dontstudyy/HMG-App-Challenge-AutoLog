#!/usr/bin/env python3
"""오토로그 시연 스크립트: 목업 영상의 운행 6건을 Virtual Console(REST)과 adb로 에뮬레이터에서 재현한다.

사용 전 준비 (한 번)
  1. 에뮬레이터를 Cold boot로 켜고 `adb forward tcp:8088 tcp:8088`.
  2. 오토로그를 설치하고 한 번 실행해 권한을 허용한다.
  3. 앱 설정 화면에서 아래 좌표로 장소를 등록한다(좌표는 `adb emu geo fix`로 옮겨 가며 '현재 위치를 ...로' 버튼):
       집      126.98, 37.55    회사   127.01, 37.57    거래처  127.05, 37.53
  4. 박과장 프로필을 설정에서 추가한다(운전자 칩으로 사람을 바꾸는 것은 앱 화면에서만 된다).

실행 (명령은 2개뿐)
  python tools/demo.py plan      # 계획만 출력(아무것도 바꾸지 않음)
  python tools/demo.py execute   # 현재 누적거리에서 시작해 실제로 실행

시작 누적거리
  항상 현재 값에서 시작한다. 모든 숫자가 현재 값만큼 옮겨질 뿐이라 업무사용비율 82.1% → 85.8%는 같다
  (현재 7,000km라면 7,000 → 7,155km로 기록된다).

주의
  - 주행거리를 올리는 것은 되돌릴 수 없고 같은 AVD를 쓰는 다른 앱에도 보인다. 그래서 execute를 명시해야만 실행하고, 실행 전에 총 증가량을 출력한다.
  - 날짜·거리를 덮어쓰는 기능은 앱에 없다. 모든 값은 Virtual Console과 adb로만 넣는다.
  - 숫자는 tools/trips.json(목업 영상의 6건)에서 읽는다.
"""
import argparse
import json
import math
import os
import shutil
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PKG = "com.dontstudyy.autolog"

# 시연용 가상 좌표 (경도, 위도). 실존 장소가 아니다.
PLACES = {
    "집": (126.98, 37.55),
    "회사": (127.01, 37.57),
    "거래처": (127.05, 37.53),
    "고객사": (127.20, 37.45),      # 미등록 위치
    "개인 용무 장소": (127.40, 37.30),  # 미등록 위치
}

# 운행 계획: trips.json의 no 순서와 같다. (출발, 도착, 사람 바꿈, 도착 뒤 Gleo 답, 설명)
PLAN = {
    1: ("집", "회사", None, None, "출근 — 장소로 자동 분류(질문 없음)"),
    2: ("회사", "거래처", None, ("LOG_TRIP_PURPOSE", "거래처 미팅"), "거래처 방문 — 일반업무 자동, 목적은 음성 명령으로 기록"),
    3: ("거래처", "고객사", "박과장", ("LOG_TRIP_PURPOSE", "고객사 회의"), "프로필 박과장 — 출발이 거래처라 일반업무 자동, 목적은 음성 명령으로 기록"),
    4: ("고객사", "집", "김대리", ("LOG_TRIP_PURPOSE", "퇴근"), "프로필 김대리 — 출발이 미등록이라 질문 → 퇴근"),
    5: ("집", "개인 용무 장소", None, ("LOG_TRIP_PURPOSE", "개인 용무"), "개인 용무"),
}


DEFAULT_START = 12321.0  # 연결이 없을 때 plan이 보여 주는 목업 영상의 시작 누적거리(km)
DRIVE_SECONDS = 6.0      # 운행 하나를 달리는 데 쓰는 시간(초)
HOST = "127.0.0.1:8088"  # Virtual Console 주소(adb forward 필요)


def decide_start(cur):
    """시작 누적거리를 정한다. 항상 현재 값에서 시작한다. 반환: (시작값, 안내 문구).
    현재 값을 모르면(연결 없음) 목업 값으로 계획만 보여 준다."""
    if cur is None:
        return DEFAULT_START, f"에뮬레이터에 연결되지 않아 목업 값 {DEFAULT_START:,.0f}km로 계획만 보여 줘요(execute하면 현재 값에서 시작)"
    return cur, f"현재 누적거리 {cur:,.1f}km에서 시작해요(숫자가 모두 그만큼 옮겨져요)"


def load_trips():
    with open(os.path.join(HERE, "trips.json"), encoding="utf-8") as f:
        return json.load(f)


def verify_plan(data):
    """trips.json의 합계·비율·연속성을 계획 단계에서 다시 확인한다(verify.py와 같은 계산)."""
    tr, ex = data["trips"], data["expect"]
    ok = True
    for i, r in enumerate(tr):
        ok &= r["after"] - r["before"] == r["dist"]
        if i:
            ok &= r["before"] == tr[i - 1]["after"]
    commute = sum(r["dist"] for r in tr if r["cls"] == "출퇴근")
    biz = sum(r["dist"] for r in tr if r["cls"] == "일반업무")
    unc = sum(r["dist"] for r in tr if r["cls"] == "미분류")
    total_before = sum(r["dist"] for r in tr) - unc
    ok &= (total_before, commute + biz) == (ex["beforeTotal"], ex["beforeBiz"])
    ok &= round((commute + biz) / total_before * 100, 1) == ex["beforeRatio"]
    total_after = sum(r["dist"] for r in tr)
    biz_after = commute + biz + tr[-1]["dist"]
    ok &= (total_after, biz_after) == (ex["afterTotal"], ex["afterBiz"])
    ok &= round(biz_after / total_after * 100, 1) == ex["afterRatio"]
    return ok


class Console:
    """Virtual Console REST. 웹페이지 대신 1초마다 하트비트를 보내면 제어된다(없으면 503)."""

    def __init__(self, host):
        self.base = "http://" + host
        self._stop = threading.Event()
        threading.Thread(target=self._beat, daemon=True).start()

    def _beat(self):
        while not self._stop.is_set():
            try:
                req = urllib.request.Request(self.base + "/browser/session?client=autolog-demo", method="POST")
                urllib.request.urlopen(req, timeout=2).read()
            except Exception:
                pass
            self._stop.wait(1.0)

    def close(self):
        self._stop.set()

    def put(self, path, **params):
        url = self.base + path + "?" + urllib.parse.urlencode(params)
        urllib.request.urlopen(urllib.request.Request(url, method="PUT"), timeout=5).read()

    def state(self):
        with urllib.request.urlopen(self.base + "/vehicle/state", timeout=5) as r:
            return json.load(r)

    def odometer(self):
        return float(self.state()["odometer"]["traveled_distance_km"])

    @staticmethod
    def read_odometer(host):
        """하트비트 없이 현재 누적거리만 읽는다(plan에서도 쓴다)."""
        with urllib.request.urlopen("http://" + host + "/vehicle/state", timeout=3) as r:
            return float(json.load(r)["odometer"]["traveled_distance_km"])

    def gear(self, g):
        self.put("/vehicle/gear", gear=g)

    def set_odometer(self, km):
        self.put("/vehicle/odometer", traveled_distance_km=f"{km:.1f}")


class Adb:
    def __init__(self, serial=None):
        exe = shutil.which("adb")
        if not exe:
            sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or r"C:\pleosEnv\Android\Sdk"
            exe = os.path.join(sdk, "platform-tools", "adb.exe" if os.name == "nt" else "adb")
        self.cmd = [exe] + (["-s", serial] if serial else [])

    def run(self, *args, check=True):
        return subprocess.run(self.cmd + list(args), capture_output=True, text=True, encoding="utf-8", check=check).stdout

    def geo(self, place):
        lon, lat = PLACES[place]
        self.run("emu", "geo", "fix", str(lon), str(lat))

    def force_stop(self):
        self.run("shell", "am", "force-stop", PKG)

    def launch_and_wait(self, timeout=90):
        self.run("logcat", "-c")
        self.run("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
        t0 = time.time()
        while time.time() - t0 < timeout:
            if "READY" in self.run("logcat", "-d", "-s", "AutoLogRT:I", check=False):
                return True
            time.sleep(1.5)
        return False

    def gleo(self, action, purpose=None):
        cmd = f"am start -a {action}"
        if purpose:
            cmd += f" --es purpose '{purpose}'"
        self.run("shell", cmd)


def main():
    ap = argparse.ArgumentParser(description="오토로그 시연 스크립트: plan(계획 출력) / execute(실행)")
    ap.add_argument("mode", choices=["plan", "execute"], help="plan: 계획만 출력, execute: 에뮬레이터를 실제로 조작")
    args = ap.parse_args()
    execute = args.mode == "execute"

    data = load_trips()
    trips = data["trips"]
    if not verify_plan(data):
        sys.exit("trips.json 검증 실패(합계·비율·연속성)")

    console = Console(HOST) if execute else None
    adb = Adb() if execute else None

    # ---- 시작값 ----
    cur = None
    try:
        cur = Console.read_odometer(HOST)  # 읽기만 한다(하트비트·조작 없음)
    except Exception as e:
        if execute:
            sys.exit(f"Virtual Console에 연결할 수 없어요({e}). adb forward tcp:8088 tcp:8088 을 확인하세요.")
    start, start_note = decide_start(cur)
    print(start_note)

    legs = []  # (번호, 목표 누적거리, 거리)
    pos = start
    for r in trips[:5]:
        d = r["dist"]
        legs.append((r["no"], pos + d, d))
        pos += d
    gap = trips[5]["dist"]
    total_increase = (pos + gap) - (cur if cur is not None else start)
    print(f"시작 누적거리 {start:,.1f}km, 운행 {len(legs)}건 + 빈 구간 {gap:.1f}km → 끝 {pos + gap:,.1f}km (총 +{total_increase:,.1f}km)")
    print(f"계획 검증: trips.json 합계·비율·연속성 OK (정리 전 {data['expect']['beforeRatio']}% → 정리 후 {data['expect']['afterRatio']}%)")
    for no, target, d in legs:
        a, b, who, ans, note = PLAN[no]
        print(f"  {no}번 {a} → {b}  +{d:.1f}km → {target:,.1f}  {('[사람: ' + who + ']') if who else ''} {note}")
    print(f"  6번 앱 강제 종료 → +{gap:.1f}km → {pos + gap:,.1f} → 앱 실행 → 빈 구간 카드")
    print("  7번 (사람) 리포트 → 미분류를 일반업무로 → 내보내기")
    if not execute:
        print("\nplan입니다. 실제로 조작하려면 `python tools/demo.py execute`를 실행하세요.")
        return

    print(f"\n주행거리를 총 +{total_increase:,.1f}km 올립니다(되돌릴 수 없어요).")

    def wait(msg, human=False):
        if human:  # 사람이 화면에서 직접 해야 하는 단계만 Enter를 기다린다
            input(f"\n▶ {msg}  (Enter)")
        else:
            print(f"\n▶ {msg}")

    def drive(target):
        now = console.odometer()
        step = 3.0
        n = max(3, math.ceil((target - now) / step))
        for i in range(1, n + 1):
            console.set_odometer(now + (target - now) * i / n)
            time.sleep(DRIVE_SECONDS / n)

    try:
        # ---- 준비 ----
        wait("준비: 앱을 끄고 기어 P, 시작 누적거리, 위치 = 집")
        adb.force_stop()
        console.gear("Park")
        console.set_odometer(start)
        adb.geo("집")
        time.sleep(2)
        if not adb.launch_and_wait():
            sys.exit("앱이 시작되지 않았어요(AutoLogRT READY 로그 없음).")
        time.sleep(3)

        for no, target, d in legs:
            a, b, who, ans, note = PLAN[no]
            if who:
                print(f"\n※ 사람 조작 필요: 앱 화면 상단 사용자 칩을 눌러 '{who}'(으)로 바꾸세요(정차 중에만 가능).")
                wait(f"{who} 선택을 마쳤으면", human=True)
            wait(f"{no}번: {a} → {b}  ({note})")
            adb.geo(a)
            time.sleep(3)
            if no == 2:
                print("   (길안내를 쓰려면 내비 앱에서 '거래처'를 목적지로 지금 시작하세요. 통행료는 내비가 알려 주는 값이 붙어요.)")
            console.gear("Drive")
            time.sleep(1.5)
            drive(target)
            adb.geo(b)
            time.sleep(3)
            console.gear("Park")
            time.sleep(4)
            if ans:
                wait(f"{no}번 답: {ans[1]}")
                adb.gleo(*ans)
                time.sleep(3)

        # ---- 6번: 앱이 꺼진 사이 ----
        wait("6번: 앱 강제 종료 → 앱이 꺼진 채로 주행 → 앱 실행")
        adb.force_stop()
        time.sleep(2)
        console.gear("Drive")
        time.sleep(1)
        drive(pos + gap)
        console.gear("Park")
        time.sleep(2)
        if not adb.launch_and_wait():
            sys.exit("앱이 다시 시작되지 않았어요.")
        print("\n빈 구간 카드가 떴어요. 이제 '나중에 정리'/'지금 정리' → 월간 리포트 → 내보내기를 화면에서 진행하세요.")
    finally:
        console.close()


if __name__ == "__main__":
    main()
