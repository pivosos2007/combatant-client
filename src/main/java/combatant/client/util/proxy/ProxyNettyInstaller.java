/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.proxy;

import combatant.client.util.logging.DebugLog;
import io.netty.channel.ChannelPipeline;

public enum ProxyNettyInstaller {
    ;

    private static final String HANDLER_NAME = "combatant_proxy";

    public static void install(ChannelPipeline pipeline) {
        if (pipeline == null) return;

        ProxyEntry proxy = ProxyBackend.activeProxy();
        if (!proxy.isConfigured()) return;

        try {
            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
            }

            pipeline.addFirst(HANDLER_NAME, new ProxyHandshakeHandler(proxy));
            ProxyBackend.markLastUsed(proxy);
            DebugLog.config("Proxy enabled for connection: %s", proxy);
        } catch (Throwable t) {
            DebugLog.warn("Failed to install proxy handler: %s", proxy);
            DebugLog.error("Proxy handler install error", t);
        }
    }
}
