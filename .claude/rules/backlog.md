# 백로그 운용 (backlog.json)

작업은 30분 단위 task로 `backlog.json`에 있고, 상세 문서는 `docs/tasks/<id>.md`다.

## 접근은 CLI로만

`backlog.json`을 Read·Edit·Write·Grep·cat·jq 등으로 직접 열지 않는다. hook이 차단한다. 항상 CLI를 쓴다.

```
node scripts/backlog.mjs next                      지금 시작할 수 있는 task
node scripts/backlog.mjs list [--phase n] [--ready] [--status s]
node scripts/backlog.mjs show <id>
node scripts/backlog.mjs status <id> <상태> [--reason "..."]
node scripts/backlog.mjs set <id> field=value ...
node scripts/backlog.mjs note <id> "메모"
node scripts/backlog.mjs ac <id> add "완료 조건"
node scripts/backlog.mjs dep <id> add|rm <otherId>
node scripts/backlog.mjs add --title "..." --phase n --desc "..." --deps T-xxx --ac "..." --spec "섹션"
node scripts/backlog.mjs validate | stats | docs | help
```

## 모니터링 보드

`node scripts/backlog-dashboard.mjs [--port 4173]`을 띄우고 `http://localhost:4173`을 열면 상태별·단계별 집계, task 표(검색·필터), task 상세 문서를 볼 수 있다. 3초마다 다시 읽으므로 CLI로 상태를 바꾸면 화면이 따라온다. 서버 스크립트가 backlog.json을 읽는 것은 CLI와 같은 경로라 허용된다. 페이지 자체(`scripts/dashboard/index.html`)는 수정 전용이 아니라 조회 전용이다.

`docs/tasks/<id>.md`는 직접 편집해도 된다. 작업 메모·결과·브리핑·리뷰를 거기에 쓴다.

## 상태 흐름

`todo → in_progress → review → done`이 기본이다.

- `in_progress`: task를 시작할 때. 선행 task가 모두 `done`이어야 한다. 아니면 CLI가 거부한다(`--force`는 사용자가 허락했을 때만).
- `review`: 구현과 테스트가 끝나고 적대적 리뷰까지 반영한 뒤. hook이 lint·build·테스트를 먼저 돌리고 실패하면 전환을 거부한다.
- `done`: Claude가 스스로 확인할 수 있는 완료 조건(빌드·테스트·파일·문서)이 모두 충족되고 적대적 리뷰 차단이 0이면 바꾼다. 사람만 확인할 수 있는 조건(실기기 화면, 외부 키, 콘솔 비용)이 남으면 `review`에 두고 사용자에게 묻는 목록에 올린다. `assignee: human`인 task는 사용자가 직접 바꾼다. (2026-10-05 사용자 지시: 진행 가능한 것은 묻지 말고 계속 진행, 판단 필요한 것은 맨 뒤에 모아서 질문)
- 선행 task가 `review`(사람 확인 대기)라서 막힐 때는 `--force`로 다음 task를 시작할 수 있다. 선행이 `todo`·`in_progress`면 안 된다.
- `needs_human`: 결정·키 발급·실기기 확인 등 사람이 해야 할 때. `--reason`으로 무엇을 결정해야 하는지 적는다.
- `waiting`: 외부 요인으로 막혔을 때. `--reason` 필수.
- `cancelled`: 범위 밖으로 판명됐을 때. 사용자 확인 후.

## 한 번에 하나

- 동시에 `in_progress`인 task는 하나만 둔다.
- 작업 중 다른 문제를 발견하면 고치지 말고 `add`로 task를 만든다. 같은 단계 번호대에 자동으로 id가 붙는다.
- 이 task의 범위 밖 코드를 건드리지 않는다. 필요하면 task를 추가하고 `dep`으로 연결한다.

## 커밋은 hook이 한다

- CLI로 backlog를 바꾸면 PostToolUse hook이 `backlog.json`과 `docs/tasks/`를 자동 커밋한다. 직접 `git add backlog.json`을 하지 않는다.
- `status <id> done`이면 hook이 **모든 변경 파일**을 "T-xxx 완료: 제목" 메시지로 커밋하고 현재 브랜치를 push한다. 그러므로 `done`을 찍기 전에 작업 트리에 이 task와 무관한 변경이 남아 있지 않게 정리한다.
- main 브랜치에서는 push를 하지 않는다. dev 등 작업 브랜치에서 작업한다.
