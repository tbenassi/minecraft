package io.github.tbenassi.com.craftfromnearbycontainers.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CraftFromNearbyContainersClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("craft-from-nearby-storage-client");

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof CraftingScreen || screen instanceof InventoryScreen) {
				if (screen instanceof InventoryScreen inventoryScreen) {
					LOGGER.info("Inventory screen opened");
				}
				if (screen instanceof CraftingScreen craftingScreen) {
					LOGGER.info("Crafting screen opened");
				}
			}
		});
	}
}