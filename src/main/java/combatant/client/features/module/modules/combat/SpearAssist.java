/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.WorldTextRenderer;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.combat.SpearKinetics;
import combatant.client.util.combat.SpearKinetics.Condition;
import combatant.client.util.combat.SpearKinetics.ReachBand;
import combatant.client.util.combat.SpearKinetics.WindowState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

@ModuleInfo(
        id = "spearassist",
        displayName = "SpearAssist",
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.LEGIT,
        description = "module.spearassist.description")
public class SpearAssist extends Module {

    private static final String SETTING_TOGGLES = "toggles";
    private static final int COLOR_LIT = 0xFF4ADE80;
    private static final int COLOR_UNMET = 0xFFFACC15;
    private static final int COLOR_EXPIRED = 0xFF6B7280;
    private static final int COLOR_PENDING = 0xFFB0B8C4;
    private static final int COLOR_CLOSE = 0xFFFF3B3B;

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanMapValue toggles = group(
            "spearassist_toggles",
            SETTING_TOGGLES,
            new java.util.LinkedHashMap<>() {{
                put("charge_hud", true);
                put("reach_band", true);
                put("auto_recharge", false);
            }}
    );

    public static boolean isSpear(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.get(DataComponents.KINETIC_WEAPON) != null
                || stack.get(DataComponents.PIERCING_WEAPON) != null);
    }

    private record Pick(Entity target, double distance) {
    }

    private static Condition cond(Optional<KineticWeapon.Condition> c) {
        return c.map(v -> new Condition(v.maxDurationTicks(), v.minSpeed(), v.minRelativeSpeed())).orElse(null);
    }

    private static int color(WindowState s) {
        return switch (s) {
            case LIT -> COLOR_LIT;
            case UNMET -> COLOR_UNMET;
            case EXPIRED -> COLOR_EXPIRED;
            case PENDING -> COLOR_PENDING;
        };
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.gameMode == null || !toggles.get("auto_recharge")) return;
        LocalPlayer player = mc.player;
        if (!player.isUsingItem()) return;
        KineticWeapon kw = player.getUseItem().get(DataComponents.KINETIC_WEAPON);
        if (kw == null) return;
        if (SpearKinetics.shouldRecharge(true, mc.options.keyUse.isDown(), true,
                player.getTicksUsingItem(), kw.delayTicks(),
                cond(kw.dismountConditions()), cond(kw.knockbackConditions()), cond(kw.damageConditions()))) {
            // Vanilla release path (RELEASE_USE_ITEM packet); handleKeybinds re-uses next tick as the key is held.
            mc.gameMode.releaseUsingItem(player);
        }
    }

    /** Nearest entity along the look ray out to {@code reach}, ignoring the spear dead zone; null when none. */
    private Pick pick(LocalPlayer player, double reach, float partial) {
        if (mc.level == null) return null;
        Vec3 eye = player.getEyePosition(partial);
        Vec3 look = player.getViewVector(partial);
        Vec3 end = eye.add(look.scale(reach));
        double limitSq = reach * reach;
        BlockHitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            limitSq = eye.distanceToSqr(block.getLocation());
        }
        AABB search = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        Entity best = null;
        double bestSq = limitSq;
        for (Entity e : mc.level.getEntities(player, search,
                en -> en.isPickable() && !en.isSpectator() && en != player.getVehicle())) {
            AABB box = e.getBoundingBox().inflate(e.getPickRadius());
            double d;
            if (box.contains(eye)) {
                d = 0.0;
            } else {
                Optional<Vec3> hit = box.clip(eye, end);
                if (hit.isEmpty()) continue;
                d = eye.distanceToSqr(hit.get());
            }
            if (d <= bestSq) {
                bestSq = d;
                best = e;
            }
        }
        if (best == null) return null;
        return new Pick(best, Math.sqrt(best.getBoundingBox().distanceToSqr(eye)));
    }

    private double forwardBlocksPerTick(LocalPlayer player) {
        return player.getLookAngle().dot(player.getKnownMovement());
    }

    private Pick reachPick(LocalPlayer player, ItemStack stack, AttackRange range, float partial) {
        double max = range.effectiveMaxRange(player);
        if (player.isUsingItem() && player.getUseItem().get(DataComponents.KINETIC_WEAPON) != null) {
            max = SpearKinetics.chargingMaxReach(max, forwardBlocksPerTick(player));
        }
        return pick(player, max + range.hitboxMargin() + 0.5, partial);
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !toggles.get("reach_band") || mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;
        ItemStack stack = player.isUsingItem() ? player.getUseItem() : player.getMainHandItem();
        if (!isSpear(stack)) return;
        AttackRange range = player.getAttackRangeWith(stack);
        if (range == null) return;
        Pick pick = reachPick(player, stack, range, tickDelta);
        if (pick == null) return;

        double max = range.effectiveMaxRange(player);
        if (player.isUsingItem() && stack.get(DataComponents.KINETIC_WEAPON) != null) {
            max = SpearKinetics.chargingMaxReach(max, forwardBlocksPerTick(player));
        }
        ReachBand band = SpearKinetics.classifyReach(pick.distance(), range.effectiveMinRange(player), max,
                range.hitboxMargin());
        int argb = switch (band) {
            case TOO_CLOSE -> COLOR_CLOSE;
            case IN_RANGE -> COLOR_LIT;
            case TOO_FAR -> COLOR_UNMET;
        };
        AABB box = pick.target().getBoundingBox();
        ExplosionRenderUtil.addOutlineBox(renderer, box, argb);

        String text = String.format(java.util.Locale.ROOT, "%.1f %s", pick.distance(),
                I18n.get("hud.spearassist.reach." + band.name().toLowerCase(java.util.Locale.ROOT)));
        TextRenderer bold = BuiltinFontCatalog.IOSEVKA_BOLD.renderer(TextRenderer.get());
        Vec3 anchor = box.getCenter().add(0.0, box.getYsize() * 0.5, 0.0);
        WorldTextRenderer.drawBillboard(renderer, bold, text, anchor,
                WorldTextRenderer.Options.defaults()
                        .withDepthMode(Renderer3D.DepthMode.NONE)
                        .withShadow(true)
                        .withOffset(0.0, 0.10)
                        .withColor(new RenderColor(argb)));
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, float tickDelta) {
        if (!isEnabled() || !toggles.get("charge_hud") || mc.player == null || mc.level == null) return;
        if (mc.screen != null || !mc.options.getCameraType().isFirstPerson()) return;
        LocalPlayer player = mc.player;
        if (!player.isUsingItem()) return;
        ItemStack stack = player.getUseItem();
        KineticWeapon kw = stack.get(DataComponents.KINETIC_WEAPON);
        if (kw == null) return;

        int used = player.getTicksUsingItem();
        int delay = kw.delayTicks();
        double attackerSpeed = SpearKinetics.speedAlong(
                player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z,
                KineticWeapon.getMotion(player).x, KineticWeapon.getMotion(player).y, KineticWeapon.getMotion(player).z);

        Pick pick = null;
        AttackRange range = player.getAttackRangeWith(stack);
        if (range != null) pick = reachPick(player, stack, range, tickDelta);
        double rel = 0.0;
        if (pick != null) {
            Vec3 look = player.getLookAngle();
            Vec3 tm = KineticWeapon.getMotion(pick.target());
            rel = SpearKinetics.relativeSpeed(attackerSpeed, SpearKinetics.speedAlong(look.x, look.y, look.z, tm.x, tm.y, tm.z));
        }
        boolean hasTarget = pick != null;

        float s = ViewportContext.getScaleFactor();
        float cx = mc.getWindow().getWidth() * 0.5f;
        float cy = mc.getWindow().getHeight() * 0.5f;
        float chipW = 26.0f * s, gap = 3.0f * s, chipH = 10.0f * s;
        float totalW = chipW * 3 + gap * 2;
        float x0 = cx - totalW * 0.5f;
        float y0 = cy + 18.0f * s;
        float barH = 3.0f * s;
        float radius = 1.5f * s;

        // charge-up bar
        float charge = SpearKinetics.chargeProgress(used, delay);
        renderer.roundedRect(x0, y0, totalW, barH, radius, HudRenderUtil.glassBackground());
        int accent = charge >= 1.0f ? COLOR_LIT : HudRenderUtil.themeAccentGradient(255).start();
        if (charge > 0.0f) {
            renderer.roundedRect(x0, y0, totalW * charge, barH, radius, accent);
        }

        Condition[] conds = {cond(kw.dismountConditions()), cond(kw.knockbackConditions()), cond(kw.damageConditions())};
        String[] labels = {I18n.get("hud.spearassist.dismount"), I18n.get("hud.spearassist.knockback"),
                I18n.get("hud.spearassist.damage")};
        float chipY = y0 + barH + gap;
        TextRenderer font = BuiltinFontCatalog.MONSTERRAT.renderer(textRenderer);
        float textScale = 5.5f * s / 18.0f;
        for (int i = 0; i < 3; i++) {
            Condition c = conds[i];
            if (c == null) continue;
            float x = x0 + i * (chipW + gap);
            WindowState state = SpearKinetics.state(c, used, delay, attackerSpeed, rel, hasTarget);
            int col = color(state);
            renderer.roundedRect(x, chipY, chipW, chipH, radius, HudRenderUtil.glassBackground());
            float remain = SpearKinetics.remainingFraction(c, used, delay);
            if (remain > 0.0f) {
                renderer.roundedRect(x, chipY + chipH - 2.0f * s, chipW * remain, 2.0f * s, radius,
                        HudRenderUtil.setAlpha(col, 200));
            }
            String text = labels[i];
            if (i == 2 && hasTarget && state == WindowState.LIT) {
                double base = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                text = ExplosionRenderUtil.formatDamage(SpearKinetics.stabDamage(base, rel, kw.damageMultiplier()));
            }
            font.begin(textScale, false, false);
            double w = font.getWidth(text, true);
            font.render(text, x + (chipW - w) * 0.5, chipY + (chipH - 2.0f * s - font.getHeight(true)) * 0.5, new RenderColor(col), true);
            font.end();
        }
    }
}
