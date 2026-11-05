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
import combatant.client.features.relations.CategoryRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.language.I18n;
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

    /** Independent local cosmetics. The original unsuffixed values remain the self profile. */
    public enum Target {
        SELF("self"), ENEMIES("enemies"), FRIENDS("friends"), STAFF("staff"), OTHERS("others");
        private final String key;
        Target(String key) { this.key = key; }
        public String key() { return key; }
        public String label() {
            return I18n.get("playermodel.target." + key);
        }
    }

    public final TargetProfile enemies = new TargetProfile(Target.ENEMIES);
    public final TargetProfile friends = new TargetProfile(Target.FRIENDS);
    public final TargetProfile staff = new TargetProfile(Target.STAFF);
    public final TargetProfile others = new TargetProfile(Target.OTHERS);

    public final class TargetProfile {
        public final Target target;
        public final BooleanValue replacement;
        public final ModeValue model;
        public final NumberValue<Float> offsetX, offsetY, offsetZ;
        public final NumberValue<Float> rotationX, rotationY, rotationZ;
        public final NumberValue<Float> scale, pivotX, pivotY, pivotZ;

        private TargetProfile(Target target) {
            this.target = target;
            String prefix = target.key() + "_";
            replacement = bool(prefix + "replacement", false);
            model = modelSetting(prefix + "model");
            offsetX = visibleWhen(num(prefix + "offset_x", 0f, -2f, 2f), replacement::get);
            offsetY = visibleWhen(num(prefix + "offset_y", 0f, -2f, 2f), replacement::get);
            offsetZ = visibleWhen(num(prefix + "offset_z", 0f, -2f, 2f), replacement::get);
            rotationX = visibleWhen(num(prefix + "rotation_x", 0f, -180f, 180f), replacement::get);
            rotationY = visibleWhen(num(prefix + "rotation_y", 0f, -180f, 180f), replacement::get);
            rotationZ = visibleWhen(num(prefix + "rotation_z", 0f, -180f, 180f), replacement::get);
            scale = visibleWhen(num(prefix + "scale", 1f, .1f, 4f), replacement::get);
            pivotX = visibleWhen(num(prefix + "pivot_x", 0f, -2f, 2f), replacement::get);
            pivotY = visibleWhen(num(prefix + "pivot_y", 0f, -2f, 2f), replacement::get);
            pivotZ = visibleWhen(num(prefix + "pivot_z", 0f, -2f, 2f), replacement::get);
        }

        public Matrix4f fitting() {
            return transform(offsetX.get(), offsetY.get(), offsetZ.get(),
                    rotationX.get(), rotationY.get(), rotationZ.get(), scale.get(),
                    pivotX.get(), pivotY.get(), pivotZ.get());
        }
    }

    public TargetProfile profile(Target target) {
        return switch (target) {
            case SELF -> null;
            case ENEMIES -> enemies;
            case FRIENDS -> friends;
            case STAFF -> staff;
            case OTHERS -> others;
        };
    }

    public ModeValue model(Target target) {
        TargetProfile profile = profile(target);
        return profile == null ? model : profile.model;
    }

    public BooleanValue replacement(Target target) {
        TargetProfile profile = profile(target);
        return profile == null ? replacement : profile.replacement;
    }

    public Matrix4f fitting(Target target) {
        TargetProfile profile = profile(target);
        return profile == null ? fitting() : profile.fitting();
    }

    /** Staff and explicit friends take precedence over enemies; all remaining players use Others. */
    public Target targetFor(AbstractClientPlayer player) {
        if (player == null) return Target.OTHERS;
        if (Minecraft.getInstance().player == player) return Target.SELF;
        String name = player.getGameProfile().name();
        return switch (CategoryRules.determine(name)) {
            case STAFF -> Target.STAFF;
            case FRIEND, BEDWARS_SELF -> Target.FRIENDS;
            case ENEMY, BEDWARS_ENEMY -> Target.ENEMIES;
            default -> Target.OTHERS;
        };
    }

    private ModeValue modelSetting() {
        return modelSetting("model");
    }

    private ModeValue modelSetting(String id) {
        ModeValue value = new ModeValue(id, "none", "none") {
            @Override public void fromJson(Object json) {
                // Preserve a saved selection when a resource pack temporarily removes its model.
                if (json instanceof String id && id.matches("[a-z0-9_./:-]{1,512}")) addOption(id);
                super.fromJson(json);
            }
        };
        setting(combatant.client.config.SettingDef.mode(id, value).disableI18n(false, true));
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
        return transform(offsetX.get(), offsetY.get(), offsetZ.get(),
                rotationX.get(), rotationY.get(), rotationZ.get(), scale.get(),
                pivotX.get(), pivotY.get(), pivotZ.get());
    }

    private static Matrix4f transform(float x, float y, float z, float rx, float ry, float rz,
                                      float scale, float px, float py, float pz) {
        float radians = (float) (Math.PI / 180);
        Matrix4f result = new Matrix4f().translation(x, y, z)
                .translate(px, py, pz)
                .rotateXYZ(rx * radians, ry * radians, rz * radians)
                .scale(scale).translate(-px, -py, -pz);
        if (!result.isFinite() || scale <= 0) throw new IllegalArgumentException("Invalid model fitting transform");
        return result;
    }

    @Override public void onDisable() { PlayerModels.invalidate(); }
}
