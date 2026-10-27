# GLSL std140 / std430 bindings

The source of truth for layout names, field names, sizes, offsets, and strides is the actual GLSL under `src/main/resources/assets/combatant/shaders`. The normal Gradle build runs `generateUniformBindings` and **then `verifyUniformBindingContracts` before `compileJava`**. The generator scans every `.vert`, `.frag`, `.geom`, `.comp`, and `.glsl`, including local `#include <combatant:...>` dependencies. There is no hand-maintained list for runtime discovery.

The runtime resource is generated as `build/generated/resources/combatantUniforms/combatant/uniform-bindings-v3.tsv`. `ShaderUniformBindings` reads version 3 of this file and fails fast if the metadata is missing, malformed, or from an incompatible generator. Older `v1` and `v2` resources are removed from the generator output folder. The runtime does not parse GLSL or use reflection. **Rebuild after changing GLSL contracts; shader hot reload cannot regenerate Java-side bindings.**

A block name that appears in several shaders may use a shorter **trailing-field subset** with matching types and offsets; the generator retains the complete layout. Incompatible layouts of the same standard are rejected instead of arbitrarily selecting one. If a block has both std140 and std430 declarations, the std140 variant keeps the plain name and the std430 variant is available as `BlockName@std430`. A block with only one standard keeps its plain name.

Java-side writers use the **exact GLSL identifiers** (for example `uBlendParams`, not `u_BlendParams`). Build-time verification checks literal `ShaderUniformBindings.block()`, `Block.member()`, and `Writer.vec4()/mat4()/floats()/ints()` references against generated metadata, including chained calls and dotted struct paths. This protects the current static binding sites; dynamic string expressions and old manual `Std140Builder` writers still require separate review. The runtime writer also validates names, types, and array indices on access. `UiClipUniforms` uses metadata-derived array capacity/offsets instead of hard-coded packing.

Supported declarations: std140 uniform/std430 blocks, fixed arrays, trailing runtime arrays in std430 storage blocks, float/int/uint/bool scalars and vectors, square/rectangular float matrices, nested named GLSL structures, integer `#define`/`const int` arithmetic, and recursive local Mojang include expansion. Unsupported constructs (multi-dimensional arrays, runtime arrays inside structs or uniform blocks, std140 storage blocks, explicit offset/align, `row_major`, and unresolved imported constants) fail generation explicitly rather than producing incomplete metadata.

For an SSBO such as `struct Drop { vec4 center; vec4 motion; }; layout(std430) buffer RainVisible { Drop visible[]; };`:

```java
var block = ShaderUniformBindings.block("RainVisible");
int bytes = block.size(100); // 3200: prefix + element count * stride
var writer = block.writer(100).vec4("visible.center", 2, 1f, 2f, 3f, 4f);
writer.floats("visible.motion", new float[]{5f, 6f, 7f, 8f}, 2);
```

`size()` is the fixed size for ordinary blocks and the tail offset for runtime-sized blocks. `size(count)` and `writer(count)` require a runtime array, accept zero elements, and reject negative counts or byte-size overflow. `writer()` requires a fixed-size block. Typed generated bindings expose `size(count)` and `new Writer(count)` with the same packing and bounds checks. Their `SIZE` is the fixed prefix and the runtime member's `COUNT_*` is zero.

Metadata v3 retains the six-column member rows: path, GLSL type, offset, count, stride (after the `member` tag). Structs have their own row followed by dotted child paths; child offsets are relative to their enclosing struct. A count of zero with a positive stride identifies the trailing runtime array. Fixed one-element arrays retain their positive stride. Runtime storage layouts expose `size(count)` and `member(name).elementOffset(index, count)`; they cannot be embedded as struct array elements.

`floats(path, values, indices...)` writes float scalars/vectors or column-major matrices; `ints(...)` writes int/uint/bool scalars/vectors, preserving uint bits and normalizing booleans to 0/1. Pass one index per array along the path, from outermost to innermost; for `Drop visible[]` containing `vec2 uv[2]`, use `floats("visible.uv", new float[]{u, v}, dropIndex, uvIndex)`.

Run verification manually:

```sh
./gradlew verifyUniformCodegen
./gradlew verifyGeneratedUniformPacking
./gradlew verifyUniformBindingContracts
```

`verifyGeneratedUniformPacking` compiles generated Java writers with JDK-only stubs, verifies actual bytes/strides, validates the **runtime v3 metadata loader**, tests fixed and runtime SSBO arrays of structs/scalars/vectors/matrices, and checks invalid indices, sizes, conflicting shader declarations and misspelled Java fields. The explicit `generate <shader-root> <manifest> <java-output-root> [metadata-output-root]` command is available for verification-only typed classes; it is independent of runtime auto-discovery.
