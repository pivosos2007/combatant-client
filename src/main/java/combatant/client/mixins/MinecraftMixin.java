/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.systems.GpuSurface;
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
import net.minecraft.world.item.ItemStack;
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
import combatant.client.events.impl.CrosshairTargetUpdateEvent;
import combatant.client.features.gui.chat.BetterChatStoreManager;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.*;
import combatant.client.features.module.modules.movement.Timer;
import combatant.client.features.module.modules.player.NoDelay;
import combatant.client.features.module.modules.player.NoInteract;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.features.relations.StaffTracker;
import combatant.client.render.helpers.TickDelta;
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

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void fastThrow(CallbackInfo ci) {
        NoDelay noDelay = Modules.get(NoDelay.class);
        if (noDelay != null && noDelay.handleFastUse()) {
            ci.cancel();
            return;
        }

        AutoBow autoBow = Modules.get(AutoBow.class);
        if (autoBow != null && autoBow.shouldCancelVanillaDoItemUse()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "handleKeybinds",
            at = @At("HEAD")
    )
    private void spearassist$blockAttackInput(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        SpearAssist assist = Modules.get(SpearAssist.class);
        if (assist == null || !assist.isEnabled() || !assist.respectCooldownEnabled()) {
            return;
        }

        ItemStack stack = mc.player.getMainHandItem();
        if (!SpearAssist.isSpear(stack)) return;

        float cooldown = mc.player.getAttackStrengthScale(0.0F);
        if (cooldown < 0.99F) {
            // ВАЖНО: гасим сам ввод
            mc.options.keyAttack.setDown(false);
        }
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void combatant$autobow$finishInputCycle(CallbackInfo ci) {
        AutoBow autoBow = Modules.get(AutoBow.class);
        if (autoBow != null && autoBow.isEnabled()) {
            autoBow.onInputCycleHandled();
        }
    }

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void combatant$disablePvpGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player)) return;
        PvpCooldowns mod = Modules.get(PvpCooldowns.class);
        if (mod == null || !mod.shouldHideTargetGlow()) return;
        cir.setReturnValue(false);
    }


    // === КУРСОР ДЛЯ CLICKGUI ===

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void onHandleBlockBreaking(boolean breaking, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        // Hitbox должен работать даже при ломании блоков: не блокируем vanilla логику
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

            // блокируем ломание
            ci.cancel();

            if (client.gameMode != null)
                client.gameMode.stopDestroyBlock();

            // имитация удара
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        AttributeSwap attributeSwap = Modules.get(AttributeSwap.class);
        if (attributeSwap != null && attributeSwap.isEnabled() && attributeSwap.handleManualAttack()) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        AutoAttack autoAttack = Modules.get(AutoAttack.class);
        if (autoAttack != null && autoAttack.isEnabled()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void combatant$betterChat$saveOnDisconnect(net.minecraft.client.gui.screens.Screen screen, boolean transferring, boolean bl, CallbackInfo ci) {
        combatant$guardSingleplayerSaveWithAltUsername();
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

        // =========================================================
        // FREECAM
        // =========================================================
        if (fc != null && fc.isEnabled()) {

            // позиция и направление FREECAM
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

            // ===============================
            // FREECAM + NO INTERACT
            // ===============================
            if (!fc.allowInteract()) {
                mc.hitResult = combatant$miss(start);
                mc.crosshairPickEntity = null;
                ci.cancel();
                return;
            }

            // ===============================
            // FREECAM + INTERACT
            // ===============================

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
                mc.crosshairPickEntity
        );
        Events.BUS.post(event);
        mc.hitResult = event.getHitResult();
        mc.crosshairPickEntity = event.getTargetedEntity();
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void noInteract$afterRaycast(float tickDelta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (!Modules.enabled(NoInteract.class))
            return;

        var hit = mc.hitResult;

        if (hit instanceof EntityHitResult) return;

        Vec3 camPos = mc.player.getEyePosition(tickDelta);

        mc.hitResult = new BlockHitResult(
                camPos,
                Direction.UP,
                BlockPos.ZERO,
                false
        );

        mc.crosshairPickEntity = null;
    }

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void freecam$onDisconnect(CallbackInfo ci) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled()) {
            fc.toggle();
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void freecam$onJoinWorld(ClientLevel world, CallbackInfo ci) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled()) {
            fc.toggle();
        }
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void combatant$captureSingleplayerJoinSession(ClientLevel world, CallbackInfo ci) {
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

        int refreshRate = mc.getWindow().getRefreshRate();
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
}
