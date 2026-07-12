/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on In-Game Account Switcher
 * (https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher).
 * Copyright (c) 2015-2022 The_Fireplace and 2021-2026 VidTu.
 *
 * Original portions remain under LGPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.session.microsoft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import combatant.client.util.session.MicrosoftSessionResult;
import combatant.client.util.logging.DebugLog;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Microsoft/Xbox/Minecraft authentication backend.
 *
 * <p>Flow and endpoint handling are derived from In-Game Account Switcher
 * (LGPL-3.0-or-later), Copyright (C) 2015-2022 The_Fireplace,
 * Copyright (C) 2021-2026 VidTu.</p>
 */
public final class MicrosoftAuthService {

    private static final String CLIENT_ID = "54fd49e4-2103-4044-9603-2b028c814ec3";
    private static final String USER_AGENT = "Combatant AltManager";
    private static final Duration TIMEOUT = Duration.ofSeconds(Long.getLong("combatant.microsoftAuth.timeout", 15L));

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        private int index;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "CombatantMicrosoftAuth-" + (++index));
            thread.setDaemon(true);
            return thread;
        }
    });

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NEVER)
            .executor(EXECUTOR)
            .build();

    private MicrosoftAuthService() {
    }

    public static CompletableFuture<MicrosoftDeviceCode> requestDeviceCode() {
        String payload = "client_id=" + CLIENT_ID + "&scope=XboxLive.signin%20XboxLive.offline_access";
        HttpRequest request = formPost("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", payload);
        return logFailures("request Microsoft device code",
                sendJson(request, "request Microsoft device code")
                        .thenApply(MicrosoftDeviceCode::fromJson));
    }

    public static CompletableFuture<MicrosoftSessionResult> loginWithDeviceCode(MicrosoftDeviceCode code) {
        if (code == null) {
            return failedLoggedFuture("start Microsoft device-code login", new NullPointerException("code"));
        }

        Instant deadline = Instant.now().plus(code.expiresIn());
        return logFailures("poll Microsoft device-code login",
                CompletableFuture.supplyAsync(() -> pollUntilAuthorized(code, deadline), EXECUTOR))
                .thenCompose(MicrosoftAuthService::loginWithMicrosoftTokens);
    }

    public static CompletableFuture<MicrosoftSessionResult> loginWithRefreshToken(String refreshToken) {
        String clean;
        try {
            clean = cleanToken(refreshToken, "refreshToken");
        } catch (Throwable t) {
            return failedLoggedFuture("start Microsoft refresh-token login", t);
        }
        return refreshToMicrosoftTokens(clean).thenCompose(MicrosoftAuthService::loginWithMicrosoftTokens);
    }

    public static CompletableFuture<MicrosoftSessionResult> loginWithAuthorizationCode(String code, String redirectUri) {
        String cleanCode;
        String cleanRedirect;
        try {
            cleanCode = cleanToken(code, "code");
            cleanRedirect = cleanToken(redirectUri, "redirectUri");
        } catch (Throwable t) {
            return failedLoggedFuture("start Microsoft authorization-code login", t);
        }

        String payload = "client_id=" + CLIENT_ID +
                "&code=" + encode(cleanCode) +
                "&grant_type=authorization_code" +
                "&redirect_uri=" + encode(cleanRedirect) +
                "&scope=XboxLive.signin%20XboxLive.offline_access";
        return logFailures("exchange Microsoft authorization code",
                sendJson(formPost("https://login.live.com/oauth20_token.srf", payload), "exchange Microsoft authorization code")
                        .thenApply(MicrosoftTokens::fromJson))
                .thenCompose(MicrosoftAuthService::loginWithMicrosoftTokens);
    }

    private static CompletableFuture<MicrosoftTokens> refreshToMicrosoftTokens(String refreshToken) {
        String clean;
        try {
            clean = cleanToken(refreshToken, "refreshToken");
        } catch (Throwable t) {
            return failedLoggedFuture("refresh Microsoft OAuth token", t);
        }

        String payload = "client_id=" + CLIENT_ID +
                "&refresh_token=" + encode(clean) +
                "&grant_type=refresh_token" +
                "&scope=XboxLive.signin%20XboxLive.offline_access";
        return logFailures("refresh Microsoft OAuth token",
                sendJson(formPost("https://login.live.com/oauth20_token.srf", payload), "refresh Microsoft OAuth token")
                        .thenApply(MicrosoftTokens::fromJson));
    }

    static CompletableFuture<MicrosoftSessionResult> loginWithMicrosoftTokens(MicrosoftTokens tokens) {
        if (tokens == null) {
            return failedLoggedFuture("build Microsoft session", new NullPointerException("tokens"));
        }

        return microsoftAccessToXbox(tokens.accessToken())
                .thenCompose(xbl -> xboxToXsts(xbl.token(), xbl.userHash()))
                .thenCompose(xsts -> xstsToMinecraftAccess(xsts.token(), xsts.userHash()))
                .thenCompose(minecraftAccess -> minecraftAccessToProfile(minecraftAccess)
                        .thenApply(profile -> new MicrosoftSessionResult(profile.name(), profile.uuid(), minecraftAccess, tokens.refreshToken())));
    }

    private static MicrosoftTokens pollUntilAuthorized(MicrosoftDeviceCode code, Instant deadline) {
        DeviceAuthPendingException pending = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                return deviceCodeToMicrosoftTokens(code.deviceCode());
            } catch (DeviceAuthPendingException e) {
                pending = e;
                try {
                    Thread.sleep(Math.max(1000L, code.interval().toMillis()));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new MicrosoftAuthException("Microsoft device-code login interrupted", interrupted);
                }
            }
        }
        throw new MicrosoftAuthException("Microsoft device-code login expired", pending, "combatant.auth.microsoft.expired");
    }

    private static MicrosoftTokens deviceCodeToMicrosoftTokens(String deviceCode) {
        String clean = cleanToken(deviceCode, "deviceCode");
        String payload = "grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                "&client_id=" + CLIENT_ID +
                "&device_code=" + encode(clean);
        HttpRequest request = formPost("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", payload);
        HttpResponse<String> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Throwable t) {
            throw new MicrosoftAuthException("Unable to poll Microsoft device-code login", t);
        }
        int status = response.statusCode();
        if (status == HttpURLConnection.HTTP_OK) {
            return MicrosoftTokens.fromJson(parseJson(response.body(), "parse Microsoft device-code token response"));
        }
        try {
            JsonObject json = parseJson(response.body(), "parse Microsoft device-code error");
            String error = MicrosoftJson.string(json, "error");
            if ("authorization_pending".equals(error)) {
                throw new DeviceAuthPendingException("Microsoft device-code login is still pending");
            }
            if ("authorization_declined".equals(error)) {
                throw new MicrosoftAuthException("Microsoft device-code login was cancelled by the user", "combatant.auth.microsoft.cancelled");
            }
            if ("expired_token".equals(error)) {
                throw new MicrosoftAuthException("Microsoft device-code login expired", "combatant.auth.microsoft.expired");
            }
        } catch (DeviceAuthPendingException | MicrosoftAuthException e) {
            throw e;
        } catch (Throwable ignored) {
        }
        throw badStatus("poll Microsoft device-code login", response);
    }

    private static CompletableFuture<XboxToken> microsoftAccessToXbox(String microsoftAccess) {
        String clean;
        try {
            clean = cleanToken(microsoftAccess, "microsoftAccess");
        } catch (Throwable t) {
            return failedLoggedFuture("exchange Microsoft access token to Xbox Live token", t);
        }

        JsonObject request = new JsonObject();
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + clean);
        request.add("Properties", properties);
        request.addProperty("RelyingParty", "http://auth.xboxlive.com");
        request.addProperty("TokenType", "JWT");
        return logFailures("exchange Microsoft access token to Xbox Live token",
                sendJson(jsonPost("https://user.auth.xboxlive.com/user/authenticate", request), "exchange Microsoft access token to Xbox Live token")
                        .thenApply(XboxToken::fromJson));
    }

    private static CompletableFuture<XboxToken> xboxToXsts(String xboxToken, String expectedHash) {
        String cleanToken;
        try {
            cleanToken = cleanToken(xboxToken, "xboxToken");
        } catch (Throwable t) {
            return failedLoggedFuture("exchange Xbox Live token to XSTS", t);
        }

        JsonObject request = new JsonObject();
        JsonObject properties = new JsonObject();
        JsonArray userTokens = new JsonArray();
        userTokens.add(cleanToken);
        properties.add("UserTokens", userTokens);
        properties.addProperty("SandboxId", "RETAIL");
        request.add("Properties", properties);
        request.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        request.addProperty("TokenType", "JWT");
        return logFailures("exchange Xbox Live token to XSTS",
                sendRaw(jsonPost("https://xsts.auth.xboxlive.com/xsts/authorize", request), "exchange Xbox Live token to XSTS")
                        .thenApply(response -> {
                    int status = response.statusCode();
                    if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        throw xstsUnauthorized(response);
                    }
                    if (status != HttpURLConnection.HTTP_OK) {
                        throw badStatus("exchange Xbox Live token to XSTS", response);
                    }
                    XboxToken token = XboxToken.fromJson(parseJson(response.body(), "parse XSTS response"));
                    if (expectedHash != null && !expectedHash.isBlank() && !expectedHash.equals(token.userHash())) {
                        throw new MicrosoftAuthException("Mismatching Xbox user hash between XBL and XSTS");
                    }
                    return token;
                }));
    }

    private static CompletableFuture<String> xstsToMinecraftAccess(String xstsToken, String userHash) {
        String cleanToken;
        String cleanHash;
        try {
            cleanToken = cleanToken(xstsToken, "xstsToken");
            cleanHash = cleanToken(userHash, "userHash");
        } catch (Throwable t) {
            return failedLoggedFuture("exchange XSTS token to Minecraft access token", t);
        }

        JsonObject request = new JsonObject();
        request.addProperty("identityToken", "XBL3.0 x=" + cleanHash + ";" + cleanToken);
        return logFailures("exchange XSTS token to Minecraft access token",
                sendJson(jsonPost("https://api.minecraftservices.com/authentication/login_with_xbox", request), "exchange XSTS token to Minecraft access token")
                        .thenApply(json -> MicrosoftJson.string(json, "access_token")));
    }

    private static CompletableFuture<MinecraftProfile> minecraftAccessToProfile(String minecraftAccess) {
        String clean;
        try {
            clean = cleanToken(minecraftAccess, "minecraftAccess");
        } catch (Throwable t) {
            return failedLoggedFuture("fetch Minecraft profile", t);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer " + clean)
                .timeout(TIMEOUT)
                .GET()
                .build();
        return logFailures("fetch Minecraft profile",
                sendRaw(request, "fetch Minecraft profile")
                        .thenApply(response -> {
                    int status = response.statusCode();
                    if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                        throw new MicrosoftAuthException("Microsoft account has no Minecraft profile", "combatant.auth.microsoft.no_profile");
                    }
                    if (status != HttpURLConnection.HTTP_OK) {
                        throw badStatus("fetch Minecraft profile", response);
                    }
                    return MinecraftProfile.fromJson(parseJson(response.body(), "parse Minecraft profile"));
                }));
    }

    private static <T> CompletableFuture<T> logFailures(String action, CompletableFuture<T> future) {
        return future.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                logAuthFailure(action, throwable);
            }
        });
    }

    private static <T> CompletableFuture<T> failedLoggedFuture(String action, Throwable throwable) {
        logAuthFailure(action, throwable);
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    private static void logAuthFailure(String action, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof DeviceAuthPendingException) {
            return;
        }

        String message = cause.getMessage();
        DebugLog.error("[AltManager][MicrosoftAuth] Failed to %s%s", cause, action,
                message == null || message.isBlank() ? "" : ": " + message);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? new MicrosoftAuthException("Unknown Microsoft authentication failure") : current;
    }

    private static HttpRequest formPost(String uri, String payload) {
        return HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
    }

    private static HttpRequest jsonPost(String uri, JsonObject payload) {
        return HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(MicrosoftJson.GSON.toJson(payload)))
                .build();
    }

    private static CompletableFuture<JsonObject> sendJson(HttpRequest request, String action) {
        return sendRaw(request, action).thenApply(response -> {
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw badStatus(action, response);
            }
            return parseJson(response.body(), action);
        });
    }

    private static CompletableFuture<HttpResponse<String>> sendRaw(HttpRequest request, String action) {
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .exceptionally(t -> {
                    throw new MicrosoftAuthException("Unable to " + action, t);
                });
    }

    private static JsonObject parseJson(String body, String action) {
        try {
            JsonObject json = MicrosoftJson.GSON.fromJson(body, JsonObject.class);
            return Objects.requireNonNull(json, "response body is null");
        } catch (Throwable t) {
            throw new MicrosoftAuthException("Unable to " + action + ": invalid JSON response", t);
        }
    }

    private static MicrosoftAuthException xstsUnauthorized(HttpResponse<String> response) {
        try {
            JsonObject json = parseJson(response.body(), "parse XSTS unauthorized response");
            long error = MicrosoftJson.number(json, "XErr");
            if (error == 2148916233L) {
                return new MicrosoftAuthException("Microsoft account has no linked Xbox account", "combatant.auth.microsoft.no_xbox");
            }
            if (error == 2148916235L) {
                return new MicrosoftAuthException("Xbox Live is not available for this account region", "combatant.auth.microsoft.xbox_unavailable");
            }
            if (error == 2148916236L || error == 2148916237L || error == 2148916238L) {
                return new MicrosoftAuthException("Microsoft account is not allowed to use Xbox Live for Minecraft", "combatant.auth.microsoft.xbox_adult_required");
            }
            return new MicrosoftAuthException("Unknown XSTS authorization error: " + error);
        } catch (MicrosoftAuthException e) {
            return e;
        } catch (Throwable t) {
            return new MicrosoftAuthException("XSTS authorization failed with HTTP 401", t);
        }
    }

    private static MicrosoftAuthException badStatus(String action, HttpResponse<String> response) {
        return new MicrosoftAuthException("Unable to " + action + ": HTTP " + response.statusCode() + " body=" + redact(response.body()));
    }

    private static String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    private static String cleanToken(String token, String name) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(name + " is blank");
        }
        return token.trim();
    }

    private static String redact(String body) {
        if (body == null) {
            return "<null>";
        }
        if (body.length() > 512) {
            return body.substring(0, 512) + "...";
        }
        return body;
    }
}
