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
package combatant.client.mixins.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import combatant.client.features.security.BackdoorProtection;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

@Mixin(HttpUtil.class)
public abstract class NetworkUtilsMixin {

    @ModifyVariable(method = "downloadFile", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Path combatant$backdoor$isolatePackCache(Path original) {
        return BackdoorProtection.isolatePackCachePath(original);
    }

    @WrapOperation(method = "downloadFile", at = @At(value = "INVOKE", target = "Ljava/net/HttpURLConnection;getInputStream()Ljava/io/InputStream;"))
    private static InputStream combatant$backdoor$guardLocalRedirects(
            HttpURLConnection connection,
            Operation<InputStream> original,
            @Local(argsOnly = true) Map<String, String> headers,
            @Local(argsOnly = true) Proxy proxy,
            @Local LocalRef<HttpURLConnection> httpURLConnection
    ) throws IOException {
        HttpURLConnection current = connection;
        BackdoorProtection.assertRemoteUrlAllowed(current.getURL());
        current.setInstanceFollowRedirects(false);

        int redirects = 0;
        int status = current.getResponseCode();
        while (isRedirect(status, current.getHeaderField("Location"))) {
            if (++redirects > 20) {
                throw new ProtocolException("Too many redirects while downloading resource pack");
            }

            URL next = resolveRedirect(current);
            BackdoorProtection.assertRemoteUrlAllowed(next);

            current = (HttpURLConnection) next.openConnection(proxy);
            current.setInstanceFollowRedirects(false);
            headers.forEach(current::setRequestProperty);
            status = current.getResponseCode();
        }

        httpURLConnection.set(current);
        return original.call(current);
    }

    private static boolean isRedirect(int status, String location) {
        if (location == null || location.isBlank()) return false;
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static URL resolveRedirect(HttpURLConnection connection) throws IOException {
        String location = connection.getHeaderField("Location");
        if (location == null || location.isBlank()) {
            throw new ProtocolException("Redirect response without Location header");
        }
        try {
            return new URL(location);
        } catch (Exception ignored) {
            return new URL(connection.getURL(), location);
        }
    }
}
