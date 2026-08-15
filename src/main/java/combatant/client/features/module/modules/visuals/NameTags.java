/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.*;
import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import combatant.client.render.helpers.*;
import combatant.client.util.item.EnchantUtil;
import combatant.client.util.player.PlayerSkinResolver;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.SettingDef;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.module.modules.combat.PvpCooldowns;
import combatant.client.features.relations.CategoryService;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.mixininterface.IEntity;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.renderer.ui.ItemBatchRenderer;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.WorldTextRenderer;
import combatant.client.render.engine.world.WorldBillboardRenderer;
import combatant.client.render.engine.world.WorldUiPresentationService;
import combatant.client.util.item.EnchantMeta;
import combatant.client.util.item.EnchantRegistry;
import combatant.client.util.item.IllegalItemUtil;
import combatant.client.util.item.TopEnchantUtil;
import combatant.client.util.player.PlayerHealthResolver;
import combatant.client.util.player.effect.StatusEffectTracker;
import combatant.client.util.player.effect.StatusEffectView;
import combatant.client.util.pvp.CooldownRegistry;
import combatant.client.util.pvp.ItemCooldownSnapshot;
import combatant.client.util.pvp.PvpTargetState;
import combatant.client.util.pvp.opponents.OpponentCooldownManager;
import combatant.client.util.pvp.opponents.TotemPopCounter;
import combatant.client.util.pvp.opponents.TotemPopSnapshot;
import combatant.client.util.text.LegacyTextUtil;
import combatant.client.util.text.TextRenderUtil;

import java.util.*;

import static combatant.client.features.theme.Theme.theme;

//todo Description
@ModuleInfo(id = "nametags", displayName = "NameTags", aliases = {"nametag", "tags", "names"}, category = ModuleCategory.VISUALS)
public class NameTags extends Module {

    private static final int ICON_SIZE = 24;
    private static final int ICON_GAP = 5;
    private static final float EQUIP_ICON_SCALE = 1.10f;
    private static final int EFFECT_LINE_HEIGHT = 26;
    private static final int EFFECT_X_OFFSET = 6;
    private static final int EFFECT_LEVEL_COLOR = 0xFFBFC6D1;
    private static final int EFFECT_ROW_GAP = 10;
    private static final int EFFECT_ROW_ICON_GAP = 8;
    private static final int EFFECT_ROW_TEXT_GAP = 4;
    private static final int EFFECT_ROW_LEVEL_GAP = 3;
    private static final int EFFECT_ROW_TIME_GAP = 2;
    private static final float EFFECT_ROW_SCALE = 1.15f;
    private static final float NAMEPLATE_SCALE = 1.08f;
    private static final float SMALL_TEXT_SCALE = 0.82f;
    private static final int NAMEPLATE_PAD_X = 4;
    private static final int NAMEPLATE_PAD_Y = 2;
    private static final float MATTE_NAMEPLATE_RADIUS = 4.0f;
    private static final float MATTE_HEAD_MIN_SIZE = 12.0f;
    private static final float MATTE_HEAD_DIVIDER_GAP = 4.0f;
    private static final float MATTE_HEAD_DIVIDER_WIDTH = 1.0f;
    private static final float MATTE_HEAD_TEXT_GAP = 5.0f;
    private static final float TOTEM_BADGE_GAP = 4.0f;
    private static final float TOTEM_BADGE_DIVIDER_WIDTH = 1.0f;
    private static final float TOTEM_BADGE_DIVIDER_GAP = 4.0f;
    private static final float TOTEM_BADGE_MIN_WIDTH = 23.0f;
    private static final float TOTEM_BADGE_PAD_X = 3.0f;
    private static final float TOTEM_BADGE_ICON_SIZE = 10.0f;
    private static final float TOTEM_BADGE_TEXT_GAP = 2.0f;
    private static final float TOTEM_BADGE_TEXT_SCALE = 0.72f;
    private static final float TOTEM_BADGE_ANIM_SPEED = 10.0f;
    private static final float NAMEPLATE_SHADOW_BLUR = 4.6f;
    private static final float NAMEPLATE_SHADOW_INNER_ALPHA = 0.18f;
    private static final float NAMEPLATE_TEXT_SHADOW_OFFSET = 0.0f;
    private static final float ENCHANT_TEXT_MIN_GAP = 2.0f;
    private static final int ENCHANT_NAME_MAX_CODEPOINTS = 2;
    private static final WorldUiPresentationService.Policy WORLD_PRESENTATION_POLICY =
            WorldUiPresentationService.Policy.defaults();
    private static final int DIST_TEXT_GAP = 5;
    private static final int PING_TEXT_GAP = 4;
    private static final float ENCHANT_TEXT_SHADOW_OFFSET = 0.7f;
    private static final int HP_TEXT_GAP = 4;
    private static final int HP_GREEN = 0xFF55FF55;
    private static final int HP_CYAN = 0xFF55FFFF;
    private static final int HP_RED = 0xFFFF5555;
    private static final int INFO_COLOR_DEFAULT = 0xFFFF8A8A;
    private static final int EFFECT_LEVEL_STACK_COLOR = 0xFFFF4444;
    private static final int EFFECT_TIME_COLOR = 0xFFCCCCCC;
    private static final int OPP_TIME_COLOR = 0xFFFFFFFF;
    private static final int OPP_TIME_COLOR_GRACE = 0xFF4DA6FF;
    private static final int OPP_STACK_Y_OFFSET = 10;
    private static final int OPPONENT_DISTANCE = 80;
    private static final int EFFECT_CACHE_TICKS = 4;
    private static final int ENCHANT_CACHE_TICKS = 2;
    private static final ItemStack[] EMPTY_SLOTS = new ItemStack[0];
    private static ItemStack totemBadgeStack;

    private static ItemStack totemBadgeStack() {
        if (totemBadgeStack == null || totemBadgeStack.isEmpty()) {
            totemBadgeStack = new ItemStack(Items.TOTEM_OF_UNDYING);
        }
        return totemBadgeStack;
    }
    private static final String SETTING_TOGGLES = "toggles";
    private static final String SETTING_DURABILITY_START = "durability_start_percent";
    private static final String SETTING_DISTANCE_TOGGLES = "distance_toggles";
    private static final String SETTING_NAMEPLATE_CUTOFF = "nameplate_cutoff";
    private static final String SETTING_SLOTS_CUTOFF = "slots_cutoff";
    private static final String SETTING_EFFECTS_CUTOFF = "effects_cutoff";
    private static final String SETTING_ARMOR_ENCHANTS = "armor_enchants";
    private static final String SETTING_MELEE_ENCHANTS = "melee_enchants";
    private static final String SETTING_TOOLS_ENCHANTS = "tools_enchants";
    private static final String SETTING_BOW_ENCHANTS = "bow_enchants";
    private static final String SETTING_FISHING_ENCHANTS = "fishing_enchants";
    private static final String SETTING_TRIDENT_ENCHANTS = "trident_enchants";
    private static final String SETTING_CROSSBOW_ENCHANTS = "crossbow_enchants";
    private static final String SETTING_MACE_ENCHANTS = "mace_enchants";
    private static final String SETTING_OTHER_ENCHANTS = "other_enchants";
    private static final String SETTING_ILLEGAL_ENCHANT_COLOR = "illegal_enchant_color";
    private static final String SETTING_ILLEGAL_ENCHANTS_ALWAYS = "illegal_enchants_always";
    private static final String SETTING_TOP_ENCHANT_COLOR = "top_enchant_color";
    private static final String SETTING_TOP_ENCHANT_IGNORE_LIST = "top_enchant_ignore_list";
    private static final String SETTING_DEFAULT_NAME_COLOR = "default_name_color";
    private static final String SETTING_FRIEND_NAME_COLOR = "friend_name_color";
    private static final String SETTING_ENEMY_NAME_COLOR = "enemy_name_color";
    private static final String SETTING_STAFF_NAME_COLOR = "staff_name_color";
    private static final String SETTING_HP_DECIMALS = "hp_decimals";
    private static final String SETTING_HP_COLOR_MODE = "hp_color_mode";
    private static final String SETTING_NAMEPLATE_ALPHA = "nameplate_alpha";
    private static final String SETTING_PRESENTATION_MODE = "presentation_mode";
    private static final String SETTING_PLAYER_HEAD = "player_head";
    private static final String SETTING_NAMEPLATE_EXTRAS = "nameplate_extras";
    private static final float NAMEPLATE_EXTRA_LIFT = 8f;
    private static final int ESP_NAME_SECTION_LIFT = 9;
    private static final int SCREEN_NAMEPLATE_GAP = 7;
    private static final int EQUIPMENT_TO_NAMEPLATE_GAP = 7;
    private static final String SETTING_OPPONENT_APPLE_COOLDOWNS = "opponent_apple_cooldowns";
    private static final String SETTING_PVP_PREFIX = "pvp_prefix";
    private static final String SETTING_PVP_PREFIX_COLOR = "pvp_prefix_color";
    private final Minecraft mc = Minecraft.getInstance();
    private final ItemIdSetValue topIgnore = TopEnchantUtil.ignoreValue();
    private final List<RenderEntry> renderQueue = new ArrayList<>();
    private final List<WorldRenderEntry> worldRenderQueue = new ArrayList<>();
    private final List<ItemBatchRenderer.WorldItemRow> worldItemRows = new ArrayList<>();
    private final List<Player> playerBuffer = new ArrayList<>();
    private final Map<UUID, ItemStack[]> slotCache = new HashMap<>();
    private final Map<UUID, EffectCache> effectCache = new HashMap<>();
    private final Map<UUID, EnchantCache> enchantCache = new HashMap<>();
    private final Map<UUID, Float> totemPopAnimations = new HashMap<>();
    private final Map<UUID, Integer> totemPopLastCounts = new HashMap<>();
    private final HashSet<UUID> seenIds = new HashSet<>();
    private final BooleanMapValue toggles = group("nameTagsToggles", SETTING_TOGGLES, Map.of(
            "Show nameplate", true,
            "Show distance", true,
            "Show armor row", true,
            "Item durability bar", true,
            "Show effects", true,
            "Show self in 3rd person", false
    ));
    private final NumberValue<Integer> durabilityStartPercent =
            visibleWhen(num("nameTagsDurabilityStartPercent", SETTING_DURABILITY_START, 99, 0, 100),
                    () -> toggles.get("Item durability bar"));
    private final BooleanMapValue distanceToggles =
            visibleWhen(group("nameTagsDistanceToggles", SETTING_DISTANCE_TOGGLES, Map.of(
                    "nameplate", true,
                    "slots", true,
                    "effects", true
            )), () -> toggles.get("Show nameplate") || toggles.get("Show armor row") || toggles.get("Show effects"));
    private final NumberValue<Integer> plateDistance =
            visibleWhen(num("nameTagsPlateDistance", SETTING_NAMEPLATE_CUTOFF, 80, 5, 256),
                    () -> toggles.get("Show nameplate"));
    private final NumberValue<Integer> slotsDistance =
            visibleWhen(num("nameTagsSlotsDistance", SETTING_SLOTS_CUTOFF, 70, 5, 256),
                    () -> toggles.get("Show armor row"));
    private final NumberValue<Integer> effectsDistance =
            visibleWhen(num("nameTagsEffectsDistance", SETTING_EFFECTS_CUTOFF, 90, 5, 256),
                    () -> toggles.get("Show effects"));
    private final NumberValue<Integer> nameplateAlpha =
            visibleWhen(num("nameTagsNameplateAlpha", SETTING_NAMEPLATE_ALPHA, 180, 0, 255),
                    () -> toggles.get("Show nameplate"));
    private final EnumValue<PresentationMode> presentationMode =
            enumSetting("nameTagsPresentationMode", SETTING_PRESENTATION_MODE, PresentationMode.HYBRID, PresentationMode.values());
    private final BooleanValue playerHead =
            visibleWhen(bool("nameTagsPlayerHead", SETTING_PLAYER_HEAD, false),
                    () -> toggles.get("Show nameplate"));
    private final BooleanMapValue nameplateExtras = visibleWhen(group("nameTagsNameplateExtras", SETTING_NAMEPLATE_EXTRAS, new LinkedHashMap<>() {{
        put("Tab names", false);
        put("Ping", false);
    }}), () -> toggles.get("Show nameplate"));
    private final BooleanMapValue enchantArmor = group("nameTagsEnchantArmor", SETTING_ARMOR_ENCHANTS, Map.ofEntries(
            Map.entry("protection", true),
            Map.entry("fire_protection", false),
            Map.entry("feather_falling", false),
            Map.entry("blast_protection", false),
            Map.entry("projectile_protection", false),
            Map.entry("respiration", false),
            Map.entry("aqua_affinity", false),
            Map.entry("thorns", true),
            Map.entry("depth_strider", false),
            Map.entry("frost_walker", false),
            Map.entry("soul_speed", false),
            Map.entry("swift_sneak", false)
    ));
    private final BooleanMapValue enchantMelee = group("nameTagsEnchantMelee", SETTING_MELEE_ENCHANTS, Map.ofEntries(
            Map.entry("sharpness", true),
            Map.entry("smite", false),
            Map.entry("bane_of_arthropods", false),
            Map.entry("knockback", true),
            Map.entry("fire_aspect", true),
            Map.entry("looting", true),
            Map.entry("sweeping_edge", false)
    ));
    private final BooleanMapValue enchantTools = group("nameTagsEnchantTools", SETTING_TOOLS_ENCHANTS, Map.ofEntries(
            Map.entry("efficiency", true),
            Map.entry("silk_touch", true),
            Map.entry("fortune", true)
    ));
    private final BooleanMapValue enchantBow = group("nameTagsEnchantBow", SETTING_BOW_ENCHANTS, Map.ofEntries(
            Map.entry("power", true),
            Map.entry("punch", true),
            Map.entry("flame", false),
            Map.entry("infinity", false)
    ));
    private final BooleanMapValue enchantFishing = group("nameTagsEnchantFishing", SETTING_FISHING_ENCHANTS, Map.ofEntries(
            Map.entry("luck_of_the_sea", false),
            Map.entry("lure", false)
    ));
    private final BooleanMapValue enchantTrident = group("nameTagsEnchantTrident", SETTING_TRIDENT_ENCHANTS, Map.ofEntries(
            Map.entry("loyalty", false),
            Map.entry("impaling", false),
            Map.entry("riptide", false),
            Map.entry("channeling", false)
    ));
    private final BooleanMapValue enchantCrossbow = group("nameTagsEnchantCrossbow", SETTING_CROSSBOW_ENCHANTS, Map.ofEntries(
            Map.entry("multishot", false),
            Map.entry("quick_charge", false),
            Map.entry("piercing", true)
    ));
    private final BooleanMapValue enchantMace = group("nameTagsEnchantMace", SETTING_MACE_ENCHANTS, Map.ofEntries(
            Map.entry("density", true),
            Map.entry("breach", false),
            Map.entry("wind_burst", true)
    ));
    private final BooleanMapValue enchantOther = group("nameTagsEnchantOther", SETTING_OTHER_ENCHANTS, Map.ofEntries(
            Map.entry("mending", false),
            Map.entry("unbreaking", false),
            Map.entry("vanishing_curse", false),
            Map.entry("lunge", true)
    ));
    private final BooleanValue illegalEnchantsAlways = bool("nameTagsIllegalEnchantsAlways", SETTING_ILLEGAL_ENCHANTS_ALWAYS, true);
    private final BooleanValue opponentAppleCooldowns = bool("nameTagsOpponentAppleCooldowns", SETTING_OPPONENT_APPLE_COOLDOWNS, true);
    private final BooleanValue totemPopCounter = TotemPopCounter.enabledValue();
    private final NumberValue<Integer> totemPopResetSeconds = TotemPopCounter.resetAfterSecondsValue();
    private final BooleanValue pvpPrefix = bool("nameTagsPvpPrefix", SETTING_PVP_PREFIX, false);
    private final RGBColorValue pvpPrefixColor =
            visibleWhen(colorNoAlpha("nameTagsPvpPrefixColor", SETTING_PVP_PREFIX_COLOR, "#FF0000"), pvpPrefix::get);
    private final BooleanValue hpDecimals =
            visibleWhen(bool("nameTagsHpDecimals", SETTING_HP_DECIMALS, false),
                    () -> toggles.get("Show nameplate"));
    private final ModeValue hpColorMode =
            visibleWhen(modeSetting("nameTagsHpColorMode", SETTING_HP_COLOR_MODE, "Static", "Static", "Gradient"),
                    () -> toggles.get("Show nameplate"));
    private transient TextRenderer cachedNameTr;
    private transient TextRenderer cachedHpTr;
    private transient TextRenderer cachedLevelTr;
    private transient TextRenderer cachedTimeTr;
    private transient float foregroundAlpha = 1.0f;

    {
        setting(SettingDef.bool(totemPopCounter)
                .common(CommonSettingSchemas.TOTEM_POP_COUNTER.commonI18nKey()));
        setting(SettingDef.number(totemPopResetSeconds)
                .common(CommonSettingSchemas.TOTEM_POP_RESET_SECONDS.commonI18nKey())
                .visibleWhen(totemPopCounter::get));
        setting(SettingDef.colorNoAlpha(SETTING_ILLEGAL_ENCHANT_COLOR, IllegalItemUtil.illegalColorValue()));
        setting(SettingDef.colorNoAlpha(SETTING_TOP_ENCHANT_COLOR, TopEnchantUtil.topColorValue()));
        setting(SettingDef.textList(SETTING_TOP_ENCHANT_IGNORE_LIST, topIgnore, TextListSetting.PickerMode.ENCHANTMENTS));
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static String formatNameplateHp(String hp) {
        return hp == null ? "" : hp;
    }

    private static int clampAlpha(int a) {
        if (a < 0) return 0;
        if (a > 255) return 255;
        return a;
    }

    private static int equipmentSignature(ItemStack[] slots) {
        if (slots == null || slots.length == 0) return 1;
        int hash = 1;
        for (ItemStack stack : slots) {
            hash = 31 * hash + itemSignature(stack);
        }
        return hash;
    }

    private static int itemSignature(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int hash = 17;
        hash = 31 * hash + Item.getId(stack.getItem());
        hash = 31 * hash + stack.getCount();
        hash = 31 * hash + stack.getDamageValue();
        hash = 31 * hash + stack.getComponents().hashCode();
        return hash;
    }

    private static int scaleRow(int value) {
        return Math.round(value * EFFECT_ROW_SCALE);
    }

    private static int equipIconSize() {
        return Math.round(ICON_SIZE * EQUIP_ICON_SCALE);
    }

    private static int equipIconGap() {
        return Math.round(ICON_GAP * EQUIP_ICON_SCALE);
    }

    private static int resolvePingColor(int ping) {
        if (ping < 0) {
            return 0xFFD0D0D0;
        }
        if (ping <= 70) {
            return 0xFF55FF55;
        }
        if (ping >= 200) {
            return 0xFFFF5555;
        }

        if (ping <= 135) {
            float t = (ping - 70.0f) / 65.0f;
            return mixColor(0xFF55FF55, 0xFFFFFF55, t);
        }

        float t = (ping - 135.0f) / 65.0f;
        return mixColor(0xFFFFFF55, 0xFFFF5555, t);
    }

    private static int mixColor(int start, int end, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int sa = (start >>> 24) & 0xFF;
        int sr = (start >>> 16) & 0xFF;
        int sg = (start >>> 8) & 0xFF;
        int sb = start & 0xFF;
        int ea = (end >>> 24) & 0xFF;
        int er = (end >>> 16) & 0xFF;
        int eg = (end >>> 8) & 0xFF;
        int eb = end & 0xFF;
        int a = (int) (sa + (ea - sa) * t + 0.5f);
        int r = (int) (sr + (er - sr) * t + 0.5f);
        int g = (int) (sg + (eg - sg) * t + 0.5f);
        int b = (int) (sb + (eb - sb) * t + 0.5f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static boolean beginTextRenderer(TextRenderer textRenderer, double scale) {
        if (textRenderer == null || textRenderer.isBuilding()) return false;
        textRenderer.begin(scale);
        return true;
    }

    private static void endTextRenderer(TextRenderer textRenderer, boolean started) {
        if (!started || textRenderer == null || !textRenderer.isBuilding()) return;
        textRenderer.end();
    }

    private static int lerpColor(int a, int b, float t) {
        int aA = (a >>> 24) & 0xFF;
        int aR = (a >>> 16) & 0xFF;
        int aG = (a >>> 8) & 0xFF;
        int aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF;
        int bR = (b >>> 16) & 0xFF;
        int bG = (b >>> 8) & 0xFF;
        int bB = b & 0xFF;
        int oA = Math.round(lerp(aA, bA, t));
        int oR = Math.round(lerp(aR, bR, t));
        int oG = Math.round(lerp(aG, bG, t));
        int oB = Math.round(lerp(aB, bB, t));
        return (oA << 24) | (oR << 16) | (oG << 8) | oB;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static boolean hasOpponentCooldowns(UUID playerId) {
        if (playerId == null) return false;
        for (Item item : CooldownRegistry.trackedItems()) {
            if (OpponentCooldownManager.snapshot(playerId, item).visible()) {
                return true;
            }
        }
        return false;
    }

    private static String secondsTextInt(float secondsLeft) {
        int sec = Math.max(0, (int) Math.ceil(secondsLeft));
        return String.valueOf(sec);
    }

    private static boolean pushNameplateScissor(NameplateLayout layout) {
        if (layout == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;

        int sw = mc.getWindow().getScreenWidth();
        int sh = mc.getWindow().getScreenHeight();

        int x1 = Math.max(0, (int) Math.floor(layout.bgX()));
        int y1 = Math.max(0, (int) Math.floor(layout.bgY()));
        int x2 = Math.min(sw, (int) Math.ceil(layout.bgX() + layout.bgW()));
        int y2 = Math.min(sh, (int) Math.ceil(layout.bgY() + layout.bgH()));

        if (x2 <= x1 || y2 <= y1) return false;
        return ScissorFunction.pushRawRect(x1, y1, x2, y2);
    }

    private static int computeOpponentStackX(NameplateLayout layout, int centerX) {
        if (layout != null) {
            return (int) Math.ceil(layout.bgX() + layout.bgW()) + EFFECT_X_OFFSET;
        }
        return centerX + (ICON_SIZE + ICON_GAP) * 2 + EFFECT_X_OFFSET;
    }

    private static int computeOpponentStackY(int nameY) {
        return nameY + OPP_STACK_Y_OFFSET;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.AFTER_MISC_OVERLAYS;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || mc.level == null || mc.player == null) return;
        if (presentationMode.get() == PresentationMode.TWO_D) return;

        ensureFontCache(TextRenderer.get());
        StatusEffectView.sync(mc);
        TotemPopCounter.tick();
        float dt = AnimationUtility.deltaTime();

        worldRenderQueue.clear();
        worldItemRows.clear();
        seenIds.clear();
        Vec3 cameraPos = mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null
                ? mc.gameRenderer.mainCamera().position()
                : mc.player.position();
        WorldBillboardRenderer.Basis basis = WorldBillboardRenderer.currentBasis();

        for (Player player : mc.level.players()) {
            if (!shouldRender(player)) continue;
            seenIds.add(player.getUUID());

            TotemPopSnapshot totemPopSnapshot = TotemPopCounter.snapshot(player.getUUID());
            float totemPopAnimation = updateTotemPopAnimation(player, totemPopSnapshot, dt);
            int totemPopCount = resolveTotemPopCount(player, totemPopSnapshot, totemPopAnimation);

            Vec3 pos = obtainEntityLerpedPos(player, tickDelta);
            double dist = cameraPos.distanceTo(pos);
            WorldUiPresentationService.Snapshot presentation = resolvePresentation(dist);
            float alpha = presentation.worldAlpha();
            if (alpha <= 0.001f) continue;

            boolean renderNameplate = toggles.get("Show nameplate")
                    && (!distanceToggles.get("nameplate") || dist <= plateDistance.get());
            boolean renderSlots = toggles.get("Show armor row")
                    && (!distanceToggles.get("slots") || dist <= slotsDistance.get());
            boolean renderEffects = toggles.get("Show effects")
                    && (!distanceToggles.get("effects") || dist <= effectsDistance.get());
            if (!renderNameplate && !renderSlots && !renderEffects) continue;

            double adaptiveScale = presentation.worldUnitsPerPixel();
            Vec3 anchor = new Vec3(pos.x, pos.y + player.getBbHeight() + 0.68, pos.z);

            ItemStack[] slots = renderSlots ? getCachedEquipmentSlots(player) : EMPTY_SLOTS;
            List<List<EnchantLine>> enchantLines = List.of();
            int maxEnchantLines = 0;
            if (renderSlots) {
                EnchantCache cache = resolveEnchantCache(player, slots);
                enchantLines = cache.lines;
                maxEnchantLines = cache.maxLines;
            }

            List<MobEffectInstance> effects = renderEffects ? resolveEffects(player) : List.of();
            boolean renderTotemBadge = renderNameplate
                    && totemPopCounter.get()
                    && totemPopCount > 0
                    && totemPopAnimation > 0.001f;
            ItemStack[] worldItems = slots;
            if (renderTotemBadge) {
                worldItems = Arrays.copyOf(slots, slots.length + 1);
                worldItems[slots.length] = totemBadgeStack();
            }

            worldRenderQueue.add(new WorldRenderEntry(
                    player, anchor, dist, adaptiveScale, alpha,
                    renderNameplate, renderSlots, renderEffects,
                    slots, enchantLines, maxEnchantLines, effects,
                    totemPopCount, totemPopAnimation, worldItems
            ));
        }

        worldRenderQueue.sort(Comparator.comparingDouble(WorldRenderEntry::distance).reversed());
        for (int i = 0; i < worldRenderQueue.size(); i++) {
            WorldRenderEntry entry = worldRenderQueue.get(i);
            worldItemRows.add(new ItemBatchRenderer.WorldItemRow(entry.player(), entry.worldItems(), i * 31));
        }

        List<ItemBatchRenderer.WorldItemSprite[]> itemSprites =
                ItemBatchRenderer.resolveWorldItemSprites(worldItemRows);
        for (int i = 0; i < worldRenderQueue.size(); i++) {
            ItemBatchRenderer.WorldItemSprite[] rowSprites = i < itemSprites.size()
                    ? itemSprites.get(i)
                    : new ItemBatchRenderer.WorldItemSprite[0];
            renderWorldNameTag(renderer, basis, worldRenderQueue.get(i), rowSprites);
        }

        if (presentationMode.get() == PresentationMode.THREE_D) pruneCaches();
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor ctx, float tickDelta) {
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        if (presentationMode.get() == PresentationMode.THREE_D) {
            renderQueue.clear();
            return;
        }

        ensureFontCache(textRenderer);

        StatusEffectView.sync(mc);
        TotemPopCounter.tick();
        boolean advanceTotemAnimationHere = presentationMode.get() == PresentationMode.TWO_D;
        float dt = advanceTotemAnimationHere ? AnimationUtility.deltaTime() : 0.0f;

        renderQueue.clear();
        playerBuffer.clear();
        seenIds.clear();

        boolean wantNameplate = toggles.get("Show nameplate");
        boolean wantSlots = toggles.get("Show armor row");
        boolean wantEffects = toggles.get("Show effects");
        boolean wantOpponent = opponentAppleCooldowns.get() && isOpponentAppleTrackingEnabled();
        if (!wantNameplate && !wantSlots && !wantEffects && !wantOpponent) return;

        boolean hasUnlimited = false;
        double maxCutoff = 0.0;
        if (wantNameplate) {
            if (distanceToggles.get("nameplate")) {
                maxCutoff = Math.max(maxCutoff, plateDistance.get());
            } else {
                hasUnlimited = true;
            }
        }
        if (wantSlots) {
            if (distanceToggles.get("slots")) {
                maxCutoff = Math.max(maxCutoff, slotsDistance.get());
            } else {
                hasUnlimited = true;
            }
        }
        if (wantEffects) {
            if (distanceToggles.get("effects")) {
                maxCutoff = Math.max(maxCutoff, effectsDistance.get());
            } else {
                hasUnlimited = true;
            }
        }
        if (wantOpponent) {
            double oppCutoff = OPPONENT_DISTANCE;
            if (oppCutoff > 0) {
                maxCutoff = Math.max(maxCutoff, oppCutoff);
            } else {
                hasUnlimited = true;
            }
        }

        Vec3 cameraPos = null;
        if (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            cameraPos = mc.gameRenderer.mainCamera().position();
        }
        if (cameraPos == null && mc.player != null) {
            cameraPos = mc.player.position();
        }

        for (Player player : mc.level.players()) {
            if (!shouldRender(player)) continue;
            playerBuffer.add(player);
            seenIds.add(player.getUUID());
        }

        if (cameraPos != null && playerBuffer.size() > 1) {
            double camX = cameraPos.x;
            double camY = cameraPos.y;
            double camZ = cameraPos.z;
            playerBuffer.sort(Comparator.comparingDouble((Player p) -> p.distanceToSqr(camX, camY, camZ)).reversed());
        }

        for (Player player : playerBuffer) {
            Vec3 pos = obtainEntityLerpedPos(player, tickDelta);
            double dist = (cameraPos == null) ? 0.0 : cameraPos.distanceTo(pos);
            if (!hasUnlimited && maxCutoff > 0.0 && dist > maxCutoff) continue;
            float presentationAlpha = resolvePresentation(dist).screenAlpha();
            if (presentationAlpha <= 0.001f) continue;

            AABB box = player.getBoundingBox().move(pos.subtract(player.position()));
            Vec3 anchor = new Vec3(box.getCenter().x, box.maxY + 0.5, box.getCenter().z);
            ScreenRect rect = projectBoxScreen(box, tickDelta);
            int centerX;
            int anchorY;
            if (rect != null) {
                centerX = (int) Math.round((rect.minX + rect.maxX) * 0.5);
                anchorY = (int) Math.round(rect.minY);
            } else {
                Vec3 screen = ScreenProjection.worldToScreen(anchor, tickDelta);
                if (screen == null) continue;
                centerX = (int) screen.x;
                anchorY = (int) screen.y;
            }
            int nameY = computeNameY(rect, anchorY);
            int sectionLift = Math.round(NAMEPLATE_EXTRA_LIFT) + computeEspNameSectionLift(player);
            nameY -= sectionLift;

            boolean renderSlots = wantSlots;
            if (renderSlots && distanceToggles.get("slots") && dist > slotsDistance.get()) renderSlots = false;

            boolean renderNameplate = wantNameplate;
            if (renderNameplate && distanceToggles.get("nameplate") && dist > plateDistance.get())
                renderNameplate = false;

            boolean renderEffects = wantEffects;
            if (renderEffects && distanceToggles.get("effects") && dist > effectsDistance.get()) renderEffects = false;

            TotemPopSnapshot totemPopSnapshot = TotemPopCounter.snapshot(player.getUUID());
            float totemPopAnimation = advanceTotemAnimationHere
                    ? updateTotemPopAnimation(player, totemPopSnapshot, dt)
                    : totemPopAnimations.getOrDefault(player.getUUID(), totemPopSnapshot.visible() ? 1.0f : 0.0f);
            int totemPopCount = resolveTotemPopCount(player, totemPopSnapshot, totemPopAnimation);

            LabelInfo labelInfo = renderNameplate ? buildLabelInfo(player) : null;
            int infoColor = labelInfo != null ? labelInfo.infoColor() : INFO_COLOR_DEFAULT;
            NameplateLayout nameplateLayout = null;
            if (renderNameplate && labelInfo != null) {
                nameplateLayout = buildNameplateLayout(
                        player,
                        cachedNameTr != null ? cachedNameTr : textRenderer,
                        cachedHpTr != null ? cachedHpTr : textRenderer,
                        labelInfo,
                        dist,
                        centerX,
                        nameY,
                        totemPopCount,
                        totemPopAnimation
                );
            }

            ItemStack[] slots = renderSlots ? getCachedEquipmentSlots(player) : EMPTY_SLOTS;
            List<List<EnchantLine>> enchantLines = List.of();
            int maxEnchantLines = 0;
            if (renderSlots) {
                EnchantCache cache = resolveEnchantCache(player, slots);
                enchantLines = cache.lines;
                maxEnchantLines = cache.maxLines;
            }
            List<MobEffectInstance> effects = renderEffects ? resolveEffects(player) : List.of();

            int itemsY = computeItemsY(rect, nameplateLayout, nameY);
            renderQueue.add(new RenderEntry(player, dist, centerX, nameY, itemsY,
                    renderSlots, renderNameplate, renderEffects, labelInfo, infoColor, nameplateLayout,
                    slots, enchantLines, maxEnchantLines, effects, totemPopCount, totemPopAnimation,
                    presentationAlpha));
        }

        pruneCaches();
    }

    @Override
    public void onRenderHudEngineForeground(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        ensureFontCache(textRenderer);

        for (RenderEntry entry : renderQueue) {
            renderNameTagForeground(renderer, ctx, textRenderer, entry);
        }
    }

    private boolean shouldRender(Player player) {
        if (player == null || mc.player == null) return false;
        if (player == mc.player) {
            CameraType p = mc.options.getCameraType();
            if (p == null || p.isFirstPerson()) return false;
            if (!toggles.get("Show self in 3rd person")) return false;
        }
        String name = player.getGameProfile().name();
        return !"FreeCamera".equals(name);
    }

    private int computeNameY(ScreenRect rect, int anchorY) {
        // Screen-space layout must not depend on projected entity height or distance.
        // Distance changes only where the entity projects, never the local spacing of the tag.
        if (rect != null) {
            return Math.round((float) rect.minY) - SCREEN_NAMEPLATE_GAP;
        }
        return anchorY - SCREEN_NAMEPLATE_GAP;
    }

    private int computeEspNameSectionLift(Player player) {
        ESP esp = Modules.get(ESP.class);
        if (esp == null || !esp.shouldLiftNameTagsSection(player)) return 0;
        return ESP_NAME_SECTION_LIFT;
    }

    private int computeItemsY(ScreenRect rect, NameplateLayout nameplateLayout, int nameY) {
        int iconSize = equipIconSize();
        // Match the 3D local layout exactly when a nameplate exists. This keeps the
        // armor/effects stack rigid instead of squeezing it as the projected box shrinks.
        if (nameplateLayout != null) {
            return Math.round(nameplateLayout.bgY() - iconSize - EQUIPMENT_TO_NAMEPLATE_GAP);
        }
        if (rect != null) {
            return Math.round((float) rect.minY) - iconSize - SCREEN_NAMEPLATE_GAP - EQUIPMENT_TO_NAMEPLATE_GAP;
        }
        return nameY - iconSize - EQUIPMENT_TO_NAMEPLATE_GAP;
    }

    private WorldUiPresentationService.Snapshot resolvePresentation(double distance) {
        WorldUiPresentationService.Mode mode = switch (presentationMode.get()) {
            case TWO_D -> WorldUiPresentationService.Mode.SCREEN;
            case THREE_D -> WorldUiPresentationService.Mode.WORLD;
            case HYBRID -> WorldUiPresentationService.Mode.HYBRID;
        };
        double projectionYScale = RenderState.worldProjection.m11();
        double logicalHeight = mc != null && mc.getWindow() != null
                ? mc.getWindow().getGuiScaledHeight()
                : 0.0;
        return WorldUiPresentationService.resolve(
                mode, distance, WORLD_PRESENTATION_POLICY, projectionYScale, logicalHeight);
    }

    private void renderWorldNameTag(Renderer3D renderer,
                                    WorldBillboardRenderer.Basis basis,
                                    WorldRenderEntry entry,
                                    ItemBatchRenderer.WorldItemSprite[] itemSprites) {
        Player player = entry.player();
        TextRenderer nameRenderer = cachedNameTr != null ? cachedNameTr : TextRenderer.get();
        TextRenderer hpRenderer = cachedHpTr != null ? cachedHpTr : nameRenderer;
        double itemY = -equipIconSize() * 0.5;
        NameplateLayout layout = null;

        if (entry.renderNameplate()) {
            LabelInfo info = buildLabelInfo(player);
            layout = buildNameplateLayout(
                    player,
                    nameRenderer,
                    hpRenderer,
                    info,
                    entry.distance(),
                    0,
                    0,
                    entry.totemPopCount(),
                    entry.totemPopAnimation()
            );

            if (layout != null) {
                float materialAlpha = (clampAlpha(nameplateAlpha.get()) / 255.0f) * entry.alpha();
                double radius = Math.min(MATTE_NAMEPLATE_RADIUS, Math.max(2.5, layout.bgH() * 0.30));
                WorldBillboardRenderer.mattePlate(
                        renderer, basis, entry.anchor(),
                        layout.bgX(), layout.bgY(), layout.bgW(), layout.bgH(),
                        radius, NAMEPLATE_SHADOW_BLUR, entry.worldScale(), materialAlpha
                );

                drawWorldNameplateHead(renderer, basis, entry, layout);

                double cursorX = layout.nameX();
                if (!layout.pingText().isEmpty()) {
                    drawWorldText(renderer, basis, nameRenderer, layout.pingText(), entry.anchor(),
                            cursorX, 0.0, NAMEPLATE_SCALE, entry.worldScale(),
                            resolvePingColor(info.ping()), entry.alpha());
                    cursorX += layout.pingWidth() + layout.pingGap();
                }

                String prefix = info.pvpPrefix();
                if (!prefix.isEmpty()) {
                    drawWorldText(renderer, basis, nameRenderer, prefix, entry.anchor(),
                            cursorX, 0.0, NAMEPLATE_SCALE, entry.worldScale(),
                            pvpPrefixColor.getArgb(), entry.alpha());
                    cursorX += worldTextWidth(nameRenderer, prefix, NAMEPLATE_SCALE);
                }

                if (shouldUseTabNames()) {
                    drawWorldStyledText(renderer, basis, nameRenderer, info.styledName(), entry.anchor(),
                            cursorX, 0.0, NAMEPLATE_SCALE, entry.worldScale(),
                            CategoryService.getColor(player), entry.alpha());
                } else {
                    drawWorldText(renderer, basis, nameRenderer, info.name(), entry.anchor(),
                            cursorX, 0.0, NAMEPLATE_SCALE, entry.worldScale(),
                            CategoryService.getColor(player), entry.alpha());
                }

                String hp = formatNameplateHp(info.hp());
                if (!hp.isEmpty()) {
                    double hpX = layout.nameX()
                            + layout.pingWidth() + layout.pingGap()
                            + layout.nameWidth() + layout.hpGap();
                    drawWorldText(renderer, basis, hpRenderer, hp, entry.anchor(),
                            hpX, 0.0, NAMEPLATE_SCALE, entry.worldScale(),
                            info.infoColor(), entry.alpha());
                }

                if (!layout.distText().isEmpty()) {
                    double distX = layout.nameX()
                            + layout.pingWidth() + layout.pingGap()
                            + layout.nameWidth() + layout.hpGap() + layout.hpWidth()
                            + (layout.hpWidth() > 0 ? DIST_TEXT_GAP : 0);
                    drawWorldText(renderer, basis, nameRenderer, layout.distText(), entry.anchor(),
                            distX, 0.0, NAMEPLATE_SCALE, entry.worldScale(),
                            0xFFD0D0D0, entry.alpha());
                }

                drawWorldTotemBadge(renderer, basis, entry, layout, itemSprites, hpRenderer);
                itemY = layout.bgY() - equipIconSize() - EQUIPMENT_TO_NAMEPLATE_GAP;
            }
        }

        int iconSize = equipIconSize();
        EquipmentLayout equipment = entry.renderSlots()
                ? computeEquipmentLayout(
                        nameRenderer, entry.slots(), entry.enchantLines(),
                        0.0, iconSize, equipIconGap(), SMALL_TEXT_SCALE
                )
                : EquipmentLayout.empty();

        if (entry.renderSlots()) {
            double enchantHeight = WorldTextRenderer.measure(
                    nameRenderer, "Ag", SMALL_TEXT_SCALE, false).height();

            for (int i = 0; i < entry.slots().length; i++) {
                ItemStack stack = entry.slots()[i];
                if (stack == null || stack.isEmpty()) continue;

                double itemX = equipment.centers()[i] - iconSize * 0.5;
                if (i < itemSprites.length && itemSprites[i] != null) {
                    WorldBillboardRenderer.item(
                            renderer, basis, entry.anchor(), itemSprites[i],
                            itemX, itemY, iconSize, entry.worldScale(), entry.alpha()
                    );
                }

                List<EnchantLine> lines = i < entry.enchantLines().size()
                        ? entry.enchantLines().get(i)
                        : List.of();
                for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                    EnchantLine line = lines.get(lineIndex);
                    double width = worldTextWidth(nameRenderer, line.text(), SMALL_TEXT_SCALE);
                    double lineY = itemY - 3.0 - enchantHeight * (lineIndex + 1);
                    drawWorldText(renderer, basis, nameRenderer, line.text(), entry.anchor(),
                            equipment.centers()[i] - width * 0.5, lineY,
                            SMALL_TEXT_SCALE, entry.worldScale(), line.color(), entry.alpha());
                }
            }
        }

        if (entry.renderEffects() && !entry.effects().isEmpty()) {
            drawWorldEffects(renderer, basis, entry, itemY, nameRenderer);
        }
    }

    private void drawWorldNameplateHead(Renderer3D renderer,
                                        WorldBillboardRenderer.Basis basis,
                                        WorldRenderEntry entry,
                                        NameplateLayout layout) {
        if (layout == null || !layout.renderHead()) return;
        if (!(entry.player() instanceof AbstractClientPlayer clientPlayer)) return;

        Identifier skin = PlayerSkinResolver.resolvePlayerSkin(clientPlayer);
        if (skin == null) return;

        float alpha = (clampAlpha(nameplateAlpha.get()) / 255.0f) * entry.alpha();
        if (alpha <= 0.001f) return;

        double inset = layout.headSize() * 0.06;
        double x = layout.headX() + inset;
        double y = layout.headY() + inset;
        double size = Math.max(1.0, layout.headSize() - inset * 2.0);

        WorldBillboardRenderer.texture(
                renderer, basis, entry.anchor(), skin,
                x, y, size, size,
                8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f,
                entry.worldScale(), 0xFFFFFFFF, alpha
        );
        WorldBillboardRenderer.texture(
                renderer, basis, entry.anchor(), skin,
                x, y, size, size,
                40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f,
                entry.worldScale(), 0xFFFFFFFF, alpha
        );

        WorldBillboardRenderer.roundedRect(
                renderer, basis, entry.anchor(),
                layout.dividerX(), layout.dividerY(), layout.dividerW(), layout.dividerH(),
                Math.min(0.5, layout.dividerW() * 0.5), entry.worldScale(),
                MatteHudStyle.withAlpha(theme().textMuted(), Math.round(110.0f * alpha))
        );
    }

    private void drawWorldTotemBadge(Renderer3D renderer,
                                     WorldBillboardRenderer.Basis basis,
                                     WorldRenderEntry entry,
                                     NameplateLayout layout,
                                     ItemBatchRenderer.WorldItemSprite[] itemSprites,
                                     TextRenderer fallbackRenderer) {
        if (layout == null || !layout.renderTotemBadge()) return;
        if (entry.totemPopCount() <= 0 || entry.totemPopAnimation() <= 0.001f) return;

        float anim = AnimationUtility.easeInOutCubic(entry.totemPopAnimation());
        float materialAlpha = (clampAlpha(nameplateAlpha.get()) / 255.0f) * anim * entry.alpha();
        if (materialAlpha <= 0.001f) return;

        WorldBillboardRenderer.roundedRect(
                renderer, basis, entry.anchor(),
                layout.totemDividerX(), layout.totemDividerY(),
                layout.totemDividerW(), layout.totemDividerH(),
                Math.min(0.5, layout.totemDividerW() * 0.5), entry.worldScale(),
                MatteHudStyle.withAlpha(theme().textMuted(), Math.round(110.0f * materialAlpha))
        );

        double iconScale = 0.86 + 0.14 * anim;
        double iconSize = Math.max(1.0, layout.totemIconSize() * iconScale);
        double iconX = layout.totemBadgeX();
        double iconY = layout.totemBadgeY() + (layout.totemBadgeH() - iconSize) * 0.5;
        int spriteIndex = entry.slots().length;
        if (spriteIndex < itemSprites.length && itemSprites[spriteIndex] != null) {
            WorldBillboardRenderer.item(
                    renderer, basis, entry.anchor(), itemSprites[spriteIndex],
                    iconX, iconY, iconSize, entry.worldScale(), entry.alpha() * anim
            );
        }

        String text = "x" + entry.totemPopCount();
        TextRenderer badgeRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, fallbackRenderer);
        double textScale = TOTEM_BADGE_TEXT_SCALE * (0.94 + 0.06 * anim);
        double textX = iconX + iconSize + TOTEM_BADGE_TEXT_GAP;
        double textH = WorldTextRenderer.measure(badgeRenderer, "Ag", textScale, false).height();
        double textY = layout.totemBadgeY() + (layout.totemBadgeH() - textH) * 0.5 - 0.1;
        int textColor = HudRenderUtil.setAlpha(theme().textPrimary(), Math.round(255.0f * materialAlpha));
        drawWorldText(
                renderer, basis, badgeRenderer, text, entry.anchor(),
                textX, textY, textScale, entry.worldScale(), textColor, 1.0f
        );
    }

    private void drawWorldEffects(Renderer3D renderer,
                                  WorldBillboardRenderer.Basis basis,
                                  WorldRenderEntry entry,
                                  double itemY,
                                  TextRenderer fallbackRenderer) {
        TextureAtlas atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
        if (atlas == null || atlas.location() == null) return;

        int count = entry.effects().size();
        int iconSize = scaleRow(ICON_SIZE);
        int gap = scaleRow(EFFECT_ROW_ICON_GAP);
        int rowGap = scaleRow(EFFECT_ROW_GAP);
        int rowWidth = count * iconSize + Math.max(0, count - 1) * gap;
        double startX = -rowWidth * 0.5;
        int enchantLift = entry.renderSlots()
                ? scaleRow(computeEnchantLift(fallbackRenderer, entry.maxEnchantLines()))
                : 0;
        double iconY = itemY - iconSize - rowGap - enchantLift;

        TextRenderer levelRenderer = cachedLevelTr != null ? cachedLevelTr : fallbackRenderer;
        TextRenderer timeRenderer = cachedTimeTr != null ? cachedTimeTr : fallbackRenderer;
        double rowTextScale = SMALL_TEXT_SCALE * EFFECT_ROW_SCALE;
        double levelHeight = WorldTextRenderer.measure(levelRenderer, "Ag", rowTextScale, false).height();
        double timeHeight = WorldTextRenderer.measure(timeRenderer, "Ag", rowTextScale, false).height();
        int levelGap = scaleRow(EFFECT_ROW_LEVEL_GAP);
        int timeGap = scaleRow(EFFECT_ROW_TIME_GAP);

        for (int i = 0; i < count; i++) {
            MobEffectInstance effect = entry.effects().get(i);
            Identifier spriteId = Hud.getMobEffectSprite(effect.getEffect());
            TextureAtlasSprite sprite = spriteId != null ? atlas.getSprite(spriteId) : null;
            double iconX = startX + i * (iconSize + gap);

            if (sprite != null) {
                WorldBillboardRenderer.texture(
                        renderer, basis, entry.anchor(), atlas.location(),
                        iconX, iconY, iconSize, iconSize,
                        sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                        entry.worldScale(), 0xFFFFFFFF, entry.alpha()
                );
            }

            int level = effect.getAmplifier() + 1;
            String duration = formatDuration(entry.player().getId(), effect);
            if (!duration.isEmpty()) {
                double width = worldTextWidth(timeRenderer, duration, rowTextScale);
                double y = level > 1
                        ? iconY - levelHeight - levelGap - timeHeight - timeGap
                        : iconY - timeHeight - levelGap;
                drawWorldText(
                        renderer, basis, timeRenderer, duration, entry.anchor(),
                        iconX + (iconSize - width) * 0.5, y,
                        rowTextScale, entry.worldScale(), EFFECT_TIME_COLOR, entry.alpha()
                );
            }

            if (level > 1) {
                String levelText = roman(level);
                double width = worldTextWidth(levelRenderer, levelText, rowTextScale);
                drawWorldText(
                        renderer, basis, levelRenderer, levelText, entry.anchor(),
                        iconX + (iconSize - width) * 0.5, iconY - levelHeight - levelGap,
                        rowTextScale, entry.worldScale(), EFFECT_LEVEL_STACK_COLOR, entry.alpha()
                );
            }
        }
    }

    private static double worldTextWidth(TextRenderer renderer, String text, double scale) {
        if (text == null || text.isEmpty()) return 0.0;
        return WorldTextRenderer.measure(renderer, text, scale, false).width();
    }

    private static double worldStyledTextWidth(TextRenderer renderer,
                                               Component text,
                                               double scale,
                                               int defaultColor) {
        if (text == null) return 0.0;
        double width = 0.0;
        for (TextRenderUtil.Part part : TextRenderUtil.flattenStyled(text, defaultColor)) {
            width += worldTextWidth(renderer, part.text(), scale);
        }
        return width;
    }

    private static void drawWorldText(Renderer3D renderer,
                                      WorldBillboardRenderer.Basis basis,
                                      TextRenderer textRenderer,
                                      String text,
                                      Vec3 anchor,
                                      double pixelX,
                                      double pixelY,
                                      double textScale,
                                      double worldScale,
                                      int argb,
                                      float alpha) {
        if (text == null || text.isEmpty() || alpha <= 0.001f) return;
        WorldBillboardRenderer.text(renderer, basis, textRenderer, text, anchor, pixelX, pixelY,
                textScale, worldScale, argb, alpha, true);
    }

    private static double drawWorldStyledText(Renderer3D renderer,
                                              WorldBillboardRenderer.Basis basis,
                                              TextRenderer textRenderer,
                                              Component text,
                                              Vec3 anchor,
                                              double pixelX,
                                              double pixelY,
                                              double textScale,
                                              double worldScale,
                                              int defaultColor,
                                              float alpha) {
        double cursor = pixelX;
        if (text == null) return cursor;
        for (TextRenderUtil.Part part : TextRenderUtil.flattenStyled(text, defaultColor)) {
            drawWorldText(renderer, basis, textRenderer, part.text(), anchor, cursor, pixelY,
                    textScale, worldScale, part.color(), alpha);
            cursor += worldTextWidth(textRenderer, part.text(), textScale);
        }
        return cursor;
    }

    private void drawNameplateBackground(Renderer2D renderer, Player player, NameplateLayout layout) {
        if (layout == null) return;
        int alpha = clampAlpha(nameplateAlpha.get());
        if (alpha <= 0) return;

        float globalAlpha = (alpha / 255.0f) * foregroundAlpha;
        MatteHudStyle.drawPlate(renderer, layout.bgX(), layout.bgY(), layout.bgW(), layout.bgH(), nameplateRadius(layout), globalAlpha);
    }

    private float nameplateRadius(NameplateLayout layout) {
        if (layout == null) return MATTE_NAMEPLATE_RADIUS;
        return Math.min(MATTE_NAMEPLATE_RADIUS, Math.max(2.5f, layout.bgH() * 0.30f));
    }

    private boolean shouldRenderPlayerHead(Player player) {
        return playerHead.get() && player instanceof AbstractClientPlayer;
    }

    private NameplateLayout buildNameplateLayout(Player player, TextRenderer nameRenderer, TextRenderer hpRenderer, LabelInfo info,
                                                 double dist, int centerX, int nameY,
                                                 int totemPopCount, float totemPopAnimation) {
        if (info == null) return null;
        float labelScale = NAMEPLATE_SCALE;
        String prefix = info.pvpPrefix();
        String name = info.name();
        String hp = formatNameplateHp(info.hp());
        String pingText = info.pingText();
        int pingWidth = pingText.isEmpty() ? 0 : (int) Math.round(worldTextWidth(nameRenderer, pingText, labelScale));
        int pingGap = pingWidth > 0 ? PING_TEXT_GAP : 0;
        double prefixWidth = prefix.isEmpty() ? 0.0 : worldTextWidth(nameRenderer, prefix, labelScale);
        double rawNameWidth = info.styledName() != null
                ? worldStyledTextWidth(nameRenderer, info.styledName(), labelScale, CategoryService.getColor(player))
                : worldTextWidth(nameRenderer, name, labelScale);
        int nameWidth = (int) Math.round(prefixWidth + rawNameWidth);
        int hpWidth = hp == null || hp.isEmpty() ? 0 : (int) Math.round(worldTextWidth(hpRenderer, hp, labelScale));
        int hpGap = hpWidth > 0 ? HP_TEXT_GAP : 0;
        boolean showDist = shouldShowDistanceText(player);
        String distText = showDist ? (int) Math.round(dist) + "m" : "";
        int distWidth = showDist ? (int) Math.round(worldTextWidth(nameRenderer, distText, labelScale)) : 0;
        int distGap = distWidth > 0 ? DIST_TEXT_GAP : 0;
        int nameHeight = (int) Math.ceil(Math.max(
                WorldTextRenderer.measure(nameRenderer, "Ag", labelScale, false).height(),
                WorldTextRenderer.measure(hpRenderer, "Ag", labelScale, false).height()));
        boolean renderHead = shouldRenderPlayerHead(player);
        float headSize = renderHead ? Math.max(MATTE_HEAD_MIN_SIZE, nameHeight + NAMEPLATE_PAD_Y * 1.5f) : 0.0f;
        float headBlock = renderHead
                ? headSize + MATTE_HEAD_DIVIDER_GAP + MATTE_HEAD_DIVIDER_WIDTH + MATTE_HEAD_TEXT_GAP
                : 0.0f;
        int textWidth = pingWidth + pingGap + nameWidth + hpGap + hpWidth + distGap + distWidth;
        int scaledHeight = Math.max(nameHeight, renderHead ? Math.round(headSize) : nameHeight);
        boolean renderTotemBadge = totemPopCounter.get() && totemPopCount > 0 && totemPopAnimation > 0.001f;
        String totemText = renderTotemBadge ? "x" + totemPopCount : "";
        float totemIconSize = renderTotemBadge
                ? Math.min(TOTEM_BADGE_ICON_SIZE, Math.max(8.0f, scaledHeight - 2.0f))
                : 0.0f;
        float totemTextWidth = renderTotemBadge
                ? (float) worldTextWidth(hpRenderer, totemText, TOTEM_BADGE_TEXT_SCALE)
                : 0.0f;
        float totemBadgeHeight = renderTotemBadge ? scaledHeight : 0.0f;
        float totemBadgeWidth = renderTotemBadge
                ? totemIconSize + TOTEM_BADGE_TEXT_GAP + totemTextWidth
                : 0.0f;
        float totemBlock = renderTotemBadge
                ? TOTEM_BADGE_GAP + TOTEM_BADGE_DIVIDER_WIDTH + TOTEM_BADGE_DIVIDER_GAP + totemBadgeWidth
                : 0.0f;
        int totalWidth = Math.round(headBlock + textWidth + totemBlock);
        int scaledWidth = totalWidth;
        int contentX = centerX - scaledWidth / 2;
        int nameX = Math.round(contentX + headBlock);

        float bgX = contentX - NAMEPLATE_PAD_X;
        float bgY = nameY - NAMEPLATE_PAD_Y;
        float bgW = scaledWidth + NAMEPLATE_PAD_X * 2f;
        float bgH = scaledHeight + NAMEPLATE_PAD_Y * 2f;
        float headX = contentX;
        float headY = nameY + (scaledHeight - headSize) * 0.5f;
        float dividerX = headX + headSize + MATTE_HEAD_DIVIDER_GAP;
        float dividerH = Math.max(3.0f, bgH * 0.48f);
        float dividerY = bgY + (bgH - dividerH) * 0.5f;
        float totemDividerX = renderTotemBadge ? contentX + headBlock + textWidth + TOTEM_BADGE_GAP : 0.0f;
        float totemDividerH = renderTotemBadge ? Math.max(3.0f, bgH * 0.48f) : 0.0f;
        float totemDividerY = renderTotemBadge ? bgY + (bgH - totemDividerH) * 0.5f : 0.0f;
        float totemBadgeX = renderTotemBadge ? totemDividerX + TOTEM_BADGE_DIVIDER_WIDTH + TOTEM_BADGE_DIVIDER_GAP : 0.0f;
        float totemBadgeY = renderTotemBadge ? nameY : 0.0f;

        return new NameplateLayout(nameX, nameY, scaledWidth, scaledHeight, labelScale, pingText, distText, bgX, bgY, bgW, bgH,
                pingWidth, pingGap, nameWidth, hpWidth, hpGap, renderHead, headX, headY, headSize,
                dividerX, dividerY, MATTE_HEAD_DIVIDER_WIDTH, dividerH,
                renderTotemBadge, totemDividerX, totemDividerY, TOTEM_BADGE_DIVIDER_WIDTH, totemDividerH,
                totemBadgeX, totemBadgeY, totemBadgeWidth, totemBadgeHeight, totemIconSize);
    }

    private void renderNameTagForeground(Renderer2D renderer, GuiGraphicsExtractor ctx, TextRenderer textRenderer, RenderEntry entry) {
        float alpha = Math.max(0.0f, Math.min(1.0f, entry.presentationAlpha()));
        if (alpha <= 0.001f) return;
        double previousRendererAlpha = renderer.getAlpha();
        float previousForegroundAlpha = foregroundAlpha;
        foregroundAlpha = alpha;
        renderer.setAlpha(previousRendererAlpha * alpha);
        try {
            renderNameTagForegroundContent(renderer, ctx, textRenderer, entry);
        } finally {
            renderer.setAlpha(previousRendererAlpha);
            foregroundAlpha = previousForegroundAlpha;
        }
    }

    private void renderNameTagForegroundContent(Renderer2D renderer, GuiGraphicsExtractor ctx, TextRenderer textRenderer, RenderEntry entry) {
        Player player = entry.player();
        int centerX = entry.centerX();
        int nameY = entry.nameY();
        int itemsY = entry.itemsY();
        if (entry.renderNameplate() && entry.nameplateLayout() != null) {
            drawNameplateBackground(renderer, player, entry.nameplateLayout());
            drawNameplateHead(renderer, ctx, player, entry.nameplateLayout());
        }

        final int effectsLift = scaleRow(computeEnchantLift(textRenderer, entry.maxEnchantLines()));
        EquipmentLayout equipmentLayout = entry.renderSlots()
                ? computeEquipmentLayout(textRenderer, entry.slots(), entry.enchantLines(), centerX,
                equipIconSize(), equipIconGap(), SMALL_TEXT_SCALE)
                : EquipmentLayout.empty();

        if (entry.renderSlots()) {
            drawEquipmentRow(ctx, entry.player(), entry.slots(), equipmentLayout, itemsY);
        }

        if (entry.renderEffects()) {
            EffectIconRenderer.beginBatch();
            drawEffectsRowIcons(ctx, entry.effects(), centerX, itemsY, effectsLift);
            EffectIconRenderer.endBatch();
        }

        drawOpponentCooldownsStackedIcons(ctx, player, entry.nameplateLayout(), centerX, nameY);

        boolean startedBaseText = beginTextRenderer(textRenderer, SMALL_TEXT_SCALE);
        try {
            if (entry.renderSlots()) {
                drawEquipmentRowText(textRenderer, entry.slots(), entry.enchantLines(), equipmentLayout, itemsY);
            }

            if (entry.renderEffects()) {
                TextRenderer levelTr = cachedLevelTr != null ? cachedLevelTr : textRenderer;
                TextRenderer timeTr = cachedTimeTr != null ? cachedTimeTr : textRenderer;
                drawEffectsRowIconOverlays(textRenderer, levelTr, timeTr, entry.effects(), entry.player().getId(), centerX, itemsY, effectsLift);
            }

            drawOpponentCooldownsStackedText(textRenderer, player, entry.nameplateLayout(), centerX, nameY);
        } finally {
            endTextRenderer(textRenderer, startedBaseText);
        }

        if (entry.renderNameplate() && entry.labelInfo() != null) {
            LabelInfo info = entry.labelInfo();
            NameplateLayout layout = entry.nameplateLayout();
            if (layout != null) {
                TextRenderer nameTr = cachedNameTr != null ? cachedNameTr : textRenderer;
                TextRenderer hpTr = cachedHpTr != null ? cachedHpTr : nameTr;
                boolean clipped = pushNameplateScissor(layout);
                float scale = layout.labelScale();
                double nameX = layout.nameX();
                boolean startedName = beginTextRenderer(nameTr, scale);
                double cursorX = nameX;
                try {
                    if (!layout.pingText().isEmpty()) {
                        renderNameplateText(nameTr, layout.pingText(), cursorX, nameY, resolvePingColor(info.ping()));
                        cursorX += layout.pingWidth() + layout.pingGap();
                    }
                    String prefix = info.pvpPrefix();
                    if (!prefix.isEmpty()) {
                        renderNameplateText(nameTr, prefix, cursorX, nameY, pvpPrefixColor.getArgb());
                        cursorX += nameTr.getWidth(prefix);
                    }
                    if (shouldUseTabNames()) {
                        renderStyledNameplateText(nameTr, info.styledName(), cursorX, nameY, CategoryService.getColor(player));
                    } else {
                        renderNameplateText(nameTr, info.name(), cursorX, nameY, CategoryService.getColor(player));
                    }
                } finally {
                    endTextRenderer(nameTr, startedName);
                }

                if (!info.hp().isEmpty()) {
                    double hpX = nameX + layout.pingWidth() + layout.pingGap() + layout.nameWidth() + layout.hpGap();
                    boolean startedHp = beginTextRenderer(hpTr, scale);
                    try {
                        renderNameplateText(hpTr, formatNameplateHp(info.hp()), hpX, nameY, entry.infoColor());
                    } finally {
                        endTextRenderer(hpTr, startedHp);
                    }
                }

                if (!layout.distText().isEmpty()) {
                    double distX = nameX + layout.pingWidth() + layout.pingGap() + layout.nameWidth() + layout.hpGap() + layout.hpWidth()
                            + (layout.hpWidth() > 0 ? DIST_TEXT_GAP : 0);
                    boolean startedDist = beginTextRenderer(nameTr, scale);
                    try {
                        renderNameplateText(nameTr, layout.distText(), distX, nameY, 0xFFD0D0D0);
                    } finally {
                        endTextRenderer(nameTr, startedDist);
                    }
                }
                if (clipped) ScissorFunction.pop();
                drawNameplateTotemBadge(renderer, textRenderer, layout, entry.totemPopCount(), entry.totemPopAnimation());
            }
        }
    }

    private void drawNameplateTotemBadge(Renderer2D renderer,
                                         TextRenderer fallbackRenderer,
                                         NameplateLayout layout,
                                         int count,
                                         float animation) {
        if (renderer == null || layout == null || !layout.renderTotemBadge()) return;
        if (count <= 0 || animation <= 0.001f) return;

        float anim = AnimationUtility.easeInOutCubic(animation);
        float globalAlpha = (clampAlpha(nameplateAlpha.get()) / 255.0f) * anim * foregroundAlpha;
        if (globalAlpha <= 0.001f) return;

        drawNameplateDivider(
                renderer,
                layout.totemDividerX(),
                layout.totemDividerY(),
                layout.totemDividerW(),
                layout.totemDividerH(),
                globalAlpha
        );

        float blockX = layout.totemBadgeX();
        float blockY = layout.totemBadgeY();
        float blockH = layout.totemBadgeH();
        float iconScale = 0.86f + 0.14f * anim;
        float iconSize = Math.max(1.0f, layout.totemIconSize() * iconScale);
        float iconX = blockX;
        float iconY = blockY + (blockH - iconSize) * 0.5f;
        renderer.item(totemBadgeStack(), iconX, iconY, Math.max(0.1f, iconSize / 16.0f), 911, Renderer2D.ITEM_OVERLAY_NONE, null);

        String text = "x" + count;
        TextRenderer badgeRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, fallbackRenderer);
        float textScale = TOTEM_BADGE_TEXT_SCALE * (0.94f + 0.06f * anim);
        float textX = iconX + iconSize + TOTEM_BADGE_TEXT_GAP;
        float textH = (float) badgeRenderer.getHeight(false) * textScale;
        float textY = blockY + (blockH - textH) * 0.5f - 0.1f;
        int textColor = HudRenderUtil.setAlpha(theme().textPrimary(), Math.round(255.0f * globalAlpha));

        badgeRenderer.begin(textScale, false, false);
        try {
            if (globalAlpha > 0.96f) {
                int baseColor = textColor;
                int darkColor = HudRenderUtil.mixColor(baseColor, HudRenderUtil.setAlpha(0xFF181B1F, Math.round(255.0f * globalAlpha)), 0.18f);
                int lightColor = HudRenderUtil.mixColor(baseColor, HudRenderUtil.setAlpha(theme().accentSoft(), Math.round(255.0f * globalAlpha)), 0.24f);
                float time = (float) (net.minecraft.util.Util.getMillis() / 1000.0);
                float pulse = time * 2.35f;
                badgeRenderer.renderGradient(text, textX, textY, (idx, cp, gx, out) -> {
                    float p = pulse + idx * 0.62f;
                    float t0 = 0.5f + 0.5f * (float) Math.sin(p);
                    float t1 = 0.5f + 0.5f * (float) Math.sin(p + 1.05f);
                    out[0] = HudRenderUtil.mixColor(darkColor, lightColor, t0);
                    out[1] = HudRenderUtil.mixColor(darkColor, lightColor, t1);
                }, false);
            } else {
                badgeRenderer.render(text, textX, textY, new RenderColor(textColor), false);
            }
        } finally {
            badgeRenderer.end();
        }
    }

    private void drawNameplateHead(Renderer2D renderer, GuiGraphicsExtractor ctx, Player player, NameplateLayout layout) {
        if (ctx == null || layout == null || !layout.renderHead()) return;
        if (!(player instanceof AbstractClientPlayer clientPlayer)) return;

        int alpha = Math.round(clampAlpha(nameplateAlpha.get()) * foregroundAlpha);
        if (alpha <= 0) return;
        float globalAlpha = alpha / 255.0f;

        PlayerHeadRenderer.drawRounded(
                ctx,
                layout.headX(),
                layout.headY(),
                layout.headSize(),
                Math.min(6.0f, layout.headSize() * 0.48f),
                clientPlayer,
                new RenderColor(MatteHudStyle.withAlpha(0xFFFFFFFF, alpha)),
                true,
                null,
                0.0f,
                false
        );

        drawNameplateDivider(
                renderer,
                layout.dividerX(),
                layout.dividerY(),
                layout.dividerW(),
                layout.dividerH(),
                globalAlpha
        );
    }

    private void drawNameplateDivider(Renderer2D renderer,
                                      float x,
                                      float y,
                                      float width,
                                      float height,
                                      float globalAlpha) {
        if (renderer == null || width <= 0.0f || height <= 0.0f || globalAlpha <= 0.001f) return;
        int color = MatteHudStyle.strokeColor(globalAlpha * 0.82f);
        float drawWidth = Math.max(0.8f, width);
        renderer.roundedRect(
                x + (width - drawWidth) * 0.5f,
                y,
                drawWidth,
                height,
                Math.min(drawWidth, height) * 0.5f,
                0.0f,
                color
        );
    }

    private void ensureFontCache(TextRenderer fallback) {
        cachedNameTr = Fonts.renderer("OnestBold", FontInfo.Type.Regular, fallback);
        cachedHpTr = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, cachedNameTr);
        cachedLevelTr = Fonts.renderer("OnestBold", FontInfo.Type.Regular, fallback);
        cachedTimeTr = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, fallback);
    }

    public void onResourceReload() {
        cachedNameTr = null;
        cachedHpTr = null;
        cachedLevelTr = null;
        cachedTimeTr = null;
        enchantCache.clear();
    }

    private boolean shouldShowDistanceText(Player player) {
        if (!toggles.get("Show distance")) return false;
        if (player == null || mc.player == null) return false;
        if (player != mc.player) return true;
        Freecam freecam = Modules.get(Freecam.class);
        return freecam != null && freecam.isEnabled();
    }

    private ItemStack[] getCachedEquipmentSlots(Player player) {
        UUID id = player.getUUID();
        ItemStack[] slots = slotCache.computeIfAbsent(id, key -> new ItemStack[6]);
        slots[0] = player.getMainHandItem();
        slots[1] = player.getItemBySlot(EquipmentSlot.FEET);
        slots[2] = player.getItemBySlot(EquipmentSlot.LEGS);
        slots[3] = player.getItemBySlot(EquipmentSlot.CHEST);
        slots[4] = player.getItemBySlot(EquipmentSlot.HEAD);
        slots[5] = player.getOffhandItem();
        return slots;
    }

    private List<MobEffectInstance> resolveEffects(Player player) {
        UUID id = player.getUUID();
        EffectCache cache = effectCache.computeIfAbsent(id, key -> new EffectCache());
        int tick = player.tickCount;
        if (tick - cache.lastTick >= EFFECT_CACHE_TICKS || cache.effects.isEmpty()) {
            cache.effects = collectEffects(player);
            cache.lastTick = tick;
        }
        return cache.effects;
    }

    private EnchantCache resolveEnchantCache(Player player, ItemStack[] slots) {
        UUID id = player.getUUID();
        EnchantCache cache = enchantCache.computeIfAbsent(id, key -> new EnchantCache());
        int tick = player.tickCount;
        int signature = equipmentSignature(slots);
        if (signature != cache.signature
                || tick - cache.lastTick >= ENCHANT_CACHE_TICKS
                || cache.lines.isEmpty()) {
            List<List<EnchantLine>> lines = buildEnchantLines(slots);
            cache.lines = lines;
            cache.maxLines = maxEnchantLines(lines);
            cache.lastTick = tick;
            cache.signature = signature;
        }
        return cache;
    }

    private float updateTotemPopAnimation(Player player, TotemPopSnapshot snapshot, float dt) {
        if (player == null) return 0.0f;
        UUID id = player.getUUID();
        boolean visible = snapshot != null && snapshot.visible();
        if (visible) {
            totemPopLastCounts.put(id, snapshot.count());
        }

        float current = totemPopAnimations.getOrDefault(id, 0.0f);
        float target = visible ? 1.0f : 0.0f;
        float next = AnimationUtility.approach(current, target, dt, TOTEM_BADGE_ANIM_SPEED);
        next = AnimationUtility.snap(next, target, 0.001f);

        if (next <= 0.001f && !visible) {
            totemPopAnimations.remove(id);
            totemPopLastCounts.remove(id);
            return 0.0f;
        }

        totemPopAnimations.put(id, next);
        return next;
    }

    private int resolveTotemPopCount(Player player, TotemPopSnapshot snapshot, float animation) {
        if (player == null) return 0;
        if (snapshot != null && snapshot.visible()) {
            return snapshot.count();
        }
        if (animation > 0.001f) {
            return Math.max(0, totemPopLastCounts.getOrDefault(player.getUUID(), 0));
        }
        return 0;
    }

    private void pruneCaches() {
        if (seenIds.isEmpty()) {
            slotCache.clear();
            effectCache.clear();
            enchantCache.clear();
            totemPopAnimations.clear();
            totemPopLastCounts.clear();
            return;
        }
        slotCache.keySet().removeIf(id -> !seenIds.contains(id));
        effectCache.keySet().removeIf(id -> !seenIds.contains(id));
        enchantCache.keySet().removeIf(id -> !seenIds.contains(id));
        totemPopAnimations.keySet().removeIf(id -> !seenIds.contains(id));
        totemPopLastCounts.keySet().removeIf(id -> !seenIds.contains(id));
    }

    private void drawEquipmentRow(GuiGraphicsExtractor ctx,
                                  Player player,
                                  ItemStack[] slots,
                                  EquipmentLayout layout,
                                  int y) {
        int iconSize = equipIconSize();
        int seed = 0;

        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = slots[i];
            float x = (float) (layout.centers()[i] - iconSize * 0.5);
            if (stack.isEmpty()) continue;

            float scale = iconSize / 16f;
            int overlayFlags = Renderer2D.ITEM_OVERLAY_COUNT;
            if (toggles.get("Item durability bar")) {
                overlayFlags |= Renderer2D.ITEM_OVERLAY_DURABILITY;
            }
            Renderer2D.COLOR.item(stack, x, y, scale, seed++, overlayFlags, null,
                    durabilityStartPercent.get(), 70);
            drawOpponentItemCooldown(player, stack, x, y, scale);
        }
    }

    private void drawOpponentItemCooldown(Player player, ItemStack stack, float x, float y, float scale) {
        if (!shouldRenderOpponentStuff(player)) return;
        if (stack == null || stack.isEmpty()) return;

        Item item = stack.getItem();
        if (!CooldownRegistry.isTracked(item)) return;

        ItemCooldownSnapshot snapshot = OpponentCooldownManager.snapshot(player.getUUID(), item);
        if (!snapshot.visible()) return;
        float progress = snapshot.cooling() ? snapshot.cooldownProgress() : snapshot.usesProgress();
        if (progress <= 0f) return;

        int top = Mth.floor(16.0F * (1.0F - progress));
        int bottom = top + Mth.ceil(16.0F * progress);
        Renderer2D.COLOR.quad(
                x,
                y + top * scale,
                16.0f * scale,
                (bottom - top) * scale,
                snapshot.cooling() ? 0x80FFA500 : 0x706D9DFF
        );

        // no text inside slot
    }

    private void drawEquipmentRowText(TextRenderer textRenderer, ItemStack[] slots, List<List<EnchantLine>> enchantLines,
                                      EquipmentLayout layout, int y) {
        int iconSize = equipIconSize();

        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = slots[i];
            double center = layout.centers()[i];
            if (stack.isEmpty()) continue;

            List<EnchantLine> ench = enchantLines.get(i);
            if (!ench.isEmpty()) {
                int lineHeight = (int) textRenderer.getHeight(true) + 1;
                int startY = y - 14;
                for (int idx = 0; idx < ench.size(); idx++) {
                    EnchantLine line = ench.get(idx);
                    int lineY = startY - idx * lineHeight;
                    int textX = (int) Math.round(center - textRenderer.getWidth(line.text(), true) * 0.5);
                    renderEquipmentText(textRenderer, line.text(), textX, lineY, line.color());
                }
            }

        }
    }

    private EquipmentLayout computeEquipmentLayout(TextRenderer textRenderer,
                                                    ItemStack[] slots,
                                                    List<List<EnchantLine>> enchantLines,
                                                    double centerX,
                                                    int iconSize,
                                                    int requestedGap,
                                                    double enchantTextScale) {
        if (slots == null || slots.length == 0) return EquipmentLayout.empty();
        double[] widths = new double[slots.length];
        double totalWidth = 0.0;
        double gap = Math.max(ENCHANT_TEXT_MIN_GAP, requestedGap);
        for (int i = 0; i < slots.length; i++) {
            double columnWidth = iconSize;
            List<EnchantLine> lines = enchantLines != null && i < enchantLines.size()
                    ? enchantLines.get(i)
                    : List.of();
            for (EnchantLine line : lines) {
                columnWidth = Math.max(columnWidth,
                        worldTextWidth(textRenderer, line.text(), enchantTextScale));
            }
            widths[i] = columnWidth;
            totalWidth += columnWidth;
        }
        totalWidth += Math.max(0, slots.length - 1) * gap;

        double[] centers = new double[slots.length];
        double cursor = centerX - totalWidth * 0.5;
        for (int i = 0; i < widths.length; i++) {
            centers[i] = cursor + widths[i] * 0.5;
            cursor += widths[i] + gap;
        }
        return new EquipmentLayout(centers, totalWidth);
    }

    private int foregroundColor(int argb) {
        return MatteHudStyle.scaleAlpha(argb, foregroundAlpha);
    }

    private void renderEquipmentText(TextRenderer textRenderer, String text, double x, double y, int color) {
        RenderColor shadow = new RenderColor(foregroundColor(0xB0000000));
        double o = ENCHANT_TEXT_SHADOW_OFFSET;
        textRenderer.render(text, x + o, y, shadow, false);
        textRenderer.render(text, x - o, y, shadow, false);
        textRenderer.render(text, x, y + o, shadow, false);
        textRenderer.render(text, x, y - o, shadow, false);
        textRenderer.render(text, x, y, new RenderColor(foregroundColor(color)), false);
    }

    private void renderNameplateText(TextRenderer textRenderer, String text, double x, double y, int color) {
        if (text == null || text.isEmpty()) return;
        if (NAMEPLATE_TEXT_SHADOW_OFFSET > 0.001f) {
            RenderColor shadow = new RenderColor(foregroundColor(0x42000000));
            double o = NAMEPLATE_TEXT_SHADOW_OFFSET;
            textRenderer.render(text, x + o, y, shadow, false);
            textRenderer.render(text, x - o, y, shadow, false);
            textRenderer.render(text, x, y + o, shadow, false);
            textRenderer.render(text, x, y - o, shadow, false);
        }
        textRenderer.render(text, x, y, new RenderColor(foregroundColor(color)), false);
    }

    private void renderStyledNameplateText(TextRenderer textRenderer, Component text, double x, double y, int defaultColor) {
        if (text == null) return;
        List<TextRenderUtil.Part> parts = TextRenderUtil.flattenStyled(text, defaultColor);
        double cursorX = x;
        for (TextRenderUtil.Part part : parts) {
            renderNameplateText(textRenderer, part.text(), cursorX, y, part.color());
            cursorX += textRenderer.getWidth(part.text());
        }
    }

    private boolean shouldUseTabNames() {
        return nameplateExtras.get("Tab names");
    }

    private boolean shouldShowPingText() {
        return nameplateExtras.get("Ping");
    }

    private int getPlayerPing(Player player) {
        if (player == null || mc == null || mc.getConnection() == null) return -1;
        PlayerInfo entry = mc.getConnection().getPlayerInfo(player.getUUID());
        return entry != null ? entry.getLatency() : -1;
    }

    private void drawEffectsIcons(GuiGraphicsExtractor ctx, List<MobEffectInstance> effects, int x, int y) {
        if (effects.isEmpty()) return;

        int cursorY = y;
        EffectIconRenderer.beginBatch();
        for (MobEffectInstance inst : effects) {
            EffectIconRenderer.draw(ctx, inst, x, cursorY, ICON_SIZE, foregroundColor(0xFFFFFFFF));
            cursorY += EFFECT_LINE_HEIGHT;
        }
        EffectIconRenderer.endBatch();
    }

    private void drawEffectsText(TextRenderer textRenderer, List<MobEffectInstance> effects, int entityId, int x, int y) {
        if (effects.isEmpty()) return;

        int cursorY = y;
        for (MobEffectInstance inst : effects) {
            int textX = x + ICON_SIZE + 6;
            int textY = cursorY + 4;
            String dur = formatDuration(entityId, inst);
            String name = I18n.get(inst.getEffect().value().getDescriptionId());
            int level = inst.getAmplifier() + 1;

            if (!dur.isEmpty()) {
                String seg = dur + " ";
                textRenderer.render(seg, textX, textY, new RenderColor(foregroundColor(0xFFFFFFFF)), true);
                textX += (int) textRenderer.getWidth(seg, true);
            }
            textRenderer.render(name, textX, textY, new RenderColor(foregroundColor(0xFFFFFFFF)), true);
            textX += (int) textRenderer.getWidth(name, true);
            if (level > 1) {
                String lvl = " " + level;
                textRenderer.render(lvl, textX, textY, new RenderColor(foregroundColor(EFFECT_LEVEL_COLOR)), true);
            }
            cursorY += EFFECT_LINE_HEIGHT;
        }
    }

    private void drawEffectIconOverlays(TextRenderer baseRenderer, TextRenderer levelRenderer, TextRenderer timeRenderer,
                                        List<MobEffectInstance> effects, int entityId, int x, int y) {
        if (effects.isEmpty()) return;

        TextRenderer levelTr = levelRenderer == null ? baseRenderer : levelRenderer;
        TextRenderer timeTr = timeRenderer == null ? baseRenderer : timeRenderer;

        boolean startedLevel = beginTextRenderer(levelTr, SMALL_TEXT_SCALE);
        boolean startedTime = timeTr != levelTr && beginTextRenderer(timeTr, SMALL_TEXT_SCALE);

        int levelHeight = (int) levelTr.getHeight(true);
        int timeHeight = (int) timeTr.getHeight(true);
        int levelGap = scaleRow(EFFECT_ROW_LEVEL_GAP);
        int timeGap = scaleRow(EFFECT_ROW_TIME_GAP);

        int cursorY = y;
        for (MobEffectInstance inst : effects) {
            int level = inst.getAmplifier() + 1;
            String dur = formatDuration(entityId, inst);

            int iconX = x;
            int iconY = cursorY;

            if (!dur.isEmpty()) {
                int timeW = (int) timeTr.getWidth(dur, true);
                int timeX = iconX + (ICON_SIZE - timeW) / 2;
                int timeY = iconY - timeHeight - 1;
                timeTr.render(dur, timeX, timeY, new RenderColor(foregroundColor(EFFECT_TIME_COLOR)), true);
            }

            if (level > 1) {
                String lvl = String.valueOf(level);
                int levelW = (int) levelTr.getWidth(lvl, true);
                int levelX = iconX + (ICON_SIZE - levelW) / 2;
                int levelY = !dur.isEmpty()
                        ? (iconY - timeHeight - 1 - levelHeight - 1)
                        : (iconY - levelHeight - 1);
                levelTr.render(lvl, levelX, levelY, new RenderColor(foregroundColor(EFFECT_LEVEL_STACK_COLOR)), true);
            }
            cursorY += EFFECT_LINE_HEIGHT;
        }

        endTextRenderer(levelTr, startedLevel);
        endTextRenderer(timeTr, startedTime);
    }

    private void drawEffectsRowIconOverlays(TextRenderer baseRenderer, TextRenderer levelRenderer, TextRenderer timeRenderer,
                                            List<MobEffectInstance> effects, int entityId, int centerX, int itemsY, int lift) {
        if (effects.isEmpty()) return;

        TextRenderer levelTr = levelRenderer == null ? baseRenderer : levelRenderer;
        TextRenderer timeTr = timeRenderer == null ? baseRenderer : timeRenderer;

        float rowTextScale = SMALL_TEXT_SCALE * EFFECT_ROW_SCALE;
        boolean startedLevel = beginTextRenderer(levelTr, rowTextScale);
        boolean startedTime = timeTr != levelTr && beginTextRenderer(timeTr, rowTextScale);

        int levelHeight = (int) levelTr.getHeight(true);
        int timeHeight = (int) timeTr.getHeight(true);
        int levelGap = scaleRow(EFFECT_ROW_LEVEL_GAP);
        int timeGap = scaleRow(EFFECT_ROW_TIME_GAP);

        int count = effects.size();
        int iconSize = scaleRow(ICON_SIZE);
        int gap = scaleRow(EFFECT_ROW_ICON_GAP);
        int rowGap = scaleRow(EFFECT_ROW_GAP);
        int rowW = count * iconSize + Math.max(0, count - 1) * gap;
        int startX = centerX - rowW / 2;
        int iconY = itemsY - iconSize - rowGap - lift;

        for (int i = 0; i < effects.size(); i++) {
            MobEffectInstance inst = effects.get(i);
            int level = inst.getAmplifier() + 1;
            String dur = formatDuration(entityId, inst);

            int iconX = startX + i * (iconSize + gap);

            if (!dur.isEmpty()) {
                int timeW = (int) timeTr.getWidth(dur, true);
                int timeX = iconX + (iconSize - timeW) / 2;
                int timeY = (level > 1)
                        ? (iconY - levelHeight - levelGap - timeHeight - timeGap)
                        : (iconY - timeHeight - levelGap);
                timeTr.render(dur, timeX, timeY, new RenderColor(foregroundColor(EFFECT_TIME_COLOR)), true);
            }

            if (level > 1) {
                String lvl = roman(level);
                int levelW = (int) levelTr.getWidth(lvl, true);
                int levelX = iconX + (iconSize - levelW) / 2;
                int levelY = iconY - levelHeight - levelGap;
                levelTr.render(lvl, levelX, levelY, new RenderColor(foregroundColor(EFFECT_LEVEL_STACK_COLOR)), true);
            }
        }

        endTextRenderer(levelTr, startedLevel);
        endTextRenderer(timeTr, startedTime);
    }

    private void drawEffectsRowIcons(GuiGraphicsExtractor ctx, List<MobEffectInstance> effects, int centerX, int itemsY, int lift) {
        if (effects.isEmpty()) return;
        int count = effects.size();
        int iconSize = scaleRow(ICON_SIZE);
        int gap = scaleRow(EFFECT_ROW_ICON_GAP);
        int rowGap = scaleRow(EFFECT_ROW_GAP);
        int rowW = count * iconSize + Math.max(0, count - 1) * gap;
        int startX = centerX - rowW / 2;
        int y = itemsY - iconSize - rowGap - lift;

        for (int i = 0; i < effects.size(); i++) {
            int x = startX + i * (iconSize + gap);
            EffectIconRenderer.draw(ctx, effects.get(i), x, y, iconSize, foregroundColor(0xFFFFFFFF));
        }
    }

    private int computeEnchantLift(TextRenderer textRenderer, int maxLines) {
        if (!toggles.get("Show armor row")) return 0;
        if (maxLines <= 0) return 6;
        int lineHeight = (int) Math.round(textRenderer.getHeight(true) * SMALL_TEXT_SCALE) + 1;
        return 6 + maxLines * lineHeight;
    }

    private LabelInfo buildLabelInfo(Player player) {
        Component styledName = resolveNameplateText(player);
        String name = styledName == null ? "" : styledName.getString();
        String prefix = "";
        if (pvpPrefix.get() && PvpTargetState.isTargetInPvp(player.getUUID())) {
            prefix = "[PVP] ";
        }
        int ping = getPlayerPing(player);
        String pingText = shouldShowPingText() && ping >= 0 ? ping + "ms" : "";
        if (isCreative(player)) {
            return new LabelInfo(prefix, styledName, name, ping, pingText, "GM", PlayerRelations.get().colorStaff());
        }
        PlayerHealthResolver.HealthSnapshot health = PlayerHealthResolver.resolve(player);
        String hp = formatHealth(health.totalHealth());
        int color = resolveHealthColor(health.totalHealth(), health.maxHealth());
        return new LabelInfo(prefix, styledName, name, ping, pingText, hp, color);
    }

    private Component resolveNameplateText(Player player) {
        if (player == null) return Component.empty();
        String fallback = player.getGameProfile().name();
        if (!shouldUseTabNames()) return Component.literal(fallback);

        var handler = mc.getConnection();
        if (handler == null) return Component.literal(fallback);
        PlayerInfo entry = handler.getPlayerInfo(player.getUUID());
        if (entry == null) return Component.literal(fallback);

        Component displayName = entry.getTabListDisplayName();
        if (displayName == null) return Component.literal(fallback);

        Component parsed = LegacyTextUtil.convertLegacyCodes(displayName);
        if (parsed == null || parsed.getString().isBlank()) return Component.literal(fallback);
        return parsed;
    }

    private String formatHealth(float hp) {
        if (hpDecimals.get()) {
            return formatTenths(hp);
        }
        int value = (int) Math.ceil(hp);
        return String.valueOf(value);
    }

    private float totalHealth(Player player) {
        return PlayerHealthResolver.resolve(player).totalHealth();
    }

    private float maxTotalHealth(Player player) {
        return PlayerHealthResolver.resolve(player).maxHealth();
    }

    private String formatTenths(float value) {
        int tenth = Math.round(value * 10f);
        int whole = tenth / 10;
        int frac = Math.abs(tenth % 10);
        return whole + "." + frac;
    }

    private int resolveHealthColor(float current, float max) {
        if (current > max) return HP_CYAN;
        if ("Static".equalsIgnoreCase(hpColorMode.get())) {
            return HP_GREEN;
        }
        return gradientHealthColor(current, max);
    }

    private int gradientHealthColor(float current, float max) {
        if (max <= 0f) return HP_RED;
        float clamped = Mth.clamp(current, 0f, max);
        if (max <= 20f) {
            float t = clamped / max;
            return lerpColor(HP_RED, HP_GREEN, t);
        }
        if (clamped <= 20f) {
            float t = clamped / 20f;
            return lerpColor(HP_RED, HP_GREEN, t);
        }
        float t = (clamped - 20f) / (max - 20f);
        return lerpColor(HP_GREEN, HP_CYAN, Mth.clamp(t, 0f, 1f));
    }

    private boolean isCreative(Player player) {
        if (player.isCreative()) return true;
        var handler = mc.getConnection();
        if (handler == null) return false;
        PlayerInfo entry = handler.getPlayerInfo(player.getUUID());
        if (entry == null) return false;
        GameType mode = entry.getGameMode();
        return mode == GameType.CREATIVE;
    }

    private String formatEffectText(int entityId, MobEffectInstance inst) {
        String name = I18n.get(inst.getEffect().value().getDescriptionId());
        int level = inst.getAmplifier() + 1;
        if (level > 1) name = name + " " + level;
        String dur = formatDuration(entityId, inst);
        return dur.isEmpty() ? name : dur + " " + name;
    }

    private String formatDuration(int entityId, MobEffectInstance inst) {
        if (StatusEffectTracker.shouldHideDuration(entityId, inst.getEffect())) return "";
        int ticks = inst.getDuration();
        if (ticks < 0 || ticks >= 32767) return "";
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (seconds < 10) return minutes + ":0" + seconds;
        return minutes + ":" + seconds;
    }

    private List<MobEffectInstance> collectEffects(Player player) {
        return StatusEffectView.collectHudEffects(player);
    }

    private String roman(int n) {
        if (n <= 0) return String.valueOf(n);
        int value = n;
        StringBuilder sb = new StringBuilder();
        int[] vals = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] syms = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        for (int i = 0; i < vals.length; i++) {
            while (value >= vals[i]) {
                value -= vals[i];
                sb.append(syms[i]);
            }
        }
        return sb.toString();
    }

    private List<EnchantLine> formatEnchantments(ItemStack stack) {
        List<EnchantLine> parts = new ArrayList<>();

        ItemEnchantments enchComp = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchComp.entrySet()) {
            Holder<Enchantment> ref = entry.getKey();
            int level = entry.getIntValue();
            if (level <= 0) continue;

            ResourceKey<Enchantment> key = ref.unwrapKey().orElse(null);
            if (key == null) continue;

            EnchantMeta meta = EnchantRegistry.REGISTRY.get(key);
            if (meta == null) continue;

            Enchantment value = ref.value();
            boolean singleLevel = value.getMaxLevel() == 1;
            boolean overMax = EnchantUtil.isInvalidLevel(ref, level);
            if (!isEnchantEnabled(meta.category(), meta.key())
                    && !(illegalEnchantsAlways.get() && overMax)) {
                continue;
            }

            String shortName = limitCodePoints(meta.shortName(), ENCHANT_NAME_MAX_CODEPOINTS);
            String text = singleLevel && level == 1
                    ? shortName
                    : shortName + level;

            int color = overMax
                    ? IllegalItemUtil.illegalColor()
                    : (level == value.getMaxLevel() ? TopEnchantUtil.topColor() : 0xFFFFFFFF);

            parts.add(new EnchantLine(text, color));
        }

        return parts;
    }

    private static String limitCodePoints(String value, int maxCodePoints) {
        if (value == null || value.isEmpty() || maxCodePoints <= 0) return "";
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) return value;
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private boolean isEnchantEnabled(String category, String path) {
        Map<String, Boolean> map = switch (category) {
            case "armor" -> enchantArmor.get();
            case "melee" -> enchantMelee.get();
            case "tools" -> enchantTools.get();
            case "bow" -> enchantBow.get();
            case "fishing" -> enchantFishing.get();
            case "trident" -> enchantTrident.get();
            case "crossbow" -> enchantCrossbow.get();
            case "mace" -> enchantMace.get();
            default -> enchantOther.get();
        };
        return map.getOrDefault(path, false);
    }

    private boolean shouldRenderOpponentStuff(Player p) {
        if (p == null || mc.player == null) return false;
        if (p == mc.player) return false;
        if (!opponentAppleCooldowns.get()) return false;
        if (!isOpponentAppleTrackingEnabled()) return false;
        if (!PvpTargetState.isTargetInPvp(p.getUUID()) && !hasOpponentCooldowns(p.getUUID())) return false;
        double maxDist = OPPONENT_DISTANCE;
        return !(maxDist > 0) || !(mc.player.distanceToSqr(p) > maxDist * maxDist);
    }

    private boolean isOpponentAppleTrackingEnabled() {
        PvpCooldowns mod = Modules.get(PvpCooldowns.class);
        return mod != null && mod.isSystemEnabled();
    }

    private void drawOpponentCooldownsStackedIcons(GuiGraphicsExtractor ctx, Player player, NameplateLayout layout, int centerX, int nameY) {
        if (!shouldRenderOpponentStuff(player)) return;

        int startX = computeOpponentStackX(layout, centerX);
        int startY = computeOpponentStackY(nameY);

        int y = startY;
        int seed = 0;
        for (Item item : CooldownRegistry.trackedItems()) {
            ItemCooldownSnapshot snapshot = OpponentCooldownManager.snapshot(player.getUUID(), item);
            if (!snapshot.visible()) continue;

            ItemStack icon = new ItemStack(item);
            float scale = ICON_SIZE / 16f;
            Renderer2D.COLOR.item(icon, startX, y, scale, seed++, Renderer2D.ITEM_OVERLAY_NONE, null);

            y += EFFECT_LINE_HEIGHT;
        }
    }

    private void drawOpponentCooldownsStackedText(TextRenderer textRenderer, Player player, NameplateLayout layout, int centerX, int nameY) {
        if (!shouldRenderOpponentStuff(player)) return;

        int startX = computeOpponentStackX(layout, centerX);
        int startY = computeOpponentStackY(nameY);

        int timeColor = PvpTargetState.isTargetInPvp(player.getUUID())
                ? OPP_TIME_COLOR
                : OPP_TIME_COLOR_GRACE;
        int y = startY;
        for (Item item : CooldownRegistry.trackedItems()) {
            ItemCooldownSnapshot snapshot = OpponentCooldownManager.snapshot(player.getUUID(), item);
            if (!snapshot.visible()) continue;

            String prefix = snapshot.compactText();
            if (!prefix.isEmpty()) {
                int textX = startX + ICON_SIZE + 6;
                int textY = y + 4;
                textRenderer.render(prefix + " ", textX, textY,
                        new RenderColor(foregroundColor(snapshot.cooling() ? timeColor : OPP_TIME_COLOR_GRACE)), true);
            }

            y += EFFECT_LINE_HEIGHT;
        }
    }

    private Vec3 obtainEntityLerpedPos(Entity e, float tickDelta) {
        try {
            return e.getPosition(tickDelta);
        } catch (NoSuchMethodError ex) {
            if (e instanceof IEntity access) {
                return access.get$InstantRenderPos().lerp(e.position(), tickDelta);
            }
            return e.position();
        }
    }

    private ScreenRect projectBoxScreen(AABB box, float tickDelta) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        boolean any = false;
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vec3 screen = ScreenProjection.worldToScreen(new Vec3(x, y, z), tickDelta);
                    if (screen == null) continue;
                    any = true;
                    minX = Math.min(minX, screen.x);
                    minY = Math.min(minY, screen.y);
                    maxX = Math.max(maxX, screen.x);
                    maxY = Math.max(maxY, screen.y);
                }
            }
        }
        if (!any) return null;
        return new ScreenRect(minX, minY, maxX, maxY);
    }

    private ItemStack[] getEquipmentSlots(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        return new ItemStack[]{main, boots, legs, chest, head, off};
    }

    private List<List<EnchantLine>> buildEnchantLines(ItemStack[] slots) {
        List<List<EnchantLine>> result = new ArrayList<>(slots.length);
        for (ItemStack stack : slots) {
            if (stack.isEmpty()) {
                result.add(List.of());
                continue;
            }
            List<EnchantLine> lines = formatEnchantments(stack);
            result.add(lines.isEmpty() ? List.of() : lines);
        }
        return result;
    }

    private int maxEnchantLines(List<List<EnchantLine>> lines) {
        int max = 0;
        for (List<EnchantLine> entry : lines) {
            int size = entry.size();
            if (size > max) max = size;
        }
        return max;
    }

    private enum PresentationMode implements EnumValue.IdProvider {
        TWO_D("2d"),
        THREE_D("3d"),
        HYBRID("hybrid");

        private final String id;

        PresentationMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private static final class EffectCache {
        int lastTick = -1;
        List<MobEffectInstance> effects = List.of();
    }

    private static final class EnchantCache {
        int lastTick = -1;
        int signature = 0;
        List<List<EnchantLine>> lines = List.of();
        int maxLines = 0;
    }

    private record ScreenRect(double minX, double minY, double maxX, double maxY) {
    }

    private record NameplateLayout(int nameX, int nameY, int width, int height, float labelScale,
                                   String pingText, String distText, float bgX, float bgY, float bgW, float bgH,
                                   int pingWidth, int pingGap, int nameWidth, int hpWidth, int hpGap,
                                   boolean renderHead, float headX, float headY, float headSize,
                                   float dividerX, float dividerY, float dividerW, float dividerH,
                                   boolean renderTotemBadge, float totemDividerX, float totemDividerY,
                                   float totemDividerW, float totemDividerH, float totemBadgeX, float totemBadgeY,
                                   float totemBadgeW, float totemBadgeH, float totemIconSize) {
    }

    private record EnchantLine(String text, int color) {
    }

    private record EquipmentLayout(double[] centers, double rowWidth) {
        private static EquipmentLayout empty() {
            return new EquipmentLayout(new double[0], 0.0);
        }
    }

    private record WorldRenderEntry(Player player,
                                    Vec3 anchor,
                                    double distance,
                                    double worldScale,
                                    float alpha,
                                    boolean renderNameplate,
                                    boolean renderSlots,
                                    boolean renderEffects,
                                    ItemStack[] slots,
                                    List<List<EnchantLine>> enchantLines,
                                    int maxEnchantLines,
                                    List<MobEffectInstance> effects,
                                    int totemPopCount,
                                    float totemPopAnimation,
                                    ItemStack[] worldItems) {
    }

    private record RenderEntry(Player player, double dist, int centerX, int nameY, int itemsY,
                               boolean renderSlots, boolean renderNameplate,
                               boolean renderEffects, LabelInfo labelInfo, int infoColor,
                               NameplateLayout nameplateLayout,
                               ItemStack[] slots, List<List<EnchantLine>> enchantLines, int maxEnchantLines,
                               List<MobEffectInstance> effects, int totemPopCount, float totemPopAnimation,
                               float presentationAlpha) {
    }

    private record LabelInfo(String pvpPrefix, Component styledName, String name, int ping, String pingText, String hp,
                             int infoColor) {
    }
}
