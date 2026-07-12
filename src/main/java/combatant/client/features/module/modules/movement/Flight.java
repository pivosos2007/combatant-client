/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.events.impl.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.*;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.LocalPlayerAccessor;
import combatant.client.mixins.accessors.ServerboundMovePlayerPacketAccessor;
import combatant.client.util.player.MovementUtil;

//todo Description
@ModuleInfo(
        id = "flight",
        displayName = "Flight",
        category = ModuleCategory.MOVEMENT
)
public class Flight extends Module {

    private final EnumValue<Mode> mode =
            enumMode("mode", Mode.ABILITIES, Mode.values());
    private final NumberValue<Double> speed =
            visibleWhen(num("speed", 0.1, 0.0, 5.0), this::usesSpeed);
    private final BooleanValue verticalSpeedMatch =
            visibleWhen(bool("vertical_speed_match", false), () -> mode.get() == Mode.VELOCITY);
    private final BooleanValue noSneak =
            visibleWhen(bool("no_sneak", false), () -> mode.get() == Mode.VELOCITY);
    private final EnumValue<AntiKickMode> antiKickMode =
            visibleWhen(enumMode("anti_kick_mode", AntiKickMode.PACKET, AntiKickMode.values()), this::supportsAntiKick);
    private final NumberValue<Integer> delay =
            visibleWhen(num("anti_kick_delay", 20, 1, 200), this::supportsAntiKick);
    private int delayLeft = delay.get();
    private final NumberValue<Integer> offTime =
            visibleWhen(num("anti_kick_off_time", 1, 1, 20), this::supportsAntiKick);
    private int offLeft = offTime.get();
    private boolean flip;
    private float lastYaw;
    private double lastPacketY = Double.MAX_VALUE;
    private Mode lastMode = mode.get();
    private int vulcanFlags;
    private boolean vulcanWait;
    private int grimTicks;

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        resetModeState();

        if (mode.get() == Mode.ABILITIES && !mc.player.isSpectator()) {
            mc.player.getAbilities().flying = true;
            if (!mc.player.getAbilities().instabuild) {
                mc.player.getAbilities().mayfly = true;
            }
        }

        if (mode.get() == Mode.VULCAN_286_113) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                    mc.player.getX(),
                    mc.player.getY() - 0.1,
                    mc.player.getZ(),
                    mc.player.onGround(),
                    mc.player.horizontalCollision
            ));
        }

        lastMode = mode.get();
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mode.get() == Mode.ABILITIES && !mc.player.isSpectator()) {
            abilitiesOff();
        }
        resetModeState();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        syncModeChange();

        if (isMeteorMode()) {
            tickMeteorMode(mc);
            return;
        }

        LocalPlayer player = mc.player;
        switch (mode.get()) {
            case AIRWALK -> {
                if (player.getDeltaMovement().y < 0.0 && !mc.options.keyJump.isDown() && !mc.options.keyShift.isDown()) {
                    player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
                }
            }
            case VULCAN_277 -> {
                if (player.fallDistance > 0.1f) {
                    Vec3 vel = player.getDeltaMovement();
                    player.setDeltaMovement(vel.x, player.tickCount % 2 == 0 ? -0.155 : -0.1, vel.z);
                }
            }
            case GRIM_2859_V -> {
                if (grimTicks == 0 && player.onGround()) {
                    player.jumpFromGround();
                }
                grimTicks++;
            }
            default -> {
            }
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (event.getType() != net.minecraft.world.entity.MoverType.SELF) return;

        switch (mode.get()) {
            case SPARTAN_524 -> {
                double yaw = Math.toRadians(mc.player.getYRot());
                event.setMovement(new Vec3(
                        -Math.sin(yaw) * 0.28,
                        0.0,
                        Math.cos(yaw) * 0.28
                ));
            }
            case AIRWALK -> {
                Vec3 movement = event.getMovement();
                if (movement != null && movement.y < 0.0) {
                    event.setMovement(new Vec3(movement.x, 0.0, movement.z));
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    private void onCollision(EventCollision event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        BlockPos below = BlockPos.containing(mc.player.position()).below();
        boolean spoofBelow = event.getPos().equals(below) && !mc.options.keyShift.isDown();

        switch (mode.get()) {
            case AIRWALK, VULCAN_286_113, VULCAN_286_18 -> {
                if (spoofBelow) {
                    event.setState(Blocks.BARRIER.defaultBlockState());
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundPlayerPositionPacket)) return;

        switch (mode.get()) {
            case VULCAN_286_113, VULCAN_286_18 -> {
                vulcanFlags++;
                if (vulcanFlags == 1) {
                    vulcanWait = true;
                    event.cancel();
                } else {
                    setEnabled(false);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) return;

        switch (mode.get()) {
            case AIRWALK, VULCAN_286_18 -> ((ServerboundMovePlayerPacketAccessor) packet).combatant$setOnGround(true);
            case VULCAN_286_113 -> {
                if (vulcanWait) {
                    ((ServerboundMovePlayerPacketAccessor) packet).combatant$setOnGround(false);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    private void onSync(EventSync event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player == null) return;

        if (mode.get() == Mode.GRIM_2859_V && grimTicks >= 2) {
            Vec3 original = player.position();
            player.setPos(original.x + 1152.0, original.y, original.z + 1152.0);
            event.addPostAction(() -> player.setPos(original.x, original.y, original.z));
        }
    }

    public ServerboundMovePlayerPacket onSendMovePacket(ServerboundMovePlayerPacket packet) {
        if (packet == null) return null;
        if (!isMeteorMode() || antiKickMode.get() != AntiKickMode.PACKET) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;

        double currentY = packet.getY(Double.MAX_VALUE);
        if (currentY != Double.MAX_VALUE) {
            antiKickPacket(packet, currentY);
            return null;
        }

        ServerboundMovePlayerPacket fullPacket;
        if (packet.hasRotation()) {
            fullPacket = new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    packet.getYRot(0),
                    packet.getXRot(0),
                    packet.isOnGround(),
                    mc.player.horizontalCollision
            );
        } else {
            fullPacket = new ServerboundMovePlayerPacket.Pos(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    packet.isOnGround(),
                    mc.player.horizontalCollision
            );
        }

        antiKickPacket(fullPacket, mc.player.getY());
        return fullPacket;
    }

    public boolean onReceiveAbilities(ClientboundPlayerAbilitiesPacket packet) {
        if (packet == null) return false;
        if (mode.get() != Mode.ABILITIES) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;

        mc.player.getAbilities().invulnerable = packet.isInvulnerable();
        mc.player.getAbilities().instabuild = packet.canInstabuild();
        mc.player.getAbilities().setWalkingSpeed(packet.getWalkingSpeed());
        return true;
    }

    public float getOffGroundSpeed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return -1;
        if (!isEnabled() || mode.get() != Mode.VELOCITY) return -1;
        return speed.get().floatValue() * (mc.player.isSprinting() ? 15f : 10f);
    }

    public boolean noSneak() {
        return isEnabled() && mode.get() == Mode.VELOCITY && noSneak.get();
    }

    private void tickMeteorMode(Minecraft mc) {
        float currentYaw = mc.player.getYRot();
        if (mc.player.fallDistance >= 3f && currentYaw == lastYaw && mc.player.getDeltaMovement().length() < 0.003d) {
            mc.player.setYRot(currentYaw + (flip ? 1 : -1));
            flip = !flip;
        }
        lastYaw = currentYaw;

        if (delayLeft > 0) delayLeft--;

        if (offLeft <= 0 && delayLeft <= 0) {
            delayLeft = delay.get();
            offLeft = offTime.get();

            if (antiKickMode.get() == AntiKickMode.PACKET) {
                if (mc.player instanceof LocalPlayerAccessor accessor) {
                    accessor.combatant$setTicksSinceLastPositionPacketSent(20);
                }
            }
        } else if (delayLeft <= 0) {
            boolean shouldReturn = false;

            if (antiKickMode.get() == AntiKickMode.NORMAL) {
                if (mode.get() == Mode.ABILITIES) {
                    abilitiesOff();
                    shouldReturn = true;
                }
            } else if (antiKickMode.get() == AntiKickMode.PACKET && offLeft == offTime.get()) {
                if (mc.player instanceof LocalPlayerAccessor accessor) {
                    accessor.combatant$setTicksSinceLastPositionPacketSent(20);
                }
            }

            offLeft--;

            if (shouldReturn) return;
        }

        if (mc.player.getYRot() != lastYaw) mc.player.setYRot(lastYaw);

        switch (mode.get()) {
            case VELOCITY -> {
                mc.player.getAbilities().flying = false;
                double horizontalSpeed = speed.get() * (mc.player.isSprinting() ? 15f : 10f);
                double[] dir = MovementUtil.forward(horizontalSpeed);
                Vec3 playerVelocity = new Vec3(dir[0], 0.0, dir[1]);
                if (mc.options.keyJump.isDown()) {
                    playerVelocity = playerVelocity.add(0, speed.get() * (verticalSpeedMatch.get() ? 10f : 5f), 0);
                }
                if (mc.options.keyShift.isDown()) {
                    playerVelocity = playerVelocity.subtract(0, speed.get() * (verticalSpeedMatch.get() ? 10f : 5f), 0);
                }
                mc.player.setDeltaMovement(playerVelocity);
                if (noSneak.get()) {
                    mc.player.setOnGround(false);
                }
            }
            case ABILITIES -> {
                if (mc.player.isSpectator()) return;
                mc.player.getAbilities().setFlyingSpeed(speed.get().floatValue());
                mc.player.getAbilities().flying = true;
                if (!mc.player.getAbilities().instabuild) {
                    mc.player.getAbilities().mayfly = true;
                }
            }
            default -> {
            }
        }
    }

    private void antiKickPacket(ServerboundMovePlayerPacket packet, double currentY) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player == null) return;

        if (delayLeft <= 0 && lastPacketY != Double.MAX_VALUE
                && shouldFlyDown(currentY, lastPacketY) && isOnAir(player)) {
            ((ServerboundMovePlayerPacketAccessor) packet).combatant$setY(lastPacketY - 0.03130D);
        } else {
            lastPacketY = currentY;
        }
    }

    private boolean shouldFlyDown(double currentY, double lastY) {
        if (currentY >= lastY) {
            return true;
        }
        return lastY - currentY < 0.03130D;
    }

    private boolean isOnAir(LocalPlayer player) {
        return !player.onGround()
                && !player.isInWater()
                && !player.isInLava()
                && !player.onClimbable();
    }

    private void abilitiesOff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().setFlyingSpeed(0.05f);
        if (!mc.player.getAbilities().instabuild) {
            mc.player.getAbilities().mayfly = false;
        }
    }

    private void syncModeChange() {
        Mode current = mode.get();
        if (current == lastMode) return;
        lastMode = current;
        if (!isEnabled() || !canUpdate()) return;
        abilitiesOff();
        resetModeState();
    }

    private boolean canUpdate() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.level != null;
    }

    private boolean isMeteorMode() {
        return mode.get() == Mode.ABILITIES || mode.get() == Mode.VELOCITY;
    }

    private boolean supportsAntiKick() {
        return mode.get() == Mode.ABILITIES || mode.get() == Mode.VELOCITY;
    }

    private boolean usesSpeed() {
        return switch (mode.get()) {
            case ABILITIES, VELOCITY -> true;
            default -> false;
        };
    }

    private void resetModeState() {
        delayLeft = delay.get();
        offLeft = offTime.get();
        flip = false;
        lastPacketY = Double.MAX_VALUE;
        vulcanFlags = 0;
        vulcanWait = false;
        grimTicks = 0;
    }

    public enum Mode {
        ABILITIES,
        VELOCITY,
        AIRWALK,
        SPARTAN_524,
        VULCAN_277,
        VULCAN_286_113,
        VULCAN_286_18,
        GRIM_2859_V
    }

    public enum AntiKickMode {
        NORMAL,
        PACKET,
        NONE
    }
}
