# 코드 크기 규칙

파일 줄 수 상한은 `.claude/code-limits.json`에 있다. Write·Edit 뒤 hook이 자동으로 검사한다.

| 파일 | 상한 |
| --- | --- |
| `*Screen.kt` (Compose 화면) | 250 |
| `*ViewModel.kt` | 200 |
| `*Repository.kt` | 200 |
| `*Dao.kt` | 120 |
| `*Test.kt` | 400 |
| 그 외 `*.kt` | 300 |
| `*.kts` | 150 |
| `scripts/hooks/*.mjs` | 150 |
| `scripts/backlog.mjs` | 400 |
| 그 외 `scripts/**/*.mjs` | 250 |
| md, json, toml, properties | 검사 안 함 |

## 경고(85% 이상)를 받으면

- 그 파일에 더 넣을 계획이면 지금 분리 지점을 정한다. 작업 메모에 "분리 예정: 무엇을 어디로" 한 줄을 적는다.
- 이번 task에서 분리까지 할 필요는 없다. 분리가 다음 task 범위에 걸치면 `add`로 task를 만든다.

## 초과(100% 초과)를 받으면

- 멈추고 그 파일부터 분리한다. 초과 상태로 다음 파일로 넘어가지 않는다.
- 분리 기준: 책임 하나당 파일 하나. Compose 화면은 하위 컴포저블을 `components/`로, ViewModel의 로직은 순수 함수나 Repository로, DAO의 복잡한 쿼리는 뷰나 별도 DAO로.
- 상한 자체를 올리는 것은 사용자 확인이 필요하다. 올릴 때는 `code-limits.json`의 `why`에 이유를 적는다.

## 파일 크기 외의 기준 (hook이 검사하지 않으므로 스스로 지킨다)

- 함수는 40줄을 넘기지 않는다. Composable은 60줄.
- 중첩 깊이 4단계 이상이면 함수로 뺀다.
- 한 파일에 public 클래스 하나. data class·enum은 관련 클래스 파일에 같이 둘 수 있다.
- 같은 코드가 두 번 나오면 세 번째 전에 합친다.
