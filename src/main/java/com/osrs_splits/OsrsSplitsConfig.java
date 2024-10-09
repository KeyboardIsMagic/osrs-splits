package com.osrs_splits;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("OsrsSplit")
public interface OsrsSplitsConfig extends Config
{
	@ConfigItem(
			keyName = "partySizeLimit",
			name = "Party Size Limit",
			description = "Set the maximum number of players allowed in the party"
	)
	default int partySizeLimit()
	{
		return 4; // Default party size limit
	}

	@ConfigItem(
			keyName = "screenshotOnConfirm",
			name = "Screenshot on Confirm",
			description = "Take a screenshot when all players confirm the split"
	)
	default boolean screenshotOnConfirm()
	{
		return true; // Enable screenshot on confirm by default
	}

}
