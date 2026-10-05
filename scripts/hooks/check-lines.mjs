// PostToolUse(Write|Edit): 파일 줄 수를 .claude/code-limits.json 기준으로 검사.
// 85% 이상 경고, 100% 초과면 block(다시 작업 요청).
import { readFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { readInput, out, ROOT, rel, toPosix } from "./_lib.mjs";

const input = readInput();
const filePath = input.tool_input?.file_path ?? input.tool_response?.filePath;
if (!filePath || !existsSync(filePath)) process.exit(0);

const cfgPath = join(ROOT, ".claude", "code-limits.json");
if (!existsSync(cfgPath)) process.exit(0);
const cfg = JSON.parse(readFileSync(cfgPath, "utf8"));
const warnRatio = cfg.warnRatio ?? 0.85;

// 최소 glob → 정규식: **, *, ?, {a,b}
function globToRegex(glob) {
  let re = "";
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === "*") {
      if (glob[i + 1] === "*") {
        i++;
        if (glob[i + 1] === "/") { i++; re += "(?:.*/)?"; } else re += ".*";
      } else re += "[^/]*";
    } else if (c === "?") re += "[^/]";
    else if (c === "{") {
      const end = glob.indexOf("}", i);
      re += "(?:" + glob.slice(i + 1, end).split(",").map((s) => s.replace(/[.+^$()|[\]\\]/g, "\\$&")).join("|") + ")";
      i = end;
    } else re += c.replace(/[.+^$()|[\]\\]/g, "\\$&");
  }
  return new RegExp("^" + re + "$", "i");
}

const relPath = rel(filePath);
const rule = cfg.rules.find((r) => globToRegex(r.glob).test(relPath) || globToRegex(r.glob).test(toPosix(filePath).split("/").pop()));
if (!rule || rule.limit == null) process.exit(0);

const text = readFileSync(filePath, "utf8");
const lines = text.split(/\r?\n/).filter((l, i, arr) => !(i === arr.length - 1 && l === "")).length;
const ratio = lines / rule.limit;
const pct = Math.round(ratio * 100);
const why = rule.why ? ` (${rule.why})` : "";

if (ratio > 1) {
  out({
    decision: "block",
    reason:
      `[줄 수 초과] ${relPath}: ${lines}줄 / 상한 ${rule.limit}줄 (${pct}%). 규칙 ${rule.glob}${why}.\n` +
      `이 파일은 상한을 넘었으므로 다시 작업하세요: 책임을 나눠 별도 파일로 분리하거나, 중복을 제거해 ${rule.limit}줄 이하로 만든 뒤 진행합니다. 상한을 올리려면 사용자 확인이 필요합니다.`,
  });
  process.exit(0);
}

if (ratio >= warnRatio) {
  const msg = `[줄 수 경고] ${relPath}: ${lines}줄 / 상한 ${rule.limit}줄 (${pct}%). 남은 여유 ${rule.limit - lines}줄. 더 늘어날 예정이면 지금 분리를 계획하세요.${why}`;
  out({
    systemMessage: msg,
    hookSpecificOutput: { hookEventName: "PostToolUse", additionalContext: msg },
  });
}
process.exit(0);
