// PostToolUse(Bash): backlog CLI가 실행된 뒤 backlog.json·docs/tasks 변경을 commit한다.
// `status <id> done`이면 모든 작업 내역을 정리해 commit + push한다.
import { readFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { readInput, out, git, tryGit, currentBranch, isBacklogCli, ROOT } from "./_lib.mjs";

const input = readInput();
const cmd = String(input.tool_input?.command ?? "");
if (!isBacklogCli(cmd)) process.exit(0);

const dirty = tryGit("status --porcelain -- backlog.json docs/tasks") ?? "";
const doneMatch = cmd.match(/\bstatus\s+(\S+)\s+done\b/);
if (!dirty && !doneMatch) process.exit(0);

function taskTitle(id) {
  try {
    const b = JSON.parse(readFileSync(join(ROOT, "backlog.json"), "utf8"));
    const norm = id.toUpperCase().replace(/^T?-?(\d+)$/, (_, n) => `T-${n.padStart(3, "0")}`);
    return b.tasks.find((t) => t.id === norm)?.title ?? null;
  } catch {
    return null;
  }
}

const trailer = "\n\nCo-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>";
const messages = [];
const branch = currentBranch();

if (doneMatch) {
  const id = doneMatch[1].toUpperCase();
  const title = taskTitle(id);
  // 작업 내역 전체 정리: 모든 변경 파일 스테이징
  git("add -A");
  const staged = tryGit("diff --cached --name-only") ?? "";
  if (staged) {
    const files = staged.split("\n").filter(Boolean);
    const body = `변경 파일 ${files.length}개:\n${files.map((f) => `- ${f}`).join("\n")}`;
    const subject = title ? `${id} 완료: ${title}` : `${id} 완료`;
    git(`commit -q -F -`, { input: `${subject}\n\n${body}${trailer}` });
    messages.push(`커밋: ${subject} (${files.length}개 파일)`);
  }
  if (branch === "main") {
    messages.push("push 생략: 현재 브랜치가 main입니다. dev 브랜치로 옮긴 뒤 직접 push하세요 (git checkout -b dev && git push -u origin dev).");
  } else {
    const pushed = tryGit(`push -u origin ${branch}`);
    messages.push(pushed === null ? `push 실패: origin/${branch}. 원격 상태를 확인하세요.` : `push 완료: origin/${branch}`);
  }
} else {
  git("add -- backlog.json docs/tasks");
  const staged = tryGit("diff --cached --name-only") ?? "";
  if (staged) {
    // CLI 뒤의 인자만 남긴다. `&&`, `;`, `|` 뒤에 붙은 다른 명령은 잘라낸다.
    const short = cmd
      .replace(/^[\s\S]*?backlog(?:\.mjs|\.cmd)?\s*/, "")
      .split(/\s*(?:&&|;|\|\|?)\s*/)[0]
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 60);
    git(`commit -q -F -`, { input: `backlog: ${short}${trailer}` });
    messages.push(`백로그 커밋: ${short}`);
  }
}

if (messages.length) {
  const text = messages.join("\n");
  out({ systemMessage: text, hookSpecificOutput: { hookEventName: "PostToolUse", additionalContext: text } });
}
process.exit(0);
