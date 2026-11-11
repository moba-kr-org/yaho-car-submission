# YAHO Car

차량 경고를 쉽게 설명하고, 정차 후 점검 요약과 현대 공식 정비 안내를 제공하는 Pleos Connect 앱입니다.

## 실행 방법

1. Android Studio에서 Pleos Connect 에뮬레이터를 실행합니다.
2. 저장소 루트에서 제출용 APK를 설치합니다.

   ```sh
   adb devices
   adb install -r "apk/MOVA_YAHO Car_release.apk"
   ```

3. 에뮬레이터의 앱 목록에서 **YAHO Hyundai**를 실행합니다. 처음 실행할 때 차량 상태 자동 조회 항목을 확인하고 시작합니다.
4. 에뮬레이터를 P단에 두고 **내 차 상태 → 가상 경고 켜기 → 점검 준비**를 선택합니다. 증상을 고르면 점검 요약이 표시됩니다. 휴대폰으로 요약 화면의 QR을 스캔하면 현대 공식 서비스 네트워크가 열립니다.
5. 요약을 연 채 에뮬레이터를 D단으로 전환하면 점검 화면이 닫히고 짧은 경고 안내가 표시됩니다.

## 테스트 환경 및 설정

| 항목 | 환경 |
| --- | --- |
| Pleos Connect 에뮬레이터 | v2.1.0, Android API 34, ARM64 시스템 이미지 |
| 화면 규격 | 2560 × 1440 px, 가로 화면, 160 dpi |
| 소스 빌드 | JDK 17, Android SDK Platform 35, Gradle Wrapper |
| 평가 시나리오 | P단에서 가상 경고 및 점검 요약 확인, D단에서 화면 조작 제한 확인 |

가상 경고는 에뮬레이터에서 점검 흐름을 재현하기 위한 입력입니다. 경고 설명과 점검 요약은 별도 서버 설정 없이 확인할 수 있습니다. 현대 공식 서비스 페이지는 휴대폰의 인터넷 연결을 사용합니다.

소스 프로젝트는 `apps/android`에 있습니다. Android Studio에서 이 폴더를 열거나, JDK와 Android SDK를 설정한 터미널에서 아래 명령으로 디버그 빌드와 단위 테스트를 실행할 수 있습니다.

```sh
cd apps/android
./gradlew assembleDebug testDebugUnitTest
```

## 제출용 APK

[MOVA_YAHO Car_release.apk](apk/MOVA_YAHO%20Car_release.apk)
