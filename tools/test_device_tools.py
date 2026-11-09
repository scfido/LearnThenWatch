"""Host tests for temporary recovery-permission restoration; no TV required."""
import unittest
from unittest.mock import patch
import device_tools


class RecoveryIsolationTest(unittest.TestCase):
    def test_only_existing_grants_are_revoked_and_restored(self):
        calls = []
        def fake(device, *parts, **kwargs):
            calls.append(parts)
            if parts[:3] == ("shell", "dumpsys", "package"):
                return "android.permission.WRITE_SECURE_SETTINGS: granted=true" if parts[-1].endswith(".debug") else ""
            return ""
        with patch.object(device_tools, "adb", fake):
            with device_tools.suspended_recovery("test-device"):
                pass
        changes = [c for c in calls if c[:2] == ("shell", "pm")]
        self.assertEqual([c[2] for c in changes], ["revoke", "grant"])
        self.assertTrue(all(c[3] == device_tools.PACKAGE for c in changes))

    def test_restores_after_test_failure(self):
        calls = []
        def fake(device, *parts, **kwargs):
            calls.append(parts)
            return "android.permission.WRITE_SECURE_SETTINGS: granted=true" if "dumpsys" in parts else ""
        with patch.object(device_tools, "adb", fake):
            with self.assertRaisesRegex(RuntimeError, "test failed"):
                with device_tools.suspended_recovery("test-device"):
                    raise RuntimeError("test failed")
        self.assertEqual(len([c for c in calls if "grant" in c]), 2)

    def test_attempts_all_restores_even_when_one_fails(self):
        grants = []
        def fake(device, *parts, **kwargs):
            if "dumpsys" in parts:
                return "android.permission.WRITE_SECURE_SETTINGS: granted=true"
            if "grant" in parts:
                grants.append(parts[3])
                if len(grants) == 1:
                    raise RuntimeError("transport failed")
            return ""
        with patch.object(device_tools, "adb", fake):
            with self.assertRaisesRegex(RuntimeError, "Could not restore"):
                with device_tools.suspended_recovery("test-device"):
                    pass
        self.assertEqual(len(grants), 2)


if __name__ == "__main__":
    unittest.main()
