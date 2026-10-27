# Changelog

All notable changes to **Combatant Client** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [0.3.0 — in progress] - 2026-10-24

### Platform
- Synced with upstream `origin/26.3`: Combatant now targets **Minecraft 26.3**. Local shaders declare explicit SPIR-V interface locations; the UI material-primitive batch binds its `UIGlassFrame` uniform (fixed a first-frame crash).
- **Sodium**: Combatant now accepts any Sodium `0.9.x` from `0.9.2` (`>=0.9.2 <0.10.0`) instead of exactly `0.9.2+mc26.3`, and is built against `0.9.3-beta.1+mc26.3`. Verified at runtime with 0.9.3-beta.1 together with Iris 1.11.7, Lithium, More Culling 1.9.0, Entity Culling, ImmediatelyFast, Litematica, EMF/ETF and the rest of a 25-mod pack. README requirements updated from 26.2 to 26.3.

### Inventory backend (`InventorySwap` / `PacketWindow`)
- **Grim PacketOrderE fix**: ElytraFly's silent firework no longer swaps to the rocket, uses it and swaps back inside one tick. `PacketWindow` tracks the Grim tick window from outgoing packets; a held-slot change is only sent while it does not follow an attack, right click, release, sprint/sneak/glide/bed/mount change or inventory open of the same window, and never after the tick's movement packet (Post). A silent use is now "swap, use, restore at the start of the next client tick"; a use the window does not allow yet returns `false` and is retried by the caller.
  Live test (Grim 2.3.74 with experimental checks on, so PacketOrderE runs): 10 s of firework flight with the rocket in another hotbar slot (a silent swap for every rocket, 330 blocks flown): 0 violations.
- **One backend**: `InventorySwap` keeps a single held-slot authority (leases whose slot change is not allowed yet are sent at the next tick start) and one serialized, owner-scoped window-click queue (MultiActionsC/D movement gate, movement lock, humanized delay). `InventorySwapPolicy`, `InventorySwapMode` and the global swap defaults (policy, scope, visibility, restore, prefer hotbar, legit/strict wait ticks, movement lock) are gone together with their settings, including the per-module swap-policy settings of HoverTotem, Offhand refill and ShulkerDropper. `SlotResult.slot()` is always a player-inventory index (0-8 hotbar, 9-35 main).

### Unified Automation Runtime (`combatant.client.automation`)
- **Control leases**: `ActionCoordinator` arbitrates rotation, movement keys, jump, sneak, sprint, hotbar, inventory and interaction by named priority tiers (`ActionPriority`, same scale as `RotationManager`). Preemption notifies the loser; ties keep the current holder; slot leases taken directly through `InventorySwap` are respected.
- **Tasks and actions**: `AutomationTask` + composable actions (`Sequence`, `Selector`, `Parallel`, `Retry`, `Timeout`, `Guard`, `Defer`) and primitives (`Rotate`, `Move`, `Jump`, `HoldJump`, `Sneak`, `SprintTap`, `PlaceBlock`, `BreakBlock`, `InteractBlock`, `InteractEntity`, `UseItem`, `Inventory`, `Navigate`, `WaitTicks`, `WaitUntil`). Actions run before the movement packet and release every backend on cancel.
- **Lifecycle**: tasks are cancelled on module disable, world change, disconnect, respawn and panic/unhook (new `WorldChangeEvent`). Player WASD/jump/sneak and physical mouse look take control back from tasks that yield to manual input.
- **Placement**: `BlockPlacer` plans with `PlacementPlanner` and places through `PlaceBlockAction`; a position is only dropped once the server acknowledges the prediction sequence and the block is there (`PlacementTracker`, `PlacementAckMixin`). Rejected cells retry with backoff. Surround, SelfTrap, AutoTrap, HoleFill and AutoWeb build their own `PlacementConstraints` from their own settings.
- **Navigation**: `NavigationProvider` API with the built-in walker (A*/route follower) and an optional Baritone adapter loaded only when Baritone is installed.
- **Migrated**: HoleSnap (runtime `MoveAction`), AutoTool (hotbar lease instead of raw slot selection), shared `MovementKeys` replaces three WASD helpers.
- **SpearAssist**: vanilla already refuses a jab below full charge, so the redundant `respect_cooldown` toggle and its `MinecraftMixin` hook were removed. New: charge HUD (charge-up bar, Dismount/Knockback/Damage windows with remaining time, predicted stab damage), reach band marker (too close / in range / too far, creative-aware) and optional auto re-charge through the vanilla release path. Rules live in the fully implemented and tested `combatant.client.util.combat.SpearKinetics`.

### Schematics
- **Formats**: Litematica `.litematic`, Sponge `.schem` (v1–v3) and vanilla structure `.nbt`, with placement transform (origin, rotation, mirror) and a live world diff that judges redstone by functional state.
- **Schematics menu**: a **Schematics** tab in the ClickGui and the `@schematic menu` screen (aliases `schem`, `litematic`, `build`). Searchable library with size/block-count chips, Load / Refresh / Open folder actions, folder path, details and a materials list with inventory counts. The centre is an orbitable, textured 3D model of the schematic.
- **Schematics tab owns its modules**: SchematicBuilder and SchematicProjection no longer appear in the Modules tab. The Schematics tab has an on/off toggle and a **Settings** button for each, which opens the regular ClickGui settings panel for that module. They stay real modules (keybinds, `@toggle`, config profiles, ModuleList). New `ModuleInfo.listed` flag hides a module from the Modules tab.
- **SchematicProjection**: in-world ghost blocks and outlines for the loaded schematic, coloured by diff state (missing / wrong / correct), with layer limits.
- **SchematicBuilder** builds the loaded schematic in survival:
  - **Ordering**: lower layers first, then farthest-first so near blocks never wall off far faces; within a step, the cell closest to the current aim goes first so the view sweeps.
  - **Exact placement**: stair and slab clicks aim clear of the half boundary; look-dependent blocks (pistons, observers, repeaters, comparators, droppers…) wait for a rotation that reproduces the planned facing, with a robustness check against rotation rounding; sneaking clicks on interactive supports; follow-up clicks set repeater delays, comparator modes, note pitches and levers.
  - **Redstone safety**: machines are built in phases so nothing fires half-built; cells that would trigger TNT are flagged; the next band opens early only when the build would otherwise stall.
  - **Movement**: walks, climbs, jump-pillars and bridges to cells out of reach, with line-of-sight path smoothing and cached terrain snapshots; climbs onto the layer below before building a layer from the ground, and falls back to building from below when nothing can lift the feet (fixes a climb-gate deadlock).
  - **Supplies**: maps every block (including redstone and stacked blocks) to the item that places it; restocks from the main inventory and from indexed nearby chests (`ChestIndex`, supply points), walking to them when needed.
  - **Terraforming**: breaks wrong blocks in the footprint and holds the layer until they are cleared.
  - **Scaffolding**: places helper blocks next to floating cells, removes all helper and pillar blocks at the end, and also clears them mid-build when placement has stalled behind them for 20 s (once per progress step, so it cannot loop).
  - **Tools**: mining picks the fastest tool and now also pulls a faster tool (e.g. a pickaxe) out of the main inventory into the restock slot.
  - **Problems panel**: approximate placements, failed adjustments and blocked cells are listed in the schematic menu.


### Security & Resource Hardening
- **AutoAuth Security**: Excluded sensitive values (passwords) from config profile exports and share codes via `ConfigValue.sensitive()`. Auto-authentication is now restricted to explicitly configured trusted servers (`autoAuthTrustedServers`).
- **Schematics NBT Bounds**: Replaced unbounded heap parsing (`NbtAccounter.unlimitedHeap()`) in `LitematicReader` and `SchematicFormats` with strict 512 MiB quota and 512 depth limits via `SchematicFormats.nbtAccounter()` to prevent allocation and decompression exhaustion.
- **Config Share Code Decompression Limits**: Enforced 16 MiB max decompressed payload size in `ConfigProfileBinaryCodec` (`InflaterInputStream`), payload length cap (16 MiB), entry count limit (10,000), value count limit (10,000), collection size limit (10,000), and string length limit (1 MiB).
- **Render Pipeline & Native Stability**: Deferred pipeline GPU compilation from `ShaderManager.apply` to render passes, guarded font destruction against double-free of native font data in `CustomTextRenderer` and `MsdfFont`, and made `MeshBuilder` capacity checks unconditional: a vertex/index write past the reserved native buffer now grows the buffer and logs the caller once, instead of corrupting the native heap (previously only checked in development builds). `Texture.upload` rejects buffers larger than the target image.
- **Native heap corruption on resource reload (fixed)**: `MsdfFont` and `Font` allocated `STBTTFontinfo` with `create()` (a GC-owned buffer) but released it with `free()`, passing a pointer jemalloc never allocated to `je_free`. Every font reload (e.g. after a server resource pack) corrupted the jemalloc heap and crashed the game (`EXCEPTION_ACCESS_VIOLATION` in `je_malloc`/`je_free`, or `STATUS_HEAP_CORRUPTION`) at the next native allocation. The invalid `free()` calls are removed.
- **Module stability sweep**: new `@stability` command (singleplayer only) toggles every module in turn, holding each for 40 ticks, and records isolated failures, logged errors and JVM crashes in `combatant/stability/report.md`. Progress is synced before each module, so a run resumes after a crash and attributes the crash to the module.
- **Stability scripts**: new `probe` (logs position, speed, gliding, held items and rockets without sending anything) and `screenshot <name>` steps.
- **Static audit fixes**: InstaCart restores the hotbar slot and releases the bow when reset mid-draw; AutoCrystal entity add/remove hooks are failure-isolated; ESP frees its shader TextureTargets on disable and never keeps a half-built set; ServerRPSpoofer sends its delayed packets on the client thread; FakePlayer/DefineTarget attack callbacks are failure-isolated; InventoryMove flushes held Grim clicks on disable instead of dropping them; Velocity drops delayed packets queued on a previous connection; Timer no longer resets the external tick timer owned by AutoDodge/Criticals.
- **Attack swing packet (Grim PacketOrderB / Vulcan BadPackets "No Swing")**: in 26.3 `LivingEntity.swing` only animates locally; vanilla tells the server about the arm swing with a separate `ServerboundPunchPacket`. Every Combatant attack, miss and mining swing (AttackUtil and therefore KillAura/AutoClicker/AutoAttack/TriggerBot, AutoCrystal, AutoHitCrystal, CrystalClearer, RubberHand, ProjectilePuncher, InteractEntityAction, BlockInteractionPipeline mining, BuildMover, AntiAFK) skipped it, so the server saw swing-less attacks. All of them now go through `AttackUtil.swingMainHand`, which sends the punch exactly like vanilla `startAttack`/`continueAttack`.
### Anticheat Compatibility (Grim / Vulcan)
- **Grim RotationPlace**: block clicks are sent before the tick's movement packet, so Grim checks them against the rotation it last received. The builder's click gate now requires a ray along that last-sent rotation to hit the clicked block's full cube within interaction range (`BlockInteractionPipeline.rayMeetsCube`). Live test: 0 flags (8 before).
- **Grim MultiActionsC**: inventory clicks (restock, tool fetch) only go out on a tick the server sees as stationary and not sneaking, even while the input is locked (`InventorySwap`).
- **Vulcan BadPackets K**: the mover's sneak is paced by `SneakPacing` (held ≥ 2 ticks, starts ≥ 3 ticks apart).
- **Rotations**: the sent yaw stays continuous (no 360° wraps), and pillaring never uses blocks the build still needs.
- **Grim PositionPlace (Surround and every other placer)**: a click may only name a face the eyes are in front of, both where the server last saw them and now (`FaceVisibility`). The shared placement planner offered faces pointing away from the player whenever a wall range was set (Surround, SelfTrap, AutoTrap, HoleFill, AutoWeb and Burrow default to 3), so Surround clicked hidden faces, e.g. the sides of the block under the feet. AutoCrystal, AutoAnchor, AutoBed, AutoHitCrystal and the anchor macros now pick faces the same way, and click/attack reach is measured from the last sent eyes as well (FarPlace, Reach).
- **Grim RotationPlace for every block click**: Grim settles a queued click with the rotation and position it last received whenever a slot change, a transaction reply or a late movement packet comes first, and after one miss it re-checks every later click that way, so a rotation sent together with its click got flagged and then most placements were cancelled. Shared placement (`PlaceBlockAction`), block interactions (`InteractBlockAction`: anchor charging/detonating, AutoCity, SpawnerProtect), AutoCrystal, AutoAnchor and AutoBed now click only once the rotation the server already has meets the block; placing aims one cell ahead, so it still places a cell per tick. Live test on Paper 26.3 + Grim 2.3.74: Surround 0 violations and all 14 wall blocks of the edge and corner scenarios placed (before: 5 PositionPlace and 41 RotationPlace violations, 2 of the 14 placed); AutoCrystal and AutoAnchor 0 violations while damaging the target.
- **Freecam keeps reporting (Grim BadPacketsR)**: vanilla sends movement packets only while the player is the camera, so the player went silent while Freecam viewed through its own entity. The player now keeps reporting like vanilla; *Freeze player* holds the player only while it stands still on the ground (never mid-air) and follows server teleports. The *Player* input mode (toggle key) now moves the player.

### Staff / Admins
- **Vanish detection moved into the Admins system**: the tab-completion probe from the former AntiVanish module now feeds `StaffTracker`. Every interval a `/tell ` completion request is sent; names the server offers but keeps out of the tab list for two probes in a row are marked **VANISH**, so the Admins HUD shows vanished players even out of render distance. The existing entity-without-tab-entry check already covered AntiVanish's tab-list desync check. New Admins HUD settings: *Detect vanish via tab completion* (default on) and *Vanish probe interval* (5–120 s, default 15). Multiplayer only.

### Combat
- **Hitbox**: new *Static hitboxes* option (+ *players only*) keeps targets' standing-size box while they sneak, crawl, swim or glide, for both rendering and ray-casts. Replaces the separate `StaticHitboxes` module; `StaticHitboxes`, `StaticHitbox` and `NoShrinkHitbox` remain as aliases of Hitbox.
- **New modules**: `HoverTotem`, `ShiftTap`, `AnchorMacro`, `DoubleAnchor` (place, charge and detonate anchors with self-damage safety; anchor self-damage prediction no longer lets the anchor shield the player from its own blast), `AutoCity`, `Burrow`, `CrystalOptimizer`, `PearlCatch`, `AntiMiss`, `AutoHitCrystal`, `AutoStun`, `HitSelect`, `InstaCart`, `JumpReset`, `RightClickShield`.
- **Legit subcategory**: `DoubleAnchor`, `HoverTotem` and `JumpReset` moved to Combat → Legit.
- **Click timing on 1.8 servers**: KillAura, AutoAttack and ProjectilePuncher set their CPS range every tick, which rebuilt the click pattern each time and read the same never-clicking slot, so below 20 CPS they almost never clicked. The pattern is now rebuilt only when the range changes.
- **Defaults and settings**: KillAura range 3.0 (was 3.2, above vanilla reach); KillAura/AutoAttack CPS capped at 20 and shown only on legacy (1.8) attack protocol; AutoAnchor, AutoBed and the anchor macros cap reach at the block interaction range like AutoCrystal; AutoBed *strict interactions* on by default; *blocks per tick* on Surround/SelfTrap shows only for rotation-free placing (rotating places one cell per tick); JumpReset hurt-time limits show only for the HURT_TIME trigger; HoverTotem's hotbar slot shows only when used; AutoBed render options hide with render off; ProjectilePuncher smoothing settings follow its rotation mode; Criticals shows jump range/visibility wherever they apply; Hitbox padding can be 0 (static hitboxes alone); InstaCart safe distance 3.0 (max 6); TPSSync minimum scale 0.1.
- **Fixes**: HoleFill no longer queues the hole you stand in; JumpReset's PACKET trigger runs its checks on the client thread; SpearAssist auto re-charge releases before the movement packet (Grim Post); PearlCatch clears a leftover trigger, RubberHand and Hitbox clear their state on disable; KillAura/ElytraTarget no longer run their target sync/emergency avoidance twice a tick; Criticals ray-casts line of sight once per tick.
- **Labels**: CrystalOptimizer, PearlCatch, Burrow and AutoCity have English and Russian labels; orphan setting keys removed.
- **AntiMiss fake swing** (default on): a swallowed miss still swings the arm on your screen only; no packet is sent and the attack cooldown is kept.

### Movement
- **New modules**: `AutoJump`, `Spider` (vanilla/jump), `NoWeb`, `LongJump` (boost/damage), `BoatFly`, `ClickTp`, `EntityControl`, `Jesus`, `SnapTap`, `TridentBoost`.
- `Parkour` gains `EdgeJump` aliases; `Freeze` gains the `AirStuck` alias and a hold-key mode.
- **ElytraFly CONTROL mode (GrimAC-safe)**: flies with vanilla elytra physics only and steers through the silent rotation, so the server predicts every tick. WASD picks the heading relative to the camera, Space climbs, Shift dives, and with no key it holds the altitude (or glides at the target speed); rockets fire automatically below the minimum speed or while climbing/below the held altitude, only once the server already has the steering rotation. Live test on Grim 2.3.74 with experimental checks: 0 violations, held altitude within ±1 block at ~32 blocks/s for 15 s, climbed 57 blocks on Space, about one rocket every 2 s.

### Player
- **New modules**: `Nuker` (filtered, ordered breaking through `BreakBlockAction`), `AreaMiner` (+ `@areaminer`), `ClickThrough`, `NoEntityTrace`, `EnderChestPlus` (cached contents in tooltip and `@echest`), `ShulkerDropper`, `TunnelBaseFinder` (1x2 tunnel with hazard pauses and storage/spawner alerts), `ReBuffNotifier`.
- **Offhand**: gained the opt-in `inventory_refill` (open-inventory totem refill with delay and hotbar refill), formerly the separate `AutoInvTotem` module.
- **Inventory modules overhaul** (on the new backend, every click and slot change gated for Grim):
  - **AutoArmor**: one shift-click per equip, real armor only (no pumpkins/heads), durability-aware score, elytra/chestplate choice (`chest_piece`), `durability_weight`; acts without a screen only while standing still.
  - **Offhand**: split into components (`player/offhand/`); no ungated swaps, no slot restore in the tick of a release. Live test: totem moved to the offhand both on low health and as passive preference, 0 violations.
  - **AutoPotion / AutoMend**: one use per tick, restore at the next tick start; AutoMend's `packet_burst` removed. **AutoReplenish** waits while you use an item and merges stacks. **HoverTotem** `close_after`. **AutoTool** scores the mace and charged weapons.
  - **ChestStealer**: takes the first item that fits and closes only when nothing fits, detects containers by menu type, `order` (slot order / best first), delay in ticks. **ItemScroller**: one transfer per tick through the queue (50 ms default). **ShulkerDropper** cancels its task on disable; shop mode clicks through the queue (`shop_command`). **ShitDropper** `inventory_only` (default on). **EnderChestPlus** detects by menu type and keeps its cache on early close.
  - **InventoryMove**: Grim mode by default, correct swim test and screen package, `screen_slowdown`; XCarry and InventoryMove share one ordered close hook.
  - Shared helpers: `PacketWindow.stationaryForClick()` (now counts jump, like Grim), `InventoryMover` (offhand/hotbar moves), `EnchantUtil`.

### Misc
- **New modules**: `AutoLeave`, `AutoAccept`, `AutoAuth`, `ServerRPSpoofer`, `DeathCoordinates` (chat + Xaero waypoint), `AntiCheatDetect` (ping-pattern heuristic), `AutoRtp`, `AutoTpa`, `CordSnapper`, `NoInvLeak`, `SpawnerProtect`.
- **DonutSMP pack** (`misc/donut`): `AhSniper`, `AutoSell`, `AutoSpawnerSell`, `FakePay`, `OrderDropper`, `PanicPay`, `SpawnerGui`.

### Visuals
- **NetheriteFinder (DonutSMP) false positives**: boxes are drawn only after the debris prediction is verified against the world. Two checks run, each trying feature index offsets −12..12 (chunks generated by a version with a different feature list). First, predicted nether quartz/gold next to air must be there, which shows whether the seed fits. Second, visible ancient debris (touching air or lava, which no anti-xray hides) must lie in predicted veins, which shows whether debris itself follows the seed. A server can seed one feature separately (Paper `feature-seeds`), so quartz can fit while every debris box is wrong; the second check catches that. A failed check hides the boxes and says which check failed (*Show unverified boxes* overrides). Buried spots tolerate the blocks Paper anti-xray shows instead of the real ones, and the biome filter now uses the vanilla biome (the client never receives biome feature lists). The configured seed is tried along with both known DonutSMP nether seeds (6608149111735331168 and −2803778941596086821); a seed the server's seed hash confirms is tested alone, otherwise every candidate is tested on the terrain (DonutSMP's nether fits 6608149111735331168 although its hash does not match), and the results quote the hash. A zero hash (a server hiding its seed) now counts as unknown instead of "does not match". Toasts stay short ("no seed fits, see chat"); the full result with per-seed scores goes to client-side chat. Local tests on Paper 26.3:
  - Right seed: quartz 53/53 and debris 6/6 verified; debris boxes 202/202 and 214/215 real without anti-xray, 185/188 and 190/194 with anti-xray engine mode 2.
  - Wrong seed (both DonutSMP seeds on another world): server hash matches neither, quartz 2/70 and 1/75, nothing drawn.
  - Debris on its own Paper feature seed: quartz 128/128, debris 0/5, nothing drawn.
- **OrePredictor**: shares the ore replay, so its biome filter also reads the vanilla biome (the client receives no biome feature lists), and a server that hides its seed hash (sends 0) no longer triggers the "seed does NOT match" warning.
- **New modules**: `StorageESP` (per-type toggle, colour and tracer for chests, trapped/ender chests, shulkers with optional dye colour, barrels, spawners, furnaces, hoppers/droppers/dispensers and enchanting tables; box/outline/fill modes, max distance, dimming of containers already opened this session), `Skeleton`, `PopChams`, `BedrockEsp`, `BlockNotifier`, `Waypoints` (+ `@waypoint`).
- **Finders**: `LuckyFinder`, `OrePredictor`, `PrimeChunkFinder`, `RtpBaseFinder`; `LightFinder` gains a strict below-Y=0 mode.
- **Shaders**: animated glow surface (pixel-width rim, climbing scan band, fresnel and sheen) for HoleESP, BlockESP and BlockHighlight; procedural glow ring for JumpCircles and soft core/halo/flare sprites for Trails and TargetESP ghosts. Each has an opt-out setting that keeps the old look.
- **ChunkFinder**: `SusChunkFinder`, `OGChunkFinder`, `PrimeChunkFinder` and `SpawnerFinder` are merged into one `ChunkFinder` module with a toggle and its own settings per finder (the old names stay as aliases, so `@toggle suschunkfinder` still works). `StorageESP`, `ChunkFinder` and `LightFinder` gain **Glow** (animated glow surface) and **Shader** (outline plus solid or gradient fill) render modes on the same glow-surface renderer.
- `Tracers` arrows gain distance colouring; item physics gains random heading and tumble speed.

### HUD
- New `KeyStrokes` element.
- Admins: vanish-probe settings (see Staff / Admins).

### GUI Polish
- **Theme-driven colours**: the ClickGui setting widgets, search, hint overlay, schematic menu, main menu, Alt/Addon/Proxy managers, map section, chat hover, preview screen, toasts, swap tooltip, debug overlay and tab-list header/footer take their colours from the active theme instead of hard-coded values, so custom themes recolour them. Semantic colours (error red, success green, staff/cooldown status, shadows) stay fixed.
- **One font family**: Onest across the ClickGui, setting widgets, schematic menu and manager screens (previously mixed Onest, Inter, Comfortaa and Montserrat).
- Animated hover on the modules-menu tune button; search outline strengthens while typing; long toast, placeholder and hint text is ellipsised or clipped instead of overflowing; toast exit fades are eased.

### Performance
- Visual modules: cached screen projection, tick-keyed entity/tracer caches and quad culling; JumpCircles and glow surfaces no longer allocate per circle/box.
- **World rendering**: wide-line meshes reuse one pooled builder per pipeline; BlockESP resolves the collision context once per frame and frustum-culls targets; Hitbox resolves ESP, colours and transform matrices once per frame instead of per entity; Tracers fetch the line batch once per loop; module world-render dispatch no longer allocates a `Runnable` per module per frame; Predictions skips the aim simulation when no aim feature is on; NameTags caches the line height per renderer/scale.
- **HUD / ClickGui**: theme sync in the settings skin runs only when the theme or surface changes (it ran ~30 colour mixes per metric lookup); script HUD panels build their props lazily; module display names are cached per language; Keybinds memoises bind formatting, Cooldowns caches item names per language, Potions resolves effect names once before sorting; Scoreboard measures values once per frame and only builds its debug dump when debug logging is on; chat line layout appends instead of O(n²) `addFirst` and uses static timestamp formatters; TargetHud reuses its effect/equipment layouts; toast metrics and island text widths are cached.
- **Render engine**: the full-frame world-colour copy used by glass/blur backdrops (≈8 MB per frame at 1080p, ≈33 MB at 4K) is skipped on frames where nothing has needed it for the last two frames; text width measurement is cached (keyed on text, scale, fonts generation, pixel density and NameProtect state) and the OpenType shaping cache holds 4096 runs instead of 384; the UI command buffer no longer records an unused command object per draw (per-kind counts are kept for stats); analytic-clip uniforms are reused per frame instead of rewritten per draw; profiler labels are no longer built every frame.

### Config
- **Persistence**: hot configs are written atomically (temp file + move) with a `.bak` of the last valid file; corrupt files are quarantined and recovered, writes are coalesced, unknown keys are preserved, and pending writes flush on shutdown and panic.
- `.cbcfg` profiles record the Combatant version that saved them; older profiles load unchanged.
- **Config sharing**: share codes with a preview panel and file dialogs; configs can be bound to servers (`ConfigServerBindings`); new colour-list setting type.

### Printer (optional Litematica)
- New `Printer` module builds the active Litematica placements through the shared runtime (placement, restock, optional navigation, orientation rules, block-model preview). Litematica is an optional dependency and is never loaded when absent.

### Fixes
- SchematicBuilder: fixed a tick overflow in the shortfall/notification timers; cells without support no longer use up plan attempts; reach is tested against real geometry.
- Crash investigation: in-game `ZipException: invalid LOC header` / `NoClassDefFoundError` crashes when enabling modules or clicking in the ClickGui were caused by replacing the mod jar (or recompiling dev classes) while the game was running, not by module code. A smoke run enabling all 199 toggleable modules one by one produced no failures.
- **More Culling 1.9.0 crash**: More Culling 1.9.0 added a `Direction` parameter to `CullingUtils.shouldCullPaintingBack`, so Combatant's compat mixin failed to apply, the resource reload aborted and the game crashed natively on startup. The compat hooks now take no target arguments (they bind to any signature) and are optional (`require = 0`), so a future change in More Culling skips the hook instead of crashing.
- **Armor HUD in development runs**: its soft shadow passes `blur` as a radius (a number), which the backdrop-blur check read as a boolean flag; with UI validation on (dev environment) that threw and quarantined the panel whenever it rendered (e.g. with chat open). Only a boolean `blur` now requests the backdrop blur. Release builds were unaffected (validation off).

### Removed
- **Anticheat presets**: the per-module preset system (`anticheat_mode`, `AntiCheatPreset`, `AntiCheatPresetDefaults`) is gone. Every module keeps its former default behaviour and uses its settings exactly as set.
- `StaticHitboxes` module → *Static hitboxes* option on `Hitbox` (aliases kept).
- `AntiVanish` module → vanish probe in the Admins/staff system.
- `SpeedMine` and `SpeedMineGameModeMixin` (`NoDelay` covers fast breaking).
- `TotemHit` module (swapped a hotbar sword in when attacking with a totem in hand).
- `PlacementConstraints.constructFailResult`: NORMAL placement now always clicks on the planned face once the server's rotation meets the support, and rotation-free placement always did, so the flag had no effect left.

## [Unreleased / Latest Krypton Updates] - 2026-10-09

### Fixes & Additions
- **Totem Pop Counter**: Deduplicated totem pop triggers to count once per totem pop instead of once per processing thread.
- **Speed**: Added a `BHOP` (Bunny Hop) mode that drives acceleration through input packets (`jump` and `sprint` bits) rather than directly injecting velocities. Covered by `BhopInputTest`.
- **SafeWalk**: Fixed edge-walking creep where holding a direction key slowly shifted the player off the block edge.
- **Blink**: Movement-only mode now holds movement packets reliably.

### Scaffold & Placement Engine Overhaul
- **Raycast Geometry**: Overhauled placement targeting so clicks satisfy GrimAC raycast geometry (aims at external block face positions from outside the block collision box).
- **Rotation Smoothing & Yaw Clamping**:
  - Constrained sent yaw angles to prevent 180° flick flags (`GrimAC AimModulo360`).
  - Aligned silent movement correction yaw quantization so diagonal movement remains natural without desyncing look angles.
  - Fast-snapped rotation in one step when smoothing latency would arrive after the placement window.
- **Tick-Based Placement Cadence**: Swapped placement timing from wall-clock nanoseconds to integer client ticks (defaulting to 20 blocks per second = 1 block per tick).
- **Trajectory & Jump Prediction**:
  - Added single-tick jump impulse prediction (`0.42` impulse + sprint boost) for accurate placement while jumping or telly-bridging.
  - Follows the player's real path on diagonal bridging rather than snapping to an ideal line when movement stabilization is disabled.
  - Fall-off prediction fixed to correctly return the supporting block face.
- **Input Handling**: Reads player movement direction directly from raw keyboard input rather than corrected movement inputs to prevent double-correction drift.

### Player & Automation Modules
- **Pre-Tick Placement Dispatch**:
  - `BlockPlacer`: Relocated block placement dispatch to before the movement packet (`start-of-tick` hook).
  - `InventorySwap`: Hotbar lease restoration now sends at the start of the tick, eliminating `Post` interaction flags.
  - `AirPlace`, `AutoMount`, and `AutoBreed`: Moved to start-of-tick hooks.
- **AutoBreed**: Now feeds all nearby breedable animals in one pass instead of stopping at the first.
- **AutoMine**: New module for automated block mining.
- **ChestStealer**: Fixed container inventory synchronization.
- **NoDelay**: Enforced minimum delays on block breaking, placing, right-clicking, and jumping under Grim.
- **AntiAFK, AutoWalk, AntiVoid, HoleSnap, AutoClicker**: Added utility modules for automation, safety, and combat assistance.
- **Surround & SelfTrap**: Refactored around a shared placement planner with options for `KTLeave` and `ProjectilePuncher`.

### Visuals & Finders
- **DonutSMP Finders**:
  - Ported `NetheriteFinder`, `ActivityFinder`, `ChunkFinder`, `SpawnerFinder`, `SusChunkFinder`, `OGChunkFinder`, `LightFinder`, and `ExplosionMarker`.
  - Added server seed mismatch detection in `NetheriteFinder` with neighbor chunk synchronization.
- **Camera Adjustments**: Added mouse-wheel adjustment to `Zoom` and `Freecam`.
- **ESP & Cosmetics**:
  - Improved `DropESP`, `BlockESP`, `Predictions`, and `ViewModel`.
  - Fixed `FakePlayer` absorption health setting having no effect.

### Rendering Engine & Performance
- **Sodium Compatibility**: Added support for Sodium 0.9.2 while retaining extended terrain stride rendering.
- **Iris Compatibility**: Prevented crash in `GlCommandEncoderMixin` when Iris is absent.
- **2D/3D Render Engine**:
  - Split JIT compilation size of `UiShapeRenderer` to improve startup compilation performance.
  - Added memory caches for script color parsing (`UiScriptColor`) and mesh arena mappings (`Blaze3dMeshArena`).
  - Eliminated per-draw allocations in visual baseline profiles.
  - Optimized health bar script reruns and dynamic island updates.

### Diagnostics & Developer Tooling
- **Command `@i18naudit`**: Developer command to dump all configuration setting key chains and identify missing English/Russian localization strings.
- **Command `@rotdbg`**: Developer command for real-time packet and rotation logging to `latest.log` for anticheat tuning.
- **Build Cleanliness**: Excluded `.omc` agent state, test server files, and stray root metadata files from release jar packaging via `.gitignore`.
