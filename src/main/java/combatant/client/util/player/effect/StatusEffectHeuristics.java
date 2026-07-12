/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import combatant.client.mixins.accessors.EffectParticleEffectAccessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Heuristics to guess effects from colored entity-effect particles.
 */
public enum StatusEffectHeuristics {
    ;

    private static final java.util.Map<Integer, java.util.Map<Holder<MobEffect>, Long>> LAST_SEEN = new java.util.HashMap<>();
    private static final long HEURISTIC_TIMEOUT_MS = 10000L;

    // Dynamic palette of effects + colors from the registry to survive version bumps.
    private static final List<Holder<MobEffect>> PALETTE = buildPalette();
    private static final int[] COLORS = computeColors(PALETTE);
    private static final int[] NORMALIZED_COLORS = computeNormalizedColors(COLORS);
    private static final Set<Holder<MobEffect>> PRIORITY_EFFECTS = Set.of(
            MobEffects.STRENGTH,
            MobEffects.RESISTANCE
    );
    private static final Set<Holder<MobEffect>> FORCE_PICK_EFFECTS = Set.of(
            MobEffects.STRENGTH,
            MobEffects.RESISTANCE
    );

    // Loose thresholds: particle tint varies a lot across versions; keep wide but still reject wild mismatches.
    private static final int COLOR_THRESHOLD = 42000;
    private static final int PRIORITY_THRESHOLD = 120000; // allow rougher matches for hard-to-distinguish colors
    private static final int AMBIGUITY_DELTA = 2000;
    private static final double AMBIGUITY_RATIO = 1.05; // accept best if second-best is clearly worse

    public static void handle(ParticleOptions particle, double px, double py, double pz) {
        Holder<MobEffect> direct = directEffectFromParticle(particle);
        if (direct != null) {
            registerHeuristic(direct, px, py, pz);
            return;
        }

        int color = extractColor(particle);
        if (color == 0) return;
        // Normalize brightness so darker/lighter particles still map to the same hue bucket.
        int normalizedColor = normalizeColor(color);
        Nearest nearest = nearest(normalizedColor, NORMALIZED_COLORS);
        boolean priority = isPriority(nearest.idx); // e.g. strength/resistance
        boolean ambiguous = nearest.secondDiff - nearest.diff < AMBIGUITY_DELTA;

        // Prefer fire resistance when it's one of the ambiguous pair so we don't mislabel it as strength.
        int chosenIdx = nearest.idx;
        int chosenDiff = nearest.diff;
        // Strength vs fire-resistance: both share orange/red; bias using channel dominance.
        if ((isStrength(nearest.idx) && isFire(nearest.secondIdx)) || (isStrength(nearest.secondIdx) && isFire(nearest.idx))) {
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            // Strength tint is deep red; fire resistance skews orange (more green). Bias toward fire only when clearly orange.
            float hue = hueDegrees(r, g, b);
            boolean looksFire = (g >= r * 0.8f) && hue >= 25f;
            if (looksFire) {
                chosenIdx = isFire(nearest.idx) ? nearest.idx : nearest.secondIdx;
                chosenDiff = isFire(nearest.idx) ? nearest.diff : nearest.secondDiff;
            } else {
                chosenIdx = isStrength(nearest.idx) ? nearest.idx : nearest.secondIdx;
                chosenDiff = isStrength(nearest.idx) ? nearest.diff : nearest.secondDiff;
            }
            priority = isPriority(chosenIdx);
            ambiguous = false; // resolved manually
        } else if (ambiguous && (isFire(nearest.idx) ^ isFire(nearest.secondIdx))) {
            chosenIdx = isFire(nearest.idx) ? nearest.idx : nearest.secondIdx;
            chosenDiff = isFire(nearest.idx) ? nearest.diff : nearest.secondDiff;
            priority = isPriority(chosenIdx);
            ambiguous = false;
        } else if (ambiguous && !priority && isPriority(nearest.secondIdx)) {
            chosenIdx = nearest.secondIdx;
            chosenDiff = nearest.secondDiff;
            priority = true;
            ambiguous = false; // force pick priority
        }

        // For priority effects (strength/resistance) allow even if ambiguous, with wide threshold.
        // Treat ambiguous matches as acceptable when the best is clearly closer than the runner-up (ratio check).
        if (ambiguous && !priority) {
            double ratio = nearest.secondDiff / (double) Math.max(1, nearest.diff);
            if (ratio < AMBIGUITY_RATIO) {
                return;
            }
        }

        boolean allowed = priority
                ? chosenDiff < PRIORITY_THRESHOLD
                : chosenDiff < COLOR_THRESHOLD;

        if (chosenIdx < 0 || chosenIdx >= PALETTE.size() || !allowed) {
            return;
        }
        Holder<MobEffect> guessed = PALETTE.get(chosenIdx);
        if (isNightVision(chosenIdx)) return; // do not infer night vision heuristically

        registerHeuristic(guessed, px, py, pz);
    }

    private static void registerHeuristic(Holder<MobEffect> guessed, double px, double py, double pz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        List<? extends Player> players = mc.level.players();
        double bestDist = Double.MAX_VALUE;
        Player target = null;
        Vec3 pos = new Vec3(px, py, pz);

        for (Player p : players) {
            double d = p.position().distanceTo(pos);
            if (d < bestDist) {
                bestDist = d;
                target = p;
            }
        }

        if (target == null) return;
        if (mc.player != null && target.getId() == mc.player.getId()) return;
        if (!StatusEffectInference.isParticleNearPlayer(target, pos)) return;

        long now = System.currentTimeMillis();
        // Always refresh last seen so we don't drop while particles keep coming.
        markSeen(target.getId(), guessed, now);
        boolean has = StatusEffectTracker.has(target.getId(), guessed);
        boolean hideCurrent = StatusEffectTracker.shouldHideDuration(target.getId(), guessed);
        if (has && !hideCurrent) {
            return; // already tracked with a visible duration, just refreshed last-seen
        }
        StatusEffectInference.ResolvedEffect resolved = null;
        if (!has || hideCurrent) {
            resolved = StatusEffectInference.resolve(target, guessed, pos, false);
            if (has && (resolved == null || !resolved.showDuration())) {
                return; // already tracked, and no duration upgrade available
            }
        }
        // Heuristic lifetime is handled by stale timeout; set infinite here.
        int heuristicDurationTicks = -1;
        int amplifier = 0;
        boolean hideDuration = true;
        if (resolved != null) {
            resolved = StatusEffectInference.resolve(target, guessed, pos, true);
            if (resolved != null) {
                amplifier = resolved.amplifier();
                heuristicDurationTicks = resolved.duration();
                hideDuration = !resolved.showDuration();
            }
        }
        StatusEffectTracker.apply(target.getId(), guessed, amplifier, heuristicDurationTicks, false, true, true, true, hideDuration);
    }

    private static boolean isPriority(int idx) {
        Holder<MobEffect> e = at(idx);
        if (e == null) return false;
        return PRIORITY_EFFECTS.contains(e);
    }

    private static boolean isFire(int idx) {
        Holder<MobEffect> e = at(idx);
        if (e == null) return false;
        return e.equals(MobEffects.FIRE_RESISTANCE);
    }

    private static boolean isStrength(int idx) {
        Holder<MobEffect> e = at(idx);
        if (e == null) return false;
        return e.equals(MobEffects.STRENGTH);
    }

    // If ambiguous, force-pick these instead of skipping
    private static boolean isForcePick(int idx) {
        Holder<MobEffect> e = at(idx);
        if (e == null) return false;
        return FORCE_PICK_EFFECTS.contains(e);
    }

    private static boolean isNightVision(int idx) {
        Holder<MobEffect> e = at(idx);
        if (e == null) return false;
        return e.equals(MobEffects.NIGHT_VISION);
    }

    private static void markSeen(int entityId, Holder<MobEffect> effect, long time) {
        var map = LAST_SEEN.computeIfAbsent(entityId, k -> new java.util.HashMap<>());
        map.put(effect, time);
    }

    /**
     * Called from tracker tick to drop heuristic effects if we stopped seeing particles.
     */
    public static void pruneStale(long nowMs) {
        if (LAST_SEEN.isEmpty()) return;
        var removeEntities = new java.util.ArrayList<Integer>();
        for (var entry : LAST_SEEN.entrySet()) {
            int eid = entry.getKey();
            var effects = entry.getValue();
            var toRemove = new java.util.ArrayList<Holder<MobEffect>>();
            for (var e : effects.entrySet()) {
                if (nowMs - e.getValue() > HEURISTIC_TIMEOUT_MS) {
                    StatusEffectTracker.remove(eid, e.getKey());
                    toRemove.add(e.getKey());
                }
            }
            toRemove.forEach(effects::remove);
            if (effects.isEmpty()) removeEntities.add(eid);
        }
        removeEntities.forEach(LAST_SEEN::remove);
    }

    private static int[] computeColors(List<Holder<MobEffect>> palette) {
        int[] colors = new int[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            colors[i] = palette.get(i).value().getColor();
        }
        return colors;
    }

    private static int[] computeNormalizedColors(int[] colors) {
        int[] normalized = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            normalized[i] = normalizeColor(colors[i]);
        }
        return normalized;
    }

    private static int normalizeColor(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max <= 0) return 0;
        float scale = 255f / (float) max;
        int nr = Math.min(255, Math.round(r * scale));
        int ng = Math.min(255, Math.round(g * scale));
        int nb = Math.min(255, Math.round(b * scale));
        return (nr << 16) | (ng << 8) | nb;
    }

    // Basic HSV hue calculation (degrees 0-360)
    private static float hueDegrees(int r, int g, int b) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        if (delta <= 0f) return 0f;
        float hue;
        if (max == rf) {
            hue = (gf - bf) / delta;
        } else if (max == gf) {
            hue = 2f + (bf - rf) / delta;
        } else {
            hue = 4f + (rf - gf) / delta;
        }
        hue *= 60f;
        if (hue < 0f) hue += 360f;
        return hue;
    }

    private static Nearest nearest(int color, int[] palette) {
        int best = -1, second = -1;
        int bestDiff = Integer.MAX_VALUE;
        int secondDiff = Integer.MAX_VALUE;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        for (int i = 0; i < palette.length; i++) {
            int c = palette[i];
            int dr = ((c >> 16) & 0xFF) - r;
            int dg = ((c >> 8) & 0xFF) - g;
            int db = (c & 0xFF) - b;
            int diff = dr * dr + dg * dg + db * db;
            if (diff < bestDiff) {
                secondDiff = bestDiff;
                second = best;
                bestDiff = diff;
                best = i;
            } else if (diff < secondDiff) {
                secondDiff = diff;
                second = i;
            }
        }
        // require reasonably close match
        return new Nearest(best, bestDiff, second, secondDiff);
    }

    private static Holder<MobEffect> at(int idx) {
        if (idx < 0 || idx >= PALETTE.size()) return null;
        return PALETTE.get(idx);
    }

    private static List<Holder<MobEffect>> buildPalette() {
        try {
            return BuiltInRegistries.MOB_EFFECT.listElements().collect(Collectors.toCollection(ArrayList::new));
        } catch (Exception e) {
            // Fallback to static list to avoid nulls during very early load
            return Arrays.asList(
                    MobEffects.SPEED,
                    MobEffects.SLOWNESS,
                    MobEffects.HASTE,
                    MobEffects.MINING_FATIGUE,
                    MobEffects.STRENGTH,
                    MobEffects.JUMP_BOOST,
                    MobEffects.NAUSEA,
                    MobEffects.REGENERATION,
                    MobEffects.RESISTANCE,
                    MobEffects.FIRE_RESISTANCE,
                    MobEffects.WATER_BREATHING,
                    MobEffects.INVISIBILITY,
                    MobEffects.BLINDNESS,
                    MobEffects.NIGHT_VISION,
                    MobEffects.HUNGER,
                    MobEffects.WEAKNESS,
                    MobEffects.POISON,
                    MobEffects.SLOW_FALLING,
                    MobEffects.DOLPHINS_GRACE,
                    MobEffects.CONDUIT_POWER,
                    MobEffects.HERO_OF_THE_VILLAGE,
                    MobEffects.WITHER,
                    MobEffects.HEALTH_BOOST,
                    MobEffects.SATURATION,
                    MobEffects.LUCK,
                    MobEffects.UNLUCK,
                    MobEffects.GLOWING,
                    MobEffects.LEVITATION,
                    MobEffects.BAD_OMEN,
                    MobEffects.DARKNESS,
                    MobEffects.TRIAL_OMEN,
                    MobEffects.RAID_OMEN,
                    MobEffects.WIND_CHARGED,
                    MobEffects.WEAVING,
                    MobEffects.OOZING,
                    MobEffects.INFESTED,
                    MobEffects.BREATH_OF_THE_NAUTILUS
            );
        }
    }

    private static Holder<MobEffect> directEffectFromParticle(ParticleOptions particle) {
        if (particle == ParticleTypes.RAID_OMEN) return MobEffects.RAID_OMEN;
        if (particle == ParticleTypes.TRIAL_OMEN) return MobEffects.TRIAL_OMEN;
        return null;
    }

    private static int extractColor(ParticleOptions particle) {
        if (particle instanceof EffectParticleEffectAccessor effect) {
            return effect.combatant$getColor() & 0xFFFFFF;
        }
        if (particle instanceof ColorParticleOption tinted) {
            int r = Math.min(255, Math.max(0, Math.round(tinted.getRed() * 255f)));
            int g = Math.min(255, Math.max(0, Math.round(tinted.getGreen() * 255f)));
            int b = Math.min(255, Math.max(0, Math.round(tinted.getBlue() * 255f)));
            return (r << 16) | (g << 8) | b;
        }
        return 0;
    }

    private record Nearest(int idx, int diff, int secondIdx, int secondDiff) {
    }
}


