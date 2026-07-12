/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.util.combat.VulcanReachController;

import java.util.List;
import java.util.Optional;

//todo Description
@ModuleInfo(
        id = "reach",
        displayName = "Reach",
        category = ModuleCategory.COMBAT
)
public class Reach extends Module {

    public final EnumValue<KillAura.RaycastMode> raycast =
            enumCommon(
                    "reachContext",
                    "raycast",
                    CommonSettingSchemas.COMBAT_RAYCAST,
                    KillAura.RaycastMode.STRICT,
                    KillAura.RaycastMode.values()
            );
    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode =
            enumCommon(
                    "reachMode",
                    "mode",
                    CommonSettingSchemas.ANTICHEAT_MODE,
                    Mode.NORMAL,
                    Mode.values()
            );

    private final NumberValue<Double> distance =
            numCommon(
                    "reachDistance",
                    "range",
                    CommonSettingSchemas.COMBAT_RANGE,
                    4.5,
                    3.0,
                    6.0
            );
    private final NumberValue<Double> wallDistance =
            visibleWhen(numCommon(
                    "reachWallDistance",
                    "wall_range",
                    CommonSettingSchemas.COMBAT_WALL_RANGE,
                    3.0,
                    0.0,
                    6.0
            ), () -> raycast.get() == KillAura.RaycastMode.THROUGH_WALLS);

    private static double boxDistanceSq(AABB box, Vec3 p) {
        double dx = 0.0;
        if (p.x < box.minX) dx = box.minX - p.x;
        else if (p.x > box.maxX) dx = p.x - box.maxX;

        double dy = 0.0;
        if (p.y < box.minY) dy = box.minY - p.y;
        else if (p.y > box.maxY) dy = p.y - box.maxY;

        double dz = 0.0;
        if (p.z < box.minZ) dz = box.minZ - p.z;
        else if (p.z > box.maxZ) dz = p.z - box.maxZ;

        return dx * dx + dy * dy + dz * dz;
    }

    public EntityHitResult raycastEntities(LocalPlayer player) {
        if (!isEnabled()) return null;
        if (player == null || mc.level == null) return null;

        double visibleRange = distance.get();
        double throughWallsRange = raycast.get() == KillAura.RaycastMode.THROUGH_WALLS
                ? wallDistance.get()
                : 0.0;
        double searchRange = Math.max(visibleRange, throughWallsRange);

        Vec3 start = player.getEyePosition(1.0F);
        Vec3 direction = player.getViewVector(1.0F).normalize();
        Vec3 end = start.add(direction.scale(searchRange));

        double blockLimitSq = resolveBlockLimitSq(player, start, end, visibleRange, throughWallsRange);

        AABB search = new AABB(start, end).inflate(1.0);

        List<Entity> candidates = mc.level.getEntities(
                player,
                search,
                e -> e instanceof LivingEntity && e.isAlive()
        );

        return findBestHit(player, start, end, candidates, blockLimitSq, searchRange);
    }

    public EntityHitResult raycastEntities(LocalPlayer player, Vec3 start, Vec3 direction) {
        if (!isEnabled()) return null;
        if (player == null || mc.level == null) return null;

        double visibleRange = distance.get();
        double throughWallsRange = raycast.get() == KillAura.RaycastMode.THROUGH_WALLS
                ? wallDistance.get()
                : 0.0;
        double searchRange = Math.max(visibleRange, throughWallsRange);

        Vec3 dir = direction.normalize();
        Vec3 end = start.add(dir.scale(searchRange));

        double blockLimitSq = resolveBlockLimitSq(player, start, end, visibleRange, throughWallsRange);

        AABB search = new AABB(start, end).inflate(1.0);

        List<Entity> candidates = mc.level.getEntities(
                player,
                search,
                e -> e instanceof LivingEntity && e.isAlive()
        );

        return findBestHit(player, start, end, candidates, blockLimitSq, searchRange);
    }

    private EntityHitResult findBestHit(LocalPlayer player,
                                        Vec3 start,
                                        Vec3 end,
                                        List<Entity> candidates,
                                        double blockLimitSq,
                                        double requestedReach) {
        Hitbox hb = Modules.get(Hitbox.class);

        EntityHitResult bestHit = null;
        double bestBoxDistSq = Double.MAX_VALUE;
        double bestHitDistSq = Double.MAX_VALUE;

        for (Entity e : candidates) {
            if (hb != null && hb.isEnabled() && hb.shouldIgnore(e)) continue;

            AABB baseBox = e.getBoundingBox();
            AABB bb = baseBox;
            if (hb != null && hb.isEnabled() && hb.shouldExpandFor(e)) {
                bb = bb.inflate(hb.getPadding());
            }

            Optional<Vec3> hit = bb.contains(start)
                    ? Optional.of(start)
                    : bb.clip(start, end);

            if (hit.isEmpty()) continue;

            double boxDistSq = boxDistanceSq(baseBox, start);
            double entityReach = resolveEntityReach(e, requestedReach, hb);
            double entityReachSq = entityReach * entityReach;
            if (boxDistSq > entityReachSq) continue;
            if (boxDistSq > blockLimitSq) continue;

            double hitDistSq = start.distanceToSqr(hit.get());

            if (boxDistSq < bestBoxDistSq
                    || (boxDistSq == bestBoxDistSq && hitDistSq < bestHitDistSq)) {
                bestBoxDistSq = boxDistSq;
                bestHitDistSq = hitDistSq;
                bestHit = new EntityHitResult(e, hit.get());
            }
        }

        return bestHit;
    }

    private double resolveEntityReach(Entity entity, double requestedReach, Hitbox hitbox) {
        if (mode.get() == Mode.VULCAN_297 && entity instanceof Player) {
            return VulcanReachController.INSTANCE.clampReach(entity, requestedReach);
        }
        return requestedReach;
    }

    public boolean isVulcan297Mode() {
        return mode.get() == Mode.VULCAN_297;
    }

    public double getConfiguredDistance() {
        return distance.get();
    }

    private double resolveBlockLimitSq(LocalPlayer player,
                                       Vec3 start,
                                       Vec3 end,
                                       double visibleRange,
                                       double throughWallsRange) {
        KillAura.RaycastMode mode = raycast.get();

        if (mode == KillAura.RaycastMode.NONE) {
            double maxRange = Math.max(visibleRange, throughWallsRange);
            return maxRange * maxRange;
        }

        Hitbox hitbox = Modules.get(Hitbox.class);
        boolean forceThrough = hitbox != null && hitbox.isEnabled();

        if (mode == KillAura.RaycastMode.THROUGH_WALLS || forceThrough) {
            double maxAllowed = Math.max(visibleRange, throughWallsRange);

            HitResult blockHit = mc.level.clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));

            if (!(blockHit instanceof BlockHitResult bhr)) {
                return maxAllowed * maxAllowed;
            }

            double blockDistSq = bhr.getLocation().distanceToSqr(start);

            if (throughWallsRange <= 0.0) {
                return blockDistSq;
            }

            return Math.max(blockDistSq, throughWallsRange * throughWallsRange);
        }

        HitResult blockHit = mc.level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        if (blockHit instanceof BlockHitResult bhr) {
            return bhr.getLocation().distanceToSqr(start);
        }

        return visibleRange * visibleRange;
    }

    public enum Mode {
        NORMAL,
        VULCAN_297
    }
}
