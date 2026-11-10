/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.KeyBindValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.PlayerCollisionRestitutionEvent;
import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.gui.hud.gauges.GaugeConnect;
import combatant.client.features.gui.hud.gauges.GaugeRegistry;
import combatant.client.features.gui.hud.draggable.impl.HudNotifier;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleListInfoProvider;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.input.minecraft.MinecraftInputAdapter;
import combatant.client.util.player.MovementUtil;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@ModuleInfo(id = "longjump", displayName = "LongJump", aliases = {"LJ"},
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.RAGE,
        description = "module.longjump.description")
public final class LongJump extends Module {
    private static final int SLIME_COOLDOWN = 8;
    private static final int BOAT_COOLDOWN = 25;
    private static final int WATER_COOLDOWN = 12;
    private static final int SHULKER_COOLDOWN = 25;
    private static final double BOAT_RADIUS = 2.5;

    private static final String BOOST_SLIME = "slime";
    private static final String BOOST_SHULKER = "shulker";
    private static final String BOOST_BOAT = "boat";
    private static final String BOOST_WATER = "water";

    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanMapValue boosts = group("longjumpBoosts", "boosts", defaultBoosts());
    private final EnumValue<Activation> activation = enumSetting("activation", "activation", Activation.AUTO, Activation.values());
    private final FunctionBindSetting launchKey = action("launch_key",
            new KeyBindValue("longJumpLaunchKey", "NONE"), BindMode.PRESS);
    { launchKey.visibleWhen(() -> activation.get() == Activation.BIND); }
    private final Vulcan297LongJumpController controller = new Vulcan297LongJumpController();
    private final AtomicBoolean correctionPending = new AtomicBoolean();

    private int lastSlimeTick = Integer.MIN_VALUE;
    private int lastBoatTick = Integer.MIN_VALUE;
    private int lastWaterTick = Integer.MIN_VALUE;
    private int lastShulkerTick = Integer.MIN_VALUE;
    private boolean jumpDown;
    private LocalPlayer previousPlayer;
    private ClientLevel previousLevel;

    private static Map<String, Boolean> defaultBoosts() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(BOOST_SLIME, true);
        defaults.put(BOOST_SHULKER, true);
        defaults.put(BOOST_BOAT, true);
        defaults.put(BOOST_WATER, false);
        return defaults;
    }

    @ModuleListInfoProvider
    private String modeLabel() {
        return activation.get() == Activation.AUTO ? "Auto" : "Bind";
    }

    @Override
    public void onEnable() { reset(); }

    @Override
    public void onDisable() {
        GaugeRegistry.removeOwner("longjump");
        reset();
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            GaugeRegistry.removeOwner("longjump");
            reset();
            return;
        }
        if (player != previousPlayer || mc.level != previousLevel) {
            reset();
            previousPlayer = player;
            previousLevel = mc.level;
        }
        if (correctionPending.getAndSet(false)) {
            controller.onServerCorrection(player.tickCount);
        }
        controller.onTick(player.tickCount, player.onGround());

        // Read the physical jump key, including while a container screen owns Minecraft's key binding.
        boolean jump = MinecraftInputAdapter.isPressed(mc.options.keyJump);
        boolean jumpEdge = jump && !jumpDown;
        jumpDown = jump;
        boolean trigger = activation.get() == Activation.AUTO ? jumpEdge : launchKey.isPressedAllowScreen();

        // All manual-launch paths consume the same press edge. Never notify on a held key.
        if (trigger && canControl(player)) attemptLaunch(player);
        GaugeRegistry.updateAnnotated("longjump", this);
    }

    public boolean isManagingVulcan297(LocalPlayer player) {
        return isEnabled() && player != null && player == mc.player && canControl(player)
                && controller.isManaging(player.tickCount);
    }

    private boolean canControl(LocalPlayer player) {
        return !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying
                && !player.isSpectator() && !player.isInLava();
    }

    /** The ready messages must use exactly the predicates used by the launch paths. */
    @GaugeConnect(id = "boat_ready", labelKey = "hud.gauges.longjump_boat",
            kind = GaugeRegistry.Kind.MESSAGE, hideAfterMillis = 0)
    private GaugeRegistry.Sample boatReadyGauge() {
        LocalPlayer p = mc.player;
        boolean available = p != null && isEnabled() && boosts.get(BOOST_BOAT)
                && hasLaunchInput() && boatFailure(p) == null;
        return new GaugeRegistry.Sample(1f, available, available, null, I18n.get("hud.gauges.ready"));
    }

    @GaugeConnect(id = "shulker_ready", labelKey = "hud.gauges.longjump_shulker",
            kind = GaugeRegistry.Kind.MESSAGE, hideAfterMillis = 0)
    private GaugeRegistry.Sample shulkerReadyGauge() {
        LocalPlayer p = mc.player;
        boolean available = p != null && isEnabled() && boosts.get(BOOST_SHULKER)
                && hasLaunchInput() && shulkerFailure(p) == null;
        return new GaugeRegistry.Sample(1f, available, available, null, I18n.get("hud.gauges.ready"));
    }

    private boolean hasLaunchInput() {
        return activation.get() == Activation.AUTO || !"NONE".equalsIgnoreCase(launchKey.get());
    }

    /** A jump is not an attempt to use every installed boost. Only inspect relevant context. */
    private void attemptLaunch(LocalPlayer player) {
        if (ClientScreen.current() instanceof ShulkerBoxScreen) {
            if (!boosts.get(BOOST_SHULKER)) return;
            String failure = shulkerFailure(player);
            if (failure == null) launchShulker(player);
            else launchFailed(failure);
            return;
        }
        if (ClientScreen.current() != null) return;

        // Do not generate "ground"/"no boat" warnings for every ordinary jump.
        if (boosts.get(BOOST_BOAT) && hasNearbyBoat(player)) {
            String failure = boatFailure(player);
            if (failure == null) launchBoat(player);
            else launchFailed(failure);
            return;
        }
        if (boosts.get(BOOST_WATER) && player.isInWater()) {
            String failure = waterFailure(player);
            if (failure == null) launchWater(player);
            else launchFailed(failure);
        }
        // Slime is collision-driven. No unrelated notifications on its normal jump key.
    }

    private void launchFailed(String reason) {
        HudNotifier.pushMessage(I18n.get("notification.longjump.failed." + reason), HudNotifier.NotifyType.WARN);
    }

    private boolean hasNearbyBoat(LocalPlayer player) {
        return !nearbyBoats(player).isEmpty();
    }

    /**
     * Recognizes nearby boat entities without requiring a passenger or ground-state flag.
     * The check uses entity proximity rather than a simulated support surface.
     */
    private java.util.List<AbstractBoat> nearbyBoats(LocalPlayer player) {
        return player.level().getEntitiesOfClass(AbstractBoat.class,
                player.getBoundingBox().inflate(BOAT_RADIUS));
    }

    private String boatFailure(LocalPlayer player) {
        if (!canControl(player)) return "unavailable";
        if (ClientScreen.current() != null) return "close_screen";
        if (!hasNearbyBoat(player)) return "no_boat";
        if (!ready(player.tickCount, lastBoatTick, BOAT_COOLDOWN)) return "cooldown";
        if (!controller.canBeginBoatLaunch(player.tickCount)) return "flight_lock";
        return null;
    }

    private String shulkerFailure(LocalPlayer player) {
        if (!canControl(player)) return "unavailable";
        if (!(ClientScreen.current() instanceof ShulkerBoxScreen)) return "open_shulker";
        if (!ready(player.tickCount, lastShulkerTick, SHULKER_COOLDOWN)) return "cooldown";
        if (!controller.canBeginBoatLaunch(player.tickCount)) return "flight_lock";
        return null;
    }

    private String waterFailure(LocalPlayer player) {
        if (!canControl(player)) return "unavailable";
        if (ClientScreen.current() != null) return "close_screen";
        if (!player.isInWater() || player.isUnderWater()) return "water_surface";
        if (!ready(player.tickCount, lastWaterTick, WATER_COOLDOWN)) return "cooldown";
        return null;
    }

    @EventHandler
    private void onCollisionRestitution(PlayerCollisionRestitutionEvent event) {
        if (!boosts.get(BOOST_SLIME) || !event.getBlockState().is(Blocks.SLIME_BLOCK)
                || !event.isVerticalCollisionBelow() || event.getAttemptedMovement().y >= -0.08) return;
        LocalPlayer player = event.getPlayer();
        if (player != mc.player || !canControl(player) || player.isShiftKeyDown()
                || player.isInWater()) return;
        int tick = player.tickCount;
        if (!ready(tick, lastSlimeTick, SLIME_COOLDOWN)) return;
        Vec3 motion = event.getVelocity();
        if (motion == null || !Double.isFinite(motion.y) || motion.y <= 0
                || !controller.beginSlimeBounce(tick)) return;
        double y = LongJumpLaunchRules.vulcanSlimeBounceVelocity(motion.y);
        lastSlimeTick = tick;
        if (y > motion.y) event.setVelocity(new Vec3(motion.x, y, motion.z));
    }

    @EventHandler
    private void onPositionCorrection(PacketEvent.Receive event) {
        if (event.isOriginal() && event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            correctionPending.set(true);
        }
    }

    private void launchShulker(LocalPlayer player) {
        if (shulkerFailure(player) != null) return;
        Vec3 motion = player.getDeltaMovement();
        if (!controller.beginShulkerLaunch(player.tickCount)) {
            launchFailed("flight_lock");
            return;
        }
        player.setDeltaMovement(motion.x, LongJumpLaunchRules.shulkerImpulse(motion.y), motion.z);
        lastShulkerTick = player.tickCount;
    }

    private void launchBoat(LocalPlayer player) {
        if (boatFailure(player) != null) return;
        if (!controller.beginBoatLaunch(player.tickCount)) {
            launchFailed("flight_lock");
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        double[] direction = MovementUtil.forward(1.0);
        double len = Math.hypot(direction[0], direction[1]);
        double x, z;
        if (len > 1.0E-5) {
            x = direction[0] / len;
            z = direction[1] / len;
        } else {
            double yaw = Math.toRadians(player.getYRot());
            x = -Math.sin(yaw);
            z = Math.cos(yaw);
        }
        double speed = Math.min(Math.max(Math.hypot(motion.x, motion.z), 0.42), 0.56);
        player.setDeltaMovement(x * speed, LongJumpLaunchRules.boatImpulse(motion.y), z * speed);
        lastBoatTick = player.tickCount;
    }

    private void launchWater(LocalPlayer player) {
        if (waterFailure(player) != null) return;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, LongJumpLaunchRules.waterImpulse(motion.y), motion.z);
        lastWaterTick = player.tickCount;
    }

    private static boolean ready(int tick, int last, int cooldown) {
        return last == Integer.MIN_VALUE || tick >= last && tick - last >= cooldown;
    }

    private void reset() {
        lastSlimeTick = Integer.MIN_VALUE;
        lastBoatTick = Integer.MIN_VALUE;
        lastWaterTick = Integer.MIN_VALUE;
        lastShulkerTick = Integer.MIN_VALUE;
        previousPlayer = null;
        previousLevel = null;
        controller.reset();
        correctionPending.set(false);
        jumpDown = false;
    }

    public enum Activation { AUTO, BIND }
}
