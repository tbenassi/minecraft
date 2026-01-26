package github.tbenassi.devauth.utils;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import static github.tbenassi.devauth.DevAuth.LOGGER;
import github.tbenassi.devauth.mixin.AbuseReportContextAccessor;
import github.tbenassi.devauth.mixin.MinecraftClientAccessor;
import github.tbenassi.devauth.mixin.SplashTextResourceSupplierAccessor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.util.Util;

import java.util.concurrent.CompletableFuture;

public final class SessionUtils {
    // The access token used for offline sessions
    public static final String OFFLINE_TOKEN = "invalidtoken";
    // The time of the last session status check (milliseconds since epoch)

    /**
     * Returns the current Minecraft session.
     *
     * @return current Minecraft session instance
     */
    public static Session getSession()
    {
        return MinecraftClient.getInstance().getSession();
    }

    /**
     * Replaces the Minecraft session instance.
     *
     * @param session new Minecraft session
     */
    public static void setSession(Session session)
    {
        final MinecraftClient client = MinecraftClient.getInstance();

        // Use an accessor mixin to update the 'private final' Minecraft session
        ((MinecraftClientAccessor) client).setSession(session);
        ((SplashTextResourceSupplierAccessor) client.getSplashTextLoader()).setSession(session);

        // Re-create the game profile future
        ((MinecraftClientAccessor) client).setGameProfileFuture(
                CompletableFuture.supplyAsync(() -> client.getApiServices().sessionService().fetchProfile(session.getUuidOrNull(), true),
                        Util.getDownloadWorkerExecutor()));

        // Re-create the user API service (ignore offline session)
        UserApiService userApiService = UserApiService.OFFLINE;
        if (!OFFLINE_TOKEN.equals(session.getAccessToken())) {
            userApiService = getAuthService().createUserApiService(session.getAccessToken());
        }
        ((MinecraftClientAccessor) client).setUserApiService(userApiService);

        // Re-create the social interactions manager
        ((MinecraftClientAccessor) client).setSocialInteractionsManager(
                new SocialInteractionsManager(client, userApiService)
        );

        // Re-create the profile keys
        ((MinecraftClientAccessor) client).setProfileKeys(
                ProfileKeys.create(userApiService, session, client.runDirectory.toPath())
        );

        // Re-create the abuse report context
        ((MinecraftClientAccessor) client).setAbuseReportContext(
                AbuseReportContext.create(
                        ((AbuseReportContextAccessor) (Object) client.getAbuseReportContext()).getEnvironment(),
                        userApiService
                )
        );

        LOGGER.info(
                "Minecraft session for {} (uuid={}) has been applied", session.getUsername(), session.getUuidOrNull()
        );
    }

    /**
     * Returns the Yggdrasil Authentication Service.
     *
     * @return Yggdrasil Authentication Service instance
     */
    public static YggdrasilAuthenticationService getAuthService()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        return new YggdrasilAuthenticationService(client.getNetworkProxy());
    }

    /**
     * The status of a Minecraft session.
     *
     */
    public enum SessionStatus
    {
        VALID, INVALID, OFFLINE, UNKNOWN
    }
}
