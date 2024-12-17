package com.osrs_splits;

import com.Utils.HttpUtil;
import com.Utils.PlayerVerificationStatus;
import com.osrs_splits.PartyManager.PartyManager;
import com.osrs_splits.PartyManager.PlayerInfo;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.PluginPanel;
import net.runelite.discord.DiscordUser;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

public class OsrsSplitPluginPanel extends PluginPanel
{
    private final JButton createPartyButton = new JButton("Create Party");
    private final JButton joinPartyButton = new JButton("Join Party");
    private final JButton leavePartyButton = new JButton("Leave Party");
    private final JLabel passphraseLabel = new JLabel("Passphrase: N/A");
    private final JPanel memberListPanel = new JPanel();
    private final JButton screenshotButton = new JButton("Screenshot and Upload");
    private final JTextField apiKeyField = new JPasswordField(20);
    private final JButton saveApiKeyButton = new JButton("Save");
    private Instant lastScreenshotTime = Instant.EPOCH;

    private final OsrsSplitPlugin plugin;
    private static final int TARGET_NPC_ID = 3031; // goblin
    private static final int[] SPECIAL_ITEM_IDS = {526}; // Bones item ID for testing



    public OsrsSplitPluginPanel(OsrsSplitPlugin plugin)
    {
        this.plugin = plugin;

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        JPanel partyButtonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        partyButtonsPanel.add(createPartyButton);
        partyButtonsPanel.add(joinPartyButton);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(partyButtonsPanel, gbc);

        JPanel leavePanel = new JPanel();
        leavePanel.setLayout(new BoxLayout(leavePanel, BoxLayout.Y_AXIS));
        leavePanel.add(leavePartyButton);
        leavePanel.add(Box.createVerticalStrut(5));
        leavePanel.add(passphraseLabel);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(leavePanel, gbc);

        memberListPanel.setLayout(new BoxLayout(memberListPanel, BoxLayout.Y_AXIS));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        add(memberListPanel, gbc);

        gbc.gridy = 3;
        add(screenshotButton, gbc);

        leavePartyButton.setVisible(false);
        passphraseLabel.setVisible(false);
        screenshotButton.setVisible(false);

        createPartyButton.addActionListener(e -> createParty());
        joinPartyButton.addActionListener(e -> joinParty());
        leavePartyButton.addActionListener(e -> leaveParty());

        screenshotButton.addActionListener(e -> {
            screenshotButton.setEnabled(false);
            sendChatMessages(() -> attemptScreenshot(() -> screenshotButton.setEnabled(true))); // Re-enable after screenshot
        });



    }

    private void createParty() {
        if (plugin.getClient().getLocalPlayer() == null) {
            showLoginWarning();
            return;
        }

        String playerName = plugin.getClient().getLocalPlayer().getName();
        int world = plugin.getClient().getWorld();

        if (plugin.getPartyManager().isInParty(playerName)) {
            JOptionPane.showMessageDialog(this, "You are already in a party.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String passphrase = JOptionPane.showInputDialog(this, "Enter a passphrase for your party:");
        if (passphrase == null || passphrase.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Passphrase cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    // Use the new Socket.IO method to create a party
                    plugin.getWebSocketClient().sendCreateParty(passphrase, playerName);
                    plugin.getPartyManager().createParty(playerName, 0);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(OsrsSplitPluginPanel.this, "Failed to create party: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }

            @Override
            protected void done() {
                updatePassphraseLabel(passphrase);
                enableLeaveParty();
                screenshotButton.setVisible(true);
                updatePartyMembers();
            }
        };

        worker.execute();
    }





    private void joinParty() {
        if (plugin.getClient().getLocalPlayer() == null) {
            showLoginWarning();
            return;
        }

        String passphrase = JOptionPane.showInputDialog(this, "Enter the passphrase of the party to join:");
        if (passphrase == null || passphrase.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Passphrase cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String playerName = plugin.getClient().getLocalPlayer().getName();

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    // Use the new Socket.IO method to join a party
                    plugin.getWebSocketClient().sendJoinParty(passphrase, playerName);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(OsrsSplitPluginPanel.this, "Failed to join party: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }

            @Override
            protected void done() {
                updatePassphraseLabel(passphrase);
                enableLeaveParty();
                screenshotButton.setVisible(true);
                updatePartyMembers();

                JOptionPane.showMessageDialog(OsrsSplitPluginPanel.this, "Joined the party successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        };

        worker.execute();
    }







    private void leaveParty() {
        if (plugin.getClient().getLocalPlayer() == null) {
            showLoginWarning();
            return;
        }

        String playerName = plugin.getClient().getLocalPlayer().getName();
        String passphrase = passphraseLabel.getText().replace("Passphrase: ", "").trim();

        if (passphrase.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No active party to leave.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    plugin.getWebSocketClient().sendLeaveParty(passphrase, playerName); // Emit leave-party event
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(OsrsSplitPluginPanel.this, "Failed to leave party: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }

            @Override
            protected void done() {
                plugin.getPartyManager().leaveParty(playerName);
                leavePartyButton.setVisible(false);
                passphraseLabel.setVisible(false);
                screenshotButton.setVisible(false);
                memberListPanel.removeAll();
                memberListPanel.revalidate();
                memberListPanel.repaint();

                // Re-enable create and join party buttons
                createPartyButton.setEnabled(true);
                joinPartyButton.setEnabled(true);

                JOptionPane.showMessageDialog(OsrsSplitPluginPanel.this, "You have left the party.", "Party Left", JOptionPane.INFORMATION_MESSAGE);
            }
        };

        worker.execute();
    }





    public void enableLeaveParty()
    {
        leavePartyButton.setVisible(true);
        joinPartyButton.setEnabled(false);
    }

    public void disableJoinParty()
    {
        leavePartyButton.setVisible(false);
        joinPartyButton.setEnabled(true);
    }

    public void showLoginWarning()
    {
        JOptionPane.showMessageDialog(this, "You must be logged in to create or join a party.", "Login Required", JOptionPane.WARNING_MESSAGE);
    }

    public void showJoinFailure()
    {
        JOptionPane.showMessageDialog(this, "Could not join the party. It may be full or not exist.", "Join Failed", JOptionPane.WARNING_MESSAGE);
    }

    private JPanel createPlayerCard(String playerName, int world, boolean verified, boolean splitConfirmed, boolean isLeader) {
        JPanel cardPanel = new JPanel(new GridBagLayout());
        cardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1, true),
                new EmptyBorder(5, 5, 5, 5)
        ));
        cardPanel.setBackground(Color.WHITE); // Base background for cards

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Row 1: Player Name + Leader Indicator
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel nameLabel = new JLabel(playerName + (isLeader ? " (Leader)" : ""));
        nameLabel.setForeground(isLeader ? Color.BLUE : Color.BLACK);
        cardPanel.add(nameLabel, gbc);

        // Row 2: World
        gbc.gridy++;
        JLabel worldLabel = new JLabel("World: " + world);
        worldLabel.setForeground(world == plugin.getClient().getWorld() ? Color.GREEN : Color.RED);
        cardPanel.add(worldLabel, gbc);

        // Row 3: Verification Status
        gbc.gridy++;
        JLabel verificationLabel = new JLabel(verified ? "Verified" : "Not Verified");
        verificationLabel.setForeground(verified ? Color.GREEN : Color.RED);
        cardPanel.add(verificationLabel, gbc);

        // Row 4: Split Confirmation
        gbc.gridy++;
        JLabel splitLabel = new JLabel(splitConfirmed ? "Split Confirmed" : "Split Pending");
        splitLabel.setForeground(splitConfirmed ? Color.GREEN : Color.RED);
        cardPanel.add(splitLabel, gbc);

        return cardPanel;
    }


    public void updatePartyMembers() {
        SwingUtilities.invokeLater(() -> {
            memberListPanel.removeAll(); // Clear the panel before re-rendering

            Map<String, PlayerInfo> members = plugin.getPartyManager().getMembers();
            String leader = plugin.getPartyManager().getLeader();

            if (members.isEmpty()) {
                JLabel noMembersLabel = new JLabel("No members in the party.");
                noMembersLabel.setHorizontalAlignment(SwingConstants.CENTER);
                memberListPanel.add(noMembersLabel);
            } else {
                // Add player cards for each member
                for (PlayerInfo player : members.values()) {
                    boolean isLeader = player.getName().equals(leader);
                    JPanel playerCard = createPlayerCard(
                            player.getName(),
                            player.getWorld(),
                            player.isVerified(),
                            player.isConfirmedSplit(),
                            isLeader
                    );
                    memberListPanel.add(playerCard);
                }
            }

            memberListPanel.revalidate();
            memberListPanel.repaint();
        });
    }




    private JPanel createPlayerPanel(PlayerInfo player, String currentPlayer, String leader) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(new LineBorder(Color.GRAY, 1, true), new EmptyBorder(2, 5, 2, 5)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);

        // Name and Leader Status
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel nameLabel = new JLabel(player.getName() + (player.getName().equals(leader) ? " (Leader)" : ""));
        nameLabel.setForeground(player.getName().equals(leader) ? Color.BLUE : Color.BLACK);
        panel.add(nameLabel, gbc);

        // World Label
        gbc.gridy = 1;
        JLabel worldLabel = new JLabel("World " + player.getWorld());
        worldLabel.setForeground(player.getWorld() == plugin.getClient().getWorld() ? Color.GREEN : Color.RED);
        panel.add(worldLabel, gbc);

        // Verification Status
        gbc.gridx = 1;
        JLabel verificationLabel = new JLabel(player.isVerified() ? "Verified" : "Not Verified");
        verificationLabel.setForeground(player.isVerified() ? Color.GREEN : Color.RED);
        panel.add(verificationLabel, gbc);

        // Confirm Split Button
        gbc.gridy = 2;
        if (player.getName().equals(currentPlayer)) {
            JButton confirmButton = new JButton("Confirm Split");
            confirmButton.addActionListener(e -> {
                player.setConfirmedSplit(true);
                sendSplitConfirmation(player.getName());
                updatePartyMembers();
            });
            panel.add(confirmButton, gbc);
        }

        return panel;
    }



    private JLabel createNameLabel(PlayerInfo player) {
        String rankIconPath = getRankIconPath(player.getRank());
        String displayName = player.getName();

        if (rankIconPath != null) {
            try {
                return new JLabel("<html>" + displayName + " <img src='" +
                        getClass().getResource(rankIconPath) + "'/></html>");
            } catch (NullPointerException e) {
                System.err.println("Error loading icon for rank: " + player.getRank());
            }
        }
        return new JLabel(displayName);
    }

    private void sendSplitConfirmation(String playerName) {
        JSONObject payload = new JSONObject();
        payload.put("action", "split_confirmed");
        payload.put("rsn", playerName);

        plugin.getWebSocketClient().send("split_confirmed", payload.toString());
        System.out.println("Sent split confirmation: " + payload);
    }





    /**
     * Returns the rank icon path based on the rank value.
     */
    private String getRankIconPath(int rank) {
        switch (rank) {
            case 0: return "/developer.png";
            case 1: return "/minion.png";
            case 2: return "/corporal.png";
            case 3: return "/colonel.png";
            case 4: return "/admiral.png";
            case 5: return "/marshal.png";
            case 6: return "/astral.png";
            case 7: return "/captain.png";
            case 8: return "/bronze_key.png";
            case 9: return "/silver_key.png";
            case 10: return "/gold_key.png";
            default: return null;
        }
    }

    /**
     * Returns the rank title based on the rank value.
     */
    private String getRankTitle(int rank) {
        switch (rank) {
            case 0: return "Developer";
            case 1: return "Verified Member";
            case 2: return "Tier 1 Splitter";
            case 3: return "Tier 2 Splitter";
            case 4: return "Tier 3 Splitter";
            case 5: return "Tier 4 Splitter";
            case 6: return "Astral Star";
            case 7: return "Tier 5 Splitter";
            case 8: return "Admin";
            case 9: return "Head Admin";
            case 10: return "Kodai";
            default: return "Unknown Rank";
        }
    }






    public void updatePassphraseLabel(String passphrase)
    {
        passphraseLabel.setText("Passphrase: " + passphrase);
    }


    private void sendChatMessages(Runnable afterMessagesSent)
    {
        ChatMessageManager chatMessageManager = plugin.getChatMessageManager();

        // Send announcement
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage("<col=ff0000>*** Nex Splits Kodai ***</col>")
                .build());

        // Send normal messages for each party member
        plugin.getPartyManager().getMembers().forEach((playerName, playerInfo) -> {
            playerName = playerName.trim();
            String message = playerName + " Confirm split World " + playerInfo.getWorld();

            // Queue messages
            chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.PUBLICCHAT)
                    .runeLiteFormattedMessage(message)
                    .build());
        });

        // delay
        Timer delayTimer = new Timer(500, e -> afterMessagesSent.run());
        delayTimer.setRepeats(false);
        delayTimer.start();
    }


    public void processWebSocketMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                JSONObject json = new JSONObject(message);
                String action = json.getString("action");

                if ("party_update".equals(action)) {
                    JSONArray members = json.getJSONArray("members");

                    plugin.getPartyManager().clearMembers();
                    for (int i = 0; i < members.length(); i++) {
                        JSONObject member = members.getJSONObject(i);
                        String name = member.getString("name");
                        int world = member.getInt("world");
                        boolean verified = member.getBoolean("verified");
                        boolean confirmedSplit = member.getBoolean("confirmedSplit");

                        PlayerInfo player = new PlayerInfo(name, world, 0);
                        player.setVerified(verified);
                        player.setConfirmedSplit(confirmedSplit);
                        plugin.getPartyManager().addMember(player);
                    }

                    updatePartyMembers(); // Refresh the UI
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Error processing WebSocket message: " + e.getMessage());
            }
        });
    }






    private void broadcastPartyUpdate(String passphrase) {
        JSONArray memberArray = new JSONArray();

        for (PlayerInfo player : plugin.getPartyManager().getMembers().values()) {
            JSONObject playerJson = new JSONObject();
            playerJson.put("name", player.getName());
            playerJson.put("world", player.getWorld());
            playerJson.put("verified", player.isVerified());
            playerJson.put("confirmedSplit", player.isConfirmedSplit());
            memberArray.put(playerJson);
        }

        JSONObject updatePayload = new JSONObject();
        updatePayload.put("action", "party_update");
        updatePayload.put("passphrase", passphrase);
        updatePayload.put("members", memberArray);

        plugin.getWebSocketClient().sendUpdateParty(updatePayload.toString());
        System.out.println("Broadcasted party update: " + updatePayload);
    }




    public void sendPartyUpdate() {
        if (plugin.getWebSocketClient() == null || !plugin.getWebSocketClient().isOpen()) {
            System.err.println("WebSocket is not connected. Attempting to reconnect...");
            try {
                plugin.getWebSocketClient().reconnect();
                System.out.println("WebSocket reconnected successfully.");
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("WebSocket reconnection failed: " + e.getMessage());
                return; // Avoid sending if the WebSocket is still not connected
            }
        }

        JSONObject payload = new JSONObject();
        payload.put("action", "party_update");
        payload.put("passphrase", plugin.getPartyManager().getLeader());

        JSONArray memberArray = new JSONArray();
        for (PlayerInfo player : plugin.getPartyManager().getMembers().values()) {
            JSONObject playerJson = new JSONObject();
            playerJson.put("name", player.getName());
            playerJson.put("world", player.getWorld());
            playerJson.put("rank", player.getRank());
            playerJson.put("verified", player.isVerified());
            playerJson.put("confirmedSplit", player.isConfirmedSplit());
            memberArray.put(playerJson);
        }

        payload.put("members", memberArray);

        // Send the event with the payload
        plugin.getWebSocketClient().send("party_update", payload.toString());
        System.out.println("Party update payload sent successfully: " + payload);
    }






    ////////////////////////////////////////////////////////////////////////
//                         SCREEN SHOTTING
///////////////////////////////////////////////////////////////////////
    private void attemptScreenshot(Runnable afterScreenshot)
    {
        try {
            screenshotAndUpload();
            showScreenshotNotification();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            afterScreenshot.run(); // Re-enable button
        }
    }


// popup notification
private void showScreenshotNotification()
{
    JOptionPane.showMessageDialog(this, "Screenshot taken and saved!", "Screenshot", JOptionPane.INFORMATION_MESSAGE);
}
private void screenshotAndUpload()
{
    try {
        BufferedImage screenshot = captureScreenshot();
        File screenshotFile = saveScreenshot(screenshot);
        uploadToDiscord(screenshotFile);
    } catch (IOException | AWTException e) {
        e.printStackTrace();
    }
}

    private BufferedImage captureScreenshot() throws AWTException
    {
        // Runelite client window
        Window clientWindow = SwingUtilities.getWindowAncestor(plugin.getClient().getCanvas());

        if (clientWindow != null) {
            // Capture Runelite only
            Rectangle clientBounds = clientWindow.getBounds();
            Robot robot = new Robot();
            return robot.createScreenCapture(clientBounds);
        } else {
            System.out.println("Error: Unable to capture RuneLite client window.");
            return null;
        }
    }



    private File saveScreenshot(BufferedImage screenshot) throws IOException
    {
        // directory path within Runelite dir
        Path runeliteDir = Paths.get(System.getProperty("user.home"), ".runelite", "screenshots", "osrs_splits");
        File screenshotDir = runeliteDir.toFile();

        // Create the dir if DNE
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs();
        }

        // Naming convention
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "screenshot_" + timestamp + ".png";

        // Create the full path for the screenshot file
        File screenshotFile = new File(screenshotDir, filename);

        // Write the image to the specified path
        ImageIO.write(screenshot, "png", screenshotFile);

        System.out.println("Screenshot saved at: " + screenshotFile.getAbsolutePath()); // Debug

        return screenshotFile;
    }


    // Uploading to Discord
    private void uploadToDiscord(File screenshotFile)
    {
        System.out.println("Uploading " + screenshotFile.getName() + " to Discord...");
        // Discord API go here
    }

    // Drop detection
    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        // Get the NPC from event
        NPC npc = event.getNpc();

        // Check if the NPC is the target
        if (npc != null && npc.getId() == TARGET_NPC_ID)
        {
            System.out.println("Loot received from NPC ID: " + npc.getId());

            // Check if the player is in a party
            if (!plugin.getPartyManager().isInParty(plugin.getClient().getLocalPlayer().getName()))
            {
                System.out.println("Player is not in a party. No screenshot will be taken.");
                return;
            }

            // Iterate over the loot items received
            for (ItemStack itemStack : event.getItems())
            {
                System.out.println("Item received: " + itemStack.getId());

                // Check if the item is unique drop
                if (isSpecialItem(itemStack.getId()))
                {
                    System.out.println("Unique item drop detected from target NPC!");

                    // Delay before taking the screenshot to allow the item to render on the ground
                    new Thread(() -> {
                        try
                        {
                            Thread.sleep(1000);

                            //  Screenshot
                            BufferedImage screenshot = captureScreenshot();
                            File screenshotFile = saveScreenshot(screenshot);
                            uploadToDiscord(screenshotFile);

                            // Send notification to user
                            SwingUtilities.invokeLater(() -> showScreenshotNotification("Screenshot taken and uploaded to Discord!"));

                            System.out.println("Screenshot taken and uploaded after delay.");
                        }
                        catch (InterruptedException | IOException | AWTException e)
                        {
                            e.printStackTrace();
                        }
                    }).start();

                    break; // Exit the loop after processing the first matching item
                }
            }
        }
    }


    private void showScreenshotNotification(String message)
    {
        JOptionPane.showMessageDialog(this, message, "Screenshot Notification", JOptionPane.INFORMATION_MESSAGE);
    }


    private boolean isSpecialItem(int itemId) {
        // Check if the item ID matches any of our special items
        return Arrays.stream(SPECIAL_ITEM_IDS).anyMatch(id -> id == itemId);
    }





}


