package com.osrs_splits;

import com.google.inject.Provides;
import javax.inject.Inject;

import com.osrs_splits.PartyManager.PartyManager;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import java.awt.image.BufferedImage;

@PluginDescriptor(
		name = "OSRS Splits"
)
public class OsrsSplitPlugin extends Plugin
{
	@Inject
	Client client;

	@Inject
	private ClientToolbar clientToolbar;

	private OsrsSplitPluginPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception
	{
		panel = new OsrsSplitPluginPanel(this);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/tempIcon.png");

		navButton = NavigationButton.builder()
				.tooltip("OSRS Splits")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
	}

	@Provides
	OsrsSplitsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsSplitsConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			String playerName = client.getLocalPlayer().getName();
			PartyManager.getInstance().updatePartyLeader(playerName);
			panel.updatePassphraseLabel(playerName);
		}
	}
}
