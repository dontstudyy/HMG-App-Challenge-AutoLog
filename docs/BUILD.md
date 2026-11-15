# 소스에서 빌드

[← README](../README.md)

필요한 것: Android SDK(compileSdk 37)와 JDK(Android Studio에 포함된 JBR), 그리고 Pleos 라이브러리 저장소(`nexus-playground.pleos.ai`)에 접근할 인터넷.

```
# local.properties에 sdk.dir 지정 후
gradlew.bat testDebugUnitTest assembleDebug     # 인터넷 필요(Pleos 라이브러리 저장소)
gradlew.bat assembleRelease                     # keystore.properties가 없으면 debug 키로 서명
```

서명 키스토어와 `keystore.properties`는 저장소에 없습니다(저장소 밖 보관). 제출용 APK는 키가 있는 PC에서 빌드했습니다. release는 R8 최적화를 켜되 `kotlin.**` 보존 규칙이 필요합니다(없으면 시작 시 `IllegalAccessError`로 종료됨 — [검증 기록](VERIFICATION.md#단계-6-release-빌드-검증) 참고).

## 키스토어 설정 (제출용 서명)

저장소 루트에 `keystore.properties`(gitignore 대상)를 두면 release가 그 키로 서명됩니다. 키 이름만 적습니다(값은 저장소에 없습니다).

```
storeFile=<키스토어 경로>
storePassword=<...>
keyAlias=<...>
keyPassword=<...>
```

## 테스트

```
gradlew.bat testDebugUnitTest           # 앱 단위 테스트 48건(운행 엔진, 분류, 집계, 엑셀, 음성 키워드)
python -m unittest discover -s tools    # 시연 스크립트 판단 로직 테스트
```

## 참고

- Windows에서 Gradle은 PowerShell에서 실행하는 것이 안전했습니다(Git Bash에서는 Kotlin 데몬·AAPT2 시작이 실패한 적이 있음).
- clone한 새 폴더에서 `testDebugUnitTest assembleDebug assembleRelease`가 모두 통과함을 확인했습니다(키스토어·`local.properties` 없이, `local.properties`에 `sdk.dir`만 지정).
