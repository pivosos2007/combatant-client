/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp;

import net.minecraft.client.Minecraft;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PvpChatEvent;
import combatant.client.events.impl.PvpOverlayEvent;
import combatant.client.events.impl.PvpTabEvent;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.PvpCooldowns;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.text.ChatNameUtil;

public final class PvpTracker {
    public static final PvpTracker INSTANCE = new PvpTracker();

    private PvpTracker() {
    }

    private static void maybeMarkDynamicEnemy(PvpChatParser.Result result, long timeMs) {
        if (result == null) return;
        if (result.source() != PvpTagSource.RECEIVED && result.source() != PvpTagSource.GIVEN) return;

        PvpCooldowns mod = Modules.get(PvpCooldowns.class);
        if (mod == null || !mod.isEnabled() || !mod.isDynamicEnemyEnabled()) {
            if (DebugLog.serverOnly()) {
                DebugLog.server("PVP dynamic enemy: skipped (module disabled)");
            }
            return;
        }

        String opponent = result.opponentName();
        if (opponent == null || opponent.isBlank()) return;
        opponent = ChatNameUtil.normalizeNickCandidate(opponent);
        if (!ChatNameUtil.isNickLike(opponent)) {
            if (DebugLog.serverOnly()) {
                DebugLog.server("PVP dynamic enemy: skipped (not nicklike) name=%s", opponent);
            }
            return;
        }
        if (isSelf(opponent)) {
            if (DebugLog.serverOnly()) {
                DebugLog.server("PVP dynamic enemy: skipped (self) name=%s", opponent);
            }
            return;
        }

        PlayerRelations rel = PlayerRelations.get();
        if (rel.isFriendOrStaff(opponent) || rel.isEnemyPersisted(opponent)) {
            if (DebugLog.serverOnly()) {
                DebugLog.server("PVP dynamic enemy: skipped (friend/staff/enemy) name=%s", opponent);
            }
            return;
        }

        rel.markDynamicEnemy(opponent, timeMs);
        if (DebugLog.serverOnly()) {
            DebugLog.server("PVP dynamic enemy: marked name=%s", opponent);
        }
    }

    private static boolean isSelf(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        String self = mc.player.getName().getString();
        return self != null && self.equalsIgnoreCase(name);
    }

    @EventHandler
    public void onOverlay(PvpOverlayEvent event) {
        if (event == null || event.message == null) return;
        PvpCooldowns mod = Modules.get(PvpCooldowns.class);
        if (mod == null || !mod.shouldReadOverlayMessages()) return;

        String raw = event.message.getString();
        PvpOverlayParser.Result result = PvpOverlayParser.parse(
                raw,
                mod.getOverlayActivePatterns(),
                mod.getOverlayExitPatterns()
        );
        if (result == null) return;
        PvpState.applyOverlay(result, event.timeMs);
    }

    @EventHandler
    public void onTab(PvpTabEvent event) {
        if (event == null) return;
        PvpCooldowns mod = Modules.get(PvpCooldowns.class);
        if (mod == null || !mod.shouldReadTabText()) return;

        PvpOverlayParser.Result result = PvpTabParser.parse(
                event.combinedText(),
                mod.getTabActivePatterns(),
                mod.getTabExitPatterns()
        );
        if (result == null) return;
        PvpState.applyOverlay(result, event.timeMs);
    }

    @EventHandler
    public void onChat(PvpChatEvent event) {
        if (event == null || event.message == null) return;
        PvpCooldowns mod = Modules.get(PvpCooldowns.class);
        if (mod == null) return;

        boolean allowedSource = switch (event.source) {
            case CHAT_MESSAGE -> mod.shouldReadChatMessages();
            case GAME_MESSAGE -> mod.shouldReadGameMessages();
        };
        if (!allowedSource) return;

        if (DebugLog.serverOnly()) {
            DebugLog.server("PVP text event: source=%s raw=\"%s\"", event.source, event.message);
        }
        PvpChatParser.Result result = PvpChatParser.parse(
                event.message,
                mod.getChatGivePatterns(),
                mod.getChatReceivePatterns(),
                mod.getChatActivePatterns(),
                mod.getChatExitPatterns()
        );
        if (result == null) {
            if (DebugLog.serverOnly()) {
                DebugLog.server("PVP text parse: no match");
            }
            return;
        }
        if (DebugLog.serverOnly()) {
            DebugLog.server("PVP text parse: source=%s opponent=%s active=%s exit=%s",
                    result.source(), result.opponentName(), result.active(), result.exit());
        }
        maybeMarkDynamicEnemy(result, event.timeMs);
        PvpState.applyChat(result, event.timeMs);
    }
}
