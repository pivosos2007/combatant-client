/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.misc.AutoReconnect;
import combatant.client.util.screen.ClientScreen;

@ModuleInfo(
        id = "boatfly",
        displayName = "BoatFly",
        aliases = {"BoatFlight", "BoatClip"},
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.RAGE,
        description = "module.boatfly.description")
public final class BoatFly extends Module {

    private static volatile boolean singleplayerBoatNoClip;
    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Double> speed = num("speed", 0.8, 0.1, 5.0);
    private final NumberValue<Double> verticalSpeed = num("vertical_speed", 0.6, 0.1, 3.0);
    private final BooleanValue passThroughBlocks = bool("pass_through_blocks", true);
    private final BooleanValue logoutAtHeight = bool("logout_at_height", false);
    private final NumberValue<Integer> logoutHeight = visibleWhen(num("logout_height", 256, 64, 320), logoutAtHeight::get);
    private final BooleanValue reconnectAfterLogout = visibleWhen(bool("reconnect_after_logout", true), logoutAtHeight::get);
    private final NumberValue<Integer> reconnectDelay = visibleWhen(num("reconnect_delay", 5, 1, 60), reconnectAfterLogout::get);

    private Boat activeBoat;
    private boolean previousNoPhysics;
    private boolean previousNoGravity;
    private boolean logoutTriggered;
    private ServerData reconnectServer;
    private int reconnectTicks = -1;

    public static boolean shouldApplySingleplayerBoatNoClip() {
        return singleplayerBoatNoClip;
    }

    @Override
    public void onTick() {
        if (reconnectTicks >= 0) {
            tickReconnect();
            return;
        }
        if (mc.player == null) return;
        if (mc.level != null) logoutTriggered = false;

        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof Boat boat)) {
            singleplayerBoatNoClip = false;
            restoreBoat();
            return;
        }
        if (activeBoat != boat) {
            restoreBoat();
            activeBoat = boat;
            previousNoPhysics = boat.noPhysics;
            previousNoGravity = boat.isNoGravity();
        }

        boat.noPhysics = passThroughBlocks.get();
        boat.setNoGravity(true);
        singleplayerBoatNoClip = passThroughBlocks.get() && mc.hasSingleplayerServer();
        applyMovement(boat);

        if (!logoutTriggered && logoutAtHeight.get() && boat.getY() >= logoutHeight.get()) {
            logoutTriggered = true;
            singleplayerBoatNoClip = false;
            if (reconnectAfterLogout.get() && !Modules.enabled(AutoReconnect.class)) {
                ServerData server = mc.getCurrentServer();
                if (server != null && ServerAddress.isValidAddress(server.ip)) {
                    reconnectServer = server;
                    reconnectTicks = reconnectDelay.get() * 20;
                }
            }
            if (mc.getConnection() != null) {
                mc.getConnection().getConnection().disconnect(Component.literal("BoatFly height limit reached"));
            }
            restoreBoat();
        }
    }

    private void applyMovement(Boat boat) {
        var options = mc.options;
        double forward = (options.keyUp.isDown() ? 1.0 : 0.0) - (options.keyDown.isDown() ? 1.0 : 0.0);
        double strafe = (options.keyLeft.isDown() ? 1.0 : 0.0) - (options.keyRight.isDown() ? 1.0 : 0.0);
        double vertical = (options.keyJump.isDown() ? 1.0 : 0.0) - (options.keySprint.isDown() ? 1.0 : 0.0);
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 right = new Vec3(look.z, 0.0, -look.x);
        if (right.lengthSqr() > 0.0) right = right.normalize();
        Vec3 direction = look.scale(forward).add(right.scale(strafe));
        if (direction.lengthSqr() > 1.0) direction = direction.normalize();
        Vec3 targetVelocity = new Vec3(
            direction.x * speed.get(),
            direction.y * speed.get() + vertical * verticalSpeed.get(),
            direction.z * speed.get()
        );
        boat.setYRot(mc.player.getYRot());
        boat.setXRot(mc.player.getXRot());
        boat.setDeltaMovement(boat.getDeltaMovement().lerp(targetVelocity, 0.55));
    }

    private void tickReconnect() {
        if (!(ClientScreen.current() instanceof DisconnectedScreen) || reconnectServer == null) return;
        if (reconnectTicks-- > 0) return;
        String addressText = reconnectServer.ip;
        ServerData server = reconnectServer;
        reconnectServer = null;
        reconnectTicks = -1;
        if (ServerAddress.isValidAddress(addressText)) {
            ConnectScreen.startConnecting(new TitleScreen(false), mc, ServerAddress.parseString(addressText), server, false, null);
        }
    }

    private void restoreBoat() {
        if (activeBoat == null) return;
        activeBoat.noPhysics = previousNoPhysics;
        activeBoat.setNoGravity(previousNoGravity);
        activeBoat = null;
    }

    @Override
    public void onDisable() {
        singleplayerBoatNoClip = false;
        restoreBoat();
        reconnectServer = null;
        reconnectTicks = -1;
        logoutTriggered = false;
    }
}