# Third-party notices

This document covers source-derived, source-adapted, and bundled third-party material identified in this source snapshot. External build dependencies remain listed in `build.gradle` and retain their own licenses.

## File-level notice policy

- Java, JavaScript, TypeScript, GLSL, and Gradle source files carry a Combatant ownership and SPDX license header.
- Files with identified upstream lineage additionally carry the upstream copyright, project, and applicable SPDX license information.
- JSON manifests/data files do not accept comments and are covered by repository-level notices instead.
- SVG assets are intentionally left unmodified. Their attribution is recorded below rather than injected into each asset.
- Font binaries and generated font atlases are covered by repository-level notices rather than injected per binary file.
- Binary/native files retain the notices and licenses of their originating component.

## Combatant original code

Unless a file-level notice states otherwise:

- Copyright: 2026 pivosos2007
- License: GNU GPL version 3 only
- License text: `LICENSE` or `THIRD_PARTY_LICENSES/GPL-3.0-only.txt`

## LiquidBounce

- Upstream: https://github.com/CCBlueX/LiquidBounce
- Copyright: 2015-2025 CCBlueX
- License applied in this project: GNU GPL version 3 only
- License text: `THIRD_PARTY_LICENSES/GPL-3.0-only.txt`
- Scope: identified rotation/aiming, Scaffold, AutoDodge, simulation/input, projectile, combat/network/timer/protocol files. Exact files carry a LiquidBounce attribution header.

## Meteor Client

- Upstream: https://github.com/MeteorDevelopment/meteor-client
- Copyright: Meteor Development
- License: GNU GPL version 3
- License text: `THIRD_PARTY_LICENSES/GPL-3.0-only.txt`
- Scope: identified portions of the early font/text, mesh, texture, vertex-format, render-pipeline and base shader foundation, plus selected movement/event/accessor files. Exact files carry a Meteor Client attribution header.

## MediaPlayerInfo

- Upstream: https://github.com/Redstonecrafter0/MediaPlayerInfo
- Copyright: Redstonecrafter0 and contributors
- License: GNU Affero General Public License version 3 only
- License text: `THIRD_PARTY_LICENSES/AGPL-3.0-only.txt`
- Scope: `java/combatant/client/util/media/**` and its bundled native integration.

## In-Game Account Switcher

- Upstream: https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher
- Copyright: 2015-2022 The_Fireplace; 2021-2026 VidTu
- License: GNU LGPL version 3 or later
- License text: `THIRD_PARTY_LICENSES/LGPL-3.0.txt`
- Scope: the identified Microsoft device-code/authentication files.

## InvMove

- Upstream: https://github.com/PieKing1215/InvMove
- Copyright: PieKing1215 and contributors
- License: GNU LGPL version 3
- License text: `THIRD_PARTY_LICENSES/LGPL-3.0.txt`
- Scope: the identified inventory movement and screen classification files.

## ExploitPreventer

- Original author: Niklas S.
- Original copyright: 2025 Niklas S.
- Original license: MIT License
- License text: `THIRD_PARTY_LICENSES/MIT.txt`
- Scope: identified protection logic under `features/security` and `mixins/security`.

## Lucide and Feather icons

- Upstream: https://github.com/lucide-icons/lucide
- Copyright: Lucide Icons and Contributors; applicable Feather portions copyright Cole Bemis
- License: ISC License, with the bundled Feather-derived icon set retaining its MIT notice
- License text: `THIRD_PARTY_LICENSES/Lucide-ISC-and-Feather-MIT.txt`
- Scope: all bundled SVG interface icons under `resources/assets/combatant/svg/**`.

The SVG files themselves are not modified solely to inject headers. The repository-level license copy and this notice are retained with distributions.

## Onest

- Upstream: https://github.com/simpals/onest
- Copyright: The Onest Project Authors
- License: SIL Open Font License version 1.1
- License text: `THIRD_PARTY_LICENSES/Onest-OFL-1.1.txt`
- Scope: bundled Onest UI font files under `resources/assets/combatant/font/onest_*.ttf` and their generated MSDF atlases under `resources/assets/combatant/font/msdf/onest_*`.

## Sodium and Iris

Local source comments identify Sodium compact terrain vertex packing and Iris shader-extension concepts where applicable. Sodium, Iris, and other separately distributed runtime dependencies retain their own licenses and notices.
