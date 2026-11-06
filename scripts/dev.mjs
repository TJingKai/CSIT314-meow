/** Optional npm development entry; Python remains the only runtime prerequisite. */
import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const python = path.join(
  root,
  ".venv",
  process.platform === "win32" ? "Scripts/python.exe" : "bin/python",
);
if (!existsSync(python)) {
  console.error("Set up the Python environment first; see README.md.");
  process.exit(1);
}
const managed = process.env.SITES_MANAGED_LINUX_CONTAINER === "1";
const child = spawn(
  python,
  [
    "run.py",
    "--demo",
    "--host",
    managed ? "0.0.0.0" : "127.0.0.1",
    "--port",
    process.env.PORT || "4173",
  ],
  {
    cwd: root,
    stdio: "inherit",
  },
);
child.on("error", (error) => {
  console.error(error.message);
  process.exitCode = 1;
});
child.on("exit", (code) => {
  process.exitCode = code ?? 1;
});
for (const signal of ["SIGINT", "SIGTERM"])
  process.on(signal, () => child.kill(signal));
