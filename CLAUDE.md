# CLAUDE.md

**독서 기록 Android 앱**(1인용, Kotlin + Jetpack Compose) 프로젝트다. 종이책에서 밑줄 친 문장을 폰으로 찍어 OCR로 저장하고, 완독 후 별점·한 줄 소감을 남기고, 쌓인 기록으로 Claude가 취향 분석·추천·토론을 해준다. 성패는 기능 수가 아니라 **타이핑을 얼마나 없애느냐**에 달려 있다.

세부 규칙은 `.claude/rules/`에 있고 자동으로 로드된다. 이 문서는 지도다.

## 문서 지도

| 문서 | 내용 | 언제 읽나 |
| --- | --- | --- |
| `docs/요구사항.md` | 사용자가 확정한 명세 원문. 결정 표, 데이터 모델, 화면 흐름, AI 프롬프트 요지, 예외 처리 표, 작업 순서 | 기능을 구현하기 전, 판단이 갈릴 때 |
| `backlog.json` | 30분 단위 task 74개와 상태. **CLI로만 접근** | `node scripts/backlog.mjs next` |
| `docs/tasks/<id>.md` | task별 상세: 브리핑, 완료 조건, 작업 메모, 결과, 리뷰 | task 시작 전·중·후 |
| `.claude/rules/product-principles.md` | 위반 금지 원칙 6개, 바꾸면 안 되는 결정, 범위 밖 | 항상 |
| `.claude/rules/workflow.md` | task 하나를 끝내는 10단계와 서브에이전트 사용 규칙 | 항상 |
| `.claude/rules/backlog.md` | CLI 명령, 상태 흐름, hook 커밋 | 백로그를 다룰 때 |
| `.claude/rules/code-limits.md` | 파일 줄 수 상한과 경고·초과 대응 | 코드를 쓸 때 |
| `.claude/rules/git-and-hooks.md` | 브랜치 정책, 설정된 hook 목록 | 커밋·hook 관련 |
| `.claude/rules/android.md` | 패키지 구조, Compose·Room·테스트 규칙 (app/** 경로에서 로드) | Android 코드 |
| `.claude/rules/ai-api.md` | Anthropic 호출 규칙 (AI 관련 경로에서 로드) | AI 기능 |
| `.claude/code-limits.json` | 줄 수 상한 정의 | hook이 읽음 |

## 현재 상태

- Android 프로젝트 골격이 있다(T-101). 빈 자리 표시 화면 하나, 패키지 디렉터리 초안(`.gitkeep`), Compose 외 기능 의존성은 아직 없다. `node scripts/backlog.mjs next`로 현재 위치를 확인한다.
- `needs_human` 4개(TTB 키, Anthropic 키, 실기기 준비, HTTP 클라이언트 선택)는 사용자가 처리한다. 이 중 실기기 준비(T-005) 전에는 "실기기에서 뜬다" 류의 완료 조건을 확인할 수 없다.
- 작업 브랜치는 `dev`. `main`이면 전환을 요청한다.

## 작업 루프 (요약, 상세는 rules/workflow.md)

```
next → task-briefer(haiku) 브리핑 → status in_progress → 구현·테스트
     → adversarial-reviewer(opus) 리뷰, 차단 0까지 수정 → 작업 메모·결과 기록
     → status review (hook이 lint·build·테스트) → 사용자 확인 → done (hook이 커밋+push)
```

- task를 시작하기 전에 `task-briefer`를 반드시 부른다.
- 파일을 만들거나 바꾼 뒤 `review`로 올리기 전에 `adversarial-reviewer`를 반드시 부른다.
- `done`은 사용자가 확인한 뒤에만.

## 로컬 빌드 환경

- JDK는 Android Studio JBR(21). Git Bash에서는 `java`가 PATH에 없으므로 gradlew 전에 `export JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr"`를 붙인다. PowerShell은 시스템 JAVA_HOME이 이미 JBR이다.
- Android SDK는 `local.properties`의 `sdk.dir`(gitignore 대상). platforms 34·36·37.0 설치됨.
- 빌드 스택: AGP 9.4.1(Kotlin 내장, `kotlin-android` 플러그인 적용 금지), Gradle 9.8.0 래퍼, Kotlin 2.4.20, compileSdk·targetSdk 37, minSdk 26. 세부는 `docs/tasks/T-101.md`.
- HTTP 클라이언트: Retrofit + OkHttp + kotlinx-serialization 컨버터(T-006 결정). 알라딘·Anthropic 호출 모두 이 조합.
- 테스트: Robolectric(sdk=34 고정) + `MainDispatcherRule` + Room `setQueryCoroutineContext(테스트 디스패처)`. 기기 없이 `./gradlew testDebugUnitTest`로 Room·ViewModel까지 돈다.

## 자주 쓰는 명령

```
node scripts/backlog.mjs next                 지금 할 수 있는 task
node scripts/backlog.mjs show T-101           task 상세
node scripts/backlog.mjs status T-101 in_progress
node scripts/backlog.mjs add --title "..." --phase 2 --desc "..." --deps T-205 --ac "..."
node scripts/backlog.mjs help

gradlew.bat assembleDebug                     빌드
gradlew.bat installDebug                      실기기 설치
gradlew.bat testDebugUnitTest                 단위 테스트
gradlew.bat lintDebug                         lint
```

## 절대 원칙 (상세는 rules/product-principles.md)

1. 앱을 열면 2초 안에 카메라. 2. 타이핑은 제목 검색·한 줄 소감·페이지 번호 세 곳만. 3. 로그인·서버·동기화 없음, 로컬 Room만. 4. AI 호출은 버튼을 눌렀을 때만. 5. 사진 원본 저장 금지. 6. API 키는 설정 화면 입력, 코드·백업에 넣지 않음.

요구사항에 없는 기능·화면·설정·의존성을 "있으면 좋을 것 같아서" 추가하지 않는다. 필요하면 task로 제안한다.

## 작업 방식

- 한 번에 task 하나. 뒤 단계 기능을 미리 넣지 않는다.
- UI 문구·주석·커밋 메시지·문서는 한국어. 코드 식별자는 영어.
- 외부 라이브러리를 추가하면 이유를 한 줄 적는다. OCR·도서 검색·AI·HTTP·Room·직렬화·이미지 로딩 범위 안에서.
- Room 스키마 변경은 Migration과 백업 `schemaVersion`을 같은 커밋에서 올린다.
- 모델 ID·단가는 저장소 문서를 믿지 말고 구현 시점에 공식 문서로 확인한다.
- hook이 거부하거나 되돌리면 그 지시를 따른다. hook·설정을 바꾸는 것은 사용자 요청이 있을 때만.
- 실기기 확인(`assignee: human`) task는 Claude가 `done`으로 바꾸지 않는다.
