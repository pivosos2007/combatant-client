# GLSL std140 / std430 bindings

The source of truth for layout names, field names, sizes, offsets, and strides is the actual GLSL under `src/main/resources/assets/combatant/shaders`. The normal Gradle build runs `generateUniformBindings` and **then `verifyUniformBindingContracts` before `compileJava`**. The generator scans every `.vert`, `.frag`, `.geom`, `.comp`, and `.glsl`, including local `#moj_import <combatant:...>` dependencies. There is no hand-maintained list for runtime discovery.

The runtime resource is generated as `build/generated/resources/combatantUniforms/combatant/uniform-bindings-v2.tsv`. `ShaderUniformBindings` reads version 2 of this file and fails fast if the metadata is missing, malformed, or from an incompatible generator. Older `v1` resources are removed from the generator output folder. The runtime does not parse GLSL or use reflection. **Rebuild after changing GLSL contracts; shader hot reload cannot regenerate Java-side bindings.**

A block name that appears in several shaders may use a shorter **trailing-field subset** with matching types and offsets; the generator retains the complete layout. Incompatible layouts of the same standard are rejected instead of arbitrarily selecting one. If a block has both std140 and std430 declarations, the std140 variant keeps the plain name and the std430 variant is available as `BlockName@std430`. A block with only one standard keeps its plain name.

Java-side writers use the **exact GLSL identifiers** (for example `uBlendParams`, not `u_BlendParams`). Build-time verification checks literal `ShaderUniformBindings.block()`, `Block.member()`, and `Writer.vec4()/mat4()` references against generated metadata, including chained calls. This protects the current static binding sites; dynamic string expressions and old manual `Std140Builder` writers still require separate review. The runtime writer also validates names, types, and array indices on access. `UiClipUniforms` now uses metadata-derived array capacity/offsets instead of hard-coded packing.

Supported declarations: std140/std430 blocks, fixed arrays, float/int/uint/bool scalars and vectors, square/rectangular float matrices, nested named GLSL structures in *generated verification Java*, integer `#define`/`const int` arithmetic, and recursive local Mojang include expansion. Unsupported constructs (runtime arrays, multi-dimensional arrays, explicit offset/align, `row_major`, unresolved imported constants, and unsupported struct metadata in the runtime format) fail generation explicitly rather than producing incomplete metadata.

Run verification manually:

```sh
./gradlew verifyUniformCodegen
./gradlew verifyGeneratedUniformPacking
./gradlew verifyUniformBindingContracts
```

`verifyGeneratedUniformPacking` compiles generated Java writers with JDK-only stubs, verifies actual bytes/strides, validates the **runtime v2 metadata loader**, tests `std430` field ordering, and asserts that a misspelled Java field is rejected during static validation. The explicit `generate <shader-root> <manifest> <java-output-root> [metadata-output-root]` command is available for verification-only typed classes; it is independent of runtime auto-discovery.
