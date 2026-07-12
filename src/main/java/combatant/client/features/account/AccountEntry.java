/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.account;

import combatant.client.util.session.MicrosoftSessionResult;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.Identifier;

import java.util.UUID;

@Getter
public final class AccountEntry {

    private String name;
    private String date;
    @Setter
    private Identifier skin;
    @Setter
    private boolean pinned;
    private int originalIndex;
    private AccountAuthType authType;
    @Setter
    private UUID onlineUuid;
    private String microsoftRefreshToken;

    public AccountEntry(String name, String date, Identifier skin, boolean pinned, int originalIndex) {
        this(name, date, skin, pinned, originalIndex, AccountAuthType.OFFLINE, null, "");
    }

    public AccountEntry(String name,
                        String date,
                        Identifier skin,
                        boolean pinned,
                        int originalIndex,
                        AccountAuthType authType,
                        UUID onlineUuid,
                        String microsoftRefreshToken) {
        this.name = name == null ? "" : name;
        this.date = date == null ? "" : date;
        this.skin = skin;
        this.pinned = pinned;
        this.originalIndex = Math.max(0, originalIndex);
        this.authType = authType == null ? AccountAuthType.OFFLINE : authType;
        this.onlineUuid = onlineUuid;
        this.microsoftRefreshToken = microsoftRefreshToken == null ? "" : microsoftRefreshToken;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public void setDate(String date) {
        this.date = date == null ? "" : date;
    }

    public void togglePinned() {
        pinned = !pinned;
    }

    public void setOriginalIndex(int originalIndex) {
        this.originalIndex = Math.max(0, originalIndex);
    }

    public void setAuthType(AccountAuthType authType) {
        this.authType = authType == null ? AccountAuthType.OFFLINE : authType;
    }

    public boolean isMicrosoft() {
        return authType == AccountAuthType.MICROSOFT;
    }

    public void setMicrosoftRefreshToken(String microsoftRefreshToken) {
        this.microsoftRefreshToken = microsoftRefreshToken == null ? "" : microsoftRefreshToken;
    }

    public void applyMicrosoftSession(MicrosoftSessionResult session) {
        if (session == null) {
            return;
        }
        this.authType = AccountAuthType.MICROSOFT;
        this.name = session.name();
        this.onlineUuid = session.uuid();
        this.microsoftRefreshToken = session.refreshToken();
    }
}
