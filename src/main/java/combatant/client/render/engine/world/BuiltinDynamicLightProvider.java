/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import combatant.client.render.engine.light.BlockLightEmitterRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.function.Consumer;

/** Built-in exact producers for moving local-light sources known by the game scene. */
final class BuiltinDynamicLightProvider implements DynamicLightProvider {
    static final BuiltinDynamicLightProvider INSTANCE = new BuiltinDynamicLightProvider();

    private static final double ENTITY_QUERY_RADIUS = 64.0;
    private static final int MAX_ENTITY_QUERY_CANDIDATES = 384;
    private static final float BLOCKLIGHT_RADIANCE_SCALE = 6.0f;

    private BuiltinDynamicLightProvider() {
    }

    @Override
    public void collect(Context context, Consumer<LightDescriptor> output) {
        if (context == null || output == null || context.level() == null) return;
        collectHeldLight(context, output);

        Vec3 camera = context.cameraPosition();
        AABB bounds = new AABB(
                camera.x - ENTITY_QUERY_RADIUS, camera.y - ENTITY_QUERY_RADIUS, camera.z - ENTITY_QUERY_RADIUS,
                camera.x + ENTITY_QUERY_RADIUS, camera.y + ENTITY_QUERY_RADIUS, camera.z + ENTITY_QUERY_RADIUS
        );
        ArrayList<Entity> entities = new ArrayList<>(128);
        context.level().getEntities(
                EntityTypeTest.forClass(Entity.class),
                bounds,
                BuiltinDynamicLightProvider::isPotentialEmitter,
                entities,
                MAX_ENTITY_QUERY_CANDIDATES
        );
        for (Entity entity : entities) collectEntityLight(entity, context.tickProgress(), output);
    }

    private static void collectHeldLight(Context context, Consumer<LightDescriptor> output) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player == null || player.level() != context.level() || player.isRemoved()) return;

        BlockLightEmitterRegistry registry = BlockLightEmitterRegistry.global();
        BlockLightEmitterRegistry.ResolvedEmitter main = registry.resolve(player.getMainHandItem());
        BlockLightEmitterRegistry.ResolvedEmitter off = registry.resolve(player.getOffhandItem());
        int level = Math.max(main.vanillaLevel(), off.vanillaLevel());
        if (level <= 0) return;

        float red = Math.max(main.red(), off.red()) * BLOCKLIGHT_RADIANCE_SCALE;
        float green = Math.max(main.green(), off.green()) * BLOCKLIGHT_RADIANCE_SCALE;
        float blue = Math.max(main.blue(), off.blue()) * BLOCKLIGHT_RADIANCE_SCALE;
        float radius = radiusForVanillaLevel(level);
        Vec3 position = player.getEyePosition(context.tickProgress()).add(0.0, -0.35, 0.0);
        output.accept(LightDescriptor.point(
                stableId(player.getId(), 1),
                position.x, position.y, position.z,
                red, green, blue, radius
        ).withShadowCasting(true).withShadowCasterExclusion(player.getId()));
    }

    private static boolean isPotentialEmitter(Entity entity) {
        if (entity == null || entity.isRemoved()) return false;
        if (entity instanceof ItemEntity itemEntity) {
            return BlockLightEmitterRegistry.global().resolve(itemEntity.getItem()).vanillaLevel() > 0;
        }
        return entity.isOnFire()
                || entity instanceof SmallFireball
                || entity instanceof LargeFireball
                || entity instanceof DragonFireball
                || entity instanceof PrimedTnt
                || entity instanceof EndCrystal
                || entity instanceof LightningBolt
                || entity instanceof FireworkRocketEntity;
    }

    private static void collectEntityLight(Entity entity, float tickProgress, Consumer<LightDescriptor> output) {
        Vec3 position = entity.getEyePosition(tickProgress);
        int entityId = entity.getId();

        if (entity instanceof SmallFireball || entity instanceof LargeFireball) {
            output.accept(pointEntity(entity, stableId(entityId, 2), position,
                    8.0f, 3.8f, 1.25f, 11.0f, true));
            return;
        }
        if (entity instanceof DragonFireball) {
            output.accept(pointEntity(entity, stableId(entityId, 3), position,
                    5.0f, 1.4f, 7.5f, 12.0f, true));
            return;
        }
        if (entity instanceof LightningBolt) {
            output.accept(LightDescriptor.sphere(stableId(entityId, 4),
                    position.x, position.y, position.z,
                    36.0f, 42.0f, 56.0f, 30.0f, 0.5f));
            return;
        }
        if (entity instanceof EndCrystal) {
            output.accept(pointEntity(entity, stableId(entityId, 5), position,
                    8.0f, 3.2f, 9.0f, 13.0f, true));
            return;
        }
        if (entity instanceof FireworkRocketEntity) {
            output.accept(LightDescriptor.sphere(stableId(entityId, 6),
                    position.x, position.y, position.z,
                    10.0f, 7.0f, 4.0f, 10.0f, 0.25f));
            return;
        }
        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            BlockLightEmitterRegistry.ResolvedEmitter emitter = BlockLightEmitterRegistry.global().resolve(stack);
            if (emitter.vanillaLevel() > 0) {
                output.accept(LightDescriptor.point(stableId(entityId, 7),
                        position.x, position.y, position.z,
                        emitter.red() * 4.0f,
                        emitter.green() * 4.0f,
                        emitter.blue() * 4.0f,
                        Math.min(8.0f, radiusForVanillaLevel(emitter.vanillaLevel()))));
            }
            return;
        }
        if (entity instanceof PrimedTnt tnt) {
            int fuse = Math.max(0, tnt.getFuse());
            float pulse = 0.45f + 0.55f * (float) Math.pow(0.5 + 0.5 * Math.cos(fuse * 0.9), 2.0);
            output.accept(pointEntity(entity, stableId(entityId, 8), position,
                    5.0f * pulse, 1.4f * pulse, 0.35f * pulse, 7.0f, true));
            return;
        }
        if (entity.isOnFire()) {
            output.accept(pointEntity(entity, stableId(entityId, 9), position,
                    5.5f, 2.0f, 0.55f, 7.5f, true));
        }
    }

    private static LightDescriptor pointEntity(Entity entity,
                                               long stableId,
                                               Vec3 position,
                                               float red,
                                               float green,
                                               float blue,
                                               float radius,
                                               boolean shadows) {
        LightDescriptor light = LightDescriptor.point(stableId,
                position.x, position.y, position.z, red, green, blue, radius);
        if (shadows) light = light.withShadowCasting(true).withShadowCasterExclusion(entity.getId());
        return light;
    }

    private static float radiusForVanillaLevel(int level) {
        return Math.max(4.0f, Math.min(14.0f, 2.0f + Math.max(0, Math.min(15, level)) * 0.8f));
    }

    private static long stableId(int entityId, int channel) {
        return 0x43424C0000000000L ^ ((long) entityId << 8) ^ (channel & 0xFFL);
    }
}
