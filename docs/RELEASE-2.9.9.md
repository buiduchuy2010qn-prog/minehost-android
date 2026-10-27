# MineHost 2.9.9 candidate

## Scope
- Keep the server overview, expanded Playit connection UI, three horizontal metrics, Console navigation and original Minecraft textures unchanged.
- Compact world list: friendly names derived from real folder names; retain the folder, size and selected/stored status; explicit play button and overflow menu.
- OneBlock: three resource phases, bonus every 25 regeneration events, persistent progress.
- SkyBlock and Treasure Island: explicit void generator and starter chests, not the default superflat terrain.
- Parkour: reachable 3-block gaps, checkpoint/spawnpoint every five stages and one completion reward.
- New-world setup waits for forced chunks, only runs once, initializes late-joining players and scopes rescue to the overworld.
- Built-in packs declare exact formats for Java 1.20–1.21.4. Unknown versions cannot create these presets; ordinary map/seed creation and existing worlds remain available. Do not downgrade an existing world to use a preset.

## Old maps: opt-in protection
Open a map's details and select “Kiểm tra datapack map cũ”. Inspection is read-only. If an old MineHost load tag directly calls minehost:init, the separate confirmation performs a full world/Nether/End backup and CRC validation before removing only that direct trigger. The backup is retained. Terrain, inventory, tick functions and progress are not regenerated or upgraded. This targeted check is not a scan of all third-party datapacks/plugins.

## Verification
Unit/UI tests plus an isolated official Minecraft server matrix: 1.20.4, 1.21.1, 1.21.4. The real-server fixtures are produced by the app's creation code, not handwritten test datapacks. The client runs against localhost only. Explicit human EULA consent applies only to temporary CI servers (2026-10-27), not to user servers in the app.

The matrix checks actual player join/mining/checkpoints, inventory rewards, terrain and persistence across reload/restart. It is automated gameplay validation, not a manual playthrough on a phone. Keep the release a candidate until all jobs pass; do not publish OTA automatically.

References for versioned formats:
- https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21
- https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4
- https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-5
