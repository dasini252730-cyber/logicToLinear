// hook 스크립트 공용: stdin JSON 읽기, 결과 출력, git 실행.
import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";

export const ROOT = process.env.CLAUDE_PROJECT_DIR || process.cwd();

export function readInput() {
  try {
    const raw = readFileSync(0, "utf8");
    return raw.trim() ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

export function out(obj) {
  process.stdout.write(JSON.stringify(obj) + "\n");
}

export function git(args, opts = {}) {
  const stdin = opts.input !== undefined ? "pipe" : "ignore";
  return execSync(`git ${args}`, { cwd: ROOT, encoding: "utf8", stdio: [stdin, "pipe", "pipe"], ...opts }).trim();
}

export function tryGit(args) {
  try { return git(args); } catch { return null; }
}

export function currentBranch() {
  return tryGit("branch --show-current") || "(detached)";
}

export function toPosix(p) {
  return String(p ?? "").replace(/\\/g, "/");
}

// 절대 경로를 프로젝트 기준 상대 경로로
export function rel(p) {
  const posix = toPosix(p);
  const root = toPosix(ROOT).replace(/\/$/, "");
  return posix.toLowerCase().startsWith(root.toLowerCase() + "/") ? posix.slice(root.length + 1) : posix;
}

export const BACKLOG_CLI_HINT =
  "backlog.json은 직접 읽거나 수정하지 않습니다. CLI를 사용하세요:\n" +
  "  node scripts/backlog.mjs list [--phase n] [--ready]   조회\n" +
  "  node scripts/backlog.mjs show <id>                     상세\n" +
  "  node scripts/backlog.mjs next                          다음 할 일\n" +
  "  node scripts/backlog.mjs status <id> <상태>            상태 변경\n" +
  "  node scripts/backlog.mjs set <id> field=value          필드 수정\n" +
  "  node scripts/backlog.mjs add --title ... --phase n     task 추가\n" +
  "  node scripts/backlog.mjs help                          전체 명령";

// 명령 문자열이 backlog CLI 호출인지
export function isBacklogCli(command) {
  return /(scripts[\\/]backlog\.mjs|backlog\.cmd|(^|[\s;&|])\.?[\\/]?backlog(\s|$)|gen-task-docs\.mjs)/.test(command);
}
