# 이세계주행천재 (PassengerCoach)

**운전자의 핸들, 깜빡이, 브레이크, 기어와 내비 안내를 동승자 화면 한 곳에서 실시간으로 보여 주고, 한 문장으로 해설해 주는 Pleos Connect 앱입니다.**
면허가 없는 동승자(예비 운전자)가 가만히 앉아 앞을 보며, 운전 조작을 실제 도로 장면과 연결해 배울 수 있도록 돕습니다.

> 현대자동차그룹 차량용 앱 개발 공모전 출품작 (개인 참가)

## 한눈에 보기
| 화면 요소 | 데이터 출처 |
|---|---|
| 핸들 각도(왼쪽/오른쪽 N°) | Pleos Vehicle SDK `SteeringWheel` |
| 방향지시등(왼쪽/오른쪽/꺼짐) | Vehicle SDK `TurnSignal` |
| 브레이크(켜짐/꺼짐) | Vehicle SDK `Brake` |
| 기어(P/R/N/D) | Vehicle SDK `Gear` |
| 내비 다음 안내(예: 좌회전 80m) | Pleos NaviHelper SDK 회전 안내(TBT) |
| 해설 한 문장 | 위 신호를 조합한 규칙 기반 해설(`CoachEngine`) |

- **읽기 전용**입니다. 차량을 제어하지 않습니다.
- **운전자 화면에는 표시되지 않습니다.** 앱은 주행 최적화(DO)를 선언하지 않아, 센터 화면에서는 주행 중 시스템이 자동으로 차단합니다. 동승자 전용 디스플레이(조수석, 뒷좌석, 태블릿)를 전제로 한 설계이며, 현재 구현과 검증은 에뮬레이터 기준입니다.
- 차량 데이터는 **기기 안에서만** 사용하고 저장하거나 전송하지 않습니다. 첫 실행에서 동의하기 전에는 차량 신호를 읽지 않습니다.
- 점수나 평가를 하지 않고, 일어난 일만 말합니다.

## APK
- 위치: [`apk/InfinityNBeyond_이세계주행천재_release.apk`](apk/) (release 빌드, 서명됨)
- 패키지 이름: `app.passengercoach`, 버전 0.1.0

## 실행 방법 (Pleos Connect 에뮬레이터)
1. Android Studio와 Pleos Connect 에뮬레이터를 설치합니다(공식 가이드: Pleos Playground 개발 환경 설정).
2. 에뮬레이터를 실행합니다.
3. APK를 설치합니다.
   ```
   adb install -r apk/InfinityNBeyond_이세계주행천재_release.apk
   ```
4. 앱을 실행하고 첫 화면에서 **동의하고 시작**을 누릅니다.
5. Virtual Console(`adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`)에서 기어, 핸들, 브레이크, 방향지시등 값을 바꾸면 화면이 따라 바뀝니다.
6. 내비 안내는 에뮬레이터의 내비 앱에서 목적지를 설정해 경로가 있을 때 표시됩니다. 경로가 없으면 "내비 안내 없음"이 표시됩니다.
7. **데모(시험 자료) 모드**: 앱 제목을 5번 누르면 켜지고 끄려면 다시 5번 누릅니다. 차량 없이 좌회전 시나리오를 반복 재생하며, 화면에 "시험 자료(데모)"가 표시됩니다.

## 테스트 환경
| 항목 | 값 |
|---|---|
| Pleos Connect Emulator | **v2.1.0** (이미지 API 34, Android 14) |
| 화면 규격 | **1024 x 768, 160dpi** 가로 (AVD `automotive_1024p_landscape`, RAM 4GB, Cold boot) |
| Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| NaviHelper SDK | `ai.pleos.playground:NaviHelper:3.0.5` |
| 빌드 | JDK 17, Gradle 8.10.2, Android Gradle Plugin 8.8.2, Kotlin 2.0.0, compileSdk 35, minSdk 28 |
| 호스트 | Windows 11 |

## 빌드
```
./gradlew testDebugUnitTest      # 코치 해설 규칙 단위 테스트(11개)
./gradlew assembleDebug          # debug APK (개발용: 에뮬레이터 경로 요청 권한 포함)
./gradlew assembleRelease        # release APK (서명 정보는 환경 변수 COACH_KEYSTORE_PROPS가 가리키는 파일에서 읽음)
```
- Pleos SDK는 `https://nexus-playground.pleos.ai/repository/maven-releases/` 저장소에서 가져옵니다(인증 불필요).
- 경로 요청(`requestRoute`) 검증용 `NAVI_ROUTE_SEARCH` 권한은 **debug 빌드에만** 선언되며 release에는 읽기 권한(`NAVI_ROUTE`)만 있습니다.

## 구조
```
app/src/main/java/app/passengercoach/
  signal/SignalHub.kt      Vehicle SDK 신호 구독(핸들, 브레이크, 방향지시, 기어)
  signal/NaviHub.kt        NaviHelper 회전 안내 구독
  coach/CoachEngine.kt     해설 규칙(Android와 SDK에 의존하지 않음, 단위 테스트 대상)
  demo/DemoPlayer.kt       데모(시험 자료) 시나리오 재생
  ui/CoachScreen.kt        동의 화면과 실시간 화면(Compose)
docs/                      요구서, 여정·IA·화면 흐름, 기술 검증 기록
```

## 확인한 것과 한계
- 에뮬레이터에서 신호 수신 지연은 약 0.2초 이하로 측정했습니다(시계 오차 ±0.1초 포함).
- Vehicle SDK 2.3.2에는 **속도, 가속페달, 시동 상태, 주변 차량 경고 API가 없어** 사용하지 않았습니다. 브레이크는 켜짐/꺼짐만 제공됩니다.
- 실차 검증, 조수석/뒷좌석 디스플레이 지원 여부, 해설 문구의 전문가 검수는 아직 하지 않았습니다(향후 계획).
- 자세한 근거는 [`docs/기술검증_2026-11-13.md`](docs/기술검증_2026-11-13.md)를 참고하세요.

## 라이선스와 고지
- 이 저장소에는 Pleos SDK, 에뮬레이터 이미지, 공식 샘플 앱 코드를 포함하지 않습니다(42dot SDK 라이선스에 따라 재배포하지 않음).
- "현대", "Pleos"는 각 권리자의 상표이며, 이 앱은 공모전 출품작으로 공식 서비스가 아닙니다.
