"""Run a Linux CI client and fail promptly if Minecraft opens its crash screen.

Usage: python3 scripts/ci_client_smoke.py GAME_DIRECTORY COMMAND [ARGS...]
Existing Gradle pass-marker checks and GitHub's step timeout remain in force.
"""

import os
from pathlib import Path
import signal
import subprocess
import sys
import time


def reports(directory):
    result = {}
    for path in (directory / "crash-reports").glob("*.txt"):
        try:
            stat = path.stat()
        except FileNotFoundError:
            continue  # Rotated or removed after glob enumerated the path.
        result[path] = (stat.st_mtime_ns, stat.st_size)
    return result


def stop(process):
    # The launcher, Xvfb, Gradle and Minecraft share our isolated process group.
    # --no-daemon in CI keeps the game out of a pre-existing Gradle daemon.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=3)
    except subprocess.TimeoutExpired:
        pass
    finally:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait()


def run(directory, command):
    cancelled = 0

    def cancel(signum, _frame):
        # Do not raise from the handler: it may interrupt Popen or cleanup.
        nonlocal cancelled
        cancelled = cancelled or signum

    handlers = {sig: signal.signal(sig, cancel) for sig in (signal.SIGTERM, signal.SIGINT)}
    process = None
    try:
        baseline = reports(directory)
        previous = baseline
        process = subprocess.Popen(command, start_new_session=True)
        while True:
            if cancelled:
                return 128 + cancelled
            result = process.poll()
            current = reports(directory)
            changed = {path: stamp for path, stamp in current.items() if baseline.get(path) != stamp}
            for path, stamp in changed.items():
                if previous.get(path) == stamp:
                    print(f"::error::Minecraft crashed: {path}; see the uploaded crash report.", flush=True)
                    return 1
            if result is not None and not changed:
                return result
            # Give a newly written report at least one full poll to settle.
            previous = current
            time.sleep(1)
    finally:
        try:
            if process is not None:
                stop(process)
        finally:
            for sig, handler in handlers.items():
                signal.signal(sig, handler)


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit("Usage: ci_client_smoke.py GAME_DIRECTORY COMMAND [ARGS...]")
    sys.exit(run(Path(sys.argv[1]), sys.argv[2:]))
