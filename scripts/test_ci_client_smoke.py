"""Regression coverage for the CI crash-screen watchdog; no Minecraft needed."""

from pathlib import Path
import os
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

import ci_client_smoke


class CrashReportsTest(unittest.TestCase):
    def test_disappearing_reports_are_skipped_and_each_file_is_statted_once(self):
        missing, present = Mock(), Mock()
        missing.stat.side_effect = FileNotFoundError
        present.stat.return_value = Mock(st_mtime_ns=12, st_size=34)
        with patch.object(Path, "glob", return_value=iter([missing, present])):
            self.assertEqual({present: (12, 34)}, ci_client_smoke.reports(Path("unused")))
        missing.stat.assert_called_once()
        present.stat.assert_called_once()


@unittest.skipUnless(sys.platform == "linux", "CI process groups require Linux")
class ClientSmokeWatchdogTest(unittest.TestCase):
    def launch(self, directory, source):
        return subprocess.run(
            [sys.executable, str(Path(__file__).with_name("ci_client_smoke.py")),
             str(directory), sys.executable, "-c", source, str(directory)],
            capture_output=True, text=True, timeout=10,
        )

    def test_exit_status_is_preserved_and_stale_reports_are_ignored(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            crashes = directory / "crash-reports"
            crashes.mkdir()
            (crashes / "old.txt").write_text("previous run")
            for status in (0, 7):
                result = self.launch(directory, f"raise SystemExit({status})")
                self.assertEqual(status, result.returncode, result.stdout + result.stderr)

    def test_crash_report_stops_a_hung_client(self):
        with tempfile.TemporaryDirectory() as temporary:
            result = self.launch(Path(temporary), """
from pathlib import Path
import sys
import time
crashes = Path(sys.argv[1]) / 'crash-reports'
crashes.mkdir()
(crashes / 'crash.txt').write_text('Mod loading failed')
time.sleep(60)
""")
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("Minecraft crashed:", result.stdout)
            self.assertTrue((Path(temporary) / "crash-reports/crash.txt").exists())

    def test_report_finishes_writing_before_client_is_stopped(self):
        with tempfile.TemporaryDirectory() as temporary:
            result = self.launch(Path(temporary), """
from pathlib import Path
import sys
import time
crashes = Path(sys.argv[1]) / 'crash-reports'
crashes.mkdir()
with (crashes / 'crash.txt').open('w') as report:
    for _ in range(10):
        report.write('details\\n')
        report.flush()
        time.sleep(.3)
    report.write('COMPLETE\\n')
time.sleep(60)
""")
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertTrue((Path(temporary) / "crash-reports/crash.txt").read_text().endswith("COMPLETE\n"))

    def test_sigterm_cleans_up_client_and_descendant(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = """
from pathlib import Path
import os
import signal
import subprocess
import sys
import time
directory = Path(sys.argv[1])
role = sys.argv[2] if len(sys.argv) > 2 else 'client'
def stop(signum, frame):
    (directory / (role + '.stopped')).touch()
    if role == 'client':
        deadline = time.monotonic() + 1
        while not (directory / 'descendant.stopped').exists() and time.monotonic() < deadline:
            time.sleep(.01)
    raise SystemExit(0)
signal.signal(signal.SIGTERM, stop)
if role == 'client':
    (directory / 'client.pid').write_text(str(os.getpid()))
    subprocess.Popen([sys.executable, '-c', (directory / 'client.py').read_text(), str(directory), 'descendant'])
(directory / (role + '.ready')).touch()
while True:
    time.sleep(.1)
"""
            (directory / "client.py").write_text(source)
            watchdog = subprocess.Popen(
                [sys.executable, str(Path(__file__).with_name("ci_client_smoke.py")),
                 str(directory), sys.executable, "-c", source, str(directory)],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
            )
            try:
                deadline = time.monotonic() + 5
                while not (directory / "descendant.ready").exists() and time.monotonic() < deadline:
                    time.sleep(.05)
                self.assertTrue((directory / "descendant.ready").exists())
                watchdog.send_signal(signal.SIGTERM)
                output, error = watchdog.communicate(timeout=8)
                self.assertEqual(128 + signal.SIGTERM, watchdog.returncode, output + error)
                self.assertTrue((directory / "client.stopped").exists())
                self.assertTrue((directory / "descendant.stopped").exists())
            finally:
                if watchdog.poll() is None:
                    watchdog.kill()
                    watchdog.communicate()
                if (directory / "client.pid").exists():
                    try:
                        os.killpg(int((directory / "client.pid").read_text()), signal.SIGKILL)
                    except ProcessLookupError:
                        pass


if __name__ == "__main__":
    unittest.main()
