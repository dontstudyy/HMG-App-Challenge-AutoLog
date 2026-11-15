# 오토로그 (AutoLog)

> **운행이 끝나면 운행기록부도 끝나 있게.** 기어가 P를 벗어나고 돌아오는 순간의 **계기판 누적거리**로 업무 운행 한 건을 자동으로 만들고, 앱이 꺼져 있던 구간까지 찾아내 국세청 「업무용승용차 운행기록부」 서식으로 내보내는 차량용 앱입니다.

- 대상 환경: Pleos Connect 에뮬레이터 (Android / Kotlin / Pleos Connect SDK)
- 제출 APK: [`apk/정통윤_AutoLog_release.apk`](apk/%EC%A0%95%ED%86%B5%EC%9C%A4_AutoLog_release.apk) (release 서명, 약 25MB)

## 무엇을 하나

| 기능 | 동작 |
|---|---|
| 자동 기록 | 기어 P → 주행(D·R·N) → P 사이의 계기판 누적거리 차이로 운행 1건을 저장. 0.1km 미만은 버림. 포그라운드 서비스라 앱 화면이 없어도 기록 |
| 빈 구간 찾기 | 앱이 꺼져 있던 동안 달린 거리를 마지막 기록과 지금 계기판의 차이로 찾아 '빈 구간' 카드로 알려 줌 (`지금 정리` / `나중에 정리`) |
| 자동 분류 | 집·회사·거래처(반경 300m)로 출퇴근·일반업무를 자동 분류. 확실하지 않을 때만 주차 후 질문(버튼, 음성은 보조) |
| 사용자 | 같은 차를 여러 명이 써도 정차 중 프로필로 사람별 기록 |
| 리포트 | 월간 업무사용비율, 총 주행거리, 예상 유류비(입력 연비·단가 기준), 예상 통행료(길안내 사용 시), 미분류 정리 |
| 내보내기 | 서식 칸(③~⑬) 그대로 엑셀(xlsx)·PDF 저장과 미리보기. 미분류가 남아 있으면 먼저 경고 |
| 음성 명령(Gleo) | `LOG_TRIP_PURPOSE`, `START_PRIVATE_TRIP`, `GET_BUSINESS_RATIO` (`assets/schema.json`) |

핵심 로직(`trip` 엔진, 분류, 월간 집계, 엑셀 생성)은 Android에 의존하지 않는 순수 Kotlin이고 단위 테스트 48건이 있습니다.

## 테스트 환경과 필요한 설정

- Pleos Connect 에뮬레이터 (AVD `Pleos_Connect_v2.1.0`, Cold boot, RAM 4GB, 2560×1440). 앱은 가로 화면 전용 레이아웃입니다.
- PC에서 Virtual Console을 쓰려면 `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/` (또는 시연 스크립트가 하트비트를 대신 보냄).
- 권한은 설치 때 모두 허용합니다: `adb install -g apk/정통윤_AutoLog_release.apk`. (차량·위치·내비 권한과 알림, 마이크)
- 이 앱은 차량 정보를 차에서 읽을 수 없는 항목을 **설정에서 입력**받습니다: 차종, 차량번호, 상호명, 사업자등록번호, 연비(100km당 L), 연료 단가. 기본값은 `업무용 승용차`, `12가 3456`, 연비 7.1, 단가 1,650입니다.
- 출퇴근·거래처 자동 분류를 쓰려면 설정에서 장소를 등록합니다(`현재 위치를 집/회사/거래처로`).

## 실행 방법

1. 에뮬레이터를 켜고 APK를 설치합니다: `adb install -g apk/정통윤_AutoLog_release.apk` 후 앱 `오토로그` 실행.
2. 설정에서 장소를 등록합니다. 시연용 가상 좌표는 `adb emu geo fix <경도> <위도>`로 옮깁니다(집 `126.98 37.55`, 회사 `127.01 37.57`, 거래처 `127.05 37.53`).
3. Virtual Console에서 기어(P/D)와 주행거리를 바꾸면 앱이 운행을 기록합니다. 한 번에 보려면 시연 스크립트를 씁니다.

### 시연 스크립트

`tools/demo.py`가 목업 영상의 운행 6건(업무사용비율 82.1% → 85.8%)을 Virtual Console과 adb로 재현합니다.
**에뮬레이터의 현재 주행거리가 얼마든** 그 값에서 시작해 그대로 실행됩니다(숫자가 모두 현재 값만큼 옮겨질 뿐이라 비율은 같습니다. 예: 현재 7,000km → 7,000 → 7,155km).

```
python tools/demo.py                       # dry-run: 계획만 출력(아무것도 바꾸지 않음)
python tools/demo.py --execute             # 현재 누적거리에서 시작해 실행
python tools/demo.py --execute --pause     # 녹화용: 단계마다 Enter로 넘김
python tools/demo.py --execute --scale 0.01   # 거리를 줄인 스모크 테스트
python tools/demo.py --execute --start 12321 --allow-large-odometer-increase   # 목업의 12,321km 숫자에 맞추기
```

- `--start`로 목업의 12,321km에 맞추려면 현재 값이 12,321 이하여야 합니다(주행거리는 올릴 수만 있어서, 더 크면 `--start`는 무시하고 현재 값에서 시작합니다).
- 숫자는 `tools/trips.json`에서 읽고, 실행 전에 합계·비율·연속성을 다시 검증합니다. 스크립트 단위 테스트: `python -m unittest discover -s tools`.
- 프로필 전환(박과장·김대리)과 월간 리포트·내보내기는 앱 화면에서 사람이 합니다. 길안내 통행료는 내비 앱에서 직접 시작합니다(debug 빌드에서는 `--navi`).

## 더 보기

- [소스에서 빌드](docs/BUILD.md)
- [알려진 한계](docs/KNOWN_LIMITATIONS.md)
- [검증 기록](docs/VERIFICATION.md): 에뮬레이터 실험 결과와 주행거리를 올린 양
