# Re-Forgematica CurseForge Release Copy

This file is for paste-ready CurseForge text.

Rules for future updates:
- Add a new section for each new version.
- Write for the CurseForge project and release pages first.
- Do not include build paths, Gradle metadata, hashes, or internal engineering notes in the release copy.
- Keep the user-facing release text clean and ready to paste.
- If Codex needs internal notes for a release, put them in a clearly separate `Internal Notes` subsection.

## Short Summary

```markdown
Forge 1.18.2 schematic building fork with backpack-aware Easy Place, optional Quark rotation help, proxy-safe storage, and a native printer for rendered layers.
```

## Project Description Copy

```markdown
# Re-Forgematica

Re-Forgematica is a Forge 1.18.2 fork of Forgematica focused on smoother survival building, especially on multiplayer servers.

This fork keeps the familiar Forgematica/Litematica workflow while adding practical quality-of-life features for players who build from schematics with storage mods, rotation helpers, proxy-style server networks, and rendered-layer printing.

## Main Features

- Adds a normal Minecraft Controls keybind for opening the Forgematica menu
- Improves Easy Place support when using Sophisticated Backpacks
- Can pull needed blocks from carried backpacks while placing
- Avoids swapping away tools when selecting hotbar slots
- Adds optional Quark Rotation Lock support for better block orientation
- Adds a native printer that can place rendered schematic blocks around the player
- Lets printer speed and range be configured
- Limits printer placement to the currently rendered schematic layers
- Improves printer placement for slabs, stairs, rods, ladders, banners, doors, heads, skulls, and directional redstone-style blocks
- Keeps schematic placement data separated across configured backend servers
- Improves stability when transferring between servers on a network
- Helps prevent schematics from appearing on the wrong backend server

## Native Printer

The native printer can place nearby missing schematic blocks while you walk through the rendered schematic.

Printer controls are available in the mod config:

- Hold the printer activation key to print temporarily
- Toggle printer mode on or off with the printer toggle key
- Adjust the number of ticks between placements
- Adjust the placement range, defaulting to 4.5 blocks

The printer uses normal placement behavior instead of commands. In creative mode it can use the creative pick-block path, and in survival it can use normal inventory selection, including Sophisticated Backpacks when that optional integration is installed.

The printer respects rendered layers, so if you render one layer at a time, it only tries to place blocks from that rendered layer range.

## Optional Mod Support

### Sophisticated Backpacks

When Sophisticated Backpacks is installed, Re-Forgematica can search carried backpacks for blocks needed by Easy Place or the printer and move them into the hotbar automatically.

### Quark

When Quark is installed on both client and server, Re-Forgematica can use Quark Rotation Lock during Easy Place and printer placement to help place directional blocks with the correct orientation.

Both integrations are optional. The mod still works without them.

## Requirements

- Minecraft Forge 1.18.2
- MaFgLib

## Version Highlights

### 1.0.2

- Removed the default `V` and `CAPS_LOCK` printer hotkeys so printer activation and toggle start unbound

## Credits

Re-Forgematica is based on Forgematica / Litematica-Forge, which is based on Litematica by masa.

The native printer was implemented independently for this fork, with behavior informed by Minecraft placement mechanics, Litematica Easy Place handling, and the open-source Forgematica printer ecosystem. Recognition goes to NeoForgematicaPrinter by Reime0 and the original ForgematicaPrinter project credited upstream by NeoForgematicaPrinter.
```

## 1.0.2

### Release Copy

```markdown
## 1.0.2

- Removed the default `V` and `CAPS_LOCK` printer hotkeys
- Printer activation and printer toggle now start unbound by default
- Printer controls remain configurable from the mod config
```

## 1.0.1

### Release Copy

```markdown
## 1.0.1

- Added native schematic printer mode for placing rendered schematic blocks around the player
- Added printer hold and toggle hotkeys
- Added configurable printer placement delay and placement range
- Made printer placement respect the current rendered schematic layer range
- Added creative-safe printer item selection through the creative pick-block path
- Added survival printer item selection through the existing inventory path, including Sophisticated Backpacks item pulls when available
- Improved printer handling for slabs, stairs, end rods, rod-style blocks, ladders, wall banners, wall signs, wall skulls, wall torches, buttons, levers, trapdoors, heads, skulls, and directional redstone-style blocks
- Added stricter support-face placement so face-attached blocks wait for the intended support block and attach to the schematic face
- Improved observer and other directional redstone placement so schematic orientation is used instead of the player's current look direction
- Improved standing head/skull placement so 16-step rotation is preserved and floor skulls place on the top surface instead of becoming wall skulls
- Added safeguards for layered double-height blocks such as doors, tall plants, and banner-style blocks
- Updated Re-Forgematica README and CurseForge copy with printer feature details and attribution
```
