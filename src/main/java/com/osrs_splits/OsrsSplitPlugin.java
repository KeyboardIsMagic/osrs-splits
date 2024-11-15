package com.osrs_splits;

import com.google.inject.Provides;
import com.osrs_splits.PartyManager.PartyManager;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WorldChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;

@PluginDescriptor(
		name = "1Nex Splits Kodai"
)
public class OsrsSplitPlugin extends Plugin
{
	@Getter
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OsrsSplitsConfig config;

	private OsrsSplitPluginPanel panel;
	private NavigationButton navButton;

	@Getter
	private PartyManager partyManager;

	@Override
	protected void startUp() throws Exception
	{
		// Initialize PartyManager with the config
		partyManager = new PartyManager(config);

		panel = new OsrsSplitPluginPanel(this);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/tempIcon.png");

		navButton = NavigationButton.builder()
				.tooltip("Nex Splits Kodai")
				.icon(icon)
				.priority(1)
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
			int combatLevel = client.getLocalPlayer().getCombatLevel();
			int world = client.getWorld();

			// Use partyManager instead of PartyManager.getInstance()
			if (partyManager.isLeader(playerName))
			{
				partyManager.updatePlayerData(playerName, combatLevel, world);
				panel.updatePartyMembers();
			}

			panel.updatePassphraseLabel(playerName);
		}
	}

	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		if (client.getLocalPlayer() != null)
		{
			String playerName = client.getLocalPlayer().getName();
			int newWorld = client.getWorld();

			// Update the player's world in partyManager
			partyManager.updatePlayerData(playerName, client.getLocalPlayer().getCombatLevel(), newWorld);

			// Update the UI to reflect the new world
			panel.updatePartyMembers();
		}
	}

	private void updatePlayerData()
	{
		if (client.getLocalPlayer() != null)
		{
			String playerName = client.getLocalPlayer().getName();
			int combatLevel = client.getLocalPlayer().getCombatLevel();
			int world = client.getWorld();
			partyManager.updatePlayerData(playerName, combatLevel, world); // Use partyManager
			panel.updatePartyMembers();
		}
	}
}
