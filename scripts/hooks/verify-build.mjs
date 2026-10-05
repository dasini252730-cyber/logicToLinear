// lint + build 검증. Stop hook과 review/done 전환 게이트에서 공용으로 쓴다.
// gradlew가 없으면(프로젝트 생성 전) 통과. 마지막 성공 시점의 소스 해시를 기억해 변경이 없으면 재실행하지 않는다.
import { execSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { createHash } from "node:crypto";
import { ROOT, tryGit } from "./_lib.mjs";

const SRC_RE = /\.(kt|kts|xml|toml|properties|java)$/;
const STAMP = join(ROOT, ".claude", "state", "last-green-build.txt");

function sourceHash() {
  const tracked = (tryGit("ls-files") ?? "").split("\n");
  const untracked = (tryGit("ls-files --others --exclude-standard") ?? "").split("\n");
  const files = [...new Set([...tracked, ...untracked])].filter((f) => SRC_RE.test(f) && existsSync(join(ROOT, f))).sort();
  const h = createHash("sha1");
  for (const f of files) {
    h.update(f);
    h.update(readFileSync(join(ROOT, f)));
  }
  return { hash: h.digest("hex"), count: files.length };
}

// 반환: { ok: boolean, skipped: boolean, message: string }
export function verifyBuild({ force = false } = {}) {
  const gradlew = existsSync(join(ROOT, "gradlew.bat")) || existsSync(join(ROOT, "gradlew"));
  if (!gradlew) return { ok: true, skipped: true, message: "gradlew 없음: Android 프로젝트 생성 전이므로 빌드 검증을 건너뜀" };

  const { hash, count } = sourceHash();
  if (count === 0) return { ok: true, skipped: true, message: "검사할 소스 파일 없음" };
  if (!force && existsSync(STAMP) && readFileSync(STAMP, "utf8").trim() === hash) {
    return { ok: true, skipped: true, message: "소스 변경 없음: 마지막 성공 빌드 결과 재사용" };
  }

  // cmd.exe는 cwd의 .bat를 이름만으로 찾지 못할 수 있어 절대 경로로 호출한다.
  const cmd = `"${join(ROOT, process.platform === "win32" ? "gradlew.bat" : "gradlew")}"`;
  const tasks = "lintDebug assembleDebug testDebugUnitTest --console=plain -q";
  try {
    execSync(`${cmd} ${tasks}`, { cwd: ROOT, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"], timeout: 9 * 60 * 1000 });
    mkdirSync(join(ROOT, ".claude", "state"), { recursive: true });
    writeFileSync(STAMP, hash, "utf8");
    return { ok: true, skipped: false, message: `빌드 통과: gradlew ${tasks}` };
  } catch (e) {
    const tail = ((e.stderr || "") + "\n" + (e.stdout || "")).trim().split("\n").slice(-40).join("\n");
    return { ok: false, skipped: false, message: `빌드/lint/테스트 실패 (gradlew ${tasks}):\n${tail}` };
  }
}

// 직접 실행 시(Stop hook): 실패하면 block으로 Claude에게 되돌린다.
if (process.argv[1] && /verify-build\.mjs$/.test(process.argv[1].replace(/\\/g, "/"))) {
  const { readInput, out } = await import("./_lib.mjs");
  const input = readInput();
  if (input.stop_hook_active) process.exit(0); // 이미 block 후 재시도 중이면 무한 루프 방지
  const r = verifyBuild();
  if (!r.ok) {
    out({ decision: "block", reason: `${r.message}\n\n실패를 고친 뒤 다시 종료하세요. 빌드가 깨진 상태로 작업을 끝낼 수 없습니다.` });
  } else if (!r.skipped) {
    out({ systemMessage: r.message });
  }
  process.exit(0);
}
