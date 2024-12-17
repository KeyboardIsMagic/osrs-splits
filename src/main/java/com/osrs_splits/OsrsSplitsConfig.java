package com.osrs_splits;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("OsrsSplit")
public interface OsrsSplitsConfig extends Config
{
	@ConfigItem(
			keyName = "apiKey",
			name = "API Key",
			description = "Enter your API key here (hidden for security)",
			secret = true
	)
	default String apiKey()
	{
		return "";
	}
}
