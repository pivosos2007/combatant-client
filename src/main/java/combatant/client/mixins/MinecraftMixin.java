/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.renderpearl.api.device.GpuSurface;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.KeyMapping;
import combatant.client.util.player.inventory.InventorySlotLocks;
import combatant.client.features.module.modules.combat.*;
import net.minecraft.client.FramerateLimiter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.events.Events;
import combatant.client.events.impl.ClientInputCycleHandledEvent;
import combatant.client.events.impl.ManualAttackEvent;
import combatant.client.events.impl.EntityGlowDecisionEvent;
import combatant.client.events.impl.CrosshairTargetUpdateEvent;
import combatant.client.events.impl.BlockPlacementInputEvent;
import combatant.client.events.impl.WorldChangeEvent;
import combatant.client.features.gui.chat.BetterChatStoreManager;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.Timer;
import combatant.client.features.module.modules.movement.EntityControl;
import combatant.client.features.module.modules.player.LiquidInteract;
import combatant.client.mixins.accessors.KeyMappingAccessor;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.features.module.modules.visuals.ViewModel;
import combatant.client.features.relations.StaffTracker;
import combatant.client.render.helpers.TickDelta;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.util.player.PlayerSkinResolver;
import combatant.client.util.combat.AntiBotTracker;
import combatant.client.util.session.SessionChanger;
import combatant.client.util.session.MinecraftGameConfigHolder;

@Mixin(Minecraft.class)
public class MinecraftMixin implements MinecraftGameConfigHolder {

    @Shadow
    @Final
    private GpuSurface windowSurface;

    @Unique
    private GameConfig combatant$gameConfig;

    @Unique
    private int combatant$useClickCount;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void combatant$captureGameConfig(GameConfig config, CallbackInfo ci) {
        this.combatant$gameConfig = config;
    }

    @Override
    public GameConfig combatant$getGameConfig() {
        return combatant$gameConfig;
    }

    @Unique
    private static BlockHitResult combatant$miss(Vec3 pos) {
        return new BlockHitResult(
                pos,
                Direction.UP,
                BlockPos.ZERO,
                false
        );
    }

    @Unique
    private static void combatant$guardSingleplayerSaveWithAltUsername() {
        SessionChanger.restoreSingleplayerJoinSessionForDisconnect();
    }

    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
    private boolean combatant$entityControlAllowBoatItems(boolean handsBusy) {
        if (!handsBusy) return false;
        EntityControl control = Modules.get(EntityControl.class);
        return control == null || !control.allowBoatItemUse(Minecraft.getInstance().player);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void combatant$placementInput(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        int clicks = combatant$useClickCount;
        combatant$useClickCount = 0;
        if (Events.BUS.hasListeners(BlockPlacementInputEvent.class)) {
            BlockPlacementInputEvent event = Events.BUS.post(new BlockPlacementInputEvent(
                    clicks, mc.options.keyUse.isDown(), mc.hitResult,
                    BlockInteractionPipeline.INSTANCE.inputOwner()));
            if (event.isCancelled()) {
                ci.cancel();
                return;
            }
        }
        AutoBow autoBow = Modules.get(AutoBow.class);
        if (autoBow != null && autoBow.shouldCancelVanillaDoItemUse()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult combatant$routeBlockInput(MultiPlayerGameMode gameMode, LocalPlayer player,
            InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
        BlockInteractionPipeline pipeline = BlockInteractionPipeline.INSTANCE;
        Object owner = pipeline.inputOwner();
        return owner == null ? original.call(gameMode, player, hand, hit)
                : pipeline.useOnBlock(owner, player, hand, -1, 0, hit, false);
    }

    @WrapOperation(method = "handleKeybinds",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
    private boolean combatant$protectOffhandKey(KeyMapping key, Operation<Boolean> original) {
        Minecraft mc = Minecraft.getInstance();
        if (key == mc.options.keyUse) {
            int count = ((KeyMappingAccessor) key).combatant$getClickCount();
            boolean consumed = original.call(key);
            combatant$useClickCount = consumed ? count : 0;
            return consumed;
        }
        // Vanilla consumes attack clicks without calling startAttack() while an item is
        // being used. Route those exact clicks to SpearAssist before they are discarded.
        if (key == mc.options.keyAttack && mc.player != null && mc.player.isUsingItem()) {
            boolean consumed = original.call(key);
            if (consumed) {
                SpearAssist spearAssist = Modules.get(SpearAssist.class);
                if (spearAssist != null) spearAssist.handleAttackClickDuringItemUse();
            }
            return consumed;
        }
        if (key == mc.options.keySwapOffhand
                && InventorySlotLocks.isLocked(InventorySlotLocks.OFFHAND)) {
            original.call(key); // drain the pressed key, but do not send the vanilla swap packet
            return false;
        }
        // Drop and offhand swap both move the selected hotbar stack out of its slot.
        if ((key == mc.options.keyDrop || key == mc.options.keySwapOffhand) && mc.player != null
                && InventorySlotLocks.blocks(mc.player.getInventory().getSelectedSlot(), null)) {
            original.call(key);
            return false;
        }
        return original.call(key);
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void combatant$autobow$finishInputCycle(CallbackInfo ci) {
        if (Events.BUS.hasListeners(ClientInputCycleHandledEvent.class)) {
            Events.BUS.post(new ClientInputCycleHandledEvent());
        }
    }

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void combatant$disablePvpGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player)) return;
        if (Events.BUS.hasListeners(EntityGlowDecisionEvent.class)
                && Events.BUS.post(new EntityGlowDecisionEvent()).isCancelled()) {
            cir.setReturnValue(false);
        }
    }



    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void onHandleBlockBreaking(boolean breaking, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        if (breaking && client.options.keyAttack.isDown()) {
            LiquidInteract liquidInteract = Modules.get(LiquidInteract.class);
            if (liquidInteract != null) liquidInteract.restoreVanillaAttackTarget();
        }

        // Preserve vanilla block breaking while the hitbox feature is active.
        Hitbox hitbox = Modules.get(Hitbox.class);
        if (hitbox != null && hitbox.isEnabled()) {
            return;
        }

        boolean attackKeyPressed = client.options.keyAttack.isDown();

        AutoAttack autoAttack = Modules.get(AutoAttack.class);
        boolean blockByAutoAttack = autoAttack != null
                && autoAttack.isEnabled()
                && !autoAttack.isAutoMode()
                && autoAttack.shouldBlockBreaking();
        boolean shouldBlockBreaking = blockByAutoAttack;

        if (shouldBlockBreaking && attackKeyPressed) {

            ci.cancel();

            if (client.gameMode != null)
                client.gameMode.stopDestroyBlock();

        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        LiquidInteract liquidInteract = Modules.get(LiquidInteract.class);
        if (liquidInteract != null) liquidInteract.restoreVanillaAttackTarget();
        if (!Events.BUS.hasListeners(ManualAttackEvent.class)) return;
        ManualAttackEvent decision = Events.BUS.post(new ManualAttackEvent());
        if (decision.isResolved()) cir.setReturnValue(decision.isReturnValue());
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void combatant$betterChat$saveOnDisconnect(net.minecraft.client.gui.screens.Screen screen, boolean transferring, boolean bl, CallbackInfo ci) {
        combatant$guardSingleplayerSaveWithAltUsername();
        if (!transferring) {
            PlayerHeadRenderer.clearSessionCache();
            PlayerSkinResolver.clearAll();
        }
        try {
            BetterChatStoreManager.flushAll();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("TAIL"))
    private void combatant$sessionRestoreAfterDisconnect(net.minecraft.client.gui.screens.Screen screen, boolean transferring, boolean bl, CallbackInfo ci) {
        SessionChanger.restoreDeferredSessionAfterDisconnect();
    }

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void onUpdateCrosshairTarget(float tickDelta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        var fc = Modules.get(Freecam.class);

        // FREECAM
        if (fc != null && fc.isEnabled()) {

            Vec3 start = new Vec3(
                    Mth.lerp(tickDelta, fc.camPosPrev.x, fc.camPos.x),
                    Mth.lerp(tickDelta, fc.camPosPrev.y, fc.camPos.y),
                    Mth.lerp(tickDelta, fc.camPosPrev.z, fc.camPos.z)
            );

            float yawRad = -fc.camYaw * ((float) Math.PI / 180F);
            float pitchRad = -fc.camPitch * ((float) Math.PI / 180F);

            Vec3 dir = new Vec3(
                    Mth.sin(yawRad) * Mth.cos(pitchRad),
                    Mth.sin(pitchRad),
                    Mth.cos(yawRad) * Mth.cos(pitchRad)
            );

            // FREECAM + NO INTERACT
            if (!fc.allowInteract()) {
                mc.hitResult = combatant$miss(start);
                mc.crosshairPickEntity = null;
                ci.cancel();
                return;
            }

            // FREECAM + INTERACT

            // Reach (FREECAM)
            Reach reachModule = Modules.get(Reach.class);
            if (reachModule != null && reachModule.isEnabled()) {
                EntityHitResult hit = reachModule.raycastEntities(
                        mc.player, start, dir
                );

                if (hit != null && hit.getEntity() instanceof LivingEntity target && target.isAlive()) {
                    mc.hitResult = hit;
                    mc.crosshairPickEntity = target;
                    ci.cancel();
                    return;
                }
            }

            // Blocks (FREECAM)
            double reach = mc.player.blockInteractionRange();
            Vec3 end = start.add(dir.scale(reach));

            BlockHitResult bhr = mc.level.clip(new net.minecraft.world.level.ClipContext(
                    start, end,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    mc.player
            ));

            mc.hitResult = bhr;
            mc.crosshairPickEntity = null;
            ci.cancel();
            return;
        }

        Reach reachModule = Modules.get(Reach.class);
        if (reachModule != null && reachModule.isEnabled()) {
            EntityHitResult entityHit = reachModule.raycastEntities(mc.player);
            if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target && target.isAlive()) {
                mc.hitResult = entityHit;
                mc.crosshairPickEntity = target;
                ci.cancel();
            }
        }
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void combatant$dispatchCrosshairTargetEvent(float tickDelta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        CrosshairTargetUpdateEvent event = new CrosshairTargetUpdateEvent(
                tickDelta,
                mc.hitResult,
                mc.crosshairPickEntity,
                ((KeyMappingAccessor) mc.options.keyUse).combatant$getClickCount(),
                mc.options.keyUse.isDown()
        );
        Events.BUS.post(event);
        mc.hitResult = event.getHitResult();
        mc.crosshairPickEntity = event.getTargetedEntity();
    }

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void freecam$onDisconnect(CallbackInfo ci) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled()) {
            fc.toggle();
        }
        Events.BUS.post(new WorldChangeEvent(WorldChangeEvent.Reason.DISCONNECT, null));
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void freecam$onJoinWorld(ClientLevel world, CallbackInfo ci) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled()) {
            fc.toggle();
        }
        Events.BUS.post(new WorldChangeEvent(WorldChangeEvent.Reason.LEVEL_CHANGE, world));
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void combatant$captureSingleplayerJoinSession(ClientLevel world, CallbackInfo ci) {
        combatant.client.render.sodium.fluid.FluidSurfaceMetadataCapture.beginWorld(world);
        if (world == null) {
            SessionChanger.clearSingleplayerJoinSession();
        } else {
            SessionChanger.captureSingleplayerJoinSession();
        }
    }

    @Inject(method = "clearDownloadedResourcePacks", at = @At("HEAD"))
    private void combatant$onDisconnected(CallbackInfo ci) {
        StaffTracker.resetAll();
        AntiBotTracker.INSTANCE.reset();
    }

    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceRealTime(J)V",
                    shift = At.Shift.AFTER
            )
    )
    private void combatant$frameModulesAfterRenderTick(boolean tick, CallbackInfo ci) {
        ModuleManager.frameAll(TickDelta.frameDeltaTicks());
    }

    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE_STRING",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
                    args = "ldc=frameLimiter",
                    shift = At.Shift.AFTER
            )
    )
    private void combatant$paceImmediateNoVsync(boolean tick, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc == null || mc.options == null || mc.getWindow() == null || windowSurface == null) {
            return;
        }
        if (Boolean.TRUE.equals(mc.options.enableVsync().get())) {
            return;
        }
        if (windowSurface.currentConfiguration().isEmpty()
                || windowSurface.currentConfiguration().get().presentMode() != GpuSurface.PresentMode.IMMEDIATE) {
            return;
        }

        int configuredLimit = mc.gameRenderer.gameRenderState().framerateLimit;
        if (configuredLimit < 260) {
            return;
        }

        int refreshRate = Math.round(mc.getWindow().getActiveVideoMode().getRefreshRate());
        int targetFps = combatant$immediatePacingTarget(refreshRate);
        FramerateLimiter.limitDisplayFPS(targetFps);
    }

    @Unique
    private static int combatant$immediatePacingTarget(int refreshRate) {
        int base = refreshRate > 0 ? refreshRate : 120;
        return Mth.clamp(base * 2, 120, 360);
    }

    @Inject(method = "getTickTargetMillis", at = @At("RETURN"), cancellable = true)
    private void combatant$applyTimer(float millis, CallbackInfoReturnable<Float> cir) {
        float mult = Timer.getTickTimer();
        if (mult <= 0.0001f) return;
        if (Math.abs(mult - 1.0f) < 0.0001f) return;
        float base = cir.getReturnValue();
        cir.setReturnValue(base / mult);
    }

    @Inject(method = "isLevelRunningNormally", at = @At("HEAD"), cancellable = true)
    private void combatant$skipPlayerDependentTicksWithoutPlayer(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player == null) {
            cir.setReturnValue(false);
        }
    }
    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
    private void combatant$viewModel$resourceReload(CallbackInfoReturnable<java.util.concurrent.CompletableFuture<Void>> cir) {
        java.util.concurrent.CompletableFuture<Void> future = cir.getReturnValue();
        if (future == null) return;

        future.thenRun(() -> Minecraft.getInstance().execute(() -> {
            ViewModel viewModel = Modules.get(ViewModel.class);
            if (viewModel != null) viewModel.onHmiResourceReload();
        }));
    }

}
