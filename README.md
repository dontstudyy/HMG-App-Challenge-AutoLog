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

`tools/demo.py`가 목업 영상의 운행 6건(12,321 → 12,476km, 업무사용비율 82.1% → 85.8%)을 Virtual Console과 adb로 재현합니다.

```
python tools/demo.py                                         # dry-run: 계획만 출력
python tools/demo.py --execute --allow-large-odometer-increase   # 실제 실행(새 AVD 권장)
python tools/demo.py --execute --start current --scale 0.01      # 현재 값에서 시작하는 축소 스모크 테스트
```

- 에뮬레이터 주행거리는 **줄일 수 없습니다.** 현재 값이 시작값보다 크면 스크립트가 실행을 거부합니다. 새 AVD에서 12,321km까지 한 번 올리고 실행하는 것을 권장합니다.
- 숫자는 `tools/trips.json`에서 읽고, 실행 전에 합계·비율·연속성을 다시 검증합니다.
- 프로필 전환(박과장·김대리)과 월간 리포트·내보내기는 앱 화면에서 사람이 합니다. 길안내 통행료는 내비 앱에서 직접 시작합니다(debug 빌드에서는 `--navi`).

## 소스에서 빌드

```
# local.properties에 sdk.dir 지정 후
gradlew.bat testDebugUnitTest assembleDebug     # 인터넷 필요(Pleos 라이브러리 저장소)
gradlew.bat assembleRelease                     # keystore.properties가 없으면 debug 키로 서명
```

서명 키스토어와 `keystore.properties`는 저장소에 없습니다(저장소 밖 보관). 제출용 APK는 키가 있는 PC에서 빌드했습니다. release는 R8 최적화를 켜되 `kotlin.**` 보존 규칙이 필요합니다(없으면 시작 시 `IllegalAccessError`로 종료됨 — 검증 기록 참고).

## 알려진 한계

- 기어가 D인 동안 Pleos OS가 앱 화면을 가립니다("주행 중에는 사용할 수 없습니다"). 앱 서비스는 화면이 가려져도 계속 기록하고, 질문·정리 카드는 주차한 뒤에만 뜹니다.
- 출발·도착 위치는 등록 장소 이름만 씁니다(내비 현재 위치가 에뮬레이터에서 고정이라 주소 변환은 제외).
- 좌석 위치로 사용자를 추천하는 기능과 주차 브레이크 표시는 에뮬레이터에서 값을 바꿀 수 없어 넣지 않았습니다.
- TTS는 앱을 새로 설치한 뒤 첫 호출만 재생 이벤트가 확인됩니다. 질문은 항상 화면 카드로 뜹니다.
- 운행기록부 칸 구성은 제3자가 올린 서식 사본 기준이라 국세청 원본과 대조가 필요합니다.

## 검증 기록

에뮬레이터: Pleos_Connect_v2.1.0 (Cold boot, RAM 4GB). 측정일 2026-11-13. 이 AVD는 재부팅 직후라 주행거리 0.0km에서 시작했다.

### 단계 0 ① 주행거리 주소·리스너 간격 — 통과

- Virtual Console REST: `PUT /vehicle/odometer?traveled_distance_km=0.1` (200 응답). 소수점이 유지된다. 값은 `GET /vehicle/state`의 `odometer.traveled_distance_km`에서 읽는다.
- Virtual Console 웹페이지 없이도 `POST /browser/session?client=ID`를 1초마다 보내면 제어된다(없으면 503).
- 앱 쪽: Pleos `getOdometer().registerDistanceDriven` 리스너와 `getCurrentDistanceDriven`이 모두 동작한다.
- 리스너 간격: 값이 바뀌지 않아도 약 4초마다 현재 값이 온다. 값이 바뀌면 PUT 후 0.2초~1.0초 안에 온다(측정 3회: 0.22초, 약 1.0초, 약 1.0초).
- AAOS `PERF_ODOMETER`(0x11600204) 직접 읽기는 `android.car.permission.CAR_MILEAGE`가 필요해 거부됐다(`SecurityException`). 사용하지 않는다. Pleos 경로가 거리의 유일한 근거다.

### 단계 0 ② 기어 이벤트와 누적거리 읽기 순서 — 통과 (조건 있음)

- 기어는 AAOS `GEAR_SELECTION`(0x11400400) 콜백으로 받는다(P=4, D=8). PUT 후 약 0.19초에 이벤트가 온다.
- 기어 이벤트 직후 `getCurrentDistanceDriven` 호출은 14~41ms 안에 응답한다.
- 주행거리 갱신이 기어 P보다 **먼저** 도착하면(PUT 순서: 주행거리 → 기어) 기어 이벤트 시점의 읽기가 최신 값이다.
- 주행거리 갱신이 기어 P보다 **나중**에 도착하면(PUT 순서: 기어 → 주행거리) 기어 이벤트 시점의 읽기는 직전 값이다. 갱신은 약 1초 뒤에 온다.
- 결론: 기어 P 이벤트 순간의 읽기만으로는 마지막 값을 놓칠 수 있다. 'P가 된 뒤 주행거리 이벤트가 더 오는지 잠시(1.5초 정도) 기다린 뒤 마지막 값을 주행 후로 저장'하는 규칙이 필요하다.

### 단계 0 ⑨ 서비스가 강제 종료된 뒤 — 확인 (재실행되지 않음)

- `adb shell am force-stop` 뒤 프로세스와 서비스가 모두 사라지고, 8초 뒤에도 자동으로 다시 뜨지 않았다(`START_STICKY`도 force-stop 뒤에는 재시작 안 됨).
- 앱을 다시 실행하면 `onServiceStart`가 마지막 기록의 '주행 후'와 지금 누적거리의 차이를 빈 구간으로 만든다(시연 6번 동작 확인).

### 단계 1·2 에뮬레이터 확인

- 값이 앱에 반영되는 지연(PUT → 앱 상태 갱신 로그): 기어 D 0.29초, 기어 P 0.20초, 주행거리 0.24초. 모두 1초 이내.
- **기어가 D인 동안 OS가 앱 화면을 가린다.** 플랫폼의 주행 화면(`ai.umos.drivingview`)이 앱 자리를 덮어 "주행 중에는 사용할 수 없습니다"(또는 내비)가 보인다. `distractionOptimized` 메타데이터를 넣어도 같았다(되돌림). 그래서 주행 중 '기록 중' 화면은 이 환경에서 보이지 않는다. 포그라운드 서비스는 화면이 가려져도 계속 기록한다(운행 기록 확인).
- 앱 프로세스가 처음 뜨는 데 약 9초 걸린다(첫 실행 `Displayed +9s487ms`). 그동안 기어·주행거리를 바꾸면 앱은 시작할 때의 값(기어 D, 누적거리 N)에서 빈 구간 + 새 운행으로 처리한다.
- 운행기록부 ⑤⑥은 정수 km로 버림해서 0.3km 운행이 1로 보일 수 있다(2.7→3.0은 2→3, ⑦=1). 설계서 6장 규칙이다.

### 단계 0 ③ 주차 브레이크 — 읽기는 되지만 쓸 수 없음 (칸 제외)

- AAOS `PARKING_BRAKE_ON`(0x11200402)은 추가 권한 없이 읽기·구독이 된다(`false`, 구독 시 초기 이벤트 1회).
- Virtual Console의 `ParkingBrake` 텔테일을 On으로 바꿔도(`PUT /vehicle/telltales/simulator?type=ParkingBrake&state=On&color=Red`, 200) 앱이 읽는 값은 `false`에서 변하지 않았다. 시연에서 보여 줄 수 없다.
- 결정(설계서 3장): '차가 아는 숫자' 패널에서 주차 브레이크 칸을 뺀다. 주차 판정은 기어 P만 쓴다.

### 단계 0 ④ 좌석 위치·등받이 각도 — 읽기 O, Virtual Console로 바꿀 수 없음 (마지막 사용자 추천)

- Pleos `getCurrentSeatForeAftPosition`·`BackrestAnglePosition`·`HeightPosition`·`TiltPosition`이 모두 읽히고(0~100, `READ_WRITE`) `register…Position` 리스너가 동작한다. 좌석 area는 ROW_1_LEFT/RIGHT, ROW_2_LEFT/RIGHT.
- Virtual Console은 `PUT /vehicle/seat?kind=position&control=slide&area=driver&value=40`(또는 `kind=move`)을 200으로 받고 화면 값도 바뀌지만, 차량 쪽 로그가 `Recline is 0.3, revised position: 0`으로 **0으로 되돌린다.** 앱이 읽는 값은 항상 0이다.
- 결정(설계서 2장 B5): 좌석 위치로는 사용자를 추천할 수 없으므로 '마지막 사용자 추천'으로 간다.
- 참고: 착석(`getCurrentSeatOccupancy`)은 에뮬레이터에서 VC 값과 일치하지 않았다(VC 둘 다 false일 때 ROW_1_LEFT=false, ROW_1_RIGHT=true). 사용하지 않는다.

### 단계 0 ⑤ FusedLocation

- `isSupported()=true`. `adb emu geo fix <경도> <위도>`가 콜백에 반영된다(콜백은 약 1.2초 간격). 새 `geo fix`를 보내지 않아도 마지막 위치가 계속 반복된다. `confidence`는 항상 5.0, `heading` 0.0.
- **포그라운드 서비스(또는 화면)가 있을 때만 계속 온다.** 브로드캐스트로만 뜬 백그라운드 프로세스는 5건(약 10초) 뒤 끊겼다. 서비스가 있으면 60초 이상 유지됐다.
- 수신 중단은 `adb shell cmd location set-location-enabled false --user 10`으로 만들 수 있다(8초간 이벤트 0건, `onError` 없음). `true`로 되돌리면 재등록 없이 다시 온다. 지하주차장 '위치 끊김' 장면은 이 명령으로 재현할 수 있고, 앱은 '마지막 수신 시각이 일정 시간(기본 10초) 넘게 갱신 없음'으로 끊김을 판정한다.
- `addProximityAlert`(ProximityAlertRequest(위도, 경도, 반경m, {ENTER, EXIT}))는 동작한다. A에서 6km 밖으로 가면 EXIT, 돌아오면 ENTER, 반경 300m 경계에서도 발화(약 344m 밖 EXIT, 약 245m 안 ENTER). 이벤트는 위치 갱신 후 수 초 안에 온다.
- 결정: 장소 판정은 설계서대로 마지막 위치와 등록 장소의 거리를 직접 계산한다(단순하고 단위 테스트가 쉬움). `addProximityAlert`는 쓰지 않는다.
- 위치 실험 후 에뮬레이터 위치는 `126.98, 37.55`(서울)로 남아 있다.

### 단계 4 에뮬레이터 확인 (분류와 질문)

설정 화면에서 현재 위치를 집(126.98, 37.55)·회사(127.01, 37.57)·거래처(127.05, 37.53)로 등록하고 `adb emu geo fix`로 이동하며 확인했다(운행마다 0.3km).

- 집 → 회사: 질문 없이 `출퇴근 · 출근`으로 자동 분류, 위치 상태 `정상 · 회사`.
- 회사 → 거래처: `일반업무 · 거래처`로 자동 분류(도착이 거래처). 거래처 → 미등록 위치도 출발이 거래처라 자동 분류된다(규칙 2).
- 미등록 → 미등록: 주차하면 질문 카드 "0.3킬로미터 운행했어요. 업무 목적인가요?"(미등록 위치 → 미등록 위치). `일반업무` → 메모 칩(거래처 미팅 등) → `거래처 미팅`이 비고에 기록, 이어서 "이 위치를 거래처로 저장할까요?". `저장`(이름 `거래처 2`) 뒤 같은 위치에 도착하면 `일반업무 · 거래처 2`로 자동 분류.
- `개인 용무`로 답하면 비업무로 기록된다.
- 프로필: 설정에서 추가, 정차 중 상단 사용자 칩을 눌러 선택. 선택한 사용자(`Park`)가 이어서 기록되고 기록부 ④ 사용자 칸에 표시된다(좌석 위치로 추천할 수 없어 '마지막 사용자 유지').
- 위치 끊김: `adb shell cmd location set-location-enabled false --user 10` 뒤 12초에 위치 상태가 `끊김 · 마지막 위치 거래처 2`(주황)로 바뀌고, 켜면 곧 `정상`으로 돌아온다.
- 확인하지 못한 것: 위치가 끊긴 상태에서 도착하는 운행의 분류(마지막 위치로 판정하는 코드는 있음), 회사 → 집(퇴근)은 단위 테스트로만 확인했다.
- 화면 키보드(한글 포함)가 뜨고, `adb shell input text`는 영문만 되며 글자가 빠질 수 있다.
- DB 스키마를 바꾸면 기록을 지우고 새로 만든다(`fallbackToDestructiveMigration`). 제출 전에 확정한다.

### 단계 5 에뮬레이터 확인 (음성·내비·내보내기)

운행마다 0.3km로 올려 확인했다(1.8 → 3.0km).

- **Gleo(adb intent)** — 세 명령 모두 동작. 공백이 든 값은 기기 쉘 안에서 따옴표를 써야 한다.
  - `adb shell "am start -a LOG_TRIP_PURPOSE --es purpose '거래처 미팅'"` → 가장 최근 미분류 운행이 `일반업무 · 거래처 미팅`으로 바뀜(그 운행의 도착이 미등록 위치면 거래처 저장 질문이 이어서 뜸).
  - `adb shell "am start -a START_PRIVATE_TRIP"` → 다음 운행이 질문 없이 `개인 용무`로 기록됨.
  - `adb shell "am start -a GET_BUSINESS_RATIO"` → 토스트와 TTS로 "이번 달 업무사용비율은 50.0퍼센트예요".
  - `assets/schema.json`과 매니페스트 intent-filter가 같은 세 이름을 선언한다.
- **⑦ TTS** — 질문 카드가 뜰 때 문장을 읽도록 호출한다. 호출마다 새로 만들어도(`init ok` → `speak` 호출까지는 매번 성공) **재생 시작 이벤트(`onStart`)는 앱을 새로 설치한 뒤 첫 호출에서만 확인됐다.** 2번째 이후 호출은 `onStart`가 오지 않았다(5회 중 1회 확인). 소리는 보조이고 질문은 항상 화면 카드로 뜬다(설계서 B2 유지).
- **⑧ STT** — 마이크 입력은 에뮬레이터가 무음이라 결과가 오지 않는다. probe의 PCM 음성 파일을 보내면 부분 결과가 실제로 온다(`내일` → `내일 오후` → `내일 오후 3` 뒤 `ON_DEVICE_ENGINE_ERROR`, 마지막 결과는 빈 문자열). 이 음성에는 목적 키워드가 없어 답으로 이어지지는 않았다. 키워드 해석은 단위 테스트(`KeywordsTest`)로 확인했고, 인식 → 답 기록의 끝까지 이어지는 시연은 못 했다.
- **⑥ 길안내** — 경로를 요청하면(약 16초 걸림) `onRouteStarted name=Gangnam toll=700`이 온다. 길안내 중 운행을 하면 그 운행에 통행료 700원과 목적지 이름이 붙는다(리포트 '예상 통행료 700원').
  - **에뮬레이터는 `onRouteStarted` 직후(4ms 뒤)에 `onRouteEnded`를 보낸다.** 이걸 '길안내 종료'로 처리하면 목적지·통행료가 지워지므로 무시하고, `onRouteCancelled`·`onDestinationArrived`에서만 지운다.
  - `onDestinationInfo`는 경로 취소 직후 `name=""`, `toll=0`으로도 온다. 이름이 빈 값이면 무시한다.
  - 경로 요청(`requestRoute`)은 `pleos.car.permission.NAVI_ROUTE_SEARCH`가 필요해 debug 빌드에만 선언했다. 앱 본체는 요청하지 않고 이벤트만 받는다.
  - 길안내 목적지 이름이 등록 장소 이름과 같을 때 분류에 쓰는 규칙은 단위 테스트로만 확인했다(실험의 `Gangnam`은 등록 장소가 아님).
- **유류비·통행료** — 리포트에 '예상 유류비'(입력 연비·단가 기준, 0.6km에서 100원)와 '예상 통행료'(700원)가 나온다. 연비를 차량에서 읽는 시도는 하지 않는다(결과표 R9 미지원).
- **내보내기** — 미분류가 남아 있으면 "미분류 2건이 남아 있어요" 경고와 `그래도 내보내기` / `정리하러 가기`가 뜬다. 정리 후 내보내면 엑셀·PDF가 저장되고 서식 칸 그대로의 미리보기와 ⑪⑫⑬이 나온다.
  - 저장 위치: 앱 전용 폴더(`Android/data/<패키지>/files/exports`)와 공용 `Download/AutoLog/`(MediaStore). **앱 전용 폴더는 `adb pull`·`run-as`로 읽을 수 없고(권한 거부), `Download/AutoLog`도 `adb pull`이 사용자 10 저장소 권한 때문에 막힌다.** 꺼내는 방법: `adb shell "content query --user 10 --uri content://media/external/file --projection _id:_display_name --where \"_display_name like 'logbook%'\""`로 `_id`를 찾고 `adb exec-out "content read --user 10 --uri content://media/external/file/<id>" > logbook.xlsx`.
  - 꺼낸 엑셀을 openpyxl로 열어 확인: 시트 `운행기록부`, 서식 칸 이름(①②③…⑬), ⑤⑥⑦⑧⑨는 숫자 셀, ⑬은 `100.0`에 `0.0"%"` 서식. PDF는 PyMuPDF로 렌더링해 한글·표·⑪⑫⑬이 맞게 그려지는 것을 확인했다.
  - 에뮬레이터 값은 2 / 2 / 100.0%(운행이 0.3km씩이라 정수 km로 버림되어 작다). 155 / 133 / 85.8%는 단위 테스트(`CostAndExportTest`)로 확인했고 화면에서는 못 봤다.
  - 서식 칸 구성은 제3자가 올린 2016년 서식 엑셀 사본 기준이다(저장소에 포함하지 않은 개발 메모에 칸 구성을 정리해 두었다). 국세청 원본과 대조해야 한다.

### 단계 6 release 빌드 검증

- `assembleRelease`(R8 최적화 켬, 저장소 밖 키스토어로 서명). `apksigner verify`로 서명 확인(인증서 `CN=AutoLog`). 크기 25.5MB.
- **R8을 `kotlin.**`·`kotlinx.**` 범위에 켜기만 하면 앱이 시작하자마자 죽는다**: `IllegalAccessError: Class kotlin.collections.j extended by class ut is inaccessible` (`androidx.startup.InitializationProvider` 초기화 중). `app/src/main/keepRules/rules.keep`에 `-keep class kotlin.** { *; }`, `-keep class kotlinx.** { *; }`를 넣으니 해결됐다(APK 21.9MB → 25.5MB).
- 최적화된 release APK를 에뮬레이터에 새로 설치해 확인: 시작(`READY`), 기어·주행거리 이벤트로 운행 기록(Room), 위치 상태, 질문 카드와 TTS 호출, Gleo `LOG_TRIP_PURPOSE`, 거래처 저장 질문, 월간 리포트·내보내기(Download/AutoLog에 저장, 거리 0km일 때 ⑬은 `-`)까지 크래시 없이 동작.
- `tools/demo.py` 스모크 테스트(현재 값에서 시작, 배율 0.01로 운행마다 0.2~0.5km, 총 +1.6km → 4.9km): 준비·운행 5건·Gleo 답 4건·앱 강제 종료 후 주행·재실행까지 끝까지 실행되고, 기록부에 일반업무(메모 포함)·퇴근·개인 용무·빈 구간이 남았다. 이 설치본에는 장소가 등록돼 있지 않아 장소 기반 자동 분류는 확인하지 않았다.
- 주행거리를 올린 양: 3.0 → 3.3(release 확인) → 4.9km.

### 단계 0 ⑩ 화면 해상도

- `wm size` 2560x1440, 밀도 160dpi. 앱 창은 홈 화면 안에서 약 1682x1210px(853,90 ~ 2535,1300)로 열렸다.

### 주행거리를 올린 양

| 일시 | 올린 양 | 누적 |
|---|---|---|
| 2026-11-13 | 0 → 0.1 → 0.2 → 0.3 km (실험 ①②) | 0.3 km |
| 2026-11-13 | 0.3 → 1.1 km (앱으로 시연 1번 확인) | 1.1 km |
| 2026-11-13 | 1.1 → 1.9 km (기어 D 중 화면 확인, 재시작 타이밍 시험) | 1.9 km |
| 2026-11-13 | 1.9 → 2.7 km (시연 6번: 강제 종료 후 주행) | 2.7 km |
| 2026-11-13 | 2.7 → 3.0 km (지연 측정) | 3.0 km |
| 2026-11-13 | (에뮬레이터가 재시작되어 0.0km에서 다시 시작) 0 → 1.8 km (단계 4 분류·질문 확인, 운행마다 0.3km) | 1.8 km |
| 2026-11-14 | 1.8 → 3.0 km (단계 5 Gleo·길안내·내보내기 확인, 운행마다 0.3km) | 3.0 km |
| 2026-11-14 | 3.0 → 3.3 km (release APK 확인) → 4.9 km (`tools/demo.py` 스모크 테스트, 배율 0.01) | 4.9 km |
