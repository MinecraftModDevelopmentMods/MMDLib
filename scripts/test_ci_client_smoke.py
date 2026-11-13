"""Regression coverage for the CI crash-screen watchdog; no Minecraft needed."""

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


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


if __name__ == "__main__":
    unittest.main()
