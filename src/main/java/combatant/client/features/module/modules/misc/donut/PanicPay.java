/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.donut;

import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PvpChatEvent;
import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.server.DonutAuctionParser;
import combatant.client.util.server.DonutSmp;
import combatant.client.util.text.ServerChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Moves your DonutSMP balance to a stash account: asks {@code /bal}, reads the reply and sends
 * {@code /pay <recipient> <amount>}. Fires from a keybind or when health drops to the configured threshold (once per
 * drop; it re-arms after health recovers). The reply is rounded down when the server abbreviates it, and the pay is
 * repeated a few times to sweep the remainder.
 */
@ModuleInfo(
        id = "panicpay",
        displayName = "PanicPay",
        aliases = {"StashPay", "EmergencyPay"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.panicpay.description")
public final class PanicPay extends Module {

    private static final Pattern NAME = Pattern.compile("\\.?[A-Za-z0-9_]{2,16}");
    private static final int REPLY_TIMEOUT_TICKS = 80;
    private static final int PAY_SETTLE_TICKS = 30;
    private static final int MAX_ROUNDS = 3;

    private enum Step { IDLE, ASK, PAY }

    private final Minecraft mc = Minecraft.getInstance();

    private final StringValue recipient = text("panicPayRecipient", "recipient", "");
    private final StringValue keepAmount = text("panicPayKeep", "keep_amount", "");
    private final NumberValue<Float> healthThreshold = num("panicPayHealth", "health_threshold", 0.0f, 0.0f, 20.0f);
    private final BooleanValue clickConfirm = bool("panicPayConfirm", "click_confirm", true);
    private final FunctionBindSetting payKey = action("pay_key", "NONE", BindMode.PRESS);

    private volatile String balanceLine;
    private Step step = Step.IDLE;
    private int wait;
    private int rounds;
    private boolean exact;
    private boolean confirmed;
    private boolean armed = true;
    private BigDecimal paid = BigDecimal.ZERO;
    private BigDecimal keep = BigDecimal.ZERO;
    private String target = "";

    @Override
    public void onEnable() {
        step = Step.IDLE;
        armed = true;
        balanceLine = null;
    }

    @Override
    public void onDisable() {
        step = Step.IDLE;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || player.connection == null) {
            step = Step.IDLE;
            return;
        }

        if (step == Step.IDLE) {
            boolean keyed = payKey.isPressed();
            float threshold = healthThreshold.get();
            boolean low = threshold > 0.0f && player.getHealth() <= threshold && !player.isDeadOrDying();
            if (!low) armed = true;
            if (keyed || low && armed) {
                armed = false;
                start();
            }
            return;
        }

        wait++;
        if (step == Step.ASK) {
            tickAsk();
        } else {
            tickPay();
        }
    }

    @EventHandler
    private void onChat(PvpChatEvent event) {
        if (step != Step.ASK || balanceLine != null || event.message == null) return;
        if (DonutAuctionParser.parseBalance(event.message) != null) balanceLine = event.message;
    }

    private void start() {
        String name = recipient.get().strip();
        if (!NAME.matcher(name).matches()) {
            Notifier.error(I18n.get("notification.panicpay.bad_recipient"));
            return;
        }
        if (name.equalsIgnoreCase(mc.player.getName().getString())) {
            Notifier.error(I18n.get("notification.panicpay.self"));
            return;
        }
        if (!keepAmount.get().isBlank()) {
            double kept = DonutAuctionParser.parseMoney(keepAmount.get());
            if (kept < 0.0) {
                Notifier.error(I18n.get("notification.panicpay.bad_keep"));
                return;
            }
            keep = BigDecimal.valueOf(kept);
        } else {
            keep = BigDecimal.ZERO;
        }
        target = name;
        rounds = 0;
        paid = BigDecimal.ZERO;
        ask();
    }

    private void ask() {
        balanceLine = null;
        step = Step.ASK;
        wait = 0;
        ServerChat.command("bal");
    }

    private void tickAsk() {
        String line = balanceLine;
        if (line == null) {
            if (wait > REPLY_TIMEOUT_TICKS) finish(rounds == 0 ? I18n.get("notification.panicpay.no_reply") : null);
            return;
        }
        DonutAuctionParser.Amount balance = DonutAuctionParser.parseBalance(line);
        BigDecimal amount = balance == null ? BigDecimal.ZERO : balance.value().subtract(keep);
        if (balance == null || amount.compareTo(BigDecimal.ONE) < 0) {
            finish(rounds == 0 ? I18n.get("notification.panicpay.nothing") : null);
            return;
        }
        ServerChat.command("pay " + target + " " + amount.stripTrailingZeros().toPlainString());
        paid = paid.add(amount);
        rounds++;
        exact = balance.exact();
        confirmed = false;
        step = Step.PAY;
        wait = 0;
    }

    private void tickPay() {
        if (clickConfirm.get() && !confirmed) confirmed = confirmMenu();
        if (wait < PAY_SETTLE_TICKS) return;
        if (!exact && rounds < MAX_ROUNDS) {
            ask();
        } else {
            finish(null);
        }
    }

    /** Presses Confirm if {@code /pay} opened a confirmation menu. */
    private boolean confirmMenu() {
        if (!DonutSmp.isMenuOpen(mc)) return false;
        int slot = DonutSmp.findMenuSlotByName(mc.player.containerMenu, "confirm");
        if (slot < 0) return false;
        DonutSmp.click(mc, slot);
        return true;
    }

    private void finish(String problem) {
        if (problem != null) {
            Notifier.warning(problem);
        } else {
            Notifier.success(I18n.get("notification.panicpay.sent", paid.stripTrailingZeros().toPlainString(), target));
        }
        step = Step.IDLE;
    }
}
