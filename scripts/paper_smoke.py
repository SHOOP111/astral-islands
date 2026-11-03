#!/usr/bin/env python3
"""Executed only by GitHub Actions. Two real Paper boots; assertions live in ServerSmoke."""
import hashlib
import json
from pathlib import Path
import queue
import shutil
import subprocess
import sys
import threading
import time
import urllib.request

ROOT = Path("run")
AGENT = "AstralIslands-CI/1.0 (https://github.com/SHOOP111/astral-islands)"

def download(url):
    request = urllib.request.Request(url, headers={"User-Agent": AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()

def boot(number):
    process = subprocess.Popen(
        ["java", "-Dastral.ci=true", "-Xms512m", "-Xmx1536m", "-jar", "paper.jar", "--nogui"],
        cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1,
    )
    lines = queue.Queue()
    def reader():
        with (ROOT / f"boot-{number}.log").open("w", encoding="utf-8") as log:
            for line in process.stdout:
                log.write(line)
                log.flush()
                lines.put(line)
    worker = threading.Thread(target=reader, daemon=True)
    worker.start()
    deadline = time.monotonic() + 240
    requested = False
    passed = False
    marker = "ASTRAL_CI_OK FIRST_BOOT" if number == 1 else "ASTRAL_CI_OK RESTART"
    try:
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=1)
            except queue.Empty:
                if process.poll() is not None:
                    raise RuntimeError(f"Paper exited early with code {process.returncode}")
                continue
            print(line, end="", flush=True)
            if "ASTRAL_CI_FAILED" in line or "Astral failed to start" in line:
                raise RuntimeError("Live plugin assertion or startup failed")
            if 'Done (' in line and not requested:
                process.stdin.write("is ci-smoke\n")
                process.stdin.flush()
                requested = True
            if marker in line:
                passed = True
                break
        if not passed:
            raise TimeoutError(f"Paper boot {number} did not produce {marker}")
    finally:
        if process.poll() is None:
            process.stdin.write("stop\n")
            process.stdin.flush()
            try:
                process.wait(timeout=60)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        worker.join(timeout=10)
    if process.returncode != 0:
        raise RuntimeError(f"Paper shutdown failed with code {process.returncode}")


def main():
    artifact = Path(sys.argv[1]).resolve()
    ROOT.mkdir(exist_ok=True)
    (ROOT / "plugins").mkdir(exist_ok=True)
    shutil.copy2(artifact, ROOT / "plugins" / "AstralIslands.jar")
    metadata = json.loads(download("https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds/232"))
    server = metadata["downloads"]["server:default"]
    jar = download(server["url"])
    if hashlib.sha256(jar).hexdigest() != server["checksums"]["sha256"]:
        raise RuntimeError("Paper download checksum mismatch")
    (ROOT / "paper.jar").write_bytes(jar)
    # This ephemeral CI server accepts Mojang's EULA, does not bind publicly, and has no players.
    (ROOT / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (ROOT / "server.properties").write_text(
        "server-ip=127.0.0.1\nonline-mode=false\nview-distance=2\nsimulation-distance=2\n"
        "spawn-protection=0\nmax-players=2\ngenerate-structures=false\nlevel-type=minecraft:flat\n"
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n',
        encoding="utf-8",
    )
    boot(1)
    boot(2)
    print("PASS: real Paper terrain, inventory, transaction, generator provenance, protection, API and restart checks")

if __name__ == "__main__":
    main()
