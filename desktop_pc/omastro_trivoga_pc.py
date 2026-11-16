"""
Омастротривога v2.0 — Настільна версія для Windows ПК (.exe)
Повністю сумісна у реальному часі з Android-додатком:
- Вхід через Google акаунт / Firebase Auth (з автозбереженням сесії)
- Синхронізація у реальному часі через Cloud Firestore + резервний ntfy.sh канал
- Усі 11 рівнів тривог (Ракета, БПЛА, Артобстріл, Хімічна, Радіаційна, Вуличні бої, Цив. захист, Жовтий, Спецсигнал)
- Персональні кнопки відбою з виділеним голосом (зокрема «Відбій цивільного захисту», артобстріл, хімічна, радіаційна)
- Режим «Без відбою» (без голосового сигналу відбою)
- Райони України та закріплення до 3 обраних районів на головній панелі
- Голосові оголошення грають рівно 1 раз і дедуплікуються
"""

from __future__ import annotations

import base64
from datetime import datetime
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import queue
import re
import secrets
import socket
import subprocess
import sys
import threading
import time
try:
    import tkinter as tk
    from tkinter import messagebox, scrolledtext, ttk
except ImportError:  # Allows headless unit testing when python3-tk is absent
    tk = None  # type: ignore
    messagebox = None  # type: ignore
    scrolledtext = None  # type: ignore
    ttk = None  # type: ignore
from typing import Any, Dict, List, Optional, Set, Tuple
import urllib.error
import urllib.parse
import urllib.request
import uuid
import webbrowser


APP_VERSION = "2.0.0"
APP_TITLE = f"Омастротривога v{APP_VERSION} — ПК версія"
KRYVE_OZERO_ID = "kryve_ozero"
MAX_PINNED_REGIONS = 3
DEFAULT_PIN = "1234"
DEFAULT_ONLINE_CHANNEL = "omastrotryvoga-kryveozero-live-v1"
UBILLING_ALERTS_URL = "https://ubilling.net.ua/aerialalerts/"
NTFY_BASE_URL = "https://ntfy.sh"
ADMIN_OWNER_EMAIL = "denispekar12@gmail.com"
ADMIN_SECOND_EMAIL = "semenenkom342@gmail.com"
ADMIN_EMAILS = (ADMIN_OWNER_EMAIL, ADMIN_SECOND_EMAIL)
ADMIN_OWNER_NAME = "Денис"
ADMIN_OVERRIDE_GRACE_MS = 30 * 60 * 1000


def is_admin_email(email: str) -> bool:
    return email.strip().lower() in {e.lower() for e in ADMIN_EMAILS}

# Firebase project configuration (matches app/google-services.json & firebase_applet_config.xml)
FIREBASE_API_KEY = "AIzaSyBiaA-c4-uIJX5SfPw38xN7FHm1wIQc5Vk"
FIREBASE_PROJECT_ID = "gen-lang-client-0900673208"
FIRESTORE_DATABASE_ID = "ai-studio-android-alerthub-63c6e249-6328-4f7a-bde0-a6fc214405f1"
DEFAULT_GOOGLE_CLIENT_ID = "74392611696-pf519u9c48ee19oqf4t5oh5kiv7ddg4r.apps.googleusercontent.com"

DEDICATED_CLEAR_AUDIO: Dict[str, str] = {
    "ARTILLERY": "alert_artillery_clear.m4a",
    "CHEMICAL": "alert_chemical_clear.m4a",
    "RADIATION": "alert_radiation_clear.m4a",
    "CIVIL_DEFENSE": "alert_civil_defense_clear.m4a",
}

ALERT_LEVELS: Dict[str, Dict[str, Any]] = {
    "SAFE": {
        "title": "Відбій повітряної тривоги",
        "short": "Відбій / Спокійно",
        "clear_title": "Відбій загрози",
        "color": "#22C55E",
        "bg": "#0D2B1C",
        "audio": "alert_all_clear.m4a",
        "clear_audio": "alert_all_clear.m4a",
        "danger": False,
        "priority": 0,
        "note": "Відбій загрози. Спокійно.",
    },
    "YELLOW": {
        "title": "Жовтий рівень небезпеки",
        "short": "Жовтий рівень",
        "clear_title": "Відбій жовтого рівня",
        "color": "#FFD500",
        "bg": "#382906",
        "audio": "alert_yellow.m4a",
        "clear_audio": "alert_all_clear.m4a",
        "danger": True,
        "priority": 1,
        "note": "Жовтий рівень небезпеки. Підвищена увага.",
    },
    "CIVIL_DEFENSE": {
        "title": "Важливе сповіщення цивільного захисту",
        "short": "Цивільний захист",
        "clear_title": "Відбій цивільного захисту",
        "color": "#A855F7",
        "bg": "#3E124A",
        "audio": "alert_civil_defense.m4a",
        "clear_audio": "alert_civil_defense_clear.m4a",
        "danger": True,
        "priority": 2,
        "note": "Важливе сповіщення цивільного захисту! Ознайомтеся з додатком Омастротривога.",
    },
    "CUSTOM_ALERT": {
        "title": "Спеціальне оповіщення",
        "short": "Спецсигнал",
        "clear_title": "Відбій спецсигналу",
        "color": "#EC4899",
        "bg": "#4A1233",
        "audio": "alert_civil_defense.m4a",
        "clear_audio": "alert_all_clear.m4a",
        "danger": True,
        "priority": 3,
        "note": "Спеціальне оповіщення від адміністратора! Слідкуйте за повідомленнями.",
    },
    "DRONE": {
        "title": "Загроза ударних БПЛА (Шахеди)",
        "short": "БПЛА / Шахеди",
        "clear_title": "Відбій загрози БПЛА",
        "color": "#F59E0B",
        "bg": "#3D2408",
        "audio": "alert_yellow.m4a",
        "clear_audio": "alert_all_clear.m4a",
        "danger": True,
        "priority": 4,
        "note": "Атака ворожих БПЛА / Шахедів у напрямку району! Негайно в укриття!",
    },
    "RED": {
        "title": "Повітряна тривога",
        "short": "Повітряна тривога",
        "clear_title": "Відбій повітряної тривоги",
        "color": "#FF2A3B",
        "bg": "#450D15",
        "audio": "alert_air_raid.m4a",
        "clear_audio": "alert_all_clear.m4a",
        "danger": True,
        "priority": 5,
        "note": "Повітряна тривога! Негайно пройдіть до найближчого укриття!",
    },
    "MISSILE": {
        "title": "Ракетна небезпека / Балістика",
        "short": "Ракета / Балістика",
        "clear_title": "Відбій ракетної небезпеки",
        "color": "#FB7185",
        "bg": "#4A0E1E",
        "audio": "alert_air_raid.m4a",
        "clear_audio": "alert_all_clear.m4a",
        "danger": True,
        "priority": 6,
        "note": "Ракетна небезпека / балістика! Терміново перейдіть в укриття!",
    },
    "ARTILLERY": {
        "title": "Загроза артобстрілу",
        "short": "Артобстріл",
        "clear_title": "Відбій загрози артобстрілу",
        "color": "#F97316",
        "bg": "#4A1C0A",
        "audio": "alert_artillery.m4a",
        "clear_audio": "alert_artillery_clear.m4a",
        "danger": True,
        "priority": 7,
        "note": "Увага, загроза артобстрілу! Негайно пройдіть до найближчого укриття!",
    },
    "STREET_FIGHTS": {
        "title": "Увага! Вуличні бої",
        "short": "Вуличні бої",
        "clear_title": "Відбій вуличних боїв",
        "color": "#FF7A00",
        "bg": "#4D2207",
        "audio": "alert_street_fights.m4a",
        "clear_audio": "alert_all_clear.m4a",
        "danger": True,
        "priority": 8,
        "note": "Вуличні бої! Залишайтесь в укритті і не підходьте до вікон!",
    },
    "CHEMICAL": {
        "title": "Хімічна загроза",
        "short": "Хімічна",
        "clear_title": "Відбій хімічної загрози",
        "color": "#84CC16",
        "bg": "#253D0A",
        "audio": "alert_chemical.m4a",
        "clear_audio": "alert_chemical_clear.m4a",
        "danger": True,
        "priority": 9,
        "note": "Увага, хімічна загроза! Увімкніть телевізор або радіо для отримання інструкцій.",
    },
    "RADIATION": {
        "title": "Радіаційна небезпека",
        "short": "Радіаційна",
        "clear_title": "Відбій радіаційної небезпеки",
        "color": "#EAB308",
        "bg": "#3E3408",
        "audio": "alert_radiation.m4a",
        "clear_audio": "alert_radiation_clear.m4a",
        "danger": True,
        "priority": 10,
        "note": "Увага, радіаційна небезпека! Увімкніть телевізор або радіо для отримання інструкцій.",
    },
}

DANGER_ORDER = [
    "RED",
    "MISSILE",
    "DRONE",
    "ARTILLERY",
    "CHEMICAL",
    "RADIATION",
    "STREET_FIGHTS",
    "CIVIL_DEFENSE",
    "YELLOW",
    "CUSTOM_ALERT",
]

UKRAINE_OBLAST_POLYGONS: List[Dict[str, Any]] = [
    {"id": "volyn", "title": "Волинська", "primaryRegionId": "volyn", "keywords": ["волин", "луцьк"], "points": [(0.14, 0.16), (0.22, 0.17), (0.23, 0.28), (0.17, 0.34), (0.12, 0.27)]},
    {"id": "rivne", "title": "Рівненська", "primaryRegionId": "rivne", "keywords": ["рівнен"], "points": [(0.22, 0.17), (0.32, 0.16), (0.33, 0.29), (0.25, 0.36), (0.21, 0.31), (0.23, 0.28)]},
    {"id": "zhytomyr", "title": "Житомирська", "primaryRegionId": "zhytomyr", "keywords": ["житомир"], "points": [(0.32, 0.16), (0.42, 0.18), (0.43, 0.36), (0.34, 0.41), (0.31, 0.34), (0.33, 0.29)]},
    {"id": "kyiv", "title": "Київська", "primaryRegionId": "kyiv_city", "keywords": ["київ", "бучан", "білоцерк"], "points": [(0.42, 0.18), (0.49, 0.18), (0.52, 0.28), (0.54, 0.39), (0.46, 0.44), (0.41, 0.38), (0.43, 0.36)]},
    {"id": "chernihiv", "title": "Чернігівська", "primaryRegionId": "chernihiv", "keywords": ["черніг"], "points": [(0.49, 0.12), (0.60, 0.13), (0.61, 0.28), (0.52, 0.28), (0.49, 0.18)]},
    {"id": "sumy", "title": "Сумська", "primaryRegionId": "sumy", "keywords": ["сумськ", "суми"], "points": [(0.60, 0.13), (0.70, 0.19), (0.72, 0.32), (0.64, 0.35), (0.61, 0.28)]},
    {"id": "lviv", "title": "Львівська", "primaryRegionId": "lviv", "keywords": ["львів"], "points": [(0.12, 0.27), (0.17, 0.34), (0.21, 0.37), (0.19, 0.49), (0.11, 0.48), (0.08, 0.38)]},
    {"id": "zakarpattia", "title": "Закарпатська", "primaryRegionId": "zakarpattia", "keywords": ["закарпат", "ужгород"], "points": [(0.06, 0.49), (0.11, 0.48), (0.17, 0.53), (0.14, 0.61), (0.06, 0.57)]},
    {"id": "ivano_frankivsk", "title": "Івано-Франківська", "primaryRegionId": "ivano_frankivsk", "keywords": ["івано-франк", "франків"], "points": [(0.17, 0.53), (0.19, 0.49), (0.23, 0.49), (0.24, 0.60), (0.17, 0.62), (0.14, 0.61)]},
    {"id": "ternopil", "title": "Тернопільська", "primaryRegionId": "ternopil", "keywords": ["терноп"], "points": [(0.21, 0.37), (0.25, 0.36), (0.27, 0.50), (0.23, 0.49), (0.19, 0.49)]},
    {"id": "chernivtsi", "title": "Чернівецька", "primaryRegionId": "chernivtsi", "keywords": ["чернів"], "points": [(0.24, 0.55), (0.31, 0.55), (0.32, 0.62), (0.23, 0.62)]},
    {"id": "khmelnytskyi", "title": "Хмельницька", "primaryRegionId": "khmelnytskyi", "keywords": ["хмельн"], "points": [(0.25, 0.36), (0.31, 0.34), (0.34, 0.41), (0.34, 0.54), (0.27, 0.52), (0.27, 0.50)]},
    {"id": "vinnytsia", "title": "Вінницька", "primaryRegionId": "vinnytsia", "keywords": ["вінниц"], "points": [(0.34, 0.41), (0.41, 0.38), (0.44, 0.48), (0.42, 0.58), (0.33, 0.56), (0.34, 0.54)]},
    {"id": "cherkasy", "title": "Черкаська", "primaryRegionId": "cherkasy", "keywords": ["черкас", "уман"], "points": [(0.41, 0.38), (0.46, 0.44), (0.55, 0.40), (0.56, 0.49), (0.45, 0.53), (0.44, 0.48)]},
    {"id": "poltava", "title": "Полтавська", "primaryRegionId": "poltava", "keywords": ["полтав"], "points": [(0.52, 0.28), (0.61, 0.28), (0.64, 0.35), (0.69, 0.39), (0.65, 0.48), (0.56, 0.46), (0.54, 0.39)]},
    {"id": "kharkiv", "title": "Харківська", "primaryRegionId": "kharkiv", "keywords": ["харків", "чугуїв"], "points": [(0.70, 0.27), (0.81, 0.29), (0.83, 0.41), (0.74, 0.47), (0.69, 0.39), (0.72, 0.32)]},
    {"id": "luhansk", "title": "Луганська", "primaryRegionId": "luhansk", "keywords": ["луган", "сєвєродон"], "points": [(0.81, 0.29), (0.90, 0.34), (0.94, 0.47), (0.87, 0.53), (0.83, 0.41)]},
    {"id": "donetsk", "title": "Донецька", "primaryRegionId": "donetsk", "keywords": ["донец", "краматор"], "points": [(0.74, 0.47), (0.83, 0.41), (0.87, 0.53), (0.84, 0.64), (0.77, 0.66), (0.74, 0.58)]},
    {"id": "dnipro", "title": "Дніпропетровська", "primaryRegionId": "dnipro", "keywords": ["дніпр", "кривор"], "points": [(0.56, 0.46), (0.65, 0.48), (0.74, 0.47), (0.74, 0.58), (0.63, 0.62), (0.56, 0.57)]},
    {"id": "kirovohrad", "title": "Кіровоградська", "primaryRegionId": "kirovohrad", "keywords": ["кіровогр", "кропив"], "points": [(0.45, 0.53), (0.56, 0.49), (0.58, 0.57), (0.52, 0.62), (0.44, 0.59)]},
    {"id": "odesa", "title": "Одеська", "primaryRegionId": "odesa", "keywords": ["одес", "подільськ", "ізмаїл"], "points": [(0.36, 0.58), (0.44, 0.59), (0.47, 0.69), (0.42, 0.76), (0.35, 0.85), (0.31, 0.82), (0.38, 0.70)]},
    {"id": "mykolaiv", "title": "Миколаївська", "primaryRegionId": "kryve_ozero", "keywords": ["миколаїв", "кривоозер", "первомай", "вознес", "баштан"], "points": [(0.44, 0.59), (0.52, 0.62), (0.56, 0.64), (0.54, 0.75), (0.46, 0.73), (0.47, 0.69)]},
    {"id": "kherson", "title": "Херсонська", "primaryRegionId": "kherson", "keywords": ["херсон"], "points": [(0.54, 0.64), (0.63, 0.65), (0.66, 0.74), (0.61, 0.81), (0.52, 0.78), (0.54, 0.75)]},
    {"id": "zaporizhzhia", "title": "Запорізька", "primaryRegionId": "zaporizhzhia", "keywords": ["запор"], "points": [(0.63, 0.62), (0.74, 0.58), (0.77, 0.66), (0.72, 0.74), (0.66, 0.74), (0.63, 0.65)]},
    {"id": "crimea", "title": "АР Крим", "primaryRegionId": "crimea", "keywords": ["крим", "сімфероп"], "points": [(0.56, 0.82), (0.63, 0.81), (0.69, 0.87), (0.73, 0.89), (0.68, 0.94), (0.61, 0.96), (0.55, 0.91)]},
]


def alerts_ua_polygon_color(level: str) -> str:
    if level in ("RED", "MISSILE"):
        return "#D94436"
    if level in ("DRONE", "YELLOW"):
        return "#F5B700"
    if level == "ARTILLERY":
        return "#E05D25"
    if level == "STREET_FIGHTS":
        return "#FF6D00"
    if level == "CIVIL_DEFENSE":
        return "#9C4DCC"
    if level == "CHEMICAL":
        return "#00B8D4"
    if level == "RADIATION":
        return "#84CC16"
    if level == "CUSTOM_ALERT":
        return "#3B82F6"
    return "#28303D"


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
    """Full list of 33 Ukrainian districts + Kryvoozerska hromada matching the Android app."""
    return [
        {
            "id": KRYVE_OZERO_ID,
            "name": "Кривоозерська громада (Криве Озеро)",
            "oblast": "Первомайський район • Миколаївщина",
            "isHromada": True,
            "isMonitored": True,
            "currentLevel": "SAFE",
            "activeAlerts": [],
            "alertStartTime": None,
            "threatDetails": "Рідна громада • Тривоги вмикає лише адміністратор Денис",
            "mapNormX": 0.47,
            "mapNormY": 0.65,
            "withoutAllClear": False,
        },
        {"id": "pervomaisk_raion", "name": "Первомайський район", "oblast": "Миколаївська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.49, "mapNormY": 0.66, "withoutAllClear": False},
        {"id": "mykolaiv", "name": "Миколаївський район", "oblast": "Миколаївська область", "isHromada": False, "isMonitored": True, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.52, "mapNormY": 0.72, "withoutAllClear": False},
        {"id": "voznesensk_raion", "name": "Вознесенський район", "oblast": "Миколаївська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.51, "mapNormY": 0.68, "withoutAllClear": False},
        {"id": "bashtanka_raion", "name": "Баштанський район", "oblast": "Миколаївська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.55, "mapNormY": 0.70, "withoutAllClear": False},
        {"id": "kyiv_city", "name": "Київський район / м. Київ", "oblast": "Київ та область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.48, "mapNormY": 0.35, "withoutAllClear": False},
        {"id": "bila_tserkva_raion", "name": "Білоцерківський район", "oblast": "Київська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.47, "mapNormY": 0.39, "withoutAllClear": False},
        {"id": "kyiv_obl", "name": "Бучанський район", "oblast": "Київська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.46, "mapNormY": 0.31, "withoutAllClear": False},
        {"id": "odesa", "name": "Одеський район", "oblast": "Одеська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.40, "mapNormY": 0.75, "withoutAllClear": False},
        {"id": "podilsk_raion", "name": "Подільський район", "oblast": "Одеська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.42, "mapNormY": 0.67, "withoutAllClear": False},
        {"id": "izmail_raion", "name": "Ізмаїльський район", "oblast": "Одеська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.36, "mapNormY": 0.80, "withoutAllClear": False},
        {"id": "kharkiv", "name": "Харківський район", "oblast": "Харківська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.75, "mapNormY": 0.38, "withoutAllClear": False},
        {"id": "chuhuiv_raion", "name": "Чугуївський район", "oblast": "Харківська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.78, "mapNormY": 0.40, "withoutAllClear": False},
        {"id": "dnipro", "name": "Дніпровський район", "oblast": "Дніпропетровська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.64, "mapNormY": 0.55, "withoutAllClear": False},
        {"id": "kryvyi_rih_raion", "name": "Криворізький район", "oblast": "Дніпропетровська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.59, "mapNormY": 0.60, "withoutAllClear": False},
        {"id": "zaporizhzhia", "name": "Запорізький район", "oblast": "Запорізька область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.68, "mapNormY": 0.68, "withoutAllClear": False},
        {"id": "kherson", "name": "Херсонський район", "oblast": "Херсонська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.58, "mapNormY": 0.78, "withoutAllClear": False},
        {"id": "vinnytsia", "name": "Вінницький район", "oblast": "Вінницька область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.38, "mapNormY": 0.50, "withoutAllClear": False},
        {"id": "cherkasy", "name": "Черкаський район", "oblast": "Черкаська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.50, "mapNormY": 0.46, "withoutAllClear": False},
        {"id": "uman_raion", "name": "Уманський район", "oblast": "Черкаська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.46, "mapNormY": 0.55, "withoutAllClear": False},
        {"id": "poltava", "name": "Полтавський район", "oblast": "Полтавська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.62, "mapNormY": 0.42, "withoutAllClear": False},
        {"id": "sumy", "name": "Сумський район", "oblast": "Сумська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.66, "mapNormY": 0.26, "withoutAllClear": False},
        {"id": "chernihiv", "name": "Чернігівський район", "oblast": "Чернігівська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.52, "mapNormY": 0.22, "withoutAllClear": False},
        {"id": "zhytomyr", "name": "Житомирський район", "oblast": "Житомирська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.38, "mapNormY": 0.34, "withoutAllClear": False},
        {"id": "rivne", "name": "Рівненський район", "oblast": "Рівненська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.28, "mapNormY": 0.30, "withoutAllClear": False},
        {"id": "volyn", "name": "Луцький район", "oblast": "Волинська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.18, "mapNormY": 0.28, "withoutAllClear": False},
        {"id": "lviv", "name": "Львівський район", "oblast": "Львівська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.16, "mapNormY": 0.42, "withoutAllClear": False},
        {"id": "khmelnytskyi", "name": "Хмельницький район", "oblast": "Хмельницька область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.30, "mapNormY": 0.46, "withoutAllClear": False},
        {"id": "ternopil", "name": "Тернопільський район", "oblast": "Тернопільська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.24, "mapNormY": 0.48, "withoutAllClear": False},
        {"id": "ivano_frankivsk", "name": "Івано-Франківський район", "oblast": "Івано-Франківська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.19, "mapNormY": 0.56, "withoutAllClear": False},
        {"id": "zakarpattia", "name": "Ужгородський район", "oblast": "Закарпатська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.12, "mapNormY": 0.58, "withoutAllClear": False},
        {"id": "chernivtsi", "name": "Чернівецький район", "oblast": "Чернівецька область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.28, "mapNormY": 0.58, "withoutAllClear": False},
        {"id": "kirovohrad", "name": "Кропивницький район", "oblast": "Кіровоградська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.54, "mapNormY": 0.54, "withoutAllClear": False},
        {"id": "donetsk", "name": "Краматорський район", "oblast": "Донецька область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.82, "mapNormY": 0.58, "withoutAllClear": False},
        {"id": "luhansk", "name": "Сєвєродонецький район", "oblast": "Луганська область", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.90, "mapNormY": 0.44, "withoutAllClear": False},
        {"id": "crimea", "name": "Сімферопольський район (Крим)", "oblast": "АР Крим", "isHromada": False, "isMonitored": False, "currentLevel": "SAFE", "activeAlerts": [], "alertStartTime": None, "threatDetails": "", "mapNormX": 0.62, "mapNormY": 0.92, "withoutAllClear": False},
    ]


def _http_json(
    url: str,
    payload: Optional[dict] = None,
    headers: Optional[Dict[str, str]] = None,
    method: Optional[str] = None,
    timeout: int = 10,
) -> dict:
    req_headers = {"User-Agent": f"OmastroTrivoga-PC/{APP_VERSION}"}
    if headers:
        req_headers.update(headers)
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req_headers.setdefault("Content-Type", "application/json; charset=utf-8")
    req = urllib.request.Request(url, data=data, headers=req_headers, method=method or ("POST" if payload is not None else "GET"))
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            return json.loads(raw) if raw.strip() else {}
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        try:
            err_json = json.loads(body)
            msg = err_json.get("error", {}).get("message") or body
        except Exception:
            msg = body or str(e)
        raise RuntimeError(f"HTTP {e.code}: {msg}") from e


def _http_form(url: str, form_data: Dict[str, str], timeout: int = 10) -> dict:
    encoded = urllib.parse.urlencode(form_data).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=encoded,
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "User-Agent": f"OmastroTrivoga-PC/{APP_VERSION}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            return json.loads(raw) if raw.strip() else {}
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        try:
            err_json = json.loads(body)
            msg = err_json.get("error_description") or err_json.get("error", {}).get("message") or body
        except Exception:
            msg = body or str(e)
        raise RuntimeError(f"HTTP {e.code}: {msg}") from e


def _fs_val(value: Any) -> dict:
    if value is None:
        return {"nullValue": None}
    if isinstance(value, bool):
        return {"booleanValue": value}
    if isinstance(value, int):
        return {"integerValue": str(value)}
    if isinstance(value, float):
        return {"doubleValue": value}
    if isinstance(value, list):
        return {"arrayValue": {"values": [_fs_val(v) for v in value]}}
    if isinstance(value, dict):
        return {"mapValue": {"fields": {str(k): _fs_val(v) for k, v in value.items()}}}
    return {"stringValue": str(value)}


def _fs_fields(mapping: Dict[str, Any]) -> Dict[str, dict]:
    return {k: _fs_val(v) for k, v in mapping.items()}


def _from_fs_val(val_obj: Any) -> Any:
    if not isinstance(val_obj, dict):
        return None
    if "stringValue" in val_obj:
        return val_obj["stringValue"]
    if "booleanValue" in val_obj:
        return bool(val_obj["booleanValue"])
    if "integerValue" in val_obj:
        try:
            return int(val_obj["integerValue"])
        except Exception:
            return 0
    if "doubleValue" in val_obj:
        return float(val_obj["doubleValue"])
    if "timestampValue" in val_obj:
        return val_obj["timestampValue"]
    if "nullValue" in val_obj:
        return None
    if "arrayValue" in val_obj:
        vals = val_obj["arrayValue"].get("values", [])
        return [_from_fs_val(v) for v in vals]
    if "mapValue" in val_obj:
        fields = val_obj["mapValue"].get("fields", {})
        return {k: _from_fs_val(v) for k, v in fields.items()}
    return None


def _parse_fs_doc(doc: dict) -> dict:
    name = doc.get("name", "")
    doc_id = name.rsplit("/", 1)[-1] if "/" in name else name
    out = {"_id": doc_id}
    for k, v in doc.get("fields", {}).items():
        out[k] = _from_fs_val(v)
    return out


class FirebaseDesktopClient:
    """Google Account + Firebase Auth + Cloud Firestore REST API client for Desktop PC."""

    def __init__(self):
        self.uid: str = ""
        self.email: str = ""
        self.display_name: str = ""
        self.email_verified: bool = False
        self.provider_id: str = ""
        self.id_token: str = ""
        self.refresh_token: str = ""
        self.token_expires_at: float = 0.0
        self.last_error: str = ""

    @property
    def is_signed_in(self) -> bool:
        return bool(self.uid and (self.id_token or self.refresh_token))

    @property
    def is_verified_admin(self) -> bool:
        return (
            self.is_signed_in
            and is_admin_email(self.email)
            and self.email_verified
        )

    def to_dict(self) -> dict:
        return {
            "uid": self.uid,
            "email": self.email,
            "displayName": self.display_name,
            "emailVerified": self.email_verified,
            "providerId": self.provider_id,
            "refreshToken": self.refresh_token,
        }

    def load_dict(self, data: Optional[dict]) -> None:
        if not isinstance(data, dict):
            return
        self.uid = str(data.get("uid", ""))
        self.email = str(data.get("email", ""))
        self.display_name = str(data.get("displayName", ""))
        self.email_verified = bool(data.get("emailVerified", False))
        self.provider_id = str(data.get("providerId", ""))
        self.refresh_token = str(data.get("refreshToken", ""))

    def sign_out(self) -> None:
        self.uid = ""
        self.email = ""
        self.display_name = ""
        self.email_verified = False
        self.provider_id = ""
        self.id_token = ""
        self.refresh_token = ""
        self.token_expires_at = 0.0
        self.last_error = ""

    def ensure_fresh_token(self) -> str:
        if not self.is_signed_in:
            raise RuntimeError("Потрібен вхід в акаунт Google / Firebase.")
        if self.id_token and time.time() < (self.token_expires_at - 90):
            return self.id_token
        if not self.refresh_token:
            raise RuntimeError("Сесія застаріла, увійдіть знову.")
        res = _http_form(
            f"https://securetoken.googleapis.com/v1/token?key={FIREBASE_API_KEY}",
            {"grant_type": "refresh_token", "refresh_token": self.refresh_token},
        )
        self.id_token = res.get("id_token", "")
        self.refresh_token = res.get("refresh_token", self.refresh_token)
        self.uid = res.get("user_id", self.uid)
        expires_in = int(res.get("expires_in", 3600))
        self.token_expires_at = time.time() + expires_in
        self._lookup_account_info()
        return self.id_token

    def _lookup_account_info(self) -> None:
        if not self.id_token:
            return
        try:
            res = _http_json(
                f"https://identitytoolkit.googleapis.com/v1/accounts:lookup?key={FIREBASE_API_KEY}",
                {"idToken": self.id_token},
            )
            users = res.get("users", [])
            if users:
                u = users[0]
                self.uid = u.get("localId", self.uid)
                self.email = u.get("email", self.email)
                self.display_name = u.get("displayName", self.display_name) or self.email.split("@")[0]
                self.email_verified = bool(u.get("emailVerified", self.email_verified))
        except Exception:
            pass

    def _apply_auth_response(self, res: dict, provider_id: str) -> None:
        self.id_token = res.get("idToken", "")
        self.refresh_token = res.get("refreshToken", "")
        self.uid = res.get("localId", "")
        self.email = res.get("email", self.email)
        self.display_name = res.get("displayName", "") or res.get("fullName", "") or (self.email.split("@")[0] if self.email else "")
        self.email_verified = bool(res.get("emailVerified", provider_id == "google.com"))
        self.provider_id = provider_id
        expires_in = int(res.get("expiresIn", 3600))
        self.token_expires_at = time.time() + expires_in
        self._lookup_account_info()
        self.last_error = ""

    def sign_in_with_google_id_token(self, google_id_token: str) -> None:
        token_clean = google_id_token.strip()
        if not token_clean:
            raise RuntimeError("Порожній Google ID Token.")
        res = _http_json(
            f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key={FIREBASE_API_KEY}",
            {
                "postBody": f"id_token={urllib.parse.quote(token_clean, safe='')}&providerId=google.com",
                "requestUri": "http://localhost",
                "returnIdpCredential": True,
                "returnSecureToken": True,
            },
        )
        self._apply_auth_response(res, "google.com")

    def exchange_google_code_and_sign_in(
        self,
        code: str,
        redirect_uri: str,
        client_id: str,
        client_secret: str = "",
        code_verifier: str = "",
    ) -> None:
        form = {
            "code": code,
            "client_id": client_id,
            "redirect_uri": redirect_uri,
            "grant_type": "authorization_code",
        }
        if client_secret:
            form["client_secret"] = client_secret
        if code_verifier:
            form["code_verifier"] = code_verifier
        tok_res = _http_form("https://oauth2.googleapis.com/token", form)
        gid = tok_res.get("id_token", "")
        if not gid:
            raise RuntimeError("Google не повернув id_token.")
        self.sign_in_with_google_id_token(gid)

    def sign_in_with_email_password(self, email: str, password: str) -> None:
        res = _http_json(
            f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_API_KEY}",
            {"email": email.strip(), "password": password, "returnSecureToken": True},
        )
        self._apply_auth_response(res, "password")

    def sign_up_with_email_password(self, email: str, password: str, display_name: str = "") -> None:
        res = _http_json(
            f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={FIREBASE_API_KEY}",
            {"email": email.strip(), "password": password, "returnSecureToken": True},
        )
        self._apply_auth_response(res, "password")
        if display_name.strip():
            self.display_name = display_name.strip()
            try:
                _http_json(
                    f"https://identitytoolkit.googleapis.com/v1/accounts:update?key={FIREBASE_API_KEY}",
                    {"idToken": self.id_token, "displayName": self.display_name, "returnSecureToken": True},
                )
            except Exception:
                pass
        try:
            self.send_email_verification()
        except Exception:
            pass

    def send_email_verification(self) -> None:
        token = self.ensure_fresh_token()
        _http_json(
            f"https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key={FIREBASE_API_KEY}",
            {"requestType": "VERIFY_EMAIL", "idToken": token},
        )

    def send_password_reset(self, email: str) -> None:
        _http_json(
            f"https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key={FIREBASE_API_KEY}",
            {"requestType": "PASSWORD_RESET", "email": email.strip()},
        )

    # ---------------- Firestore REST API ----------------
    def _db_root(self) -> str:
        return f"https://firestore.googleapis.com/v1/projects/{FIREBASE_PROJECT_ID}/databases/{FIRESTORE_DATABASE_ID}/documents"

    def _auth_headers(self) -> Dict[str, str]:
        token = self.ensure_fresh_token()
        return {"Authorization": f"Bearer {token}"}

    def query_public_collection(self, collection_id: str, limit: int = 80) -> List[dict]:
        url = f"{self._db_root()}:runQuery"
        query = {
            "structuredQuery": {
                "from": [{"collectionId": collection_id}],
                "where": {
                    "fieldFilter": {
                        "field": {"fieldPath": "isPublic"},
                        "op": "EQUAL",
                        "value": {"booleanValue": True},
                    }
                },
                "limit": limit,
            }
        }
        req_headers = {
            "Authorization": f"Bearer {self.ensure_fresh_token()}",
            "Content-Type": "application/json; charset=utf-8",
            "User-Agent": f"OmastroTrivoga-PC/{APP_VERSION}",
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(query).encode("utf-8"),
            headers=req_headers,
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            arr = json.loads(resp.read().decode("utf-8", errors="replace"))
        docs = []
        if isinstance(arr, list):
            for entry in arr:
                doc = entry.get("document")
                if isinstance(doc, dict):
                    docs.append(_parse_fs_doc(doc))
        return docs

    def get_document_if_exists(self, collection_id: str, doc_id: str) -> Optional[dict]:
        safe_id = re.sub(r"[^a-zA-Z0-9_-]", "_", doc_id)[:128]
        url = f"{self._db_root()}/{collection_id}/{safe_id}"
        try:
            doc = _http_json(url, headers=self._auth_headers(), method="GET")
            return _parse_fs_doc(doc)
        except RuntimeError as e:
            if "HTTP 404" in str(e):
                return None
            raise

    def upsert_user_profile(
        self,
        monitored_region_ids: List[str],
        sound_volume: float,
        vibration_enabled: bool,
        auto_play_voice: bool,
        online_channel_id: str,
        dsns_auto_sync: bool,
        selected_tab: int,
        selected_region_id: str,
    ) -> None:
        if not self.is_signed_in:
            return
        uid = self.uid
        doc_path = f"projects/{FIREBASE_PROJECT_ID}/databases/{FIRESTORE_DATABASE_ID}/documents/users/{uid}"
        existing = self.get_document_if_exists("users", uid)
        common_fields = {
            "displayName": (self.display_name or self.email.split("@")[0] or "Користувач")[:80],
            "email": self.email[:160],
            "monitoredRegionIds": [str(r) for r in monitored_region_ids[:35]],
            "soundVolume": max(0.0, min(1.0, float(sound_volume))),
            "vibrationEnabled": bool(vibration_enabled),
            "autoPlayVoice": bool(auto_play_voice),
            "onlineChannelId": (online_channel_id or DEFAULT_ONLINE_CHANNEL)[:64],
            "dsnsAutoSyncEnabled": bool(dsns_auto_sync),
            "selectedTab": max(0, min(4, int(selected_tab))),
            "selectedRegionId": (selected_region_id or KRYVE_OZERO_ID)[:64],
        }
        if existing is not None:
            write = {
                "update": {"name": doc_path, "fields": _fs_fields(common_fields)},
                "updateMask": {
                    "fieldPaths": [
                        "displayName", "email", "monitoredRegionIds",
                        "soundVolume", "vibrationEnabled", "autoPlayVoice",
                        "onlineChannelId", "dsnsAutoSyncEnabled", "selectedTab",
                        "selectedRegionId", "updatedAt",
                    ]
                },
                "updateTransforms": [{"fieldPath": "updatedAt", "setToServerValue": "REQUEST_TIME"}],
            }
        else:
            create_fields = dict(common_fields)
            create_fields["userId"] = uid
            write = {
                "currentDocument": {"exists": False},
                "update": {"name": doc_path, "fields": _fs_fields(create_fields)},
                "updateTransforms": [
                    {"fieldPath": "createdAt", "setToServerValue": "REQUEST_TIME"},
                    {"fieldPath": "updatedAt", "setToServerValue": "REQUEST_TIME"},
                ],
            }
        _http_json(
            f"{self._db_root()}:commit",
            payload={"writes": [write]},
            headers=self._auth_headers(),
            method="POST",
        )

    def publish_region_alert(
        self,
        region_id: str,
        region_name: str,
        current_level: str,
        active_levels: List[str],
        threat_details: str,
        admin_name: str,
        alert_start_time: Optional[int],
        without_all_clear: bool,
    ) -> None:
        if not self.is_verified_admin:
            return
        safe_id = re.sub(r"[^a-zA-Z0-9_-]", "_", region_id)[:128]
        doc_path = f"projects/{FIREBASE_PROJECT_ID}/databases/{FIRESTORE_DATABASE_ID}/documents/alert_regions/{safe_id}"
        existing = self.get_document_if_exists("alert_regions", safe_id)
        fields_dict: Dict[str, Any] = {
            "regionName": region_name[:120],
            "currentLevel": current_level,
            "activeLevels": active_levels[:10],
            "threatDetails": threat_details[:500],
            "adminName": (admin_name or ADMIN_OWNER_NAME)[:80],
            "updatedByUid": self.uid,
            "withoutAllClear": bool(without_all_clear),
        }
        if alert_start_time is not None and alert_start_time >= 0:
            fields_dict["alertStartTime"] = int(alert_start_time)

        if existing is not None:
            write = {
                "update": {"name": doc_path, "fields": _fs_fields(fields_dict)},
                "updateMask": {
                    "fieldPaths": [
                        "regionName", "currentLevel", "activeLevels",
                        "threatDetails", "adminName", "updatedByUid",
                        "withoutAllClear", "alertStartTime", "updatedAt",
                    ]
                },
                "updateTransforms": [{"fieldPath": "updatedAt", "setToServerValue": "REQUEST_TIME"}],
            }
        else:
            fields_dict["isPublic"] = True
            write = {
                "currentDocument": {"exists": False},
                "update": {"name": doc_path, "fields": _fs_fields(fields_dict)},
                "updateTransforms": [
                    {"fieldPath": "createdAt", "setToServerValue": "REQUEST_TIME"},
                    {"fieldPath": "updatedAt", "setToServerValue": "REQUEST_TIME"},
                ],
            }
        _http_json(
            f"{self._db_root()}:commit",
            payload={"writes": [write]},
            headers=self._auth_headers(),
            method="POST",
        )

    def publish_alert_event(
        self,
        event_id: str,
        region_id: str,
        region_name: str,
        level: str,
        active_levels: List[str],
        threat_details: str,
        source: str,
        admin_name: str,
        is_broadcast_banner: bool,
        is_total_all_clear: bool,
        without_all_clear: bool,
        client_timestamp_ms: int,
    ) -> None:
        if not self.is_verified_admin:
            return
        safe_id = re.sub(r"[^a-zA-Z0-9_-]", "_", event_id)[:128]
        doc_path = f"projects/{FIREBASE_PROJECT_ID}/databases/{FIRESTORE_DATABASE_ID}/documents/alert_events/{safe_id}"
        fields_dict = {
            "regionId": region_id[:64],
            "regionName": region_name[:120],
            "level": level,
            "activeLevels": (active_levels or [level])[:10],
            "threatDetails": threat_details[:500],
            "source": (source or f"Адміністратор {ADMIN_OWNER_NAME}")[:120],
            "adminName": (admin_name or ADMIN_OWNER_NAME)[:80],
            "authorUid": self.uid,
            "isBroadcastBanner": bool(is_broadcast_banner),
            "isTotalAllClear": bool(is_total_all_clear),
            "withoutAllClear": bool(without_all_clear),
            "isPublic": True,
            "clientTimestampMs": max(0, int(client_timestamp_ms)),
        }
        write = {
            "currentDocument": {"exists": False},
            "update": {"name": doc_path, "fields": _fs_fields(fields_dict)},
            "updateTransforms": [{"fieldPath": "createdAt", "setToServerValue": "REQUEST_TIME"}],
        }
        _http_json(
            f"{self._db_root()}:commit",
            payload={"writes": [write]},
            headers=self._auth_headers(),
            method="POST",
        )

    def post_community_message(
        self,
        message_id: str,
        author_name: str,
        author_role: str,
        text: str,
        region_tag: str,
        is_admin_verified: bool,
        client_timestamp_ms: int,
    ) -> None:
        if not self.is_signed_in:
            return
        safe_id = re.sub(r"[^a-zA-Z0-9_-]", "_", message_id)[:128]
        doc_path = f"projects/{FIREBASE_PROJECT_ID}/databases/{FIRESTORE_DATABASE_ID}/documents/community_messages/{safe_id}"
        safe_role = "ADMIN" if (self.is_verified_admin and author_role == "ADMIN") else "USER"
        safe_verified = bool(self.is_verified_admin and is_admin_verified)
        fields_dict = {
            "authorUid": self.uid,
            "authorName": (author_name or "Житель громади")[:80],
            "authorRole": safe_role,
            "text": text.strip()[:1000],
            "regionTag": (region_tag or "Кривоозерська громада")[:100],
            "isAdminVerified": safe_verified,
            "isPublic": True,
            "clientTimestampMs": max(0, int(client_timestamp_ms)),
        }
        write = {
            "currentDocument": {"exists": False},
            "update": {"name": doc_path, "fields": _fs_fields(fields_dict)},
            "updateTransforms": [
                {"fieldPath": "createdAt", "setToServerValue": "REQUEST_TIME"},
                {"fieldPath": "updatedAt", "setToServerValue": "REQUEST_TIME"},
            ],
        }
        _http_json(
            f"{self._db_root()}:commit",
            payload={"writes": [write]},
            headers=self._auth_headers(),
            method="POST",
        )


def start_google_browser_oauth(
    client_id: str,
    client_secret: str = "",
    on_success=None,
    on_error=None,
) -> Tuple[int, str, threading.Event]:
    """
    Opens Google Account sign-in in the default system browser and listens on a
    local loopback HTTP server (127.0.0.1:<port>/callback) for the OAuth response.
    Supports both Desktop App Client ID (PKCE auth code) and Web Client ID (id_token fragment).
    """
    stop_event = threading.Event()

    # Bind on localhost ephemeral port
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    sock.close()

    use_code_flow = bool(client_secret.strip()) or ("apps.googleusercontent.com" in client_id and client_id != DEFAULT_GOOGLE_CLIENT_ID)
    redirect_uri = f"http://127.0.0.1:{port}/callback"
    nonce = secrets.token_urlsafe(16)
    state = secrets.token_urlsafe(16)

    verifier = secrets.token_urlsafe(48)
    digest = hashlib.sha256(verifier.encode("ascii")).digest()
    challenge = base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")

    params: Dict[str, str] = {
        "client_id": client_id.strip(),
        "redirect_uri": redirect_uri,
        "scope": "openid email profile",
        "state": state,
        "prompt": "select_account",
    }
    if use_code_flow:
        params["response_type"] = "code"
        params["code_challenge"] = challenge
        params["code_challenge_method"] = "S256"
    else:
        params["response_type"] = "id_token"
        params["nonce"] = nonce

    auth_url = "https://accounts.google.com/o/oauth2/v2/auth?" + urllib.parse.urlencode(params)

    class _CallbackHandler(BaseHTTPRequestHandler):
        def log_message(self, format, *args):
            return

        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            if parsed.path != "/callback":
                self.send_response(404)
                self.end_headers()
                return
            qs = urllib.parse.parse_qs(parsed.query)
            if "error" in qs:
                err_msg = qs["error"][0]
                self._send_html(f"<h2>Помилка входу Google: {err_msg}</h2><p>Ви можете закрити цю вкладку.</p>")
                if on_error:
                    on_error(f"Google відхилив вхід: {err_msg}")
                stop_event.set()
                return
            if "code" in qs:
                code = qs["code"][0]
                self._send_html("<h2>✅ Вхід через Google виконано!</h2><p>Можете повернутися до програми «Омастротривога ПК».</p>")
                if on_success:
                    on_success({"kind": "code", "code": code, "redirect_uri": redirect_uri, "verifier": verifier})
                stop_event.set()
                return

            # Implicit id_token comes in window.location.hash -> post fragment back to /token
            html = """
<!DOCTYPE html><html lang="uk"><head><meta charset="utf-8"><title>Омастротривога — Вхід Google</title></head>
<body style="background:#090B10;color:#fff;font-family:Segoe UI,sans-serif;text-align:center;padding-top:60px;">
<h2 id="st">Завершення входу через Google...</h2>
<script>
(function() {
  var hash = window.location.hash ? window.location.hash.substring(1) : "";
  var q = new URLSearchParams(hash);
  var idToken = q.get("id_token") || "";
  var err = q.get("error") || "";
  fetch("/token", {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({id_token: idToken, error: err})
  }).then(function() {
    document.getElementById("st").innerText = idToken
      ? "✅ Вхід через Google успішний! Повертайтеся до програми Омастротривога ПК."
      : ("⚠️ Помилка: " + (err || "Не отримано id_token"));
  });
})();
</script></body></html>
"""
            self._send_html(html)

        def do_POST(self):
            if self.path != "/token":
                self.send_response(404)
                self.end_headers()
                return
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length).decode("utf-8", errors="replace")
            try:
                data = json.loads(raw)
            except Exception:
                data = {}
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"OK")
            if data.get("id_token"):
                if on_success:
                    on_success({"kind": "id_token", "id_token": data["id_token"]})
            else:
                if on_error:
                    on_error(data.get("error") or "Не вдалося отримати Google ID Token.")
            stop_event.set()

        def _send_html(self, html: str) -> None:
            body = html.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    def _serve():
        try:
            httpd = HTTPServer(("127.0.0.1", port), _CallbackHandler)
            httpd.timeout = 0.5
            deadline = time.time() + 180
            while not stop_event.is_set() and time.time() < deadline:
                httpd.handle_request()
            httpd.server_close()
        except Exception as e:
            if on_error and not stop_event.is_set():
                on_error(str(e))

    threading.Thread(target=_serve, daemon=True).start()
    webbrowser.open(auth_url)
    return port, auth_url, stop_event


class DesktopAudioPlayer:
    """Plays .m4a voice alerts sequentially, each EXACTLY ONCE without looping or duplication."""

    def __init__(self, on_status_change):
        self.on_status_change = on_status_change
        self._lock = threading.Lock()
        self._stop_flag = threading.Event()
        self._current_proc: Optional[subprocess.Popen] = None
        self._play_thread: Optional[threading.Thread] = None

    @staticmethod
    def deduplicate_filenames(filenames: List[str]) -> List[str]:
        seen: Set[str] = set()
        out: List[str] = []
        for fn in filenames:
            if fn and fn not in seen:
                seen.add(fn)
                out.append(fn)
        return out

    def play_sequence(
        self,
        levels: List[str],
        volume: float = 1.0,
        auto_play: bool = True,
        explicit_files: Optional[List[str]] = None,
        title_override: str = "",
    ) -> None:
        if not auto_play:
            return
        if explicit_files:
            raw_files = self.deduplicate_filenames(explicit_files)
            titles = [title_override or "Відбій загрози"] * len(raw_files)
        else:
            if not levels:
                return
            seen_audio: Set[str] = set()
            raw_files = []
            titles = []
            for lv in dict.fromkeys(levels):
                info = ALERT_LEVELS.get(lv)
                if not info:
                    continue
                audio_fn = info["audio"]
                if audio_fn in seen_audio:
                    continue
                seen_audio.add(audio_fn)
                raw_files.append(audio_fn)
                titles.append(info["title"])

        if not raw_files:
            return
        self.stop()
        self._stop_flag.clear()
        self._play_thread = threading.Thread(
            target=self._run_files_sequence,
            args=(list(zip(raw_files, titles)), volume),
            daemon=True,
        )
        self._play_thread.start()

    def preview_file(self, filename: str, title: str, volume: float = 1.0) -> None:
        self.stop()
        self._stop_flag.clear()
        self._play_thread = threading.Thread(
            target=self._run_files_sequence,
            args=([(filename, f"Тест: {title}")], volume),
            daemon=True,
        )
        self._play_thread.start()

    def _run_files_sequence(self, items: List[Tuple[str, str]], volume: float) -> None:
        try:
            for filename, title in items:
                if self._stop_flag.is_set():
                    break
                path = resolve_audio_path(filename)
                if path is None:
                    continue
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
                "$dur = [int]$p.NaturalDuration.TimeSpan.TotalMilliseconds + 350; "
                "$wait = [Math]::Min([Math]::Max($dur, 700), 30000); "
                "Start-Sleep -Milliseconds $wait "
                "} else { Start-Sleep -Milliseconds 5500 }; "
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
            player_cmd = None
            for tool in (["ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", str(path)], ["afplay", str(path)]):
                try:
                    proc = subprocess.Popen(tool, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    player_cmd = proc
                    break
                except FileNotFoundError:
                    continue
            if player_cmd is None:
                time.sleep(0.3)
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
    """Thread-safe repository with full JSON persistence, Firebase Firestore sync, and ntfy live sync."""

    def __init__(self, storage_path: Optional[Path] = None, enable_network: bool = True):
        self.lock = threading.RLock()
        self.enable_network = enable_network
        self.storage_path = storage_path or get_storage_path()
        self.device_id = str(uuid.uuid4())
        self.firebase = FirebaseDesktopClient()
        self.google_client_id = DEFAULT_GOOGLE_CLIENT_ID
        self.google_client_secret = ""
        self.user_role = "USER"  # Safe default until verified admin signs in or local admin PIN unlocked
        self.local_admin_unlocked = False
        self.admin_pin = DEFAULT_PIN
        self.author_name = ""
        self.selected_tab = 0
        self.selected_region_id = KRYVE_OZERO_ID
        self.online_channel_id = DEFAULT_ONLINE_CHANNEL
        # Manual alerts only by default — matches Android requirement ("не само робило тривог — тільки я")
        self.dsns_auto_sync = False
        self.sound_volume = 1.0
        self.auto_play_voice = True
        self.last_sync_ts = 0
        self.is_online = False
        self.is_syncing = False
        self.status_message = "Готово • Очікування входу Google або ефіру"
        self.processed_remote_ids: Set[str] = set()
        self.manual_override_ts: Dict[str, int] = {}
        self.has_loaded_initial_fs_events = False
        self.pinned_limit_message: Optional[str] = None

        self.regions: List[dict] = initial_regions_template()
        self.events: List[dict] = [
            {
                "id": "initial_ko_event",
                "regionName": "Кривоозерська громада",
                "level": "SAFE",
                "activeLevels": ["SAFE"],
                "timestamp": int(time.time() * 1000) - 10 * 60 * 1000,
                "threatDetails": "Офіційне сповіщення: тривоги вмикає лише адміністратор Денис",
                "source": "Адміністратор Денис",
                "withoutAllClear": False,
            }
        ]
        self.latest_broadcast: Optional[dict] = None
        self.comments: List[dict] = [
            {
                "id": "admin_welcome",
                "authorName": "Денис (Адміністратор)",
                "authorRole": "ADMIN",
                "text": "Вітаю у прямій системі «Омастротривога ПК»! Увійдіть через Google для повної синхронізації з Android.",
                "timestamp": int(time.time() * 1000) - 25 * 60 * 1000,
                "regionTag": "Кривоозерська громада",
                "isAdminVerified": True,
            }
        ]
        self._load()
        self._recompute_role()

    def _recompute_role(self) -> None:
        if self.firebase.is_signed_in:
            if self.firebase.is_verified_admin:
                self.user_role = "USER" if self.local_admin_unlocked is False and hasattr(self, "_explicit_lock") and self._explicit_lock else "ADMIN"
            else:
                self.user_role = "USER"
        else:
            self.user_role = "ADMIN" if self.local_admin_unlocked else "USER"

    def _load(self) -> None:
        if not self.storage_path.is_file():
            self._save()
            return
        try:
            data = json.loads(self.storage_path.read_text(encoding="utf-8"))
            self.device_id = data.get("deviceId") or self.device_id
            self.firebase.load_dict(data.get("firebaseAuth"))
            self.google_client_id = str(data.get("googleClientId") or DEFAULT_GOOGLE_CLIENT_ID)
            self.google_client_secret = str(data.get("googleClientSecret") or "")
            self.local_admin_unlocked = bool(data.get("localAdminUnlocked", False))
            self.admin_pin = str(data.get("adminPin", DEFAULT_PIN))
            self.author_name = str(data.get("authorName", ""))
            self.selected_tab = max(0, min(4, int(data.get("selectedTab", 0))))
            self.selected_region_id = str(data.get("selectedRegionId", KRYVE_OZERO_ID))
            self.online_channel_id = sanitize_channel_id(data.get("onlineChannelId", DEFAULT_ONLINE_CHANNEL))
            self.dsns_auto_sync = bool(data.get("dsnsAutoSync", False))
            self.sound_volume = float(data.get("soundVolume", 1.0))
            self.auto_play_voice = bool(data.get("autoPlayVoice", True))
            self.last_sync_ts = int(data.get("lastSyncTs", 0))
            self.processed_remote_ids = set(data.get("processedRemoteIds", []))
            self.manual_override_ts = {
                str(k): int(v) for k, v in data.get("manualOverrideTs", {}).items()
            }

            saved_regions = {r["id"]: r for r in data.get("regions", []) if isinstance(r, dict) and "id" in r}
            merged = []
            monitored_count = 0
            for base in initial_regions_template():
                sv = saved_regions.get(base["id"])
                if sv:
                    active = [lv for lv in sv.get("activeAlerts", []) if ALERT_LEVELS.get(lv, {}).get("danger")]
                    if not active and ALERT_LEVELS.get(sv.get("currentLevel", ""), {}).get("danger"):
                        active = [sv["currentLevel"]]
                    want_mon = bool(sv.get("isMonitored", base["isMonitored"]))
                    allow_mon = want_mon and monitored_count < MAX_PINNED_REGIONS
                    if allow_mon:
                        monitored_count += 1
                    base["isMonitored"] = allow_mon
                    base["activeAlerts"] = sorted_active(set(active))
                    base["currentLevel"] = primary_of(set(active))
                    base["alertStartTime"] = sv.get("alertStartTime")
                    base["threatDetails"] = sv.get("threatDetails") or base["threatDetails"]
                    base["withoutAllClear"] = bool(sv.get("withoutAllClear", False))
                else:
                    if base["isMonitored"] and monitored_count < MAX_PINNED_REGIONS:
                        monitored_count += 1
                    else:
                        base["isMonitored"] = False
                merged.append(base)
            self.regions = merged

            if isinstance(data.get("events"), list) and data["events"]:
                self.events = data["events"][:300]
            if isinstance(data.get("comments"), list) and data["comments"]:
                self.comments = data["comments"][:300]
            self.latest_broadcast = data.get("latestBroadcast")
        except Exception:
            pass

    def _save(self) -> None:
        with self.lock:
            payload = {
                "deviceId": self.device_id,
                "firebaseAuth": self.firebase.to_dict(),
                "googleClientId": self.google_client_id,
                "googleClientSecret": self.google_client_secret,
                "localAdminUnlocked": self.local_admin_unlocked,
                "adminPin": self.admin_pin,
                "authorName": self.author_name,
                "selectedTab": self.selected_tab,
                "selectedRegionId": self.selected_region_id,
                "onlineChannelId": self.online_channel_id,
                "dsnsAutoSync": self.dsns_auto_sync,
                "soundVolume": self.sound_volume,
                "autoPlayVoice": self.auto_play_voice,
                "lastSyncTs": self.last_sync_ts,
                "processedRemoteIds": list(self.processed_remote_ids)[-300:],
                "manualOverrideTs": self.manual_override_ts,
                "regions": self.regions,
                "events": self.events[:300],
                "comments": self.comments[:300],
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
                    return dict(reg)
        return None

    def get_monitored_regions(self) -> List[dict]:
        with self.lock:
            return [dict(r) for r in self.regions if r.get("isMonitored")]

    def toggle_monitored_region(self, region_id: str) -> bool:
        with self.lock:
            monitored = [r for r in self.regions if r.get("isMonitored")]
            target = next((r for r in self.regions if r["id"] == region_id), None)
            if target is None:
                return False
            if target.get("isMonitored"):
                if len(monitored) <= 1:
                    self.pinned_limit_message = "Повинен бути закріплений хоча б 1 район."
                    return False
                target["isMonitored"] = False
                self.pinned_limit_message = None
            else:
                if len(monitored) >= MAX_PINNED_REGIONS:
                    self.pinned_limit_message = f"Максимум {MAX_PINNED_REGIONS} закріплених районів на головній. Спочатку відкріпіть один."
                    return False
                target["isMonitored"] = True
                self.pinned_limit_message = None
            self._save()
        if self.firebase.is_signed_in:
            self._sync_profile_async()
        return True

    def _sync_profile_async(self) -> None:
        def _run():
            try:
                mon = [r["id"] for r in self.get_monitored_regions()]
                self.firebase.upsert_user_profile(
                    monitored_region_ids=mon,
                    sound_volume=self.sound_volume,
                    vibration_enabled=True,
                    auto_play_voice=self.auto_play_voice,
                    online_channel_id=self.online_channel_id,
                    dsns_auto_sync=self.dsns_auto_sync,
                    selected_tab=self.selected_tab,
                    selected_region_id=self.selected_region_id,
                )
            except Exception:
                pass

        threading.Thread(target=_run, daemon=True).start()

    def _can_admin_write(self) -> bool:
        if self.firebase.is_signed_in:
            return self.firebase.is_verified_admin and self.user_role == "ADMIN"
        return self.user_role == "ADMIN"

    def set_region_active_alerts(
        self,
        region_id: str,
        levels: Set[str],
        note: str = "",
        publish_online: bool = True,
        emit_broadcast: bool = True,
        without_all_clear: bool = False,
        explicit_clear_files: Optional[List[str]] = None,
        emit_audio: bool = True,
    ) -> Optional[Tuple[dict, List[str], List[str]]]:
        if publish_online and not self._can_admin_write():
            return None

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
                    no_clr_suffix = " (без відбою)" if without_all_clear else ""
                    if note.strip():
                        resolved_note = note.strip()
                    elif len(ordered) > 1:
                        resolved_note = f"Одночасно{no_clr_suffix}: " + " + ".join(ALERT_LEVELS[lv]["short"] for lv in ordered)
                    elif primary in ALERT_LEVELS and primary != "SAFE":
                        resolved_note = ALERT_LEVELS[primary]["note"] + no_clr_suffix
                    elif without_all_clear:
                        resolved_note = "Тривогу знято (без відбою)."
                    else:
                        resolved_note = "Відбій загрози. Спокійно."

                    reg["currentLevel"] = primary
                    reg["activeAlerts"] = ordered
                    reg["alertStartTime"] = start_time
                    reg["threatDetails"] = resolved_note
                    reg["withoutAllClear"] = bool(without_all_clear)
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
                    "withoutAllClear": bool(without_all_clear),
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
                    "withoutAllClear": bool(without_all_clear),
                },
            )
            self.events = self.events[:300]
            self._save()

        audio_levels = levels_display if emit_audio else []
        explicit_files = list(explicit_clear_files or []) if emit_audio else []

        if publish_online and self.enable_network:
            event_id = str(uuid.uuid4())
            self._mark_remote_processed(event_id)
            self._mark_remote_processed(f"fs_{event_id}")
            if self.firebase.is_verified_admin:
                def _fs_push():
                    try:
                        self.firebase.publish_region_alert(
                            region_id=target["id"],
                            region_name=target["name"],
                            current_level=primary,
                            active_levels=ordered,
                            threat_details=target["threatDetails"],
                            admin_name=ADMIN_OWNER_NAME,
                            alert_start_time=target["alertStartTime"],
                            without_all_clear=without_all_clear,
                        )
                        self.firebase.publish_alert_event(
                            event_id=event_id,
                            region_id=target["id"],
                            region_name=target["name"],
                            level=primary,
                            active_levels=levels_display,
                            threat_details=target["threatDetails"],
                            source=f"Адміністратор {ADMIN_OWNER_NAME} (ПК)",
                            admin_name=ADMIN_OWNER_NAME,
                            is_broadcast_banner=emit_broadcast,
                            is_total_all_clear=False,
                            without_all_clear=without_all_clear,
                            client_timestamp_ms=now_ms,
                        )
                    except Exception:
                        pass

                threading.Thread(target=_fs_push, daemon=True).start()

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
                "withoutAllClear": bool(without_all_clear),
                "emitAudio": bool(emit_audio),
                "timestamp": now_ms,
            }
            self._publish_online_async(title, envelope, 5 if clean_danger else 3)

        return target, audio_levels, explicit_files

    def toggle_alert_in_region(
        self,
        region_id: str,
        level: str,
        without_all_clear: bool = False,
    ) -> Optional[Tuple[dict, List[str], List[str]]]:
        reg = self.get_region(region_id)
        if not reg:
            return None
        if not ALERT_LEVELS.get(level, {}).get("danger"):
            return self.set_region_active_alerts(
                region_id,
                set(),
                without_all_clear=without_all_clear,
                emit_audio=not without_all_clear,
            )

        existing = set(reg.get("activeAlerts", []))
        if level in existing:
            existing.remove(level)
            was_added = False
        else:
            existing.add(level)
            was_added = True

        effective_no_clear = (bool(reg.get("withoutAllClear")) or without_all_clear) if was_added else without_all_clear
        dedicated_clear_fn = DEDICATED_CLEAR_AUDIO.get(level)
        emit_audio = bool(
            was_added or (not effective_no_clear and (not existing or dedicated_clear_fn is not None))
        )
        explicit_files: List[str] = []
        note = ""
        if not was_added and emit_audio and dedicated_clear_fn:
            explicit_files = [dedicated_clear_fn]
            clear_title = ALERT_LEVELS[level]["clear_title"]
            if not existing:
                note = f"{clear_title}! Загроза минула."
            else:
                note = f"{clear_title}. Залишаються активними: " + ", ".join(
                    ALERT_LEVELS[lv]["short"] for lv in sorted_active(existing)
                )

        return self.set_region_active_alerts(
            region_id,
            existing,
            note=note,
            without_all_clear=effective_no_clear,
            explicit_clear_files=explicit_files,
            emit_audio=emit_audio,
        )

    def trigger_specific_all_clear(
        self,
        region_id: str,
        level: str,
        without_all_clear: bool = False,
    ) -> Optional[Tuple[dict, List[str], List[str]]]:
        reg = self.get_region(region_id)
        existing = set(reg.get("activeAlerts", [])) if reg else set()
        remaining = {lv for lv in existing if lv != level}
        lv_info = ALERT_LEVELS.get(level, ALERT_LEVELS["SAFE"])
        dedicated_fn = DEDICATED_CLEAR_AUDIO.get(level)
        if not remaining:
            note = (
                f"Загрозу ({lv_info['short']}) знято (без відбою)."
                if without_all_clear
                else f"{lv_info['clear_title']}! Загроза минула."
            )
        else:
            prefix = (
                f"Знято {lv_info['short']} (без відбою)"
                if without_all_clear
                else lv_info["clear_title"]
            )
            note = f"{prefix}. Залишаються активними: " + ", ".join(
                ALERT_LEVELS[lv]["short"] for lv in sorted_active(remaining)
            )
        explicit_files = [] if without_all_clear else ([dedicated_fn] if dedicated_fn else ["alert_all_clear.m4a"])
        return self.set_region_active_alerts(
            region_id,
            remaining,
            note=note,
            without_all_clear=without_all_clear,
            explicit_clear_files=explicit_files,
            emit_audio=not without_all_clear,
        )

    def set_multiple_regions_alerts(
        self,
        region_ids: List[str],
        levels: Set[str],
        note: str = "",
        without_all_clear: bool = False,
    ) -> Optional[Tuple[dict, List[str], List[str]]]:
        result = None
        for idx, rid in enumerate(region_ids):
            res = self.set_region_active_alerts(
                rid,
                levels,
                note=note,
                publish_online=True,
                emit_broadcast=(idx == len(region_ids) - 1),
                without_all_clear=without_all_clear,
                emit_audio=(idx == 0) and (bool(levels) or not without_all_clear),
            )
            if idx == 0:
                result = res
        return result

    def trigger_total_all_clear(self, without_all_clear: bool = False) -> Tuple[List[str], List[str]]:
        if not self._can_admin_write():
            return [], []
        now_ms = int(time.time() * 1000)
        note = (
            "Тривоги знято по всіх районах (без відбою)."
            if without_all_clear
            else "Тотальний відбій для всієї території України! Небезпека минула."
        )
        with self.lock:
            previously_danger = [dict(r) for r in self.regions if ALERT_LEVELS.get(r["currentLevel"], {}).get("danger") or r["id"] == KRYVE_OZERO_ID]
            for reg in self.regions:
                self.manual_override_ts[reg["id"]] = now_ms
                reg["currentLevel"] = "SAFE"
                reg["activeAlerts"] = []
                reg["alertStartTime"] = None
                reg["threatDetails"] = note
                reg["withoutAllClear"] = bool(without_all_clear)

            banner_region = "Вся Україна (ЗНЯТО БЕЗ ВІДБОЮ)" if without_all_clear else "Вся територія України (ВЕСЬ ВІДБІЙ)"
            self.latest_broadcast = {
                "id": str(uuid.uuid4()),
                "regionName": banner_region,
                "level": "SAFE",
                "activeLevels": ["SAFE"],
                "timestamp": now_ms,
                "note": note,
                "adminName": ADMIN_OWNER_NAME,
                "withoutAllClear": bool(without_all_clear),
            }
            self.events.insert(
                0,
                {
                    "id": str(uuid.uuid4()),
                    "regionName": banner_region,
                    "level": "SAFE",
                    "activeLevels": ["SAFE"],
                    "timestamp": now_ms,
                    "threatDetails": note,
                    "source": f"Адміністратор {ADMIN_OWNER_NAME} (ПК)",
                    "withoutAllClear": bool(without_all_clear),
                },
            )
            self.events = self.events[:300]
            self._save()

        event_id = str(uuid.uuid4())
        self._mark_remote_processed(event_id)
        self._mark_remote_processed(f"fs_{event_id}")
        if not self.enable_network:
            return ([] if without_all_clear else ["SAFE"]), []

        if self.firebase.is_verified_admin:
            def _fs_clear():
                try:
                    for reg in previously_danger:
                        self.firebase.publish_region_alert(
                            region_id=reg["id"],
                            region_name=reg["name"],
                            current_level="SAFE",
                            active_levels=[],
                            threat_details=note,
                            admin_name=ADMIN_OWNER_NAME,
                            alert_start_time=None,
                            without_all_clear=without_all_clear,
                        )
                    self.firebase.publish_alert_event(
                        event_id=event_id,
                        region_id="all_ukraine",
                        region_name=banner_region,
                        level="SAFE",
                        active_levels=["SAFE"],
                        threat_details=note,
                        source=f"Адміністратор {ADMIN_OWNER_NAME} (ПК)",
                        admin_name=ADMIN_OWNER_NAME,
                        is_broadcast_banner=True,
                        is_total_all_clear=True,
                        without_all_clear=without_all_clear,
                        client_timestamp_ms=now_ms,
                    )
                except Exception:
                    pass

            threading.Thread(target=_fs_clear, daemon=True).start()

        envelope = {
            "type": "TOTAL_CLEAR",
            "eventId": event_id,
            "senderDeviceId": self.device_id,
            "note": note,
            "adminName": ADMIN_OWNER_NAME,
            "withoutAllClear": bool(without_all_clear),
            "timestamp": now_ms,
        }
        self._publish_online_async(
            "ЗНЯТО БЕЗ ВІДБОЮ — Вся Україна" if without_all_clear else "🟢 ВЕСЬ ВІДБІЙ — Вся Україна",
            envelope,
            4,
        )
        return ([] if without_all_clear else ["SAFE"]), []

    def _apply_all_ukraine_locally(
        self,
        ordered: List[str],
        primary: str,
        resolved_note: str,
        ts_ms: int,
        without_all_clear: bool = False,
        event_id: Optional[str] = None,
        admin_name: str = ADMIN_OWNER_NAME,
    ) -> None:
        levels_display = ordered if ordered else ["SAFE"]
        eid = event_id or str(uuid.uuid4())
        with self.lock:
            for reg in self.regions:
                self.manual_override_ts[reg["id"]] = ts_ms
                reg["currentLevel"] = primary
                reg["activeAlerts"] = list(ordered)
                reg["alertStartTime"] = (reg.get("alertStartTime") or ts_ms) if ordered else None
                reg["threatDetails"] = resolved_note
                reg["withoutAllClear"] = bool(without_all_clear)

            self.latest_broadcast = {
                "id": eid,
                "regionName": "ВСЯ ТЕРИТОРІЯ УКРАЇНИ",
                "level": primary,
                "activeLevels": levels_display,
                "timestamp": ts_ms,
                "note": resolved_note,
                "adminName": admin_name,
                "withoutAllClear": bool(without_all_clear),
            }
            self.events.insert(
                0,
                {
                    "id": eid,
                    "regionName": "ВСЯ ТЕРИТОРІЯ УКРАЇНИ",
                    "level": primary,
                    "activeLevels": levels_display,
                    "timestamp": ts_ms,
                    "threatDetails": resolved_note,
                    "source": f"Адміністратор {admin_name} (ПК)",
                    "withoutAllClear": bool(without_all_clear),
                },
            )
            self.events = self.events[:300]
            self._save()

    def trigger_all_ukraine_alert(
        self,
        level: str = "RED",
        levels: Optional[Set[str]] = None,
        note: str = "",
        without_all_clear: bool = False,
    ) -> Optional[Tuple[List[str], List[str]]]:
        """Оголошує масштабну тривогу одночасно по всіх 36 районах України."""
        if not self._can_admin_write():
            return None

        target_set = set(levels) if levels else ({level} if ALERT_LEVELS.get(level, {}).get("danger") else set())
        clean_danger = {lv for lv in target_set if ALERT_LEVELS.get(lv, {}).get("danger")}
        if not clean_danger:
            return self.trigger_total_all_clear(without_all_clear=without_all_clear)

        now_ms = int(time.time() * 1000)
        ordered = sorted_active(clean_danger)
        primary = primary_of(clean_danger)
        if note.strip():
            resolved_note = note.strip()
        elif len(ordered) > 1:
            resolved_note = "Тривога по всій Україні: " + " + ".join(ALERT_LEVELS[lv]["short"] for lv in ordered)
        else:
            resolved_note = f"ВСЯ УКРАЇНА: {ALERT_LEVELS[primary]['note']}"

        event_id = str(uuid.uuid4())
        self._apply_all_ukraine_locally(
            ordered=ordered,
            primary=primary,
            resolved_note=resolved_note,
            ts_ms=now_ms,
            without_all_clear=without_all_clear,
            event_id=event_id,
            admin_name=ADMIN_OWNER_NAME,
        )
        self._mark_remote_processed(event_id)
        self._mark_remote_processed(f"fs_{event_id}")

        if self.enable_network:
            all_regs_snapshot = [dict(r) for r in self.regions]
            if self.firebase.is_verified_admin:
                def _fs_all_ua():
                    try:
                        for reg in all_regs_snapshot:
                            self.firebase.publish_region_alert(
                                region_id=reg["id"],
                                region_name=reg["name"],
                                current_level=primary,
                                active_levels=ordered,
                                threat_details=resolved_note,
                                admin_name=ADMIN_OWNER_NAME,
                                alert_start_time=now_ms,
                                without_all_clear=without_all_clear,
                            )
                        self.firebase.publish_alert_event(
                            event_id=event_id,
                            region_id="all_ukraine",
                            region_name="ВСЯ ТЕРИТОРІЯ УКРАЇНИ",
                            level=primary,
                            active_levels=ordered,
                            threat_details=resolved_note,
                            source=f"Адміністратор {ADMIN_OWNER_NAME} (ПК)",
                            admin_name=ADMIN_OWNER_NAME,
                            is_broadcast_banner=True,
                            is_total_all_clear=False,
                            without_all_clear=without_all_clear,
                            client_timestamp_ms=now_ms,
                        )
                    except Exception:
                        pass

                threading.Thread(target=_fs_all_ua, daemon=True).start()

            title = (
                f"🚨 ТРИВОГА ПО ВСІЙ УКРАЇНІ ({len(ordered)})"
                if len(ordered) > 1
                else f"🚨 ВСЯ УКРАЇНА: {ALERT_LEVELS[primary]['title']}"
            )
            envelope = {
                "type": "ALL_UKRAINE_ALERT",
                "eventId": event_id,
                "senderDeviceId": self.device_id,
                "level": primary,
                "activeLevels": ordered,
                "note": resolved_note,
                "adminName": ADMIN_OWNER_NAME,
                "withoutAllClear": bool(without_all_clear),
                "emitAudio": True,
                "timestamp": now_ms,
            }
            self._publish_online_async(title, envelope, 5)

        return ordered, []

    def post_comment(self, text: str, author_name: str, region_tag: str = "Кривоозерська громада") -> None:
        trimmed = text.strip()
        if not trimmed:
            return
        now_ms = int(time.time() * 1000)
        is_admin = self.firebase.is_verified_admin or self.user_role == "ADMIN"
        if is_admin:
            email_l = self.firebase.email.strip().lower()
            if email_l == ADMIN_SECOND_EMAIL.lower():
                admin_label = self.firebase.display_name.strip() or "Адміністратор #2"
            else:
                admin_label = self.firebase.display_name.strip() or ADMIN_OWNER_NAME
            final_author = f"{admin_label} (Адміністратор)"
        else:
            fallback_name = self.firebase.display_name or self.author_name or "Житель громади"
            final_author = author_name.strip() or fallback_name
            if author_name.strip():
                self.author_name = author_name.strip()

        cid = str(uuid.uuid4())
        comment = {
            "id": cid,
            "authorName": final_author,
            "authorRole": "ADMIN" if is_admin else "USER",
            "text": trimmed,
            "timestamp": now_ms,
            "regionTag": region_tag,
            "isAdminVerified": is_admin,
        }
        with self.lock:
            self.comments.insert(0, comment)
            self.comments = self.comments[:300]
            self._save()

        self._mark_remote_processed(cid)
        if not self.enable_network:
            return
        if self.firebase.is_signed_in:
            def _fs_msg():
                try:
                    self.firebase.post_community_message(
                        message_id=cid,
                        author_name=final_author,
                        author_role=comment["authorRole"],
                        text=trimmed,
                        region_tag=region_tag,
                        is_admin_verified=is_admin,
                        client_timestamp_ms=now_ms,
                    )
                except Exception:
                    pass

            threading.Thread(target=_fs_msg, daemon=True).start()

        envelope = {
            "type": "COMMENT",
            "eventId": cid,
            "senderDeviceId": self.device_id,
            "authorName": final_author,
            "authorRole": comment["authorRole"],
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
            if len(self.processed_remote_ids) > 300:
                self.processed_remote_ids = set(list(self.processed_remote_ids)[-200:])
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
                    headers={"Content-Type": "application/json; charset=utf-8", "User-Agent": f"OmastroTrivoga-PC/{APP_VERSION}"},
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

    def sync_now(self) -> List[Tuple[str, List[str], List[str], str]]:
        """
        Syncs with Firebase Firestore (when Google/Firebase signed in) + ntfy live channel + optional DSNS API.
        Returns list of (region_name, audio_levels, explicit_files, details) triggers to announce ONCE.
        """
        triggers: List[Tuple[str, List[str], List[str], str]] = []
        with self.lock:
            if self.is_syncing:
                return triggers
            self.is_syncing = True

        any_ok = False
        try:
            # 1. Sync Cloud Firestore if signed in with Google / Firebase
            if self.firebase.is_signed_in:
                try:
                    fs_regions = self.firebase.query_public_collection("alert_regions", limit=60)
                    fs_events = self.firebase.query_public_collection("alert_events", limit=60)
                    fs_msgs = self.firebase.query_public_collection("community_messages", limit=60)
                    any_ok = True

                    if fs_regions:
                        by_id = {d["_id"]: d for d in fs_regions}
                        with self.lock:
                            for reg in self.regions:
                                rem = by_id.get(reg["id"])
                                if rem:
                                    raw_lvls = [lv for lv in (rem.get("activeLevels") or []) if ALERT_LEVELS.get(lv, {}).get("danger")]
                                    if not raw_lvls and ALERT_LEVELS.get(rem.get("currentLevel", ""), {}).get("danger"):
                                        raw_lvls = [rem["currentLevel"]]
                                    reg["activeAlerts"] = sorted_active(set(raw_lvls))
                                    reg["currentLevel"] = primary_of(set(raw_lvls))
                                    reg["threatDetails"] = rem.get("threatDetails") or reg["threatDetails"]
                                    reg["alertStartTime"] = rem.get("alertStartTime")
                                    reg["withoutAllClear"] = bool(rem.get("withoutAllClear", False))
                            self._save()

                    if fs_events:
                        fs_events_sorted = sorted(fs_events, key=lambda x: int(x.get("clientTimestampMs") or 0), reverse=True)
                        mapped_events = []
                        for ev in fs_events_sorted:
                            primary = ev.get("level", "SAFE")
                            if primary not in ALERT_LEVELS:
                                primary = "SAFE"
                            active = [lv for lv in (ev.get("activeLevels") or [primary]) if lv in ALERT_LEVELS] or [primary]
                            ts = int(ev.get("clientTimestampMs") or 0) or int(time.time() * 1000)
                            mapped_events.append({
                                "id": ev["_id"],
                                "regionName": ev.get("regionName", "Україна"),
                                "level": primary,
                                "activeLevels": active,
                                "timestamp": ts,
                                "threatDetails": ev.get("threatDetails", ""),
                                "source": ev.get("source") or f"Адміністратор {ADMIN_OWNER_NAME}",
                                "withoutAllClear": bool(ev.get("withoutAllClear", False)),
                            })
                        with self.lock:
                            merged_ev = {}
                            for item in mapped_events + self.events:
                                merged_ev.setdefault(item["id"], item)
                            self.events = sorted(merged_ev.values(), key=lambda x: int(x.get("timestamp", 0)), reverse=True)[:300]

                            newest_banner = next((ev for ev in fs_events_sorted if ev.get("isBroadcastBanner")), None)
                            if newest_banner:
                                b_ts = int(newest_banner.get("clientTimestampMs") or 0)
                                cur_b_ts = int((self.latest_broadcast or {}).get("timestamp") or 0)
                                if b_ts > cur_b_ts:
                                    b_lv = newest_banner.get("level", "SAFE")
                                    b_act = [lv for lv in (newest_banner.get("activeLevels") or [b_lv]) if lv in ALERT_LEVELS] or [b_lv]
                                    self.latest_broadcast = {
                                        "id": newest_banner["_id"],
                                        "regionName": newest_banner.get("regionName", "Україна"),
                                        "level": b_lv if b_lv in ALERT_LEVELS else "SAFE",
                                        "activeLevels": b_act,
                                        "timestamp": b_ts,
                                        "note": newest_banner.get("threatDetails", ""),
                                        "adminName": newest_banner.get("adminName", ADMIN_OWNER_NAME),
                                        "withoutAllClear": bool(newest_banner.get("withoutAllClear", False)),
                                    }
                            self._save()

                        now_ms = int(time.time() * 1000)
                        if not self.has_loaded_initial_fs_events:
                            self.has_loaded_initial_fs_events = True
                            for ev in fs_events_sorted:
                                self._mark_remote_processed(f"fs_{ev['_id']}")
                        else:
                            newest_unseen = next(
                                (
                                    ev for ev in fs_events_sorted
                                    if (now_ms - int(ev.get("clientTimestampMs") or now_ms)) < 5 * 60 * 1000
                                    and self._mark_remote_processed(f"fs_{ev['_id']}")
                                ),
                                None,
                            )
                            for ev in fs_events_sorted:
                                self._mark_remote_processed(f"fs_{ev['_id']}")
                            if newest_unseen:
                                lv = newest_unseen.get("level", "SAFE")
                                active = [x for x in (newest_unseen.get("activeLevels") or [lv]) if x in ALERT_LEVELS] or [lv]
                                without_clear = bool(newest_unseen.get("withoutAllClear", False))
                                is_danger = ALERT_LEVELS.get(lv, {}).get("danger", False)
                                if is_danger or not without_clear:
                                    triggers.append((
                                        newest_unseen.get("regionName", "Україна"),
                                        active,
                                        [],
                                        newest_unseen.get("threatDetails", ""),
                                    ))

                    if fs_msgs:
                        fs_msgs_sorted = sorted(fs_msgs, key=lambda x: int(x.get("clientTimestampMs") or 0), reverse=True)
                        mapped_msgs = []
                        for m in fs_msgs_sorted:
                            txt = str(m.get("text", "")).strip()
                            if not txt:
                                continue
                            mapped_msgs.append({
                                "id": m["_id"],
                                "authorName": m.get("authorName", "Житель громади"),
                                "authorRole": m.get("authorRole", "USER"),
                                "text": txt,
                                "timestamp": int(m.get("clientTimestampMs") or 0) or int(time.time() * 1000),
                                "regionTag": m.get("regionTag", "Кривоозерська громада"),
                                "isAdminVerified": bool(m.get("isAdminVerified", False)),
                            })
                        with self.lock:
                            merged_msg = {}
                            for item in mapped_msgs + self.comments:
                                merged_msg.setdefault(item["id"], item)
                            self.comments = sorted(merged_msg.values(), key=lambda x: int(x.get("timestamp", 0)), reverse=True)[:300]
                            self._save()
                except Exception as e:
                    self.firebase.last_error = str(e)

            # 2. Poll ntfy.sh live pub/sub channel
            try:
                ch = sanitize_channel_id(self.online_channel_id)
                req = urllib.request.Request(
                    f"{NTFY_BASE_URL}/{ch}/json?poll=1&since=12h",
                    headers={"User-Agent": f"OmastroTrivoga-PC/{APP_VERSION}"},
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
                        without_clear = bool(env.get("withoutAllClear", False))

                        if t == "REGION_ALERT":
                            rid = env.get("regionId", KRYVE_OZERO_ID)
                            raw_active = [lv for lv in (env.get("activeLevels") or []) if lv in ALERT_LEVELS]
                            if not raw_active and ALERT_LEVELS.get(env.get("level", ""), {}).get("danger"):
                                raw_active = [env["level"]]
                            res = self.set_region_active_alerts(
                                rid,
                                set(raw_active),
                                note=env.get("note", ""),
                                publish_online=False,
                                emit_broadcast=True,
                                without_all_clear=without_clear,
                                emit_audio=bool(env.get("emitAudio", True)),
                            )
                            if res and is_recent:
                                reg, lvls, exp_files = res
                                if lvls or exp_files:
                                    triggers.append((reg["name"], lvls, exp_files, reg["threatDetails"]))

                        elif t == "ALL_UKRAINE_ALERT":
                            raw_active = [lv for lv in (env.get("activeLevels") or []) if ALERT_LEVELS.get(lv, {}).get("danger")]
                            if not raw_active and ALERT_LEVELS.get(env.get("level", ""), {}).get("danger"):
                                raw_active = [env["level"]]
                            ordered_ua = sorted_active(set(raw_active))
                            primary_ua = primary_of(set(raw_active))
                            ua_note = env.get("note") or (
                                f"ВСЯ УКРАЇНА: {ALERT_LEVELS[primary_ua]['note']}"
                                if primary_ua != "SAFE"
                                else "Відбій тривоги"
                            )
                            self._apply_all_ukraine_locally(
                                ordered=ordered_ua,
                                primary=primary_ua,
                                resolved_note=ua_note,
                                ts_ms=ts,
                                without_all_clear=without_clear,
                                event_id=event_id,
                                admin_name=env.get("adminName", ADMIN_OWNER_NAME),
                            )
                            if is_recent and bool(env.get("emitAudio", True)) and ordered_ua:
                                triggers.append(("ВСЯ ТЕРИТОРІЯ УКРАЇНИ", ordered_ua, [], ua_note))

                        elif t == "TOTAL_CLEAR":
                            with self.lock:
                                for reg in self.regions:
                                    self.manual_override_ts[reg["id"]] = ts
                                    reg["currentLevel"] = "SAFE"
                                    reg["activeAlerts"] = []
                                    reg["alertStartTime"] = None
                                    reg["threatDetails"] = env.get("note", "Тотальний відбій для всієї України!")
                                    reg["withoutAllClear"] = without_clear
                                self.latest_broadcast = {
                                    "id": event_id,
                                    "regionName": "Вся територія України (ВЕСЬ ВІДБІЙ)" if not without_clear else "Вся Україна (ЗНЯТО БЕЗ ВІДБОЮ)",
                                    "level": "SAFE",
                                    "activeLevels": ["SAFE"],
                                    "timestamp": ts,
                                    "note": env.get("note", ""),
                                    "adminName": env.get("adminName", ADMIN_OWNER_NAME),
                                    "withoutAllClear": without_clear,
                                }
                                self._save()
                            if is_recent and not without_clear:
                                triggers.append(("Вся Україна", ["SAFE"], [], env.get("note", "Весь відбій")))

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
                                        self.comments = sorted(self.comments, key=lambda c: c["timestamp"], reverse=True)[:300]
                                        self._save()
                    except Exception:
                        continue
            except Exception:
                pass

            # 3. Optional DSNS API sync (OFF by default: only if admin explicitly enables it)
            if self.dsns_auto_sync:
                try:
                    req = urllib.request.Request(
                        UBILLING_ALERTS_URL,
                        headers={"User-Agent": f"OmastroTrivoga-PC/{APP_VERSION}", "Accept": "application/json"},
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
                                is_special = any(lv != "RED" for lv in reg.get("activeAlerts", []))
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
                                        changed_monitored.append((reg["name"], [target_lvl], [], reg["threatDetails"]))
                            if changed_monitored:
                                for rname, lvls, _, det in changed_monitored:
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
                                self.events = self.events[:300]
                                self._save()
                                triggers.append(changed_monitored[0])
                except Exception:
                    pass

            with self.lock:
                if any_ok:
                    self.is_online = True
                    self.last_sync_ts = int(time.time() * 1000)
                    if self.firebase.is_signed_in:
                        self.status_message = "Онлайн • Google + Firestore"
                    else:
                        self.status_message = "Онлайн • Ефір (без акаунта Google)"
                else:
                    self.is_online = False
                    self.status_message = "Офлайн • Збережено локально"
                self._save()
        finally:
            with self.lock:
                self.is_syncing = False

        return triggers


_TkBase = tk.Tk if tk is not None else object


class OmastroTrivogaDesktopApp(_TkBase):
    def __init__(self):
        if tk is None:
            raise RuntimeError("Tkinter UI requires a desktop Python environment with Tcl/Tk.")
        super().__init__()
        self.title(APP_TITLE)
        self.geometry("1200x820")
        self.minsize(960, 680)
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
        self.without_all_clear_var = tk.BooleanVar(value=False)
        self.admin_region_multi_vars: Dict[str, tk.BooleanVar] = {}
        self.auth_dialog_open = False
        self.dashboard_target_mode = "REGION"  # "REGION" or "ALL_UKRAINE"
        self.dashboard_target_region_id = KRYVE_OZERO_ID
        self.dashboard_alert_tab = 0
        self.map_show_legend = False

        self._build_styles()
        self._build_shell()
        self._select_tab(self.active_tab, save=False)

        self.after(150, self._process_ui_queue)
        if self.store.firebase.is_signed_in:
            self._refresh_firebase_session_async()
        else:
            self.after(450, self._open_google_auth_dialog)
        self._start_background_sync_loop()

    def _build_styles(self) -> None:
        style = ttk.Style(self)
        style.theme_use("clam")
        style.configure("Dark.TFrame", background="#090B10")
        style.configure("Card.TFrame", background="#131826")
        style.configure("Dark.TLabel", background="#090B10", foreground="#F3F4F6", font=("Segoe UI", 10))

    def _build_shell(self) -> None:
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
        tk.Label(title_row, text="ТРИВОГА ПК ", bg="#0C0F17", fg="#FF2A3B", font=("Segoe UI", 14, "bold")).pack(side="left")
        tk.Label(title_row, text="v2.0", bg="#D94436", fg="#FFFFFF", font=("Segoe UI", 9, "bold"), padx=6, pady=1).pack(side="left", padx=(4, 0))

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
            text="🔄 Оновити",
            bg="#1A2236",
            fg="#22C55E",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=10,
            pady=5,
            command=self._trigger_manual_sync,
        )
        self.btn_sync.pack(side="left", padx=4)

        self.btn_google_auth = tk.Button(
            right_hdr,
            text="",
            bg="#0057B7",
            fg="#FFFFFF",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=12,
            pady=5,
            command=self._open_google_auth_dialog,
        )
        self.btn_google_auth.pack(side="left", padx=4)

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

        self.audio_banner = tk.Frame(self, bg="#3B0B13", padx=16, pady=6)
        self.lbl_audio_playing = tk.Label(self.audio_banner, text="", bg="#3B0B13", fg="#FFFFFF", font=("Segoe UI", 10, "bold"))
        self.lbl_audio_playing.pack(side="left")

        nav = tk.Frame(self, bg="#0D101A", padx=16, pady=8)
        nav.pack(fill="x")
        self.nav_buttons: List[tk.Button] = []
        tabs = [
            ("🚨 Тривога", 0),
            ("🗺️ Мапа районів", 1),
            ("💬 Обговорення", 2),
            ("🔊 Звуки", 3),
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

        self.content_host = tk.Frame(self, bg="#090B10")
        self.content_host.pack(fill="both", expand=True, padx=16, pady=10)

    def _update_header_badges(self) -> None:
        if self.store.firebase.is_signed_in:
            disp = self.store.firebase.display_name or self.store.firebase.email
            if self.store.firebase.is_verified_admin:
                self.btn_google_auth.configure(text=f"🟢 Google: {disp}", bg="#113825", fg="#22C55E")
            else:
                self.btn_google_auth.configure(text=f"🔵 Google: {disp}", bg="#182742", fg="#93C5FD")
        else:
            self.btn_google_auth.configure(text="🔵 Увійти через Google", bg="#0057B7", fg="#FFFFFF")

        is_admin = self.store.user_role == "ADMIN"
        if is_admin:
            email_l = self.store.firebase.email.strip().lower()
            if email_l == ADMIN_SECOND_EMAIL.lower():
                admin_short = self.store.firebase.display_name.strip() or "Адмін #2"
            else:
                admin_short = ADMIN_OWNER_NAME
            role_label = f"👑 Адмін ({admin_short})"
        else:
            role_label = "🔒 Користувач (перегляд)"
        self.btn_role.configure(
            text=role_label,
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

    def _refresh_firebase_session_async(self) -> None:
        def _worker():
            try:
                self.store.firebase.ensure_fresh_token()
                with self.store.lock:
                    self.store._recompute_role()
                    self.store._save()
                self.store.sync_now()
            except Exception as e:
                self.store.firebase.last_error = str(e)
            self.ui_queue.put(("auth_updated",))

        threading.Thread(target=_worker, daemon=True).start()

    def _open_google_auth_dialog(self) -> None:
        if self.auth_dialog_open:
            return
        self.auth_dialog_open = True

        dlg = tk.Toplevel(self)
        dlg.title("Вхід через Google акаунт — Омастротривога ПК")
        dlg.geometry("520x580")
        dlg.configure(bg="#0D111A")
        dlg.transient(self)
        dlg.grab_set()

        def _on_close():
            self.auth_dialog_open = False
            dlg.destroy()

        dlg.protocol("WM_DELETE_WINDOW", _on_close)

        wrap = tk.Frame(dlg, bg="#0D111A", padx=22, pady=18)
        wrap.pack(fill="both", expand=True)

        tk.Label(wrap, text="🔵 ВХІД ЧЕРЕЗ GOOGLE АКАУНТ", bg="#0D111A", fg="#FFD500", font=("Segoe UI", 13, "bold")).pack(anchor="w")
        tk.Label(
            wrap,
            text=(
                "Увійдіть у свій Google акаунт на ПК для миттєвої синхронізації тривог, "
                "районів та чату з мобільним додатком через Firebase Firestore.\n"
                f"Адміністраторські права активуються автоматично для {', '.join(ADMIN_EMAILS)}."
            ),
            bg="#0D111A",
            fg="#D1D5DB",
            font=("Segoe UI", 9),
            wraplength=470,
            justify="left",
        ).pack(anchor="w", pady=(6, 12))

        status_lbl = tk.Label(wrap, text="", bg="#0D111A", fg="#22C55E", font=("Segoe UI", 9, "bold"), wraplength=470, justify="left")

        if self.store.firebase.is_signed_in:
            cur_box = tk.Frame(wrap, bg="#113825", highlightbackground="#22C55E", highlightthickness=1, padx=12, pady=10)
            cur_box.pack(fill="x", pady=(0, 12))
            role_txt = "👑 Адміністратор системи (повний доступ)" if self.store.firebase.is_verified_admin else "👤 Звичайний користувач (перегляд + чат)"
            tk.Label(cur_box, text=f"Ви увійшли як: {self.store.firebase.display_name} ({self.store.firebase.email})", bg="#113825", fg="#FFFFFF", font=("Segoe UI", 10, "bold"), wraplength=440, justify="left").pack(anchor="w")
            tk.Label(cur_box, text=role_txt, bg="#113825", fg="#FFD500", font=("Segoe UI", 9, "bold")).pack(anchor="w", pady=(2, 8))
            tk.Button(
                cur_box,
                text="🚪 Вийти з акаунта Google",
                bg="#FF2A3B",
                fg="#FFFFFF",
                font=("Segoe UI", 9, "bold"),
                relief="flat",
                padx=12,
                pady=5,
                command=lambda: self._sign_out_google(dlg, _on_close),
            ).pack(anchor="w")

        # Card 1: 1-Click Browser Google OAuth
        g_card = tk.Frame(wrap, bg="#131826", highlightbackground="#2563EB", highlightthickness=1, padx=14, pady=12)
        g_card.pack(fill="x", pady=(0, 10))
        tk.Label(g_card, text="1. Швидкий вхід через Google у браузері", bg="#131826", fg="#FFFFFF", font=("Segoe UI", 10, "bold")).pack(anchor="w")
        tk.Label(
            g_card,
            text="Відкриє вікно вибору Google акаунта у вашому браузері та автоматично поверне сесію в програму.",
            bg="#131826",
            fg="#9CA3AF",
            font=("Segoe UI", 8),
            wraplength=440,
            justify="left",
        ).pack(anchor="w", pady=(2, 8))

        cid_row = tk.Frame(g_card, bg="#131826")
        cid_row.pack(fill="x", pady=(0, 8))
        tk.Label(cid_row, text="Google OAuth Client ID (необов'язково змінювати):", bg="#131826", fg="#9CA3AF", font=("Segoe UI", 8)).pack(anchor="w")
        cid_entry = tk.Entry(cid_row, bg="#090B10", fg="#E5E7EB", insertbackground="#FFFFFF", font=("Segoe UI", 8))
        cid_entry.insert(0, self.store.google_client_id)
        cid_entry.pack(fill="x", ipady=3)

        def _launch_browser_google():
            chosen_cid = cid_entry.get().strip() or DEFAULT_GOOGLE_CLIENT_ID
            self.store.google_client_id = chosen_cid
            self.store._save()
            status_lbl.configure(text="🌐 Відкрито вікно Google у браузері... Завершіть вхід і поверніться сюди.", fg="#FFD500")

            def _on_ok(res: dict):
                try:
                    if res.get("kind") == "id_token":
                        self.store.firebase.sign_in_with_google_id_token(res["id_token"])
                    else:
                        self.store.firebase.exchange_google_code_and_sign_in(
                            code=res["code"],
                            redirect_uri=res["redirect_uri"],
                            client_id=chosen_cid,
                            client_secret=self.store.google_client_secret,
                            code_verifier=res.get("verifier", ""),
                        )
                    with self.store.lock:
                        if self.store.firebase.is_verified_admin:
                            self.store.local_admin_unlocked = True
                        self.store._recompute_role()
                        self.store._save()
                    self.ui_queue.put(("google_login_done", dlg))
                except Exception as exc:
                    self.ui_queue.put(("auth_error", status_lbl, str(exc)))

            def _on_err(msg: str):
                self.ui_queue.put(("auth_error", status_lbl, msg))

            start_google_browser_oauth(chosen_cid, self.store.google_client_secret, _on_ok, _on_err)

        tk.Button(
            g_card,
            text="🔵 Увійти через Google (відкрити браузер)",
            bg="#2563EB",
            fg="#FFFFFF",
            font=("Segoe UI", 10, "bold"),
            relief="flat",
            pady=8,
            command=_launch_browser_google,
        ).pack(fill="x")

        # Card 2: Direct Email + Password Firebase Sign-In / Sign-Up (works even without localhost OAuth redirect setup)
        e_card = tk.Frame(wrap, bg="#131826", padx=14, pady=12)
        e_card.pack(fill="x", pady=(0, 10))
        tk.Label(e_card, text="2. Вхід або реєстрація за Email та паролем (Firebase / Gmail)", bg="#131826", fg="#FFFFFF", font=("Segoe UI", 10, "bold")).pack(anchor="w")
        tk.Label(
            e_card,
            text="Працює на будь-якому ПК без додаткового налаштування браузера.",
            bg="#131826",
            fg="#9CA3AF",
            font=("Segoe UI", 8),
        ).pack(anchor="w", pady=(2, 6))

        tk.Label(e_card, text="Email:", bg="#131826", fg="#D1D5DB", font=("Segoe UI", 9)).pack(anchor="w")
        email_entry = tk.Entry(e_card, bg="#090B10", fg="#FFFFFF", insertbackground="#FFFFFF", font=("Segoe UI", 10))
        email_entry.insert(0, self.store.firebase.email or "")
        email_entry.pack(fill="x", pady=(2, 6), ipady=4)

        tk.Label(e_card, text="Пароль:", bg="#131826", fg="#D1D5DB", font=("Segoe UI", 9)).pack(anchor="w")
        pass_entry = tk.Entry(e_card, show="*", bg="#090B10", fg="#FFFFFF", insertbackground="#FFFFFF", font=("Segoe UI", 10))
        pass_entry.pack(fill="x", pady=(2, 8), ipady=4)

        def _email_auth(is_signup: bool):
            em = email_entry.get().strip()
            pw = pass_entry.get()
            if not em or len(pw) < 6:
                status_lbl.configure(text="⚠️ Введіть коректний Email і пароль (мін. 6 символів).", fg="#F87171")
                return
            status_lbl.configure(text="⏳ Виконується вхід...", fg="#FFD500")

            def _worker():
                try:
                    if is_signup:
                        self.store.firebase.sign_up_with_email_password(em, pw)
                    else:
                        self.store.firebase.sign_in_with_email_password(em, pw)
                    with self.store.lock:
                        if self.store.firebase.is_verified_admin:
                            self.store.local_admin_unlocked = True
                        self.store._recompute_role()
                        self.store._save()
                    self.ui_queue.put(("google_login_done", dlg))
                except Exception as exc:
                    self.ui_queue.put(("auth_error", status_lbl, str(exc)))

            threading.Thread(target=_worker, daemon=True).start()

        btn_e_row = tk.Frame(e_card, bg="#131826")
        btn_e_row.pack(fill="x")
        tk.Button(btn_e_row, text="🔑 Увійти", bg="#22C55E", fg="#000000", font=("Segoe UI", 9, "bold"), relief="flat", padx=14, pady=6, command=lambda: _email_auth(False)).pack(side="left", padx=(0, 6))
        tk.Button(btn_e_row, text="📝 Створити акаунт", bg="#1E293B", fg="#FFFFFF", font=("Segoe UI", 9, "bold"), relief="flat", padx=12, pady=6, command=lambda: _email_auth(True)).pack(side="left", padx=(0, 6))
        tk.Button(
            btn_e_row,
            text="Забули пароль?",
            bg="#131826",
            fg="#93C5FD",
            font=("Segoe UI", 8, "underline"),
            relief="flat",
            command=lambda: self._send_reset_password(email_entry.get(), status_lbl),
        ).pack(side="right")

        status_lbl.pack(fill="x", pady=(4, 8))

        tk.Button(
            wrap,
            text="Продовжити без входу (перегляд + онлайн-ефір ntfy)",
            bg="#181C26",
            fg="#9CA3AF",
            font=("Segoe UI", 9),
            relief="flat",
            pady=6,
            command=_on_close,
        ).pack(fill="x")

    def _send_reset_password(self, email: str, status_lbl: tk.Label) -> None:
        em = email.strip()
        if not em:
            status_lbl.configure(text="⚠️ Спочатку введіть Email.", fg="#F87171")
            return

        def _w():
            try:
                self.store.firebase.send_password_reset(em)
                self.ui_queue.put(("auth_info", status_lbl, f"✉️ Лист для відновлення пароля надіслано на {em}."))
            except Exception as exc:
                self.ui_queue.put(("auth_error", status_lbl, str(exc)))

        threading.Thread(target=_w, daemon=True).start()

    def _sign_out_google(self, dlg: tk.Toplevel, close_cb) -> None:
        self.store.firebase.sign_out()
        self.store.local_admin_unlocked = False
        self.store._recompute_role()
        self.store._save()
        close_cb()
        self._update_header_badges()
        self._render_current_tab()

    def _select_tab(self, idx: int, save: bool = True) -> None:
        self.active_tab = idx
        if save:
            self.store.selected_tab = idx
            self.store._save()
            if self.store.firebase.is_signed_in:
                self.store._sync_profile_async()

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

    def _make_scrollable_frame(self, parent: tk.Widget) -> tk.Frame:
        canvas = tk.Canvas(parent, bg="#090B10", highlightthickness=0)
        sb = ttk.Scrollbar(parent, orient="vertical", command=canvas.yview)
        inner = tk.Frame(canvas, bg="#090B10")
        win_id = canvas.create_window((0, 0), window=inner, anchor="nw")
        inner.bind("<Configure>", lambda _e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.bind("<Configure>", lambda e: canvas.itemconfigure(win_id, width=e.width))
        canvas.configure(yscrollcommand=sb.set)
        canvas.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")

        def _on_wheel(event):
            canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")

        canvas.bind_all("<MouseWheel>", _on_wheel)
        return inner

    def _draw_tk_shahed_icon(self, canvas: tk.Canvas, cx: float, cy: float, size: float = 13.0, fill: str = "#EF4444", outline: str = "#FFFFFF") -> None:
        """Малює цілий нерозрізаний значок Шахеда (Shahed-136 дельта-крило) на Tkinter Canvas."""
        s = size
        pts = [
            cx, cy - s * 0.92,          # Ніс
            cx + s * 0.20, cy - s * 0.25,
            cx + s * 0.88, cy + s * 0.48,  # Праве крило
            cx + s * 0.88, cy + s * 0.72,  # Правий кінцевик
            cx + s * 0.22, cy + s * 0.56,
            cx + s * 0.14, cy + s * 0.86,  # Хвіст право
            cx - s * 0.14, cy + s * 0.86,  # Хвіст ліво
            cx - s * 0.22, cy + s * 0.56,
            cx - s * 0.88, cy + s * 0.72,  # Лівий кінцевик
            cx - s * 0.88, cy + s * 0.48,  # Ліве крило
            cx - s * 0.20, cy - s * 0.25,
        ]
        canvas.create_polygon(*pts, fill=fill, outline=outline, width=1.2)
        canvas.create_line(cx, cy - s * 0.78, cx, cy + s * 0.70, fill="#FCA5A5", width=1.4)

    def _render_dashboard(self) -> None:
        ko = self.store.get_region(KRYVE_OZERO_ID)
        active_alerts = ko.get("activeAlerts", []) if ko else []
        primary = ko["currentLevel"] if ko else "SAFE"
        lv_info = ALERT_LEVELS[primary]

        bc = self.store.latest_broadcast
        if bc:
            bc_frame = tk.Frame(self.content_host, bg="#1B1726", highlightbackground="#FFD500", highlightthickness=1, padx=14, pady=8)
            bc_frame.pack(fill="x", pady=(0, 8))
            bc_levels = bc.get("activeLevels") or [bc["level"]]
            lv_titles = " + ".join(ALERT_LEVELS.get(x, {}).get("short", x) for x in bc_levels)
            no_clr = " [БЕЗ ВІДБОЮ]" if bc.get("withoutAllClear") else ""
            hdr_bc = tk.Frame(bc_frame, bg="#1B1726")
            hdr_bc.pack(fill="x")
            tk.Label(hdr_bc, text=f"📡 НАЖИВО ВІД АДМІНІСТРАТОРА (ДЕНИС){no_clr}", bg="#1B1726", fg="#FFD500", font=("Segoe UI", 9, "bold")).pack(side="left")
            if "DRONE" in bc_levels:
                sh_c = tk.Canvas(hdr_bc, width=22, height=22, bg="#1B1726", highlightthickness=0)
                sh_c.pack(side="left", padx=6)
                self._draw_tk_shahed_icon(sh_c, 11, 11, size=9.5)
            tk.Label(bc_frame, text=f"{lv_titles} — {bc['regionName']}", bg="#1B1726", fg="#FFFFFF", font=("Segoe UI", 11, "bold")).pack(anchor="w")
            tk.Label(bc_frame, text=bc.get("note", ""), bg="#1B1726", fg="#D1D5DB", font=("Segoe UI", 9)).pack(anchor="w")

        cols = tk.Frame(self.content_host, bg="#090B10")
        cols.pack(fill="both", expand=True)

        left_wrap = tk.Frame(cols, bg="#090B10")
        left_wrap.pack(side="left", fill="both", expand=True, padx=(0, 10))
        left = self._make_scrollable_frame(left_wrap)

        hero = tk.Frame(left, bg=lv_info["bg"], highlightbackground=lv_info["color"], highlightthickness=2, padx=16, pady=14)
        hero.pack(fill="x", pady=(0, 10))

        badges_row = tk.Frame(hero, bg=lv_info["bg"])
        badges_row.pack(fill="x", pady=(0, 6))
        if not active_alerts:
            tk.Label(badges_row, text=lv_info["title"].upper(), bg="#113825", fg="#22C55E", font=("Segoe UI", 10, "bold"), padx=10, pady=4).pack(side="left")
        else:
            for lv in active_alerts:
                c = ALERT_LEVELS[lv]["color"]
                if lv == "DRONE":
                    sh_badge = tk.Canvas(badges_row, width=22, height=22, bg="#0B0F1A", highlightthickness=0)
                    sh_badge.pack(side="left", padx=(0, 2))
                    self._draw_tk_shahed_icon(sh_badge, 11, 11, size=9.5)
                tk.Label(badges_row, text=ALERT_LEVELS[lv]["short"].upper(), bg="#0B0F1A", fg=c, font=("Segoe UI", 9, "bold"), padx=8, pady=4).pack(side="left", padx=(0, 3))
                if self.store.user_role == "ADMIN":
                    tk.Button(
                        badges_row,
                        text="×",
                        bg="#0B0F1A",
                        fg=c,
                        relief="flat",
                        font=("Segoe UI", 10, "bold"),
                        command=lambda l=lv: self._toggle_ko_alert(l),
                    ).pack(side="left", padx=(0, 8))

        tk.Label(hero, text="Кривоозерська громада (Криве Озеро)", bg=lv_info["bg"], fg="#FFFFFF", font=("Segoe UI", 15, "bold")).pack(anchor="w")
        tk.Label(
            hero,
            text=ko.get("threatDetails") or "Повітряна тривога відсутня. Обстановка спокійна.",
            bg=lv_info["bg"],
            fg="#E5E7EB",
            font=("Segoe UI", 10),
            wraplength=560,
            justify="left",
        ).pack(anchor="w", pady=(3, 8))

        if self.store.user_role == "ADMIN":
            # 1) Master All-Ukraine Alert button
            tk.Button(
                hero,
                text="🚨 ТРИВОГА ПО ВСІЙ УКРАЇНІ (ВСІ РАЙОНИ)",
                bg="#D94436",
                fg="#FFFFFF",
                font=("Segoe UI", 10, "bold"),
                relief="flat",
                padx=14,
                pady=8,
                command=lambda: self._trigger_all_ukraine_alert("RED"),
            ).pack(fill="x", pady=(0, 8))

            # 2) Target Selector Panel (declare alerts via tabs without opening the map)
            target_box = tk.Frame(hero, bg="#0B0F1A", padx=10, pady=8)
            target_box.pack(fill="x", pady=(0, 8))
            t_hdr = tk.Frame(target_box, bg="#0B0F1A")
            t_hdr.pack(fill="x", pady=(0, 4))
            tk.Label(t_hdr, text="Оголошення тривоги по вкладках (без карти):", bg="#0B0F1A", fg="#FFD500", font=("Segoe UI", 9, "bold")).pack(side="left")
            tk.Checkbutton(
                t_hdr,
                text="🔕 Без відбою",
                variable=self.without_all_clear_var,
                bg="#0B0F1A",
                fg="#E5E7EB",
                selectcolor="#182032",
                activebackground="#0B0F1A",
                font=("Segoe UI", 8, "bold"),
            ).pack(side="right")

            target_chips = tk.Frame(target_box, bg="#0B0F1A")
            target_chips.pack(fill="x", pady=(0, 4))

            is_ua = self.dashboard_target_mode == "ALL_UKRAINE"
            tk.Button(
                target_chips,
                text="🇺🇦 Вся Україна",
                bg="#D94436" if is_ua else "#1E293B",
                fg="#FFFFFF",
                font=("Segoe UI", 8, "bold"),
                relief="flat",
                padx=8,
                pady=4,
                command=lambda: self._set_dashboard_target("ALL_UKRAINE", self.dashboard_target_region_id),
            ).pack(side="left", padx=(0, 4))

            quick_regs: List[dict] = []
            seen_ids: Set[str] = set()
            for candidate in [self.store.get_region(KRYVE_OZERO_ID), self.store.get_region(self.dashboard_target_region_id)] + self.store.get_monitored_regions():
                if candidate and candidate["id"] not in seen_ids:
                    seen_ids.add(candidate["id"])
                    quick_regs.append(candidate)
            for qr in quick_regs[:4]:
                is_sel_t = (not is_ua) and (self.dashboard_target_region_id == qr["id"])
                short_lbl = "Криве Озеро" if qr["id"] == KRYVE_OZERO_ID else qr["name"].replace(" район", "")
                tk.Button(
                    target_chips,
                    text=f"📍 {short_lbl}",
                    bg="#0057B7" if is_sel_t else "#1E293B",
                    fg="#FFD500" if is_sel_t else "#D1D5DB",
                    font=("Segoe UI", 8, "bold"),
                    relief="flat",
                    padx=8,
                    pady=4,
                    command=lambda rid=qr["id"]: self._set_dashboard_target("REGION", rid),
                ).pack(side="left", padx=(0, 4))

            # Dropdown for ANY of the 36 districts without opening map
            drop_row = tk.Frame(target_box, bg="#0B0F1A")
            drop_row.pack(fill="x", pady=(2, 0))
            tk.Label(drop_row, text="Або оберіть будь-який район:", bg="#0B0F1A", fg="#9CA3AF", font=("Segoe UI", 8)).pack(side="left", padx=(0, 6))
            reg_names = [f"{r['name']} ({r['oblast']})" for r in self.store.regions]
            cur_reg = self.store.get_region(self.dashboard_target_region_id) or self.store.regions[0]
            combo_var = tk.StringVar(value=f"{cur_reg['name']} ({cur_reg['oblast']})")
            reg_combo = ttk.Combobox(drop_row, textvariable=combo_var, values=reg_names, state="readonly", width=40)
            reg_combo.pack(side="left", fill="x", expand=True)

            def _on_combo_pick(_e=None):
                idx = reg_combo.current()
                if 0 <= idx < len(self.store.regions):
                    self._set_dashboard_target("REGION", self.store.regions[idx]["id"])

            reg_combo.bind("<<ComboboxSelected>>", _on_combo_pick)

            # 3) 4 Category Tabs for Threat Declaration
            tabs_hdr = tk.Frame(target_box, bg="#0B0F1A")
            tabs_hdr.pack(fill="x", pady=(8, 6))
            tab_defs = [
                "Повітря / Шахед",
                "Бої / Арта / ЦЗ",
                "Хімія / Радіо",
                "Комбо / Відбій",
            ]
            for tidx, tname in enumerate(tab_defs):
                active_t = self.dashboard_alert_tab == tidx
                tk.Button(
                    tabs_hdr,
                    text=tname,
                    bg="#FFD500" if active_t else "#182032",
                    fg="#000000" if active_t else "#D1D5DB",
                    font=("Segoe UI", 8, "bold"),
                    relief="flat",
                    padx=8,
                    pady=5,
                    command=lambda i=tidx: self._set_dashboard_alert_tab(i),
                ).pack(side="left", expand=True, fill="x", padx=(0 if tidx == 0 else 4, 0))

            tab_body = tk.Frame(target_box, bg="#0B0F1A")
            tab_body.pack(fill="x", pady=(0, 6))

            if self.dashboard_alert_tab == 0:
                r0 = tk.Frame(tab_body, bg="#0B0F1A")
                r0.pack(fill="x")
                for txt, lv, bgc, fgc in [
                    ("🚨 Повітряна", "RED", "#FF2A3B", "#FFFFFF"),
                    ("🚀 Ракета", "MISSILE", "#FB7185", "#000000"),
                    ("Шахед / БПЛА", "DRONE", "#F59E0B", "#000000"),
                    ("⚠️ Жовтий", "YELLOW", "#FFD500", "#000000"),
                ]:
                    btn_f = tk.Frame(r0, bg=bgc)
                    btn_f.pack(side="left", padx=(0, 5), pady=2)
                    if lv == "DRONE":
                        ic = tk.Canvas(btn_f, width=18, height=18, bg=bgc, highlightthickness=0)
                        ic.pack(side="left", padx=(5, 0))
                        self._draw_tk_shahed_icon(ic, 9, 9, size=7.5, fill="#991B1B", outline="#FFFFFF")
                    tk.Button(btn_f, text=txt, bg=bgc, fg=fgc, font=("Segoe UI", 9, "bold"), relief="flat", padx=8, pady=5, command=lambda level=lv: self._trigger_dashboard_target(level)).pack(side="left")
            elif self.dashboard_alert_tab == 1:
                r1 = tk.Frame(tab_body, bg="#0B0F1A")
                r1.pack(fill="x")
                for txt, lv, bgc, fgc in [
                    ("💥 Артобстріл", "ARTILLERY", "#F97316", "#FFFFFF"),
                    ("🛑 Вуличні бої", "STREET_FIGHTS", "#FF7A00", "#FFFFFF"),
                    ("📢 Цив. захист", "CIVIL_DEFENSE", "#A855F7", "#FFFFFF"),
                    ("📣 Спецсигнал", "CUSTOM_ALERT", "#EC4899", "#FFFFFF"),
                ]:
                    tk.Button(r1, text=txt, bg=bgc, fg=fgc, font=("Segoe UI", 9, "bold"), relief="flat", padx=8, pady=5, command=lambda level=lv: self._trigger_dashboard_target(level)).pack(side="left", padx=(0, 5), pady=2)
            elif self.dashboard_alert_tab == 2:
                r2 = tk.Frame(tab_body, bg="#0B0F1A")
                r2.pack(fill="x")
                for txt, lv, bgc, fgc in [
                    ("☣️ Хімічна загроза", "CHEMICAL", "#84CC16", "#000000"),
                    ("☢️ Радіаційна небезпека", "RADIATION", "#EAB308", "#000000"),
                ]:
                    tk.Button(r2, text=txt, bg=bgc, fg=fgc, font=("Segoe UI", 9, "bold"), relief="flat", padx=12, pady=6, command=lambda level=lv: self._trigger_dashboard_target(level)).pack(side="left", padx=(0, 6), pady=2)
            else:
                checks_grid = tk.Frame(tab_body, bg="#0B0F1A")
                checks_grid.pack(fill="x", pady=(0, 6))
                for idx, lv in enumerate(DANGER_ORDER):
                    var = self.multi_ko_vars.setdefault(lv, tk.BooleanVar(value=False))
                    tk.Checkbutton(
                        checks_grid,
                        text=ALERT_LEVELS[lv]["short"],
                        variable=var,
                        bg="#0B0F1A",
                        fg=ALERT_LEVELS[lv]["color"],
                        selectcolor="#182032",
                        activebackground="#0B0F1A",
                        font=("Segoe UI", 8, "bold"),
                    ).grid(row=idx // 4, column=idx % 4, sticky="w", padx=4, pady=2)
                tk.Button(
                    tab_body,
                    text="🚨 Запустити вибрані комбо-тривоги",
                    bg="#FFD500",
                    fg="#000000",
                    font=("Segoe UI", 9, "bold"),
                    relief="flat",
                    padx=12,
                    pady=6,
                    command=self._launch_multi_target_from_vars,
                ).pack(fill="x", pady=(0, 6))

                # Per-type voice all-clear buttons
                clr_spec = tk.Frame(tab_body, bg="#0B0F1A")
                clr_spec.pack(fill="x")
                for txt, lv, bgc, fgc in [
                    ("📢 Відбій ЦЗ", "CIVIL_DEFENSE", "#3E124A", "#E9D5FF"),
                    ("🛡️ Відбій боїв", "STREET_FIGHTS", "#1E3A2B", "#22C55E"),
                    ("💥 Відбій арти", "ARTILLERY", "#1E3A2B", "#FDBA74"),
                    ("☣️ Відбій хім.", "CHEMICAL", "#1E3A2B", "#BEF264"),
                    ("☢️ Відбій рад.", "RADIATION", "#1E3A2B", "#FDE047"),
                ]:
                    tk.Button(clr_spec, text=txt, bg=bgc, fg=fgc, font=("Segoe UI", 8, "bold"), relief="flat", padx=6, pady=4, command=lambda l=lv: self._trigger_specific_clear_target(l)).pack(side="left", padx=(0, 4))

            # Always-available All-Clear action row
            clr_main = tk.Frame(target_box, bg="#0B0F1A")
            clr_main.pack(fill="x", pady=(2, 0))
            tk.Button(
                clr_main,
                text="🟢 Відбій для цілі (з голосом)",
                bg="#22C55E",
                fg="#000000",
                font=("Segoe UI", 9, "bold"),
                relief="flat",
                padx=10,
                pady=5,
                command=lambda: self._clear_dashboard_target(without_clear=False),
            ).pack(side="left", padx=(0, 5))
            tk.Button(
                clr_main,
                text="🔕 Зняти без відбою",
                bg="#1E293B",
                fg="#E5E7EB",
                font=("Segoe UI", 8, "bold"),
                relief="flat",
                padx=8,
                pady=5,
                command=lambda: self._clear_dashboard_target(without_clear=True),
            ).pack(side="left", padx=(0, 5))
            tk.Button(
                clr_main,
                text="🟢 ВЕСЬ ВІДБІЙ (УКРАЇНА)",
                bg="#14532D",
                fg="#86EFAC",
                font=("Segoe UI", 8, "bold"),
                relief="flat",
                padx=8,
                pady=5,
                command=self._trigger_total_all_clear,
            ).pack(side="right")
        else:
            tk.Label(
                hero,
                text=(
                    "🔒 Режим спостереження: тривоги вмикають лише адміністратори "
                    f"({ADMIN_OWNER_EMAIL}, {ADMIN_SECOND_EMAIL}). Увійдіть під Google акаунтом адміна для керування."
                ),
                bg=lv_info["bg"],
                fg="#9CA3AF",
                font=("Segoe UI", 9),
                wraplength=560,
                justify="left",
            ).pack(anchor="w")

        # Monitored districts (max 3)
        mon_card = tk.Frame(left, bg="#131826", padx=14, pady=10)
        mon_card.pack(fill="x")
        monitored = self.store.get_monitored_regions()
        dangers_count = sum(1 for r in self.store.regions if ALERT_LEVELS.get(r["currentLevel"], {}).get("danger"))
        hdr_mon = tk.Frame(mon_card, bg="#131826")
        hdr_mon.pack(fill="x", pady=(0, 6))
        tk.Label(
            hdr_mon,
            text=f"ЗАКРІПЛЕНІ РАЙОНИ ({len(monitored)}/{MAX_PINNED_REGIONS}) • У тривозі по Україні: {dangers_count}",
            bg="#131826",
            fg="#9CA3AF",
            font=("Segoe UI", 9, "bold"),
        ).pack(side="left")
        tk.Button(
            hdr_mon,
            text="+ Обрати райони (до 3)",
            bg="#1E293B",
            fg="#FFD500",
            font=("Segoe UI", 8, "bold"),
            relief="flat",
            padx=8,
            pady=3,
            command=lambda: self._select_tab(1, save=True),
        ).pack(side="right")

        chips = tk.Frame(mon_card, bg="#131826")
        chips.pack(fill="x")
        for r in monitored[:MAX_PINNED_REGIONS]:
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

        right = tk.Frame(cols, bg="#131826", width=340, padx=14, pady=12)
        right.pack(side="right", fill="y")
        right.pack_propagate(False)
        tk.Label(right, text="ОСТАННІ ОПОВІЩЕННЯ ОНЛАЙН", bg="#131826", fg="#FFD500", font=("Segoe UI", 10, "bold")).pack(anchor="w", pady=(0, 8))

        feed = scrolledtext.ScrolledText(right, bg="#0D111A", fg="#E5E7EB", font=("Segoe UI", 9), relief="flat", wrap="word", state="normal")
        feed.pack(fill="both", expand=True)
        for ev in self.store.events[:40]:
            tstr = datetime.fromtimestamp(ev["timestamp"] / 1000.0).strftime("%d.%m %H:%M")
            lvls = ev.get("activeLevels") or [ev["level"]]
            lv_label = " + ".join(("🛸 Шахед" if x == "DRONE" else ALERT_LEVELS.get(x, {}).get("short", x)) for x in lvls)
            feed.insert("end", f"[{tstr}] {ev['regionName']}\n{lv_label}: {ev['threatDetails']}\n\n")
        feed.configure(state="disabled")

    def _render_map_tab(self) -> None:
        container = tk.Frame(self.content_host, bg="#090B10")
        container.pack(fill="both", expand=True)

        map_card = tk.Frame(container, bg="#0A0D13", padx=12, pady=10)
        map_card.pack(side="left", fill="both", expand=True, padx=(0, 10))

        now_str = datetime.now().strftime("%d.%m.%Y %H:%M")
        hdr = tk.Frame(map_card, bg="#0A0D13")
        hdr.pack(fill="x", pady=(0, 6))
        title_col = tk.Frame(hdr, bg="#0A0D13")
        title_col.pack(side="left")
        t_row = tk.Frame(title_col, bg="#0A0D13")
        t_row.pack(anchor="w")
        tk.Label(t_row, text="Мапа тривог", bg="#0A0D13", fg="#FFFFFF", font=("Segoe UI", 12, "bold")).pack(side="left")
        tk.Label(t_row, text=" v2.0 ", bg="#D94436", fg="#FFFFFF", font=("Segoe UI", 8, "bold"), padx=4).pack(side="left", padx=6)
        drone_cnt = sum(1 for r in self.store.regions if "DRONE" in (r.get("activeAlerts") or []) or r.get("currentLevel") == "DRONE")
        if drone_cnt > 0:
            tk.Label(t_row, text=f"Шахеди: {drone_cnt}", bg="#3B1214", fg="#FCA5A5", font=("Segoe UI", 8, "bold"), padx=6, pady=1).pack(side="left")
        tk.Label(title_col, text=f"Станом на {now_str}", bg="#0A0D13", fg="#9CA3AF", font=("Segoe UI", 8)).pack(anchor="w")

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
        ).pack(side="right", padx=(4, 0))

        tk.Button(
            hdr,
            text="ⓘ Легенда",
            bg="#1E293B" if not self.map_show_legend else "#FFD500",
            fg="#FFFFFF" if not self.map_show_legend else "#000000",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=8,
            pady=4,
            command=self._toggle_map_legend,
        ).pack(side="right", padx=(4, 0))

        if self.store.user_role == "ADMIN":
            tk.Button(
                hdr,
                text="🚨 Вся Україна",
                bg="#D94436",
                fg="#FFFFFF",
                font=("Segoe UI", 9, "bold"),
                relief="flat",
                padx=10,
                pady=4,
                command=lambda: self._trigger_all_ukraine_alert("RED"),
            ).pack(side="right")

        if self.map_show_legend:
            leg = tk.Frame(map_card, bg="#131826", highlightbackground="#2A3447", highlightthickness=1, padx=10, pady=6)
            leg.pack(fill="x", pady=(0, 6))
            tk.Label(
                leg,
                text="🟥 Повітряна / Ракета   🟨 БПЛА / Шахеди   ▒ Часткова тривога по районах   ⬛ Немає тривоги   ▲ Значок Шахеда",
                bg="#131826",
                fg="#E5E7EB",
                font=("Segoe UI", 8, "bold"),
            ).pack(anchor="w")

        canvas = tk.Canvas(map_card, bg="#0A0D13", highlightthickness=1, highlightbackground="#1E2538")
        canvas.pack(fill="both", expand=True)

        def draw_map(_evt=None):
            canvas.delete("all")
            w = max(canvas.winfo_width(), 440)
            h = max(canvas.winfo_height(), 320)
            pad_x = w * 0.04
            pad_y = h * 0.04
            draw_w = w - 2 * pad_x
            draw_h = h - 2 * pad_y

            sel_id = self.store.selected_region_id
            for poly in UKRAINE_OBLAST_POLYGONS:
                kws = poly["keywords"]
                matching = [
                    r for r in self.store.regions
                    if any(kw in f"{r['id']} {r['name']} {r['oblast']}".lower() for kw in kws)
                ]
                dangerous = [r for r in matching if ALERT_LEVELS.get(r["currentLevel"], {}).get("danger")]
                is_full = bool(matching) and len(dangerous) == len(matching)
                is_partial = bool(dangerous) and not is_full
                if dangerous:
                    dom = max(dangerous, key=lambda r: ALERT_LEVELS.get(r["currentLevel"], {}).get("priority", 0))
                    dom_color = alerts_ua_polygon_color(dom["currentLevel"])
                else:
                    dom_color = "#28303D"

                pts_flat = [
                    coord
                    for nx, ny in poly["points"]
                    for coord in (pad_x + nx * draw_w, pad_y + ny * draw_h)
                ]
                is_poly_sel = any(r["id"] == sel_id for r in matching)
                tag = f"poly_{poly['id']}"
                # Base polygon
                canvas.create_polygon(
                    *pts_flat,
                    fill=dom_color if is_full else "#28303D",
                    outline="#FFFFFF" if is_poly_sel else "#10141C",
                    width=2.4 if is_poly_sel else 1.4,
                    tags=(tag,),
                )
                # Partial oblast alert overlay (hatched stipple effect matching alerts.in.ua)
                if is_partial:
                    canvas.create_polygon(
                        *pts_flat,
                        fill=dom_color,
                        stipple="gray50",
                        outline="#FFFFFF" if is_poly_sel else "#10141C",
                        width=2.4 if is_poly_sel else 1.4,
                        tags=(tag,),
                    )

                target_rid = (dangerous[0]["id"] if dangerous else poly["primaryRegionId"])
                canvas.tag_bind(tag, "<Button-1>", lambda _e, rid=target_rid: self._select_region_on_map(rid))

            # Render district markers and complete Shahed-136 icons on top
            for reg in self.store.regions:
                px = pad_x + reg["mapNormX"] * draw_w
                py = pad_y + reg["mapNormY"] * draw_h
                is_ko = reg["id"] == KRYVE_OZERO_ID
                is_sel = reg["id"] == sel_id
                active = reg.get("activeAlerts", [])
                has_drone = ("DRONE" in active) or (reg["currentLevel"] == "DRONE")
                tag = f"reg_{reg['id']}"

                if has_drone:
                    self._draw_tk_shahed_icon(canvas, px, py, size=13.0, fill="#EF4444", outline="#FFFFFF")
                    canvas.create_oval(px - 14, py - 14, px + 14, py + 14, fill="", outline="", tags=(tag,))
                elif is_ko or is_sel or reg["currentLevel"] != "SAFE":
                    color = ALERT_LEVELS[reg["currentLevel"]]["color"]
                    r = 7 if is_ko else 5
                    if is_ko:
                        canvas.create_oval(px - 11, py - 11, px + 11, py + 11, outline="#FFD500", width=2)
                    if is_sel:
                        canvas.create_oval(px - 14, py - 14, px + 14, py + 14, outline="#FFFFFF", width=2)
                    canvas.create_oval(px - r, py - r, px + r, py + r, fill=color, outline="#0A0D13", width=1.2, tags=(tag,))

                if is_ko or is_sel:
                    short_name = "Криве Озеро" if is_ko else reg["name"].replace(" район", " р-н")
                    canvas.create_text(px, py - 16, text=short_name, fill="#FFFFFF", font=("Segoe UI", 8, "bold"))

                canvas.tag_bind(tag, "<Button-1>", lambda _e, rid=reg["id"]: self._select_region_on_map(rid))

        canvas.bind("<Configure>", draw_map)
        self.after(50, draw_map)

        side = tk.Frame(container, bg="#131826", width=390, padx=14, pady=12)
        side.pack(side="right", fill="y")
        side.pack_propagate(False)

        sel = self.store.get_region(self.store.selected_region_id) or self.store.regions[0]
        tk.Label(side, text=sel["name"], bg="#131826", fg="#FFFFFF", font=("Segoe UI", 12, "bold"), wraplength=350, justify="left").pack(anchor="w")
        tk.Label(side, text=sel["oblast"], bg="#131826", fg="#9CA3AF", font=("Segoe UI", 8)).pack(anchor="w")
        tk.Label(
            side,
            text=sel.get("threatDetails") or f"Статус: {ALERT_LEVELS[sel['currentLevel']]['title']}",
            bg="#131826",
            fg=ALERT_LEVELS[sel["currentLevel"]]["color"],
            font=("Segoe UI", 9, "bold"),
            wraplength=350,
            justify="left",
        ).pack(anchor="w", pady=(4, 6))

        is_pinned = bool(sel.get("isMonitored"))
        tk.Button(
            side,
            text=("⭐ Відкріпити з головної" if is_pinned else f"☆ Закріпити на головній (макс. {MAX_PINNED_REGIONS})"),
            bg="#1E293B" if not is_pinned else "#382906",
            fg="#FFD500",
            font=("Segoe UI", 8, "bold"),
            relief="flat",
            pady=4,
            command=lambda rid=sel["id"]: self._toggle_pin_region(rid),
        ).pack(fill="x", pady=(0, 8))

        if self.store.user_role == "ADMIN":
            tk.Button(
                side,
                text="🚨 ТРИВОГА ПО ВСІЙ УКРАЇНІ",
                bg="#D94436",
                fg="#FFFFFF",
                font=("Segoe UI", 9, "bold"),
                relief="flat",
                pady=5,
                command=lambda: self._trigger_all_ukraine_alert("RED"),
            ).pack(fill="x", pady=(0, 6))

            tk.Label(side, text="Вмикати / вимикати загрози для району:", bg="#131826", fg="#FFD500", font=("Segoe UI", 9, "bold")).pack(anchor="w")
            toggles_frame = tk.Frame(side, bg="#131826")
            toggles_frame.pack(fill="x", pady=4)
            active_set = set(sel.get("activeAlerts", []))
            for idx, lv in enumerate(DANGER_ORDER):
                is_on = lv in active_set
                tk.Button(
                    toggles_frame,
                    text=("✓ " if is_on else "+ ") + ALERT_LEVELS[lv]["short"],
                    bg=ALERT_LEVELS[lv]["color"] if is_on else "#1B2234",
                    fg="#000000" if (is_on and lv in ("YELLOW", "CHEMICAL", "RADIATION", "DRONE", "MISSILE")) else ("#FFFFFF" if is_on else "#9CA3AF"),
                    font=("Segoe UI", 8, "bold"),
                    relief="flat",
                    padx=6,
                    pady=3,
                    command=lambda level=lv, rid=sel["id"]: self._toggle_region_alert(rid, level),
                ).grid(row=idx // 2, column=idx % 2, sticky="ew", padx=2, pady=2)
            toggles_frame.columnconfigure(0, weight=1)
            toggles_frame.columnconfigure(1, weight=1)

            tk.Button(
                side,
                text="🟢 Відбій для цього району",
                bg="#22C55E",
                fg="#000000",
                font=("Segoe UI", 9, "bold"),
                relief="flat",
                pady=5,
                command=lambda rid=sel["id"]: self._clear_single_region(rid),
            ).pack(fill="x", pady=(4, 8))

        tk.Label(side, text="УСІ РАЙОНИ УКРАЇНИ:", bg="#131826", fg="#9CA3AF", font=("Segoe UI", 9, "bold")).pack(anchor="w", pady=(2, 4))
        lb = tk.Listbox(side, bg="#0D111A", fg="#E5E7EB", selectbackground="#1E293B", font=("Segoe UI", 9), relief="flat")
        lb.pack(fill="both", expand=True)
        for reg in self.store.regions:
            act = reg.get("activeAlerts", [])
            star = "⭐ " if reg.get("isMonitored") else ""
            badge = f"[{len(act)} загроз]" if len(act) > 1 else f"[{ALERT_LEVELS[reg['currentLevel']]['short']}]"
            lb.insert("end", f"{star}{reg['name']} — {badge}")
        lb.bind("<<ListboxSelect>>", lambda _e: self._on_listbox_region_select(lb))

    def _render_discussion_tab(self) -> None:
        card = tk.Frame(self.content_host, bg="#131826", padx=16, pady=14)
        card.pack(fill="both", expand=True)

        tk.Label(card, text="💬 ОБГОВОРЕННЯ СИТУАЦІЇ (GOOGLE FIRESTORE + ЕФІР)", bg="#131826", fg="#FFD500", font=("Segoe UI", 12, "bold")).pack(anchor="w")
        tk.Label(
            card,
            text="Повідомлення синхронізуються між комп'ютерами та Android-телефонами громади в реальному часі.",
            bg="#131826",
            fg="#9CA3AF",
            font=("Segoe UI", 9),
        ).pack(anchor="w", pady=(2, 10))

        chat_box = scrolledtext.ScrolledText(card, bg="#0D111A", fg="#F3F4F6", font=("Segoe UI", 10), relief="flat", wrap="word")
        chat_box.pack(fill="both", expand=True, pady=(0, 10))
        for c in self.store.comments:
            tstr = datetime.fromtimestamp(c["timestamp"] / 1000.0).strftime("%d.%m %H:%M")
            prefix = "👑 [ОФІЦІЙНО] " if c.get("isAdminVerified") else ""
            chat_box.insert("end", f"{prefix}{c['authorName']} ({c.get('regionTag', '')}) • {tstr}\n{c['text']}\n\n")
        chat_box.configure(state="disabled")

        input_row = tk.Frame(card, bg="#131826")
        input_row.pack(fill="x")

        name_entry = None
        if self.store.user_role != "ADMIN":
            name_entry = tk.Entry(input_row, bg="#0D111A", fg="#FFFFFF", insertbackground="#FFFFFF", font=("Segoe UI", 10), width=22)
            name_entry.insert(0, self.store.firebase.display_name or self.store.author_name)
            name_entry.pack(side="left", padx=(0, 8), ipady=6)

        msg_entry = tk.Entry(input_row, bg="#0D111A", fg="#FFFFFF", insertbackground="#FFFFFF", font=("Segoe UI", 10))
        msg_entry.pack(side="left", fill="x", expand=True, padx=(0, 8), ipady=6)

        def send():
            txt = msg_entry.get().strip()
            if not txt:
                return
            fallback_admin = (
                self.store.firebase.display_name.strip()
                or ("Адміністратор #2" if self.store.firebase.email.strip().lower() == ADMIN_SECOND_EMAIL.lower() else ADMIN_OWNER_NAME)
            )
            author = name_entry.get().strip() if name_entry is not None else fallback_admin
            self.store.post_comment(txt, author)
            msg_entry.delete(0, "end")
            self._render_current_tab()

        msg_entry.bind("<Return>", lambda _e: send())
        tk.Button(input_row, text="Надіслати", bg="#FFD500", fg="#000000", font=("Segoe UI", 10, "bold"), relief="flat", padx=16, pady=6, command=send).pack(side="right")

    def _render_audio_tab(self) -> None:
        inner = self._make_scrollable_frame(self.content_host)
        card = tk.Frame(inner, bg="#131826", padx=16, pady=14)
        card.pack(fill="both", expand=True)

        tk.Label(card, text="🔊 ГОЛОСОВІ СИГНАЛИ (ЗВУЧАТЬ 1 РАЗ БЕЗ ПОВТОРУ)", bg="#131826", fg="#FFFFFF", font=("Segoe UI", 12, "bold")).pack(anchor="w")
        tk.Label(
            card,
            text="Усі записи очищені від подвоєння й відтворюються рівно 1 раз. При одночасних тривогах звучать послідовно без дублікатів.",
            bg="#131826",
            fg="#22C55E",
            font=("Segoe UI", 9, "bold"),
        ).pack(anchor="w", pady=(2, 10))

        for lv in ["RED", "MISSILE", "DRONE", "ARTILLERY", "CHEMICAL", "RADIATION", "STREET_FIGHTS", "CIVIL_DEFENSE", "YELLOW", "CUSTOM_ALERT", "SAFE"]:
            info = ALERT_LEVELS[lv]
            row = tk.Frame(card, bg="#0D111A", padx=12, pady=6)
            row.pack(fill="x", pady=3)
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

        tk.Label(card, text="ПЕРСОНАЛЬНІ СИГНАЛИ ВІДБОЮ ЗАГРОЗ:", bg="#131826", fg="#22C55E", font=("Segoe UI", 10, "bold")).pack(anchor="w", pady=(10, 4))
        for lv_key, fn, title in [
            ("CIVIL_DEFENSE", "alert_civil_defense_clear.m4a", "Відбій цивільного захисту"),
            ("ARTILLERY", "alert_artillery_clear.m4a", "Відбій загрози артобстрілу"),
            ("CHEMICAL", "alert_chemical_clear.m4a", "Відбій хімічної загрози"),
            ("RADIATION", "alert_radiation_clear.m4a", "Відбій радіаційної небезпеки"),
        ]:
            row = tk.Frame(card, bg="#0B2418", padx=12, pady=6)
            row.pack(fill="x", pady=3)
            tk.Label(row, text=title, bg="#0B2418", fg="#86EFAC", font=("Segoe UI", 10, "bold")).pack(side="left")
            tk.Button(
                row,
                text="▶ Слухати відбій (1 раз)",
                bg="#14532D",
                fg="#FFFFFF",
                font=("Segoe UI", 9, "bold"),
                relief="flat",
                padx=12,
                pady=4,
                command=lambda f=fn, t=title: self.audio_player.preview_file(f, t, self.store.sound_volume),
            ).pack(side="right")

        vol_row = tk.Frame(card, bg="#131826", pady=10)
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
                text=(
                    f"Керувати тривогами можуть лише адміністратори ({', '.join(ADMIN_EMAILS)}).\n"
                    "Увійдіть через Google акаунт адміністратора або розблокуйте панель адміністратора."
                ),
                bg="#131826",
                fg="#9CA3AF",
                font=("Segoe UI", 10),
                justify="center",
            ).pack(pady=(0, 14))
            btns = tk.Frame(box, bg="#131826")
            btns.pack()
            tk.Button(btns, text="🔵 Увійти через Google", bg="#2563EB", fg="#FFFFFF", font=("Segoe UI", 10, "bold"), relief="flat", padx=16, pady=8, command=self._open_google_auth_dialog).pack(side="left", padx=6)
            tk.Button(btns, text="🔑 Локальний PIN-код", bg="#0057B7", fg="#FFFFFF", font=("Segoe UI", 10, "bold"), relief="flat", padx=16, pady=8, command=self._on_role_button_click).pack(side="left", padx=6)
            return

        inner = self._make_scrollable_frame(self.content_host)

        c1 = tk.Frame(inner, bg="#131826", padx=16, pady=14)
        c1.pack(fill="x", pady=(0, 12))
        tk.Label(c1, text="👑 ПАНЕЛЬ АДМІНІСТРАТОРА ДЕНИСА • v2.0 • БАГАТОРАЙОННИЙ ЗАПУСК", bg="#131826", fg="#FFD500", font=("Segoe UI", 11, "bold")).pack(anchor="w")
        tk.Label(c1, text="Оберіть кілька районів, увімкніть тривогу по всій Україні або запустіть пресет:", bg="#131826", fg="#9CA3AF", font=("Segoe UI", 9)).pack(anchor="w", pady=(2, 8))

        tk.Button(
            c1,
            text="🚨 ТРИВОГА ПО ВСІЙ УКРАЇНІ (ВСІ РАЙОНИ)",
            bg="#D94436",
            fg="#FFFFFF",
            font=("Segoe UI", 10, "bold"),
            relief="flat",
            pady=8,
            command=lambda: self._trigger_all_ukraine_alert("RED"),
        ).pack(fill="x", pady=(0, 8))

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
            text="🔒 Перемкнутися в режим перегляду",
            bg="#1E293B",
            fg="#E5E7EB",
            font=("Segoe UI", 9),
            relief="flat",
            padx=12,
            pady=6,
            command=self._lock_to_user_role,
        ).pack(side="right")

        reg_grid = tk.Frame(c1, bg="#0D111A", padx=8, pady=8)
        reg_grid.pack(fill="x", pady=6)
        for idx, reg in enumerate(self.store.regions):
            var = self.admin_region_multi_vars.setdefault(reg["id"], tk.BooleanVar(value=(reg["id"] == KRYVE_OZERO_ID)))
            tk.Checkbutton(
                reg_grid,
                text=reg["name"],
                variable=var,
                bg="#0D111A",
                fg="#E5E7EB",
                selectcolor="#1E293B",
                activebackground="#0D111A",
                font=("Segoe UI", 8),
            ).grid(row=idx // 3, column=idx % 3, sticky="w", padx=6, pady=2)

        admin_threats_row = tk.Frame(c1, bg="#0D111A", padx=8, pady=6)
        admin_threats_row.pack(fill="x", pady=(0, 6))
        for idx, lv in enumerate(DANGER_ORDER):
            tk.Checkbutton(
                admin_threats_row,
                text=ALERT_LEVELS[lv]["short"],
                variable=self.multi_ko_vars[lv],
                bg="#0D111A",
                fg=ALERT_LEVELS[lv]["color"],
                selectcolor="#1E293B",
                activebackground="#0D111A",
                font=("Segoe UI", 9, "bold"),
            ).grid(row=idx // 5, column=idx % 5, sticky="w", padx=8, pady=2)

        note_entry = tk.Entry(c1, bg="#0D111A", fg="#FFFFFF", insertbackground="#FFFFFF", font=("Segoe UI", 10))
        note_entry.pack(fill="x", pady=6, ipady=5)

        tk.Button(
            c1,
            text="🚨 Оголосити вибрані тривоги для обраних районів",
            bg="#FF2A3B",
            fg="#FFFFFF",
            font=("Segoe UI", 10, "bold"),
            relief="flat",
            pady=7,
            command=lambda: self._apply_multi_region_from_vars(note_entry.get()),
        ).pack(fill="x")

        c2 = tk.Frame(inner, bg="#131826", padx=16, pady=14)
        c2.pack(fill="x", pady=(0, 12))
        tk.Label(c2, text="🌐 СИНХРОНІЗАЦІЯ ТА ЗБЕРЕЖЕННЯ (НАСТУПНОГО ДНЯ ВСЕ ЗАЛИШАЄТЬСЯ)", bg="#131826", fg="#22C55E", font=("Segoe UI", 11, "bold")).pack(anchor="w")
        tk.Label(c2, text=f"Локальна база: {self.store.storage_path}", bg="#131826", fg="#9CA3AF", font=("Segoe UI", 8)).pack(anchor="w", pady=(2, 8))

        dsns_var = tk.BooleanVar(value=self.store.dsns_auto_sync)
        tk.Checkbutton(
            c2,
            text="Додатково авто-синхронізувати API ДСНС (вимкнено за замовчуванням — тривоги вмикаєте лише ви)",
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
            text="Зберегти канал",
            bg="#0057B7",
            fg="#FFFFFF",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=12,
            pady=5,
            command=lambda: self._save_channel(ch_entry.get()),
        ).pack(side="right")

        c3 = tk.Frame(inner, bg="#131826", padx=16, pady=14)
        c3.pack(fill="x")
        tk.Label(c3, text="🔐 ЛОКАЛЬНИЙ PIN-КОД АДМІНІСТРАТОРА", bg="#131826", fg="#FFFFFF", font=("Segoe UI", 10, "bold")).pack(anchor="w", pady=(0, 6))
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
    def _set_dashboard_target(self, mode: str, region_id: str) -> None:
        self.dashboard_target_mode = mode
        if region_id:
            self.dashboard_target_region_id = region_id
        self._render_current_tab()

    def _set_dashboard_alert_tab(self, idx: int) -> None:
        self.dashboard_alert_tab = idx
        self._render_current_tab()

    def _toggle_map_legend(self) -> None:
        self.map_show_legend = not self.map_show_legend
        self._render_current_tab()

    def _trigger_dashboard_target(self, level: str) -> None:
        no_clr = self.without_all_clear_var.get()
        if self.dashboard_target_mode == "ALL_UKRAINE":
            self._trigger_all_ukraine_alert(level)
            return
        res = self.store.set_region_active_alerts(
            self.dashboard_target_region_id,
            {level} if level != "SAFE" else set(),
            without_all_clear=no_clr,
        )
        if res:
            reg, lvls, exp = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"], exp)
        self._render_current_tab()

    def _trigger_specific_clear_target(self, level: str) -> None:
        no_clr = self.without_all_clear_var.get()
        if self.dashboard_target_mode == "ALL_UKRAINE":
            res = self.store.trigger_total_all_clear(without_all_clear=no_clr)
            lvls, exp = res
            if lvls or exp:
                self._announce_locally("Вся Україна", lvls, ALERT_LEVELS[level]["clear_title"], exp)
            self._render_current_tab()
            return
        res = self.store.trigger_specific_all_clear(self.dashboard_target_region_id, level, without_all_clear=no_clr)
        if res:
            reg, lvls, exp = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"], exp, ALERT_LEVELS[level]["clear_title"])
        self._render_current_tab()

    def _clear_dashboard_target(self, without_clear: bool = False) -> None:
        if self.dashboard_target_mode == "ALL_UKRAINE":
            lvls, exp = self.store.trigger_total_all_clear(without_all_clear=without_clear)
            if lvls or exp:
                self._announce_locally("Вся Україна", lvls, "Тотальний відбій! Небезпека минула.", exp)
            self._render_current_tab()
            return
        res = self.store.set_region_active_alerts(
            self.dashboard_target_region_id,
            set(),
            without_all_clear=without_clear,
            emit_audio=not without_clear,
        )
        if res:
            reg, lvls, exp = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"], exp)
        self._render_current_tab()

    def _launch_multi_target_from_vars(self) -> None:
        chosen = {lv for lv, var in self.multi_ko_vars.items() if var.get()}
        if not chosen:
            messagebox.showwarning("Увага", "Оберіть хоча б одну тривогу.")
            return
        if self.dashboard_target_mode == "ALL_UKRAINE":
            res = self.store.trigger_all_ukraine_alert(
                levels=chosen,
                without_all_clear=self.without_all_clear_var.get(),
            )
            if res:
                lvls, exp = res
                self._announce_locally("ВСЯ ТЕРИТОРІЯ УКРАЇНИ", lvls, "Тривога по всій Україні!", exp)
            self._render_current_tab()
            return
        self._apply_multi_alerts([self.dashboard_target_region_id], chosen, "")

    def _trigger_all_ukraine_alert(self, level: str = "RED") -> None:
        res = self.store.trigger_all_ukraine_alert(
            level=level,
            without_all_clear=self.without_all_clear_var.get(),
        )
        if res:
            lvls, exp = res
            self._announce_locally(
                "ВСЯ ТЕРИТОРІЯ УКРАЇНИ",
                lvls,
                f"ВСЯ УКРАЇНА: {ALERT_LEVELS[level]['note']}",
                exp,
            )
        self._render_current_tab()

    def _trigger_single_ko(self, level: str) -> None:
        res = self.store.set_region_active_alerts(
            KRYVE_OZERO_ID,
            {level} if level != "SAFE" else set(),
            without_all_clear=self.without_all_clear_var.get(),
        )
        if res:
            reg, lvls, exp = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"], exp)
        self._render_current_tab()

    def _toggle_ko_alert(self, level: str) -> None:
        res = self.store.toggle_alert_in_region(KRYVE_OZERO_ID, level, without_all_clear=self.without_all_clear_var.get())
        if res:
            reg, lvls, exp = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"], exp)
        self._render_current_tab()

    def _trigger_specific_clear(self, level: str) -> None:
        res = self.store.trigger_specific_all_clear(KRYVE_OZERO_ID, level, without_all_clear=self.without_all_clear_var.get())
        if res:
            reg, lvls, exp = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"], exp, ALERT_LEVELS[level]["clear_title"])
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
            messagebox.showwarning("Увага", "Оберіть хоча б один район.")
            return
        self._apply_multi_alerts(rids, chosen, note)

    def _apply_multi_alerts(self, region_ids: List[str], levels: Set[str], note: str) -> None:
        res = self.store.set_multiple_regions_alerts(
            region_ids,
            levels,
            note=note,
            without_all_clear=self.without_all_clear_var.get(),
        )
        if res:
            reg, lvls, exp = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"], exp)
        self._render_current_tab()

    def _trigger_total_all_clear(self) -> None:
        no_clr = self.without_all_clear_var.get()
        lvls, exp = self.store.trigger_total_all_clear(without_all_clear=no_clr)
        if lvls or exp:
            self._announce_locally("Вся Україна", lvls, "Тотальний відбій! Небезпека минула.", exp)
        self._render_current_tab()

    def _select_region_on_map(self, region_id: str) -> None:
        self.store.selected_region_id = region_id
        self.store._save()
        if self.store.firebase.is_signed_in:
            self.store._sync_profile_async()
        self._render_current_tab()

    def _toggle_pin_region(self, region_id: str) -> None:
        ok = self.store.toggle_monitored_region(region_id)
        if not ok and self.store.pinned_limit_message:
            messagebox.showwarning("Закріплені райони", self.store.pinned_limit_message)
        self._render_current_tab()

    def _toggle_region_alert(self, region_id: str, level: str) -> None:
        res = self.store.toggle_alert_in_region(region_id, level, without_all_clear=self.without_all_clear_var.get())
        if res:
            reg, lvls, exp = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"], exp)
        self._render_current_tab()

    def _clear_single_region(self, region_id: str) -> None:
        no_clr = self.without_all_clear_var.get()
        res = self.store.set_region_active_alerts(region_id, set(), without_all_clear=no_clr, emit_audio=not no_clr)
        if res:
            reg, lvls, exp = res
            self._announce_locally(reg["name"], lvls, reg["threatDetails"], exp)
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
        self.store._explicit_lock = True
        self.store.local_admin_unlocked = False
        self.store.user_role = "USER"
        self.store._save()
        self._update_header_badges()
        self._render_current_tab()

    def _on_role_button_click(self) -> None:
        if self.store.user_role == "ADMIN":
            if messagebox.askyesno("Режим користувача", "Перемкнутися в режим спостереження?"):
                self._lock_to_user_role()
            return

        if self.store.firebase.is_signed_in and not self.store.firebase.is_verified_admin:
            messagebox.showinfo(
                "Доступ адміністратора",
                f"Ви увійшли як {self.store.firebase.email}.\nПрава адміністратора належать виключно {', '.join(ADMIN_EMAILS)}.",
            )
            return

        if self.store.firebase.is_verified_admin:
            self.store._explicit_lock = False
            self.store.local_admin_unlocked = True
            self.store.user_role = "ADMIN"
            self.store._save()
            self._update_header_badges()
            self._render_current_tab()
            return

        dlg = tk.Toplevel(self)
        dlg.title("Вхід адміністратора")
        dlg.geometry("420x200")
        dlg.configure(bg="#131826")
        dlg.transient(self)
        dlg.grab_set()
        tk.Label(dlg, text=f"Адміністратори: {', '.join(ADMIN_EMAILS)}", bg="#131826", fg="#FFD500", font=("Segoe UI", 9, "bold")).pack(pady=(14, 6))
        tk.Label(dlg, text="Введіть локальний PIN-код або увійдіть через Google:", bg="#131826", fg="#E5E7EB", font=("Segoe UI", 9)).pack()
        pin_in = tk.Entry(dlg, show="*", bg="#0D111A", fg="#FFFFFF", insertbackground="#FFFFFF", font=("Segoe UI", 11), justify="center")
        pin_in.pack(pady=8, ipady=4)
        pin_in.focus_set()

        def verify():
            if pin_in.get().strip() == self.store.admin_pin:
                self.store._explicit_lock = False
                self.store.local_admin_unlocked = True
                self.store.user_role = "ADMIN"
                self.store._save()
                dlg.destroy()
                self._update_header_badges()
                self._render_current_tab()
            else:
                messagebox.showerror("Помилка", "Невірний PIN-код!")

        pin_in.bind("<Return>", lambda _e: verify())
        tk.Button(dlg, text="Увійти", bg="#FFD500", fg="#000000", font=("Segoe UI", 10, "bold"), relief="flat", padx=20, pady=5, command=verify).pack()

    def _announce_locally(
        self,
        region_name: str,
        levels: List[str],
        details: str,
        explicit_files: Optional[List[str]] = None,
        title_override: str = "",
    ) -> None:
        if levels or explicit_files:
            self.audio_player.play_sequence(
                levels,
                volume=self.store.sound_volume,
                auto_play=self.store.auto_play_voice,
                explicit_files=explicit_files,
                title_override=title_override,
            )
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
                time.sleep(8)

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
                    for rname, lvls, exp, det in triggers:
                        self._announce_locally(rname, lvls, det, exp)
                    if self.active_tab in (0, 1, 2):
                        self._render_current_tab()
                elif kind == "auth_updated":
                    self._update_header_badges()
                    self._render_current_tab()
                elif kind == "google_login_done":
                    _, dlg = item
                    self.auth_dialog_open = False
                    try:
                        dlg.destroy()
                    except Exception:
                        pass
                    self._update_header_badges()
                    self._render_current_tab()
                    self._trigger_manual_sync()
                elif kind == "auth_error":
                    _, lbl, msg = item
                    try:
                        lbl.configure(text=f"⚠️ Помилка: {msg}", fg="#F87171")
                    except Exception:
                        pass
                elif kind == "auth_info":
                    _, lbl, msg = item
                    try:
                        lbl.configure(text=msg, fg="#22C55E")
                    except Exception:
                        pass
        except queue.Empty:
            pass
        self.after(200, self._process_ui_queue)


if __name__ == "__main__":
    app = OmastroTrivogaDesktopApp()
    app.mainloop()
