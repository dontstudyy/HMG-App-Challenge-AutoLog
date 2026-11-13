# 오토로그 (AutoLog)

계기판 누적거리를 차에서 직접 읽어 국세청 업무용승용차 운행기록부를 자동으로 완성해 주는 업무 운행 기록 앱입니다.

- 현대 Pleos Connect 앱 공모전 출품작
- 대상 환경: Android(Kotlin) / Pleos Connect SDK
- 상태: 개발 준비 중

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

### 단계 0 ⑩ 화면 해상도

- `wm size` 2560x1440, 밀도 160dpi. 앱 창은 홈 화면 안에서 약 1682x1210px(853,90 ~ 2535,1300)로 열렸다.

### 주행거리를 올린 양

| 일시 | 올린 양 | 누적 |
|---|---|---|
| 2026-11-13 | 0 → 0.1 → 0.2 → 0.3 km (실험 ①②) | 0.3 km |
