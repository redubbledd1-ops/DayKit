# QR-afbeelding tonen in het "Integratie toevoegen"-scherm (niet alleen tekstcode)

## Context

De vorige fix toont bij "Integratie toevoegen" al een tekst-koppelcode, maar
geen scanbare QR - dat is minder handig dan scannen. HA's config-flow
`description` ondersteunt basis-markdown, inclusief een `data:`-URI-afbeelding
(`![alt](data:image/png;base64,...)`) - dat is de manier om hier alsnog een
scanbare QR te tonen zonder een losse camera-entiteit nodig te hebben (die
bestaat immers pas ná het aanmaken van de config entry).

Relevante bestanden: `config_flow.py` (`AgendaAlarmBackupConfigFlow.async_step_user`),
`qr.py` (`render_qr_png(payload) -> bytes`, al gebruikt door `camera.py` en
`http_views.py`), `strings.json` (net toegevoegd in de vorige stap).

## Wat te doen

In `config_flow.py`'s `async_step_user`: genereer, naast de tekstcode, ook de
QR-PNG via de bestaande `qr.py`-helper met dezelfde payload als elders
(`{"base_url": ..., "code": ...}`), base64-encode 'm, en geef 'm mee als extra
`description_placeholder` (bv. `qr_data_uri`, als volledige `data:image/...`-URI
inclusief prefix - zodat de markdown-afbeelding er direct naar kan verwijzen).

```python
import base64
import json

from homeassistant.helpers.network import NoURLAvailableError, get_url

from .qr import render_qr_png

async def async_step_user(
    self, user_input: dict[str, Any] | None = None
) -> config_entries.FlowResult:
    if user_input is not None:
        return self.async_create_entry(title="AgendaAlarm Backup", data={})

    from .pairing import get_pairing_store
    code = get_pairing_store(self.hass).generate_code()

    placeholders: dict[str, str] = {"code": code, "qr_data_uri": ""}
    try:
        base_url = get_url(self.hass, prefer_external=False, allow_internal=True)
    except NoURLAvailableError:
        base_url = None
    if base_url:
        payload = json.dumps({"base_url": base_url, "code": code})
        png_bytes = await self.hass.async_add_executor_job(render_qr_png, payload)
        b64 = base64.b64encode(png_bytes).decode("ascii")
        placeholders["qr_data_uri"] = f"data:image/png;base64,{b64}"

    return self.async_show_form(
        step_id="user",
        data_schema=vol.Schema({}),
        description_placeholders=placeholders,
    )
```

Werk `strings.json`'s beschrijving voor de `user`-stap bij zodat 'ie de QR als
markdown-afbeelding toont, mét de tekstcode als fallback voor als scannen niet
lukt (of als `qr_data_uri` leeg is, bv. geen bereikbare HA base-URL - de
afbeelding-tag rendert dan gewoon niks bruikbaars, de tekstcode blijft dan de
werkende fallback):

```json
{
  "config": {
    "step": {
      "user": {
        "title": "Pair AgendaAlarm",
        "description": "Scan this QR code with the AgendaAlarm app (Settings > Pair):\n\n![Pairing QR code]({qr_data_uri})\n\nCan't scan? Enter this code manually instead: {code}\n\nValid for 5 minutes. You can also finish setup and pair later from the device page."
      }
    }
  }
}
```

Kopieer dezelfde tekst naar `translations/en.json` als dat bestand al bestaat
(consistent met hoe de rest van de integratie vertalingen aanpakt).

## Oplevercriteria

- Bij "Integratie toevoegen" toont het eerste scherm een daadwerkelijk scanbare
  QR-afbeelding (niet alleen tekst).
- De tekstcode staat er ook nog steeds bij, als fallback voor als scannen niet
  lukt.
- Als er om wat voor reden dan ook geen HA base-URL beschikbaar is, breekt het
  scherm niet (geen crash) - de tekstcode blijft dan gewoon bruikbaar, ook al
  toont de afbeelding niks.
