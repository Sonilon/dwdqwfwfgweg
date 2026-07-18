package com.pirs.holyinvisible.client;

/**
 * Простое хранилище настроек мода. Держится только в памяти клиента,
 * переключается клавишей и не требует синхронизации с сервером.
 */
public final class HolyConfig {

	private static boolean enabled = true;

	private HolyConfig() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
	}

	public static void set(boolean value) {
		enabled = value;
	}
}
