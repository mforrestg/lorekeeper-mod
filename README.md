# Lorekeeper Mod

A mysterious Lore Keeper roams the server, collects player-driven history, and sells it back as in-game news and archives.

## Features

- Persistent lore logging with `/lore log <text>`.
- Daily Gazette book generation with `/lore news`.
- Custom Lore Keeper NPC with trades:
  - Latest Gazette (1 emerald).
  - Server Archive (5 emeralds).
  - Buys written books for 10 emeralds and logs their contents.
- Daily publication snapshots: once a Gazette is published for a day, it stays fixed.

## Commands

- `/lore log <text>`: record a lore entry.
- `/lore news`: deliver the Gazette book (or print to console if no player).

## Lore Keeper NPC

- Spawn egg: `lorekeeper:lorekeeper_spawn_egg`
- Summon: `/summon lorekeeper:lorekeeper`

## Development

```bash
./gradlew build
```

Server + client workflow:

```bash
./gradlew runServer
```

First run creates `run/eula.txt`. Set `eula=true`, then restart the server.
If you hit "invalid session" locally, set `online-mode=false` in `run/server.properties`.

```bash
./gradlew runClient
```

Connect to `localhost:25565`.

## Notes

- In-game day number = `world time / 24000`. Gazettes are named by day.
- The Archive book caps at 100 pages to stay within vanilla limits.
- AI summarization is planned but not required for MVP.

## License

See `LICENSE`.
