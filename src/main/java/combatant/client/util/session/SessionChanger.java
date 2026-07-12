/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.session;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import combatant.client.mixins.accessors.MinecraftAccessor;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Services;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public enum SessionChanger {
    ;

    private static String originalUsername = "";
    private static User originalUser;
    private static User singleplayerJoinUser;
    private static User deferredPostDisconnectUser;

    public static CompletableFuture<Void> changeUsername(String newUsername) {
        return changeSession(SessionLoginData.offline(newUsername));
    }

    public static CompletableFuture<Void> changeSession(SessionLoginData data) {
        return changeSession(data, false);
    }

    public static CompletableFuture<Void> changeSession(SessionLoginData data, boolean allowInWorld) {
        if (data == null || normalizeName(data.name()).isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return CompletableFuture.completedFuture(null);
        }

        captureOriginalUsername(client);

        if (!allowInWorld && isInWorld(client)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cannot change Minecraft session while a world is loaded"));
        }

        return applySessionFuture(client, data);
    }

    public static String getCurrentUsername() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getUser() == null) {
            return "";
        }
        captureOriginalUsername(client);
        return client.getUser().getName();
    }

    public static String getOriginalUsername() {
        Minecraft client = Minecraft.getInstance();
        captureOriginalUsername(client);
        return originalUsername;
    }

    public static boolean canUseOriginalLauncherSession(String name, UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        captureOriginalUsername(client);
        if (originalUser == null || originalUser.getAccessToken() == null || originalUser.getAccessToken().isBlank()) {
            return false;
        }
        UUID originalUuid = originalUser.getProfileId();
        if (uuid != null && originalUuid != null) {
            return uuid.equals(originalUuid);
        }
        return !normalizeName(name).isBlank() && normalizeName(name).equalsIgnoreCase(originalUser.getName());
    }

    public static CompletableFuture<Void> restoreOriginalLauncherSession(boolean allowInWorld) {
        Minecraft client = Minecraft.getInstance();
        captureOriginalUsername(client);
        if (originalUser == null || originalUser.getAccessToken() == null || originalUser.getAccessToken().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Original launcher session is not available"));
        }
        return changeSession(new SessionLoginData(
                originalUser.getName(),
                originalUser.getProfileId(),
                originalUser.getAccessToken(),
                true
        ), allowInWorld);
    }

    public static boolean isUsernameChanged() {
        String original = getOriginalUsername();
        String current = getCurrentUsername();
        return !original.isBlank() && !current.isBlank() && !original.equalsIgnoreCase(current);
    }

    public static void captureSingleplayerJoinSession() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || !client.hasSingleplayerServer() || client.getUser() == null) {
            return;
        }
        if (singleplayerJoinUser == null) {
            singleplayerJoinUser = client.getUser();
        }
    }

    public static void clearSingleplayerJoinSession() {
        singleplayerJoinUser = null;
    }

    public static void restoreSingleplayerJoinSessionForDisconnect() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || !client.hasSingleplayerServer() || client.getUser() == null || singleplayerJoinUser == null) {
            return;
        }

        User current = client.getUser();
        if (sameUser(current, singleplayerJoinUser)) {
            return;
        }

        deferredPostDisconnectUser = current;
        ((MinecraftAccessor) client).combatant$setSession(singleplayerJoinUser);
        DebugLog.config("[SessionChanger] restored singleplayer join user for disconnect: current=%s join=%s",
                safeName(current), safeName(singleplayerJoinUser));
    }

    public static void restoreDeferredSessionAfterDisconnect() {
        Minecraft client = Minecraft.getInstance();
        User deferred = deferredPostDisconnectUser;
        if (client == null || deferred == null) {
            return;
        }
        if (client.level != null || client.player != null || client.getSingleplayerServer() != null) {
            return;
        }

        ((MinecraftAccessor) client).combatant$setSession(deferred);
        deferredPostDisconnectUser = null;
        singleplayerJoinUser = null;
        DebugLog.config("[SessionChanger] restored deferred post-disconnect user: %s", safeName(deferred));
    }

    private static CompletableFuture<Void> applySessionFuture(Minecraft client, SessionLoginData data) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        User user = new User(
                data.name(),
                data.uuid(),
                data.accessToken(),
                Optional.empty(),
                Optional.empty()
        );

        Runnable task = () -> {
            try {
                flushSession(client, data, user);
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };

        if (client.isSameThread()) {
            task.run();
        } else {
            client.execute(task);
        }
        return future;
    }

    private static void flushSession(Minecraft client, SessionLoginData data, User user) {
        try {
            MinecraftAccessor accessor = (MinecraftAccessor) client;
            YggdrasilAuthenticationService authService = data.online()
                    ? new YggdrasilAuthenticationService(client.getProxy())
                    : YggdrasilAuthenticationService.createOffline(client.getProxy());

            Services services = Services.create(authService, client.gameDirectory);
            GameConfig original = client instanceof MinecraftGameConfigHolder holder ? holder.combatant$getGameConfig() : null;
            GameConfig.UserData userData = new GameConfig.UserData(user, client.getProxy());
            GameConfig config = original == null
                    ? null
                    : new GameConfig(userData, original.display, original.location, original.game, original.quickPlay);

            UserApiService apiService = data.online() && config != null
                    ? MinecraftAccessor.combatant$createUserApiService(authService, config)
                    : UserApiService.OFFLINE;

            UserApiService.UserProperties properties;
            try {
                properties = apiService.fetchProperties();
            } catch (Throwable ignored) {
                properties = UserApiService.OFFLINE_PROPERTIES;
            }

            CompletableFuture<UserApiService.UserProperties> propertiesFuture = CompletableFuture.completedFuture(properties);
            CompletableFuture<ProfileResult> profileFuture = CompletableFuture.completedFuture(data.online()
                    ? services.sessionService().fetchProfile(data.uuid(), true)
                    : null);

            FriendsService friendsService = authService.createFriendsService(data.accessToken());
            RemoteFriendListUpdateHandler friendList = new RemoteFriendListUpdateHandler(friendsService, client);
            PlayerSocialManager socialManager = new PlayerSocialManager(client, apiService, friendsService, friendList);
            ClientTelemetryManager telemetryManager = new ClientTelemetryManager(client, apiService, user);
            ProfileKeyPairManager keyPairManager = ProfileKeyPairManager.create(apiService, user, client.gameDirectory.toPath());
            ReportingContext reportingContext = ReportingContext.create(ReportEnvironment.local(), apiService);

            accessor.combatant$setServices(services);
            accessor.combatant$setSession(user);
            accessor.combatant$setProfileFuture(profileFuture);
            accessor.combatant$setUserApiService(apiService);
            accessor.combatant$setUserPropertiesFuture(propertiesFuture);
            accessor.combatant$setPlayerSocialManager(socialManager);
            RemoteFriendListUpdateHandler oldFriendList = accessor.combatant$getRemoteFriendListUpdateHandler();
            if (oldFriendList != null) {
                oldFriendList.close();
            }
            accessor.combatant$setRemoteFriendListUpdateHandler(friendList);
            if (socialManager.isFriendListEnabled()) {
                friendList.start();
            }
            accessor.combatant$setTelemetryManager(telemetryManager);
            accessor.combatant$setProfileKeyPairManager(keyPairManager);
            accessor.combatant$setReportingContext(reportingContext);
            client.updateTitle();
        } catch (Throwable t) {
            throw new RuntimeException("Unable to change Minecraft session to: " + data, t);
        }
    }

    private static void captureOriginalUsername(Minecraft client) {
        if (client == null || !originalUsername.isBlank() || client.getUser() == null) {
            return;
        }
        originalUser = client.getUser();
        originalUsername = normalizeName(originalUser.getName());
    }

    private static boolean isInWorld(Minecraft client) {
        return client.player != null || client.level != null || client.getConnection() != null
                || client.gameMode != null || client.getSingleplayerServer() != null;
    }

    private static boolean sameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return safeName(a).equalsIgnoreCase(safeName(b))
                && safeUuid(a).equals(safeUuid(b));
    }

    private static String safeName(User user) {
        return user == null ? "" : normalizeName(user.getName());
    }

    private static UUID safeUuid(User user) {
        return user == null || user.getProfileId() == null ? new UUID(0L, 0L) : user.getProfileId();
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }
}
