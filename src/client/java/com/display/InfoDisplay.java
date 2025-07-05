package com.display;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text; // Ensure Text is imported
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.util.InputUtil;
import net.minecraft.world.World; // Import for World to get time and moon phase
import net.minecraft.item.ItemStack; // Import for ItemStack to get armor info
import net.minecraft.entity.player.PlayerInventory; // Import for PlayerInventory

import java.util.ArrayList;
import java.util.List;

public class InfoDisplay implements ClientModInitializer {

	// Keybinding for toggling display modes
	private static KeyBinding toggleDisplayKeybind;

	// Display modes
	private static final int MODE_ALL = 0;
	private static final int MODE_COORDS_ONLY = 1;
	private static final int MODE_FPS_ONLY = 2;
	private static final int MODE_CLOCK_ONLY = 3; // Time & Day combined, Moon separate
	private static final int MODE_ARMOR_ONLY = 4; // Armor Status
	private static final int MODE_OFF = 5;        // Off mode

	// Current display mode, starts with OFF
	private static int currentDisplayMode = MODE_OFF;

	@Override
	public void onInitializeClient() {
		// Register the keybinding
		toggleDisplayKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.info-display.toggle", // Translation key for the keybinding name
				InputUtil.Type.KEYSYM,        // Type of input (keyboard key)
				InputUtil.GLFW_KEY_F4,        // Default key is F4
				"category.info-display"   // Translation key for the keybinding category
		));

		// Register a client tick event to listen for key presses
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleDisplayKeybind.wasPressed()) {
				// Cycle through display modes when the key is pressed
				currentDisplayMode = (currentDisplayMode + 1) % (MODE_OFF + 1);

				// Provide feedback to the player using Text.translatable
				Text feedbackText;
				switch (currentDisplayMode) {
					case MODE_ALL:
						feedbackText = Text.translatable("displaymode.all_info");
						break;
					case MODE_COORDS_ONLY:
						feedbackText = Text.translatable("displaymode.coords_only");
						break;
					case MODE_FPS_ONLY:
						feedbackText = Text.translatable("displaymode.fps_only");
						break;
					case MODE_CLOCK_ONLY:
						feedbackText = Text.translatable("displaymode.clock_moon_day");
						break;
					case MODE_ARMOR_ONLY:
						feedbackText = Text.translatable("displaymode.armor_only");
						break;
					case MODE_OFF:
						feedbackText = Text.translatable("displaymode.off");
						break;
					default:
						feedbackText = Text.literal("Display Mode: Unknown"); // Fallback for unknown mode
						break;
				}
				client.player.sendMessage(feedbackText, true); // Send to action bar
			}
		});

		// Register the HUD rendering callback
		HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
			MinecraftClient client = MinecraftClient.getInstance();

			// Only draw if a player is currently in a game world and display is not off
			if (client.player != null && client.world != null && currentDisplayMode != MODE_OFF) {
				TextRenderer textRenderer = client.textRenderer;

				int startX = 5; // X position for all text
				int currentY = 5; // Starting Y position for the first line of text

				// --- Gather all information strings ---
				boolean vsyncEnabled = client.options.getEnableVsync().getValue();
				String vsyncStatus = vsyncEnabled ? "on" : "off";
				String fpsString = String.format("FPS: %d vsync: %s",
						MinecraftClient.getInstance().getCurrentFps(),
						vsyncStatus);

				String coordsString = String.format("X: %d Y: %d Z: %d", client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ());

				// Get in-game time
				long timeOfDay = client.world.getTimeOfDay() % 24000; // Time in ticks (0-23999)
				int hours24 = (int) ((timeOfDay / 1000 + 6) % 24); // Convert ticks to 24-hour format (Minecraft day starts at 6 AM)
				int minutes = (int) ((timeOfDay % 1000) * 60 / 1000); // Convert remaining ticks to minutes

				// Convert to 12-hour format with AM/PM
				String amPm;
				int hours12;

				if (hours24 == 0) { // 00:xx (midnight) -> 12 AM
					hours12 = 12;
					amPm = "AM";
				} else if (hours24 == 12) { // 12:xx (noon) -> 12 PM
					hours12 = 12;
					amPm = "PM";
				} else if (hours24 > 12) { // 13:xx to 23:xx -> 1 PM to 11 PM
					hours12 = hours24 - 12;
					amPm = "PM";
				} else { // 01:xx to 11:xx -> 1 AM to 11 AM
					hours12 = hours24;
					amPm = "AM";
				}
				String timeString = String.format("Time: %02d:%02d %s", hours12, minutes, amPm);

				// Get moon phase
				String moonPhaseString = "Phase: " + getMoonPhaseName(client.world.getMoonPhase());

				// Get Day Count
				long dayCount = client.world.getTime() / 24000L; // Total world time in ticks / ticks per day
				String dayCountString = "Day: " + dayCount;

				// Combined Time and Day string for MODE_ALL and now MODE_CLOCK_ONLY
				String combinedTimeDayString = String.format("Time: %02d:%02d %s (Day: %d)", hours12, minutes, amPm, dayCount);


				// Get armor status
				List<ArmorDisplayInfo> armorDisplayList = new ArrayList<>();
				PlayerInventory inventory = client.player.getInventory();

				// Check for Elytra first if equipped in chest slot
				ItemStack chestPiece = inventory.getArmorStack(2); // Chestplate slot is index 2
				if (!chestPiece.isEmpty() && chestPiece.isDamageable() && chestPiece.getItem() instanceof net.minecraft.item.ElytraItem) {
					int currentDamage = chestPiece.getDamage();
					int maxDamage = chestPiece.getMaxDamage();
					double durabilityPercentage = (double)(maxDamage - currentDamage) / maxDamage * 100.0;
					int percentageColor = getDurabilityColor((int)durabilityPercentage);
					armorDisplayList.add(new ArmorDisplayInfo("Elytra:", String.format(" %d%%", (int)durabilityPercentage), percentageColor));
				} else {
					// Iterate through standard armor slots (helmet, chestplate, leggings, boots)
					// getArmorStack(3) is helmet, (2) chestplate, (1) leggings, (0) boots
					// We iterate from helmet down to boots for display order (3 to 0)
					for (int i = 3; i >= 0; i--) {
						ItemStack armorPiece = inventory.getArmorStack(i);
						if (!armorPiece.isEmpty() && armorPiece.isDamageable()) {
							int currentDamage = armorPiece.getDamage();
							int maxDamage = armorPiece.getMaxDamage();

							// Determine the simplified item name based on slot index
							String simplifiedItemName;
							switch (i) {
								case 3: simplifiedItemName = "Helmet:"; break;
								case 2: simplifiedItemName = "Chest:"; break;
								case 1: simplifiedItemName = "Leggings:"; break;
								case 0: simplifiedItemName = "Boots:"; break;
								default: simplifiedItemName = "Armor:"; break; // Fallback, should not happen for armor slots
							}

							double durabilityPercentage = (double)(maxDamage - currentDamage) / maxDamage * 100.0;
							int percentageColor = getDurabilityColor((int)durabilityPercentage);

							armorDisplayList.add(new ArmorDisplayInfo(simplifiedItemName, String.format(" %d%%", (int)durabilityPercentage), percentageColor));
						}
					}
				}


				// --- Calculate dimensions for the background rectangle ---
				int longestTextWidth = 0;
				int linesToRender = 0;

				// Logic for MODE_ALL
				if (currentDisplayMode == MODE_ALL) {
					longestTextWidth = Math.max(longestTextWidth, textRenderer.getWidth(fpsString));
					linesToRender++;
					longestTextWidth = Math.max(longestTextWidth, textRenderer.getWidth(coordsString));
					linesToRender++;
					longestTextWidth = Math.max(longestTextWidth, textRenderer.getWidth(combinedTimeDayString)); // Use combined string
					linesToRender++;
					longestTextWidth = Math.max(longestTextWidth, textRenderer.getWidth(moonPhaseString));
					linesToRender++;
					for (ArmorDisplayInfo armorInfo : armorDisplayList) {
						int combinedWidth = textRenderer.getWidth(armorInfo.itemName) + textRenderer.getWidth(armorInfo.percentageText);
						longestTextWidth = Math.max(longestTextWidth, combinedWidth);
					}
					linesToRender += armorDisplayList.size();
				}
				// Logic for MODE_FPS_ONLY
				else if (currentDisplayMode == MODE_FPS_ONLY) {
					longestTextWidth = textRenderer.getWidth(fpsString);
					linesToRender = 1;
				}
				// Logic for MODE_COORDS_ONLY
				else if (currentDisplayMode == MODE_COORDS_ONLY) {
					longestTextWidth = textRenderer.getWidth(coordsString);
					linesToRender = 1;
				}
				// Logic for MODE_CLOCK_ONLY (Time & Day combined, Moon separate)
				else if (currentDisplayMode == MODE_CLOCK_ONLY) {
					longestTextWidth = Math.max(textRenderer.getWidth(combinedTimeDayString), textRenderer.getWidth(moonPhaseString)); // CHANGED: Use combined string
					linesToRender = 2; // CHANGED: Now 2 lines (Combined Time/Day + Moon)
				}
				// Logic for MODE_ARMOR_ONLY
				else if (currentDisplayMode == MODE_ARMOR_ONLY) {
					for (ArmorDisplayInfo armorInfo : armorDisplayList) {
						int combinedWidth = textRenderer.getWidth(armorInfo.itemName) + textRenderer.getWidth(armorInfo.percentageText);
						longestTextWidth = Math.max(longestTextWidth, combinedWidth);
					}
					linesToRender = armorDisplayList.size();
				}


				// Calculate total height needed for the background
				int totalHeight = (textRenderer.fontHeight * linesToRender) + (linesToRender > 0 ? (linesToRender - 1) * 2 : 0);

				// Add padding for the background
				int padding = 3;
				int rectX1 = startX - padding;
				int rectY1 = currentY - padding;
				int rectX2 = startX + longestTextWidth + padding;
				int rectY2 = currentY + totalHeight + padding;

				// Draw the semi-transparent black background rectangle
				drawContext.fill(rectX1, rectY1, rectX2, rectY2, 0x60000000);

				// --- Draw texts based on enabled modes ---
				if (currentDisplayMode == MODE_ALL) {
					drawContext.drawText(textRenderer, Text.literal(fpsString), startX, currentY, 0xFFFFFFFF, true);
					currentY += textRenderer.fontHeight + 2;
					drawContext.drawText(textRenderer, Text.literal(coordsString), startX, currentY, 0xFFFFFFFF, true);
					currentY += textRenderer.fontHeight + 2;
					drawContext.drawText(textRenderer, Text.literal(combinedTimeDayString), startX, currentY, 0xFFFFFFFF, true); // Draw combined
					currentY += textRenderer.fontHeight + 2;
					drawContext.drawText(textRenderer, Text.literal(moonPhaseString), startX, currentY, 0xFFFFFFFF, true);
					currentY += textRenderer.fontHeight + 2;
					for (ArmorDisplayInfo armorInfo : armorDisplayList) {
						drawContext.drawText(textRenderer, Text.literal(armorInfo.itemName), startX, currentY, 0xFFFFFFFF, true);
						int percentageX = startX + textRenderer.getWidth(armorInfo.itemName);
						drawContext.drawText(textRenderer, Text.literal(armorInfo.percentageText), percentageX, currentY, armorInfo.percentageColor, true);
						currentY += textRenderer.fontHeight + 2;
					}
				}
				else if (currentDisplayMode == MODE_FPS_ONLY) {
					drawContext.drawText(textRenderer, Text.literal(fpsString), startX, currentY, 0xFFFFFFFF, true);
				}
				else if (currentDisplayMode == MODE_COORDS_ONLY) {
					drawContext.drawText(textRenderer, Text.literal(coordsString), startX, currentY, 0xFFFFFFFF, true);
				}
				else if (currentDisplayMode == MODE_CLOCK_ONLY) {
					drawContext.drawText(textRenderer, Text.literal(combinedTimeDayString), startX, currentY, 0xFFFFFFFF, true); // CHANGED: Draw combined
					currentY += textRenderer.fontHeight + 2;
					drawContext.drawText(textRenderer, Text.literal(moonPhaseString), startX, currentY, 0xFFFFFFFF, true); // CHANGED: Only moon phase is separate
				}
				else if (currentDisplayMode == MODE_ARMOR_ONLY) {
					for (ArmorDisplayInfo armorInfo : armorDisplayList) {
						drawContext.drawText(textRenderer, Text.literal(armorInfo.itemName), startX, currentY, 0xFFFFFFFF, true);
						int percentageX = startX + textRenderer.getWidth(armorInfo.itemName);
						drawContext.drawText(textRenderer, Text.literal(armorInfo.percentageText), percentageX, currentY, armorInfo.percentageColor, true);
						currentY += textRenderer.fontHeight + 2;
					}
				}
			}
		});
	}

	// Helper method to get the moon phase name from its integer ID
	private String getMoonPhaseName(int moonPhase) {
		switch (moonPhase) {
			case 0: return "Full Moon";
			case 1: return "Waning Gibbous";
			case 2: return "Last Quarter";
			case 3: return "Waning Crescent";
			case 4: return "New Moon";
			case 5: return "Waxing Crescent";
			case 6: return "First Quarter";
			case 7: return "Waxing Gibbous";
			default: return "Unknown";
		}
	}

	// Helper method to get color based on durability percentage
	private int getDurabilityColor(int percentage) {
		if (percentage >= 50) {
			return 0xFF00FF00; // Green
		} else if (percentage >= 20) {
			return 0xFFFFFF00; // Yellow
		} else {
			return 0xFFFF0000; // Red
		}
	}

	// Helper class to store armor text parts and their colors
	private static class ArmorDisplayInfo {
		String itemName;
		String percentageText;
		int percentageColor;

		ArmorDisplayInfo(String itemName, String percentageText, int percentageColor) {
			this.itemName = itemName;
			this.percentageText = percentageText;
			this.percentageColor = percentageColor;
		}
	}
}
