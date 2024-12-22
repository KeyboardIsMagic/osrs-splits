package com.osrs_splits;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("OsrsSplit")
public interface OsrsSplitsConfig extends Config
{
	@ConfigSection(
			name = "API Configuration",
			description = "Settings for managing the API key",
			position = 0
	)
	String apiConfigSection = "apiConfig";

	@ConfigItem(
			keyName = "apiKey",
			name = "API Key",
			description = "Enter your API key here (hidden for security)",
			secret = true,
			position = 1,
			section = apiConfigSection
	)
	String apiKey();

	@ConfigItem(
			keyName = "saveApiKey",
			name = "Save API Key",
			description = "Click to save or overwrite the API key file.",
			position = 2,
			section = apiConfigSection
	)
	default boolean saveApiKey() {
		return false; // Default to false, acts as a trigger
	}
}
