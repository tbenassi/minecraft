package github.tbenassi.devauth;

import com.google.gson.JsonObject;
import github.tbenassi.devauth.config.DevAuthConfig;
import github.tbenassi.devauth.utils.MicrosoftUtils;
import github.tbenassi.devauth.utils.SessionUtils;
import me.shedaniel.autoconfig.ConfigHolder;
import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DevAuth implements ClientModInitializer {
    public static final String MOD_ID = "devauth";

    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ConfigHolder<DevAuthConfig> CONFIG = DevAuthConfig.init();

    @Override
    public void onInitializeClient() {
        // Prepare a new executor thread to run the login task on
        Executor executor = Executors.newSingleThreadExecutor();

        // Try to load a saved Minecraft Access Token.
        if (!CONFIG.getConfig().minecraftAccessToken.isEmpty())
        {

            String token = CONFIG.getConfig().minecraftAccessToken;
            LOGGER.info("Loaded Minecraft Access Token: {}", token);

            // Decode the saved Minecraft Access Token.
            JsonObject decodedToken = MicrosoftUtils.decodeMCAccessToken(token);

            // Check if the token is expired
            if (MicrosoftUtils.isTokenExpired(decodedToken)) {
                LOGGER.info("Minecraft Access Token is expired.");
                // Get a new token
                fetchAndLogin(executor);
                return;
            }

            // Use the saved token
            login(token, executor);
            return;
        }

        // No saved token, fetch one
        fetchAndLogin(executor);
    }

    private static void fetchAndLogin(Executor executor) {
        CompletableFuture<String> task = MicrosoftUtils
                // Acquire a Microsoft auth code
                .acquireMSAuthCode(
                        success -> "Successfully acquired Microsoft auth code! You can now close the window.",
                        executor
                )

                // Exchange the Microsoft auth code for an access token
                .thenComposeAsync(msAuthCode -> MicrosoftUtils.acquireMSAccessToken(msAuthCode, executor))

                // Exchange the Microsoft access token for an Xbox access token
                .thenComposeAsync(msAccessToken -> MicrosoftUtils.acquireXboxAccessToken(msAccessToken, executor))

                // Exchange the Xbox access token for an XSTS token
                .thenComposeAsync(xboxAccessToken -> MicrosoftUtils.acquireXboxXstsToken(xboxAccessToken, executor))

                // Exchange the Xbox XSTS token for a Minecraft access token
                .thenComposeAsync(xboxXstsData -> MicrosoftUtils.acquireMCAccessToken(
                        xboxXstsData.get("Token"), xboxXstsData.get("uhs"), executor
                ))

                .thenApply(mcToken -> mcToken);

        try {
            String token = task.get();
            login(token, executor);
        } catch (Exception e) {
            LOGGER.error("Failed to fetch access token: {}", e.getMessage());
        }
    }

    private static void login(String token, Executor executor) {
        MicrosoftUtils.login(token, executor)

                // Update the game session and greet the player
                .thenAccept(session -> {
                    // Apply the new session
                    SessionUtils.setSession(session);
                    // Add a toast that greets the player
                    SystemToast.add(
                            // get the minecraft client
                            MinecraftClient.getInstance().getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                            Text.translatable("gui.devauth.toast.greeting", Text.literal(session.getUsername())), null
                    );

                    // Mark the task as successful, in turn closing the screen
                    LOGGER.info("Successfully logged in via Microsoft!");
                });
    }

    /**
     * Returns the config instance.
     *
     * @return config instance
     * @see ConfigHolder#getConfig()
     */
    public static DevAuthConfig getConfig()
    {
        return CONFIG.getConfig();
    }
}