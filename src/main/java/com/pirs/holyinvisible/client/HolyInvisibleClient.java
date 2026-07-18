package com.pirs.holyinvisible.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class HolyInvisibleClient implements ClientModInitializer {

	public static final String MOD_ID = "holyinvisible";

	// Клавиша появляется в Настройки -> Управление -> категория "HolyinvisibleMod".
	// По умолчанию: H. Игрок может переназначить её в игре в любой момент.
	private static KeyBinding toggleKey;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.holyinvisible.toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				"category.holyinvisible.general"
		));

		WorldRenderEvents.AFTER_ENTITIES.register(HaloRenderer::render);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.wasPressed()) {
				HolyConfig.toggle();
				sendToggleMessage(client);
			}
		});
	}

	private static void sendToggleMessage(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		boolean enabled = HolyConfig.isEnabled();
		Text message = Text.literal("HolyinvisibleMod: ")
				.formatted(Formatting.GOLD)
				.append(Text.literal(enabled ? "нимбы включены" : "нимбы выключены")
						.formatted(enabled ? Formatting.GREEN : Formatting.RED));
		client.player.sendMessage(message, true);
	}
}
