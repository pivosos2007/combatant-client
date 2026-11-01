/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playermodel;

import combatant.client.features.playeranimator.PlayerRigBone;
import combatant.client.render.engine.asset.model.ModelRuntimeAsset;
import combatant.client.render.engine.asset.model.io.ModelImporters;
import combatant.client.render.engine.asset.model.io.ModelResourceResolver;
import combatant.client.render.engine.asset.model.material.ModelMaterialBridge;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlayerModelAssetsTest {
    private static final ModelResourceResolver SOURCE = id -> {
        String external = System.getProperty("combatant.playerModelAssets");
        if (external != null) return java.nio.file.Files.newInputStream(java.nio.file.Path.of(external,
                "assets", id.getNamespace(), id.getPath()));
        var input = PlayerModelAssetsTest.class.getResourceAsStream("/assets/" + id.getNamespace() + "/" + id.getPath());
        if (input == null) throw new IOException("Missing bundled resource: " + id);
        return input;
    };

    @Test void deferredPosesStayIndependentAcrossPlayersAndPasses() {
        var source = new combatant.client.features.playeranimator.PlayerRigInstance();
        source.setRotation(PlayerRigBone.HEAD.ordinal(), 0.3f, 0.6f, 0f).solve();
        var first = PlayerModels.freezePose(source);
        var before = first.rig().skinMatrix(PlayerRigBone.HEAD.ordinal(), new org.joml.Matrix4f());
        source.setRotation(PlayerRigBone.HEAD.ordinal(), -0.4f, -0.8f, 0f).solve();
        var second = PlayerModels.freezePose(source);
        assertEquals(before, first.rig().skinMatrix(PlayerRigBone.HEAD.ordinal(), new org.joml.Matrix4f()));
        assertNotEquals(before, second.rig().skinMatrix(PlayerRigBone.HEAD.ordinal(), new org.joml.Matrix4f()));
        assertNotSame(first, second);
    }

    @Test void rejectsMalformedCatalogAndMissingResources() {
        assertThrows(IOException.class, () -> ModelCatalog.load(id -> new ByteArrayInputStream("[]".getBytes()), ModelCatalog.id("test:combatant/player_models/test.json")));
        assertThrows(IOException.class, () -> ModelCatalog.read(SOURCE, "test:models/player/missing.obj"));
        String invalid = "[{\"id\":\"test\",\"model\":\"../outside.obj\",\"texture\":\"x\",\"icon\":\"x\"}]";
        assertThrows(IOException.class, () -> ModelCatalog.load(id -> new ByteArrayInputStream(invalid.getBytes()), ModelCatalog.id("test:combatant/player_models/test.json")));
        assertThrows(IOException.class, () -> PlayerModels.validatePng(new byte[32]));
    }

    @Test void flexibleRegionPinsEndpointsWithoutAffectingOtherGroups() {
        var blend = new PlayerModelProfile.Blend("bodyModel", "CHEST", "HEAD",
                new float[]{-5, -5, -5}, new float[]{5, 5, 5},
                new float[]{0, 5, 0}, new float[]{0, -5, 0});
        new PlayerModelProfile(Map.of(), List.of(blend));
        assertEquals(0f, blend.weight(new Vector3f(0, 5, 0)), 1e-6);
        assertEquals(1f, blend.weight(new Vector3f(0, -5, 0)), 1e-6);
        assertEquals(0.5f, blend.weight(new Vector3f()), 1e-6);
        assertFalse(blend.contains("headModel", new Vector3f()));
        assertFalse(blend.contains("bodyModel", new Vector3f(8, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new PlayerModelProfile(Map.of("bodyModel", "UNKNOWN"), List.of()));
    }

    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "combatant.playerModelAssets", matches = ".+")
    @Test void productionCollectionImportsEveryCompleteModelAndPreservesAllFaces() throws Exception {
        var entries = ModelCatalog.load(SOURCE, ModelCatalog.id("sc_armor_addon:combatant/player_models/armor.json"));
        assertFalse(entries.isEmpty());
        assertEquals(entries.size(), entries.stream().map(ModelCatalog.Entry::id).distinct().count());
        assertTrue(entries.stream().noneMatch(e -> e.id().startsWith("aw_")));
        var base = entries.stream().filter(e -> e.id().equals("sc_armor_addon:albatros_shturmovik")).findFirst().orElseThrow();
        var variant = entries.stream().filter(e -> e.id().equals("sc_armor_addon:albatros_shturmovik_runa")).findFirst().orElseThrow();
        assertEquals(base.model(), variant.model());
        assertNotEquals(base.texture(), variant.texture());
        var compiled = new HashSet<String>();
        for (var entry : entries) {
            PlayerModels.validatePng(ModelCatalog.read(SOURCE, entry.texture()));
            byte[] icon = ModelCatalog.read(SOURCE, entry.icon());
            PlayerModels.validatePng(icon);
            assertTrue(ByteBuffer.wrap(icon, 16, 4).getInt() <= 256, entry.id());
            assertTrue(ByteBuffer.wrap(icon, 20, 4).getInt() <= 256, entry.id());
            if (!compiled.add(entry.model())) continue;
            var asset = ModelImporters.load(ModelCatalog.id(entry.model()), id -> SOURCE.open(
                    id.equals(ModelCatalog.id(entry.atlas())) ? ModelCatalog.id(entry.texture()) : id));
            var runtime = new ModelRuntimeAsset(asset, ModelMaterialBridge.build(asset));
            var surfaces = PlayerModelCompiler.compile(runtime, PlayerModelProfile.load(SOURCE, entry.profile()));
            int expectedIndices = runtime.defaultSceneLayout().primitives().stream()
                    .mapToInt(p -> runtime.compilePrimitive(p.meshIndex(), p.primitiveIndex()).indices().length).sum();
            assertEquals(expectedIndices, surfaces.stream().mapToInt(s -> s.mesh().indexCount()).sum(), entry.id());
            var bones = new HashSet<Integer>();
            for (var surface : surfaces) for (var part : surface.mesh().parts()) {
                for (int i = 0; i < part.vertexCount(); i++) {
                    var vertex = part.vertex(i);
                    bones.add(vertex.bone0());
                    if (vertex.weight1() > 0) bones.add(vertex.bone1());
                    assertEquals(1f, vertex.weight0() + vertex.weight1(), 1e-6, entry.id());
                    assertTrue(Float.isFinite(vertex.x()) && Float.isFinite(vertex.y()) && Float.isFinite(vertex.z()));
                }
            }
            assertTrue(bones.contains(PlayerRigBone.HEAD.ordinal()), entry.id());
            assertTrue(bones.contains(PlayerRigBone.LEFT_FOREARM.ordinal()), entry.id());
            assertTrue(bones.contains(PlayerRigBone.RIGHT_SHIN.ordinal()), entry.id());
        }
        System.out.println("Production models validated: " + compiled.size() + " geometries, " + entries.size() + " appearances/icons");
    }
}
