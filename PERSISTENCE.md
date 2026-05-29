# Re-Forgematica Persistence Notes

This file summarizes the Re-Forgematica work added on top of Forgematica / Litematica-Forge so the project context survives across IDEs, machines, and future development sessions.

## Project Identity

- Published fork name: `Re-Forgematica`
- Upstream base: `ThinkingStudios/Litematica-Forge`
- Original schematic mod: `maruohon/litematica`
- Target branch pushed to GitHub: `main`
- Local development branch used during this work: `1.18.x-forge/dev`

## Licensing Decision

The combined fork remains LGPLv3 because it modifies and extends Litematica/Forgematica code.

Files added for publishing:

- `README.md`: public project overview and feature summary.
- `LICENSE_RE-FORGEMATICA.md`: MIT-style grant for new Re-Forgematica additions to the maximum extent separable from upstream LGPL code.
- `LICENSE`: left as the upstream LGPLv3 license text.

Important constraint: the whole mod should not be advertised as pure MIT unless upstream Litematica/Forgematica copyright holders grant relicensing permission. The practical setup is LGPLv3 for the combined fork, with an MIT-style grant for newly written standalone additions where separable.

Quark is not bundled or copied. Re-Forgematica only uses optional runtime reflection against Quark when it is installed by the user. Sophisticated Backpacks is handled the same way.

## Sophisticated Backpacks Easy Place Support

Primary file:

- `src/main/java/fi/dy/masa/litematica/util/SophisticatedBackpacksCompat.java`

Related files:

- `src/main/java/fi/dy/masa/litematica/materials/MaterialListUtils.java`
- `src/main/java/fi/dy/masa/litematica/util/InventoryUtils.java`
- `src/main/java/fi/dy/masa/litematica/util/WorldUtils.java`
- `src/main/java/fi/dy/masa/litematica/config/Configs.java`

Behavior added:

- Easy Place can open carried Sophisticated Backpacks when the required placement item is missing from player inventory.
- The opened backpack container is searched for the exact item needed for the schematic block.
- The search waits for server container sync instead of immediately failing.
- The logic retries and can fall through multiple carried backpacks.
- The transfer path uses conservative inventory clicks to move the required item into a hotbar slot.
- The displaced hotbar stack is moved back into the backpack where possible to reduce inventory clutter.
- Easy Place warning spam is suppressed while a backpack transfer is pending.

Config added in Generic:

- `sophisticatedBackpacksAvoidSwappingTools`
- Default: `true`
- Purpose: avoid putting the currently held tool or damageable item into the backpack when Easy Place needs to swap in a block.

Design note: this integration is intentionally optional. It should not crash or prevent base Forgematica behavior if Sophisticated Backpacks is not installed or if reflection fails.

## Quark Rotation Lock Support

Primary file:

- `src/main/java/fi/dy/masa/litematica/util/QuarkRotationCompat.java`

Related files:

- `src/main/java/fi/dy/masa/litematica/util/WorldUtils.java`
- `src/main/java/fi/dy/masa/litematica/scheduler/ClientTickHandler.java`

Behavior added:

- Easy Place can temporarily use Quark's rotation-lock support before sending the placement interaction.
- This is done through runtime reflection, with no compile-time Quark dependency.
- The lock is cleared immediately after placement.
- A second clear is sent two client ticks later to avoid leaving the player stuck in a locked orientation state.

Scope:

- Only attempted when the active Easy Place protocol is `Slabs only` or `None`.
- Single-player v3 and Carpet-compatible v2 behavior are preserved and should not be overridden.

Design note: this does not spoof visible player camera rotation. It relies on Quark's own client/server rotation-lock mechanism when Quark is present on both sides.

## Proxy / Multi-Server Storage Scoping

Primary file:

- `src/main/java/fi/dy/masa/litematica/util/NetworkServerStorageScope.java`

Related files:

- `src/main/java/fi/dy/masa/litematica/mixin/MixinClientPlayerEntity.java`
- `src/main/resources/forgematica.mixins.json`
- `src/main/java/fi/dy/masa/litematica/data/DataManager.java`
- `src/main/java/fi/dy/masa/litematica/event/WorldLoadListener.java`
- `src/main/java/fi/dy/masa/litematica/scheduler/ClientTickHandler.java`
- `src/main/java/fi/dy/masa/litematica/world/SchematicWorldHandler.java`

Problem addressed:

On a multi-server network, dimensions can have identical names on every backend. Saving only by server address and dimension causes placements from one backend to appear at the same coordinates on another backend, or disappear after transfer/reload.

Behavior added:

- Outgoing chat commands are watched client-side.
- Configured transfer command names are treated as backend server aliases.
- Before a transfer command is sent, current placement data is saved.
- After the new world loads, the pending alias becomes the active storage scope.
- Placement storage filenames include the backend alias.
- If a transfer leaves the schematic world missing, the client tick handler recreates it and reloads placement data.

Default Generic config:

- `serverStorageScopeFromCommands`: `true`
- `serverStorageScopeCommands`: `millennium, vaulthalla, eon, century, asgard, pog, lobby, omega, echo, vault`
- `serverStorageScopeInitial`: empty

Use `serverStorageScopeInitial` only if the first backend after login is always known. Example: set it to `lobby` if every session starts in lobby.

Compatibility:

- Normal single-player and direct multiplayer clients remain unscoped unless a configured command is sent.
- This does not require Velocity APIs or server-side plugin support.

## Server Transfer Crash / Render Hardening

Files touched:

- `src/main/java/fi/dy/masa/litematica/render/OverlayRenderer.java`
- `src/main/java/fi/dy/masa/litematica/materials/MaterialListHudRenderer.java`
- `src/main/java/fi/dy/masa/litematica/schematic/placement/SchematicPlacementManager.java`
- `src/main/java/fi/dy/masa/litematica/util/RayTraceUtils.java`
- `src/main/java/fi/dy/masa/litematica/util/SchematicUtils.java`
- `src/main/java/fi/dy/masa/litematica/util/WorldUtils.java`
- `src/main/java/fi/dy/masa/litematica/world/SchematicWorldHandler.java`
- `src/main/java/fi/dy/masa/litematica/scheduler/ClientTickHandler.java`

Behavior added:

- Avoids crashes when the schematic world is temporarily null during server transfer.
- Recreates schematic world state when the client has a real world/player again but schematic world state is missing.
- Prevents material list, overlay, ray trace, and placement paths from assuming schematic world availability during reconnect.

Observed result from testing:

- Loaded schematics began surviving transfers.
- Schematics rendered again after returning to a scoped backend.
- The null schematic world crash stopped reproducing during transfer tests.

## Forge Controls Keybind

Primary file:

- `src/main/java/org/thinkingstudio/forgematica/ForgeKeybindings.java`

Related files:

- `src/main/java/org/thinkingstudio/forgematica/Forgematica.java`
- `src/main/resources/assets/forgematica/lang/en_us.json`
- `src/main/java/fi/dy/masa/litematica/config/Hotkeys.java`

Behavior added:

- Adds normal Minecraft/Forge Controls category: `Forgematica`
- Adds keybind: `Open Menu`
- Default key: unbound (`InputUtil.UNKNOWN_KEY`)
- Opens the existing Litematica/Forgematica main menu.

Internal hotkey change:

- `Hotkeys.OPEN_GUI_MAIN_MENU` default changed from the old internal menu key to no default.
- Reason: the Forge Controls keybind is now the default user-facing way to open the menu.
- Other internal MaLiLib/Forgematica hotkeys remain configurable inside the mod.

## Native Schematic Printer

Primary file:

- `src/main/java/fi/dy/masa/litematica/util/SchematicPrinter.java`

Related files:

- `src/main/java/fi/dy/masa/litematica/scheduler/ClientTickHandler.java`
- `src/main/java/fi/dy/masa/litematica/config/Configs.java`
- `src/main/java/fi/dy/masa/litematica/config/Hotkeys.java`
- `src/main/java/fi/dy/masa/litematica/event/KeyCallbacks.java`
- `src/main/java/fi/dy/masa/litematica/util/WorldUtils.java`
- `src/main/java/fi/dy/masa/litematica/util/InventoryUtils.java`
- `src/main/java/fi/dy/masa/litematica/util/SophisticatedBackpacksCompat.java`
- `src/main/java/fi/dy/masa/litematica/util/QuarkRotationCompat.java`

Behavior added:

- Adds a native Re-Forgematica printer, intentionally implemented without copying NeoForgematicaPrinter source because NeoForgematicaPrinter is AGPLv3.
- Runs from the normal client tick handler.
- Places at most one nearby missing schematic block per configured interval.
- Only considers positions inside the current render layer range, so single-layer rendering limits printer placement to that layer.
- Scans around the player within the configured printer range and sorts candidates nearest-first.
- Uses normal player placement packets rather than commands.
- Uses `MaterialCache` to resolve the required placement item.
- Uses existing `InventoryUtils.schematicWorldPickBlock` behavior for survival and creative mode.
- In creative mode, the existing creative pick-block path can synthesize the required item into the hotbar before placement.
- In survival, existing inventory pick-block behavior is used, including Sophisticated Backpacks item pulls.
- If Sophisticated Backpacks starts a pending transfer, the printer pauses placement until the transfer finishes.
- Keeps Quark rotation lock support in the placement path via `WorldUtils.interactBlockWithOptionalQuarkRotationLock`.

Printer configs added in Generic:

- `printerMode`
  - Default: `false`
  - Purpose: toggle automatic schematic printing on/off.
- `printerInterval`
  - Default: `6`
  - Range: `1` to `100`
  - Purpose: number of client ticks to wait between printer placement attempts.
- `printerRange`
  - Default: `4.5`
  - Range: `1.0` to `16.0`
  - Purpose: max placement range in blocks; default matches vanilla survival reach, with room for servers that increase reach.

Printer hotkeys added:

- `printerActivation`
  - Default: `V`
  - Behavior: hold to print even if `printerMode` is off.
  - Uses `KeybindSettings.PRESS_ALLOWEXTRA_EMPTY` so it can function while other keys/buttons are pressed.
- `printerToggle`
  - Default: `CAPS_LOCK`
  - Behavior: toggles `printerMode`.
  - Uses `KeybindSettings.PRESS_ALLOWEXTRA_EMPTY`.

Native printer rotation support:

- Before sending the placement interaction, the printer simulates candidate player rotations against Minecraft's own `BlockItem#getPlacementState(...)`.
- Candidate rotations cover the four horizontal directions plus straight up/down.
- The best matching simulated rotation is sent with `PlayerMoveC2SPacket.LookAndOnGround`.
- Matching currently scores common orientation/state properties such as `FACING`, `HORIZONTAL_FACING`, `AXIS`, `BLOCK_HALF`, `SLAB_TYPE`, `ROTATION`, `WALL_MOUNT_LOCATION`, `DOOR_HINGE`, `CHEST_TYPE`, and `STAIR_SHAPE`.
- If simulation cannot find a useful rotation, the printer falls back to a property-based look-direction heuristic.
- Existing Easy Place placement protocol hit-vector support is still applied for v3, Carpet v2, and slab-only behavior.
- Existing Quark rotation lock is still applied afterward when its protocol scope allows it.

Current limitations / next likely work:

- This is the first testable implementation.
- It does normal block placement and orientation assistance.
- It does not yet implement post-placement interaction guides such as stripping logs, lighting candles, filling flower pots, editing signs, tilling dirt, or cycling block states.
- More block-family-specific guide logic can be added after in-game testing identifies gaps.

## GitHub Issue Template

Added:

- `.github/ISSUE_TEMPLATE/bug_report.yml`

Purpose:

- Provides a default bug report form for crashes, Easy Place, Sophisticated Backpacks, Quark rotation, server transfers, rendering, keybinds, material list issues, and other mod behavior.
- Asks for Re-Forgematica version, Forge version, MaFgLib version, optional mods, reproduction steps, server/proxy context, logs, and screenshots.

## Build / Artifact Naming

Files changed:

- `build.gradle`
- `gradle.properties`

Current artifact naming:

- `archives_base_name=re-forgematica`
- `mod_version=1.0.0`
- `version = "${project.mod_version}-${project.minecraft_version}"`

Current built jar:

```text
build/libs/re-forgematica-1.0.0-1.18.2.jar
```

Current sources jar:

```text
build/libs/re-forgematica-1.0.0-1.18.2-sources.jar
```

## Important Implementation Constraints

- Optional integrations must remain optional. Do not add hard dependencies on Quark or Sophisticated Backpacks unless the mod metadata and distribution model are intentionally changed.
- Runtime reflection failures should disable only that compatibility layer, not the whole mod.
- Avoid server-specific APIs for backend scoping. The current implementation is client-only and command-alias based.
- Do not remove the delayed second Quark lock-clear unless another fallback is added.
- Do not copy NeoForgematicaPrinter source directly unless the licensing plan is intentionally changed; it is AGPLv3.
- Keep printer behavior throttled by `printerInterval`; do not make it place entire schematics instantly.
- Keep printer placement limited by `DataManager.getRenderLayerRange()` so rendered layers control what gets placed.
- Do not replace the LGPLv3 root license with MIT.

## Build Verification

The last build command run before publishing:

```powershell
.\gradlew.bat build
```

Result:

```text
BUILD SUCCESSFUL
```

Built jar path from the latest build:

```text
build/libs/re-forgematica-1.0.0-1.18.2.jar
```

## Published Git State

Remote added locally:

```text
reforgematica https://github.com/HoYin1600p/Re-Forgematica.git
```

The target repo originally had a one-file unrelated `main` initial commit. It was merged with `--allow-unrelated-histories`, the README conflict was resolved in favor of the Re-Forgematica README, and then pushed normally as a fast-forward update to `main`.

Commit containing the main feature/docs work:

```text
a18845b Add Re-Forgematica enhancements
```

Merge commit pushed to `main`:

```text
325d668 Merge Re-Forgematica repository root
```
