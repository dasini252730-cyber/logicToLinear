---
paths:
  - "app/**"
  - "build.gradle.kts"
  - "settings.gradle.kts"
  - "gradle/**"
---

# Android 코드 규칙

## 구조

- 단일 `app` 모듈. 패키지 초안(프로젝트 생성 시 확정):
  - `data/db` Room 엔티티·DAO·Database·Converter
  - `data/repo` Repository
  - `data/remote/aladin`, `data/remote/anthropic` HTTP 클라이언트·DTO
  - `data/backup` 백업 모델·매퍼
  - `data/secret` SecretStore(EncryptedSharedPreferences)
  - `domain` 순수 함수(줄 합치기, 페이지 후보, JSON 파싱, 동일 책 판정, 턴 계산)
  - `ocr` ML Kit 래퍼
  - `ui/<화면>` 화면별 Screen·ViewModel·components
  - `ui/navigation` 라우트 상수와 NavHost
- DI 프레임워크 없이 `AppContainer`에서 수동 주입. ViewModel은 Factory.
- 로직은 `domain`의 순수 함수로 빼고 단위 테스트한다. ViewModel은 상태와 이벤트만.

## Compose

- `@Composable` 화면은 `XxxScreen(state, onEvent)` 형태로 상태를 받고 이벤트를 올린다. ViewModel을 직접 참조하는 컴포저블은 라우트 진입점 하나만.
- 문자열은 `strings.xml`. 요구사항 원문 문구를 그대로 쓴다.
- 미리보기(`@Preview`)는 화면마다 하나.

## Room

- 스키마를 바꾸면 같은 커밋에서 `version`을 올리고 `Migration`을 추가하고 백업 `schemaVersion`을 같은 값으로 맞춘다. `fallbackToDestructiveMigration`은 쓰지 않는다.
- `exportSchema = true`, `schemas/` 디렉터리를 커밋한다.
- Instant·LocalDate·enum은 TypeConverter. 시간은 `Instant`, 완독일만 `LocalDate`.

## 네트워크·비밀

- API 키는 `SecretStore`에서만 읽는다. 로그에 찍지 않는다.
- 네트워크 호출은 Repository 안에서만. UI나 ViewModel이 HTTP 클라이언트를 직접 쓰지 않는다.
- 오프라인·오류는 `Result` 또는 sealed class로 돌려주고 UI가 요구사항 "예외 처리" 표대로 보여 준다.

## 테스트

- 순수 함수: `src/test` JUnit. 경계값(빈 입력, 1개, 상한, 상한+1)을 포함한다.
- Room: `src/androidTest` 또는 Robolectric. CASCADE·Migration은 테스트로 증명한다.
- 테스트 이름은 `동작_조건_기대결과` 한국어 가능. 예: `joinLines_음절중간_공백없이붙임`.

## 하지 말 것

- `GlobalScope`, `runBlocking`(테스트 제외), `!!` 남용.
- `Log.d`에 사용자 데이터(글귀 텍스트, 키) 출력.
- 요구사항에 없는 설정 항목·화면·다이얼로그 추가.
