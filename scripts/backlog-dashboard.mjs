// backlog.json 모니터링 대시보드. 사용법: node scripts/backlog-dashboard.mjs [--port 4173]
// 로컬 HTTP 서버 하나로 scripts/dashboard/index.html 과 /api/backlog, /api/doc/<id> 를 제공한다.
// 브라우저가 몇 초마다 /api/backlog 를 다시 읽으므로 backlog.json이 바뀌면 화면이 따라 바뀐다.
import { createServer } from "node:http";
import { readFileSync, existsSync, statSync } from "node:fs";
import { join } from "node:path";
import { execSync } from "node:child_process";
import { ROOT, BACKLOG_PATH, load, normalizeId, depsDone } from "./backlog-lib.mjs";

const args = process.argv.slice(2);
const portIdx = args.indexOf("--port");
const PORT = portIdx >= 0 ? Number(args[portIdx + 1]) : 4173;
const HTML_PATH = join(ROOT, "scripts", "dashboard", "index.html");

function gitInfo() {
  const run = (cmd) => {
    try { return execSync(cmd, { cwd: ROOT, stdio: ["ignore", "pipe", "ignore"] }).toString().trim(); } catch { return ""; }
  };
  return {
    branch: run("git rev-parse --abbrev-ref HEAD"),
    head: run("git log -1 --format=%h%x09%s"),
    dirty: run("git status --porcelain").split("\n").filter(Boolean).length,
  };
}

function snapshot() {
  const backlog = load();
  const byId = new Map(backlog.tasks.map((t) => [t.id, t]));
  const tasks = backlog.tasks.map((t) => ({
    ...t,
    ready: t.status === "todo" && depsDone(backlog, t),
    blockedBy: (t.dependsOn || []).filter((d) => byId.get(d)?.status !== "done"),
    dependents: backlog.tasks.filter((o) => (o.dependsOn || []).includes(t.id)).map((o) => o.id),
  }));
  return {
    project: backlog.project,
    updatedAt: backlog.updatedAt,
    fileMtime: statSync(BACKLOG_PATH).mtime.toISOString(),
    phases: backlog.phases,
    statuses: backlog.statuses,
    git: gitInfo(),
    tasks,
    generatedAt: new Date().toISOString(),
  };
}

function send(res, code, body, type) {
  res.writeHead(code, { "content-type": type, "cache-control": "no-store", "access-control-allow-origin": "*" });
  res.end(body);
}

const server = createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  try {
    if (url.pathname === "/" || url.pathname === "/index.html") {
      return send(res, 200, readFileSync(HTML_PATH), "text/html; charset=utf-8");
    }
    if (url.pathname === "/api/backlog") {
      return send(res, 200, JSON.stringify(snapshot()), "application/json; charset=utf-8");
    }
    const doc = url.pathname.match(/^\/api\/doc\/([^/]+)$/);
    if (doc) {
      const id = normalizeId(decodeURIComponent(doc[1]));
      const path = join(ROOT, "docs", "tasks", `${id}.md`);
      if (!/^T-\d+$/.test(id) || !existsSync(path)) return send(res, 404, "문서 없음", "text/plain; charset=utf-8");
      return send(res, 200, readFileSync(path), "text/markdown; charset=utf-8");
    }
    send(res, 404, "not found", "text/plain; charset=utf-8");
  } catch (e) {
    send(res, 500, String(e?.stack || e), "text/plain; charset=utf-8");
  }
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`backlog 대시보드: http://localhost:${PORT}  (Ctrl+C로 종료)`);
});
