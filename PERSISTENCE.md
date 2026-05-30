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
- `CURSEFORGE_RELEASES.md`: paste-ready CurseForge project description, short summary, and version release copy.
- `CHANGELOG.md`: short publisher changelog for the current release.
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
- `src/main/java/fi/dy/masa/litematica/mixin/MixinScreen.java`
- `src/main/resources/forgematica.mixins.json`
- `src/main/java/fi/dy/masa/litematica/data/DataManager.java`
- `src/main/java/fi/dy/masa/litematica/event/WorldLoadListener.java`
- `src/main/java/fi/dy/masa/litematica/scheduler/ClientTickHandler.java`
- `src/main/java/org/thinkingstudio/forgematica/network/ServerScopeNetworking.java`
- `src/main/java/org/thinkingstudio/forgematica/network/ServerStorageScopeIdentity.java`
- `src/main/java/fi/dy/masa/litematica/world/SchematicWorldHandler.java`

Problem addressed:

On a multi-server network, dimensions can have identical names on every backend. Saving only by server address and dimension causes placements from one backend to appear at the same coordinates on another backend, or disappear after transfer/reload.

Behavior added:

- Outgoing chat commands are watched client-side.
- Configured transfer command names are treated as backend server aliases.
- The production network uses direct backend-name commands such as `/millennium`, `/vault`, `/test`, and `/usatest`, not `/server <name>`.
- If installed server-side, each backend sends a stable storage scope ID to clients on login. This is preferred over command sniffing because it works for direct login and non-chat transfer flows.
- Server-side identity is stored in `config/forgematica-server.properties`. Blank `serverStorageScopeName` uses an auto-generated `serverStorageScopeId` UUID; setting `serverStorageScopeName` gives a human-readable stable alias.
- Before a transfer command is sent, current placement data is saved.
- After the new world loads, the pending alias becomes the active storage scope.
- Placement storage filenames include the backend alias.
- If a transfer leaves the schematic world missing, the client tick handler recreates it and reloads placement data.

Default Generic config:

- `serverStorageScopeFromCommands`: `true`
- `serverStorageScopeCommands`: `millennium, vaulthalla, eon, century, asgard, pog, lobby, omega, echo, vault`
- Live test additions used in Prism: `test, usatest`
- `serverStorageScopeInitial`: empty

Use `serverStorageScopeInitial` only if the first backend after login is always known. Example: set it to `lobby` if every session starts in lobby.

Compatibility:

- Normal single-player and direct multiplayer clients remain unscoped unless a configured command is sent or a Re-Forgematica backend identity packet is received.
- This does not require Velocity APIs or separate server-side plugin support. The same jar can run on the server for the identity packet path.

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

- Adds normal Minecraft/Forge Controls category: `Re-Forgematica`
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
  - Default: unbound
  - Behavior: hold to print even if `printerMode` is off.
  - Uses `KeybindSettings.PRESS_ALLOWEXTRA_EMPTY` so it can function while other keys/buttons are pressed.
- `printerToggle`
  - Default: unbound
  - Behavior: toggles `printerMode`.
  - Uses `KeybindSettings.PRESS_ALLOWEXTRA_EMPTY`.

Native printer rotation support:

- Before sending the placement interaction, the printer simulates candidate player rotations against Minecraft's own `BlockItem#getPlacementState(...)`.
- Candidate rotations cover the four horizontal directions plus straight up/down.
- The simulation also tries all six clicked block faces, so face-sensitive blocks like end rods can choose horizontal placement instead of always using the printer's original upward click face.
- Generic simulation only tries target-space hit results. It does not try neighboring support-face clicks for normal blocks, because that can place blocks into adjacent positions when the next render layer has support below it.
- Candidate support faces are ordered by the schematic state's own facing properties before fallback/all directions, so exact-match ties prefer the intended schematic face.
- Support-face placements are performed while temporarily sneaking, matching normal player shift-place behavior so interactable support blocks like trapdoors do not consume the right-click and open/close instead.
- Blocks with a required clicked support face now use a direct singleton support-face placement plan instead of generic face guessing.
- This strict support-face path covers `WALL_MOUNT_LOCATION` blocks such as buttons/levers, vanilla wall-attached blocks such as wall banners/signs/skulls/torches and ladders, plus modded wall-attached blocks whose class name/facing properties expose the same wall-facing pattern.
- Wall banners need this direct placement plan because banner items are `StandingAndWallBlockItem`s; the generic `BlockItem#getBlock().getPlacementState(...)` simulation only represents the standing banner block and cannot infer wall-banner placement.
- The best matching simulated rotation is applied locally during the actual `interactBlock` call and sent with `PlayerMoveC2SPacket.Full`, then the player's visible rotation is restored.
- Matching scores all shared block-state properties instead of a hardcoded vanilla-only list, so modded stick/rod-style blocks can work if their placement result exposes the right state properties.
- Runtime/transient properties that normal placement cannot force are ignored by property name while scoring simulated placement matches: `powered`, `triggered`, `extended`, `locked`, `lit`, and `enabled`.
- Directional redstone components keep a direct orientation fallback before generic simulation, covering observers/repeaters/comparators/pistons/dispensers/droppers when a protocol mode cannot encode full state.
- Observers are special-cased because vanilla observer placement uses the player look direction as `FACING`, while most other generic `FACING` redstone blocks use the opposite.
- Compared against NeoForgematicaPrinter after observer failures: it does not have an observer-specific guide, but its prepare action sends a full position+rotation movement packet before placement. The printer now sends `PlayerMoveC2SPacket.Full` instead of look-only so server-side placement sees the intended rotation before the interact packet.
- The `BlockItem#getPlacementState` mixin now decodes accurate-placement protocol data whenever encoded hit-vector data is present, even if Easy Place mode is off. This is the primary fix for printer-driven directional state: printer mode uses the same protocol data as Easy Place, so observers and other directional blocks are not left to vanilla player-look placement when the user is printing with Easy Place disabled.
- Standing heads/skulls, plus other `ROTATION`/16-step standing blocks, have a direct yaw fallback modeled after NeoForgematicaPrinter's rotation guide. Their placement plan clicks the top face of the support block below the schematic position while preserving the encoded hit-vector data, so `WallStandingBlockItem` chooses the standing/floor variant instead of a wall variant.
- Skull items inherit placement from `WallStandingBlockItem`, which overrides `BlockItem#getPlacementState`; `MixinWallStandingBlockItem` applies the same encoded hit-vector protocol decode there so printer placements can force `ROTATION` even when Easy Place mode is off. Wall-mounted heads/skulls continue through the strict support-face path.
- Exact block-state matches are strongly preferred and mismatched properties are penalized, which helps blocks like ladders choose the schematic face when multiple adjacent faces are valid placement targets.
- If simulation cannot find a useful rotation, the printer falls back to a property-based look-direction heuristic.
- Existing Easy Place placement protocol hit-vector support is still applied for v3, Carpet v2, and slab-only behavior.
- Printer hit vectors always run through the slab/stair Y-position helper after protocol encoding, so top slabs/top-half blocks placed into air do not default to bottom placement.
- Existing Quark rotation lock is still applied afterward when its protocol scope allows it.
- Before swapping items or placing, the printer checks `BlockState#canPlaceAt` against the real client world. This makes supported blocks such as banners, buttons, levers, and ladders wait until their required support face/block exists instead of attaching to some other valid nearby surface.
- Layered double-height handling:
  - Blocks using the vanilla double-block-half property skip their lower half during printer scans.
  - When the upper half is rendered, the printer resolves the placement anchor.
  - Doors and tall plants use the matching lower schematic block as the placement anchor because vanilla places them from the bottom.
  - Other double-height blocks using the same property are treated as top-anchored, which covers banner-style blocks whose connection point is above the lower visual half.

Current limitations / next likely work:

- This is the first testable implementation.
- It does normal block placement and orientation assistance.
- It does not yet implement post-placement interaction guides such as stripping logs, lighting candles, filling flower pots, editing signs, tilling dirt, or cycling block states.
- More block-family-specific guide logic can be added after in-game testing identifies gaps.

1.0.4 release handoff, 2026-05-29:

- Version bumped from `1.0.3` to `1.0.4` in `gradle.properties`.
- Fixed connection compatibility for servers that do not have Re-Forgematica installed.
- `ServerScopeNetworking` now uses `NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)` for both client and server version predicates.
- This keeps the backend identity packet optional: installed servers can send a scope ID, while missing-server-channel connections fall back to client-side command scoping.
- `README.md`, `CHANGELOG.md`, `CURSEFORGE_RELEASES.md`, and this persistence file were updated for 1.0.4.
- Final build passed with `.\gradlew.bat build`.
- Final jar for release testing/publishing: `build/libs/re-forgematica-1.0.4-1.18.2.jar`.
- Final sources jar: `build/libs/re-forgematica-1.0.4-1.18.2-sources.jar`.
- Follow-up live test finding: command aliases must be pending transfer hints, not immediate loads. The command path saves current placements, preserves the current scope through disconnect, and applies the pending alias after the next world load only if no server identity packet supersedes it.
- Mixed install finding: if the destination backend has Re-Forgematica installed server-side, the server-provided UUID/name is authoritative after reconnect. Command aliases remain the fallback for backends without the server-side identity packet.
- Same-server reconnect finding: a configured command such as `/usatest` can reconnect the player even when already scoped as `usatest`. Active scoped storage must remember the last valid server/world name and dimension so reconnect timing cannot fall through to `forgematica_default.json`.
- Live instance config had `usatestt`; corrected it to `usatest` in `config/forgematica.json`.
- After live testing confirmed the fix, temporary `[storage-scope]` diagnostic logging and the unused `commandScopeActive` bookkeeping were removed.
- Release docs were updated for CurseForge publication: `README.md`, `CHANGELOG.md`, `CURSEFORGE_RELEASES.md`, and this persistence file.
- Current cleaned test jar copied to the Prism instance has SHA-256 `8A508288AFFF0DB1B0FEAE89CB7B0EB8B7EA2A5FCA81DF6A0AFB60482D0129A8`.

1.0.3 release handoff, 2026-05-29:

- Version bumped from `1.0.2` to `1.0.3` in `gradle.properties`.
- Added optional server-side backend identity packet support:
  - `src/main/java/org/thinkingstudio/forgematica/network/ServerScopeNetworking.java`
  - `src/main/java/org/thinkingstudio/forgematica/network/ServerScopePacket.java`
  - `src/main/java/org/thinkingstudio/forgematica/network/ServerStorageScopeIdentity.java`
  - `src/main/java/org/thinkingstudio/forgematica/network/ServerStorageScopeEvents.java`
- Server config `config/forgematica-server.properties` auto-generates `serverStorageScopeId` on each backend. `serverStorageScopeName` can override it with a readable stable alias.
- Common/server registration is in `src/main/java/org/thinkingstudio/forgematica/Forgematica.java`; client-only render, GUI, schematic, hotkey, MaLiLib, and MaFgLib initialization is isolated in `src/main/java/org/thinkingstudio/forgematica/client/ForgematicaClient.java` and called only on `Dist.CLIENT`.
- `META-INF/mods.toml` marks `mafglib` as `side = "CLIENT"` so dedicated servers do not require MaFgLib for the backend identity packet path.
- Added `MixinScreen` so direct chat command submissions are captured earlier than `ClientPlayerEntity.sendChatMessage`; command sniffing remains a fallback when server identity packets are unavailable.
- Fixed unbound printer activation being treated as active by guarding `isKeybindHeld()` with a non-empty key list.
- Fixed the dedicated-server mod loading failure reported in `crash-2026-05-29_22.44.51-fml.txt`, where Forge rejected the jar because MaFgLib was still declared as a server-side dependency.
- `README.md`, `CHANGELOG.md`, `CURSEFORGE_RELEASES.md`, and this persistence file were updated for 1.0.3.
- Final build passed with `.\gradlew.bat build`.
- Final jar for release testing/publishing: `build/libs/re-forgematica-1.0.3-1.18.2.jar`.
- Final sources jar: `build/libs/re-forgematica-1.0.3-1.18.2-sources.jar`.

1.0.2 release handoff, 2026-05-29:

- Removed the default `V` and `CAPS_LOCK` printer hotkeys.
- `printerActivation` and `printerToggle` now default to unbound in `src/main/java/fi/dy/masa/litematica/config/Hotkeys.java`.
- Version bumped from `1.0.1` to `1.0.2` in `gradle.properties`.
- `README.md`, `CHANGELOG.md`, and `CURSEFORGE_RELEASES.md` now describe the unbound printer defaults.
- Final build passed with `.\gradlew.bat build`.
- Final jar for release testing/publishing: `build/libs/re-forgematica-1.0.2-1.18.2.jar`.
- Final sources jar: `build/libs/re-forgematica-1.0.2-1.18.2-sources.jar`.

1.0.1 release handoff, 2026-05-29:

- Version bumped from `1.0.0` to `1.0.1` in `gradle.properties`.
- README now has a `Latest Patch` section for `1.0.1` near the top, plus the native printer feature section and printer attribution in Credits.
- `CURSEFORGE_RELEASES.md` added in the same style as ArcaneBeam's release-copy file. It includes:
  - Short CurseForge summary under 256 characters.
  - Full project description copy.
  - `1.0.1` release copy.
- `CHANGELOG.md` now contains the `1.0.1` publisher changelog.
- Mod icon was replaced in both required locations:
  - `src/main/resources/icon.png`
  - `src/main/resources/assets/forgematica/icon.png`
- Both icon files were verified by SHA-256 against user-provided source `E:\server\Re-Forgematica\mod-icon.png`.
- Final build after icon correction passed with `.\gradlew.bat build`.
- Final jar for release testing/publishing: `build/libs/re-forgematica-1.0.1-1.18.2.jar`.
- Final sources jar: `build/libs/re-forgematica-1.0.1-1.18.2-sources.jar`.

End-of-night printer handoff, 2026-05-28:

- Latest confirmed good test from user: the strict support-face rule fixed wall banners/ladders attaching to the wrong nearby face.
- Regression found immediately after: when moving to the next render layer, normal blocks/slabs could be double-placed one block higher because generic placement simulation was allowed to click neighboring support faces.
- Fix applied: generic simulation now only tries hit results inside the target blockspace. Neighbor support-face clicks are reserved for blocks with a required clicked support face via `getDirectPlacementPlan(...)`.
- `.\gradlew.bat build` passed after that fix.
- Jar to test at that time: `build/libs/re-forgematica-1.0.1-1.18.2.jar`.
- Next defensive hardening idea: before sending a printer placement interaction, derive the actual `ItemPlacementContext` placement position and reject the plan unless it targets the intended schematic position. Allow known exceptions only when the schematic intentionally merges into the same block, such as the second half of a double slab.

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
- `src/main/resources/icon.png`

Current artifact naming:

- `archives_base_name=re-forgematica`
- `mod_version=1.0.4`
- `version = "${project.mod_version}-${project.minecraft_version}"`

Current built jar:

```text
build/libs/re-forgematica-1.0.4-1.18.2.jar
```

Current sources jar:

```text
build/libs/re-forgematica-1.0.4-1.18.2-sources.jar
```

Current mod icon:

- `src/main/resources/icon.png`
- `src/main/resources/assets/forgematica/icon.png`
- Source provided by user: `E:\server\Re-Forgematica\mod-icon.png`
- `META-INF/mods.toml` still references `logoFile="icon.png"`.

## Important Implementation Constraints

- Do not bump `mod_version`, update release-version headings, or prepare a new release version unless the user explicitly asks for a version bump or release prep. Bugfix builds for live testing may stay on the current version until the user decides to publish.
- Optional integrations must remain optional. Do not add hard dependencies on Quark or Sophisticated Backpacks unless the mod metadata and distribution model are intentionally changed.
- Runtime reflection failures should disable only that compatibility layer, not the whole mod.
- Keep dedicated-server code limited to backend identity generation and packet sending. Rendering, GUI, schematic, hotkey, MaLiLib, and MaFgLib paths must stay client-only.
- Command-alias backend scoping remains the client-side fallback when a server identity packet is unavailable.
- Live server-transfer debugging showed two persistence hazards: command aliases must be treated as pending transfer hints, not immediate loads, and active scoped storage must not fall back to `forgematica_default.json` while Minecraft temporarily has no server/world name during reconnect.
- When a server provides its own UUID scope, that server-provided scope is authoritative after reconnect; command aliases are still needed for backend switches where the destination server does not have Re-Forgematica installed server-side.
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
build/libs/re-forgematica-1.0.4-1.18.2.jar
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
