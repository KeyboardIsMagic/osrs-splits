package com.osrs_splits;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

import com.osrs_splits.PartyManager.PartyManager;
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
import java.awt.image.BufferedImage;

@PluginDescriptor(
		name = "0SRS Splits \"The Kodai\""
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

			// Ensure player data is updated correctly on login
			if (PartyManager.getInstance().isLeader(playerName))
			{
				PartyManager.getInstance().updatePlayerData(playerName, combatLevel, world);
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

			// Update the player's world in PartyManager
			PartyManager.getInstance().updatePlayerData(playerName, client.getLocalPlayer().getCombatLevel(), newWorld);

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
			PartyManager.getInstance().updatePlayerData(playerName, combatLevel, world);
			panel.updatePartyMembers();
		}
	}
}
