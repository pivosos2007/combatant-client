/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.io;

import combatant.client.render.engine.asset.model.data.ModelAsset;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImportedModelImporterTest {
    @Test void selectsCompoundGeckoExtensionWithoutClaimingAllJson() {
        assertTrue(ModelImporters.supports(id("example:models/cube.geo.json")));
        assertTrue(ModelImporters.supports(id("example:models/cube.gltf")));
        assertTrue(ModelImporters.supports(id("example:models/cube.glb")));
        assertTrue(ModelImporters.supports(id("example:models/cube.obj")));
        assertFalse(ModelImporters.supports(id("example:models/cube.animation.json")));
        assertFalse(ModelImporters.supports(id("example:models/metadata.json")));
    }

    @Test void restrictsExternalPayloadsToNamespaceRoot() throws Exception {
        Identifier parent = id("example:models/dev/sample.obj");
        assertEquals(id("example:models/dev/surface.png"), ModelResourcePaths.sibling(parent, "surface.png"));
        assertEquals(id("example:models/surface.png"), ModelResourcePaths.sibling(parent, "../surface.png"));
        assertThrows(IOException.class, () -> ModelResourcePaths.sibling(parent, "../../../escape.png"));
        assertThrows(IOException.class, () -> ModelResourcePaths.sibling(parent, "https://example.com/texture.png"));
        assertThrows(IOException.class, () -> ModelResourcePaths.sibling(parent, "/absolute.png"));
        assertThrows(IOException.class, () -> ModelResourcePaths.sibling(parent, "surface.png?version=1"));
    }

    @Test void objMtlUsesSameModelContractAsGltf() throws Exception {
        String obj = """
                mtllib cube.mtl
                o cube
                v 0 0 0
                v 1 0 0
                v 1 1 0
                v 0 1 0
                vt 0 0
                vt 1 0
                vt 1 1
                vt 0 1
                usemtl surface
                f -4/1 -3/2 -2/3 -1/4
                """;
        String mtl = """
                newmtl surface
                Kd 0.8 0.5 0.2
                d 0.75
                Pr 0.6
                Pm 0.3
                """;
        Map<Identifier, byte[]> sources = Map.of(
                id("example:models/cube.obj"), obj.getBytes(StandardCharsets.UTF_8),
                id("example:models/cube.mtl"), mtl.getBytes(StandardCharsets.UTF_8));
        ModelResourceResolver resolver = resource -> {
            byte[] bytes = sources.get(resource);
            if (bytes == null) throw new FileNotFoundException(resource.toString());
            return new ByteArrayInputStream(bytes);
        };
        ModelAsset model = ModelImporters.load(id("example:models/cube.obj"), resolver);
        assertEquals(1, model.scenes().size());
        assertEquals(1, model.meshes().size());
        assertEquals(6, model.meshes().getFirst().primitives().getFirst().indexCount());
        assertEquals(4, model.meshes().getFirst().primitives().getFirst().vertexCount());
        assertEquals(0.75f, model.materials().get(1).baseColor()[3], 0.001f);
    }

    @Test void mtlTexturesResolveRelativeToTheirOwnLibraryAndNotObj() throws Exception {
        String obj = """
                mtllib materials/surfaces.mtl
                v 0 0 0
                v 1 0 0
                v 0 1 0
                usemtl blue
                f 1 2 3
                """;
        String mtl = """
                newmtl blue
                map_Kd blue.png
                """;
        Map<Identifier, byte[]> sources = Map.of(
                id("example:models/geometry.obj"), obj.getBytes(StandardCharsets.UTF_8),
                id("example:models/materials/surfaces.mtl"), mtl.getBytes(StandardCharsets.UTF_8),
                id("example:models/materials/blue.png"), new byte[]{1});
        ModelAsset model = ModelImporters.load(id("example:models/geometry.obj"), location -> {
            byte[] bytes = sources.get(location);
            if (bytes == null) throw new FileNotFoundException(location.toString());
            return new ByteArrayInputStream(bytes);
        });
        assertEquals(1, model.textures().size());
        assertEquals(0, model.materials().get(1).baseColorTexture());
    }

    @Test void objNegativeIndicesAndSmoothingOffDoNotWeldAcrossFaces() throws Exception {
        String geometry = """
                v 0 0 0
                v 1 0 0
                v 0 1 0
                f -3 -2 -1
                v 0 0 1
                f -3 -2 -1
                """;
        ModelAsset model = new combatant.client.render.engine.asset.obj.io.ObjAssetImporter().load(
                id("example:models/negative.obj"), path ->
                        new ByteArrayInputStream(geometry.getBytes(StandardCharsets.UTF_8)));
        var primitive = model.meshes().getFirst().primitives().getFirst();
        assertEquals(6, primitive.indexCount());
        assertEquals(6, primitive.vertexCount());
        float[] positions = primitive.positions();
        assertEquals(1.0f, positions[5 * 3 + 2]);
    }

    @Test void readsBoundedInput() throws Exception {
        assertArrayEquals(new byte[]{1, 2, 3},
                ModelResourceReader.read(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "fixture"));
        assertThrows(IOException.class, () ->
                ModelResourceReader.read(new ByteArrayInputStream(new byte[]{1, 2, 3}), 2, "fixture"));
    }

    private static Identifier id(String value) {
        return java.util.Objects.requireNonNull(Identifier.tryParse(value));
    }
}
