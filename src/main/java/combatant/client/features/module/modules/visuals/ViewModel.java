/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

/**
 * ViewModel: mini items + custom swing animations
 */
//todo Description
@ModuleInfo(id = "viewmodel", displayName = "ViewModel", category = ModuleCategory.VISUALS)
public class ViewModel extends Module {

    private static final String SETTING_MINI_ALL = "mini_all_items";
    private static final String SETTING_MINI_ITEMS = "mini_items";
    private static final String SETTING_MINI_SCALE = "mini_scale";
    private static final String SETTING_SWING_ENABLED = "swing_enabled";
    private static final String SETTING_SWING_ALL = "swing_all_items";
    private static final String SETTING_SWING_ITEMS = "swing_items";
    private static final String SETTING_ROTATION_BYPASS_ITEMS = "rotation_bypass_items";
    private static final String SETTING_SWING_OFFSET_X = "swing_offset_x";
    private static final String SETTING_SWING_OFFSET_Y = "swing_offset_y";
    private static final String SETTING_SWING_OFFSET_Z = "swing_offset_z";
    private static final String SETTING_LIQUID_OFFSET_Z = "liquid_offset_z";
    private static final String SETTING_ANIM_MODE = "anim_mode";
    private static final String SETTING_ANIM_SPEED = "anim_speed";
    private static final String SETTING_ANIM_STRENGTH = "anim_strength";
    private static final String SETTING_ANIM_SPIN_ENABLED = "anim_spin_enabled";
    private static final String SETTING_ANIM_SPIN_SPEED = "anim_spin_speed";
    private static final String SETTING_ANIM_SPIN_AXIS = "anim_spin_axis";
    private static final String SETTING_EQUIP_LOWERING = "equip_lowering_factor";
    private static final String SETTING_SMOOTH_RUN_LOWERING = "smooth_run_lowering";
    private static final String SETTING_SMOOTH_RUN_LOWERING_AMOUNT = "smooth_run_lowering_amount";
    public final Minecraft mc = Minecraft.getInstance();
    public final NumberValue<Float> liquidOffsetZ = num("liquid_offset_z", SETTING_LIQUID_OFFSET_Z, 0.0f, -2.0f, 2.0f);
    /* -----------------------------
     * MINI ITEMS
     * ----------------------------- */
    private final BooleanValue miniAll = bool("mini_all_items", SETTING_MINI_ALL, false);
    private final ItemIdSetValue miniItems =
            visibleWhen(itemList("mini_items", SETTING_MINI_ITEMS, TextListSetting.PickerMode.ITEMS), () -> !miniAll.get());
    private final NumberValue<Float> miniScale = num("mini_scale", SETTING_MINI_SCALE, 0.65f, 0.05f, 2.0f);
    /* -----------------------------
     * OFFSETS
     * ----------------------------- */
    private final BooleanValue swingEnabled = bool("swing_enabled", SETTING_SWING_ENABLED, true);
    public final NumberValue<Float> swingX = visibleWhen(num("swing_offset_x", SETTING_SWING_OFFSET_X, 0f, -2f, 2f), swingEnabled::get);
    public final NumberValue<Float> swingY = visibleWhen(num("swing_offset_y", SETTING_SWING_OFFSET_Y, 0f, -2f, 2f), swingEnabled::get);
    public final NumberValue<Float> swingZ = visibleWhen(num("swing_offset_z", SETTING_SWING_OFFSET_Z, 0f, -2f, 2f), swingEnabled::get);
    /* -----------------------------
     * ANIMATION SYSTEM
     * ----------------------------- */
    public final ModeValue animMode = visibleWhen(modeSetting(
            "anim_mode",
            SETTING_ANIM_MODE,
            "Smooth",
            "Back", "Smooth Down", "Block", "Smooth", "Swipe Back", "Block Down",
            "New", "7", "Forward", "Glide", "Tap", "Blocking", "Touch", "Slant",
            "Spin", "Helicopter", "Stab", "Circle", "Wave", "360 Swing",
            "SmoothVanilla", "SwipeBackDown", "Vanilla", "Flip", "Side", "Overhead",
            "Hammer", "Chop", "Arc", "Double", "Shake", "Shove", "Slash", "Thrust"), swingEnabled::get);
    public final NumberValue<Float> animSpeed = visibleWhen(num("anim_speed", SETTING_ANIM_SPEED, 1.0f, 0.1f, 4.0f), swingEnabled::get);
    public final NumberValue<Float> animStrength = visibleWhen(num("anim_strength", SETTING_ANIM_STRENGTH, 5.0f, 0.1f, 10.0f), swingEnabled::get);
    public final BooleanValue continuousSpin = visibleWhen(bool("anim_spin_enabled", SETTING_ANIM_SPIN_ENABLED, false), swingEnabled::get);
    public final NumberValue<Float> spinSpeed =
            visibleWhen(num("anim_spin_speed", SETTING_ANIM_SPIN_SPEED, 1.0f, 0.1f, 15.0f), () -> swingEnabled.get() && continuousSpin.get());
    public final ModeValue spinAxis =
            visibleWhen(modeSetting("anim_spin_axis", SETTING_ANIM_SPIN_AXIS, "Y", "X", "Y", "Z"), () -> swingEnabled.get() && continuousSpin.get());
    public final NumberValue<Float> equipLowering = visibleWhen(num("equip_lowering_factor", SETTING_EQUIP_LOWERING, 1.0f, 0.0f, 2.0f), swingEnabled::get);
    private final BooleanValue swingAll = visibleWhen(bool("swing_all_items", SETTING_SWING_ALL, false), swingEnabled::get);
    private final ItemIdSetValue swingItems =
            visibleWhen(itemList("swing_items", SETTING_SWING_ITEMS, TextListSetting.PickerMode.ITEMS),
                    () -> swingEnabled.get() && !swingAll.get());
    private final ItemIdSetValue rotationBypassItems =
            visibleWhen(itemList("rotation_bypass_items", SETTING_ROTATION_BYPASS_ITEMS, TextListSetting.PickerMode.ITEMS), swingEnabled::get);
    private final BooleanValue smoothRunLowering = bool("smooth_run_lowering", SETTING_SMOOTH_RUN_LOWERING, false);
    private final NumberValue<Float> smoothRunLoweringAmount =
            visibleWhen(num("smooth_run_lowering_amount", SETTING_SMOOTH_RUN_LOWERING_AMOUNT, 0.20f, 0.0f, 1.0f), smoothRunLowering::get);

    private float currentSpin = 0.0f;

    public boolean smoothRunLoweringEnabled() {
        return isEnabled() && smoothRunLowering.get();
    }

    public float getSmoothRunLoweringAmount() {
        return smoothRunLoweringAmount.get();
    }


    /* --------------------------------------------
     * Item checks
     * -------------------------------------------- */

    public boolean shouldScale(ItemStack st) {
        if (miniAll.get()) return true;
        if (st == null || st.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        return miniItems.get().contains(id);
    }

    public boolean shouldSwing(ItemStack st) {
        if (!swingEnabled.get()) return false;
        if (swingAll.get()) return true;
        if (st == null || st.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        return swingItems.get().contains(id);
    }

    public boolean shouldBypassRotationTransform(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        return rotationBypassItems.get().contains(id);
    }


    /* --------------------------------------------
     * MINI SCALE
     * -------------------------------------------- */

    public void applyMini(PoseStack ms, ItemStack st) {
        if (!shouldScale(st)) return;
        float sc = miniScale.get();
        ms.scale(sc, sc, sc);
    }


    /* --------------------------------------------
     * SWING ANIMATIONS (custom baked)
     * -------------------------------------------- */

    public void applySwingAnimation(float swingProg, PoseStack ms, HumanoidArm arm) {

        if (arm == HumanoidArm.LEFT) return;
        if (mc.player == null) return;

        ItemStack st = mc.player.getMainHandItem();
        if (!shouldSwing(st)) return;

        float speed = animSpeed.get();
        float progress = Mth.clamp(swingProg, 0.0f, 1.0f);
        float animProgress = speed >= 1.0f
                ? Mth.clamp(progress * speed, 0.0f, 1.0f)
                : progress; // keep full swing so slow speeds don't get cut at cooldown end
        float anim = Mth.sin(animProgress * (float) Math.PI);
        float sin1 = Mth.sin(animProgress * animProgress * (float) Math.PI);
        float sin2 = Mth.sin(Mth.sqrt(animProgress) * (float) Math.PI);
        float power = animStrength.get();
        float scale = 1.0f;

        // Apply user offsets
        ms.translate(swingX.get(), swingY.get(), swingZ.get());

        boolean applySpin = continuousSpin.get();
        if (applySpin) {
            currentSpin += spinSpeed.get() * 0.5f;
            if (currentSpin > 360.0f) currentSpin -= 360.0f;
        }

        switch (animMode.get()) {
            case "SmoothVanilla": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, -0.2f * anim, 0.0);
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f * anim));
                break;
            }

            case "SwipeBackDown": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, 0.1, -0.1);
                ms.mulPose(Axis.YP.rotationDegrees(60.0f));
                ms.mulPose(Axis.ZP.rotationDegrees(-60.0f));
                ms.translate(0.0, -0.3f * anim, 0.0);
                break;
            }

            case "Swipe Back": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, 0.1, -0.1);
                ms.mulPose(Axis.YP.rotationDegrees(60.0f));
                ms.mulPose(Axis.ZP.rotationDegrees(-60.0f));
                ms.mulPose(Axis.YP.rotationDegrees(sin2 * sin1 * -5.0f));
                ms.mulPose(Axis.XP.rotationDegrees(-10.0f - power * 10.0f * anim));
                ms.mulPose(Axis.XP.rotationDegrees(-60.0f));
                break;
            }

            case "New": {
                ms.scale(scale * (1.0f + 0.1f * sin2), scale, scale);
                ms.translate(0.2, 0.1, -0.2);
                ms.mulPose(Axis.XP.rotationDegrees(60.0f));
                float swingAngle = -120.0f - power * 10.0f * anim;
                ms.mulPose(Axis.XP.rotationDegrees(swingAngle));
                ms.mulPose(Axis.YP.rotationDegrees(30.0f * sin2));
                ms.mulPose(Axis.ZP.rotationDegrees(15.0f * sin2));
                if (progress > 0.8f) {
                    ms.mulPose(Axis.XP.rotationDegrees(10.0f * (progress - 0.8f) * 5.0f));
                }
                break;
            }

            case "Block Down": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, 0.1, -0.1);
                ms.translate(0.5, -0.1, 0.0);
                ms.mulPose(Axis.XP.rotationDegrees(sin2 * -9.0f));
                ms.translate(-0.5, 0.1, 0.0);
                ms.translate(0.5, -0.1, 0.0);
                ms.mulPose(Axis.YP.rotationDegrees(sin2 * -18.0f));
                ms.translate(-0.5, 0.1, 0.0);
                ms.mulPose(Axis.YP.rotationDegrees(50.0f));
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f));
                ms.mulPose(Axis.YP.rotationDegrees(50.0f));
                break;
            }

            case "Back": {
                ms.scale(scale, scale, scale);
                ms.translate(0.4, 0.1, -0.5);
                ms.mulPose(Axis.YP.rotationDegrees(90.0f));
                ms.mulPose(Axis.ZP.rotationDegrees(-60.0f));
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f - power * 10.0f * anim));
                break;
            }

            case "7": {
                ms.scale(scale, scale, scale);
                ms.mulPose(Axis.YP.rotationDegrees(15.0f * anim));
                ms.mulPose(Axis.ZP.rotationDegrees(-50.0f * anim));
                ms.mulPose(Axis.XP.rotationDegrees((-10.0f - power) * anim));
                break;
            }

            case "Block": {
                ms.scale(scale, scale, scale);
                ms.translate(0.4, 0.0, -0.5);
                ms.mulPose(Axis.YP.rotationDegrees(90.0f));
                ms.mulPose(Axis.ZP.rotationDegrees(-30.0f));
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f - power * 10.0f * anim));
                break;
            }

            case "Forward": {
                ms.scale(scale, scale, scale);
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f));
                ms.mulPose(Axis.ZP.rotationDegrees(90.0f));
                ms.mulPose(Axis.XP.rotationDegrees(-1.35f));
                ms.mulPose(Axis.YP.rotationDegrees(90.0f));
                ms.mulPose(Axis.XP.rotationDegrees(50.0f));
                ms.mulPose(Axis.YP.rotationDegrees(-30.0f));
                ms.translate(0.2, 0.5, 1.3);
                ms.translate(0.9f * anim, 0.2f + 0.35f * anim, 0.3f * anim);
                ms.mulPose(Axis.YP.rotationDegrees(anim * 50.0f));
                ms.mulPose(Axis.ZP.rotationDegrees(anim * -20.0f));
                break;
            }

            case "Glide": {
                ms.scale(scale, scale, scale);
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f));
                ms.mulPose(Axis.ZP.rotationDegrees(90.0f));
                ms.mulPose(Axis.XP.rotationDegrees(-1.35f));
                ms.mulPose(Axis.YP.rotationDegrees(90.0f));
                ms.mulPose(Axis.XP.rotationDegrees(50.0f));
                ms.mulPose(Axis.YP.rotationDegrees(-30.0f));
                ms.translate(0.2, 0.5, 1.3);
                ms.translate(0.0, 0.7f * anim + 0.2, -0.7f * anim);
                ms.mulPose(Axis.XP.rotationDegrees(anim * -70.0f));
                break;
            }

            case "Tap": {
                ms.scale(scale, scale, scale);
                ms.mulPose(Axis.XP.rotationDegrees(-60.0f));
                ms.mulPose(Axis.ZP.rotationDegrees(60.0f));
                ms.mulPose(Axis.XP.rotationDegrees(-0.9f));
                ms.mulPose(Axis.YP.rotationDegrees(60.0f));
                ms.translate(0.6, 0.0, 1.0);
                ms.translate(0.4f * anim, 0.0, 0.0);
                break;
            }

            case "Blocking": {
                ms.scale(scale, scale, scale);
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f));
                ms.mulPose(Axis.ZP.rotationDegrees(90.0f));
                ms.mulPose(Axis.YP.rotationDegrees(40.0f));
                ms.mulPose(Axis.ZP.rotationDegrees(-20.0f));
                ms.translate(0.0, 0.3, 0.9);
                ms.translate(0.0, 0.6f * anim, -0.6f * anim);
                ms.mulPose(Axis.XP.rotationDegrees(anim * -45.0f));
                break;
            }

            case "Touch": {
                ms.scale(scale, scale, scale);
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f));
                ms.mulPose(Axis.ZP.rotationDegrees(90.0f));
                ms.mulPose(Axis.XP.rotationDegrees(-1.35f));
                ms.mulPose(Axis.YP.rotationDegrees(90.0f));
                ms.translate(-0.3, 0.0, 1.3);
                ms.translate(0.0, 0.0, 0.2f * anim);
                ms.mulPose(Axis.XP.rotationDegrees(anim * 15.0f));
                break;
            }

            case "Slant": {
                ms.scale(scale, scale, scale);
                float rotate = 35.0f;
                ms.translate(0.0, 0.0, -0.3f * anim);
                ms.mulPose(Axis.XP.rotationDegrees(anim * -rotate));
                ms.mulPose(Axis.ZP.rotationDegrees(anim * rotate));
                break;
            }

            case "Spin": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, 0.1, -0.2);
                ms.mulPose(Axis.YP.rotationDegrees(anim * 360.0f * power));
                ms.mulPose(Axis.XP.rotationDegrees(45.0f));
                break;
            }

            case "Helicopter": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, -0.2, 0.0);
                float rotation = (System.currentTimeMillis() % 2000L) / 2000.0f * 360.0f * power;
                ms.mulPose(Axis.ZP.rotationDegrees(rotation));
                ms.translate(0.0, 0.3, 0.0);
                break;
            }

            case "Stab": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, 0.0, -0.5f * anim * power);
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f));
                break;
            }

            case "Circle": {
                ms.scale(scale, scale, scale);
                float circleProgress = progress * 2.0f * (float) Math.PI;
                ms.translate(Math.sin(circleProgress) * 0.5f * power, Math.cos(circleProgress) * 0.3 * power, 0.0);
                ms.mulPose(Axis.ZP.rotationDegrees(anim * 180.0f));
                break;
            }

            case "Wave": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, Math.sin(progress * Math.PI * 2.0f) * 0.3 * power, 0.0);
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f + anim * 45.0f * power));
                break;
            }

            case "360 Swing": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, 0.1, -0.1);
                ms.mulPose(Axis.XP.rotationDegrees(anim * 360.0f * power));
                break;
            }

            case "Vanilla": {
                ms.scale(scale, scale, scale);
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f * anim));
                break;
            }

            case "Flip": {
                ms.scale(scale, scale, scale);
                float angle = anim * 180.0f * power;
                ms.mulPose(Axis.ZP.rotationDegrees(angle));
                ms.translate(0.0, 0.0, -0.3f * anim);
                break;
            }

            case "Side": {
                ms.scale(scale, scale, scale);
                ms.translate(0.4f * anim, 0.0, -0.2f * anim);
                ms.mulPose(Axis.YP.rotationDegrees(-60.0f * anim));
                break;
            }

            case "Overhead": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, 0.5, 0.0);
                ms.mulPose(Axis.XP.rotationDegrees(-150.0f * anim));
                break;
            }

            case "Hammer": {
                ms.scale(scale, scale, scale);
                ms.translate(0.3, 0.3, -0.3);
                ms.mulPose(Axis.ZP.rotationDegrees(30.0f));
                ms.mulPose(Axis.XP.rotationDegrees(-120.0f * anim));
                break;
            }

            case "Chop": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, -0.2f * anim, -0.3f * anim);
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f * anim));
                break;
            }

            case "Arc": {
                ms.scale(scale, scale, scale);
                float a = anim * 90.0f;
                ms.mulPose(Axis.YP.rotationDegrees(a));
                ms.mulPose(Axis.XP.rotationDegrees(-a));
                break;
            }

            case "Double": {
                ms.scale(scale, scale, scale);
                float d = anim * 720.0f;
                ms.mulPose(Axis.XP.rotationDegrees(d));
                break;
            }

            case "Shake": {
                ms.scale(scale, scale, scale);
                float s = (float) Math.sin((System.currentTimeMillis() % 200L) / 200.0f * Math.PI * 2.0f) * 5.0f;
                ms.mulPose(Axis.ZP.rotationDegrees(s));
                break;
            }

            case "Shove": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, 0.0, -0.6f * anim);
                break;
            }

            case "Slash": {
                ms.scale(scale, scale, scale);
                ms.mulPose(Axis.YP.rotationDegrees(90.0f));
                ms.mulPose(Axis.XP.rotationDegrees(-120.0f * anim));
                break;
            }

            case "Thrust": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, 0.0, -1.0f * anim);
                break;
            }

            case "Smooth Down": {
                ms.scale(scale, scale, scale);
                ms.translate(0.0, -0.1f * anim, 0.0);
                float down = Mth.sin(progress * (float) Math.PI) * -65.0f * power;
                ms.mulPose(Axis.XP.rotationDegrees(down));
                break;
            }

            case "Smooth": {
                ms.scale(scale, scale, scale);
                float s = Mth.sqrt(progress);
                float back = Mth.sin(s * (float) Math.PI) * 25.0f * power;
                float down = Mth.sin(progress * (float) Math.PI) * -55.0f * power;
                ms.mulPose(Axis.XP.rotationDegrees(back + down));
                break;
            }

            default: {
                ms.scale(scale, scale, scale);
                ms.mulPose(Axis.XP.rotationDegrees(-90.0f * anim * power * 0.35f));
            }
        }

        if (applySpin) {
            switch (spinAxis.get()) {
                case "X" -> ms.mulPose(Axis.XP.rotationDegrees(currentSpin));
                case "Z" -> ms.mulPose(Axis.ZP.rotationDegrees(currentSpin));
                default -> ms.mulPose(Axis.YP.rotationDegrees(currentSpin));
            }
        }
    }
}
