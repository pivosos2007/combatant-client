/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventCollision;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PlayerJumpEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

import java.util.LinkedHashMap;
import java.util.Map;

//todo Description
@ModuleInfo(
        id = "nostun",
        displayName = "NoStun",
        category = ModuleCategory.MOVEMENT
)
public class NoStun extends Module {

    private static final float DEFAULT_SLIPPERINESS = 0.6F;
    private static final float DEFAULT_VELOCITY_MULTIPLIER = 1.0F;
    private static final double VULCAN_297_WEB_BUFFER_MAX = 7.0;
    private static final double VULCAN_297_WEB_BUFFER_DECAY = 0.15;
    private static final double VULCAN_297_WEB_FULL_COST = 1.0;
    private static final double VULCAN_297_SPEED_E_BUFFER_MAX = 2.0;
    private static final double VULCAN_297_SPEED_E_BUFFER_DECAY = 0.025;
    private static final int VULCAN_297_SLOWNESS_JUMP_WINDOW_TICKS = 5;
    private static final double VULCAN_297_SLOWNESS_EXEMPT_RATIO = 1.0;
    private static final Identifier SLOWNESS_MOVEMENT_SPEED_MODIFIER_ID = Identifier.withDefaultNamespace("effect.slowness");
    private static final String SETTING_MODE = "mode";
    private static final String SETTING_FUNCTIONS = "functions";
    private static final String SETTING_ENV_BLOCKS = "env_blocks";
    private static final String SETTING_DEBUFFS = "debuffs";
    private static final String SETTING_EAT_SPEED = "eat_speed";
    private static final String SETTING_VULCAN_WEB_RATIO = "vulcan297_web_ratio";
    private static final String SETTING_VULCAN_SOUL_RATIO = "vulcan297_soul_sand_ratio";
    private static final String FN_USE_SPEED = "use_speed";
    private static final String FN_ENV_BLOCKS = "env_blocks";
    private static final String FN_NO_HURT_STUN = "no_hurt_stun";
    private static final String FN_DEBUFFS = "debuffs";
    private static final String ENV_WEB = "web";
    private static final String ENV_HONEY = "honey";
    private static final String ENV_SLIME = "slime";
    private static final String ENV_SOUL = "soul_sand";
    private static final String ENV_BERRY = "sweet_berry";
    private static final String DEBUFF_SLOWNESS = "slowness";
    private static final String DEBUFF_BLINDNESS = "blindness";
    private final Minecraft mc = Minecraft.getInstance();
    //todo ну хз на прыжках еще флагует блять так что хз хз
    private final EnumValue<Mode> mode =
            enumSetting("noStunMode", SETTING_MODE, Mode.NORMAL, Mode.values());
    private final BooleanMapValue functions =
            group("noStunFunctions", SETTING_FUNCTIONS, defaultFunctions());
    private final BooleanMapValue envBlocks =
            visibleWhen(group("noStunEnvBlocks", SETTING_ENV_BLOCKS, defaultEnvBlocks()), () -> functions.get(FN_ENV_BLOCKS));
    private final NumberValue<Double> vulcan297WebRatio =
            visibleWhen(num("noStunVulcan297WebRatio", SETTING_VULCAN_WEB_RATIO, 0.35, 0.0, 1.0),
                    () -> functions.get(FN_ENV_BLOCKS) && envBlocks.get(ENV_WEB) && isVulcan297());
    private final NumberValue<Double> vulcan297SoulSandRatio =
            visibleWhen(num("noStunVulcan297SoulSandRatio", SETTING_VULCAN_SOUL_RATIO, 0.35, 0.0, 1.0),
                    () -> functions.get(FN_ENV_BLOCKS) && envBlocks.get(ENV_SOUL) && isVulcan297());
    private final BooleanMapValue debuffs =
            visibleWhen(group("noStunDebuffs", SETTING_DEBUFFS, defaultDebuffs()), () -> functions.get(FN_DEBUFFS));
    private final NumberValue<Double> eatSpeed =
            visibleWhen(num("noStunEatSpeed", SETTING_EAT_SPEED, 0.0, 0.0, 1.0), () -> functions.get(FN_USE_SPEED));
    private double vulcan297WebBudget;
    private int vulcan297LastWebChargeAge = -1;
    private int vulcan297LastWebDecayAge = -1;
    private double vulcan297SlownessSpeedEBuffer;
    private int vulcan297SlownessJumpTicks;
    private int vulcan297LastSlownessDecisionAge = -1;
    private double vulcan297LastSlownessRatio;

    private static double blend(double ratio, double vanilla, double normalized) {
        return Mth.lerp(Mth.clamp(ratio, 0.0, 1.0), vanilla, normalized);
    }

    public static String fnUseSpeed() {
        return FN_USE_SPEED;
    }

    public static String fnEnvBlocks() {
        return FN_ENV_BLOCKS;
    }

    public static String fnNoHurtStun() {
        return FN_NO_HURT_STUN;
    }

    public static String fnDebuffs() {
        return FN_DEBUFFS;
    }

    public static String envWeb() {
        return ENV_WEB;
    }

    public static String envHoney() {
        return ENV_HONEY;
    }

    public static String envSlime() {
        return ENV_SLIME;
    }

    public static String envSoulSand() {
        return ENV_SOUL;
    }

    public static String envBerry() {
        return ENV_BERRY;
    }

    private static Map<String, Boolean> defaultFunctions() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put(FN_USE_SPEED, true);
        m.put(FN_ENV_BLOCKS, true);
        m.put(FN_NO_HURT_STUN, true);
        m.put(FN_DEBUFFS, true);
        return m;
    }

    private static Map<String, Boolean> defaultEnvBlocks() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put(ENV_WEB, true);
        m.put(ENV_HONEY, true);
        m.put(ENV_SLIME, true);
        m.put(ENV_BERRY, true);
        m.put(ENV_SOUL, true);
        return m;
    }

    private static Map<String, Boolean> defaultDebuffs() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put(DEBUFF_SLOWNESS, true);
        m.put(DEBUFF_BLINDNESS, true);
        return m;
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (shouldBlockWebJump() && event.isJump()) {
            event.setJump(false);
        }
    }

    @EventHandler
    private void onPlayerJump(PlayerJumpEvent event) {
        if (shouldBlockWebJump()) {
            event.cancel();
            return;
        }

        if (shouldUseVulcan297SlownessLogic(mc.player)) {
            vulcan297SlownessJumpTicks = VULCAN_297_SLOWNESS_JUMP_WINDOW_TICKS;
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || !isVulcan297() || mc.player == null || mc.level == null) {
            resetVulcan297WebBudget();
            resetVulcan297SlownessSpeedEBuffer();
            return;
        }

        if (!isInsideWeb(mc.player)) {
            decayVulcan297WebBudget();
            vulcan297LastWebChargeAge = -1;
        }

        if (vulcan297SlownessJumpTicks > 0) {
            vulcan297SlownessJumpTicks--;
        }
        if (!shouldUseVulcan297SlownessLogic(mc.player)) {
            resetVulcan297SlownessSpeedEBuffer();
        }
    }

    @EventHandler
    private void onCollision(EventCollision event) {
        if (shouldMakeWebSolidWhileGliding(event.getState(), event.getPos())) {
            event.setState(Blocks.BARRIER.defaultBlockState());
        }
    }

    @Override
    public void onDisable() {
        resetVulcan297WebBudget();
        resetVulcan297SlownessSpeedEBuffer();
    }

    public boolean isFunctionEnabled(String key) {
        return functions.get(key);
    }

    public boolean isEnvBlockEnabled(String key) {
        return envBlocks.get(key);
    }

    public boolean isEnvBlocksEnabled() {
        return functions.get(FN_ENV_BLOCKS);
    }

    public boolean isVulcan297() {
        return mode.get() == Mode.VULCAN_297;
    }

    public float getEatSpeed01() {
        return (float) Mth.clamp(eatSpeed.get(), 0.0, 1.0);
    }

    public boolean shouldIgnoreBlindness(Player player) {
        return isEnabled() && isLocalPlayer(player) && functions.get(FN_DEBUFFS) && debuffs.get(DEBUFF_BLINDNESS);
    }

    public double getMovementSpeedAttributeWithoutSlowness(Player player, double original) {
        if (!isEnabled() || !isLocalPlayer(player) || !functions.get(FN_DEBUFFS) || !debuffs.get(DEBUFF_SLOWNESS)) {
            return original;
        }

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null || !movementSpeed.hasModifier(SLOWNESS_MOVEMENT_SPEED_MODIFIER_ID)) {
            return original;
        }

        double noSlowness = computeMovementSpeedWithoutSlownessModifier(movementSpeed);
        if (!isVulcan297()) {
            return noSlowness;
        }

        double ratio = getVulcan297SlownessRatio(player, movementSpeed);
        return blend(ratio, original, noSlowness);
    }

    private double computeMovementSpeedWithoutSlownessModifier(AttributeInstance movementSpeed) {
        double base = movementSpeed.getBaseValue();
        double value = base;

        for (AttributeModifier modifier : movementSpeed.getModifiers()) {
            if (isMovementSpeedModifier(modifier, AttributeModifier.Operation.ADD_VALUE)) {
                value += modifier.amount();
            }
        }

        double multipliedBase = value;
        for (AttributeModifier modifier : movementSpeed.getModifiers()) {
            if (isMovementSpeedModifier(modifier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)) {
                multipliedBase += base * modifier.amount();
            }
        }

        for (AttributeModifier modifier : movementSpeed.getModifiers()) {
            if (isMovementSpeedModifier(modifier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)) {
                multipliedBase *= 1.0 + modifier.amount();
            }
        }

        return Attributes.MOVEMENT_SPEED.value().sanitizeValue(multipliedBase);
    }

    private boolean isMovementSpeedModifier(AttributeModifier modifier, AttributeModifier.Operation operation) {
        return modifier.operation() == operation && !modifier.is(SLOWNESS_MOVEMENT_SPEED_MODIFIER_ID);
    }

    private double getVulcan297SlownessRatio(Player player, AttributeInstance movementSpeed) {
        int age = player.tickCount;
        if (age == vulcan297LastSlownessDecisionAge) {
            return vulcan297LastSlownessRatio;
        }

        int slownessLevel = getSlownessLevel(movementSpeed);
        double ratio;
        if (isVulcan297SpeedEPredictionExempt(player)) {
            ratio = VULCAN_297_SLOWNESS_EXEMPT_RATIO;
            decayVulcan297SlownessSpeedEBuffer();
        } else if (shouldUseVulcan297SlownessJumpRatio(player)) {
            double cost = getVulcan297SlownessJumpCost(slownessLevel);
            if (canChargeVulcan297SlownessSpeedEBuffer(cost)) {
                ratio = getVulcan297SlownessJumpRatio(slownessLevel);
                chargeVulcan297SlownessSpeedEBuffer(cost);
            } else {
                ratio = getVulcan297SlownessWalkRatio(slownessLevel);
                decayVulcan297SlownessSpeedEBuffer();
            }
        } else {
            ratio = getVulcan297SlownessWalkRatio(slownessLevel);
            decayVulcan297SlownessSpeedEBuffer();
        }

        vulcan297LastSlownessDecisionAge = age;
        vulcan297LastSlownessRatio = ratio;
        return ratio;
    }

    private int getSlownessLevel(AttributeInstance movementSpeed) {
        AttributeModifier modifier = movementSpeed.getModifier(SLOWNESS_MOVEMENT_SPEED_MODIFIER_ID);
        if (modifier == null) {
            return 0;
        }

        return Math.max(1, (int) Math.round(Math.abs(modifier.amount()) / 0.15));
    }

    private double getVulcan297SlownessWalkRatio(int slownessLevel) {
        if (slownessLevel >= 6) return 0.28;
        if (slownessLevel >= 4) return 0.42;
        if (slownessLevel >= 2) return 0.70;
        return 1.0;
    }

    private double getVulcan297SlownessJumpRatio(int slownessLevel) {
        if (slownessLevel >= 6) return 0.58;
        if (slownessLevel >= 4) return 0.78;
        return 1.0;
    }

    private double getVulcan297SlownessJumpCost(int slownessLevel) {
        if (slownessLevel >= 6) return 0.70;
        if (slownessLevel >= 4) return 0.50;
        return 0.35;
    }

    private boolean shouldUseVulcan297SlownessJumpRatio(Player player) {
        return player != null
                && vulcan297SlownessJumpTicks > 0
                && !player.onGround()
                && !player.isFallFlying()
                && player.getDeltaMovement().y > -0.08;
    }

    private boolean shouldUseVulcan297SlownessLogic(Player player) {
        if (player == null || !isEnabled() || !isVulcan297() || !functions.get(FN_DEBUFFS) || !debuffs.get(DEBUFF_SLOWNESS)) {
            return false;
        }

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return movementSpeed != null && movementSpeed.hasModifier(SLOWNESS_MOVEMENT_SPEED_MODIFIER_ID);
    }

    private boolean canChargeVulcan297SlownessSpeedEBuffer(double cost) {
        return vulcan297SlownessSpeedEBuffer + cost <= VULCAN_297_SPEED_E_BUFFER_MAX;
    }

    private void chargeVulcan297SlownessSpeedEBuffer(double cost) {
        vulcan297SlownessSpeedEBuffer = Math.min(VULCAN_297_SPEED_E_BUFFER_MAX, vulcan297SlownessSpeedEBuffer + cost);
    }

    private void decayVulcan297SlownessSpeedEBuffer() {
        vulcan297SlownessSpeedEBuffer = Math.max(0.0, vulcan297SlownessSpeedEBuffer - VULCAN_297_SPEED_E_BUFFER_DECAY);
    }

    private void resetVulcan297SlownessSpeedEBuffer() {
        vulcan297SlownessSpeedEBuffer = 0.0;
        vulcan297SlownessJumpTicks = 0;
        vulcan297LastSlownessDecisionAge = -1;
        vulcan297LastSlownessRatio = 0.0;
    }

    private boolean isVulcan297SpeedEPredictionExempt(Player player) {
        if (player == null || player.level() == null) return false;
        if (player.getAbilities().instabuild || player.getAbilities().flying) return true;
        if (player.isFallFlying() || player.isAutoSpinAttack() || player.isPassenger() || player.isSleeping())
            return true;

        BlockPos feetPos = player.blockPosition();
        Block feet = player.level().getBlockState(feetPos).getBlock();
        Block below = player.level().getBlockState(feetPos.below()).getBlock();

        return isVulcan297SpeedEPredictionExemptBlock(feet)
                || isVulcan297SpeedEPredictionExemptBlock(below);
    }

    private boolean isVulcan297SpeedEPredictionExemptBlock(Block block) {
        return block == Blocks.COBWEB || block == Blocks.HONEY_BLOCK;
    }

    public Vec3 getSlowMovementOverride(BlockState state, Vec3 vanillaMultiplier) {
        if (!isEnabled() || state == null || vanillaMultiplier == null || !functions.get(FN_ENV_BLOCKS)) {
            return null;
        }

        if (state.is(Blocks.COBWEB) && envBlocks.get(ENV_WEB) && isVulcan297()) {
            if (shouldUseFullVulcan297WebBypass()) {
                return null;
            }

            decayVulcan297WebBudget();
            double horizontal = blend(vulcan297WebRatio.get(), vanillaMultiplier.x, 1.0);
            return new Vec3(horizontal, vanillaMultiplier.y, horizontal);
        }

        return null;
    }

    public boolean shouldCancelSlowMovement(BlockState state) {
        if (!isEnabled() || state == null || !functions.get(FN_ENV_BLOCKS)) {
            return false;
        }
        if (state.is(Blocks.COBWEB) && envBlocks.get(ENV_WEB)) {
            if (!isVulcan297()) {
                return true;
            }
            if (shouldUseFullVulcan297WebBypass()) {
                chargeVulcan297WebBudget();
                return true;
            }
            return false;
        }
        return state.is(Blocks.SWEET_BERRY_BUSH) && envBlocks.get(ENV_BERRY);
    }

    public Float getSlipperinessOverride(Block block, float vanilla) {
        if (!isEnabled() || block == null || !functions.get(FN_ENV_BLOCKS)) {
            return null;
        }

        if (block == Blocks.SLIME_BLOCK && envBlocks.get(ENV_SLIME)
                || block == Blocks.HONEY_BLOCK && envBlocks.get(ENV_HONEY)) {
            return isVulcan297()
                    ? (float) blend(vulcan297SoulSandRatio.get(), vanilla, DEFAULT_SLIPPERINESS)
                    : DEFAULT_SLIPPERINESS;
        }

        return null;
    }

    public Float getVelocityMultiplierOverride(Block block, float vanilla) {
        if (!isEnabled() || block == null || !functions.get(FN_ENV_BLOCKS)) {
            return null;
        }

        if (block == Blocks.HONEY_BLOCK && envBlocks.get(ENV_HONEY)) {
            return isVulcan297()
                    ? (float) blend(vulcan297SoulSandRatio.get(), vanilla, DEFAULT_VELOCITY_MULTIPLIER)
                    : DEFAULT_VELOCITY_MULTIPLIER;
        }

        if (block == Blocks.SOUL_SAND && envBlocks.get(ENV_SOUL)) {
            return getSoulSandVelocityMultiplier(vanilla);
        }

        return null;
    }

    public float getSoulSandVelocityMultiplier(float vanilla) {
        if (!isEnabled() || !functions.get(FN_ENV_BLOCKS) || !envBlocks.get(ENV_SOUL)) {
            return vanilla;
        }
        if (isVulcan297()) {
            return (float) blend(vulcan297SoulSandRatio.get(), vanilla, DEFAULT_VELOCITY_MULTIPLIER);
        }
        return vanilla < 1.0F ? 1.0F : vanilla;
    }

    private boolean shouldBlockWebJump() {
        return isEnabled()
                && functions.get(FN_ENV_BLOCKS)
                && envBlocks.get(ENV_WEB)
                && mc.player != null
                && isInsideWeb(mc.player);
    }

    private boolean shouldMakeWebSolidWhileGliding(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !state.is(Blocks.COBWEB)) return false;
        if (!isEnabled() || !isVulcan297() || !functions.get(FN_ENV_BLOCKS) || !envBlocks.get(ENV_WEB)) return false;
        if (mc.player == null || mc.level == null || !mc.player.isFallFlying() || mc.player.isSpectator()) return false;

        AABB movementBox = mc.player.getBoundingBox()
                .expandTowards(mc.player.getDeltaMovement())
                .inflate(0.2);
        return movementBox.intersects(new AABB(pos));
    }

    private boolean isInsideWeb(LocalPlayer player) {
        if (mc.level == null) return false;

        AABB box = player.getBoundingBox().deflate(1.0E-4);
        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (mc.level.getBlockState(new BlockPos(x, y, z)).is(Blocks.COBWEB)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean canUseVulcan297WebBurst() {
        return vulcan297WebBudget + VULCAN_297_WEB_FULL_COST <= VULCAN_297_WEB_BUFFER_MAX;
    }

    private boolean shouldUseFullVulcan297WebBypass() {
        return canUseVulcan297WebBurst();
    }

    private void chargeVulcan297WebBudget() {
        LocalPlayer player = mc.player;
        int age = player != null ? player.tickCount : -1;
        if (age == vulcan297LastWebChargeAge) {
            return;
        }

        vulcan297WebBudget = Math.min(VULCAN_297_WEB_BUFFER_MAX, vulcan297WebBudget + VULCAN_297_WEB_FULL_COST);
        vulcan297LastWebChargeAge = age;
    }

    private void decayVulcan297WebBudget() {
        LocalPlayer player = mc.player;
        int age = player != null ? player.tickCount : -1;
        if (age == vulcan297LastWebDecayAge) {
            return;
        }

        vulcan297WebBudget = Math.max(0.0, vulcan297WebBudget - VULCAN_297_WEB_BUFFER_DECAY);
        vulcan297LastWebDecayAge = age;
    }

    private void resetVulcan297WebBudget() {
        vulcan297WebBudget = 0.0;
        vulcan297LastWebChargeAge = -1;
        vulcan297LastWebDecayAge = -1;
    }

    private boolean isLocalPlayer(Player player) {
        return player != null && mc != null && player == mc.player;
    }

    public enum Mode {
        NORMAL,
        VULCAN_297
    }
}
