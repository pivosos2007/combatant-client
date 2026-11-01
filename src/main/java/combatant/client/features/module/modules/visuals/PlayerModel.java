/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.*;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.playermodel.PlayerModels;
import org.joml.Matrix4f;


@ModuleInfo(id = "playermodel", displayName = "PlayerModel", category = ModuleCategory.VISUALS,
        subcategory = ModuleSubcategory.COSMETIC, description = "module.playermodel.description")
public final class PlayerModel extends Module {
    public final ModeValue model = modelSetting();
    public final ModeValue textureResolution = textureResolutionSetting();
    public final ModeValue textureFiltering = mode("texture_filtering", "Linear", "Nearest", "Linear");
    public final BooleanValue replacement = bool("replacement", true);
    public final BooleanValue firstPersonHands = visibleWhen(bool("first_person_hands", false), replacement::get);
    public final ModeValue previewPose = mode("preview_pose", "Live", "Live", "Idle", "Walk", "Sneak", "Swim");
    public final NumberValue<Float> offsetX = num("offset_x", 0f, -2f, 2f);
    public final NumberValue<Float> offsetY = num("offset_y", 0f, -2f, 2f);
    public final NumberValue<Float> offsetZ = num("offset_z", 0f, -2f, 2f);
    public final NumberValue<Float> rotationX = num("rotation_x", 0f, -180f, 180f);
    public final NumberValue<Float> rotationY = num("rotation_y", 0f, -180f, 180f);
    public final NumberValue<Float> rotationZ = num("rotation_z", 0f, -180f, 180f);
    public final NumberValue<Float> scale = num("scale", 1f, 0.1f, 4f);
    public final NumberValue<Float> pivotX = num("pivot_x", 0f, -2f, 2f);
    public final NumberValue<Float> pivotY = num("pivot_y", 0f, -2f, 2f);
    public final NumberValue<Float> pivotZ = num("pivot_z", 0f, -2f, 2f);

    private ModeValue modelSetting() {
        ModeValue value = new ModeValue("model", "none", "none") {
            @Override public void fromJson(Object json) {
                // Preserve a saved selection when a resource pack temporarily removes its model.
                if (json instanceof String id && id.matches("[a-z0-9_./:-]{1,512}")) addOption(id);
                super.fromJson(json);
            }
        };
        setting(combatant.client.config.SettingDef.mode("model", value).disableI18n(false, true));
        return visibleWhen(value, () -> false);
    }

    private ModeValue textureResolutionSetting() {
        ModeValue value = new ModeValue("texture_resolution", "100%",
                "100%", "75%", "50%", "25%", "12.5%") {
            @Override public void fromJson(Object json) {
                // Migrate old absolute caps to relative quality levels instead of silently
                // ignoring existing configs. New saves always use relative percentages.
                if (json instanceof String old) {
                    json = switch (old) {
                        case "Original" -> "100%";
                        case "2048" -> "50%";
                        case "1024" -> "25%";
                        case "512" -> "12.5%";
                        default -> old;
                    };
                }
                super.fromJson(json);
            }
        };
        setting(combatant.client.config.SettingDef.mode("texture_resolution", value));
        return value;
    }

    public float textureScale() {
        return switch (textureResolution.get()) {
            case "75%" -> .75f;
            case "50%" -> .5f;
            case "25%" -> .25f;
            case "12.5%" -> .125f;
            default -> 1f;
        };
    }

    public Matrix4f fitting() {
        float radians = (float) (Math.PI / 180);
        Matrix4f result = new Matrix4f().translation(offsetX.get(), offsetY.get(), offsetZ.get())
                .translate(pivotX.get(), pivotY.get(), pivotZ.get())
                .rotateXYZ(rotationX.get() * radians, rotationY.get() * radians, rotationZ.get() * radians)
                .scale(scale.get()).translate(-pivotX.get(), -pivotY.get(), -pivotZ.get());
        if (!result.isFinite() || scale.get() <= 0) throw new IllegalArgumentException("Invalid model fitting transform");
        return result;
    }

    @Override public void onDisable() { PlayerModels.invalidate(); }
}
