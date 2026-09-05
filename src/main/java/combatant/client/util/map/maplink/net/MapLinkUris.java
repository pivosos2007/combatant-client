/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.map.maplink.net;

import java.net.URI;

public enum MapLinkUris {
    ;

    public static URI normalizeBase(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("MapLink base URL is empty");
        String text = raw.trim().replace(" ", "%20");
        if (!text.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) text = "https://" + text;
        int hash = text.indexOf('#');
        if (hash >= 0) text = text.substring(0, hash);
        int query = text.indexOf('?');
        if (query >= 0) text = text.substring(0, query);
        if (text.endsWith("index.html")) text = text.substring(0, text.length() - "index.html".length());
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return URI.create(text);
    }

    public static URI append(URI base, String suffix) {
        String root = base.toString();
        String tail = suffix == null ? "" : suffix.trim();
        if (!tail.startsWith("/")) tail = "/" + tail;
        return URI.create(root + tail.replace(" ", "%20"));
    }

    public static URI resolveAgainstOrigin(URI page, String target) {
        if (target == null || target.isBlank()) return page;
        String clean = target.trim().replace(" ", "%20");
        URI candidate = URI.create(clean);
        if (candidate.isAbsolute()) return candidate;
        String origin = page.getScheme() + "://" + page.getAuthority();
        if (!clean.startsWith("/")) clean = "/" + clean;
        return URI.create(origin + clean);
    }
}
