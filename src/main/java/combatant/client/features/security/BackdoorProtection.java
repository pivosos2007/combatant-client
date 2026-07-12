/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on ExploitPreventer.
 * Original work copyright (c) 2025 Niklas S.
 *
 * Original portions remain under the MIT License.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

/*
 * Portions of this file adapt protection logic from ExploitPreventer.
 * Original work copyright (c) 2025 Niklas S.
 * Licensed under MIT; see THIRD_PARTY_NOTICES.md.
 */
package combatant.client.features.security;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import combatant.client.config.MainConfig;

import java.net.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public enum BackdoorProtection {
    ;

    private static final Map<String, Set<String>> TRANSLATION_KEY_PACKS = new ConcurrentHashMap<>();
    private static final Set<String> VANILLA_KEYBINDS = ConcurrentHashMap.newKeySet();

    private static volatile String currentServerAddress;
    private static volatile boolean currentServerLocal;

    public static void resetLoadedTranslations() {
        TRANSLATION_KEY_PACKS.clear();
    }

    public static void registerTranslationKey(String key, String packId) {
        if (key == null || key.isBlank() || packId == null || packId.isBlank()) return;
        TRANSLATION_KEY_PACKS.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(packId);
    }

    public static void resetVanillaKeybinds() {
        VANILLA_KEYBINDS.clear();
    }

    public static void registerVanillaKeybind(String key) {
        if (key == null || key.isBlank()) return;
        VANILLA_KEYBINDS.add(key);
    }

    public static void updateServerConnection(Connection connection) {
        currentServerAddress = null;
        currentServerLocal = true;
        if (connection == null || connection.isMemoryConnection()) return;
        SocketAddress address = connection.getRemoteAddress();
        if (!(address instanceof InetSocketAddress inetSocketAddress)) return;
        InetAddress inetAddress = inetSocketAddress.getAddress();
        String host = inetAddress != null ? inetAddress.getHostAddress() : inetSocketAddress.getHostString();
        currentServerAddress = host;
        currentServerLocal = isLocalAddressQuiet(host);
    }

    public static Component filterText(Component input) {
        if (input == null || !MainConfig.get().isBackdoorTranslationFilterEnabled()) return input;

        ComponentContents content = filterContent(input.getContents());
        MutableComponent filtered = MutableComponent.create(content);
        filtered.setStyle(input.getStyle());
        for (Component sibling : input.getSiblings()) {
            filtered.append(filterText(sibling));
        }
        return filtered;
    }

    private static ComponentContents filterContent(ComponentContents content) {
        if (content instanceof FilteredTranslatableTextContent) {
            return content;
        }
        if (content instanceof TranslatableContents translatable) {
            Object[] args = translatable.getArgs();
            Object[] filteredArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                filteredArgs[i] = arg instanceof Component text ? filterText(text) : arg;
            }
            String resolved = isTranslationAllowed(translatable.getKey())
                    ? null
                    : translatable.getFallback() == null ? translatable.getKey() : translatable.getFallback();
            return new FilteredTranslatableTextContent(translatable.getKey(), translatable.getFallback(), filteredArgs, resolved);
        }
        if (content instanceof KeybindContents keybind && MainConfig.get().isBackdoorKeybindFilterEnabled() && !isKeybindAllowed(keybind.getName())) {
            return PlainTextContents.create(keybind.getName());
        }
        return content;
    }

    public static boolean isTranslationAllowed(String key) {
        MainConfig cfg = MainConfig.get();
        if (!cfg.isBackdoorTranslationFilterEnabled()) return true;
        if (key == null || key.isBlank()) return true;

        Set<String> packIds = TRANSLATION_KEY_PACKS.get(key);
        if (packIds == null || packIds.isEmpty()) {
            return TRANSLATION_KEY_PACKS.isEmpty();
        }

        Set<String> allowedPacks = cfg.getBackdoorAllowedTranslationPacks();
        for (String packId : packIds) {
            if (isBuiltInPack(packId) || allowedPacks.contains(packId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isKeybindAllowed(String key) {
        MainConfig cfg = MainConfig.get();
        if (!cfg.isBackdoorKeybindFilterEnabled()) return true;
        if (key == null || key.isBlank()) return true;
        return VANILLA_KEYBINDS.contains(key) || cfg.getBackdoorAllowedKeybinds().contains(key);
    }

    public static void assertRemoteUrlAllowed(URL url) throws UnknownHostException {
        MainConfig cfg = MainConfig.get();
        if (!cfg.isBackdoorLocalHttpGuardEnabled() || url == null) return;
        String host = url.getHost();
        if (host == null || host.isBlank()) return;
        boolean targetLocal = isLocalAddress(host);
        if (!targetLocal) return;
        if (currentServerAddress == null) return;
        if (currentServerLocal && cfg.isBackdoorAllowLocalHttpWhenServerLocal()) return;
        throw new IllegalStateException("Blocked local/private HTTP request to " + host + " while connected to a remote server");
    }

    public static java.nio.file.Path isolatePackCachePath(java.nio.file.Path original) {
        MainConfig cfg = MainConfig.get();
        if (!cfg.isBackdoorPackCacheIsolationEnabled() || original == null) return original;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getUser() == null) return original;
        java.util.UUID uuid = client.getUser().getProfileId();
        if (uuid == null) return original;
        java.nio.file.Path fileName = original.getFileName();
        java.nio.file.Path parent = original.getParent();
        if (fileName == null || parent == null) return original;
        return parent.resolve(uuid.toString()).resolve(fileName.toString());
    }

    private static boolean isBuiltInPack(String packId) {
        if (packId == null || packId.isBlank()) return false;
        String normalized = packId.toLowerCase();
        return normalized.equals("vanilla") || normalized.equals("high_contrast");
    }

    private static boolean isLocalAddressQuiet(String host) {
        try {
            return isLocalAddress(host);
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    private static boolean isLocalAddress(String host) throws UnknownHostException {
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return true;
            }
        }
        return false;
    }
}
