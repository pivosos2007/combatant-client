# Credits and provenance

Combatant Client is an independently maintained project by **pivosos2007**. It is not affiliated with, endorsed by, or an official continuation of any project named below.

This document records both code lineage and major architectural references. File-level notices are present in the identified derived/adapted files. Detailed license information is in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## LiquidBounce / CCBlueX

LiquidBounce was the principal upstream source or implementation reference for parts of:

- the rotation request/processing, smoothing, ray-tracing, point-selection, and movement-correction subsystem;
- Scaffold and its placement, target-selection, movement-planning, prediction, and block-selection helpers;
- AutoDodge and related movement-input/player-simulation helpers;
- projectile trajectory and angle calculation;
- selected combat/network behavior, including Blink, FakeLag, Backtrack, Velocity, click scheduling, attack helpers, timers, and protocol/ViaFabricPlus compatibility.

The Combatant versions have been ported to a different Minecraft version and mapping set, integrated with Combatant's module/config/event architecture, and in many places substantially redesigned or extended.

Upstream: https://github.com/CCBlueX/LiquidBounce

## Meteor Client / Meteor Development

Meteor Client served as the initial implementation reference for the first generation of Combatant's rendering foundation. Identified lineage is concentrated in:

- the early text/font abstraction;
- mesh, texture, vertex-format, render-pipeline, and full-screen rendering helpers;
- the basic position/color, position/texture/color, and text shaders;
- selected movement code (`Flight`, `AirJump`, movement events and packet accessors).

Combatant's current renderer, UI batching, post-processing, frame graph, effects, Vulkan work, and most higher-level rendering systems were subsequently developed independently and differ substantially from Meteor's architecture.

Upstream: https://github.com/MeteorDevelopment/meteor-client

## GFBS-glTF / LytharaLab

Combatant's imported-model decoding foundation is adapted from **GFBS-glTF 1.5.1**, commit `8906d4083c23b5d5bbf5191a0555d149a5272900`, under the MIT License. The adapted code is limited to immutable glTF data contracts, validation, glTF/GLB decoding, OBJ/MTL importing, resource resolution, animation data needed by the loader, and selected backend-neutral CPU morph/skin geometry transforms. GFBS' Forge renderer, OpenGL GPU model path, networking, collision, plugin manager, and RenderType integration are not incorporated. Combatant ports the importer to Minecraft 26.2 `Identifier`, its own material contracts, and backend-neutral RHI ownership.

Upstream: https://github.com/LytharaLab/GFBS-glTF

The decoder uses **JglTF 2.0.4** by Marco Hutter (`de.javagl`) under the MIT License. JglTF is used as a parsing/model dependency; its viewer/renderers are not used. JglTF pulls Jackson for JSON decoding; Jackson is licensed under Apache-2.0.

The dev source set contains Khronos **BoxVertexColors.glb** as a tiny importer smoke fixture. The asset is CC0/public domain and is not part of release resources. The intended full PBR/Photon validation fixture is Khronos **BoomBox** (CC0), loaded externally/dev-only because it is substantially larger.

## Accurate Block Placement: Reborn

The accurate placement state machine in NoDelay is adapted from **Accurate Block
Placement: Reborn**, commit `f9f1acfbb58add990ca43b778a700a4eacda38ea`, by
**schwar, KadTheHunter, Flourick and Clayborn**, under the MIT License.
The port uses Combatant's event bus, block interaction pipeline and rotation manager.
Source comparison, adaptations and validation limits are recorded in `ABP_PARITY.md` and `ABP_VALIDATION.md`.

Upstream: https://github.com/KadTheHunter/AccurateBlockPlacement-Reborn

The original copyright and license are retained verbatim in
`THIRD_PARTY_LICENSES/AccurateBlockPlacement-Reborn-MIT.txt` and included in the jar.

## Other acknowledged upstream work

- **MediaPlayerInfo** by Redstonecrafter0: modified Java/native port under AGPL-3.0-only in `java/combatant/client/util/media/**`.
- **In-Game Account Switcher**: Microsoft authentication backend flow under LGPL-3.0-or-later.
- **InvMove** by PieKing1215: inventory movement behavior/screen classification under LGPL-3.0.
- **ExploitPreventer** by Niklas S.: selected security filters and mixins under the MIT License.
- **Sodium / Iris**: explicitly noted implementation concepts in the terrain vertex path where applicable.
- **Hold My Items** by sapling: first-person hand/item pose system and resource-pack animation API adapted for Minecraft 26.2. Port source: the supplied HMI 5.1.1 (Minecraft 1.21.11) jar; project page: https://modrinth.com/mod/hold-my-items. Combatant ports its Lua behavior to the existing Javet/JavaScript runtime in `java/combatant/client/features/hmi/**`, `java/combatant/client/mixins/hmi/**`, and `resources/assets/minecraft/holdmyitems/**`. The supplied jar declares/bundles CC0-1.0; that bundled license text is retained verbatim.
- **Noto / Noto CJK** by the Noto Project Authors and contributors: bundled OFL-1.1 Unicode fallback fonts for multilingual UI text. GPU glyph pages are populated only for characters encountered at runtime.
- **Iosevka** by Renzhi Li (Belleve Invis): full OFL-1.1 Iosevka 34.8.1 sources used for BetterChat's broad European/Greek/Cyrillic MSDF coverage. CJK remains delegated to the Noto fallback because upstream Iosevka does not include CJK glyphs.

## Authorship assessment for this snapshot

A conservative static inventory of this source snapshot found:

- approximately **177,197** non-blank/non-comment Java lines in total;
- **18,566** lines across **110** Java files with identified Meteor or LiquidBounce lineage (**10.48%** of the Java code-line inventory);
- **21,293** lines across **134** Java files when all currently identified source-derived third-party components are included (**12.02%**);
- therefore, about **87.98%** of Java code lines lie outside files currently identified as source-derived from third parties.

This is deliberately **not** presented as a copyright-ownership percentage. It counts entire files even when only part came from an upstream project or the file was heavily rewritten; it excludes conceptual influence that cannot be measured as source lineage; and it does not classify generated resources or binary dependencies. The defensible conclusion is that the large majority of the project is independently authored, while the named subsystems contain material upstream lineage and must retain their notices.
