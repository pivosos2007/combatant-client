/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.aiming;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.EventSync;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.PlayerVelocityStrafe;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.mixininterface.ILocalPlayer;
import combatant.client.mixins.accessors.EntityInvoker;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;

/**
 * Rotation manager adapted from LiquidBounce.
 */
public final class RotationManager {

    public static final RotationManager INSTANCE = new RotationManager();

    private final RequestHandler<RotationTarget> rotationTargetHandler = new RequestHandler<>();
    private RotationTarget previousRotationTarget;
    private Object previousRotationProvider;

    private Rotation currentRotation;
    private Rotation previousRotation;
    private Object currentRotationProvider;
    private boolean smoothReturnActive;
    private int smoothReturnTicks;

    private Rotation actualServerRotation = Rotation.ZERO;
    private Rotation theoreticalServerRotation = Rotation.ZERO;
    private int lastLifecycleAge = Integer.MIN_VALUE;

    private RotationManager() {
    }

    private static float computeRotationDifference(Rotation a, Rotation b) {
        if (a == null || b == null) {
            return Float.MAX_VALUE;
        }
        float yawDiff = Math.abs(RotationUtil.angleDifference(a.yaw(), b.yaw()));
        float pitchDiff = Math.abs(a.pitch() - b.pitch());
        return (float) Math.hypot(yawDiff, pitchDiff);
    }

    public static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    public static Rotation getPlayerLastRotation() {
        LocalPlayer player = player();
        if (player instanceof ILocalPlayer access) {
            return new Rotation(access.combatant$getLastYaw(), access.combatant$getLastPitch(), true);
        }
        if (player != null) {
            return new Rotation(player.getYRot(), player.getXRot(), true);
        }
        return Rotation.ZERO;
    }

    public static double boxedDistanceToPlayer(Entity entity) {
        LocalPlayer player = player();
        if (player == null || entity == null) return 0.0;
        Vec3 eyes = player.getEyePosition();
        var box = entity.getBoundingBox();
        double dx = 0.0;
        if (eyes.x < box.minX) dx = box.minX - eyes.x;
        else if (eyes.x > box.maxX) dx = eyes.x - box.maxX;
        double dy = 0.0;
        if (eyes.y < box.minY) dy = box.minY - eyes.y;
        else if (eyes.y > box.maxY) dy = eyes.y - box.maxY;
        double dz = 0.0;
        if (eyes.z < box.minZ) dz = box.minZ - eyes.z;
        else if (eyes.z > box.maxZ) dz = eyes.z - box.maxZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public RotationTarget getActiveRotationTarget() {
        RotationTarget active = rotationTargetHandler.getActiveRequestValue();
        return active != null ? active : previousRotationTarget;
    }

    public RotationTarget getPreviousRotationTarget() {
        return previousRotationTarget;
    }

    public Rotation getCurrentRotation() {
        return currentRotation;
    }

    private void setCurrentRotation(Rotation rotation) {
        setCurrentRotation(rotation, currentRotationProvider);
    }

    private void setCurrentRotation(Rotation rotation, Object provider) {
        if (rotation == null) {
            if (currentRotation != null) {
                previousRotation = currentRotation;
            }
            currentRotation = null;
            currentRotationProvider = null;
            return;
        }

        previousRotation = currentRotation;
        currentRotation = rotation;
        currentRotationProvider = provider;
    }

    public Rotation getPreviousRotation() {
        return previousRotation;
    }

    public Rotation getServerRotation() {
        return actualServerRotation;
    }

    public Rotation getMovementRotation() {
        LocalPlayer player = player();
        Rotation fallback = player != null
                ? new Rotation(player.getYRot(), player.getXRot(), true)
                : (currentRotation != null ? currentRotation : Rotation.ZERO);

        RotationTarget active = getActiveRotationTarget();
        if (currentRotation == null || active == null || active.movementCorrection == MovementCorrection.OFF) {
            return fallback;
        }

        return currentRotation;
    }

    public void setRotationTarget(RotationTarget plan, int priority, Object provider) {
        if (plan == null) return;

        rotationTargetHandler.request(new RequestHandler.Request<>(plan.ticksUntilReset, priority, provider, plan));
        smoothReturnActive = false;
        smoothReturnTicks = 0;
    }

    public void setRotationTarget(RotationTarget plan, int priority) {
        setRotationTarget(plan, priority, null);
    }

    private boolean isRotatingAllowed(RotationTarget target) {
        if (!target.considerInventory) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return true;
        return !(ClientScreen.current() instanceof AbstractContainerScreen<?>);
    }

    /**
     * Update current rotation to a new rotation step.
     */
    public void update() {
        LocalPlayer player = player();
        if (player == null) {
            clear();
            return;
        }

        Rotation playerRotation = new Rotation(player.getYRot(), player.getXRot(), true);
        RequestHandler.Request<RotationTarget> activeRequest = rotationTargetHandler.getActiveRequest();
        RotationTarget activeRotationTarget = activeRequest != null ? activeRequest.value() : previousRotationTarget;
        Object activeProvider = activeRequest != null ? activeRequest.provider() : previousRotationProvider;

        if (activeRotationTarget == null) {
            if (currentRotation == null || !smoothReturnActive) {
                return;
            }

            float diffToPlayer = computeRotationDifference(currentRotation, playerRotation);
            if (diffToPlayer <= 0.5f) {
                finishSmoothReturn();
                return;
            }

            float speed = 0.25f + 0.4f * Math.min(1.0f, diffToPlayer / 30.0f);
            float yawDiff = RotationUtil.angleDifference(playerRotation.yaw(), currentRotation.yaw());
            float newYaw = currentRotation.yaw() + yawDiff * speed;
            float newPitch = Mth.lerp(speed, currentRotation.pitch(), playerRotation.pitch());
            setCurrentRotation(new Rotation(newYaw, newPitch, false).normalize(), currentRotationProvider);
            return;
        }

        if (isRotatingAllowed(activeRotationTarget)) {
            Rotation fromRotation = resolveBaseRotation(playerRotation);
            if (activeRequest == null) {
                if (currentRotation == null) {
                    finishSmoothReturn();
                    return;
                }
                if (!smoothReturnActive) {
                    smoothReturnActive = true;
                    smoothReturnTicks = 0;
                }

                Rotation resetRotation = activeRotationTarget.towards(fromRotation, true).normalize();
                setCurrentRotation(resetRotation, activeProvider);
                previousRotationTarget = activeRotationTarget;
                previousRotationProvider = activeProvider;

                smoothReturnTicks++;
                float diffToPlayer = computeRotationDifference(resetRotation, playerRotation);
                int maxTicks = Math.max(1, activeRotationTarget.ticksUntilReset);
                float threshold = Math.max(0.05f, activeRotationTarget.resetThreshold);
                if (diffToPlayer <= threshold || smoothReturnTicks >= maxTicks) {
                    finishSmoothReturn();
                    rotationTargetHandler.tick();
                    return;
                }
            } else {
                Rotation rotation = activeRotationTarget.towards(fromRotation, false).normalize();
                setCurrentRotation(rotation, activeProvider);
                previousRotationTarget = activeRotationTarget;
                previousRotationProvider = activeProvider;
                smoothReturnActive = false;
                smoothReturnTicks = 0;

                if (activeRotationTarget.whenReached != null) {
                    activeRotationTarget.whenReached.invoke();
                }
            }
        }

        rotationTargetHandler.tick();
    }

    private void finishSmoothReturn() {
        setCurrentRotation(null);
        previousRotation = null;
        previousRotationTarget = null;
        previousRotationProvider = null;
        smoothReturnActive = false;
        smoothReturnTicks = 0;
    }

    private Rotation resolveBaseRotation(Rotation playerRotation) {
        if (currentRotation != null) {
            return currentRotation;
        }
        if (actualServerRotation != null && actualServerRotation != Rotation.ZERO) {
            return actualServerRotation;
        }
        return playerRotation;
    }

    public void clear() {
        rotationTargetHandler.clear();
        previousRotationTarget = null;
        previousRotationProvider = null;
        currentRotation = null;
        currentRotationProvider = null;
        previousRotation = null;
        smoothReturnActive = false;
        smoothReturnTicks = 0;
    }

    public void clear(Object provider) {
        if (provider == null) return;
        boolean removed = rotationTargetHandler.clear(provider);
        boolean ownsVisualState = provider == currentRotationProvider || provider == previousRotationProvider;
        if (!removed && !ownsVisualState) {
            return;
        }

        if (currentRotation != null && ownsVisualState) {
            smoothReturnActive = true;
            smoothReturnTicks = 0;
            return;
        }

        if (ownsVisualState) {
            previousRotationTarget = null;
            previousRotationProvider = null;
            previousRotation = null;
        }
        smoothReturnActive = false;
        smoothReturnTicks = 0;
    }

    /**
     * Stops accepting new rotations from provider, but keeps current/previous state
     * so update() can return camera smoothly instead of snapping instantly.
     */
    public void release(Object provider) {
        release(provider, true);
    }

    public void release(Object provider, boolean smoothReturn) {
        if (provider == null) return;
        boolean removed = rotationTargetHandler.clear(provider);
        boolean ownsVisualState = provider == currentRotationProvider || provider == previousRotationProvider;
        if (!removed && !ownsVisualState) {
            return;
        }
        if (!smoothReturn) {
            if (ownsVisualState) {
                setCurrentRotation(null);
                previousRotationTarget = null;
                previousRotationProvider = null;
                previousRotation = null;
            }
            smoothReturnActive = false;
            smoothReturnTicks = 0;
            return;
        }
        smoothReturnActive = ownsVisualState && currentRotation != null;
        smoothReturnTicks = 0;
    }

    @EventHandler
    public void onSync(EventSync e) {
        RotationTarget activeRotationTarget = getActiveRotationTarget();
        if (activeRotationTarget == null || currentRotation == null) return;

        e.setRotation(currentRotation.yaw(), currentRotation.pitch(), true);
    }

    @EventHandler(priority = 100)
    public void onVelocityStrafe(PlayerVelocityStrafe event) {
        RotationTarget active = getActiveRotationTarget();
        if (active == null) return;
        if (active.movementCorrection == MovementCorrection.OFF) {
            return;
        }

        Rotation rotation = getMovementRotation();
        if (rotation == null) return;

        Vec3 velocity = EntityInvoker.combatant$movementInputToVelocity(
                event.getMovementInput(),
                event.getSpeed(),
                rotation.yaw()
        );
        event.setVelocity(velocity);
    }

    @EventHandler(priority = -100)
    public void onPacketSend(PacketEvent.Send e) {
        if (e.isCancelled()) return;
        var packet = e.getPacket();

        Rotation rot = null;
        if (packet instanceof ServerboundMovePlayerPacket move) {
            if (!move.hasRotation()) return;
            rot = new Rotation(move.getYRot(0.0f), move.getXRot(0.0f), true);
        } else if (packet instanceof ServerboundUseItemPacket use) {
            rot = new Rotation(use.getYRot(), use.getXRot(), true);
        }

        if (rot != null) {
            actualServerRotation = rot;
            theoreticalServerRotation = rot;
        }
    }

    @EventHandler(priority = -100)
    public void onPacketReceive(PacketEvent.Receive e) {
        if (!(e.getPacket() instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet))
            return;
        Rotation rot = new Rotation(packet.change().yRot(), packet.change().xRot(), true);
        theoreticalServerRotation = rot;
        actualServerRotation = rot;
    }

    public void runTickLifecycle() {
        LocalPlayer player = player();
        if (player == null) {
            lastLifecycleAge = Integer.MIN_VALUE;
            return;
        }

        if (player.tickCount == lastLifecycleAge) {
            return;
        }
        lastLifecycleAge = player.tickCount;

        Events.BUS.post(new RotationUpdateEvent(RotationUpdateEvent.Type.PRE));
        update();
        Events.BUS.post(new RotationUpdateEvent(RotationUpdateEvent.Type.POST));
    }
}
