// PreToolUse: backlog.json 직접 접근 차단. CLI(scripts/backlog.mjs) 경유만 허용.
import { readInput, out, BACKLOG_CLI_HINT, isBacklogCli, toPosix } from "./_lib.mjs";

const input = readInput();
const tool = input.tool_name ?? "";
const ti = input.tool_input ?? {};

function deny(reason) {
  out({
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: reason,
    },
  });
  process.exit(0);
}

const touchesBacklog = (s) => /(^|[\\/\s"'`])backlog\.json\b/i.test(toPosix(s));

if (["Read", "Edit", "Write", "MultiEdit", "NotebookEdit"].includes(tool)) {
  if (touchesBacklog(ti.file_path ?? ti.notebook_path)) deny(`${tool} 도구로 backlog.json에 접근할 수 없습니다.\n${BACKLOG_CLI_HINT}`);
}

if (tool === "Grep" && (touchesBacklog(ti.path) || touchesBacklog(ti.glob))) {
  deny(`backlog.json을 Grep으로 읽을 수 없습니다.\n${BACKLOG_CLI_HINT}`);
}

if (tool === "Bash" || tool === "PowerShell") {
  const cmd = String(ti.command ?? "");
  if (touchesBacklog(cmd) && !isBacklogCli(cmd)) {
    deny(`셸 명령으로 backlog.json을 읽거나 수정할 수 없습니다.\n${BACKLOG_CLI_HINT}`);
  }
}

process.exit(0);
