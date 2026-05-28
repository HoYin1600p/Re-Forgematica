# Re-Forgematica

Re-Forgematica is a fork of [Forgematica / Litematica-Forge](https://github.com/ThinkingStudios/Litematica-Forge), the unofficial Forge port of [Litematica](https://github.com/maruohon/litematica).

This fork focuses on survival building workflows on Forge servers, especially Easy Place behavior with backpack storage, orientation helpers, and proxy-style server networks.

## Changes In This Fork

### Forge Controls Keybind

Re-Forgematica adds a normal Minecraft/Forge Controls entry:

- Category: `Forgematica`
- Key: `Open Menu`
- Default: `L`

The original internal Litematica/Forgematica main-menu hotkey no longer has a default key assigned, so the Forge Controls key is the default way to open the menu. The rest of the existing Litematica/Forgematica hotkeys remain configurable from inside the mod's own config screens.

### Sophisticated Backpacks Support

Re-Forgematica adds optional Easy Place integration for [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks).

When Easy Place needs a block that is not already in the player inventory, the mod can:

- Detect Sophisticated Backpacks in the player's inventory.
- Open carried backpacks while Easy Place is trying to pick the required block.
- Search the opened backpack container for the requested placement item.
- Match the required item using block/item identity and NBT where needed.
- Pull the required block into a hotbar slot.
- Swap the displaced hotbar item back into the backpack where possible.
- Retry patiently while backpack contents sync from the server.
- Fall back through all carried backpacks if cached contents are stale or incomplete.
- Suppress repeated Easy Place prevention warnings while a backpack transfer is still pending.

There is also a Generic config option, `sophisticatedBackpacksAvoidSwappingTools`, enabled by default. When enabled, Re-Forgematica avoids using the currently held tool or damageable item as the hotbar slot to swap into the backpack. It prefers an empty, block, non-tool, or pick-blockable hotbar slot instead.

Sophisticated Backpacks is optional. If it is not installed, this integration is inactive.

### Quark Rotation Lock Support

Re-Forgematica adds optional Easy Place integration for [Quark](https://github.com/VazkiiMods/Quark)'s Rotation Lock feature.

On multiplayer Forge servers where Quark is installed on both client and server, Easy Place can temporarily send Quark a rotation-lock profile matching the schematic block state before placing the block. After the placement packet is sent, the lock is cleared immediately and then cleared a second time two client ticks later as a safety fallback.

This helps Easy Place respect orientation for blocks supported by Quark's rotation-lock logic, including common facing, axis, slab, stair, and vertical slab cases.

Quark is optional. Re-Forgematica does not bundle Quark, does not compile against Quark, and uses runtime reflection so the mod still loads normally without Quark.

### Easy Place Behavior

The existing Litematica Easy Place protocols are preserved:

- Single-player v3 handling is left unchanged.
- Carpet-compatible v2 handling is left unchanged.
- Multiplayer without Carpet still keeps the existing slab-only behavior.
- Quark rotation lock is only attempted when the active Easy Place protocol is `Slabs only` or `None`, so it does not compete with Carpet or v3 placement handling.

### Proxy Server Storage Scoping

Re-Forgematica can separate loaded schematic placement data by backend server alias, not just by Minecraft dimension. This is intended for Velocity/Bungee-style networks where multiple servers use the same dimension names and players transfer between servers with commands.

The Generic tab includes these options:

- `serverStorageScopeFromCommands`: enables command-based backend scoping.
- `serverStorageScopeCommands`: comma-separated backend transfer command names.
- `serverStorageScopeInitial`: optional initial backend scope to use before any transfer command has been captured.

The default command list is:

```text
millennium, vaulthalla, eon, century, asgard, pog, lobby, omega, echo, vault
```

With that list, sending `/millennium`, `/lobby`, `/omega`, and the other configured commands scopes the saved loaded-schematic data to that backend name. Normal single-player and direct multiplayer clients are unaffected unless one of the configured commands is sent.

If your network always logs players into a known server first, set `serverStorageScopeInitial` to that server alias, for example `lobby`. If it is left empty, Re-Forgematica starts unscoped until a configured transfer command is sent.

### Server Transfer Hardening

Re-Forgematica includes lifecycle hardening for server transfers where the client world can briefly be missing or replaced:

- Saves current placement data immediately before a configured server transfer command is sent.
- Promotes the pending backend scope after the new world loads.
- Recreates the schematic world if the client reconnects before it is available.
- Reloads placement data after transfer recovery.
- Adds null guards around schematic rendering, material lists, ray tracing, and placement helpers while the transfer is in progress.

This prevents loaded schematics from rendering on the wrong backend and avoids crashes or lost renders while moving between servers.

## Compatibility Notes

The backpack and Quark integrations are best-effort compatibility layers. They depend on implementation details of the target mods and may need updates if those mods change their internal class names, packet names, or container behavior.

If an optional integration fails to initialize, it disables itself and the base Forgematica behavior remains available.

## Credits

- [Litematica](https://github.com/maruohon/litematica) by masa/maruohon: original schematic mod and Easy Place behavior.
- [Forgematica / Litematica-Forge](https://github.com/ThinkingStudios/Litematica-Forge) by TexTrue and ThinkingStudio: Forge port used as the base for this fork.
- [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) by P3pp3rF1y: optional backpack integration target.
- [Quark](https://github.com/VazkiiMods/Quark) by Vazkii and contributors: optional rotation-lock integration target.

## Licensing

Re-Forgematica is a fork of LGPLv3-covered Litematica/Forgematica code. The combined mod remains distributed under the GNU Lesser General Public License version 3.0; see [LICENSE](LICENSE).

New Re-Forgematica additions are also offered under an MIT-style permissive grant to the maximum extent they are separable from the upstream LGPLv3 work; see [LICENSE_RE-FORGEMATICA.md](LICENSE_RE-FORGEMATICA.md). That grant does not relicense upstream Litematica, Forgematica, or other third-party code.

Optional integrations do not bundle Sophisticated Backpacks or Quark. Users must install those mods separately if they want the related functionality.

Referenced project licenses:

- Litematica: LGPL-3.0
- Forgematica / Litematica-Forge: LGPL-3.0
- Quark: CC BY-NC-SA 3.0, as listed in Quark's repository license
- Sophisticated Backpacks: All Rights Reserved, as listed for that project
