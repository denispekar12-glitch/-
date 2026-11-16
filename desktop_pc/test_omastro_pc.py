import tempfile
import unittest
from pathlib import Path

import omastro_trivoga_pc as pc


class OmastroPcTests(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.state_path = Path(self.tmpdir.name) / "state.json"

    def tearDown(self):
        self.tmpdir.cleanup()

    def test_all_12_audio_tracks_exist(self):
        raw_dir = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"
        expected_files = {info["audio"] for info in pc.ALERT_LEVELS.values()} | {
            info["clear_audio"] for info in pc.ALERT_LEVELS.values()
        }
        for filename in expected_files:
            path = raw_dir / filename
            self.assertTrue(path.is_file(), f"Missing audio asset: {path}")
            self.assertGreater(path.stat().st_size, 1000, f"Empty audio asset: {path}")

    def test_audio_deduplication_shared_tracks(self):
        # RED and MISSILE both use alert_air_raid.m4a; deduplicator must yield it once
        files = [
            pc.ALERT_LEVELS["RED"]["audio"],
            pc.ALERT_LEVELS["MISSILE"]["audio"],
            pc.ALERT_LEVELS["CIVIL_DEFENSE"]["audio"],
            pc.ALERT_LEVELS["CUSTOM_ALERT"]["audio"],
        ]
        deduped = pc.DesktopAudioPlayer.deduplicate_filenames(files)
        self.assertEqual(deduped, ["alert_air_raid.m4a", "alert_civil_defense.m4a"])

    def test_google_admin_vs_regular_user_role(self):
        store = pc.AlertStateStore(storage_path=self.state_path, enable_network=False)
        self.assertEqual(store.user_role, "USER")
        self.assertFalse(store.dsns_auto_sync)

        # Signed in regular user cannot trigger alerts
        store.firebase.uid = "user_123"
        store.firebase.email = "citizen@gmail.com"
        store.firebase.email_verified = True
        store.firebase.id_token = "tok"
        store._recompute_role()
        self.assertEqual(store.user_role, "USER")
        self.assertIsNone(store.set_region_active_alerts(pc.KRYVE_OZERO_ID, {"RED"}))

        # Verified admin Denis automatically gets ADMIN role
        store.firebase.uid = "admin_denis"
        store.firebase.email = pc.ADMIN_OWNER_EMAIL
        store.firebase.email_verified = True
        store._recompute_role()
        self.assertEqual(store.user_role, "ADMIN")
        res = store.set_region_active_alerts(pc.KRYVE_OZERO_ID, {"RED"})
        self.assertIsNotNone(res)

        # Unverified email claiming 2nd admin cannot get ADMIN role
        store.firebase.uid = "spoof_second"
        store.firebase.email = pc.ADMIN_SECOND_EMAIL
        store.firebase.email_verified = False
        store._recompute_role()
        self.assertEqual(store.user_role, "USER")

        # Verified 2nd admin (semenenkom342@gmail.com) gets ADMIN role and can publish alerts
        store.firebase.uid = "admin_second"
        store.firebase.email = pc.ADMIN_SECOND_EMAIL.upper()
        store.firebase.email_verified = True
        store._recompute_role()
        self.assertEqual(store.user_role, "ADMIN")
        res2 = store.set_region_active_alerts(pc.KRYVE_OZERO_ID, {"DRONE"})
        self.assertIsNotNone(res2)

    def _sign_in_admin(self, store):
        store.firebase.uid = "admin_denis"
        store.firebase.email = pc.ADMIN_OWNER_EMAIL
        store.firebase.email_verified = True
        store.firebase.id_token = "tok"
        store._recompute_role()

    def test_dedicated_civil_defense_clear_voice_on_toggle(self):
        store = pc.AlertStateStore(storage_path=self.state_path, enable_network=False)
        self._sign_in_admin(store)

        store.set_region_active_alerts(pc.KRYVE_OZERO_ID, {"CIVIL_DEFENSE", "RED"}, publish_online=False)
        res = store.toggle_alert_in_region(pc.KRYVE_OZERO_ID, "CIVIL_DEFENSE")
        self.assertIsNotNone(res)
        reg, lvls, exp = res
        self.assertEqual(reg["activeAlerts"], ["RED"])
        self.assertEqual(exp, ["alert_civil_defense_clear.m4a"])

    def test_without_all_clear_suppresses_clear_audio(self):
        store = pc.AlertStateStore(storage_path=self.state_path, enable_network=False)
        self._sign_in_admin(store)

        store.set_region_active_alerts(pc.KRYVE_OZERO_ID, {"ARTILLERY"}, publish_online=False)
        res = store.trigger_specific_all_clear(pc.KRYVE_OZERO_ID, "ARTILLERY", without_all_clear=True)
        self.assertIsNotNone(res)
        reg, lvls, exp = res
        self.assertEqual(reg["currentLevel"], "SAFE")
        self.assertEqual(lvls, [])
        self.assertEqual(exp, [])

    def test_max_3_pinned_districts_and_next_day_persistence(self):
        store = pc.AlertStateStore(storage_path=self.state_path, enable_network=False)
        self.assertEqual(len(store.get_monitored_regions()), 2)
        self.assertTrue(store.toggle_monitored_region("pervomaisk_raion"))
        self.assertEqual(len(store.get_monitored_regions()), 3)
        # 4th must be blocked
        self.assertFalse(store.toggle_monitored_region("kyiv_city"))
        self.assertIsNotNone(store.pinned_limit_message)

        # Trigger alert + auth persistence and reload from disk
        self._sign_in_admin(store)
        store.firebase.refresh_token = "rt_saved"
        store.set_region_active_alerts(pc.KRYVE_OZERO_ID, {"CHEMICAL"}, publish_online=False)

        reloaded = pc.AlertStateStore(storage_path=self.state_path, enable_network=False)
        self.assertEqual(reloaded.firebase.email, pc.ADMIN_OWNER_EMAIL)
        self.assertEqual(reloaded.firebase.refresh_token, "rt_saved")
        self.assertEqual(len(reloaded.get_monitored_regions()), 3)
        self.assertEqual(reloaded.get_region(pc.KRYVE_OZERO_ID)["currentLevel"], "CHEMICAL")

    def test_firestore_rest_roundtrip_serialization(self):
        fields = pc._fs_fields({
            "regionName": "Кривоозерська громада",
            "isPublic": True,
            "activeLevels": ["STREET_FIGHTS", "CIVIL_DEFENSE"],
            "clientTimestampMs": 1731570000000,
        })
        parsed = pc._parse_fs_doc({"name": "projects/p/databases/d/documents/alert_events/ev1", "fields": fields})
        self.assertEqual(parsed["_id"], "ev1")
        self.assertEqual(parsed["regionName"], "Кривоозерська громада")
        self.assertTrue(parsed["isPublic"])
        self.assertEqual(parsed["activeLevels"], ["STREET_FIGHTS", "CIVIL_DEFENSE"])
        self.assertEqual(parsed["clientTimestampMs"], 1731570000000)

    def test_trigger_all_ukraine_alert_and_v2_map_polygons(self):
        self.assertGreaterEqual(len(pc.UKRAINE_OBLAST_POLYGONS), 24)
        self.assertTrue(pc.APP_VERSION.startswith("2."))

        store = pc.AlertStateStore(storage_path=self.state_path, enable_network=False)
        self._sign_in_admin(store)

        res = store.trigger_all_ukraine_alert(level="DRONE")
        self.assertIsNotNone(res)
        lvls, exp = res
        self.assertEqual(lvls, ["DRONE"])
        self.assertEqual(exp, [])
        self.assertTrue(all(r["currentLevel"] == "DRONE" for r in store.regions))
        self.assertEqual(store.latest_broadcast["regionName"], "ВСЯ ТЕРИТОРІЯ УКРАЇНИ")

        # Verify persistence on restart
        reloaded = pc.AlertStateStore(storage_path=self.state_path, enable_network=False)
        self.assertTrue(all(r["currentLevel"] == "DRONE" for r in reloaded.regions))


if __name__ == "__main__":
    unittest.main()
