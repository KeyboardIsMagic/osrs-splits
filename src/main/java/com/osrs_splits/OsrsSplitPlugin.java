package com.osrs_splits;

import com.Utils.HttpUtil;
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
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import java.awt.image.BufferedImage;

@PluginDescriptor(
		name = "1Nex Splits Kodai"
)
public class OsrsSplitPlugin extends Plugin {

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

	private final String API_URL = "http://127.0.0.1:5000/verify";

	@Getter
	private OsrsSplitPluginPanel panel;
	private NavigationButton navButton;

	@Getter
	private PartyManager partyManager;

	@Getter
	private PartySocketIOClient socketIoClient;

	@Override
	protected void startUp() throws Exception {
		// Initialize the PartyManager
		partyManager = new PartyManager(config, this);

		// Initialize the PluginPanel
		panel = new OsrsSplitPluginPanel(this);

		// Initialize Socket.IO Client
		try {
			String socketIoUri = "http://127.0.0.1:5000";
			socketIoClient = new PartySocketIOClient(socketIoUri, this);
			System.out.println("Socket.IO client connected successfully.");
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Failed to initialize Socket.IO client during startup.");
		}

		// Register the plugin and panel to the EventBus
		eventBus.register(this);
		eventBus.register(panel);

		saveApiKeyToFile(config.apiKey());

		if (discordService != null) {
			discordService.init();
		}

		// Load the plugin icon
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/tempIcon.png");

		// Create the navigation button
		navButton = NavigationButton.builder()
				.tooltip("Nex Splits Kodai")
				.icon(icon)
				.priority(1)
				.panel(panel)
				.build();

		// Add navigation button to the client toolbar
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception {
		// Clean up navigation button and EventBus
		clientToolbar.removeNavigation(navButton);
		eventBus.unregister(this);
		eventBus.unregister(panel);

		// Safely close the Socket.IO client
		if (socketIoClient != null) {
			socketIoClient.disconnect();
			System.out.println("Socket.IO client disconnected during plugin shutdown.");
		}
	}

	@Provides
	OsrsSplitsConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(OsrsSplitsConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null) {
			String playerName = client.getLocalPlayer().getName();
			int world = client.getWorld();

			// Verify player using the API
			PlayerVerificationStatus status = getPlayerVerificationStatus(config.apiKey());
			if (status.isVerified()) {
				PlayerInfo playerInfo = new PlayerInfo(playerName, world, status.getRank(), status.isVerified(), false);
				playerInfo.setVerified(true);
				partyManager.addMember(playerInfo); // Add/update player data
			}
		}
	}


	@Subscribe
	public void onWorldChanged(WorldChanged event) {
		if (client.getLocalPlayer() != null) {
			String playerName = client.getLocalPlayer().getName();
			int newWorld = client.getWorld();

			partyManager.updatePlayerData(playerName, newWorld);
			panel.sendPartyUpdate(); // Call the method from the panel to broadcast changes
			panel.updatePartyMembers(); // Refresh UI to reflect new changes
		}
	}

	private void saveApiKeyToFile(String apiKey) {
		if (apiKey == null || apiKey.isEmpty()) {
			System.out.println("No API key to save.");
			return;
		}

		Path runeliteDir = Paths.get(System.getProperty("user.home"), ".runelite", "osrs_splits");
		File configDir = runeliteDir.toFile();

		// Ensure the directory exists
		if (!configDir.exists()) {
			configDir.mkdirs();
		}

		File apiKeyFile = new File(configDir, "api_key.txt");

		try (FileWriter writer = new FileWriter(apiKeyFile)) {
			writer.write(apiKey);
			System.out.println("API key saved successfully to " + apiKeyFile.getAbsolutePath());
		} catch (IOException e) {
			System.err.println("Error saving API key: " + e.getMessage());
		}
	}

	public PartySocketIOClient getWebSocketClient() {
		return socketIoClient; // Correct variable name
	}



	public PlayerVerificationStatus getPlayerVerificationStatus(String apiKey) {
		JSONObject payload = new JSONObject();
		payload.put("apiKey", apiKey);

		try {
			String response = HttpUtil.postRequest(API_URL, payload.toString());
			JSONObject jsonResponse = new JSONObject(response);
			System.out.println("Using API Key: " + apiKey);

			boolean verified = jsonResponse.optBoolean("verified", false);
			int rank = -1;

			if (verified) {
				JSONArray rsnData = jsonResponse.optJSONArray("rsnData");
				if (rsnData != null) {
					for (int i = 0; i < rsnData.length(); i++) {
						JSONObject rsnObject = rsnData.getJSONObject(i);
						rank = rsnObject.optInt("rank", -1);
						String rsn = rsnObject.optString("rsn");

						// Update the party manager with verified status
						PlayerInfo playerInfo = partyManager.getMember(rsn);
						if (playerInfo != null) {
							playerInfo.setVerified(true);
							playerInfo.setRank(rank);
							partyManager.updatePlayerData(rsn, playerInfo.getWorld());
						}

						return new PlayerVerificationStatus(rsn, true, rank);
					}
				}
			}

			return new PlayerVerificationStatus("", false, -1); // Unverified status
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Error fetching verification status: " + e.getMessage());
			return new PlayerVerificationStatus("", false, -1);
		}
	}




}
