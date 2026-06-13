## 1.0.5

- Fixed a client crash when rebuilding schematic preview chunks containing Powah cables
- Cleared schematic-world block entities when schematic chunks are unloaded or replaced so modded block entities can release their client-side state
- Removed existing schematic block entities before installing replacements to avoid duplicate lifecycle registration during preview rebuilds
- Cleared all loaded schematic chunks before discarding the schematic world
- Bumped the mod version to `1.0.5`

## 1.0.4

- Made the backend identity packet channel optional so Re-Forgematica clients can connect to servers that do not have Re-Forgematica installed
- Kept server-provided backend IDs active when the server does have Re-Forgematica installed
- Preserved command-based backend scoping as the fallback for servers without Re-Forgematica installed
- Fixed proxy/server reconnect persistence so loaded schematics stay associated with the correct backend after direct backend commands such as `/test` and `/usatest`
- Changed command aliases to pending transfer hints instead of immediate storage loads, preventing the current placement set from being cleared before reconnect completes
- Prevented active scoped storage from falling back to `forgematica_default.json` when Minecraft temporarily has no server/world name during reconnect
- Cleaned up temporary diagnostic logging from the live-test builds
- Bumped the mod version to `1.0.4`

## 1.0.3

- Added optional server-side backend identity packets so each server can tell clients which schematic placement storage scope to load
- Added automatic per-server UUID generation in `config/forgematica-server.properties`
- Kept all rendering, GUI, hotkey, and schematic logic client-only while allowing the jar to run safely on servers for identity packets
- Marked MaFgLib as a client-only dependency so dedicated servers can load Re-Forgematica without installing MaFgLib
- Improved proxy/server transfer scoping so command sniffing remains a fallback when the server-side identity packet is unavailable
- Fixed unbound printer activation being treated as always held
- Bumped the mod version to `1.0.3`

## 1.0.2

- Removed the default `V` and `CAPS_LOCK` printer hotkeys so printer activation and toggle start unbound
- Bumped the mod version to `1.0.2`

## 1.0.1

- Added native schematic printer mode with configurable placement interval and range
- Added printer hold and toggle hotkeys
- Added printer support for current rendered layer range, creative pick-block placement, survival inventory selection, and Sophisticated Backpacks item pulls
- Improved printer placement for slabs, stairs, rods, ladders, wall-attached blocks, banners, doors, heads/skulls, and directional redstone-style blocks
- Added stricter support-face handling so face-attached blocks wait for the intended support block and attach to the schematic face
- Added placement protocol decoding for wall/standing block items so skull/head rotation can be forced outside Easy Place mode
- Updated README and CurseForge release copy
