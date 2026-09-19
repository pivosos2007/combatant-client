/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.light;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import combatant.client.util.logging.DebugLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit block-emission color metadata for the renderer-owned colored-light volume.
 * Unknown emitting blocks remain valid emitters and use a neutral white fallback.
 */
public final class BlockLightEmitterRegistry {
    private static final BlockLightEmitterRegistry GLOBAL = new BlockLightEmitterRegistry();
    private static final String ROOT = "block_lights";
    private static final Emitter NEUTRAL = new Emitter(1.0f, 1.0f, 1.0f, 1.0f);

    private volatile Map<Identifier, Emitter> emitters = Map.of();

    public static BlockLightEmitterRegistry global() {
        return GLOBAL;
    }

    public void reload(ResourceManager resources) {
        if (resources == null) {
            emitters = Map.of();
            return;
        }
        Map<Identifier, Emitter> next = new HashMap<>();
        Map<Identifier, Resource> found = resources.listResources(ROOT, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) throw new IllegalArgumentException("root must be an object");
                JsonObject object = root.getAsJsonObject();
                if (!object.has("emitters") || !object.get("emitters").isJsonArray()) {
                    throw new IllegalArgumentException("emitters must be an array");
                }
                for (JsonElement element : object.getAsJsonArray("emitters")) {
                    JsonObject emitter = element.getAsJsonObject();
                    Identifier block = Identifier.tryParse(emitter.get("block").getAsString());
                    if (block == null) throw new IllegalArgumentException("invalid block identifier");
                    var color = emitter.getAsJsonArray("color");
                    if (color.size() != 3) throw new IllegalArgumentException("color must contain 3 components");
                    float intensity = emitter.has("intensity") ? emitter.get("intensity").getAsFloat() : 1.0f;
                    next.put(block, new Emitter(
                            color.get(0).getAsFloat(), color.get(1).getAsFloat(), color.get(2).getAsFloat(), intensity
                    ));
                }
            } catch (Throwable error) {
                DebugLog.warnOnce(
                        "combatant.block-light.registry.invalid." + entry.getKey(),
                        "[BlockLight] invalid emitter descriptor %s: %s: %s",
                        entry.getKey(), error.getClass().getSimpleName(), error.getMessage()
                );
            }
        }
        emitters = Map.copyOf(next);
        DebugLog.renderThread("[BlockLight] emitter registry reload: explicit=%d", next.size());
    }

    public ResolvedEmitter resolve(BlockState state) {
        if (state == null) return ResolvedEmitter.NONE;
        int level = Math.max(0, Math.min(15, state.getLightEmission()));
        if (level <= 0) return ResolvedEmitter.NONE;
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Emitter emitter = emitters.getOrDefault(id, NEUTRAL);
        float levelScale = level / 15.0f;
        return new ResolvedEmitter(
                emitter.red * emitter.intensity * levelScale,
                emitter.green * emitter.intensity * levelScale,
                emitter.blue * emitter.intensity * levelScale,
                level
        );
    }

    /**
     * Reuses the exact block-emission metadata for held/dropped block items. This does not create
     * an analytic light by itself; dynamic-light providers can opt into that separate contract
     * without duplicating emitter color tables or inferring source color from rendered pixels.
     */
    public ResolvedEmitter resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return ResolvedEmitter.NONE;
        }
        return resolve(blockItem.getBlock().defaultBlockState());
    }

    public record ResolvedEmitter(float red, float green, float blue, int vanillaLevel) {
        public static final ResolvedEmitter NONE = new ResolvedEmitter(0.0f, 0.0f, 0.0f, 0);
    }

    private record Emitter(float red, float green, float blue, float intensity) {
        private Emitter {
            red = finiteNonNegative(red);
            green = finiteNonNegative(green);
            blue = finiteNonNegative(blue);
            intensity = finiteNonNegative(intensity);
        }

        private static float finiteNonNegative(float value) {
            return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
        }
    }
}
