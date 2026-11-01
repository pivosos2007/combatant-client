/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.io;

import combatant.client.render.engine.asset.blockbench.io.BlockbenchGeckoLibImporter;
import combatant.client.render.engine.asset.model.data.ModelAsset;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchGeckoLibImporterTest {
    @Test void importsBakedGeckoCubesAndChildBoneHierarchy() throws Exception {
        String geometry = """
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{"identifier":"geometry.test","texture_width":16,"texture_height":16},
                    "bones":[
                      {"name":"root","pivot":[0,8,0],"cubes":[{"origin":[0,0,0],"size":[8,8,8],"uv":[0,0]}]},
                      {"name":"child","parent":"root","pivot":[0,8,0],"rotation":[0,45,0],
                       "cubes":[{"origin":[0,8,0],"size":[4,4,4],"uv":[0,0]}]}
                    ]
                  }],
                  "combatant:texture":"example:textures/cube.png"
                }
                """;
        Map<Identifier, byte[]> fixtures = Map.of(
                id("example:models/test.geo.json"), geometry.getBytes(StandardCharsets.UTF_8),
                id("example:textures/cube.png"), new byte[]{1, 2, 3, 4});
        ModelAsset result = new BlockbenchGeckoLibImporter().load(id("example:models/test.geo.json"), path -> {
            byte[] content = fixtures.get(path);
            if (content == null) throw new FileNotFoundException(path.toString());
            return new ByteArrayInputStream(content);
        });
        assertEquals(1, result.scenes().size());
        assertEquals(2, result.nodes().size());
        assertArrayEquals(new int[]{1}, result.nodes().getFirst().children());
        assertEquals(0, result.nodes().get(1).parent());
        assertEquals(2, result.meshes().size());
        assertEquals(36, result.meshes().getFirst().primitives().getFirst().indexCount());
        assertEquals(1, result.textures().size());
        assertEquals(0, result.materials().getFirst().baseColorTexture());
        assertTrue(Math.abs(result.nodes().get(1).rotation()[1]) > 0.01f);
    }

    @Test void rejectsNonGeometryJson() {
        assertFalse(ModelImporters.supports(id("example:animations/test.animation.json")));
    }

    private static Identifier id(String source) {
        return java.util.Objects.requireNonNull(Identifier.tryParse(source));
    }
}
