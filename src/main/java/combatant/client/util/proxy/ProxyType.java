/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.proxy;

import com.google.gson.annotations.SerializedName;

public enum ProxyType {
    @SerializedName("SOCKS4")
    SOCKS4,

    @SerializedName("SOCKS5")
    SOCKS5;

    public static ProxyType safe(ProxyType type) {
        return type == null ? SOCKS5 : type;
    }
}
