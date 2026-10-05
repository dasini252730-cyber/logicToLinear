// SessionStart: 현재 브랜치를 알리고 main이면 dev로 옮기라고 안내한다. 시작 가능한 task도 보여준다.
import { execSync } from "node:child_process";
import { out, currentBranch, tryGit, ROOT } from "./_lib.mjs";

const branch = currentBranch();
const lines = [`현재 브랜치: ${branch}`];

if (branch === "main") {
  lines.push("경고: main 브랜치에서 작업 중입니다. 작업 브랜치 dev로 전환하세요.");
  lines.push("  기존 dev가 있으면: git checkout dev");
  lines.push("  없으면:           git checkout -b dev");
  lines.push("main에서는 코드 변경을 시작하지 말고, 사용자에게 브랜치 전환을 먼저 요청하세요.");
}

const dirty = (tryGit("status --porcelain") ?? "").split("\n").filter(Boolean).length;
if (dirty) lines.push(`커밋되지 않은 변경 ${dirty}개 파일`);

try {
  const next = execSync("node scripts/backlog.mjs next --n 3", { cwd: ROOT, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim();
  lines.push("", "backlog 요약:", next);
} catch {
  lines.push("backlog 요약을 가져오지 못했습니다 (node scripts/backlog.mjs next).");
}

const text = lines.join("\n");
out({
  systemMessage: branch === "main" ? `브랜치 ${branch}: dev로 전환하세요` : `브랜치 ${branch}`,
  hookSpecificOutput: { hookEventName: "SessionStart", additionalContext: text },
});
