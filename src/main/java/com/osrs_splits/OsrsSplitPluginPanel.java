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
import java.util.Objects;

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

        JPanel leavePanel = new JPanel(new BorderLayout());
        leavePanel.add(leavePartyButton, BorderLayout.NORTH);

        passphraseLabel.setHorizontalAlignment(SwingConstants.CENTER);
        passphraseLabel.setFont(passphraseLabel.getFont().deriveFont(Font.PLAIN));
        passphraseLabel.setBorder(new EmptyBorder(5, 0, 10, 0)); // Add padding (top, left, bottom, right)

        leavePanel.add(passphraseLabel, BorderLayout.SOUTH);

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
        int world = plugin.getClient().getWorld(); // Get the player's world

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
                    // Send the create-party event with the world
                    plugin.getWebSocketClient().sendCreateParty(passphrase, playerName, world);
                    plugin.getPartyManager().createParty(playerName, passphrase);

                    // Set current party details
                    plugin.getPartyManager().setCurrentPartyPassphrase(passphrase);
                    Map<String, PlayerInfo> updatedMembers = Map.of(
                            playerName, new PlayerInfo(playerName, world, 0, false, false)
                    );
                    plugin.getPartyManager().setMembers(updatedMembers);
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
                    plugin.getWebSocketClient().sendJoinParty(
                            passphrase,
                            playerName,
                            plugin.getClient().getWorld(), // Current world
                            plugin.getConfig().apiKey()   // API key for verification
                    );

                    // Retrieve the party details from the server or WebSocket
                    Map<String, PlayerInfo> updatedMembers = plugin.getPartyManager().getMembers(); // Or use server data
                    plugin.getPartyManager().setCurrentPartyPassphrase(passphrase);
                    plugin.getPartyManager().setMembers(updatedMembers);
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
        createPartyButton.setEnabled(false);
    }


    public void showLoginWarning()
    {
        JOptionPane.showMessageDialog(this, "You must be logged in to create or join a party.", "Login Required", JOptionPane.WARNING_MESSAGE);
    }

    public void showJoinFailure()
    {
        JOptionPane.showMessageDialog(this, "Could not join the party. It may be full or not exist.", "Join Failed", JOptionPane.WARNING_MESSAGE);
    }

    private JPanel createPlayerCard(String playerName, int world, boolean verified, boolean splitConfirmed, boolean isLeader, int rank) {
        JPanel cardPanel = new JPanel(new GridBagLayout());
        cardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1, true),
                new EmptyBorder(5, 5, 5, 5)
        ));
        cardPanel.setBackground(getBackground()); // Match parent background

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 1: Name (left) with Leader highlight
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        JLabel nameLabel = new JLabel(playerName + (isLeader ? " (Leader)" : ""));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(isLeader ? Color.BLUE : Color.BLACK);

        // Add rank icon next to the player's name
        if (rank >= 0) {
            String rankIconPath = getRankIconPath(rank);
            if (rankIconPath != null) {
                ImageIcon rankIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource(rankIconPath)));
                nameLabel.setIconTextGap(5); // Add gap between text and icon
                nameLabel.setToolTipText(getRankTitle(rank)); // Tooltip for rank
                nameLabel.setText(playerName + " "); // Add space between name and icon
                nameLabel.setIcon(rankIcon);
            }
        }
        cardPanel.add(nameLabel, gbc);

        // World information
        gbc.gridx = 1;
        JLabel worldLabel = new JLabel("World: " + world);
        worldLabel.setForeground(world == plugin.getClient().getWorld() ? Color.GREEN : Color.RED); // Highlight current world
        cardPanel.add(worldLabel, gbc);

        // Row 2: Discord Label and Verification Status
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel discordLabel = new JLabel("Discord");
        discordLabel.setForeground(new Color(88, 101, 242)); // Discord blue color
        discordLabel.setFont(discordLabel.getFont().deriveFont(Font.BOLD));
        cardPanel.add(discordLabel, gbc);

        gbc.gridx = 1;
        JLabel verificationLabel = new JLabel(verified ? "Verified" : "Not Verified");
        verificationLabel.setForeground(verified ? Color.GREEN : Color.RED); // Green if verified, red otherwise
        cardPanel.add(verificationLabel, gbc);

        // Row 3: Split Confirmation Button and Status
        gbc.gridx = 0;
        gbc.gridy = 2;
        JButton confirmButton = new JButton("Confirm Split");
        confirmButton.setPreferredSize(new Dimension(110, 20));
        confirmButton.addActionListener(e -> {
            plugin.getPartyManager().toggleSplitConfirmation(playerName); // Toggle confirmation status
            updatePartyMembers(); // Refresh the UI
            screenshotButton.setEnabled(plugin.getPartyManager().allPlayersConfirmedAndSameWorld());
        });
        cardPanel.add(confirmButton, gbc);

        gbc.gridx = 1;
        JLabel confirmationStatus = new JLabel(splitConfirmed ? "Yes" : "No");
        confirmationStatus.setForeground(splitConfirmed ? Color.GREEN : Color.RED); // Green if confirmed, red otherwise
        cardPanel.add(confirmationStatus, gbc);

        return cardPanel;
    }







    public void updatePartyMembers() {
        SwingUtilities.invokeLater(() -> {
            memberListPanel.removeAll();

            Map<String, PlayerInfo> members = plugin.getPartyManager().getMembers();
            if (members == null || members.isEmpty()) {
                JLabel noMembersLabel = new JLabel("No party members found.");
                noMembersLabel.setHorizontalAlignment(SwingConstants.CENTER);
                memberListPanel.add(noMembersLabel);
            } else {
                for (PlayerInfo player : members.values()) {
                    JPanel playerCard = createPlayerCard(
                            player.getName(),
                            player.getWorld(),
                            player.isVerified(),
                            player.isConfirmedSplit(),
                            player.getName().equals(plugin.getPartyManager().getLeader()),
                            player.getRank()
                    );
                    memberListPanel.add(playerCard);
                }
            }

            memberListPanel.revalidate();
            memberListPanel.repaint();
        });
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


