# TableForge

Session prep for Dungeon Masters, built for an Android tablet (laid out for the Galaxy Tab S6 Lite, works on phones too).

## What's in v1

- **Forge** — generate NPCs, events, locations, dungeon rooms, magic items and story beats. Say what you're after, tap vibe chips, pin the ones you like and regenerate the rest, or reroll a single card. Save keepers to Ideas.
- **Story** — write the overall plot, build an outline of beats grouped by act, mark beats Planned / In play / Done, get "what happens next" suggestions, and tap **Fill gap** between any two beats to get bridging options that slot right in.
- **Ideas** — everything saved from the Forge plus your own ideas. Search, filter, favorite, mark as used, and send an idea to the outline, the concept map, or the party loot.
- **Concepts** — a free-form idea map: add concepts, drag them around, link them and label the links ("secretly funds", "rivals").
- **Maps** — square-grid maps. Tap a square or long-press and drag over an area, then ask for anything: "a lighthouse that takes up 4 squares", "a river running north to south", "three goblin tents around a campfire", "make this corner a swamp". Claude places it, sizes it to the grid and writes a read-aloud description. Paint terrain by hand, long-press a feature to move it, and undo any change.
- **Loot** — players and their characters, each magic item and who holds it, rarity, attunement (with the 3-item limit), charges and where it came from. Unclaimed items sit in the party stash.
- **Tables** — make d4 through d100 tables, have entries generated, roll on them, and keep a roll log. Includes a quick dice tray.

Everything is stored on the tablet. Generated ideas take your campaign's plot, setting, tone, outline, party and existing NPCs into account.

## AI setup

Open **Settings**:

- **Claude** — paste an API key from https://console.anthropic.com. Unlocks free-form map requests and the best ideas.
- **Ollama** — point at a local Ollama server on your network (e.g. `http://192.168.1.10:11434`).
- **Offline** — built-in random tables; no internet needed. Map requests understand simple sizes ("4 squares", "3x2") and "make this water".

Use **Test connection** to check it before the session.

## Install

Every push to `main` builds an APK on GitHub Actions and publishes it under **Releases**. On the tablet, open the latest release, download `TableForge-v1.0.N.apk`, and allow installs from your browser when Android asks.

## Build locally

Android Studio Ladybug or newer, JDK 17: open the project and run, or `./gradlew assembleRelease`.

## Credits

See [CREDITS.md](CREDITS.md) for design inspiration and font licenses.
