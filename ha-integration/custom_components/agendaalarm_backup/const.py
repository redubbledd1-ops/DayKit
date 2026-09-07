"""Constanten voor de DayKit integratie."""

DOMAIN = "daykit"

# Config/options keys
CONF_SPEAKER_ENTITY = "speaker_entity_id"
CONF_PRESENCE_ENTITY = "presence_entity_id"  # optioneel: person./device_tracker./binary_sensor.
CONF_NOTIFY_SERVICE = "notify_service"       # optioneel: bv. notify.mobile_app_xxx
CONF_DEFAULT_VOLUME = "default_volume"
# Als true: laat het volume van de fallback-speaker ongemoeid (geen volume_set-call vóór
# play_media), gebruikt dus altijd wat er toevallig al op staat. Gespiegeld vanuit de app's
# HomeAssistantSettings.skipBackupVolume, zie HaSettingsViewModel.kt's buildConfigPatchJson.
CONF_SKIP_BACKUP_VOLUME = "skip_backup_volume"
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

# Timer- en weer-speaker instellingen - zelfde vorm als CONF_SPEAKER_ENTITY/CONF_SPEAKER_MODE/
# CONF_DEFAULT_VOLUME/CONF_SKIP_BACKUP_VOLUME hierboven, maar dit zijn losse velden in een eigen
# naamruimte (i.p.v. de bestaande speaker_*/default_volume-velden te hergebruiken), omdat die
# laatste specifiek de Agenda-alarm-watchdog voeden (zie __init__.py) - Timer en Weer hebben geen
# watchdog-concept, maar de Android-app speelt ze wel af via SpeakerContext.TIMER/WEATHER, dus
# horen hun instellingen (net als bij de al bestaande alarm_/timer_script_*-velden) hier ook
# gewoon los van elkaar geconfigureerd te kunnen worden. Zie ook HomeAssistantSettings.kt's
# SpeakerSettings/SpeakerContext en HaSettingsViewModel.kt's buildConfigPatchJson/buildConfigFieldDiffs.
CONF_TIMER_SPEAKER_ENTITY = "timer_speaker_entity_id"
CONF_TIMER_SPEAKER_MODE = "timer_speaker_mode"
CONF_TIMER_SPEAKER_VOLUME = "timer_speaker_volume"
CONF_TIMER_SPEAKER_SKIP_VOLUME = "timer_speaker_skip_volume"
CONF_WEATHER_SPEAKER_ENTITY = "weather_speaker_entity_id"
CONF_WEATHER_SPEAKER_MODE = "weather_speaker_mode"
CONF_WEATHER_SPEAKER_VOLUME = "weather_speaker_volume"
CONF_WEATHER_SPEAKER_SKIP_VOLUME = "weather_speaker_skip_volume"
# Geluid per onderdeel. De waarde is altijd een volledige /local/daykit_sounds/<naam>.mp3-URL
# (zelfde vorm als CONF_DEFAULT_SOUND_URL hierboven, dat het geluid van het agenda-alarm/de
# watchdog is): de app upload haar lokale geluiden naar die map, dus de bestandsnaam is de
# gedeelde sleutel tussen beide kanten. In HA's "Configureren" kies je gewoon een naam uit een
# dropdown, de URL wordt daar omheen gebouwd (zie config_flow.py's _sound_name_to_url).
CONF_TIMER_SPEAKER_SOUND_URL = "timer_speaker_sound_url"
CONF_WEATHER_SPEAKER_SOUND_URL = "weather_speaker_sound_url"
# Weeralarmen laten uitspreken (app-gedrag: telefoon rendert de spraak lokaal en speelt 'm af op
# de weer-speaker, zie WeatherAlertWorker.kt) - hoort bij de Weer-speaker-sectie, geen aparte
# HA-functionaliteit maar wel een gedeelde instelling die vanuit HA gezet moet kunnen worden.
CONF_WEATHER_TTS_ENABLED = "weather_tts_enabled"

# Wie heeft de config als laatste gewijzigd, en wanneer. Bedoeld om te voorkomen dat de app
# blijft vragen of je een HA-wijziging wilt overnemen die er helemaal niet is: zonder dit kon de
# app alleen wáárdes vergelijken, en zag ze dus geen verschil tussen "HA heeft dit net gewijzigd"
# en "HA heeft nog de oude waarde omdat de app nog niet gepusht had".
#
# CONF_CONFIG_LAST_MODIFIED is milliseconden sinds epoch, maar wordt door de app bewust NIET met
# de eigen klok vergeleken - telefoon en HA-server lopen zelden gelijk. De app onthoudt simpelweg
# welke waarde ze het laatst gezien heeft; verandert die, dan is er echt iets gebeurd.
CONF_CONFIG_LAST_MODIFIED = "config_last_modified"
# "ha" = gewijzigd via HA's Configureren-scherm, "app" = binnengekomen via een push van de app.
CONF_CONFIG_LAST_MODIFIED_BY = "config_last_modified_by"

SOURCE_HA = "ha"
SOURCE_APP = "app"

DEFAULT_VOLUME = 70
DEFAULT_SKIP_BACKUP_VOLUME = False
DEFAULT_INTERVAL = 10
DEFAULT_SPEAKER_MODE = "DISABLED"
DEFAULT_PRESENCE_EXPECTED_STATE = "home"
DEFAULT_OUT_OF_BED_EXPECTED_VALUE = "off"
DEFAULT_OUT_OF_BED_ENABLED = False
DEFAULT_SAFETY_TIMEOUT = 60
DEFAULT_WEATHER_TTS_ENABLED = False

# Standaard marge tussen de eigenlijke alarmtijd en het moment waarop de watchdog mag ingrijpen.
DEFAULT_GRACE_SECONDS = 90

# HTTP endpoints
EVENT_URL = f"/api/{DOMAIN}/event"
SOUND_UPLOAD_URL = f"/api/{DOMAIN}/sound_upload"
WEATHER_TTS_UPLOAD_URL = f"/api/{DOMAIN}/weather_tts_upload"
PAIR_URL = f"/api/{DOMAIN}/pair"
QR_IMAGE_URL = f"/api/{DOMAIN}/qr_code.png"
CONFIG_URL = f"/api/{DOMAIN}/config"

# Geluiden map onder www/, publiek bereikbaar via /local/daykit_sounds/
SOUNDS_SUBDIR = "daykit_sounds"

# Weeralarm-tts: telefoon genereert de spraak lokaal (Android TextToSpeech) en upload 'm
# hierheen zodat media_player.play_media 'm bij de HA-speaker kan krijgen - zie
# DayKitWeatherTtsUploadView in http_views.py. Bewust een eigen map, apart van
# SOUNDS_SUBDIR: dit is vluchtige, bij elk weeralarm overschreven audio, geen permanent
# geluid dat in de custom-sound-bibliotheek/manifest/backup-sync thuishoort.
WEATHER_TTS_SUBDIR = "daykit_tts"
WEATHER_TTS_FILENAME = "weather_tts.wav"
# Bij de integratie gebundeld fallback-geluid (assets/), gekopieerd naar
# <config>/www/daykit_sounds/ bij setup (zie __init__.py) zodat er altijd een
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
