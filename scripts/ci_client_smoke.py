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
    return {
        path: (path.stat().st_mtime_ns, path.stat().st_size)
        for path in (directory / "crash-reports").glob("*.txt")
    }


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
    previous = reports(directory)
    process = subprocess.Popen(command, start_new_session=True)
    try:
        while True:
            result = process.poll()
            for path, stamp in reports(directory).items():
                if previous.get(path) != stamp:
                    print(f"::error::Minecraft crashed: {path}; see the uploaded crash report.", flush=True)
                    return 1
            if result is not None:
                return result
            time.sleep(1)
    finally:
        stop(process)


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit("Usage: ci_client_smoke.py GAME_DIRECTORY COMMAND [ARGS...]")
    sys.exit(run(Path(sys.argv[1]), sys.argv[2:]))
