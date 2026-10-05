// backlog.json을 검증하고 없는 상세 문서를 생성한다. `node scripts/backlog.mjs validate` + `docs`와 같다.
import { load, validate, ensureDocs } from "./backlog-lib.mjs";

const backlog = load();
const errors = validate(backlog);
if (errors.length) {
  console.error(errors.join("\n"));
  process.exit(1);
}
const created = ensureDocs(backlog);
const counts = {};
for (const t of backlog.tasks) counts[t.status] = (counts[t.status] ?? 0) + 1;
console.log(`tasks: ${backlog.tasks.length}, docs created: ${created.length}, total minutes: ${backlog.tasks.length * backlog.unitMinutes}`);
console.log("status:", JSON.stringify(counts));
