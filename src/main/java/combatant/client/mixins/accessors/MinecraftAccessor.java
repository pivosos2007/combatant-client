/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("rightClickDelay")
    int combatant$getItemUseCooldown();

    @Accessor("rightClickDelay")
    void combatant$setItemUseCooldown(int itemUseCooldown);

    @Mutable
    @Accessor("user")
    void combatant$setSession(User session);

    @Mutable
    @Accessor("services")
    void combatant$setServices(Services services);

    @Mutable
    @Accessor("profileFuture")
    void combatant$setProfileFuture(CompletableFuture<ProfileResult> profileFuture);

    @Mutable
    @Accessor("userApiService")
    void combatant$setUserApiService(UserApiService userApiService);

    @Mutable
    @Accessor("userPropertiesFuture")
    void combatant$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> userPropertiesFuture);

    @Mutable
    @Accessor("playerSocialManager")
    void combatant$setPlayerSocialManager(PlayerSocialManager playerSocialManager);

    @Accessor("remoteFriendListUpdateHandler")
    RemoteFriendListUpdateHandler combatant$getRemoteFriendListUpdateHandler();

    @Mutable
    @Accessor("remoteFriendListUpdateHandler")
    void combatant$setRemoteFriendListUpdateHandler(RemoteFriendListUpdateHandler remoteFriendListUpdateHandler);

    @Mutable
    @Accessor("telemetryManager")
    void combatant$setTelemetryManager(ClientTelemetryManager telemetryManager);

    @Mutable
    @Accessor("profileKeyPairManager")
    void combatant$setProfileKeyPairManager(ProfileKeyPairManager profileKeyPairManager);

    @Mutable
    @Accessor("reportingContext")
    void combatant$setReportingContext(ReportingContext reportingContext);

    @Invoker("createUserApiService")
    static UserApiService combatant$createUserApiService(YggdrasilAuthenticationService service, GameConfig config) {
        throw new AssertionError();
    }
}
