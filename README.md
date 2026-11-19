# Fakeplayer Chunkloader

A Minecraft mod that keeps chunks loaded with fake players while providing live HUDs and map controls for monitoring without relying on real players.

## Supported versions

| Minecraft | Loaders |
|-----------|---------|
| 1.21 / 1.21.1 | Fabric, Forge, NeoForge, Quilt |
| 1.21.2 | Fabric, NeoForge, Quilt |
| 1.21.3 | Fabric, Forge, NeoForge, Quilt |
| 1.21.4 | Fabric, Forge, NeoForge, Quilt |
| 1.21.5 | Fabric, Forge, NeoForge, Quilt |
| 1.21.6 / 1.21.7 / 1.21.8 | Fabric, Forge, NeoForge, Quilt |
| 1.21.9 / 1.21.10 | Fabric, Forge, NeoForge, Quilt |
| 1.21.11 | Fabric, Forge, NeoForge, Quilt |
| 26.1 / 26.1.1 / 26.1.2 | Fabric, Forge, NeoForge, Quilt |
| 26.2 | Fabric, Forge, NeoForge, Quilt |
| 26.3 | Fabric, Forge, NeoForge, Quilt |

## Highlights

- **Dual mode**: Fakeplayer for natural mob spawning or Chunkplayer for keeping chunks active without triggering natural spawns
- **Mob Target**: Optional Fakeplayer-only toggle so mobs may aggro/pathfind (AFK farm target). Default off; no inventory, armor, or combat
- **Mob movement**: Existing mobs stay active by default with a new Chunkplayer. You can pause them where only that Chunkplayer keeps them active.
- **Visual markers & HUDs**: Color-coded markers, live Simulation/Chunkplayer HUDs, and interactive chunk map
- **Interactive map**: Terrain-rendered chunk map with controls for toggling, radius adjustments, and mode switch
- **Panel color & skins**: Live UI color editor and custom skin PNGs for fakeplayers (per world / server-authoritative)
- **Resilient config**: Per-world JSON file with backups, limits, and auto-restoration

## Quick Start

1. Run `/fp add` where you want the Fakeplayer to stand, or use `/fp add <x> <y> <z> [dimension]` to place it at coordinates. Leave out the dimension to use your current one. It starts active in Fakeplayer mode.
2. Right-click the Fakeplayer to open its chunk map. From there you can change its range or switch to Chunkplayer mode.

Use `/fp move <name> <x> <y> <z> [dimension]` to move a saved player. Leave out the dimension to keep it in the same one.

Hit your player to disable it and keep its entry saved. You can restore it from the disabled list. To delete it, sneak and hit it, sneak and right-click it, or run `/fp remove <name>`.

On a dedicated server, operators at permission level 2 or higher have access by default and can grant access with `/fp permission grant <player>`.

Run `/fp help` to open the guide before placing your first player. You can also open **Help** from the map or find the command reference under **Info -> Commands**.

## Default Key Controls

| Key | Function |
|-----|----------|
| F6 | Toggle Simulation Status HUD |
| F7 | Toggle Chunkplayer Status HUD |
| F8 | Open Disabled Players List |
| F9 | Open Active Players List |

All keybindings are configurable under Minecraft's `Controls > Miscellaneous` section or under `Keybinds` in the Menu.

## Configuration

The mod saves your Fakeplayers, Chunkplayers, permissions and custom skins in `<world-directory>/chunkloader/`. Player data is in `chunkloader_config.json` and custom skins are in `skins/`. This data carries over when you update the mod. Copy the whole folder if you move the world.

**Automatic features:**
- The mod creates its configuration automatically when the world loads
- Up to two timestamped backups are kept in `chunkloader/backups/` (plus a latest alias)
- If the main player data file can't be read, the mod tries to restore it from a valid backup
- One config per world; each player entry is keyed by dimension + chunk coordinates

## Important Notes

- Fakeplayers never break blocks or use items
- The selected areas of newly placed, moved, or enlarged players cannot overlap
- Works across Overworld, Nether, and End
- SD selects the highlighted area on the map. New players start at SD 0, a single chunk.
- The first ring beyond the selected area runs block and fluid ticks. The second ring stays loaded without normal ticking.
- Fakeplayers allow natural mob spawning in the selected area. At SD 1, the minimum distance from the Fakeplayer is more than 8 blocks instead of more than 24. The other spawn rules still apply.
- For a mob farm at SD 0, place the Fakeplayer in the same chunk, more than 24 blocks above or below where mobs spawn.
- With mod access, you can view maps for other people's Fakeplayers and Chunkplayers. The owner or an admin can share a player so someone else can edit it, move it, or turn it on or off. Only the owner or an admin can manage shares, reset the player, or delete it.
- Fakeplayers and Chunkplayers are protected from mob damage.
- Install the mod on the server to run Fakeplayers and Chunkplayers, and on your client for the map, HUDs and custom skins. Use the same mod version on the server and clients.
- Fakeplayers do not count toward "server is full". A dedicated server keeps running when only fakeplayers are online
- Fakeplayer Chunkloader is not available on Aternos.
- Use GUI scale 3 or lower for the mod's menus.

## Credits

- Chunk map design inspired by [SuperMartijn642](https://github.com/SuperMartijn642)
