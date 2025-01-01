package com.osrs_splits;

import com.Utils.PartySocketIOClient;
import com.Utils.PlayerVerificationStatus;
import com.google.inject.Provides;
import com.osrs_splits.PartyManager.PartyManager;
import com.osrs_splits.PartyManager.PlayerInfo;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WorldChanged;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.discord.DiscordService;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

@PluginDescriptor(name = "1Nex Splits Kodai")
public class OsrsSplitPlugin extends Plugin
{
	@Getter
	@Inject
	private Client client;

	@Getter
	@Inject
	private DiscordService discordService;

	@Inject
	private ClientToolbar clientToolbar;

	@Getter
	@Inject
	private OsrsSplitsConfig config;

	@Inject
	private EventBus eventBus;

	@Getter
	@Inject
	private ChatMessageManager chatMessageManager;


	@Getter
	private OsrsSplitPluginPanel panel;
	private NavigationButton navButton;

	@Getter
	private PartyManager partyManager;

	@Getter
	private PartySocketIOClient socketIoClient;

	@Override
	protected void startUp() throws Exception
	{
		partyManager = new PartyManager(config, this);

		panel = new OsrsSplitPluginPanel(this);

		// Initialize Socket.IO
		try
		{
			String socketIoUri = "https://osrssplits.xyz";
			socketIoClient = new PartySocketIOClient(socketIoUri, this);
			System.out.println("Socket.IO client connected successfully.");
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.err.println("Failed to initialize Socket.IO client during startup.");
		}

		eventBus.register(this);
		eventBus.register(panel);

		saveApiKeyToFile(config.apiKey());

		if (discordService != null)
		{
			discordService.init();
		}

		// Create panel button
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
		eventBus.unregister(this);
		eventBus.unregister(panel);

		if (socketIoClient != null)
		{
			socketIoClient.disconnect();
			System.out.println("Socket.IO client disconnected during plugin shutdown.");
		}
	}

	@Provides
	OsrsSplitsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsSplitsConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("OsrsSplit") && event.getKey().equals("saveApiKey"))
		{
			if (config.saveApiKey())
			{
				saveApiKeyToFile(config.apiKey());
				System.out.println("API key saved via configuration.");
			}
		}
	}


	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		int newWorld = client.getWorld();
		//System.out.println("DEBUG: onWorldChanged fired => newWorld = " + newWorld);

		if (newWorld < 1)
		{
			System.out.println("Skipping world update because newWorld = " + newWorld);
			return;
		}


		String localPlayer = client.getLocalPlayer().getName();
		if (!partyManager.isInParty(localPlayer))
		{
			//System.out.println("Not in a party, so skipping world update for " + localPlayer);
			return;
		}

		// If we’re in a party => push new world to PartyManager
		partyManager.updatePlayerData(localPlayer, newWorld);
		//System.out.println("Updated " + localPlayer + " to newWorld=" + newWorld + "; Syncing to server...");
	}


	public static void saveApiKeyToFile(String apiKey)
	{
		if (apiKey == null || apiKey.isEmpty())
		{
			System.out.println("API key is empty. User will not be verified.");
			return;
		}

		Path runeliteDir = Paths.get(System.getProperty("user.home"), ".runelite", "osrs_splits");
		File configDir = runeliteDir.toFile();
		if (!configDir.exists())
		{
			configDir.mkdirs();
		}

		File apiKeyFile = new File(configDir, "api_key.txt");
		try (FileWriter writer = new FileWriter(apiKeyFile))
		{
			writer.write(apiKey);
			System.out.println("API key saved successfully to " + apiKeyFile.getAbsolutePath());
		}
		catch (IOException e)
		{
			System.err.println("Error saving API key: " + e.getMessage());
		}
	}

}
