/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.server;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads prices and sellers out of the item lore of DonutSMP's {@code /ah} and {@code /order} chest menus, and
 * recognises those menus by title. Everything here is pure text work except {@link #lore(ItemStack)} and
 * {@link #itemId(ItemStack)}, so the parsing rules stay testable.
 */
public final class DonutAuctionParser {

    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern MONEY = Pattern.compile(
            "\\$?[ \\t]*([0-9][0-9,]*(?:\\.[0-9]+)?)[ \\t]*([kmbt])?(?![a-z0-9])");
    private static final Pattern DOLLAR_MONEY = Pattern.compile(
            "\\$[ \\t]*([0-9][0-9,]*(?:\\.[0-9]+)?)[ \\t]*([kmbt])?(?![a-z0-9])");
    private static final Pattern SELLER = Pattern.compile("^(?:seller|sold by|owner|player|from)\\s*:?\\s*(\\S+)");
    private static final Pattern TIME = Pattern.compile("^(?:time left|expires(?: in)?|ends in)\\s*:?\\s*(.+)$");
    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀѕꜱᴛᴜᴠᴡʏᴢ";
    private static final String SMALL_CAPS_PLAIN = "abcdefghijklmnoprsstuvwyz";

    private DonutAuctionParser() {
    }

    /**
     * One parsed menu entry. Prices are -1 when the lore did not state them; {@code unitPrice} is derived from the
     * total and the stack size when the lore only gives a total.
     */
    public record Listing(String seller, double totalPrice, double unitPrice, int count, String timeLeft) {

        public boolean priced() {
            return unitPrice > 0.0;
        }
    }

    /** A money amount; {@code exact} is false when it was abbreviated ("1.5m") and is a rounded-down lower bound. */
    public record Amount(BigDecimal value, boolean exact) {
    }

    public enum Menu {
        AUCTION,
        AUCTION_CONFIRM,
        ORDERS,
        ORDER_DELIVER,
        SELL,
        OTHER
    }

    /** Lower-case text without colour codes, with small-caps letters and arrows folded to ASCII. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String text = FORMATTING.matcher(raw).replaceAll("");
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int small = SMALL_CAPS.indexOf(c);
            if (small >= 0) {
                out.append(SMALL_CAPS_PLAIN.charAt(small));
            } else if (c == '➜' || c == '→') {
                out.append('-');
            } else {
                out.append(c);
            }
        }
        return out.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Classifies a container screen by its title. */
    public static Menu menuOf(String title) {
        String t = normalize(title);
        if (t.isEmpty()) return Menu.OTHER;
        boolean confirm = t.contains("confirm");
        if (t.contains("auction") || t.equals("ah") || t.startsWith("ah ")) {
            return confirm ? Menu.AUCTION_CONFIRM : Menu.AUCTION;
        }
        if (t.contains("deliver")) return Menu.ORDER_DELIVER;
        if (t.contains("order")) return confirm ? Menu.ORDER_DELIVER : Menu.ORDERS;
        if (t.contains("sell")) return Menu.SELL;
        return confirm ? Menu.AUCTION_CONFIRM : Menu.OTHER;
    }

    public static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return out;
        for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
            out.add(line.getString());
        }
        return out;
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Parses a menu item's lore, or returns null when the item has no recognisable price line. */
    public static Listing parse(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return parseLines(lore(stack), stack.getCount());
    }

    /**
     * Parses lore lines. A price line mentioning "each" / "per item" / "/ea" is a unit price; any other price line
     * is a total, split over {@code count}. Balance/wallet lines are never read as prices.
     */
    public static Listing parseLines(List<String> lines, int count) {
        int stackSize = Math.max(1, count);
        String seller = "";
        String timeLeft = "";
        double total = -1.0;
        double unit = -1.0;

        for (String raw : lines) {
            String line = bullet(normalize(raw));
            if (line.isEmpty()) continue;

            Matcher sellerMatch = SELLER.matcher(line);
            if (sellerMatch.find()) {
                seller = sellerMatch.group(1);
                continue;
            }
            Matcher timeMatch = TIME.matcher(line);
            if (timeMatch.find()) {
                timeLeft = timeMatch.group(1);
                continue;
            }
            if (isNotPrice(line)) continue;

            boolean perItem = line.contains("each") || line.contains("per item") || line.contains("/ea")
                    || line.contains("per unit");
            boolean priceLine = line.contains("price") || line.contains("cost") || line.contains("pay")
                    || line.contains("$");
            if (!priceLine) continue;

            double value = firstMoney(line);
            if (value <= 0.0) continue;
            if (perItem) {
                if (unit < 0.0) unit = value;
            } else if (total < 0.0) {
                total = value;
            }
        }

        if (unit < 0.0 && total < 0.0) return null;
        if (total < 0.0) total = unit * stackSize;
        if (unit < 0.0) unit = total / stackSize;
        return new Listing(seller, total, unit, stackSize, timeLeft);
    }

    private static boolean isNotPrice(String line) {
        return line.contains("balance") || line.contains("you have") || line.contains("your money")
                || line.contains("wallet") || line.contains("bank");
    }

    private static String bullet(String text) {
        int start = 0;
        while (start < text.length() && !Character.isLetterOrDigit(text.charAt(start)) && text.charAt(start) != '$') {
            start++;
        }
        return text.substring(start);
    }

    private static double firstMoney(String line) {
        Matcher m = (line.contains("$") ? DOLLAR_MONEY : MONEY).matcher(line);
        while (m.find()) {
            double value = scale(m.group(1), m.group(2));
            if (value > 0.0) return value;
        }
        return -1.0;
    }

    /** Parses a user-typed amount such as {@code 50m}, {@code 1.5k}, {@code $1,200}. Returns -1 when invalid. */
    public static double parseMoney(String raw) {
        if (raw == null) return -1.0;
        String text = raw.trim().toLowerCase(Locale.ROOT);
        Matcher m = MONEY.matcher(text);
        if (!m.matches()) return -1.0;
        return scale(m.group(1), m.group(2));
    }

    /**
     * Reads the balance out of a chat line like {@code Balance: $1.5M}. Returns null if the line is not a balance
     * report. Abbreviated values become a lower bound (rounded down by half the shown precision) so paying the
     * result can never overdraw.
     */
    public static Amount parseBalance(String message) {
        String line = normalize(message);
        if (!line.contains("balance") && !line.contains("money") && !line.contains("$")) return null;
        Matcher m = (line.contains("$") ? DOLLAR_MONEY : MONEY).matcher(line.replace("balance", ""));
        if (!m.find()) return null;
        BigDecimal value;
        try {
            value = new BigDecimal(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        String suffix = m.group(2);
        if (suffix == null) return new Amount(value.setScale(2, RoundingMode.DOWN), true);
        BigDecimal half = BigDecimal.ONE.movePointLeft(Math.max(0, value.scale())).divide(BigDecimal.valueOf(2));
        BigDecimal low = value.subtract(half).max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(multiplier(suffix)));
        return new Amount(low.setScale(0, RoundingMode.DOWN), false);
    }

    private static double scale(String number, String suffix) {
        double value;
        try {
            value = Double.parseDouble(number.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1.0;
        }
        return suffix == null ? value : value * multiplier(suffix);
    }

    private static long multiplier(String suffix) {
        return switch (suffix) {
            case "k" -> 1_000L;
            case "m" -> 1_000_000L;
            case "b" -> 1_000_000_000L;
            default -> 1_000_000_000_000L;
        };
    }
}
