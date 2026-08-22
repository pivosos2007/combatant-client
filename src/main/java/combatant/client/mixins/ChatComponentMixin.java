/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ChatComponent.DisplayMode;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.gui.chat.BetterChatStoreManager;
import combatant.client.features.gui.hud.draggable.impl.BetterChat;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.misc.MessageFilter;
import combatant.client.runtime.RuntimeGate;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$filterChatFull(Component message,
                                          MessageSignature signatureData,
                                          GuiMessageSource source,
                                          GuiMessageTag indicator,
                                          CallbackInfo ci) {
        if (!RuntimeGate.canRunHud()) return;
        MessageFilter messageFilter = Modules.get(MessageFilter.class);
        if (messageFilter != null && messageFilter.shouldHideChat(message)) {
            ci.cancel();
            return;
        }
        if (!CommandOutput.isCombatantMessage(message)) {
            boolean accepted = BetterChatStoreManager.addMessage(message);
            if (!accepted && BetterChat.isActive()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "logChatMessage(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V", at = @At("HEAD"), cancellable = true)
    private void combatant$commands$suppressClientLog(GuiMessage message, CallbackInfo ci) {
        if (message != null && CommandOutput.isCombatantMessage(message.content())) {
            ci.cancel();
        }
    }

    @Inject(method = "clearMessages(Z)V", at = @At("TAIL"))
    private void combatant$betterChat$clear(boolean clearHistory, CallbackInfo ci) {
        if (!RuntimeGate.canRunHud()) return;
        if (!BetterChat.isActive()) return;
        // Never wipe BetterChat store on vanilla clear; just persist to disk.
        BetterChatStoreManager.flushActive();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void combatant$betterChat$hideVanilla(GuiGraphicsExtractor context,
                                                  Font textRenderer,
                                                  int currentTick,
                                                  int mouseX,
                                                  int mouseY,
                                                  DisplayMode displayMode,
                                                  boolean inChat,
                                                  CallbackInfo ci) {
        if (!RuntimeGate.canRunHud()) return;
        if (!BetterChat.isActive()) return;
        BetterChat cfg = BetterChat.get();
        if (cfg != null && cfg.hideVanilla()) {
            ci.cancel();
        }
    }
}

