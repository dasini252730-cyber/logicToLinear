#!/usr/bin/env node
// backlog.json 조회·수정·추가 CLI. 사용법: node scripts/backlog.mjs help
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import {
  ROOT, load, save, findTask, normalizeId, nextId, validate, ensureDocs, renderDoc,
  depsDone, today, TASK_FIELDS, LIST_FIELDS, READONLY_FIELDS,
} from "./backlog-lib.mjs";

// ---------- 인자 파싱 ----------
function parseArgs(argv) {
  const pos = [];
  const opts = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith("--")) {
      let [k, v] = a.slice(2).split(/=(.*)/s);
      if (v === undefined) {
        if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) v = argv[++i];
        else v = true;
      }
      if (k in opts) opts[k] = [].concat(opts[k], v);
      else opts[k] = v;
    } else pos.push(a);
  }
  return { pos, opts };
}

const splitList = (v) => (v === undefined || v === true ? [] : [].concat(v).flatMap((s) => String(s).split(/\s*[,;]\s*/)).map((s) => s.trim()).filter(Boolean));

// ---------- 출력 ----------
const wide = (ch) => /[ᄀ-ᅟ⺀-꓏가-힣豈-﫿︰-﹏＀-｠￠-￦]/.test(ch);
const width = (s) => [...String(s)].reduce((n, ch) => n + (wide(ch) ? 2 : 1), 0);
function pad(s, w) {
  s = String(s);
  return s + " ".repeat(Math.max(0, w - width(s)));
}
function cut(s, w) {
  s = String(s);
  let out = "", n = 0;
  for (const ch of s) {
    const cw = wide(ch) ? 2 : 1;
    if (n + cw > w - 1) return out + "…";
    out += ch; n += cw;
  }
  return out;
}
function table(rows, headers) {
  const widths = headers.map((h, i) => Math.max(width(h), ...rows.map((r) => width(r[i]))));
  const line = (cells) => cells.map((c, i) => pad(c, widths[i])).join("  ");
  console.log(line(headers));
  console.log(widths.map((w) => "-".repeat(w)).join("  "));
  for (const r of rows) console.log(line(r));
}

const STATUS_MARK = {
  todo: "○", in_progress: "◐", review: "◉", needs_human: "?", done: "●", cancelled: "×", waiting: "‖",
};

function fail(msg) {
  console.error(`오류: ${msg}`);
  process.exit(1);
}

function mustFind(backlog, id) {
  const t = findTask(backlog, id);
  if (!t) fail(`task ${normalizeId(id)} 없음`);
  return t;
}

function touch(t) {
  t.updatedAt = today();
}

// 상세 문서의 상태 행을 동기화한다.
function syncDocStatus(t) {
  const path = join(ROOT, t.doc);
  if (!existsSync(path)) return;
  const md = readFileSync(path, "utf8");
  const next = md.replace(/^\| 상태 \| .* \|$/m, `| 상태 | ${t.status} |`);
  if (next !== md) writeFileSync(path, next, "utf8");
}

function saveAndValidate(backlog) {
  const errors = validate(backlog);
  if (errors.length) fail(`저장 중단, 검증 실패:\n  ${errors.join("\n  ")}`);
  save(backlog);
}

// ---------- 명령 ----------
const commands = {};

commands.help = () => {
  console.log(`backlog.json CLI

조회
  list [--status s] [--phase n] [--assignee a] [--priority p] [--tag t] [--ready] [--all] [--json]
        기본은 done·cancelled를 숨긴다. --ready는 선행 작업이 모두 done인 todo만.
  show <id> [--json]                task 상세 (id는 T-101, t101, 101 모두 허용)
  next [--n 5]                      지금 시작할 수 있는 task
  stats                             상태별·단계별 집계
  validate                          무결성 검사

수정
  status <id> <상태> [--reason "..."] [--force]
        상태 변경. in_progress는 startedAt, done은 completedAt을 채운다.
        waiting은 --reason 필수. 선행 미완료 시 --force 없이는 in_progress 불가.
  set <id> field=value [field=value ...]
        필드 직접 수정. 배열 필드는 쉼표 구분, null은 null로.
        예: set T-101 priority=P1 tags=setup,gradle blockedReason=null
  note <id> "내용"                  notes에 날짜와 함께 한 줄 추가
  dep <id> add|rm <otherId>         의존성 추가·제거
  ac <id> add "조건" | rm <n>       완료 조건 추가·삭제(n은 1부터)

추가
  add --title "..." --phase n [--desc "..."] [--priority P0] [--assignee claude|human]
      [--status todo] [--deps T-101,T-102] [--tags a,b] [--spec "섹션1;섹션2"]
      [--ac "조건1" --ac "조건2"]
        id는 단계 안에서 자동 부여(T-<phase><nn>), 상세 문서도 함께 생성.
  docs [--force]                    없는 상세 문서 생성(--force는 전부 다시 생성)
  regen-doc <id>                    해당 task 상세 문서를 템플릿으로 다시 생성(기존 내용 삭제)

상태 값: todo, in_progress, review, needs_human, done, cancelled, waiting`);
};

commands.list = (backlog, { opts }) => {
  let rows = backlog.tasks;
  if (opts.status) { const s = splitList(opts.status); rows = rows.filter((t) => s.includes(t.status)); }
  else if (!opts.all) rows = rows.filter((t) => !["done", "cancelled"].includes(t.status));
  if (opts.phase !== undefined) { const p = splitList(opts.phase).map(Number); rows = rows.filter((t) => p.includes(t.phase)); }
  if (opts.assignee) rows = rows.filter((t) => t.assignee === opts.assignee);
  if (opts.priority) { const p = splitList(opts.priority); rows = rows.filter((t) => p.includes(t.priority)); }
  if (opts.tag) { const tags = splitList(opts.tag); rows = rows.filter((t) => tags.some((g) => t.tags.includes(g))); }
  if (opts.ready) rows = rows.filter((t) => t.status === "todo" && depsDone(backlog, t));
  if (opts.json) { console.log(JSON.stringify(rows, null, 2)); return; }
  if (!rows.length) { console.log("(없음)"); return; }
  table(
    rows.map((t) => [
      t.id,
      `${STATUS_MARK[t.status] ?? " "} ${t.status}`,
      t.priority,
      String(t.phase),
      t.assignee,
      depsDone(backlog, t) ? "" : "대기:" + t.dependsOn.filter((d) => findTask(backlog, d)?.status !== "done").join(","),
      cut(t.title, 44),
    ]),
    ["ID", "상태", "P", "단계", "담당", "선행", "제목"],
  );
  console.log(`\n${rows.length}개`);
};

commands.show = (backlog, { pos, opts }) => {
  const t = mustFind(backlog, pos[0] ?? fail("id 필요"));
  if (opts.json) { console.log(JSON.stringify(t, null, 2)); return; }
  const phase = backlog.phases.find((p) => p.id === t.phase);
  console.log(`${t.id}  ${t.title}`);
  console.log(`${"─".repeat(60)}`);
  console.log(`상태      ${STATUS_MARK[t.status]} ${t.status}   우선순위 ${t.priority}   담당 ${t.assignee}   ${t.estimateMinutes}분`);
  console.log(`단계      ${t.phase}. ${phase?.name ?? ""}`);
  console.log(`문서      ${t.doc}`);
  console.log(`설명      ${t.description}`);
  console.log(`선행      ${t.dependsOn.length ? t.dependsOn.map((d) => `${d}(${findTask(backlog, d)?.status ?? "?"})`).join(", ") : "없음"}`);
  const dependents = backlog.tasks.filter((x) => x.dependsOn.includes(t.id)).map((x) => x.id);
  console.log(`후행      ${dependents.length ? dependents.join(", ") : "없음"}`);
  console.log(`참조      ${t.specRefs.join(" / ") || "-"}`);
  console.log(`태그      ${t.tags.join(", ") || "-"}`);
  console.log(`완료 조건`);
  for (const c of t.acceptanceCriteria) console.log(`  - ${c}`);
  console.log(`날짜      생성 ${t.createdAt}  수정 ${t.updatedAt}  시작 ${t.startedAt ?? "-"}  완료 ${t.completedAt ?? "-"}`);
  if (t.blockedReason) console.log(`대기 사유 ${t.blockedReason}`);
  if (t.notes) console.log(`메모\n${t.notes.split("\n").map((l) => "  " + l).join("\n")}`);
};

commands.next = (backlog, { opts }) => {
  const n = Number(opts.n ?? 5);
  const order = { P0: 0, P1: 1, P2: 2, P3: 3 };
  const ready = backlog.tasks
    .filter((t) => t.status === "todo" && depsDone(backlog, t))
    .sort((a, b) => a.phase - b.phase || order[a.priority] - order[b.priority] || a.id.localeCompare(b.id))
    .slice(0, n);
  const active = backlog.tasks.filter((t) => ["in_progress", "review", "needs_human"].includes(t.status));
  if (active.length) {
    console.log("진행 중 / 리뷰 / 사람 판단 필요:");
    table(active.map((t) => [t.id, `${STATUS_MARK[t.status]} ${t.status}`, t.assignee, cut(t.title, 50)]), ["ID", "상태", "담당", "제목"]);
    console.log();
  }
  console.log("시작 가능:");
  if (!ready.length) console.log("(없음)");
  else table(ready.map((t) => [t.id, t.priority, String(t.phase), t.assignee, cut(t.title, 50)]), ["ID", "P", "단계", "담당", "제목"]);
};

commands.stats = (backlog) => {
  const byStatus = {};
  for (const s of Object.keys(backlog.statuses)) byStatus[s] = 0;
  for (const t of backlog.tasks) byStatus[t.status]++;
  console.log("상태별");
  table(Object.entries(byStatus).map(([s, n]) => [`${STATUS_MARK[s]} ${s}`, String(n)]), ["상태", "수"]);
  console.log("\n단계별");
  table(
    backlog.phases.map((p) => {
      const ts = backlog.tasks.filter((t) => t.phase === p.id);
      const done = ts.filter((t) => t.status === "done").length;
      const cancelled = ts.filter((t) => t.status === "cancelled").length;
      const left = ts.length - done - cancelled;
      return [String(p.id), p.name, String(ts.length), String(done), String(left), `${left * backlog.unitMinutes}분`];
    }),
    ["단계", "이름", "전체", "완료", "남음", "남은 시간"],
  );
  const total = backlog.tasks.filter((t) => t.status !== "cancelled").length;
  const done = byStatus.done;
  console.log(`\n전체 ${total}개 중 ${done}개 완료 (${total ? Math.round((done / total) * 100) : 0}%), 남은 예상 ${(total - done) * backlog.unitMinutes}분`);
};

commands.validate = (backlog) => {
  const errors = validate(backlog);
  const missingDocs = backlog.tasks.filter((t) => !existsSync(join(ROOT, t.doc))).map((t) => t.id);
  if (missingDocs.length) errors.push(`상세 문서 없음: ${missingDocs.join(", ")} (docs 명령으로 생성)`);
  if (errors.length) { console.error(errors.join("\n")); process.exit(1); }
  console.log(`정상: task ${backlog.tasks.length}개, 문서 ${backlog.tasks.length}개`);
};

commands.status = (backlog, { pos, opts }) => {
  const t = mustFind(backlog, pos[0] ?? fail("id 필요"));
  const to = pos[1] ?? fail("새 상태 필요");
  if (!(to in backlog.statuses)) fail(`알 수 없는 상태 ${to}. 가능: ${Object.keys(backlog.statuses).join(", ")}`);
  const from = t.status;
  const reason = opts.reason === true ? "" : opts.reason;

  if (to === "in_progress" && !depsDone(backlog, t) && !opts.force) {
    const open = t.dependsOn.filter((d) => findTask(backlog, d)?.status !== "done");
    fail(`선행 task 미완료: ${open.join(", ")}. 그래도 시작하려면 --force`);
  }
  if (to === "waiting" && !reason && !t.blockedReason) fail("waiting 상태는 --reason 필요");

  t.status = to;
  if (to === "in_progress" && !t.startedAt) t.startedAt = today();
  if (to === "done") t.completedAt = today();
  if (from === "done" && to !== "done") t.completedAt = null;
  if (to === "waiting") t.blockedReason = reason || t.blockedReason;
  else if (from === "waiting") t.blockedReason = null;
  if (reason && to !== "waiting") appendNote(t, `[${to}] ${reason}`);
  touch(t);
  saveAndValidate(backlog);
  syncDocStatus(t);
  console.log(`${t.id}: ${from} → ${to}`);
  if (to === "done" && t.assignee === "human") console.log("참고: 담당이 human인 task입니다. 사용자 확인을 거쳤는지 확인하세요.");
  if (to === "done") {
    const unlocked = backlog.tasks.filter((x) => x.status === "todo" && x.dependsOn.includes(t.id) && depsDone(backlog, x));
    if (unlocked.length) console.log(`시작 가능해진 task: ${unlocked.map((x) => x.id).join(", ")}`);
  }
};

function appendNote(t, text) {
  const line = `${today()} ${text}`;
  t.notes = t.notes ? `${t.notes}\n${line}` : line;
}

commands.note = (backlog, { pos }) => {
  const t = mustFind(backlog, pos[0] ?? fail("id 필요"));
  const text = pos.slice(1).join(" ");
  if (!text) fail("메모 내용 필요");
  appendNote(t, text);
  touch(t);
  saveAndValidate(backlog);
  console.log(`${t.id}: 메모 추가`);
};

function coerce(field, raw) {
  if (raw === "null") return null;
  if (LIST_FIELDS.has(field)) return splitList(raw);
  if (field === "phase" || field === "estimateMinutes") {
    const n = Number(raw);
    if (Number.isNaN(n)) fail(`${field}는 숫자여야 함`);
    return n;
  }
  return raw;
}

commands.set = (backlog, { pos }) => {
  const t = mustFind(backlog, pos[0] ?? fail("id 필요"));
  const pairs = pos.slice(1);
  if (!pairs.length) fail("field=value 필요");
  for (const p of pairs) {
    const m = p.match(/^([A-Za-z]+)=(.*)$/s);
    if (!m) fail(`형식 오류: ${p} (field=value)`);
    const [, field, raw] = m;
    if (!TASK_FIELDS.includes(field)) fail(`알 수 없는 필드 ${field}. 가능: ${TASK_FIELDS.join(", ")}`);
    if (READONLY_FIELDS.has(field)) fail(`${field}는 직접 수정 불가`);
    if (field === "status") fail("status는 status 명령을 사용");
    if (field === "dependsOn") for (const d of splitList(raw)) if (!findTask(backlog, d)) fail(`없는 task ${d}`);
    t[field] = coerce(field, raw);
    if (field === "dependsOn") t.dependsOn = t.dependsOn.map(normalizeId);
  }
  touch(t);
  saveAndValidate(backlog);
  console.log(`${t.id}: ${pairs.map((p) => p.split("=")[0]).join(", ")} 수정`);
};

commands.dep = (backlog, { pos }) => {
  const t = mustFind(backlog, pos[0] ?? fail("id 필요"));
  const action = pos[1];
  const other = mustFind(backlog, pos[2] ?? fail("대상 id 필요"));
  if (action === "add") {
    if (!t.dependsOn.includes(other.id)) t.dependsOn.push(other.id);
  } else if (action === "rm") {
    t.dependsOn = t.dependsOn.filter((d) => d !== other.id);
  } else fail("add 또는 rm");
  touch(t);
  saveAndValidate(backlog);
  console.log(`${t.id} dependsOn: ${t.dependsOn.join(", ") || "없음"}`);
};

commands.ac = (backlog, { pos }) => {
  const t = mustFind(backlog, pos[0] ?? fail("id 필요"));
  const action = pos[1];
  if (action === "add") {
    const text = pos.slice(2).join(" ");
    if (!text) fail("조건 내용 필요");
    t.acceptanceCriteria.push(text);
  } else if (action === "rm") {
    const n = Number(pos[2]);
    if (!n || n > t.acceptanceCriteria.length) fail(`1~${t.acceptanceCriteria.length} 범위의 번호 필요`);
    t.acceptanceCriteria.splice(n - 1, 1);
  } else fail("add 또는 rm");
  touch(t);
  saveAndValidate(backlog);
  t.acceptanceCriteria.forEach((c, i) => console.log(`  ${i + 1}. ${c}`));
};

commands.add = (backlog, { opts }) => {
  const title = opts.title;
  if (!title || title === true) fail("--title 필요");
  if (opts.phase === undefined) fail("--phase 필요");
  const phase = Number(opts.phase);
  if (!backlog.phases.some((p) => p.id === phase)) fail(`없는 phase ${opts.phase}`);
  const defaultPriority = phase <= 2 ? "P0" : phase <= 5 ? "P1" : phase <= 7 ? "P2" : "P3";
  const deps = splitList(opts.deps).map(normalizeId);
  for (const d of deps) if (!findTask(backlog, d)) fail(`없는 task ${d}`);
  const id = nextId(backlog, phase);
  const now = today();
  const t = {
    id,
    title,
    description: opts.desc && opts.desc !== true ? opts.desc : title,
    doc: `${backlog.taskDocDir}/${id}.md`,
    phase,
    status: opts.status && opts.status !== true ? opts.status : "todo",
    priority: opts.priority && opts.priority !== true ? opts.priority : defaultPriority,
    assignee: opts.assignee && opts.assignee !== true ? opts.assignee : "claude",
    estimateMinutes: backlog.unitMinutes,
    dependsOn: deps,
    specRefs: splitList(opts.spec),
    acceptanceCriteria: opts.ac === undefined ? [] : [].concat(opts.ac).filter((s) => s !== true),
    tags: splitList(opts.tags),
    createdAt: now,
    updatedAt: now,
    startedAt: null,
    completedAt: null,
    blockedReason: opts.reason && opts.reason !== true ? opts.reason : null,
    notes: "",
  };
  // 같은 단계 끝에 넣어 파일 순서를 유지한다.
  let idx = backlog.tasks.length;
  for (let i = backlog.tasks.length - 1; i >= 0; i--) if (backlog.tasks[i].phase === phase) { idx = i + 1; break; }
  backlog.tasks.splice(idx, 0, t);
  saveAndValidate(backlog);
  ensureDocs(backlog);
  console.log(`추가: ${id}  ${title}\n문서: ${t.doc}`);
};

commands.docs = (backlog, { opts }) => {
  const created = ensureDocs(backlog, { force: !!opts.force });
  console.log(created.length ? `문서 생성: ${created.join(", ")}` : "생성할 문서 없음");
};

commands["regen-doc"] = (backlog, { pos }) => {
  const t = mustFind(backlog, pos[0] ?? fail("id 필요"));
  writeFileSync(join(ROOT, t.doc), renderDoc(backlog, t), "utf8");
  console.log(`${t.doc} 다시 생성`);
};

// ---------- 진입 ----------
const [cmd = "help", ...rest] = process.argv.slice(2);
if (!(cmd in commands)) {
  console.error(`알 수 없는 명령: ${cmd}\n`);
  commands.help();
  process.exit(1);
}
if (cmd === "help") commands.help();
else commands[cmd](load(), parseArgs(rest));
