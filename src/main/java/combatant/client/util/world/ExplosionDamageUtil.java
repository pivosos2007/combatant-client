/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.world;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.entity.simulation.PositionExtrapolation;
import combatant.client.util.player.effect.StatusEffectAccess;
import combatant.client.util.player.simulation.PlayerSimulationCache;

public enum ExplosionDamageUtil {
    ;

    private static final float CRYSTAL_POWER = 6.0f;
    private static final int MAX_EXPOSURE_TOTAL_SAMPLES = 384;
    private static final int MIN_CAPPED_SAMPLES_PER_AXIS = 2;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    public static float getCrystalDamage(LivingEntity entity,
                                         Vec3 explosionPos,
                                         int predictedTicks,
                                         boolean ignoreTerrain) {
        return getExplosionDamage(entity, explosionPos, CRYSTAL_POWER, predictedTicks, ignoreTerrain);
    }

    public static float getCrystalDamage(LivingEntity entity,
                                         Vec3 explosionPos,
                                         int predictedTicks,
                                         boolean ignoreTerrain,
                                         BlockPos ghostObsidianPos) {
        return getExplosionDamage(entity, explosionPos, CRYSTAL_POWER, predictedTicks, ignoreTerrain, ghostObsidianPos);
    }

    public static DamageDebug debugCrystalDamage(LivingEntity entity,
                                                 Vec3 explosionPos,
                                                 int predictedTicks,
                                                 boolean ignoreTerrain) {
        return calculateExplosionDamage(entity, explosionPos, CRYSTAL_POWER, predictedTicks, ignoreTerrain, null);
    }

    public static DamageDebug debugCrystalDamage(LivingEntity entity,
                                                 Vec3 explosionPos,
                                                 int predictedTicks,
                                                 boolean ignoreTerrain,
                                                 BlockPos ghostObsidianPos) {
        return calculateExplosionDamage(entity, explosionPos, CRYSTAL_POWER, predictedTicks, ignoreTerrain, ghostObsidianPos);
    }

    public static float getExplosionDamage(LivingEntity entity,
                                           Vec3 explosionPos,
                                           float power,
                                           int predictedTicks,
                                           boolean ignoreTerrain) {
        return calculateExplosionDamage(entity, explosionPos, power, predictedTicks, ignoreTerrain, null).finalDamage();
    }

    public static float getExplosionDamage(LivingEntity entity,
                                           Vec3 explosionPos,
                                           float power,
                                           int predictedTicks,
                                           boolean ignoreTerrain,
                                           BlockPos ghostObsidianPos) {
        return calculateExplosionDamage(entity, explosionPos, power, predictedTicks, ignoreTerrain, ghostObsidianPos).finalDamage();
    }

    private static DamageDebug calculateExplosionDamage(LivingEntity entity,
                                                        Vec3 explosionPos,
                                                        float power,
                                                        int predictedTicks,
                                                        boolean ignoreTerrain,
                                                        BlockPos ghostObsidianPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || entity == null || explosionPos == null)
            return emptyDebug("null", null, 0.0);
        if (entity.isInvulnerable()) return emptyDebug("invulnerable", entity.getBoundingBox(), 0.0);

        AABB box = getPredictedBox(entity, predictedTicks);

        if (!intersectsExplosionRange(explosionPos, box, power)) {
            return emptyDebug("range_box", box, entity.distanceToSqr(explosionPos));
        }

        double explosionDiameter = power * 2.0f;
        double distanceToExplosionSq = getExposureDistanceSq(entity, box, explosionPos, predictedTicks);
        if (distanceToExplosionSq > explosionDiameter * explosionDiameter) {
            return new DamageDebug("distance", 0.0f, 1.0, 0.0, 0.0f, 0.0f, 0.0f, 0.0f, distanceToExplosionSq, box, 0, 0, null, null, null, false, false);
        }

        ExposureDebug exposureDebug = getExposure(entity, explosionPos, box, ignoreTerrain, ghostObsidianPos);
        float exposure = exposureDebug.exposure();
        double distanceDecay = 1.0 - (Math.sqrt(distanceToExplosionSq) / explosionDiameter);
        double impact = exposure * distanceDecay;
        if (impact <= 0.0) {
            return new DamageDebug(
                    "impact",
                    exposure,
                    distanceDecay,
                    impact,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    distanceToExplosionSq,
                    box,
                    exposureDebug.clearRays(),
                    exposureDebug.totalRays(),
                    exposureDebug.blockerSample(),
                    exposureDebug.blockerPos(),
                    exposureDebug.blockerBlock(),
                    exposureDebug.blockerAtSampleCell(),
                    exposureDebug.blockerAtExplosionCell()
            );
        }

        float preDamage = (float) (((impact * impact + impact) / 2.0) * 7.0 * explosionDiameter + 1.0);
        float difficultyDamage = applyDifficulty(preDamage);
        float armorDamage = applyArmorReduction(entity, difficultyDamage);
        float resistanceDamage = applyResistanceReduction(entity, armorDamage);
        float finalDamage = Math.max(applyProtectionReduction(entity, resistanceDamage), 0.0f);
        return new DamageDebug(
                "ok",
                exposure,
                distanceDecay,
                impact,
                preDamage,
                armorDamage,
                resistanceDamage,
                finalDamage,
                distanceToExplosionSq,
                box,
                exposureDebug.clearRays(),
                exposureDebug.totalRays(),
                exposureDebug.blockerSample(),
                exposureDebug.blockerPos(),
                exposureDebug.blockerBlock(),
                exposureDebug.blockerAtSampleCell(),
                exposureDebug.blockerAtExplosionCell()
        );
    }

    private static boolean intersectsExplosionRange(Vec3 explosionPos, AABB box, float power) {
        double maxDist = power * 2.0;
        return new AABB(
                Mth.floor(explosionPos.x - maxDist - 1.0),
                Mth.floor(explosionPos.y - maxDist - 1.0),
                Mth.floor(explosionPos.z - maxDist - 1.0),
                Mth.floor(explosionPos.x + maxDist + 1.0),
                Mth.floor(explosionPos.y + maxDist + 1.0),
                Mth.floor(explosionPos.z + maxDist + 1.0)
        ).intersects(box);
    }

    private static double getExposureDistanceSq(LivingEntity entity, AABB box, Vec3 explosionPos, int predictedTicks) {
        if (predictedTicks <= 0) {
            return entity.distanceToSqr(explosionPos);
        }

        return box.getCenter().add(0.0, -0.9, 0.0).distanceToSqr(explosionPos);
    }

    private static AABB getPredictedBox(LivingEntity entity, int predictedTicks) {
        if (entity == null) {
            return AABB.unitCubeFromLowerCorner(Vec3.ZERO);
        }

        if (predictedTicks <= 0) {
            return entity.getBoundingBox();
        }

        if (entity instanceof Player player) {
            PlayerSimulationCache.SimulatedPlayerCache simulation = resolveSimulation(player);
            if (simulation != null) {
                PlayerSimulationCache.SimulatedPlayerSnapshot snapshot = simulation.getSnapshotAt(predictedTicks);
                EntityDimensions dimensions = entity.getDimensions(entity.getPose());
                return dimensions.makeBoundingBox(snapshot.pos());
            }
        }

        return PositionExtrapolation.getBestForEntity(entity).getBoxInTicks(predictedTicks);
    }

    private static PlayerSimulationCache.SimulatedPlayerCache resolveSimulation(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && player == mc.player) {
            return PlayerSimulationCache.getSimulationForLocalPlayer();
        }
        return PlayerSimulationCache.getSimulationForOtherPlayers(player);
    }

    private static DamageDebug emptyDebug(String reason, AABB box, double distanceSq) {
        return new DamageDebug(reason, 0.0f, 0.0, 0.0, 0.0f, 0.0f, 0.0f, 0.0f, distanceSq, box, 0, 0, null, null, null, false, false);
    }

    private static ExposureDebug getExposure(LivingEntity entity, Vec3 source, AABB box, boolean ignoreTerrain, BlockPos ghostObsidianPos) {
        return getExposureToExplosion(entity, source, box, ignoreTerrain ? 600.0f : null, ghostObsidianPos);
    }

    private static ExposureDebug getExposureToExplosion(LivingEntity entity, Vec3 source, AABB box, Float maxBlastResistance, BlockPos ghostObsidianPos) {
        double sizeX = box.maxX - box.minX;
        double sizeY = box.maxY - box.minY;
        double sizeZ = box.maxZ - box.minZ;
        double stepX = 1.0 / (sizeX * 2.0 + 1.0);
        double stepY = 1.0 / (sizeY * 2.0 + 1.0);
        double stepZ = 1.0 / (sizeZ * 2.0 + 1.0);
        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;

        if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) {
            return new ExposureDebug(0.0f, 0, 0, null, null, null, false, false);
        }

        int sampleCountX = estimateSampleCount(sizeX);
        int sampleCountY = estimateSampleCount(sizeY);
        int sampleCountZ = estimateSampleCount(sizeZ);
        long estimatedTotalSamples = (long) sampleCountX * sampleCountY * sampleCountZ;

        if (estimatedTotalSamples > MAX_EXPOSURE_TOTAL_SAMPLES) {
            return getExposureToExplosionCapped(
                    entity,
                    source,
                    box,
                    maxBlastResistance,
                    sampleCountX,
                    sampleCountY,
                    sampleCountZ,
                    estimatedTotalSamples,
                    ghostObsidianPos
            );
        }

        net.minecraft.world.phys.shapes.CollisionContext shapeContext = net.minecraft.world.phys.shapes.CollisionContext.withPosition(entity, box.minY);
        int hits = 0;
        int total = 0;
        Vec3 firstBlockedSample = null;
        BlockPos firstBlockedPos = null;
        String firstBlockedBlock = null;
        boolean blockedAtSampleCell = false;
        boolean blockedAtExplosionCell = false;

        for (double x = 0.0; x <= 1.0; x += stepX) {
            for (double y = 0.0; y <= 1.0; y += stepY) {
                for (double z = 0.0; z <= 1.0; z += stepZ) {
                    double sampleX = Mth.lerp(x, box.minX, box.maxX);
                    double sampleY = Mth.lerp(y, box.minY, box.maxY);
                    double sampleZ = Mth.lerp(z, box.minZ, box.maxZ);
                    Vec3 sample = new Vec3(sampleX + offsetX, sampleY, sampleZ + offsetZ);
                    BlockHitResult hitResult = raycast(new ClipContext(
                            sample,
                            source,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            shapeContext
                    ), maxBlastResistance, ghostObsidianPos);
                    if (hitResult.getType() == HitResult.Type.MISS) {
                        hits++;
                    } else if (firstBlockedPos == null) {
                        firstBlockedSample = sample;
                        firstBlockedPos = hitResult.getBlockPos();
                        firstBlockedBlock = getBlockDebugName(hitResult.getBlockPos(), ghostObsidianPos);
                        blockedAtSampleCell = hitResult.getBlockPos().equals(BlockPos.containing(sample));
                        blockedAtExplosionCell = hitResult.getBlockPos().equals(BlockPos.containing(source));
                    }
                    total++;
                }
            }
        }

        return new ExposureDebug(
                total <= 0 ? 0.0f : (float) hits / (float) total,
                hits,
                total,
                firstBlockedSample,
                firstBlockedPos,
                firstBlockedBlock,
                blockedAtSampleCell,
                blockedAtExplosionCell
        );
    }

    private static ExposureDebug getExposureToExplosionCapped(LivingEntity entity,
                                                              Vec3 source,
                                                              AABB box,
                                                              Float maxBlastResistance,
                                                              int baseCountX,
                                                              int baseCountY,
                                                              int baseCountZ,
                                                              long estimatedTotalSamples,
                                                              BlockPos ghostObsidianPos) {
        int sampleCountX = baseCountX;
        int sampleCountY = baseCountY;
        int sampleCountZ = baseCountZ;

        double scale = Math.cbrt((double) MAX_EXPOSURE_TOTAL_SAMPLES / (double) estimatedTotalSamples);
        sampleCountX = clampSampleCount((int) Math.round(sampleCountX * scale), baseCountX);
        sampleCountY = clampSampleCount((int) Math.round(sampleCountY * scale), baseCountY);
        sampleCountZ = clampSampleCount((int) Math.round(sampleCountZ * scale), baseCountZ);

        while ((long) sampleCountX * sampleCountY * sampleCountZ > MAX_EXPOSURE_TOTAL_SAMPLES) {
            if (sampleCountX >= sampleCountY && sampleCountX >= sampleCountZ && sampleCountX > MIN_CAPPED_SAMPLES_PER_AXIS) {
                sampleCountX--;
            } else if (sampleCountY >= sampleCountZ && sampleCountY > MIN_CAPPED_SAMPLES_PER_AXIS) {
                sampleCountY--;
            } else if (sampleCountZ > MIN_CAPPED_SAMPLES_PER_AXIS) {
                sampleCountZ--;
            } else {
                break;
            }
        }

        net.minecraft.world.phys.shapes.CollisionContext shapeContext = net.minecraft.world.phys.shapes.CollisionContext.withPosition(entity, box.minY);
        int hits = 0;
        int total = 0;
        Vec3 firstBlockedSample = null;
        BlockPos firstBlockedPos = null;
        String firstBlockedBlock = null;
        boolean blockedAtSampleCell = false;
        boolean blockedAtExplosionCell = false;

        for (int x = 0; x < sampleCountX; x++) {
            for (int y = 0; y < sampleCountY; y++) {
                for (int z = 0; z < sampleCountZ; z++) {
                    Vec3 sample = new Vec3(
                            sampleCoordinate(box.minX, box.maxX, x, sampleCountX),
                            sampleCoordinate(box.minY, box.maxY, y, sampleCountY),
                            sampleCoordinate(box.minZ, box.maxZ, z, sampleCountZ)
                    );

                    BlockHitResult hitResult = raycast(new ClipContext(
                            sample,
                            source,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            shapeContext
                    ), maxBlastResistance, ghostObsidianPos);
                    if (hitResult.getType() == HitResult.Type.MISS) {
                        hits++;
                    } else if (firstBlockedPos == null) {
                        firstBlockedSample = sample;
                        firstBlockedPos = hitResult.getBlockPos();
                        firstBlockedBlock = getBlockDebugName(hitResult.getBlockPos(), ghostObsidianPos);
                        blockedAtSampleCell = hitResult.getBlockPos().equals(BlockPos.containing(sample));
                        blockedAtExplosionCell = hitResult.getBlockPos().equals(BlockPos.containing(source));
                    }
                    total++;
                }
            }
        }

        return new ExposureDebug(
                total <= 0 ? 0.0f : (float) hits / (float) total,
                hits,
                total,
                firstBlockedSample,
                firstBlockedPos,
                firstBlockedBlock,
                blockedAtSampleCell,
                blockedAtExplosionCell
        );
    }

    private static int estimateSampleCount(double size) {
        return Math.max(2, (int) Math.floor(size * 2.0 + 1.0) + 1);
    }

    private static int clampSampleCount(int proposed, int original) {
        return Math.max(MIN_CAPPED_SAMPLES_PER_AXIS, Math.min(original, proposed));
    }

    private static double sampleCoordinate(double min, double max, int index, int total) {
        if (total <= 1) {
            return (min + max) * 0.5;
        }
        double t = (index + 0.5) / total;
        return Mth.lerp(t, min, max);
    }

    private static BlockHitResult raycast(ClipContext context, Float maxBlastResistance, BlockPos ghostObsidianPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return BlockHitResult.miss(context.getTo(), Direction.getApproximateNearest(0.0, 0.0, 1.0), BlockPos.containing(context.getTo()));
        }

        return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (innerContext, blockPos) -> {
            boolean ghostBlock = ghostObsidianPos != null && blockPos.equals(ghostObsidianPos);
            var state = ghostBlock
                    ? net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState()
                    : mc.level.getBlockState(blockPos);
            var fluidState = ghostBlock
                    ? Fluids.EMPTY.defaultFluidState()
                    : mc.level.getFluidState(blockPos);
            if (maxBlastResistance != null && state.getBlock().getExplosionResistance() < maxBlastResistance) {
                state = net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
            }
            if (maxBlastResistance != null && fluidState.getExplosionResistance() < maxBlastResistance) {
                fluidState = Fluids.EMPTY.defaultFluidState();
            }

            Vec3 start = innerContext.getFrom();
            Vec3 end = innerContext.getTo();
            var blockShape = innerContext.getBlockShape(state, mc.level, blockPos);
            var blockHit = mc.level.clipWithInteractionOverride(start, end, blockPos, blockShape, state);
            var fluidShape = innerContext.getFluidShape(fluidState, mc.level, blockPos);
            var fluidHit = fluidShape.clip(start, end, blockPos);
            double blockDistance = blockHit == null ? Double.MAX_VALUE : start.distanceToSqr(blockHit.getLocation());
            double fluidDistance = fluidHit == null ? Double.MAX_VALUE : start.distanceToSqr(fluidHit.getLocation());
            return blockDistance <= fluidDistance ? blockHit : fluidHit;
        }, innerContext -> {
            Vec3 diff = innerContext.getFrom().subtract(innerContext.getTo());
            return BlockHitResult.miss(innerContext.getTo(), Direction.getApproximateNearest(diff.x, diff.y, diff.z), BlockPos.containing(innerContext.getTo()));
        });
    }

    private static String getBlockDebugName(BlockPos pos, BlockPos ghostObsidianPos) {
        Minecraft mc = Minecraft.getInstance();
        if (ghostObsidianPos != null && ghostObsidianPos.equals(pos)) {
            return BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Blocks.OBSIDIAN).toString();
        }
        if (mc == null || mc.level == null || pos == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
    }

    private static float applyDifficulty(float damage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return damage;

        return switch (mc.level.getDifficulty()) {
            case EASY -> Math.min(damage / 2.0f + 1.0f, damage);
            case HARD -> damage * 1.5f;
            default -> damage;
        };
    }

    private static float applyArmorReduction(LivingEntity entity, float damage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return damage;

        DamageSource source = Explosion.getDefaultDamageSource(mc.level, mc.player);
        float armor = entity.getArmorValue();
        float toughness = (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        return CombatRules.getDamageAfterAbsorb(entity, damage, source, armor, toughness);
    }

    private static float applyResistanceReduction(LivingEntity entity, float damage) {
        MobEffectInstance resistance = StatusEffectAccess.get(entity, MobEffects.RESISTANCE);
        if (resistance == null) return damage;
        int reduced = 25 - (resistance.getAmplifier() + 1) * 5;
        return Math.max(damage * reduced / 25.0f, 0.0f);
    }

    private static float applyProtectionReduction(LivingEntity entity, float damage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || damage <= 0.0f) return Math.max(damage, 0.0f);
        float protection = getProtectionAmount(entity);
        return protection > 0.0f ? CombatRules.getDamageAfterMagicAbsorb(damage, protection) : damage;
    }

    private static float getProtectionAmount(LivingEntity entity) {
        float total = 0.0f;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            total += getProtectionAmount(entity.getItemBySlot(slot));
        }
        return total;
    }

    private static float getProtectionAmount(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || stack == null || stack.isEmpty()) return 0.0f;
        return getEnchantmentLevel(Enchantments.BLAST_PROTECTION, stack) * 2.0f
                + getEnchantmentLevel(Enchantments.PROTECTION, stack);
    }

    private static int getEnchantmentLevel(ResourceKey<Enchantment> key, ItemStack stack) {
        Holder<Enchantment> entry = getEnchantmentEntry(key);
        return entry != null ? EnchantmentHelper.getItemEnchantmentLevel(entry, stack) : 0;
    }

    private static Holder<Enchantment> getEnchantmentEntry(ResourceKey<Enchantment> key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || key == null) return null;

        Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.getValue(key);
        return enchantment != null ? registry.wrapAsHolder(enchantment) : null;
    }

    public record DamageDebug(
            String reason,
            float exposure,
            double distanceFactor,
            double impact,
            float preDamage,
            float postArmorDamage,
            float postResistanceDamage,
            float finalDamage,
            double distanceSq,
            AABB box,
            int clearRays,
            int totalRays,
            Vec3 blockerSample,
            BlockPos blockerPos,
            String blockerBlock,
            boolean blockerAtSampleCell,
            boolean blockerAtExplosionCell
    ) {
    }

    private record ExposureDebug(
            float exposure,
            int clearRays,
            int totalRays,
            Vec3 blockerSample,
            BlockPos blockerPos,
            String blockerBlock,
            boolean blockerAtSampleCell,
            boolean blockerAtExplosionCell
    ) {
    }
}
