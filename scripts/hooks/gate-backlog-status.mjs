// PreToolUse(Bash): `backlog status <id> review|done` 전에 lint/build를 강제한다.
// 실패하면 상태 전환을 거부한다.
import { readInput, out, isBacklogCli } from "./_lib.mjs";
import { verifyBuild } from "./verify-build.mjs";

const input = readInput();
const cmd = String(input.tool_input?.command ?? "");
if (!isBacklogCli(cmd)) process.exit(0);

const m = cmd.match(/\bstatus\s+(\S+)\s+(review|done)\b/);
if (!m) process.exit(0);

const r = verifyBuild();
if (!r.ok) {
  out({
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: `${m[1]}을(를) ${m[2]}로 바꾸기 전에 빌드가 통과해야 합니다.\n${r.message}`,
    },
  });
  process.exit(0);
}
if (!r.skipped) out({ systemMessage: r.message });
process.exit(0);
