/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on InvMove
 * (https://github.com/PieKing1215/InvMove).
 * Copyright (c) PieKing1215 and contributors.
 *
 * Original portions remain under LGPLv3.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.features.module.modules.movement;

import com.mojang.blaze3d.platform.InputConstants;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.player.Input;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.SetValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.gui.clickgui.ClickGuiEditorScreen;
import combatant.client.features.gui.clickgui.ClickGuiPickerScreen;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiScreen;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.relations.RelationsComponent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.InputAccessor;
import combatant.client.util.screen.ScreenCatalog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Inventory movement and per-screen policy system for Combatant.
 * <p>
 * Credit:
 * Behavior design and feature scope were adapted from the InvMove mod by pieking1215.
 */
//todo Description
@ModuleInfo(
        id = "inventorymove",
        displayName = "InventoryMove",
        category = ModuleCategory.MOVEMENT
)
public class InventoryMove extends Module {

    private static final long STOP_TIMEOUT_MS = 100L;
    private static final long RESUME_DELAY_MS = 40L;
    private static final double STOP_VELOCITY = 0.03;
    private static final String TOGGLE_JUMP = "Jump";
    private static final String TOGGLE_SNEAK = "Sneak";
    private static final String TOGGLE_SPRINT = "Sprint";
    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode = enumSetting("mode", "mode", Mode.NORMAL, Mode.values());
    private final BooleanValue cancelServerClose =
            visibleWhen(bool("cancel_server_close", true), () -> mode.get() == Mode.GRIM);
    private final BooleanValue textFieldDisablesMovement = bool("text_fields_disable", true);
    private final BooleanMapValue movementToggles = group("movement_toggles", defaultMovementToggles());
    private final SetValue allowedScreens =
            textList("allowed_screens", "allowed_screens", TextListSetting.PickerMode.SCREENS, defaultAllowedScreens());
    private final SetValue blockedScreens =
            textList("blocked_screens", "blocked_screens", TextListSetting.PickerMode.SCREENS, defaultBlockedScreens());
    private final List<ServerboundContainerClickPacket> heldPackets = new ArrayList<>();
    private GrimPhase grimPhase = GrimPhase.IDLE;
    private long phaseStartedAt = 0L;
    private int pendingCloseSyncId = -1;
    private boolean flushingPackets = false;
    private boolean allowClosePacket = false;
    private boolean movementBlocked = false;

    private static Set<String> defaultAllowedScreens() {
        LinkedHashSet<String> screens = new LinkedHashSet<>();
        screens.add(AbstractContainerScreen.class.getName());
        screens.add(PauseScreen.class.getName());
        screens.add(ScreenCatalog.CLICK_GUI);
        screens.add(ScreenCatalog.CLICK_GUI_EDITOR);
        screens.add(ScreenCatalog.CLICK_GUI_PICKER);
        screens.add("pivosos2007.gui.clickgui.ClickGuiScreen");
        screens.add("pivosos2007.gui.clickgui.ClickGuiPickerScreen");
        screens.add("pivosos2007.gui.clickgui.ClickGuiEditorScreen");
        screens.add("pivosos2007.features.gui.clickgui.ClickGuiScreen");
        screens.add("pivosos2007.features.gui.clickgui.ClickGuiEditorScreen");
        screens.add("pivosos2007.features.gui.clickgui.ClickGuiPickerScreen");
        screens.add(CreativeModeInventoryScreen.class.getName());
        screens.add(InventoryScreen.class.getName());
        screens.add(ContainerScreen.class.getName());
        return screens;
    }

    private static java.util.Map<String, Boolean> defaultMovementToggles() {
        java.util.LinkedHashMap<String, Boolean> toggles = new java.util.LinkedHashMap<>();
        toggles.put(TOGGLE_JUMP, true);
        toggles.put(TOGGLE_SNEAK, true);
        toggles.put(TOGGLE_SPRINT, true);
        return toggles;
    }

    private static Set<String> defaultBlockedScreens() {
        return new LinkedHashSet<>(Set.of(
                AbstractSignEditScreen.class.getName(),
                AbstractCommandBlockEditScreen.class.getName(),
                StructureBlockEditScreen.class.getName(),
                AnvilScreen.class.getName(),
                BookEditScreen.class.getName(),
                BookSignScreen.class.getName()
        ));
    }

    @Override
    public void onEnable() {
        resetGrimState();
        syncMovementKeysFromPhysical();
    }

    @Override
    public void onDisable() {
        resetGrimState();
        syncMovementKeysFromPhysical();
    }

    @Override
    public void onTick() {
        if (ClientScreen.current() != null) {
            ScreenCatalog.registerSeen(ClientScreen.current());
        }

        if (mc.player == null) return;

        if (mode.get() == Mode.NORMAL) {
            tickNormal();
            return;
        }

        tickGrim();
    }

    @EventHandler
    public void onMovementInput(MovementInputEvent event) {
        if (mc.player == null || ClientScreen.current() == null) {
            return;
        }

        if (mode.get() == Mode.NORMAL) {
            applyMovementToEvent(event, isMovementAllowed(ClientScreen.current()));
            return;
        }

        if (movementBlocked || isBlockingGrimPhase()) {
            clearMovementEvent(event);
            return;
        }

        applyMovementToEvent(event, isMovementAllowed(ClientScreen.current()));
    }

    private boolean isBlockingGrimPhase() {
        return grimPhase == GrimPhase.STOPPING
                || grimPhase == GrimPhase.FLUSHING
                || grimPhase == GrimPhase.CLOSING;
    }

    private void applyMovementToEvent(MovementInputEvent event, boolean canMove) {
        if (!canMove || mc.getWindow() == null) {
            clearMovementEvent(event);
            return;
        }

        event.setForward(isPhysicallyPressed(mc.options.keyUp));
        event.setBackward(isPhysicallyPressed(mc.options.keyDown));
        event.setLeft(isPhysicallyPressed(mc.options.keyLeft));
        event.setRight(isPhysicallyPressed(mc.options.keyRight));
        event.setJump(movementToggles.get(TOGGLE_JUMP) && isPhysicallyPressed(mc.options.keyJump));
        event.setSneak(movementToggles.get(TOGGLE_SNEAK) && isPhysicallyPressed(mc.options.keyShift));
        event.setSprint(movementToggles.get(TOGGLE_SPRINT) && isPhysicallyPressed(mc.options.keySprint));
    }

    private void clearMovementEvent(MovementInputEvent event) {
        event.setForward(false);
        event.setBackward(false);
        event.setLeft(false);
        event.setRight(false);
        event.setJump(false);
        event.setSprint(false);
        event.setSneak(false);

        if (mc.player != null && mc.player.input != null) {
            ((InputAccessor) mc.player.input).setPlayerInput(new Input(false, false, false, false, false, false, false));
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (mode.get() != Mode.GRIM || mc.player == null) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundContainerClickPacket click) {
            if (flushingPackets) return;
            if (shouldBufferClickPacket(click)) {
                heldPackets.add(click);
                event.cancel();
            }
            return;
        }

        if (packet instanceof ServerboundContainerClosePacket close) {
            if (allowClosePacket) return;
            if (requestClose(close.getContainerId())) {
                event.cancel();
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (mode.get() != Mode.GRIM || !cancelServerClose.get()) return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundContainerClosePacket close && close.getContainerId() == 0) {
            event.cancel();
        }
    }

    public boolean shouldCancelHandledScreenClose(AbstractContainerScreen<?> screen) {
        return mode.get() == Mode.GRIM && requestClose(syncId(screen));
    }

    public boolean shouldCancelCloseHandledScreen() {
        if (mode.get() != Mode.GRIM || mc.player == null) return false;
        if (!(ClientScreen.current() instanceof AbstractContainerScreen<?>)) return false;
        return requestClose(mc.player.containerMenu.containerId);
    }

    private void tickNormal() {
        applyMovementForCurrentScreen(isMovementAllowed(ClientScreen.current()));
    }

    private void tickGrim() {
        boolean grimManaged = shouldManageCurrentHandledScreen();

        if (grimManaged) {
            if (grimPhase == GrimPhase.IDLE) {
                grimPhase = GrimPhase.ACTIVE;
            }
            if (grimPhase == GrimPhase.ACTIVE) {
                movementBlocked = false;
                syncMovementKeysFromPhysical();
            }
        } else if (grimPhase == GrimPhase.ACTIVE) {
            resetGrimState();
        }

        if (!grimManaged && grimPhase == GrimPhase.IDLE) {
            movementBlocked = false;
            applyMovementForCurrentScreen(isMovementAllowed(ClientScreen.current()));
            return;
        }

        long elapsed = System.currentTimeMillis() - phaseStartedAt;
        switch (grimPhase) {
            case STOPPING -> {
                movementBlocked = true;
                releaseMovementKeys();
                if (isPlayerStopped() || elapsed >= STOP_TIMEOUT_MS) {
                    grimPhase = heldPackets.isEmpty() ? GrimPhase.CLOSING : GrimPhase.FLUSHING;
                    phaseStartedAt = System.currentTimeMillis();
                }
            }
            case FLUSHING -> {
                movementBlocked = true;
                releaseMovementKeys();
                flushHeldPackets();
                grimPhase = GrimPhase.CLOSING;
                phaseStartedAt = System.currentTimeMillis();
            }
            case CLOSING -> {
                movementBlocked = true;
                releaseMovementKeys();
                closeInventoryNow();
                grimPhase = GrimPhase.RESUMING;
                phaseStartedAt = System.currentTimeMillis();
            }
            case RESUMING -> {
                movementBlocked = false;
                syncMovementKeysFromPhysical();
                if (elapsed >= RESUME_DELAY_MS) {
                    resetGrimState();
                    if (shouldManageCurrentHandledScreen()) {
                        grimPhase = GrimPhase.ACTIVE;
                    } else {
                        applyMovementForCurrentScreen(isMovementAllowed(ClientScreen.current()));
                    }
                }
            }
            default -> {
            }
        }
    }

    private boolean requestClose(int syncId) {
        if (!shouldManageCurrentHandledScreen()) return false;
        if (syncId < 0) return false;
        if (heldPackets.isEmpty() && !hasGrimInventoryMovementRisk()) return false;
        if (grimPhase != GrimPhase.ACTIVE && grimPhase != GrimPhase.IDLE) return true;

        pendingCloseSyncId = syncId;
        movementBlocked = hasGrimInventoryMovementRisk();
        grimPhase = movementBlocked ? GrimPhase.STOPPING : (heldPackets.isEmpty() ? GrimPhase.CLOSING : GrimPhase.FLUSHING);
        phaseStartedAt = System.currentTimeMillis();
        return true;
    }

    private void flushHeldPackets() {
        if (heldPackets.isEmpty() || mc.getConnection() == null) return;
        flushingPackets = true;
        try {
            for (ServerboundContainerClickPacket packet : heldPackets) {
                mc.getConnection().send(packet);
            }
        } finally {
            heldPackets.clear();
            flushingPackets = false;
        }
    }

    private void closeInventoryNow() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (pendingCloseSyncId >= 0) {
            allowClosePacket = true;
            try {
                mc.getConnection().send(new ServerboundContainerClosePacket(pendingCloseSyncId));
            } finally {
                allowClosePacket = false;
            }
        }
        mc.player.clientSideCloseContainer();
        pendingCloseSyncId = -1;
    }

    private boolean shouldBufferClickPacket(ServerboundContainerClickPacket packet) {
        return shouldManageCurrentHandledScreen()
                && packet.containerId() == syncId(ClientScreen.current())
                && (!heldPackets.isEmpty() || hasGrimInventoryMovementRisk() || grimPhase == GrimPhase.STOPPING);
    }

    private boolean shouldManageCurrentHandledScreen() {
        return syncId(ClientScreen.current()) >= 0 && isMovementAllowed(ClientScreen.current());
    }

    private boolean isMovementAllowed(Screen screen) {
        if (screen == null || mc.player == null) return false;
        if (isClickGuiTextInputActive(screen)) return false;
        if (textFieldDisablesMovement.get() && hasActiveTextField(screen)) return false;
        if (ScreenCatalog.matches(screen, blockedScreens.get())) return false;
        if (isSafeVanillaNonInventoryScreen(screen)) return true;
        return ScreenCatalog.matches(screen, allowedScreens.get());
    }

    private boolean isSafeVanillaNonInventoryScreen(Screen screen) {
        if (screen == null || screen instanceof AbstractContainerScreen<?>) return false;

        String name = screen.getClass().getName();
        return name.startsWith("net.minecraft.client.gui.screen.");
    }

    private boolean isClickGuiTextInputActive(Screen screen) {
        if (!(screen instanceof ClickGuiScreen
                || screen instanceof ClickGuiEditorScreen
                || screen instanceof ClickGuiPickerScreen)) {
            return false;
        }
        return ClickGuiRenderer.isBlockingModuleKeybinds() || RelationsComponent.isMovementInputBlocked();
    }

    private boolean hasActiveTextField(Screen screen) {
        if (screen == null) return false;
        return screen.getFocused() instanceof EditBox field && field.canConsumeInput();
    }

    private int syncId(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> handled)) return -1;
        return handled.getMenu().containerId;
    }

    private boolean isPlayerStopped() {
        if (mc.player == null) return true;
        return Math.abs(mc.player.getDeltaMovement().x) < STOP_VELOCITY
                && Math.abs(mc.player.getDeltaMovement().z) < STOP_VELOCITY;
    }

    private void resetGrimState() {
        heldPackets.clear();
        grimPhase = GrimPhase.IDLE;
        phaseStartedAt = 0L;
        pendingCloseSyncId = -1;
        flushingPackets = false;
        allowClosePacket = false;
        movementBlocked = false;
    }

    private void applyMovementForCurrentScreen(boolean canMove) {
        if (ClientScreen.current() == null) return;
        if (canMove) {
            syncMovementKeysFromPhysical();
        } else {
            releaseMovementKeys();
        }
    }

    private void syncMovementKeysFromPhysical() {
        if (mc.player == null || mc.getWindow() == null) return;
        syncKey(mc.options.keyUp, true);
        syncKey(mc.options.keyDown, true);
        syncKey(mc.options.keyLeft, true);
        syncKey(mc.options.keyRight, true);
        syncKey(mc.options.keyJump, movementToggles.get(TOGGLE_JUMP));
        syncKey(mc.options.keyShift, movementToggles.get(TOGGLE_SNEAK));
        syncKey(mc.options.keySprint, movementToggles.get(TOGGLE_SPRINT));
    }

    private void releaseMovementKeys() {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
        mc.options.keySprint.setDown(false);
    }

    private void syncKey(KeyMapping key, boolean allowed) {
        key.setDown(allowed && isPhysicallyPressed(key));
    }

    private boolean isPhysicallyPressed(KeyMapping key) {
        InputConstants.Key boundKey = InputConstants.getKey(key.saveString());
        return InputConstants.isKeyDown(mc.getWindow(), boundKey.getValue());
    }

    private boolean hasPhysicalMovementInput() {
        return mc.getWindow() != null
                && (isPhysicallyPressed(mc.options.keyUp)
                || isPhysicallyPressed(mc.options.keyDown)
                || isPhysicallyPressed(mc.options.keyLeft)
                || isPhysicallyPressed(mc.options.keyRight)
                || (movementToggles.get(TOGGLE_JUMP) && isPhysicallyPressed(mc.options.keyJump))
                || (movementToggles.get(TOGGLE_SNEAK) && isPhysicallyPressed(mc.options.keyShift)));
    }

    private boolean hasGrimInventoryMovementRisk() {
        if (mc.player == null) return false;
        if (mc.player.isSprinting() && (!mc.player.isSwimming() || mc.player.onGround())) return true;
        if (hasPhysicalGrimMovingInput()) return true;
        if (mc.player.input == null || mc.player.input.keyPresses == null) return false;

        Input input = mc.player.input.keyPresses;
        return input.forward() || input.backward() || input.left() || input.right() || input.jump();
    }

    private boolean hasPhysicalGrimMovingInput() {
        return mc.getWindow() != null
                && (isPhysicallyPressed(mc.options.keyUp)
                || isPhysicallyPressed(mc.options.keyDown)
                || isPhysicallyPressed(mc.options.keyLeft)
                || isPhysicallyPressed(mc.options.keyRight)
                || (movementToggles.get(TOGGLE_JUMP) && isPhysicallyPressed(mc.options.keyJump)));
    }

    public enum Mode {
        NORMAL,
        GRIM
    }

    private enum GrimPhase {
        IDLE,
        ACTIVE,
        STOPPING,
        FLUSHING,
        CLOSING,
        RESUMING
    }
}
