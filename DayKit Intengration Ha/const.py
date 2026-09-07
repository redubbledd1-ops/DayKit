"""Constanten voor de AgendaAlarm Backup integratie."""

DOMAIN = "agendaalarm_backup"

# Config/options keys
CONF_SPEAKER_ENTITY = "speaker_entity_id"
CONF_PRESENCE_ENTITY = "presence_entity_id"  # optioneel: person./device_tracker./binary_sensor.
CONF_NOTIFY_SERVICE = "notify_service"       # optioneel: bv. notify.mobile_app_xxx
CONF_DEFAULT_VOLUME = "default_volume"
CONF_DEFAULT_INTERVAL = "default_interval"
CONF_DEFAULT_SOUND_URL = "default_sound_url"

# --- Uitgebreide config (bidirectionele sync met de app, zie config_sync.py) ---
# Generieke, handmatig gekozen lijst van entities (zelfde rol als de app's "Entiteiten"-lijst).
# Bewust een multi-entity selector in config_flow.py i.p.v. vrije tekst, zodat je in HA
# native kan zoeken/filteren i.p.v. entity_id's over te typen.
CONF_ENTITIES = "entities"
# Spiegelt Android's ExternalSpeakerMode enum (DISABLED/DEFAULT/BACKUP_ONLY/BOTH) 1-op-1,
# zodat er geen aparte naam-mapping nodig is tussen app en HA.
CONF_SPEAKER_MODE = "speaker_mode"
CONF_PRESENCE_EXPECTED_STATE = "presence_expected_state"  # bv. "home", "on", "true"
CONF_OUT_OF_BED_ENTITY = "out_of_bed_entity_id"
CONF_OUT_OF_BED_EXPECTED_VALUE = "out_of_bed_expected_value"  # waarde die "in bed" betekent
CONF_OUT_OF_BED_ENABLED = "out_of_bed_check_enabled"  # aan/uit-toggle zelf (los van de entity)
CONF_ALARM_SCRIPT_ENTITY = "alarm_script_entity_id"
CONF_ALARM_SCRIPT_ENABLED = "alarm_script_enabled"
CONF_ALARM_SCRIPT_IGNORE_PRESENCE = "alarm_script_ignore_presence"
CONF_TIMER_SCRIPT_ENTITY = "timer_script_entity_id"
CONF_TIMER_SCRIPT_ENABLED = "timer_script_enabled"
CONF_TIMER_SCRIPT_IGNORE_PRESENCE = "timer_script_ignore_presence"
# Veiligheidsklep: stop de fallback automatisch na dit aantal seconden als niemand 'm stopt.
CONF_SAFETY_TIMEOUT = "safety_timeout_seconds"

DEFAULT_VOLUME = 70
DEFAULT_INTERVAL = 10
DEFAULT_SPEAKER_MODE = "DISABLED"
DEFAULT_PRESENCE_EXPECTED_STATE = "home"
DEFAULT_OUT_OF_BED_EXPECTED_VALUE = "off"
DEFAULT_OUT_OF_BED_ENABLED = False
DEFAULT_SAFETY_TIMEOUT = 60

# Standaard marge tussen de eigenlijke alarmtijd en het moment waarop de watchdog mag ingrijpen.
DEFAULT_GRACE_SECONDS = 90

# HTTP endpoints
EVENT_URL = f"/api/{DOMAIN}/event"
SOUND_UPLOAD_URL = f"/api/{DOMAIN}/sound_upload"
PAIR_URL = f"/api/{DOMAIN}/pair"
QR_IMAGE_URL = f"/api/{DOMAIN}/qr_code.png"
CONFIG_URL = f"/api/{DOMAIN}/config"

# Geluiden map onder www/, publiek bereikbaar via /local/agendaalarm_sounds/
SOUNDS_SUBDIR = "agendaalarm_sounds"
# Bij de integratie gebundeld fallback-geluid (assets/), gekopieerd naar
# <config>/www/agendaalarm_sounds/ bij setup (zie __init__.py) zodat er altijd een
# fallback-geluid is zonder internetverbinding/GitHub-afhankelijkheid. Relatief pad -
# elke gebruiksplek combineert dit zelf met get_url(hass, ...) tot een absolute URL,
# want dat kan per HA-instance verschillen (interne vs externe URL).
DEFAULT_SOUND_FILENAME = "discoAlarmBackupAlarm.mp3"
DEFAULT_SOUND_PATH = f"/local/{SOUNDS_SUBDIR}/{DEFAULT_SOUND_FILENAME}"

# Signal gebruikt om entities te laten verversen als de hub-state verandert
SIGNAL_UPDATE = f"{DOMAIN}_update"

# Entity unique-id-sleutels voor de koppelcode/QR-entities - hier gecentraliseerd zodat
# de entity-classes gegarandeerd dezelfde sleutels gebruiken.
ENTITY_KEY_GENERATE_PAIRING_CODE = "generate_pairing_code"
ENTITY_KEY_PAIRING_CODE_SENSOR = "pairing_code"
ENTITY_KEY_PAIRING_QR_CAMERA = "pairing_qr"
ENTITY_KEY_PHONE_REACHABLE = "phone_reachable_ping"

# Ping-detectie (binary_sensor.py): pingt hub.last_known_phone_ip, geen handmatig IP nodig.
PING_INTERVAL_SECONDS = 30
PING_TIMEOUT_SECONDS = 2
# Aantal gemiste polling-cycli op rij vóór de state omslaat naar "niet bereikbaar" - beschermt
# tegen een losse gemiste ping (bv. telefoon-WiFi in powersave met scherm uit).
PING_CONSECUTIVE_FAILURES_BEFORE_UNAVAILABLE = 3
