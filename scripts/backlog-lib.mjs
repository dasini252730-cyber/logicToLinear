// backlog.json 읽기·쓰기·검증·상세 문서 생성 공용 모듈.
import { readFileSync, writeFileSync, existsSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
export const BACKLOG_PATH = join(ROOT, "backlog.json");

export const TASK_FIELDS = [
  "id", "title", "description", "doc", "phase", "status", "priority", "assignee",
  "estimateMinutes", "dependsOn", "specRefs", "acceptanceCriteria", "tags",
  "createdAt", "updatedAt", "startedAt", "completedAt", "blockedReason", "notes",
];
export const LIST_FIELDS = new Set(["dependsOn", "specRefs", "acceptanceCriteria", "tags"]);
export const READONLY_FIELDS = new Set(["id", "doc", "createdAt", "updatedAt"]);

export function today() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function load() {
  return JSON.parse(readFileSync(BACKLOG_PATH, "utf8"));
}

export function save(backlog) {
  backlog.updatedAt = today();
  writeFileSync(BACKLOG_PATH, JSON.stringify(backlog, null, 2) + "\n", "utf8");
}

export function findTask(backlog, id) {
  const norm = normalizeId(id);
  return backlog.tasks.find((t) => t.id === norm);
}

export function normalizeId(id) {
  const s = String(id).trim().toUpperCase();
  if (/^\d+$/.test(s)) return `T-${s.padStart(3, "0")}`;
  if (/^T\d+$/.test(s)) return `T-${s.slice(1).padStart(3, "0")}`;
  return s;
}

export function nextId(backlog, phase) {
  const prefix = `T-${phase}`;
  const nums = backlog.tasks
    .filter((t) => t.phase === phase && t.id.startsWith(prefix))
    .map((t) => Number(t.id.slice(prefix.length)))
    .filter((n) => !Number.isNaN(n));
  const next = nums.length ? Math.max(...nums) + 1 : 1;
  return `${prefix}${String(next).padStart(2, "0")}`;
}

export function validate(backlog) {
  const errors = [];
  const validStatuses = new Set(Object.keys(backlog.statuses));
  const validPriorities = new Set(Object.keys(backlog.priorities));
  const validAssignees = new Set(Object.keys(backlog.assignees));
  const phaseIds = new Set(backlog.phases.map((p) => p.id));
  const ids = new Set(backlog.tasks.map((t) => t.id));

  if (ids.size !== backlog.tasks.length) errors.push("중복 id 존재");

  for (const t of backlog.tasks) {
    for (const f of TASK_FIELDS) if (!(f in t)) errors.push(`${t.id}: 필드 누락 ${f}`);
    if (t.doc !== `${backlog.taskDocDir}/${t.id}.md`) errors.push(`${t.id}: doc 경로가 ${backlog.taskDocDir}/${t.id}.md 가 아님`);
    if (!validStatuses.has(t.status)) errors.push(`${t.id}: 알 수 없는 status ${t.status}`);
    if (!validPriorities.has(t.priority)) errors.push(`${t.id}: 알 수 없는 priority ${t.priority}`);
    if (!validAssignees.has(t.assignee)) errors.push(`${t.id}: 알 수 없는 assignee ${t.assignee}`);
    if (!phaseIds.has(t.phase)) errors.push(`${t.id}: 알 수 없는 phase ${t.phase}`);
    if (t.estimateMinutes !== backlog.unitMinutes) errors.push(`${t.id}: estimateMinutes가 ${backlog.unitMinutes}이 아님`);
    for (const f of LIST_FIELDS) if (!Array.isArray(t[f])) errors.push(`${t.id}: ${f}는 배열이어야 함`);
    for (const d of t.dependsOn ?? []) {
      if (!ids.has(d)) errors.push(`${t.id}: dependsOn에 없는 id ${d}`);
      if (d === t.id) errors.push(`${t.id}: 자기 자신에 의존`);
    }
    if (t.status === "waiting" && !t.blockedReason) errors.push(`${t.id}: waiting 상태에는 blockedReason이 필요`);
    if (t.status === "done" && !t.completedAt) errors.push(`${t.id}: done 상태에는 completedAt이 필요`);
  }

  const byId = Object.fromEntries(backlog.tasks.map((t) => [t.id, t]));
  const state = {};
  const visit = (id, stack) => {
    if (state[id] === "done") return;
    if (state[id] === "visiting") { errors.push(`순환 의존: ${[...stack, id].join(" -> ")}`); return; }
    state[id] = "visiting";
    for (const d of byId[id]?.dependsOn ?? []) if (byId[d]) visit(d, [...stack, id]);
    state[id] = "done";
  };
  for (const t of backlog.tasks) visit(t.id, []);
  return errors;
}

export function renderDoc(backlog, t) {
  const byId = Object.fromEntries(backlog.tasks.map((x) => [x.id, x]));
  const phaseName = Object.fromEntries(backlog.phases.map((p) => [p.id, p.name]));
  const deps = t.dependsOn.length
    ? t.dependsOn.map((d) => `- [${d} ${byId[d]?.title ?? ""}](${d}.md)`).join("\n")
    : "- 없음";
  return `# ${t.id} ${t.title}

| 항목 | 값 |
| --- | --- |
| 단계 | ${t.phase}. ${phaseName[t.phase] ?? ""} |
| 상태 | ${t.status} |
| 우선순위 | ${t.priority} |
| 담당 | ${t.assignee} |
| 예상 | ${t.estimateMinutes}분 |
| 요구사항 참조 | ${t.specRefs.length ? t.specRefs.map((s) => `\`${s}\``).join(", ") : "-"} |

## 설명

${t.description}

## 선행 작업

${deps}

## 완료 조건

${t.acceptanceCriteria.length ? t.acceptanceCriteria.map((c) => `- [ ] ${c}`).join("\n") : "- (없음)"}

## 작업 메모

(작업 중 결정, 측정값, 발견한 문제를 여기에 적는다)

## 결과

(완료 시 무엇을 만들었고 어디서 확인할 수 있는지 적는다)
`;
}

// 없는 상세 문서만 만든다. 생성된 id 목록을 돌려준다.
export function ensureDocs(backlog, { force = false } = {}) {
  mkdirSync(join(ROOT, backlog.taskDocDir), { recursive: true });
  const created = [];
  for (const t of backlog.tasks) {
    const path = join(ROOT, t.doc);
    if (existsSync(path) && !force) continue;
    writeFileSync(path, renderDoc(backlog, t), "utf8");
    created.push(t.id);
  }
  return created;
}

// 선행 task가 모두 done인지
export function depsDone(backlog, t) {
  const byId = Object.fromEntries(backlog.tasks.map((x) => [x.id, x]));
  return t.dependsOn.every((d) => byId[d]?.status === "done");
}
