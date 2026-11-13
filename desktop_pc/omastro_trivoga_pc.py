"""
Омастротривога — Настільна версія для Windows ПК (.exe)
Повністю сумісна у реальному часі з Android-додатком через спільний онлайн-канал та API тривог України.
"""

from __future__ import annotations

import json
import os
import queue
import re
import subprocess
import sys
import threading
import time
import tkinter as tk
from tkinter import messagebox, scrolledtext, ttk
import urllib.error
import urllib.request
import uuid
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple


APP_TITLE = "Омастротривога — ПК версія"
KRYVE_OZERO_ID = "kryve_ozero"
DEFAULT_PIN = "1234"
DEFAULT_ONLINE_CHANNEL = "omastrotryvoga-kryveozero-live-v1"
UBILLING_ALERTS_URL = "https://ubilling.net.ua/aerialalerts/"
NTFY_BASE_URL = "https://ntfy.sh"
ADMIN_OWNER_EMAIL = "denispekar12@gmail.com"
ADMIN_OWNER_NAME = "Денис"
ADMIN_OVERRIDE_GRACE_MS = 30 * 60 * 1000


ALERT_LEVELS = {
    "SAFE": {
        "title": "Відбій повітряної тривоги",
        "short": "Відбій / Спокійно",
        "color": "#22C55E",
        "bg": "#0D2B1C",
        "audio": "alert_all_clear.m4a",
        "danger": False,
        "priority": 0,
    },
    "YELLOW": {
        "title": "Жовтий рівень небезпеки",
        "short": "Жовтий рівень",
        "color": "#FFD500",
        "bg": "#382906",
        "audio": "alert_yellow.m4a",
        "danger": True,
        "priority": 1,
    },
    "CIVIL_DEFENSE": {
        "title": "Важливе сповіщення цивільного захисту",
        "short": "Цивільний захист",
        "color": "#A855F7",
        "bg": "#3E124A",
        "audio": "alert_civil_defense.m4a",
        "danger": True,
        "priority": 2,
    },
    "RED": {
        "title": "Повітряна тривога",
        "short": "Повітряна тривога",
        "color": "#FF2A3B",
        "bg": "#450D15",
        "audio": "alert_air_raid.m4a",
        "danger": True,
        "priority": 3,
    },
    "STREET_FIGHTS": {
        "title": "Увага! Вуличні бої",
        "short": "Вуличні бої",
        "color": "#FF7A00",
        "bg": "#4D2207",
        "audio": "alert_street_fights.m4a",
        "danger": True,
        "priority": 4,
    },
}

DANGER_ORDER = ["RED", "YELLOW", "STREET_FIGHTS", "CIVIL_DEFENSE"]


def primary_of(levels: Set[str]) -> str:
    dangers = [lv for lv in levels if ALERT_LEVELS.get(lv, {}).get("danger", False)]
    if not dangers:
        return "SAFE"
    return max(dangers, key=lambda lv: ALERT_LEVELS[lv]["priority"])


def sorted_active(levels: Set[str]) -> List[str]:
    dangers = [lv for lv in levels if ALERT_LEVELS.get(lv, {}).get("danger", False)]
    return sorted(dangers, key=lambda lv: ALERT_LEVELS[lv]["priority"], reverse=True)


def sanitize_channel_id(raw: str) -> str:
    cleaned = re.sub(r"[^a-z0-9_-]", "-", raw.strip().lower()).strip("-")
    return cleaned[:64] or DEFAULT_ONLINE_CHANNEL


def resolve_audio_path(filename: str) -> Optional[Path]:
    candidates: List[Path] = []
    if hasattr(sys, "_MEIPASS"):
        meipass = Path(getattr(sys, "_MEIPASS"))
        candidates.append(meipass / "assets" / "audio" / filename)
        candidates.append(meipass / filename)
    here = Path(__file__).resolve().parent
    candidates.append(here / "assets" / "audio" / filename)
    candidates.append(here.parent / "app" / "src" / "main" / "res" / "raw" / filename)
    for path in candidates:
        if path.is_file():
            return path
    return None


def get_storage_path() -> Path:
    appdata = os.environ.get("APPDATA")
    if appdata:
        base = Path(appdata) / "OmastroTrivoga"
    else:
        base = Path.home() / ".omastro_trivoga"
    base.mkdir(parents=True, exist_ok=True)
    return base / "state.json"


def initial_regions_template() -> List[dict]:
    now_ms = int(time.time() * 1000)
    return [
        {
            "id": KRYVE_OZERO_ID,
            "name": "Кривоозерська громада (Криве Озеро)",
            "oblast": "Миколаївська область",
            "isHromada": True,
            "isMonitored": True,
            "currentLevel": "SAFE",
            "activeAlerts": [],
            "alertStartTime": None,
            "threatDetails": "Рідна громада під постійним моніторингом",
            "mapNormX": 0.47,
            "mapNormY": 0.65,
        },
        {"id": "mykolaiv", "name": "Миколаївська область", "oblast": "Миколаївська область", "isHromada": False, "isMonitored": True, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.52, "mapNormY": 0.70},
        {"id": "kyiv_city", "name": "м. Київ", "oblast": "м. Київ", "isHromada": False, "isMonitored": True, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.48, "mapNormY": 0.35},
        {"id": "kyiv_obl", "name": "Київська область", "oblast": "Київська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.48, "mapNormY": 0.31},
        {"id": "odesa", "name": "Одеська область", "oblast": "Одеська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.40, "mapNormY": 0.75},
        {"id": "kharkiv", "name": "Харківська область", "oblast": "Харківська область", "isHromada": False, "isMonitored": False, "currentLevel": "RED", "activeAlerts": ["RED"], "alertStartTime": now_ms - 15 * 60 * 1000, "threatDetails": "Загроза ударних БПЛА та КАБ", "mapNormX": 0.75, "mapNormY": 0.38},
        {"id": "dnipro", "name": "Дніпропетровська область", "oblast": "Дніпропетровська область", "isHromada": False, "isMonitored": False, "currentLevel": "YELLOW", "activeAlerts": ["YELLOW"], "alertStartTime": now_ms - 8 * 60 * 1000, "threatDetails": "Активність тактичної авіації", "mapNormX": 0.64, "mapNormY": 0.55},
        {"id": "zaporizhzhia", "name": "Запорізька область", "oblast": "Запорізька область", "isHromada": False, "isMonitored": False, "currentLevel": "RED", "activeAlerts": ["RED"], "alertStartTime": now_ms - 22 * 60 * 1000, "threatDetails": "Артилерійський обстріл та балістика", "mapNormX": 0.68, "mapNormY": 0.68},
        {"id": "kherson", "name": "Херсонська область", "oblast": "Херсонська область", "isHromada": False, "isMonitored": False, "currentLevel": "RED", "activeAlerts": ["RED"], "alertStartTime": now_ms - 35 * 60 * 1000, "threatDetails": "Загроза обстрілів прибережних зон", "mapNormX": 0.58, "mapNormY": 0.78},
        {"id": "vinnytsia", "name": "Вінницька область", "oblast": "Вінницька область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.38, "mapNormY": 0.50},
        {"id": "cherkasy", "name": "Черкаська область", "oblast": "Черкаська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.50, "mapNormY": 0.46},
        {"id": "poltava", "name": "Полтавська область", "oblast": "Полтавська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.62, "mapNormY": 0.42},
        {"id": "sumy", "name": "Сумська область", "oblast": "Сумська область", "isHromada": False, "isMonitored": False, "currentLevel": "RED", "activeAlerts": ["RED"], "alertStartTime": now_ms - 40 * 60 * 1000, "threatDetails": "КАБ по прикордонних громадах", "mapNormX": 0.66, "mapNormY": 0.26},
        {"id": "chernihiv", "name": "Чернігівська область", "oblast": "Чернігівська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.52, "mapNormY": 0.22},
        {"id": "zhytomyr", "name": "Житомирська область", "oblast": "Житомирська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.38, "mapNormY": 0.34},
        {"id": "rivne", "name": "Рівненська область", "oblast": "Рівненська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.28, "mapNormY": 0.30},
        {"id": "volyn", "name": "Волинська область", "oblast": "Волинська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.18, "mapNormY": 0.28},
        {"id": "lviv", "name": "Львівська область", "oblast": "Львівська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.16, "mapNormY": 0.42},
        {"id": "khmelnytskyi", "name": "Хмельницька область", "oblast": "Хмельницька область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.30, "mapNormY": 0.46},
        {"id": "ternopil", "name": "Тернопільська область", "oblast": "Тернопільська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.24, "mapNormY": 0.48},
        {"id": "ivano_frankivsk", "name": "Івано-Франківська область", "oblast": "Івано-Франківська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.19, "mapNormY": 0.56},
        {"id": "zakarpattia", "name": "Закарпатська область", "oblast": "Закарпатська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.12, "mapNormY": 0.58},
        {"id": "chernivtsi", "name": "Чернівецька область", "oblast": "Чернівецька область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.28, "mapNormY": 0.58},
        {"id": "kirovohrad", "name": "Кіровоградська область", "oblast": "Кіровоградська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.54, "mapNormY": 0.54},
        {"id": "donetsk", "name": "Донецька область", "oblast": "Донецька область", "isHromada": False, "isMonitored": False, "currentLevel": "RED", "activeAlerts": ["RED"], "alertStartTime": now_ms - 120 * 60 * 1000, "threatDetails": "Постійна загроза бойових дій та ракет", "mapNormX": 0.82, "mapNormY": 0.58},
        {"id": "luhansk", "name": "Луганська область", "oblast": "Луганська область", "isHromada": False, "isMonitored": False, "currentLevel": "RED", "activeAlerts": ["RED"], "alertStartTime": now_ms - 300 * 60 * 1000, "threatDetails": "Тривала повітряна тривога", "mapNormX": 0.90, "mapNormY": 0.44},
        {"id": "crimea", "name": "АР Крим", "oblast": "АР Крим", "isHromada": False, "isMonitored": False, "currentLevel": "RED", "activeAlerts": ["RED"], "alertStartTime": now_ms - 300 * 60 * 1000, "threatDetails": "Тривога діє постійно", "mapNormX": 0.62, "mapNormY": 0.92},
    ]


class DesktopAudioPlayer:
    """Plays .m4a voice alerts sequentially, each EXACTLY ONCE without looping."""

    def __init__(self, on_status_change):
        self.on_status_change = on_status_change
        self._lock = threading.Lock()
        self._stop_flag = threading.Event()
        self._current_proc: Optional[subprocess.Popen] = None
        self._play_thread: Optional[threading.Thread] = None

    def play_sequence(self, levels: List[str], volume: float = 1.0, auto_play: bool = True) -> None:
        if not auto_play or not levels:
            return
        unique_levels = list(dict.fromkeys(levels))
        self.stop()
        self._stop_flag.clear()
        self._play_thread = threading.Thread(
            target=self._run_sequence,
            args=(unique_levels, volume),
            daemon=True,
        )
        self._play_thread.start()

    def preview_file(self, filename: str, title: str, volume: float = 1.0) -> None:
        self.stop()
        self._stop_flag.clear()
        self._play_thread = threading.Thread(
            target=self._run_single_file,
            args=(filename, f"Тест: {title}", volume),
            daemon=True,
        )
        self._play_thread.start()

    def _run_sequence(self, levels: List[str], volume: float) -> None:
        try:
            for lv in levels:
                if self._stop_flag.is_set():
                    break
                info = ALERT_LEVELS.get(lv)
                if not info:
                    continue
                path = resolve_audio_path(info["audio"])
                if path is None:
                    continue
                self.on_status_change(True, info["title"])
                self._play_path_once_blocking(path, volume)
        finally:
            self.on_status_change(False, "")

    def _run_single_file(self, filename: str, title: str, volume: float) -> None:
        try:
            path = resolve_audio_path(filename)
            if path is None:
                return
            self.on_status_change(True, title)
            self._play_path_once_blocking(path, volume)
        finally:
            self.on_status_change(False, "")

    def _play_path_once_blocking(self, path: Path, volume: float) -> None:
        vol = max(0.0, min(1.0, float(volume)))
        if sys.platform.startswith("win"):
            ps_path = str(path).replace("'", "''")
            ps_script = (
                "Add-Type -AssemblyName PresentationCore; "
                "$p = New-Object System.Windows.Media.MediaPlayer; "
                f"$p.Open([Uri]'{ps_path}'); "
                f"$p.Volume = {vol:.2f}; "
                "$sw = [System.Diagnostics.Stopwatch]::StartNew(); "
                "while (-not $p.NaturalDuration.HasTimeSpan -and $sw.ElapsedMilliseconds -lt 4000) { Start-Sleep -Milliseconds 50 }; "
                "$p.Play(); "
                "if ($p.NaturalDuration.HasTimeSpan) { "
                "$dur = [int]$p.NaturalDuration.TimeSpan.TotalMilliseconds + 400; "
                "$wait = [Math]::Min([Math]::Max($dur, 800), 45000); "
                "Start-Sleep -Milliseconds $wait "
                "} else { Start-Sleep -Milliseconds 6000 }; "
                "$p.Stop(); $p.Close()"
            )
            flags = 0x08000000  # CREATE_NO_WINDOW
            proc = subprocess.Popen(
                ["powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", ps_script],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                creationflags=flags,
            )
        else:
            # Fallback for testing on Linux/macOS
            player_cmd = None
            for tool in (["ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", str(path)], ["afplay", str(path)]):
                try:
                    proc = subprocess.Popen(tool, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    player_cmd = proc
                    break
                except FileNotFoundError:
                    continue
            if player_cmd is None:
                time.sleep(0.5)
                return
            proc = player_cmd

        with self._lock:
            self._current_proc = proc

        while proc.poll() is None:
            if self._stop_flag.is_set():
                try:
                    proc.terminate()
                except Exception:
                    pass
                break
            time.sleep(0.08)

        with self._lock:
            if self._current_proc is proc:
                self._current_proc = None

    def stop(self) -> None:
        self._stop_flag.set()
        with self._lock:
            proc = self._current_proc
            self._current_proc = None
        if proc is not None and proc.poll() is None:
            try:
                proc.terminate()
            except Exception:
                pass
        self.on_status_change(False, "")


def show_windows_notification(title: str, body: str) -> None:
    if not sys.platform.startswith("win"):
        return

    def _dispatch():
        safe_title = title.replace("'", "''")[:120]
        safe_body = body.replace("'", "''").replace("\n", " ")[:240]
        ps = (
            "Add-Type -AssemblyName System.Windows.Forms; "
            "Add-Type -AssemblyName System.Drawing; "
            "$n = New-Object System.Windows.Forms.NotifyIcon; "
            "$n.Icon = [System.Drawing.SystemIcons]::Warning; "
            "$n.BalloonTipIcon = 'Warning'; "
            f"$n.BalloonTipTitle = '{safe_title}'; "
            f"$n.BalloonTipText = '{safe_body}'; "
            "$n.Visible = $true; "
            "$n.ShowBalloonTip(7000); "
            "Start-Sleep -Milliseconds 7500; "
            "$n.Dispose()"
        )
        try:
            subprocess.Popen(
                ["powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", ps],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                creationflags=0x08000000,
            )
        except Exception:
            pass

    threading.Thread(target=_dispatch, daemon=True).start()


class AlertStateStore:
    """Thread-safe repository with full JSON persistence and real-time online sync."""

    def __init__(self):
        self.lock = threading.RLock()
        self.storage_path = get_storage_path()
        self.device_id = str(uuid.uuid4())
        self.user_role = "ADMIN"
        self.admin_pin = DEFAULT_PIN
        self.author_name = ""
        self.selected_tab = 0
        self.selected_region_id = KRYVE_OZERO_ID
        self.online_channel_id = DEFAULT_ONLINE_CHANNEL
        self.dsns_auto_sync = True
        self.sound_volume = 1.0
        self.auto_play_voice = True
        self.last_sync_ts = 0
        self.is_online = False
        self.is_syncing = False
        self.status_message = "Онлайн • Підключення до ефіру..."
        self.processed_remote_ids: Set[str] = set()
        self.manual_override_ts: Dict[str, int] = {}

        self.regions: List[dict] = initial_regions_template()
        self.events: List[dict] = [
            {
                "id": "initial_ko_event",
                "regionName": "Кривоозерська громада",
                "level": "SAFE",
                "activeLevels": ["SAFE"],
                "timestamp": int(time.time() * 1000) - 10 * 60 * 1000,
                "threatDetails": "Офіційне сповіщення: ситуація спокійна",
                "source": "Адміністратор Денис",
            }
        ]
        self.latest_broadcast: Optional[dict] = None
        self.comments: List[dict] = [
            {
                "id": "admin_welcome",
                "authorName": "Денис (Адміністратор)",
                "authorRole": "ADMIN",
                "text": "Вітаю у прямому часі громади «Омастротривога»! Усі сповіщення з ПК та телефону синхронізуються онлайн.",
                "timestamp": int(time.time() * 1000) - 25 * 60 * 1000,
                "regionTag": "Кривоозерська громада",
                "isAdminVerified": True,
            }
        ]
        self._load()

    def _load(self) -> None:
        if not self.storage_path.is_file():
            self._save()
            return
        try:
            data = json.loads(self.storage_path.read_text(encoding="utf-8"))
            self.device_id = data.get("deviceId") or self.device_id
            self.user_role = data.get("userRole", "ADMIN")
            self.admin_pin = data.get("adminPin", DEFAULT_PIN)
            self.author_name = data.get("authorName", "")
            self.selected_tab = int(data.get("selectedTab", 0))
            self.selected_region_id = data.get("selectedRegionId", KRYVE_OZERO_ID)
            self.online_channel_id = sanitize_channel_id(data.get("onlineChannelId", DEFAULT_ONLINE_CHANNEL))
            self.dsns_auto_sync = bool(data.get("dsnsAutoSync", True))
            self.sound_volume = float(data.get("soundVolume", 1.0))
            self.auto_play_voice = bool(data.get("autoPlayVoice", True))
            self.last_sync_ts = int(data.get("lastSyncTs", 0))
            self.processed_remote_ids = set(data.get("processedRemoteIds", []))
            self.manual_override_ts = {
                str(k): int(v) for k, v in data.get("manualOverrideTs", {}).items()
            }

            saved_regions = {r["id"]: r for r in data.get("regions", []) if isinstance(r, dict) and "id" in r}
            merged = []
            for base in initial_regions_template():
                sv = saved_regions.get(base["id"])
                if sv:
                    active = [lv for lv in sv.get("activeAlerts", []) if ALERT_LEVELS.get(lv, {}).get("danger")]
                    if not active and ALERT_LEVELS.get(sv.get("currentLevel", ""), {}).get("danger"):
                        active = [sv["currentLevel"]]
                    base["isMonitored"] = bool(sv.get("isMonitored", base["isMonitored"]))
                    base["activeAlerts"] = sorted_active(set(active))
                    base["currentLevel"] = primary_of(set(active))
                    base["alertStartTime"] = sv.get("alertStartTime")
                    base["threatDetails"] = sv.get("threatDetails") or base["threatDetails"]
                merged.append(base)
            self.regions = merged

            if isinstance(data.get("events"), list) and data["events"]:
                self.events = data["events"][:50]
            if isinstance(data.get("comments"), list) and data["comments"]:
                self.comments = data["comments"][:60]
            self.latest_broadcast = data.get("latestBroadcast")
        except Exception:
            pass

    def _save(self) -> None:
        with self.lock:
            payload = {
                "deviceId": self.device_id,
                "userRole": self.user_role,
                "adminPin": self.admin_pin,
                "authorName": self.author_name,
                "selectedTab": self.selected_tab,
                "selectedRegionId": self.selected_region_id,
                "onlineChannelId": self.online_channel_id,
                "dsnsAutoSync": self.dsns_auto_sync,
                "soundVolume": self.sound_volume,
                "autoPlayVoice": self.auto_play_voice,
                "lastSyncTs": self.last_sync_ts,
                "processedRemoteIds": list(self.processed_remote_ids)[-200:],
                "manualOverrideTs": self.manual_override_ts,
                "regions": self.regions,
                "events": self.events[:50],
                "comments": self.comments[:60],
                "latestBroadcast": self.latest_broadcast,
            }
            try:
                self.storage_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
            except Exception:
                pass

    def get_region(self, region_id: str) -> Optional[dict]:
        with self.lock:
            for reg in self.regions:
                if reg["id"] == region_id:
                    return reg
        return None

    def set_region_active_alerts(
        self,
        region_id: str,
        levels: Set[str],
        note: str = "",
        publish_online: bool = True,
        emit_broadcast: bool = True,
    ) -> Optional[Tuple[dict, List[str]]]:
        now_ms = int(time.time() * 1000)
        clean_danger = {lv for lv in levels if ALERT_LEVELS.get(lv, {}).get("danger")}
        ordered = sorted_active(clean_danger)
        primary = primary_of(clean_danger)

        with self.lock:
            self.manual_override_ts[region_id] = now_ms
            target = None
            for reg in self.regions:
                if reg["id"] == region_id:
                    was_danger = bool(reg.get("activeAlerts")) or ALERT_LEVELS.get(reg["currentLevel"], {}).get("danger")
                    is_danger = bool(clean_danger)
                    start_time = now_ms if (is_danger and not was_danger) else (None if not is_danger else (reg.get("alertStartTime") or now_ms))
                    if note.strip():
                        resolved_note = note.strip()
                    elif len(ordered) > 1:
                        resolved_note = "Одночасно: " + " + ".join(ALERT_LEVELS[lv]["short"] for lv in ordered)
                    elif primary == "RED":
                        resolved_note = "Повітряна тривога! Перейдіть в укриття!"
                    elif primary == "YELLOW":
                        resolved_note = "Жовтий рівень небезпеки. Підвищена увага."
                    elif primary == "STREET_FIGHTS":
                        resolved_note = "Вуличні бої! Залишайтесь в укритті!"
                    elif primary == "CIVIL_DEFENSE":
                        resolved_note = "Важливе сповіщення цивільного захисту! Ознайомтеся з додатком Омастротривога."
                    else:
                        resolved_note = "Відбій загрози. Спокійно."

                    reg["currentLevel"] = primary
                    reg["activeAlerts"] = ordered
                    reg["alertStartTime"] = start_time
                    reg["threatDetails"] = resolved_note
                    target = dict(reg)
                    break

            if target is None:
                return None

            levels_display = ordered if ordered else ["SAFE"]
            if emit_broadcast:
                self.latest_broadcast = {
                    "id": str(uuid.uuid4()),
                    "regionName": target["name"],
                    "level": primary,
                    "activeLevels": levels_display,
                    "timestamp": now_ms,
                    "note": target["threatDetails"],
                    "adminName": ADMIN_OWNER_NAME,
                }

            self.events.insert(
                0,
                {
                    "id": str(uuid.uuid4()),
                    "regionName": target["name"],
                    "level": primary,
                    "activeLevels": levels_display,
                    "timestamp": now_ms,
                    "threatDetails": target["threatDetails"],
                    "source": f"Адміністратор {ADMIN_OWNER_NAME} (ПК)",
                },
            )
            self.events = self.events[:50]
            self._save()

        if publish_online:
            event_id = str(uuid.uuid4())
            self._mark_remote_processed(event_id)
            title = (
                f"🚨 Кілька тривог ({len(ordered)}) — {target['name']}"
                if len(ordered) > 1
                else f"{ALERT_LEVELS[primary]['title']} — {target['name']}"
            )
            envelope = {
                "type": "REGION_ALERT",
                "eventId": event_id,
                "senderDeviceId": self.device_id,
                "regionId": target["id"],
                "regionName": target["name"],
                "level": primary,
                "activeLevels": ordered,
                "note": target["threatDetails"],
                "adminName": ADMIN_OWNER_NAME,
                "timestamp": now_ms,
            }
            self._publish_online_async(title, envelope, 5 if clean_danger else 3)

        return target, levels_display

    def toggle_alert_in_region(self, region_id: str, level: str) -> Optional[Tuple[dict, List[str]]]:
        reg = self.get_region(region_id)
        if not reg:
            return None
        if not ALERT_LEVELS.get(level, {}).get("danger"):
            return self.set_region_active_alerts(region_id, set())
        existing = set(reg.get("activeAlerts", []))
        if level in existing:
            existing.remove(level)
        else:
            existing.add(level)
        return self.set_region_active_alerts(region_id, existing)

    def set_multiple_regions_alerts(self, region_ids: List[str], levels: Set[str], note: str = "") -> Optional[Tuple[dict, List[str]]]:
        result = None
        for idx, rid in enumerate(region_ids):
            res = self.set_region_active_alerts(
                rid,
                levels,
                note=note,
                publish_online=True,
                emit_broadcast=(idx == len(region_ids) - 1),
            )
            if idx == 0:
                result = res
        return result

    def trigger_total_all_clear(self) -> List[str]:
        now_ms = int(time.time() * 1000)
        note = "Тотальний відбій для всієї території України! Небезпека минула."
        with self.lock:
            for reg in self.regions:
                self.manual_override_ts[reg["id"]] = now_ms
                reg["currentLevel"] = "SAFE"
                reg["activeAlerts"] = []
                reg["alertStartTime"] = None
                reg["threatDetails"] = note

            self.latest_broadcast = {
                "id": str(uuid.uuid4()),
                "regionName": "Вся територія України (ВЕСЬ ВІДБІЙ)",
                "level": "SAFE",
                "activeLevels": ["SAFE"],
                "timestamp": now_ms,
                "note": note,
                "adminName": ADMIN_OWNER_NAME,
            }
            self.events.insert(
                0,
                {
                    "id": str(uuid.uuid4()),
                    "regionName": "Вся Україна (Тотальний відбій)",
                    "level": "SAFE",
                    "activeLevels": ["SAFE"],
                    "timestamp": now_ms,
                    "threatDetails": note,
                    "source": f"Адміністратор {ADMIN_OWNER_NAME} (ПК)",
                },
            )
            self.events = self.events[:50]
            self._save()

        event_id = str(uuid.uuid4())
        self._mark_remote_processed(event_id)
        envelope = {
            "type": "TOTAL_CLEAR",
            "eventId": event_id,
            "senderDeviceId": self.device_id,
            "note": note,
            "adminName": ADMIN_OWNER_NAME,
            "timestamp": now_ms,
        }
        self._publish_online_async("🟢 ВЕСЬ ВІДБІЙ — Вся Україна", envelope, 4)
        return ["SAFE"]

    def trigger_street_fights_all_clear(self, region_id: str = KRYVE_OZERO_ID) -> Optional[Tuple[dict, List[str]]]:
        reg = self.get_region(region_id)
        remaining = {lv for lv in (reg.get("activeAlerts", []) if reg else []) if lv != "STREET_FIGHTS"}
        note = (
            "Відбій загрози вуличних боїв! Ситуація в громаді стабілізована та під повним контролем."
            if not remaining
            else "Відбій вуличних боїв. Залишаються активними: " + ", ".join(ALERT_LEVELS[lv]["short"] for lv in sorted_active(remaining))
        )
        return self.set_region_active_alerts(region_id, remaining, note=note)

    def post_comment(self, text: str, author_name: str, region_tag: str = "Кривоозерська громада") -> None:
        trimmed = text.strip()
        if not trimmed:
            return
        now_ms = int(time.time() * 1000)
        is_admin = self.user_role == "ADMIN"
        if is_admin:
            final_author = "Денис (Адміністратор)"
        else:
            final_author = author_name.strip() or "Житель громади"
            if author_name.strip():
                self.author_name = author_name.strip()

        cid = str(uuid.uuid4())
        comment = {
            "id": cid,
            "authorName": final_author,
            "authorRole": self.user_role,
            "text": trimmed,
            "timestamp": now_ms,
            "regionTag": region_tag,
            "isAdminVerified": is_admin,
        }
        with self.lock:
            self.comments.insert(0, comment)
            self.comments = self.comments[:60]
            self._save()

        self._mark_remote_processed(cid)
        envelope = {
            "type": "COMMENT",
            "eventId": cid,
            "senderDeviceId": self.device_id,
            "authorName": final_author,
            "authorRole": self.user_role,
            "text": trimmed,
            "regionTag": region_tag,
            "isAdminVerified": is_admin,
            "timestamp": now_ms,
        }
        self._publish_online_async(f"💬 {final_author}", envelope, 3)

    def _mark_remote_processed(self, event_id: str) -> bool:
        with self.lock:
            if event_id in self.processed_remote_ids:
                return False
            self.processed_remote_ids.add(event_id)
            if len(self.processed_remote_ids) > 250:
                self.processed_remote_ids = set(list(self.processed_remote_ids)[-150:])
            self._save()
            return True

    def _publish_online_async(self, title: str, envelope: dict, priority: int) -> None:
        def _send():
            try:
                body = json.dumps(
                    {
                        "topic": sanitize_channel_id(self.online_channel_id),
                        "title": title,
                        "message": json.dumps(envelope, ensure_ascii=False),
                        "priority": priority,
                    },
                    ensure_ascii=False,
                ).encode("utf-8")
                req = urllib.request.Request(
                    f"{NTFY_BASE_URL}/",
                    data=body,
                    headers={"Content-Type": "application/json; charset=utf-8", "User-Agent": "OmastroTrivoga-PC/1.0"},
                    method="POST",
                )
                with urllib.request.urlopen(req, timeout=6):
                    with self.lock:
                        self.is_online = True
                        self.last_sync_ts = int(time.time() * 1000)
                        self.status_message = "Онлайн • Надіслано в ефір"
                        self._save()
            except Exception:
                with self.lock:
                    self.is_online = False
                    self.status_message = "Офлайн • Збережено локально"

        threading.Thread(target=_send, daemon=True).start()

    def sync_now(self) -> List[Tuple[str, List[str], str]]:
        """Polls ntfy.sh live channel + official Ukraine API. Returns list of recent audio/notification triggers."""
        triggers: List[Tuple[str, List[str], str]] = []
        with self.lock:
            if self.is_syncing:
                return triggers
            self.is_syncing = True

        any_ok = False
        try:
            # 1. Poll live pub/sub channel
            try:
                ch = sanitize_channel_id(self.online_channel_id)
                req = urllib.request.Request(
                    f"{NTFY_BASE_URL}/{ch}/json?poll=1&since=12h",
                    headers={"User-Agent": "OmastroTrivoga-PC/1.0"},
                )
                with urllib.request.urlopen(req, timeout=7) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                any_ok = True
                for line in raw.splitlines():
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        obj = json.loads(line)
                        if obj.get("event") != "message":
                            continue
                        remote_id = obj.get("id", "")
                        msg_raw = obj.get("message", "")
                        if not msg_raw.startswith("{"):
                            continue
                        env = json.loads(msg_raw)
                        event_id = env.get("eventId") or remote_id
                        if not event_id:
                            continue
                        if env.get("senderDeviceId") == self.device_id:
                            self._mark_remote_processed(event_id)
                            continue
                        if not self._mark_remote_processed(event_id):
                            continue
                        t = env.get("type", "")
                        ts = int(env.get("timestamp") or obj.get("time", 0) * 1000)
                        is_recent = (int(time.time() * 1000) - ts) < 20 * 60 * 1000

                        if t == "REGION_ALERT":
                            rid = env.get("regionId", KRYVE_OZERO_ID)
                            raw_active = env.get("activeLevels") or []
                            if not raw_active and env.get("level") in ALERT_LEVELS and ALERT_LEVELS[env["level"]]["danger"]:
                                raw_active = [env["level"]]
                            res = self.set_region_active_alerts(
                                rid,
                                set(raw_active),
                                note=env.get("note", ""),
                                publish_online=False,
                                emit_broadcast=True,
                            )
                            if res and is_recent:
                                reg, lvls = res
                                triggers.append((reg["name"], lvls, reg["threatDetails"]))

                        elif t == "TOTAL_CLEAR":
                            with self.lock:
                                for reg in self.regions:
                                    self.manual_override_ts[reg["id"]] = ts
                                    reg["currentLevel"] = "SAFE"
                                    reg["activeAlerts"] = []
                                    reg["alertStartTime"] = None
                                    reg["threatDetails"] = env.get("note", "Тотальний відбій для всієї України!")
                                self.latest_broadcast = {
                                    "id": event_id,
                                    "regionName": "Вся територія України (ВЕСЬ ВІДБІЙ)",
                                    "level": "SAFE",
                                    "activeLevels": ["SAFE"],
                                    "timestamp": ts,
                                    "note": env.get("note", ""),
                                    "adminName": env.get("adminName", ADMIN_OWNER_NAME),
                                }
                                self._save()
                            if is_recent:
                                triggers.append(("Вся Україна", ["SAFE"], env.get("note", "Весь відбій")))

                        elif t == "COMMENT":
                            text = (env.get("text") or "").strip()
                            if text:
                                with self.lock:
                                    if not any(c["id"] == event_id for c in self.comments):
                                        self.comments.insert(
                                            0,
                                            {
                                                "id": event_id,
                                                "authorName": env.get("authorName", "Житель громади"),
                                                "authorRole": env.get("authorRole", "USER"),
                                                "text": text,
                                                "timestamp": ts,
                                                "regionTag": env.get("regionTag", "Кривоозерська громада"),
                                                "isAdminVerified": bool(env.get("isAdminVerified", False)),
                                            },
                                        )
                                        self.comments = sorted(self.comments, key=lambda c: c["timestamp"], reverse=True)[:60]
                                        self._save()
                    except Exception:
                        continue
            except Exception:
                pass

            # 2. Fetch official Ukraine alerts API
            if self.dsns_auto_sync:
                try:
                    req = urllib.request.Request(
                        UBILLING_ALERTS_URL,
                        headers={"User-Agent": "OmastroTrivoga-PC/1.0", "Accept": "application/json"},
                    )
                    with urllib.request.urlopen(req, timeout=7) as resp:
                        root = json.loads(resp.read().decode("utf-8", errors="replace"))
                    states = root.get("states", {})
                    if isinstance(states, dict) and states:
                        any_ok = True
                        now_ms = int(time.time() * 1000)
                        changed_monitored = []
                        with self.lock:
                            for reg in self.regions:
                                ov = self.manual_override_ts.get(reg["id"], 0)
                                is_special = any(lv in ("STREET_FIGHTS", "CIVIL_DEFENSE") for lv in reg.get("activeAlerts", []))
                                if (ov > 0 and (now_ms - ov) < ADMIN_OVERRIDE_GRACE_MS) or is_special:
                                    continue
                                info = states.get(reg["oblast"]) or states.get(reg["name"])
                                if not info and reg["id"] == "crimea":
                                    info = states.get("Автономна Республіка Крим")
                                if not isinstance(info, dict):
                                    continue
                                alert_now = bool(info.get("alertnow", False))
                                target_lvl = "RED" if alert_now else "SAFE"
                                target_set = ["RED"] if alert_now else []
                                if reg["currentLevel"] != target_lvl or reg.get("activeAlerts") != target_set:
                                    reg["currentLevel"] = target_lvl
                                    reg["activeAlerts"] = target_set
                                    reg["alertStartTime"] = now_ms if alert_now else None
                                    reg["threatDetails"] = (
                                        "Офіційна повітряна тривога (Онлайн API ДСНС)"
                                        if alert_now
                                        else "Відбій повітряної тривоги (Онлайн API ДСНС)"
                                    )
                                    if reg.get("isMonitored"):
                                        changed_monitored.append((reg["name"], [target_lvl], reg["threatDetails"]))
                            if changed_monitored:
                                for rname, lvls, det in changed_monitored:
                                    self.events.insert(
                                        0,
                                        {
                                            "id": str(uuid.uuid4()),
                                            "regionName": rname,
                                            "level": lvls[0],
                                            "activeLevels": lvls,
                                            "timestamp": now_ms,
                                            "threatDetails": det,
                                            "source": "ДСНС / Онлайн API",
                                        },
                                    )
                                self.events = self.events[:50]
                                self._save()
                                triggers.append(changed_monitored[0])
                except Exception:
                    pass

            with self.lock:
                if any_ok:
                    self.is_online = True
                    self.last_sync_ts = int(time.time() * 1000)
                    self.status_message = "Онлайн • ДСНС + Прямий ефір" if self.dsns_auto_sync else "Онлайн • Прямий ефір"
                else:
                    self.is_online = False
                    self.status_message = "Офлайн • Збережено локально"
                self._save()
        finally:
            with self.lock:
                self.is_syncing = False

        return triggers


class OmastroTrivogaDesktopApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(APP_TITLE)
        self.geometry("1120x780")
        self.minsize(920, 660)
        self.configure(bg="#090B10")

        self.store = AlertStateStore()
        self.ui_queue: queue.Queue = queue.Queue()
        self.audio_player = DesktopAudioPlayer(self._on_audio_status_changed)

        self.active_tab = self.store.selected_tab
        ko_init = self.store.get_region(KRYVE_OZERO_ID)
        init_picks = set(ko_init.get("activeAlerts", [])) if ko_init else set()
        if not init_picks:
            init_picks = {"STREET_FIGHTS", "CIVIL_DEFENSE"}
        self.multi_ko_vars: Dict[str, tk.BooleanVar] = {
            lv: tk.BooleanVar(value=(lv in init_picks)) for lv in DANGER_ORDER
        }
        self.admin_region_multi_vars: Dict[str, tk.BooleanVar] = {}
        self.map_filter = "ALL"

        self._build_styles()
        self._build_shell()
        self._select_tab(self.active_tab, save=False)

        self.after(150, self._process_ui_queue)
        self._start_background_sync_loop()

    def _build_styles(self) -> None:
        style = ttk.Style(self)
        style.theme_use("clam")
        style.configure("Dark.TFrame", background="#090B10")
        style.configure("Card.TFrame", background="#131826")
        style.configure("Dark.TLabel", background="#090B10", foreground="#F3F4F6", font=("Segoe UI", 10))

    def _build_shell(self) -> None:
        # Top Header
        header = tk.Frame(self, bg="#0C0F17", height=64, padx=16, pady=10)
        header.pack(fill="x")

        brand_frame = tk.Frame(header, bg="#0C0F17")
        brand_frame.pack(side="left")

        badge = tk.Label(brand_frame, text="ОТ", bg="#0057B7", fg="#FFD500", font=("Segoe UI", 13, "bold"), width=3, pady=4)
        badge.pack(side="left", padx=(0, 10))

        title_col = tk.Frame(brand_frame, bg="#0C0F17")
        title_col.pack(side="left")

        title_row = tk.Frame(title_col, bg="#0C0F17")
        title_row.pack(anchor="w")
        tk.Label(title_row, text="ОМАСТРО", bg="#0C0F17", fg="#FFFFFF", font=("Segoe UI", 14, "bold")).pack(side="left")
        tk.Label(title_row, text="ТРИВОГА ПК", bg="#0C0F17", fg="#FF2A3B", font=("Segoe UI", 14, "bold")).pack(side="left")

        self.lbl_online = tk.Label(title_col, text=self.store.status_message, bg="#0C0F17", fg="#9CA3AF", font=("Segoe UI", 9))
        self.lbl_online.pack(anchor="w")

        right_hdr = tk.Frame(header, bg="#0C0F17")
        right_hdr.pack(side="right")

        self.btn_stop_audio = tk.Button(
            right_hdr,
            text="🔇 Зупинити звук",
            bg="#FF2A3B",
            fg="#FFFFFF",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=10,
            pady=5,
            command=self.audio_player.stop,
        )

        self.btn_sync = tk.Button(
            right_hdr,
            text="🔄 Оновити онлайн",
            bg="#1A2236",
            fg="#22C55E",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=10,
            pady=5,
            command=self._trigger_manual_sync,
        )
        self.btn_sync.pack(side="left", padx=6)

        self.btn_role = tk.Button(
            right_hdr,
            text="",
            bg="#1E283E",
            fg="#FFD500",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=12,
            pady=5,
            command=self._on_role_button_click,
        )
        self.btn_role.pack(side="left")
        self._update_header_badges()

        # Audio Status Banner
        self.audio_banner = tk.Frame(self, bg="#3B0B13", padx=16, pady=6)
        self.lbl_audio_playing = tk.Label(self.audio_banner, text="", bg="#3B0B13", fg="#FFFFFF", font=("Segoe UI", 10, "bold"))
        self.lbl_audio_playing.pack(side="left")

        # Navigation Bar
        nav = tk.Frame(self, bg="#0D101A", padx=16, pady=8)
        nav.pack(fill="x")
        self.nav_buttons: List[tk.Button] = []
        tabs = [
            ("🚨 Тривога", 0),
            ("🗺️ Мапа України", 1),
            ("💬 Обговорення", 2),
            ("🔊 Звук", 3),
            ("🛡️ Адмін", 4),
        ]
        for label, idx in tabs:
            b = tk.Button(
                nav,
                text=label,
                font=("Segoe UI", 10, "bold"),
                relief="flat",
                padx=14,
                pady=7,
                command=lambda i=idx: self._select_tab(i, save=True),
            )
            b.pack(side="left", padx=(0, 8))
            self.nav_buttons.append(b)

        # Scrollable Content Host
        self.content_host = tk.Frame(self, bg="#090B10")
        self.content_host.pack(fill="both", expand=True, padx=16, pady=10)

    def _update_header_badges(self) -> None:
        is_admin = self.store.user_role == "ADMIN"
        self.btn_role.configure(
            text="👑 Адмін (Денис)" if is_admin else "🔒 Користувач (перегляд)",
            fg="#FFD500" if is_admin else "#E5E7EB",
            bg="#1E283E" if is_admin else "#181C26",
        )
        ts_str = ""
        if self.store.last_sync_ts > 0:
            ts_str = " • " + datetime.fromtimestamp(self.store.last_sync_ts / 1000.0).strftime("%H:%M:%S")
        self.lbl_online.configure(
            text=f"{self.store.status_message}{ts_str}",
            fg="#22C55E" if self.store.is_online else "#F87171",
        )

    def _select_tab(self, idx: int, save: bool = True) -> None:
        self.active_tab = idx
        if save:
            self.store.selected_tab = idx
            self.store._save()

        for i, btn in enumerate(self.nav_buttons):
            if i == idx:
                btn.configure(bg="#1E293B", fg="#FFD500")
            else:
                btn.configure(bg="#111622", fg="#9CA3AF")

        self._render_current_tab()

    def _render_current_tab(self) -> None:
        for w in self.content_host.winfo_children():
            w.destroy()

        if self.active_tab == 0:
            self._render_dashboard()
        elif self.active_tab == 1:
            self._render_map_tab()
        elif self.active_tab == 2:
            self._render_discussion_tab()
        elif self.active_tab == 3:
            self._render_audio_tab()
        else:
            self._render_admin_tab()

    def _render_dashboard(self) -> None:
        ko = self.store.get_region(KRYVE_OZERO_ID)
        active_alerts = ko.get("activeAlerts", []) if ko else []
        primary = ko["currentLevel"] if ko else "SAFE"
        lv_info = ALERT_LEVELS[primary]

        # Live Broadcast Banner
        bc = self.store.latest_broadcast
        if bc:
            bc_frame = tk.Frame(self.content_host, bg="#1B1726", highlightbackground="#FFD500", highlightthickness=1, padx=14, pady=10)
            bc_frame.pack(fill="x", pady=(0, 10))
            bc_levels = bc.get("activeLevels") or [bc["level"]]
            lv_titles = " + ".join(ALERT_LEVELS.get(x, {}).get("short", x) for x in bc_levels)
            tk.Label(bc_frame, text="📡 НАЖИВО ВІД АДМІНІСТРАТОРА (ДЕНИС)", bg="#1B1726", fg="#FFD500", font=("Segoe UI", 9, "bold")).pack(anchor="w")
            tk.Label(bc_frame, text=f"{lv_titles} — {bc['regionName']}", bg="#1B1726", fg="#FFFFFF", font=("Segoe UI", 11, "bold")).pack(anchor="w")
            tk.Label(bc_frame, text=bc.get("note", ""), bg="#1B1726", fg="#D1D5DB", font=("Segoe UI", 9)).pack(anchor="w")

        # Main Split: Left Hero & Multi-alert controls | Right Events
        cols = tk.Frame(self.content_host, bg="#090B10")
        cols.pack(fill="both", expand=True)

        left = tk.Frame(cols, bg="#090B10")
        left.pack(side="left", fill="both", expand=True, padx=(0, 10))

        hero = tk.Frame(left, bg=lv_info["bg"], highlightbackground=lv_info["color"], highlightthickness=2, padx=18, pady=16)
        hero.pack(fill="x", pady=(0, 12))

        badges_row = tk.Frame(hero, bg=lv_info["bg"])
        badges_row.pack(fill="x", pady=(0, 8))
        if not active_alerts:
            tk.Label(badges_row, text=lv_info["title"].upper(), bg="#113825", fg="#22C55E", font=("Segoe UI", 10, "bold"), padx=10, pady=4).pack(side="left")
        else:
            for lv in active_alerts:
                c = ALERT_LEVELS[lv]["color"]
                lbl = tk.Label(badges_row, text=ALERT_LEVELS[lv]["short"].upper(), bg="#0B0F1A", fg=c, font=("Segoe UI", 10, "bold"), padx=10, pady=4)
                lbl.pack(side="left", padx=(0, 6))
                if self.store.user_role == "ADMIN":
                    btn_x = tk.Button(
                        badges_row,
                        text="×",
                        bg="#0B0F1A",
                        fg=c,
                        relief="flat",
                        font=("Segoe UI", 10, "bold"),
                        command=lambda l=lv: self._toggle_ko_alert(l),
                    )
                    btn_x.pack(side="left", padx=(0, 8))

        tk.Label(hero, text="Кривоозерська громада (Криве Озеро)", bg=lv_info["bg"], fg="#FFFFFF", font=("Segoe UI", 16, "bold")).pack(anchor="w")
        tk.Label(
            hero,
            text=ko.get("threatDetails") or "Повітряна тривога відсутня. Обстановка спокійна.",
            bg=lv_info["bg"],
            fg="#E5E7EB",
            font=("Segoe UI", 11),
            wraplength=540,
            justify="left",
        ).pack(anchor="w", pady=(4, 10))

        if self.store.user_role == "ADMIN":
            tk.Label(hero, text="Швидке керування (звучить 1 раз у реальному часі):", bg=lv_info["bg"], fg="#FFD500", font=("Segoe UI", 9, "bold")).pack(anchor="w")
            btn_row = tk.Frame(hero, bg=lv_info["bg"])
            btn_row.pack(fill="x", pady=(6, 8))

            quick_buttons = [
                ("🚨 Тривога", "RED", "#FF2A3B", "#FFFFFF"),
                ("⚠️ Жовтий", "YELLOW", "#FFD500", "#000000"),
                ("🛑 Вуличні бої", "STREET_FIGHTS", "#FF7A00", "#FFFFFF"),
                ("📢 Цив. захист", "CIVIL_DEFENSE", "#A855F7", "#FFFFFF"),
            ]
            for txt, lv, bgc, fgc in quick_buttons:
                tk.Button(
                    btn_row,
                    text=txt,
                    bg=bgc,
                    fg=fgc,
                    font=("Segoe UI", 9, "bold"),
                    relief="flat",
                    padx=10,
                    pady=6,
                    command=lambda level=lv: self._trigger_single_ko(level),
                ).pack(side="left", padx=(0, 6))

            clear_row = tk.Frame(hero, bg=lv_info["bg"])
            clear_row.pack(fill="x", pady=(0, 10))
            tk.Button(
                clear_row,
                text="🟢 ВЕСЬ ВІДБІЙ (ВСЯ УКРАЇНА)",
                bg="#22C55E",
                fg="#000000",
                font=("Segoe UI", 9, "bold"),
                relief="flat",
                padx=12,
                pady=6,
                command=self._trigger_total_all_clear,
            ).pack(side="left", padx=(0, 8))
            tk.Button(
                clear_row,
                text="🛡️ Відбій вуличних боїв",
                bg="#1E3A2B",
                fg="#22C55E",
                font=("Segoe UI", 9, "bold"),
                relief="flat",
                padx=12,
                pady=6,
                command=self._trigger_street_fights_clear,
            ).pack(side="left")

            # Multi-alert builder right on Dashboard
            combo_box = tk.Frame(hero, bg="#0B0F1A", padx=12, pady=10)
            combo_box.pack(fill="x")
            tk.Label(
                combo_box,
                text="КОМБІНУВАТИ ДЕКІЛЬКА ТРИВОГ ОДНОЧАСНО (напр. Вуличні бої + Цивільний захист):",
                bg="#0B0F1A",
                fg="#FFD500",
                font=("Segoe UI", 9, "bold"),
            ).pack(anchor="w")

            checks_row = tk.Frame(combo_box, bg="#0B0F1A")
            checks_row.pack(fill="x", pady=6)
            default_picks = set(active_alerts) if active_alerts else {"STREET_FIGHTS", "CIVIL_DEFENSE"}
            for lv in DANGER_ORDER:
                var = self.multi_ko_vars.get(lv)
                if var is None:
                    var = tk.BooleanVar(value=(lv in default_picks))
                    self.multi_ko_vars[lv] = var
                cb = tk.Checkbutton(
                    checks_row,
                    text=ALERT_LEVELS[lv]["short"],
                    variable=var,
                    bg="#0B0F1A",
                    fg=ALERT_LEVELS[lv]["color"],
                    selectcolor="#182032",
                    activebackground="#0B0F1A",
                    font=("Segoe UI", 9, "bold"),
                )
                cb.pack(side="left", padx=(0, 10))

            tk.Button(
                combo_box,
                text="🚨 Оголосити вибрані тривоги одночасно",
                bg="#FFD500",
                fg="#000000",
                font=("Segoe UI", 10, "bold"),
                relief="flat",
                padx=14,
                pady=7,
                command=self._launch_multi_ko_from_vars,
            ).pack(fill="x")
        else:
            tk.Label(
                hero,
                text="🔒 Режим спостереження: ви автоматично отримуєте всі сигнали тривоги та відбою у реальному часі.",
                bg=lv_info["bg"],
                fg="#9CA3AF",
                font=("Segoe UI", 9),
            ).pack(anchor="w")

        # Monitored quick strip
        mon_card = tk.Frame(left, bg="#131826", padx=14, pady=10)
        mon_card.pack(fill="x")
        monitored = [r for r in self.store.regions if r.get("isMonitored")]
        dangers_count = sum(1 for r in self.store.regions if ALERT_LEVELS.get(r["currentLevel"], {}).get("danger"))
        tk.Label(
            mon_card,
            text=f"ВІДСТЕЖУВАНІ РЕГІОНИ ({len(monitored)}) • У тривозі по Україні: {dangers_count}",
            bg="#131826",
            fg="#9CA3AF",
            font=("Segoe UI", 9, "bold"),
        ).pack(anchor="w", pady=(0, 6))
        chips = tk.Frame(mon_card, bg="#131826")
        chips.pack(fill="x")
        for r in monitored[:6]:
            c = ALERT_LEVELS[r["currentLevel"]]["color"]
            extra = max(0, len(r.get("activeAlerts", [])) - 1)
            suffix = f" (+{extra})" if extra > 0 else ""
            tk.Label(
                chips,
                text=f"● {r['name']}: {ALERT_LEVELS[r['currentLevel']]['short']}{suffix}",
                bg="#0D121D",
                fg=c,
                font=("Segoe UI", 9, "bold"),
                padx=8,
                pady=4,
            ).pack(side="left", padx=(0, 6))

        # Right column: Live events log
        right = tk.Frame(cols, bg="#131826", width=340, padx=14, pady=12)
        right.pack(side="right", fill="y")
        right.pack_propagate(False)
        tk.Label(right, text="ОСТАННІ ОПОВІЩЕННЯ ОНЛАЙН", bg="#131826", fg="#FFD500", font=("Segoe UI", 10, "bold")).pack(anchor="w", pady=(0, 8))

        feed = scrolledtext.ScrolledText(right, bg="#0D111A", fg="#E5E7EB", font=("Segoe UI", 9), relief="flat", wrap="word", state="normal")
        feed.pack(fill="both", expand=True)
        for ev in self.store.events[:25]:
            tstr = datetime.fromtimestamp(ev["timestamp"] / 1000.0).strftime("%H:%M")
            lvls = ev.get("activeLevels") or [ev["level"]]
            lv_label = " + ".join(ALERT_LEVELS.get(x, {}).get("short", x) for x in lvls)
            feed.insert("end", f"[{tstr}] {ev['regionName']}\n{lv_label}: {ev['threatDetails']}\n\n")
        feed.configure(state="disabled")

    def _render_map_tab(self) -> None:
        container = tk.Frame(self.content_host, bg="#090B10")
        container.pack(fill="both", expand=True)

        # Left: Ukraine Canvas Map
        map_card = tk.Frame(container, bg="#0F131D", padx=12, pady=12)
        map_card.pack(side="left", fill="both", expand=True, padx=(0, 10))

        hdr = tk.Frame(map_card, bg="#0F131D")
        hdr.pack(fill="x", pady=(0, 8))
        tk.Label(hdr, text="ІНТЕРАКТИВНА МАПА ТРИВОГ УКРАЇНИ", bg="#0F131D", fg="#D1D5DB", font=("Segoe UI", 11, "bold")).pack(side="left")
        tk.Button(
            hdr,
            text="🎯 Криве Озеро",
            bg="#0057B7",
            fg="#FFD500",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=10,
            pady=4,
            command=lambda: self._select_region_on_map(KRYVE_OZERO_ID),
        ).pack(side="right")

        canvas = tk.Canvas(map_card, bg="#090B10", highlightthickness=1, highlightbackground="#1E2538")
        canvas.pack(fill="both", expand=True)

        def draw_map(_evt=None):
            canvas.delete("all")
            w = max(canvas.winfo_width(), 400)
            h = max(canvas.winfo_height(), 300)
            contour_pts = [
                (0.12, 0.32), (0.28, 0.20), (0.52, 0.18), (0.70, 0.22), (0.88, 0.32),
                (0.94, 0.46), (0.86, 0.64), (0.72, 0.74), (0.64, 0.82), (0.68, 0.96),
                (0.58, 0.95), (0.54, 0.82), (0.44, 0.78), (0.36, 0.82), (0.34, 0.66),
                (0.26, 0.62), (0.18, 0.64), (0.10, 0.62), (0.12, 0.46),
            ]
            flat = [coord for x, y in contour_pts for coord in (x * w, y * h)]
            canvas.create_polygon(*flat, fill="#111726", outline="#2C395B", width=2)

            for reg in self.store.regions:
                px = reg["mapNormX"] * w
                py = reg["mapNormY"] * h
                is_ko = reg["id"] == KRYVE_OZERO_ID
                is_sel = reg["id"] == self.store.selected_region_id
                color = ALERT_LEVELS[reg["currentLevel"]]["color"]
                if reg["currentLevel"] == "SAFE" and not reg.get("isMonitored"):
                    color = "#43516E"
                r = 8 if is_ko else 5
                active = reg.get("activeAlerts", [])
                if len(active) > 1:
                    c2 = ALERT_LEVELS[active[1]]["color"]
                    canvas.create_oval(px - (r + 5), py - (r + 5), px + (r + 5), py + (r + 5), outline=c2, width=2)
                if is_ko:
                    canvas.create_oval(px - 12, py - 12, px + 12, py + 12, outline="#FFD500", width=2)
                if is_sel:
                    canvas.create_oval(px - 15, py - 15, px + 15, py + 15, outline="#FFFFFF", width=2)
                tag = f"reg_{reg['id']}"
                canvas.create_oval(px - r, py - r, px + r, py + r, fill=color, outline="", tags=(tag,))
                if is_ko or is_sel or reg["currentLevel"] != "SAFE":
                    short_name = reg["name"].replace(" область", "")
                    canvas.create_text(px, py - 14, text=short_name, fill="#E5E7EB", font=("Segoe UI", 8, "bold"))
                canvas.tag_bind(tag, "<Button-1>", lambda _e, rid=reg["id"]: self._select_region_on_map(rid))

        canvas.bind("<Configure>", draw_map)
        self.after(50, draw_map)

        # Right: Selected Region Details & Multi-Alert Toggles
        side = tk.Frame(container, bg="#131826", width=360, padx=14, pady=12)
        side.pack(side="right", fill="y")
        side.pack_propagate(False)

        sel = self.store.get_region(self.store.selected_region_id) or self.store.regions[0]
        tk.Label(side, text=sel["name"], bg="#131826", fg="#FFFFFF", font=("Segoe UI", 12, "bold"), wraplength=320, justify="left").pack(anchor="w")
        tk.Label(
            side,
            text=sel.get("threatDetails") or f"Статус: {ALERT_LEVELS[sel['currentLevel']]['title']}",
            bg="#131826",
            fg=ALERT_LEVELS[sel["currentLevel"]]["color"],
            font=("Segoe UI", 9, "bold"),
            wraplength=320,
            justify="left",
        ).pack(anchor="w", pady=(4, 8))

        if self.store.user_role == "ADMIN":
            tk.Label(side, text="Вмикати / вимикати загрози для регіону:", bg="#131826", fg="#FFD500", font=("Segoe UI", 9, "bold")).pack(anchor="w")
            toggles_frame = tk.Frame(side, bg="#131826")
            toggles_frame.pack(fill="x", pady=6)
            active_set = set(sel.get("activeAlerts", []))
            for lv in DANGER_ORDER:
                is_on = lv in active_set
                tk.Button(
                    toggles_frame,
                    text=("✓ " if is_on else "+ ") + ALERT_LEVELS[lv]["short"],
                    bg=ALERT_LEVELS[lv]["color"] if is_on else "#1B2234",
                    fg="#000000" if (is_on and lv == "YELLOW") else ("#FFFFFF" if is_on else "#9CA3AF"),
                    font=("Segoe UI", 8, "bold"),
                    relief="flat",
                    padx=8,
                    pady=4,
                    command=lambda level=lv, rid=sel["id"]: self._toggle_region_alert(rid, level),
                ).pack(side="left", padx=(0, 4), pady=2)
            tk.Button(
                side,
                text="🟢 Відбій для цього регіону",
                bg="#22C55E",
                fg="#000000",
                font=("Segoe UI", 9, "bold"),
                relief="flat",
                pady=5,
                command=lambda rid=sel["id"]: self._clear_single_region(rid),
            ).pack(fill="x", pady=(4, 10))

        tk.Label(side, text="СПИСОК РЕГІОНІВ:", bg="#131826", fg="#9CA3AF", font=("Segoe UI", 9, "bold")).pack(anchor="w", pady=(4, 4))
        lb = tk.Listbox(side, bg="#0D111A", fg="#E5E7EB", selectbackground="#1E293B", font=("Segoe UI", 9), relief="flat")
        lb.pack(fill="both", expand=True)
        for reg in self.store.regions:
            act = reg.get("activeAlerts", [])
            badge = f"[{len(act)} загроз]" if len(act) > 1 else f"[{ALERT_LEVELS[reg['currentLevel']]['short']}]"
            lb.insert("end", f"{reg['name']} — {badge}")
        lb.bind("<<ListboxSelect>>", lambda _e: self._on_listbox_region_select(lb))

    def _render_discussion_tab(self) -> None:
        card = tk.Frame(self.content_host, bg="#131826", padx=16, pady=14)
        card.pack(fill="both", expand=True)

        tk.Label(card, text="💬 ОБГОВОРЕННЯ СИТУАЦІЇ (СПІЛЬНИЙ ЧАТ ОНЛАЙН)", bg="#131826", fg="#FFD500", font=("Segoe UI", 12, "bold")).pack(anchor="w")
        tk.Label(
            card,
            text="Повідомлення синхронізуються між комп'ютерами та телефонами громади в реальному часі.",
            bg="#131826",
            fg="#9CA3AF",
            font=("Segoe UI", 9),
        ).pack(anchor="w", pady=(2, 10))

        chat_box = scrolledtext.ScrolledText(card, bg="#0D111A", fg="#F3F4F6", font=("Segoe UI", 10), relief="flat", wrap="word")
        chat_box.pack(fill="both", expand=True, pady=(0, 10))
        for c in self.store.comments:
            tstr = datetime.fromtimestamp(c["timestamp"] / 1000.0).strftime("%H:%M")
            prefix = "👑 [ОФІЦІЙНО] " if c.get("isAdminVerified") else ""
            chat_box.insert("end", f"{prefix}{c['authorName']} ({c.get('regionTag', '')}) • {tstr}\n{c['text']}\n\n")
        chat_box.configure(state="disabled")

        input_row = tk.Frame(card, bg="#131826")
        input_row.pack(fill="x")

        name_entry = None
        if self.store.user_role != "ADMIN":
            name_entry = tk.Entry(input_row, bg="#0D111A", fg="#FFFFFF", insertbackground="#FFFFFF", font=("Segoe UI", 10), width=20)
            name_entry.insert(0, self.store.author_name)
            name_entry.pack(side="left", padx=(0, 8), ipady=6)

        msg_entry = tk.Entry(input_row, bg="#0D111A", fg="#FFFFFF", insertbackground="#FFFFFF", font=("Segoe UI", 10))
        msg_entry.pack(side="left", fill="x", expand=True, padx=(0, 8), ipady=6)

        def send():
            txt = msg_entry.get().strip()
            if not txt:
                return
            author = name_entry.get().strip() if name_entry is not None else "Денис"
            self.store.post_comment(txt, author)
            msg_entry.delete(0, "end")
            self._render_current_tab()

        msg_entry.bind("<Return>", lambda _e: send())
        tk.Button(input_row, text="Надіслати", bg="#FFD500", fg="#000000", font=("Segoe UI", 10, "bold"), relief="flat", padx=16, pady=6, command=send).pack(side="right")

    def _render_audio_tab(self) -> None:
        card = tk.Frame(self.content_host, bg="#131826", padx=16, pady=14)
        card.pack(fill="both", expand=True)

        tk.Label(card, text="🔊 ГОЛОСОВІ СИГНАЛИ (ВІДТВОРЮЮТЬСЯ 1 РАЗ)", bg="#131826", fg="#FFFFFF", font=("Segoe UI", 12, "bold")).pack(anchor="w")
        tk.Label(
            card,
            text="Сигнал програється рівно один раз і зупиняється автоматично. При комбінації кількох тривог записи звучать по черзі.",
            bg="#131826",
            fg="#22C55E",
            font=("Segoe UI", 9, "bold"),
        ).pack(anchor="w", pady=(2, 12))

        for lv in ["RED", "YELLOW", "STREET_FIGHTS", "CIVIL_DEFENSE", "SAFE"]:
            info = ALERT_LEVELS[lv]
            row = tk.Frame(card, bg="#0D111A", padx=12, pady=8)
            row.pack(fill="x", pady=4)
            tk.Label(row, text=info["title"], bg="#0D111A", fg=info["color"], font=("Segoe UI", 10, "bold")).pack(side="left")
            tk.Button(
                row,
                text="▶ Слухати (1 раз)",
                bg="#1E293B",
                fg="#FFFFFF",
                font=("Segoe UI", 9, "bold"),
                relief="flat",
                padx=12,
                pady=4,
                command=lambda fn=info["audio"], t=info["title"]: self.audio_player.preview_file(fn, t, self.store.sound_volume),
            ).pack(side="right")

        vol_row = tk.Frame(card, bg="#131826", pady=12)
        vol_row.pack(fill="x")
        tk.Label(vol_row, text="Гучність (зберігається автоматично):", bg="#131826", fg="#E5E7EB", font=("Segoe UI", 10, "bold")).pack(side="left")
        vol_scale = tk.Scale(
            vol_row,
            from_=0,
            to=100,
            orient="horizontal",
            bg="#131826",
            fg="#FFD500",
            troughcolor="#0D111A",
            highlightthickness=0,
            length=220,
            command=lambda val: self._update_volume(int(float(val)) / 100.0),
        )
        vol_scale.set(int(self.store.sound_volume * 100))
        vol_scale.pack(side="left", padx=12)

        auto_var = tk.BooleanVar(value=self.store.auto_play_voice)
        tk.Checkbutton(
            card,
            text="Автоматично озвучувати нові тривоги в реальному часі",
            variable=auto_var,
            bg="#131826",
            fg="#E5E7EB",
            selectcolor="#0D111A",
            activebackground="#131826",
            font=("Segoe UI", 10),
            command=lambda: self._toggle_auto_voice(auto_var.get()),
        ).pack(anchor="w")

    def _render_admin_tab(self) -> None:
        if self.store.user_role != "ADMIN":
            box = tk.Frame(self.content_host, bg="#131826", padx=30, pady=30)
            box.pack(expand=True)
            tk.Label(box, text="🔒 ДОСТУП ОБМЕЖЕНО", bg="#131826", fg="#FFFFFF", font=("Segoe UI", 15, "bold")).pack(pady=(0, 8))
            tk.Label(
                box,
                text=f"Єдиний адміністратор системи — Денис ({ADMIN_OWNER_EMAIL}).\nІншим користувачам доступний перегляд карти, сповіщень, озвучення та чат.",
                bg="#131826",
                fg="#9CA3AF",
                font=("Segoe UI", 10),
                justify="center",
            ).pack(pady=(0, 14))
            tk.Button(
                box,
                text="🔑 Увійти як адміністратор Денис",
                bg="#0057B7",
                fg="#FFFFFF",
                font=("Segoe UI", 10, "bold"),
                relief="flat",
                padx=16,
                pady=8,
                command=self._on_role_button_click,
            ).pack()
            return

        scroll_canvas = tk.Canvas(self.content_host, bg="#090B10", highlightthickness=0)
        sb = ttk.Scrollbar(self.content_host, orient="vertical", command=scroll_canvas.yview)
        inner = tk.Frame(scroll_canvas, bg="#090B10")
        win_id = scroll_canvas.create_window((0, 0), window=inner, anchor="nw")
        inner.bind("<Configure>", lambda _e: scroll_canvas.configure(scrollregion=scroll_canvas.bbox("all")))
        scroll_canvas.bind("<Configure>", lambda e: scroll_canvas.itemconfigure(win_id, width=e.width))
        scroll_canvas.configure(yscrollcommand=sb.set)
        scroll_canvas.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")

        # Card 1: Multi-region + Multi-alert launcher
        c1 = tk.Frame(inner, bg="#131826", padx=16, pady=14)
        c1.pack(fill="x", pady=(0, 12))
        tk.Label(c1, text="👑 КЕРУВАННЯ АДМІНІСТРАТОРА ДЕНИСА • БАГАТОТРИВОЖНИЙ ЗАПУСК", bg="#131826", fg="#FFD500", font=("Segoe UI", 11, "bold")).pack(anchor="w")
        tk.Label(c1, text="Швидкий пресет або вибір кількох областей і кількох загроз одночасно:", bg="#131826", fg="#9CA3AF", font=("Segoe UI", 9)).pack(anchor="w", pady=(2, 8))

        preset_row = tk.Frame(c1, bg="#131826")
        preset_row.pack(fill="x", pady=(0, 10))
        tk.Button(
            preset_row,
            text="🛑+📢 Криве Озеро: Вуличні бої + Цивільний захист",
            bg="#FF7A00",
            fg="#FFFFFF",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=12,
            pady=6,
            command=lambda: self._apply_multi_alerts([KRYVE_OZERO_ID], {"STREET_FIGHTS", "CIVIL_DEFENSE"}, "Вуличні бої та важливе сповіщення цивільного захисту!"),
        ).pack(side="left", padx=(0, 8))
        tk.Button(
            preset_row,
            text="🔒 Заблокувати права (режим користувача)",
            bg="#1E293B",
            fg="#E5E7EB",
            font=("Segoe UI", 9),
            relief="flat",
            padx=12,
            pady=6,
            command=self._lock_to_user_role,
        ).pack(side="right")

        # Multi-region selection grid
        tk.Label(c1, text="Оберіть одну або кілька громад/областей:", bg="#131826", fg="#FFFFFF", font=("Segoe UI", 9, "bold")).pack(anchor="w")
        reg_grid = tk.Frame(c1, bg="#0D111A", padx=8, pady=8)
        reg_grid.pack(fill="x", pady=6)
        for idx, reg in enumerate(self.store.regions[:12]):
            var = self.admin_region_multi_vars.get(reg["id"])
            if var is None:
                var = tk.BooleanVar(value=(reg["id"] == KRYVE_OZERO_ID))
                self.admin_region_multi_vars[reg["id"]] = var
            cb = tk.Checkbutton(
                reg_grid,
                text=reg["name"].replace(" область", ""),
                variable=var,
                bg="#0D111A",
                fg="#E5E7EB",
                selectcolor="#1E293B",
                activebackground="#0D111A",
                font=("Segoe UI", 9),
            )
            cb.grid(row=idx // 3, column=idx % 3, sticky="w", padx=8, pady=2)

        tk.Label(c1, text="Оберіть одну або кілька загроз одночасно:", bg="#131826", fg="#FFFFFF", font=("Segoe UI", 9, "bold")).pack(anchor="w", pady=(4, 2))
        admin_threats_row = tk.Frame(c1, bg="#0D111A", padx=8, pady=6)
        admin_threats_row.pack(fill="x", pady=(0, 6))
        for lv in DANGER_ORDER:
            tk.Checkbutton(
                admin_threats_row,
                text=ALERT_LEVELS[lv]["short"],
                variable=self.multi_ko_vars[lv],
                bg="#0D111A",
                fg=ALERT_LEVELS[lv]["color"],
                selectcolor="#1E293B",
                activebackground="#0D111A",
                font=("Segoe UI", 9, "bold"),
            ).pack(side="left", padx=(0, 12))

        note_entry = tk.Entry(c1, bg="#0D111A", fg="#FFFFFF", insertbackground="#FFFFFF", font=("Segoe UI", 10))
        note_entry.insert(0, "")
        note_entry.pack(fill="x", pady=6, ipady=5)

        tk.Button(
            c1,
            text="🚨 Оголосити вибрані тривоги для всіх обраних регіонів",
            bg="#FF2A3B",
            fg="#FFFFFF",
            font=("Segoe UI", 10, "bold"),
            relief="flat",
            pady=7,
            command=lambda: self._apply_multi_region_from_vars(note_entry.get()),
        ).pack(fill="x")

        # Card 2: Online Sync Channel & Auto-Save Settings
        c2 = tk.Frame(inner, bg="#131826", padx=16, pady=14)
        c2.pack(fill="x", pady=(0, 12))
        tk.Label(c2, text="🌐 ОНЛАЙН-ЕФІР ТА АВТОЗБЕРЕЖЕННЯ", bg="#131826", fg="#22C55E", font=("Segoe UI", 11, "bold")).pack(anchor="w")
        tk.Label(c2, text=f"Стан збереження: {self.store.storage_path}", bg="#131826", fg="#9CA3AF", font=("Segoe UI", 8)).pack(anchor="w", pady=(2, 8))

        dsns_var = tk.BooleanVar(value=self.store.dsns_auto_sync)
        tk.Checkbutton(
            c2,
            text="Автоматично синхронізувати офіційні тривоги України онлайн",
            variable=dsns_var,
            bg="#131826",
            fg="#FFFFFF",
            selectcolor="#0D111A",
            activebackground="#131826",
            font=("Segoe UI", 9, "bold"),
            command=lambda: self._toggle_dsns_sync(dsns_var.get()),
        ).pack(anchor="w", pady=(0, 6))

        ch_row = tk.Frame(c2, bg="#131826")
        ch_row.pack(fill="x")
        ch_entry = tk.Entry(ch_row, bg="#0D111A", fg="#FFFFFF", insertbackground="#FFFFFF", font=("Segoe UI", 10))
        ch_entry.insert(0, self.store.online_channel_id)
        ch_entry.pack(side="left", fill="x", expand=True, padx=(0, 8), ipady=5)
        tk.Button(
            ch_row,
            text="Зберегти канал і оновити",
            bg="#0057B7",
            fg="#FFFFFF",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=12,
            pady=5,
            command=lambda: self._save_channel(ch_entry.get()),
        ).pack(side="right")

        # Card 3: Change PIN
        c3 = tk.Frame(inner, bg="#131826", padx=16, pady=14)
        c3.pack(fill="x")
        tk.Label(c3, text="🔐 ЗМІНА PIN-КОДУ АДМІНІСТРАТОРА", bg="#131826", fg="#FFFFFF", font=("Segoe UI", 10, "bold")).pack(anchor="w", pady=(0, 6))
        pin_row = tk.Frame(c3, bg="#131826")
        pin_row.pack(fill="x")
        pin_entry = tk.Entry(pin_row, bg="#0D111A", fg="#FFFFFF", show="*", insertbackground="#FFFFFF", font=("Segoe UI", 10), width=20)
        pin_entry.pack(side="left", padx=(0, 8), ipady=5)
        tk.Button(
            pin_row,
            text="Зберегти новий PIN",
            bg="#1E293B",
            fg="#FFD500",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=12,
            pady=5,
            command=lambda: self._update_pin(pin_entry.get()),
        ).pack(side="left")

    # Actions
    def _trigger_single_ko(self, level: str) -> None:
        res = self.store.set_region_active_alerts(KRYVE_OZERO_ID, {level} if level != "SAFE" else set())
        if res:
            reg, lvls = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"])
        self._render_current_tab()

    def _toggle_ko_alert(self, level: str) -> None:
        res = self.store.toggle_alert_in_region(KRYVE_OZERO_ID, level)
        if res:
            reg, lvls = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"])
        self._render_current_tab()

    def _launch_multi_ko_from_vars(self) -> None:
        chosen = {lv for lv, var in self.multi_ko_vars.items() if var.get()}
        if not chosen:
            messagebox.showwarning("Увага", "Оберіть хоча б одну тривогу.")
            return
        self._apply_multi_alerts([KRYVE_OZERO_ID], chosen, "")

    def _apply_multi_region_from_vars(self, note: str) -> None:
        rids = [rid for rid, var in self.admin_region_multi_vars.items() if var.get()]
        chosen = {lv for lv, var in self.multi_ko_vars.items() if var.get()} or {"STREET_FIGHTS", "CIVIL_DEFENSE"}
        if not rids:
            messagebox.showwarning("Увага", "Оберіть хоча б один регіон.")
            return
        self._apply_multi_alerts(rids, chosen, note)

    def _apply_multi_alerts(self, region_ids: List[str], levels: Set[str], note: str) -> None:
        res = self.store.set_multiple_regions_alerts(region_ids, levels, note)
        if res:
            reg, lvls = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"])
        self._render_current_tab()

    def _trigger_total_all_clear(self) -> None:
        lvls = self.store.trigger_total_all_clear()
        self._announce_locally("Вся Україна", lvls, "Тотальний відбій! Небезпека минула.")
        self._render_current_tab()

    def _trigger_street_fights_clear(self) -> None:
        res = self.store.trigger_street_fights_all_clear(KRYVE_OZERO_ID)
        if res:
            reg, lvls = res
            self._announce_locally(reg["name"], ["SAFE"] if lvls == ["SAFE"] else lvls, reg["threatDetails"])
        self._render_current_tab()

    def _select_region_on_map(self, region_id: str) -> None:
        self.store.selected_region_id = region_id
        self.store._save()
        self._render_current_tab()

    def _toggle_region_alert(self, region_id: str, level: str) -> None:
        res = self.store.toggle_alert_in_region(region_id, level)
        if res:
            reg, lvls = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"])
        self._render_current_tab()

    def _clear_single_region(self, region_id: str) -> None:
        res = self.store.set_region_active_alerts(region_id, set())
        if res:
            reg, lvls = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"])
        self._render_current_tab()

    def _on_listbox_region_select(self, lb: tk.Listbox) -> None:
        sel = lb.curselection()
        if not sel:
            return
        idx = sel[0]
        if 0 <= idx < len(self.store.regions):
            self._select_region_on_map(self.store.regions[idx]["id"])

    def _update_volume(self, vol: float) -> None:
        self.store.sound_volume = vol
        self.store._save()

    def _toggle_auto_voice(self, enabled: bool) -> None:
        self.store.auto_play_voice = enabled
        self.store._save()

    def _toggle_dsns_sync(self, enabled: bool) -> None:
        self.store.dsns_auto_sync = enabled
        self.store._save()
        if enabled:
            self._trigger_manual_sync()

    def _save_channel(self, raw: str) -> None:
        self.store.online_channel_id = sanitize_channel_id(raw)
        self.store._save()
        self._trigger_manual_sync()
        messagebox.showinfo("Онлайн-канал", f"Збережено канал:\n{self.store.online_channel_id}")

    def _update_pin(self, new_pin: str) -> None:
        clean = new_pin.strip()
        if len(clean) < 3:
            messagebox.showwarning("PIN", "PIN-код має містити щонайменше 3 символи.")
            return
        self.store.admin_pin = clean
        self.store._save()
        messagebox.showinfo("Безпека", "Новий PIN-код адміністратора збережено.")

    def _lock_to_user_role(self) -> None:
        self.store.user_role = "USER"
        self.store._save()
        self._update_header_badges()
        self._render_current_tab()

    def _on_role_button_click(self) -> None:
        if self.store.user_role == "ADMIN":
            if messagebox.askyesno("Режим користувача", "Перемкнутися в обмежений режим користувача?"):
                self._lock_to_user_role()
            return

        dlg = tk.Toplevel(self)
        dlg.title("Вхід адміністратора (Денис)")
        dlg.geometry("360x180")
        dlg.configure(bg="#131826")
        dlg.transient(self)
        dlg.grab_set()
        tk.Label(dlg, text=f"Адміністратор: {ADMIN_OWNER_NAME} ({ADMIN_OWNER_EMAIL})", bg="#131826", fg="#FFD500", font=("Segoe UI", 9, "bold")).pack(pady=(14, 6))
        tk.Label(dlg, text="Введіть PIN-код (за замовчуванням 1234):", bg="#131826", fg="#E5E7EB", font=("Segoe UI", 9)).pack()
        pin_in = tk.Entry(dlg, show="*", bg="#0D111A", fg="#FFFFFF", insertbackground="#FFFFFF", font=("Segoe UI", 11), justify="center")
        pin_in.pack(pady=8, ipady=4)
        pin_in.focus_set()

        def verify():
            if pin_in.get().strip() == self.store.admin_pin:
                self.store.user_role = "ADMIN"
                self.store._save()
                dlg.destroy()
                self._update_header_badges()
                self._render_current_tab()
            else:
                messagebox.showerror("Помилка", "Невірний PIN-код!")

        pin_in.bind("<Return>", lambda _e: verify())
        tk.Button(dlg, text="Увійти", bg="#FFD500", fg="#000000", font=("Segoe UI", 10, "bold"), relief="flat", padx=20, pady=5, command=verify).pack()

    def _announce_locally(self, region_name: str, levels: List[str], details: str) -> None:
        self.audio_player.play_sequence(levels, volume=self.store.sound_volume, auto_play=self.store.auto_play_voice)
        show_windows_notification(f"Омастротривога: {region_name}", details)

    def _on_audio_status_changed(self, playing: bool, title: str) -> None:
        self.ui_queue.put(("audio", playing, title))

    def _trigger_manual_sync(self) -> None:
        def _worker():
            triggers = self.store.sync_now()
            self.ui_queue.put(("sync_done", triggers))

        threading.Thread(target=_worker, daemon=True).start()

    def _start_background_sync_loop(self) -> None:
        def _loop():
            while True:
                triggers = self.store.sync_now()
                self.ui_queue.put(("sync_done", triggers))
                time.sleep(10)

        threading.Thread(target=_loop, daemon=True).start()

    def _process_ui_queue(self) -> None:
        try:
            while True:
                item = self.ui_queue.get_nowait()
                kind = item[0]
                if kind == "audio":
                    _, playing, title = item
                    if playing:
                        self.lbl_audio_playing.configure(text=f"🔊 Лунає сигнал: {title}")
                        self.audio_banner.pack(fill="x", after=self.nav_buttons[0].master)
                        self.btn_stop_audio.pack(side="left", padx=(0, 6))
                    else:
                        self.audio_banner.pack_forget()
                        self.btn_stop_audio.pack_forget()
                elif kind == "sync_done":
                    _, triggers = item
                    self._update_header_badges()
                    for rname, lvls, det in triggers:
                        self._announce_locally(rname, lvls, det)
                    if self.active_tab in (0, 1, 2):
                        self._render_current_tab()
        except queue.Empty:
            pass
        self.after(200, self._process_ui_queue)


if __name__ == "__main__":
    app = OmastroTrivogaDesktopApp()
    app.mainloop()
