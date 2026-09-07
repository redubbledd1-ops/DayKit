# Screenshot Guide - SoundRepository Implementation

## Instructies voor het maken van screenshots

### Voorbereiding
1. Start de app op een Android device of emulator
2. Navigeer naar Timer Settings of Trigger Rules Activity
3. Open de Custom Sounds modal

---

## Screenshot 1: Sound Toevoegen

### Stappen:
1. Open Custom Sounds modal
2. Klik op "Add Custom Sound" button
3. Selecteer een audiobestand (MP3, WAV, of OGG)
4. Wacht tot success message verschijnt
5. **Maak screenshot** van de modal met:
   - Nieuwe sound in de lijst
   - Groene success message bovenaan
   - Sound naam en duur zichtbaar

### Wat te tonen:
- ✅ Success message: "Geluid succesvol toegevoegd"
- ✅ Nieuwe sound in lijst met naam
- ✅ Duur van sound (bijv. "2:34")
- ✅ Play en Delete buttons zichtbaar

### Bestandsnaam: `01_sound_add_success.png`

---

## Screenshot 2: Sound Testen/Afspelen

### Stappen:
1. In Custom Sounds modal
2. Klik op Play button (▶) van een sound
3. **Maak screenshot** terwijl sound afspeelt met:
   - Stop button (⏹) in plaats van Play button
   - Sound item gehighlight als "playing"
   - Andere sounds met Play button

### Wat te tonen:
- ✅ Stop button actief voor spelende sound
- ✅ Play buttons voor andere sounds
- ✅ Sound naam en duur duidelijk leesbaar
- ✅ Visuele indicatie van actieve afspeel state

### Bestandsnaam: `02_sound_playing.png`

---

## Screenshot 3: Sound Verwijderen

### Stappen:
1. In Custom Sounds modal
2. Klik op Delete button (🗑️) van een sound
3. Confirmatie dialog verschijnt
4. **Maak screenshot** van confirmatie dialog met:
   - "Delete Custom Sound" titel
   - Sound naam in confirmatie tekst
   - "Delete" en "Cancel" buttons
   - Rode Delete button

### Alternatief (na verwijdering):
1. Klik "Delete" in confirmatie
2. **Maak screenshot** van modal met:
   - Groene success message: "Geluid verwijderd"
   - Sound verdwenen uit lijst
   - Andere sounds nog zichtbaar

### Wat te tonen (Optie 1 - Confirmatie):
- ✅ Delete confirmatie dialog
- ✅ Sound naam in tekst
- ✅ Rode Delete button
- ✅ Cancel button

### Wat te tonen (Optie 2 - Na verwijdering):
- ✅ Success message: "Geluid verwijderd"
- ✅ Sound niet meer in lijst
- ✅ Andere sounds ongewijzigd

### Bestandsnaam: `03_sound_delete_confirm.png` of `03_sound_delete_success.png`

---

## Extra Screenshot (Optioneel): Empty State

### Stappen:
1. Verwijder alle custom sounds
2. **Maak screenshot** van lege staat met:
   - Groot muziek icoon (🎵)
   - "No custom sounds" tekst
   - "Add Custom Sound" button bovenaan

### Wat te tonen:
- ✅ Empty state icoon en tekst
- ✅ Duidelijke call-to-action
- ✅ Clean, overzichtelijke UI

### Bestandsnaam: `04_empty_state.png`

---

## Extra Screenshot (Optioneel): Realtime Update Demo

### Stappen:
1. Open Custom Sounds modal op 2 devices/emulators tegelijk
2. Voeg sound toe op device 1
3. **Maak screenshot** van device 2 die automatisch update
4. Of: Maak video van realtime update

### Wat te tonen:
- ✅ Automatische lijst update
- ✅ Geen handmatige refresh nodig
- ✅ StateFlow reactive behavior

### Bestandsnaam: `05_realtime_update.png` of `05_realtime_update.mp4`

---

## Screenshot Specificaties

### Technisch:
- **Resolutie**: Minimaal 1080x1920 (portrait) of 1920x1080 (landscape)
- **Format**: PNG (voor screenshots) of MP4 (voor video's)
- **Kwaliteit**: Hoge kwaliteit, geen compressie
- **Device**: Bij voorkeur moderne Android device (API 26+)

### Compositie:
- **Focus**: Custom Sounds modal centraal in beeld
- **Context**: Genoeg omgeving om context te tonen
- **Leesbaarheid**: Tekst duidelijk leesbaar
- **Kleuren**: Goede contrast en zichtbaarheid

### Content:
- **Realistische data**: Gebruik echte sound namen (geen "test1", "test2")
- **Nederlandse tekst**: Alle UI teksten in het Nederlands
- **Geen persoonlijke info**: Geen gevoelige data zichtbaar
- **Clean UI**: Geen debug info of development artifacts

---

## Checklist voor Screenshots

### Voor het maken:
- [ ] App geïnstalleerd en werkend
- [ ] Minimaal 2-3 test sounds beschikbaar
- [ ] Device scherm schoon (geen notificaties)
- [ ] Goede verlichting (voor fysiek device)
- [ ] Correcte oriëntatie (portrait/landscape)

### Tijdens het maken:
- [ ] Screenshot 1: Add sound success
- [ ] Screenshot 2: Sound playing
- [ ] Screenshot 3: Delete confirmation/success
- [ ] (Optioneel) Screenshot 4: Empty state
- [ ] (Optioneel) Screenshot 5: Realtime update

### Na het maken:
- [ ] Screenshots opgeslagen in project root of docs folder
- [ ] Bestandsnamen correct volgens guide
- [ ] Kwaliteit gecontroleerd
- [ ] Alle belangrijke features zichtbaar
- [ ] Screenshots toegevoegd aan changelog/documentation

---

## Gebruik van Screenshots

### In Documentatie:
```markdown
## Add Sound Feature
![Add Sound Success](01_sound_add_success.png)

## Play Sound Feature
![Sound Playing](02_sound_playing.png)

## Delete Sound Feature
![Delete Confirmation](03_sound_delete_confirm.png)
```

### In README:
```markdown
# Features

### Custom Sound Management
- **Add**: Easily add custom alarm sounds
  ![Add Sound](screenshots/01_sound_add_success.png)
  
- **Test**: Preview sounds before using
  ![Play Sound](screenshots/02_sound_playing.png)
  
- **Delete**: Remove unwanted sounds
  ![Delete Sound](screenshots/03_sound_delete_confirm.png)
```

---

## Tips voor Goede Screenshots

1. **Timing**: Maak screenshots op het juiste moment (success message zichtbaar)
2. **Stabiliteit**: Wacht tot animaties compleet zijn
3. **Consistentie**: Gebruik dezelfde theme/colors voor alle screenshots
4. **Context**: Toon genoeg UI om feature te begrijpen
5. **Kwaliteit**: Gebruik native screenshot tools (niet camera foto's)

---

## Troubleshooting

### Screenshot niet duidelijk:
- Verhoog device brightness
- Gebruik emulator voor perfecte screenshots
- Controleer schermresolutie

### Feature niet zichtbaar:
- Zoom in op relevante UI delen
- Crop screenshots naar belangrijke content
- Gebruik annotations/arrows indien nodig

### Tekst niet leesbaar:
- Kies grotere font size in device settings
- Gebruik device met hogere DPI
- Maak screenshot in landscape voor meer ruimte

---

## Deliverables

### Minimaal vereist:
1. ✅ Screenshot 1: Add sound success
2. ✅ Screenshot 2: Sound playing
3. ✅ Screenshot 3: Delete confirmation/success

### Optioneel maar aanbevolen:
4. Screenshot 4: Empty state
5. Screenshot 5: Realtime update demo
6. Video: Complete workflow (add → test → delete)

### Documentatie:
- ✅ SOUND_REPOSITORY_CHANGELOG.md (compleet)
- ✅ SCREENSHOT_GUIDE.md (deze file)
- Screenshots in `/screenshots` folder of project root

---

## Conclusie

Met deze screenshots kun je de volledige functionaliteit van de SoundRepository implementatie demonstreren:
- ✅ Add functionaliteit met realtime update
- ✅ Test/Play functionaliteit
- ✅ Delete functionaliteit met confirmatie
- ✅ Reactive state management (StateFlow)
- ✅ Clean, gebruiksvriendelijke UI

De screenshots dienen als visueel bewijs dat alle requirements zijn geïmplementeerd en correct werken.
