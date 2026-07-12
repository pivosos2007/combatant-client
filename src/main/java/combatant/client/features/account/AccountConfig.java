/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.account;

import net.minecraft.resources.Identifier;
import combatant.client.config.ConfigNameProvider;
import combatant.client.config.ConfigSerializer;
import combatant.client.config.JsonConfigObject;
import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.StringValue;
import combatant.client.util.session.MicrosoftSessionResult;
import combatant.client.util.session.SessionChanger;
import combatant.client.util.session.microsoft.MicrosoftAuthService;
import combatant.client.util.logging.DebugLog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class AccountConfig implements JsonConfigObject, ConfigNameProvider {

    public static final AccountConfig INSTANCE = new AccountConfig();

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final AccountsValue accounts = new AccountsValue("accounts");
    private final StringValue activeAccountName = new StringValue("active_name", "");
    private final StringValue activeAccountDate = new StringValue("active_date", "");
    private final StringValue activeAccountSkin = new StringValue("active_skin", "");
    private String microsoftAuthorizationRequiredName = "";

    private AccountConfig() {
        ConfigSerializer.load(this);
        applyActiveSession();
    }

    public static AccountConfig get() {
        return INSTANCE;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private static String nowString() {
        return LocalDateTime.now().format(DATE_FORMAT);
    }

    private static Identifier parseIdentifier(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Identifier.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static UUID parseUuid(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    public List<AccountEntry> getAccounts() {
        return accounts.get();
    }

    public List<AccountEntry> getSortedAccounts() {
        List<AccountEntry> sorted = new ArrayList<>(accounts.get());
        sorted.sort(Comparator
                .comparing(AccountEntry::isPinned).reversed()
                .thenComparingInt(AccountEntry::getOriginalIndex));
        return sorted;
    }

    public AccountEntry getAccountBySortedIndex(int sortedIndex) {
        List<AccountEntry> sorted = getSortedAccounts();
        if (sortedIndex < 0 || sortedIndex >= sorted.size()) {
            return null;
        }
        return sorted.get(sortedIndex);
    }

    public AccountEntry findByName(String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) {
            return null;
        }
        for (AccountEntry entry : accounts.get()) {
            if (normalizeName(entry.getName()).equals(normalized)) {
                return entry;
            }
        }
        return null;
    }

    public AccountEntry addAccount(String name) {
        return addAccount(name, nowString(), null);
    }

    public AccountEntry addAccount(String name, String date, Identifier skin) {
        String cleanName = normalizeName(name);
        if (cleanName.isEmpty()) {
            return null;
        }

        AccountEntry existing = findByName(cleanName);
        if (existing != null) {
            existing.setAuthType(AccountAuthType.OFFLINE);
            existing.setOnlineUuid(null);
            existing.setMicrosoftRefreshToken("");
            clearAuthorizationRequired(existing.getName());
            if (!isBlank(date)) {
                existing.setDate(date);
            }
            if (skin != null) {
                existing.setSkin(skin);
            }
            save();
            return existing;
        }

        AccountEntry entry = new AccountEntry(cleanName, isBlank(date) ? nowString() : date, skin, false, accounts.get().size());
        accounts.get().add(entry);
        save();
        return entry;
    }

    public AccountEntry addMicrosoftAccount(MicrosoftSessionResult session) {
        if (session == null || isBlank(session.name()) || session.uuid() == null || isBlank(session.refreshToken())) {
            return null;
        }

        AccountEntry existing = findByName(session.name());
        if (existing != null) {
            existing.applyMicrosoftSession(session);
            existing.setDate(nowString());
            clearAuthorizationRequired(existing.getName());
            save();
            return existing;
        }

        AccountEntry entry = new AccountEntry(
                session.name(),
                nowString(),
                null,
                false,
                accounts.get().size(),
                AccountAuthType.MICROSOFT,
                session.uuid(),
                session.refreshToken()
        );
        accounts.get().add(entry);
        clearAuthorizationRequired(entry.getName());
        save();
        return entry;
    }

    public void removeAccount(AccountEntry entry) {
        if (entry == null) {
            return;
        }
        if (!accounts.get().remove(entry)) {
            return;
        }

        if (isActive(entry)) {
            clearActiveAccount();
        } else {
            reindexAccounts();
            save();
        }
    }

    public void removeAccountBySortedIndex(int sortedIndex) {
        removeAccount(getAccountBySortedIndex(sortedIndex));
    }

    public void clearAllAccounts() {
        accounts.get().clear();
        activeAccountName.set("");
        activeAccountDate.set("");
        activeAccountSkin.set("");
        microsoftAuthorizationRequiredName = "";
        save();
    }

    public void togglePin(int sortedIndex) {
        AccountEntry entry = getAccountBySortedIndex(sortedIndex);
        if (entry == null) {
            return;
        }
        entry.togglePinned();
        save();
    }

    public void setActiveAccount(AccountEntry entry) {
        if (entry == null) {
            clearActiveAccount();
            return;
        }

        activeAccountName.set(normalizeName(entry.getName()));
        activeAccountDate.set(entry.getDate() == null ? "" : entry.getDate());
        Identifier skin = entry.getSkin();
        activeAccountSkin.set(skin == null ? "" : skin.toString());
        applySession(entry);
        save();
    }

    public void setActiveAccount(String name, String date, Identifier skin) {
        String cleanName = normalizeName(name);
        if (cleanName.isEmpty()) {
            clearActiveAccount();
            return;
        }

        AccountEntry entry = findByName(cleanName);
        if (entry == null) {
            entry = addAccount(cleanName, date, skin);
        } else {
            if (!isBlank(date)) {
                entry.setDate(date);
            }
            if (skin != null) {
                entry.setSkin(skin);
            }
        }
        setActiveAccount(entry);
    }

    public void clearActiveAccount() {
        activeAccountName.set("");
        activeAccountDate.set("");
        activeAccountSkin.set("");
        reindexAccounts();
        save();
    }

    public String getActiveAccountName() {
        return activeAccountName.get();
    }

    public String getActiveAccountDate() {
        return activeAccountDate.get();
    }

    public Identifier getActiveAccountSkin() {
        return parseIdentifier(activeAccountSkin.get());
    }

    public boolean isMicrosoftAuthorizationRequired() {
        return getMicrosoftAuthorizationRequiredAccount() != null;
    }

    public AccountEntry getMicrosoftAuthorizationRequiredAccount() {
        if (isBlank(microsoftAuthorizationRequiredName)) {
            return null;
        }
        AccountEntry entry = findByName(microsoftAuthorizationRequiredName);
        return entry != null && entry.isMicrosoft() ? entry : null;
    }

    public void clearMicrosoftAuthorizationRequired() {
        microsoftAuthorizationRequiredName = "";
    }

    public void save() {
        reindexAccounts();
        ConfigSerializer.requestSave(this);
    }

    @Override
    public String getConfigName() {
        return "accounts";
    }

    @Override
    public List<ConfigValue<?>> getConfigValues() {
        List<ConfigValue<?>> values = new ArrayList<>();
        values.add(accounts);
        values.add(activeAccountName);
        values.add(activeAccountDate);
        values.add(activeAccountSkin);
        return values;
    }

    private void applyActiveSession() {
        if (isBlank(activeAccountName.get())) {
            return;
        }
        AccountEntry entry = findByName(activeAccountName.get());
        if (entry != null) {
            applySession(entry);
        } else {
            SessionChanger.changeUsername(activeAccountName.get());
        }
    }

    private void applySession(AccountEntry entry) {
        if (entry == null) {
            return;
        }

        if (!entry.isMicrosoft()) {
            clearAuthorizationRequired(entry.getName());
            SessionChanger.changeUsername(entry.getName())
                    .exceptionally(t -> {
                        DebugLog.error("[AccountConfig] Failed to apply offline account session: " + entry.getName(), t);
                        return null;
                    });
            return;
        }

        String refreshToken = entry.getMicrosoftRefreshToken();
        if (isBlank(refreshToken)) {
            if (SessionChanger.canUseOriginalLauncherSession(entry.getName(), entry.getOnlineUuid())) {
                clearAuthorizationRequired(entry.getName());
                SessionChanger.restoreOriginalLauncherSession(false)
                        .exceptionally(t -> {
                            DebugLog.error("[AccountConfig] Failed to restore launcher Microsoft session: " + entry.getName(), t);
                            markAuthorizationRequired(entry);
                            return null;
                        });
                return;
            }
            markAuthorizationRequired(entry);
            DebugLog.warnOnce("alt-microsoft-token-empty-" + entry.getName(), "[AccountConfig] Microsoft account requires authorization: %s", entry.getName());
            return;
        }

        clearAuthorizationRequired(entry.getName());
        MicrosoftAuthService.loginWithRefreshToken(refreshToken)
                .thenCompose(session -> {
                    entry.applyMicrosoftSession(session);
                    activeAccountName.set(session.name());
                    activeAccountDate.set(entry.getDate() == null ? "" : entry.getDate());
                    save();
                    return SessionChanger.changeSession(session.toLoginData());
                })
                .exceptionally(t -> {
                    DebugLog.error("[AccountConfig] Failed to apply Microsoft account session: " + entry.getName(), t);
                    markAuthorizationRequired(entry);
                    return null;
                });
    }

    private void markAuthorizationRequired(AccountEntry entry) {
        microsoftAuthorizationRequiredName = entry == null ? "" : normalizeName(entry.getName());
    }

    private void clearAuthorizationRequired(String name) {
        if (normalizeName(name).equalsIgnoreCase(normalizeName(microsoftAuthorizationRequiredName))) {
            microsoftAuthorizationRequiredName = "";
        }
    }

    private boolean isActive(AccountEntry entry) {
        if (entry == null) {
            return false;
        }
        return normalizeName(entry.getName()).equals(normalizeName(activeAccountName.get()));
    }

    private void reindexAccounts() {
        List<AccountEntry> list = accounts.get();
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setOriginalIndex(i);
        }
    }

    private static final class AccountsValue extends ConfigValue<List<AccountEntry>> {

        private AccountsValue(String name) {
            super(name, new ArrayList<>());
        }

        private static String stringValue(Object value) {
            if (value == null) {
                return "";
            }
            return String.valueOf(value).trim();
        }

        private static boolean boolValue(Object value) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String string) {
                return Boolean.parseBoolean(string);
            }
            return false;
        }

        private static int intValue(Object value, int fallback) {
            if (value instanceof Number number) {
                return Math.max(0, number.intValue());
            }
            if (value instanceof String string) {
                try {
                    return Math.max(0, Integer.parseInt(string.trim()));
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        @Override
        public Object toJson() {
            List<Object> out = new ArrayList<>();
            for (AccountEntry entry : value) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", entry.getName());
                row.put("date", entry.getDate());
                row.put("skin", entry.getSkin() == null ? "" : entry.getSkin().toString());
                row.put("pinned", entry.isPinned());
                row.put("originalIndex", entry.getOriginalIndex());
                row.put("authType", entry.getAuthType().id());
                row.put("onlineUuid", entry.getOnlineUuid() == null ? "" : entry.getOnlineUuid().toString());
                row.put("microsoftRefreshToken", "");
                out.add(row);
            }
            return out;
        }

        @Override
        public void fromJson(Object json) {
            List<AccountEntry> loaded = new ArrayList<>();
            if (json instanceof Iterable<?> iterable) {
                int fallbackIndex = 0;
                for (Object item : iterable) {
                    if (!(item instanceof Map<?, ?> row)) {
                        continue;
                    }

                    String name = stringValue(row.get("name"));
                    if (name.isBlank()) {
                        continue;
                    }

                    String date = stringValue(row.get("date"));
                    Identifier skin = parseIdentifier(stringValue(row.get("skin")));
                    boolean pinned = boolValue(row.get("pinned"));
                    int originalIndex = intValue(row.get("originalIndex"), fallbackIndex);
                    AccountAuthType authType = AccountAuthType.byId(stringValue(row.get("authType")));
                    UUID onlineUuid = parseUuid(stringValue(row.get("onlineUuid")));
                    loaded.add(new AccountEntry(name, date, skin, pinned, originalIndex, authType, onlineUuid, ""));
                    fallbackIndex++;
                }
            }

            loaded.sort(Comparator.comparingInt(AccountEntry::getOriginalIndex));
            value = loaded;
        }
    }
}
