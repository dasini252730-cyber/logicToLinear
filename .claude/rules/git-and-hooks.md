# Git과 hook

## 브랜치

- `main`은 완성된 단계만 올리는 브랜치다. 일상 작업은 `dev`에서 한다.
- 세션 시작 시 `main`이면 코드를 건드리지 말고 사용자에게 전환을 요청한다. 사용자가 `main`에서 계속하라고 명시하면 그대로 따른다.
- 브랜치 생성·전환·병합은 사용자가 지시했을 때만 한다.

## 커밋

- 백로그 변경과 task 완료 커밋은 hook이 한다. 그 외 커밋은 사용자가 요청했을 때만.
- 커밋 메시지는 한국어. 첫 줄은 "T-xxx: 무엇을 했는지" 형식. hook이 만드는 메시지는 "backlog: ..."와 "T-xxx 완료: 제목".
- 비밀(API 키, keystore, local.properties)은 절대 커밋하지 않는다. `.gitignore`에 있다.
- `--no-verify`, `--force`, `reset --hard`는 사용자가 명시적으로 요청할 때만.

## 설정된 hook (`.claude/settings.json`, 스크립트는 `scripts/hooks/`)

| 시점 | 스크립트 | 하는 일 |
| --- | --- | --- |
| SessionStart | session-start.mjs | 현재 브랜치 표시, main이면 dev 전환 안내, `backlog next` 요약 |
| PreToolUse (Read·Edit·Write·Grep·Bash·PowerShell) | guard-backlog.mjs | backlog.json 직접 접근 차단, CLI 안내 |
| PreToolUse (Bash·PowerShell) | gate-backlog-status.mjs | `status <id> review\|done` 전에 lint·build·테스트 실행, 실패 시 거부 |
| PostToolUse (Write·Edit) | check-lines.mjs | 줄 수 85% 경고, 100% 초과 시 재작업 요구 |
| PostToolUse (Bash·PowerShell) | backlog-commit.mjs | backlog CLI 실행 후 backlog.json·docs/tasks 커밋. `done`이면 전체 커밋 + push |
| Stop | verify-build.mjs | 소스가 바뀌었으면 lint·build·테스트. 실패하면 작업 종료를 막고 되돌림 |

- 빌드 검증은 `gradlew.bat lintDebug assembleDebug testDebugUnitTest`다. `gradlew`가 없으면(프로젝트 생성 전) 건너뛴다. 마지막 성공 시점의 소스 해시를 `.claude/state/`에 기억해 변경이 없으면 다시 돌리지 않는다.
- backlog CLI를 호출하는 Bash 명령 뒤에 실패할 수 있는 명령을 `&&`로 붙이지 않는다. Bash 호출이 실패로 끝나면 PostToolUse hook(백로그 커밋)이 실행되지 않는다. CLI 호출은 단독 명령으로 보낸다.
- hook이 거부하거나 block하면 그 메시지를 따른다. hook을 우회하거나 `.claude/settings.json`을 바꾸는 것은 사용자 요청이 있을 때만.
- hook 스크립트를 고친 뒤에는 `echo '{...}' | node scripts/hooks/<name>.mjs`로 파이프 테스트를 한다. 입력 예시는 각 스크립트 상단 주석 참고.
