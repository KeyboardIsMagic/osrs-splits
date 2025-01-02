package com.osrs_splits;

import com.Utils.HttpUtil;
import com.osrs_splits.PartyManager.PlayerInfo;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.NPC;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.PluginPanel;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class OsrsSplitPluginPanel extends PluginPanel
{
    @Getter
    private final JButton createPartyButton = new JButton("Create Party");
    @Getter
    private final JButton joinPartyButton = new JButton("Join Party");
    @Getter
    private final JButton leavePartyButton = new JButton("Leave Party");
    @Getter
    private final JLabel passphraseLabel = new JLabel("Passphrase: N/A");
    @Getter
    private final JLabel statusLabel = new JLabel("", SwingConstants.CENTER);

    @Setter
    @Getter
    private String lastProposedPassphrase = null;
    private final JPanel memberListPanel = new JPanel();
    @Getter
    private final JButton screenshotButton = new JButton("Screenshot and Upload");
    private Instant lastScreenshotTime = Instant.EPOCH;

    private final OsrsSplitPlugin plugin;
    private static final int TARGET_NPC_ID = 3031; // goblin
    private static final int[] SPECIAL_ITEM_IDS = {526, 26370, 26372, 26374, 26376, 26378, 26380}; // Added Unique Items (REMOVE BONES)****

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
        passphraseLabel.setBorder(new EmptyBorder(5, 0, 10, 0)); // Add padding
        leavePanel.add(passphraseLabel, BorderLayout.SOUTH);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        add(statusLabel, gbc);
        statusLabel.setVisible(false);

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
            sendChatMessages(() -> attemptScreenshot(() -> screenshotButton.setEnabled(true)));
        });
    }

    private void createParty()
    {
        if (plugin.getClient().getLocalPlayer() == null)
        {
            showLoginWarning();
            return;
        }

        final String localPlayer = plugin.getClient().getLocalPlayer().getName();
        final int myWorld = plugin.getClient().getWorld();

        if (plugin.getPartyManager().isInParty(localPlayer))
        {
            JOptionPane.showMessageDialog(this, "You are already in a party.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final String passphrase = JOptionPane.showInputDialog(this, "Enter a passphrase for your party:");
        if (passphrase == null || passphrase.trim().isEmpty())
        {
            JOptionPane.showMessageDialog(this, "Passphrase cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Possibly handle empty API key
        String rawApiKeyTmp = plugin.getConfig().apiKey();
        if (rawApiKeyTmp == null || rawApiKeyTmp.trim().isEmpty())
        {
            rawApiKeyTmp = "null";
        }
        final String rawApiKey = rawApiKeyTmp;

        statusLabel.setText("Creating party, please wait...");
        statusLabel.setVisible(true);

        createPartyButton.setEnabled(false);
        joinPartyButton.setEnabled(false);


        // set passphrase so we don't ignore the party_update
        plugin.getPartyManager().setCurrentPartyPassphrase(passphrase);

        // store it in the panel so PartySocketIOClient knows what we tried
        setLastProposedPassphrase(passphrase);

        //create-party request to server
        SwingWorker<Void, Void> worker = new SwingWorker<>()
        {
            @Override
            protected Void doInBackground()
            {
                plugin.getSocketIoClient().sendCreateParty(
                        passphrase,
                        localPlayer,
                        myWorld,
                        rawApiKey
                );
                return null;
            }

            @Override
            protected void done()
            {
                // wait for the "response" event in PartySocketIOClient => success or error
            }
        };
        worker.execute();
    }

    private void joinParty()
    {
        if (plugin.getClient().getLocalPlayer() == null)
        {
            showLoginWarning();
            return;
        }

        final String passphrase = JOptionPane.showInputDialog(this, "Enter the passphrase of the party to join:");
        if (passphrase == null || passphrase.trim().isEmpty())
        {
            JOptionPane.showMessageDialog(this, "Passphrase cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final String localPlayer = plugin.getClient().getLocalPlayer().getName();
        String tmpApiKey = plugin.getConfig().apiKey();
        if (tmpApiKey == null || tmpApiKey.trim().isEmpty())
        {
            tmpApiKey = "null";
        }
        final String rawApiKey = tmpApiKey;

        statusLabel.setText("Joining party, please wait...");
        statusLabel.setVisible(true);

        createPartyButton.setEnabled(false);
        joinPartyButton.setEnabled(false);

        plugin.getPartyManager().setCurrentPartyPassphrase(passphrase);

        // store passphrase for "response" logic in PartySocketIOClient
        setLastProposedPassphrase(passphrase);

        SwingWorker<Void, Void> worker = new SwingWorker<>()
        {
            @Override
            protected Void doInBackground()
            {
                plugin.getSocketIoClient().sendJoinParty(
                        passphrase,
                        localPlayer,
                        plugin.getClient().getWorld(),
                        rawApiKey
                );
                return null;
            }

            @Override
            protected void done()
            {
                // Wait for the "response" event in PartySocketIOClient => success or error
            }
        };
        worker.execute();
    }


    private void leaveParty()
    {
        if (plugin.getClient().getLocalPlayer() == null)
        {
            showLoginWarning();
            return;
        }

        final String passphrase = passphraseLabel.getText().replace("Passphrase: ", "").trim();
        final String playerName = plugin.getClient().getLocalPlayer().getName();

        SwingWorker<Void, Void> worker = new SwingWorker<>()
        {
            @Override
            protected Void doInBackground()
            {
                try
                {
                    plugin.getSocketIoClient().sendLeaveParty(passphrase, playerName);
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(
                            OsrsSplitPluginPanel.this,
                            "Failed to leave party: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
                return null;
            }

            @Override
            protected void done()
            {
                passphraseLabel.setVisible(false);
                leavePartyButton.setVisible(false);
                screenshotButton.setVisible(false);

                createPartyButton.setEnabled(true);
                joinPartyButton.setEnabled(true);

                statusLabel.setText("No active party.");
                statusLabel.setVisible(true);
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

    private JPanel createPlayerCard(String playerName, int world, boolean verified, boolean splitConfirmed, boolean isLeader, int rank)
    {
        JPanel cardPanel = new JPanel(new GridBagLayout());
        cardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1, true),
                new EmptyBorder(5, 5, 5, 5)
        ));
        cardPanel.setBackground(getBackground());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Name label
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        JLabel nameLabel = new JLabel(playerName + (isLeader ? " (Leader)" : ""));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(isLeader ? Color.ORANGE : Color.GRAY);
        if (rank >= 0) {
            String rankIconPath = getRankIconPath(rank);
            if (rankIconPath != null) {
                ImageIcon rankIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource(rankIconPath)));
                nameLabel.setIconTextGap(5);
                nameLabel.setToolTipText(getRankTitle(rank));
                nameLabel.setText(playerName + " ");
                nameLabel.setIcon(rankIcon);
            }
        }
        cardPanel.add(nameLabel, gbc);

        // World label
        gbc.gridx = 1;
        JLabel worldLabel = new JLabel("World: " + world);
        worldLabel.setForeground(world == plugin.getClient().getWorld() ? Color.GREEN : Color.RED);
        cardPanel.add(worldLabel, gbc);

        // Discord + verification
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel discordLabel = new JLabel("Discord");
        discordLabel.setForeground(new Color(88, 101, 242));
        discordLabel.setFont(discordLabel.getFont().deriveFont(Font.BOLD));
        cardPanel.add(discordLabel, gbc);

        gbc.gridx = 1;
        JLabel verificationLabel = new JLabel(verified ? "Verified" : "Not Verified");
        verificationLabel.setForeground(verified ? Color.GREEN : Color.RED);
        cardPanel.add(verificationLabel, gbc);

        // Confirm split button + status
        gbc.gridx = 0;
        gbc.gridy = 2;
        JButton confirmButton = createConfirmSplitButton(playerName);
        confirmButton.setPreferredSize(new Dimension(110, 20));
        cardPanel.add(confirmButton, gbc);

        gbc.gridx = 1;
        JLabel confirmationStatus = new JLabel(splitConfirmed ? "Yes" : "No");
        confirmationStatus.setForeground(splitConfirmed ? Color.GREEN : Color.RED);
        cardPanel.add(confirmationStatus, gbc);

        return cardPanel;
    }

    public void updatePartyMembers()
    {
        SwingUtilities.invokeLater(() ->
        {
            // Clear out old UI to avoid stale data
            memberListPanel.removeAll();

            String currentPassphrase = plugin.getPartyManager().getCurrentPartyPassphrase();
            Map<String, PlayerInfo> members = plugin.getPartyManager().getMembers();

            if (currentPassphrase == null || members.isEmpty())
            {
                // No active party => show “No active party” and remove stale cards
                statusLabel.setText("No active party.");
                statusLabel.setVisible(true);

                screenshotButton.setVisible(false);
                screenshotButton.setEnabled(false);
                screenshotButton.setToolTipText("Not in a party.");
            }
            else
            {
                statusLabel.setText("");
                statusLabel.setVisible(false);

                screenshotButton.setVisible(true);

                String localPlayer = (plugin.getClient().getLocalPlayer() != null)
                        ? plugin.getClient().getLocalPlayer().getName()
                        : null;
                String partyLeader = plugin.getPartyManager().getLeader();
                boolean isLeader = (localPlayer != null
                        && partyLeader != null
                        && partyLeader.equalsIgnoreCase(localPlayer));

                boolean allConfirmedAndSameWorld = plugin.getPartyManager().allPlayersConfirmedAndSameWorld();

                if (isLeader)
                {
                    if (allConfirmedAndSameWorld)
                    {
                        // Leader + everyone in same world + everyone confirmed
                        screenshotButton.setEnabled(true);
                        screenshotButton.setToolTipText(
                                "All players confirmed and on the same world. You can screenshot now!"
                        );
                    }
                    else
                    {
                        screenshotButton.setEnabled(false);
                        screenshotButton.setToolTipText(
                                "All players must confirm and be on the same world to enable screenshot."
                        );
                    }
                }
                else
                {
                    // Non-leader
                    screenshotButton.setEnabled(false);
                    screenshotButton.setToolTipText("Only the leader can take screenshots. (Orange).");
                }

                // Build and add each player's card
                for (PlayerInfo p : members.values())
                {
                    JPanel playerCard = createPlayerCard(
                            p.getName(),
                            p.getWorld(),
                            p.isVerified(),
                            p.isConfirmedSplit(),
                            p.getName().equals(plugin.getPartyManager().getLeader()),
                            p.getRank()
                    );
                    memberListPanel.add(playerCard);
                    memberListPanel.add(Box.createVerticalStrut(5));
                }
            }

            // Revalidate and repaint the panel to ensure the UI updates
            memberListPanel.revalidate();
            memberListPanel.repaint();
        });
    }


    private String getRankIconPath(int rank)
    {
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
            case 20: return "/zaros.png";
            case 21: return "/learner.png";
            default: return null;
        }
    }

    private String getRankTitle(int rank)
    {
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
            case 20: return "Nex Teacher";
            case 21: return "Nex Learner";
            default: return "Unknown Rank";
        }
    }

    public void updatePassphraseLabel(String passphrase)
    {
        passphraseLabel.setText("Passphrase: " + passphrase);
        passphraseLabel.setVisible(true);
    }

    private void sendChatMessages(Runnable afterMessagesSent)
    {
        ChatMessageManager chatMessageManager = plugin.getChatMessageManager();

        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage("<col=ff0000>*** Nex Splits Kodai ***</col>")
                .build());

        // For each member => build a message based on if they're confirmed
        plugin.getPartyManager().getMembers().forEach((playerName, playerInfo) ->
        {
            playerName = playerName.trim();

            String message;
            if (playerInfo.isConfirmedSplit())
            {
                message = playerName + " is Confirmed in World " + playerInfo.getWorld();
            }
            else
            {
                message = playerName + " has NOT confirmed split in World " + playerInfo.getWorld();
            }

            chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.PUBLICCHAT)
                    .runeLiteFormattedMessage(message)
                    .build());
        });

        Timer delayTimer = new Timer(500, e -> afterMessagesSent.run());
        delayTimer.setRepeats(false);
        delayTimer.start();
    }



    private JButton createConfirmSplitButton(String playerName)
    {
        JButton confirmButton = new JButton("Confirm Split");
        if (!playerName.equals(plugin.getClient().getLocalPlayer().getName()))
        {
            confirmButton.setEnabled(false);
            return confirmButton;
        }

        confirmButton.addActionListener(e ->
        {
            SwingWorker<Void, Void> worker = new SwingWorker<>()
            {
                @Override
                protected Void doInBackground()
                {
                    try
                    {
                        JSONObject payload = new JSONObject()
                                .put("passphrase", plugin.getPartyManager().getCurrentPartyPassphrase())
                                .put("rsn", plugin.getClient().getLocalPlayer().getName())
                                .put("apiKey", plugin.getConfig().apiKey());
                        plugin.getSocketIoClient().send("toggle-confirm-split", payload.toString());
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void done()
                {
                    System.out.println("Sent toggle-confirm-split to server. Waiting for broadcast update.");
                }
            };
            worker.execute();
        });
        return confirmButton;
    }

    private void attemptScreenshot(Runnable afterScreenshot)
    {
        try {
            screenshotAndUpload();
            showScreenshotNotification("Screenshot taken and uploaded to Discord!");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            afterScreenshot.run();
        }
    }

    private void showScreenshotNotification(String s)
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
        Window clientWindow = SwingUtilities.getWindowAncestor(plugin.getClient().getCanvas());
        if (clientWindow != null)
        {
            Rectangle clientBounds = clientWindow.getBounds();
            Robot robot = new Robot();
            return robot.createScreenCapture(clientBounds);
        }
        else
        {
            System.out.println("Error: Unable to capture RuneLite client window.");
            return null;
        }
    }

    private File saveScreenshot(BufferedImage screenshot) throws IOException
    {
        Path runeliteDir = Paths.get(System.getProperty("user.home"), ".runelite", "screenshots", "osrs_splits");
        File screenshotDir = runeliteDir.toFile();

        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "screenshot_" + timestamp + ".png";

        File screenshotFile = new File(screenshotDir, filename);
        ImageIO.write(screenshot, "png", screenshotFile);

        System.out.println("Screenshot saved at: " + screenshotFile.getAbsolutePath());
        return screenshotFile;
    }

    private void uploadToDiscord(File screenshotFile)
    {
        try
        {
            // get list of current party members
            java.util.List<String> partyList = new java.util.ArrayList<>(plugin.getPartyManager().getMembers().keySet());

            // grab party leader
            String leader = plugin.getPartyManager().getLeader();
            if (leader == null || leader.isEmpty())
            {
                leader = "Unknown";
            }

            String itemName = "Confirmation Screenshot";

            HttpUtil.sendUniqueDiscord(
                    "http://127.0.0.1:8000/on-party-screenshot/",    // FIXME
                    partyList,
                    leader,
                    itemName,
                    screenshotFile
            );

            System.out.println("Screenshot posted to Discord via on-drop endpoint.");

        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.out.println("Failed to post screenshot to Discord: " + e.getMessage());
        }
    }



    // Get name for unique item (Could do this on API Side)
    private String getUniqueItem(int uniqueItem) {
        switch (uniqueItem) {
            case 526: return "Bones";
            case 26370: return "Ancient hilt";
            case 26372: return "Nihil Horn";
            case 26374: return "Zaryte vambraces";
            case 26376: return "Torva full helm (damaged)";
            case 26378: return "Torva platebody (damaged)";
            case 26380: return "Torva platelegs (damaged)";

            default: return null;
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        NPC npc = event.getNpc();
        if (npc != null && npc.getId() == TARGET_NPC_ID)
        {
            System.out.println("Loot received from NPC ID: " + npc.getId());

            if (!plugin.getPartyManager().isInParty(plugin.getClient().getLocalPlayer().getName()))
            {
                System.out.println("Player is not in a party. No screenshot will be taken.");
                return;
            }

            for (ItemStack itemStack : event.getItems())
            {
                System.out.println("Item received: " + itemStack.getId());
                if (isSpecialItem(itemStack.getId()))
                {
                    System.out.println("Unique item drop detected from target NPC!");
                    new Thread(() -> {
                        try
                        {

                            Thread.sleep(1000);

                            BufferedImage screenshot = captureScreenshot();
                            File screenshotFile = saveScreenshot(screenshot);

                            java.util.List<String> partyList = new ArrayList<>(plugin.getPartyManager().getMembers().keySet());

                            // Get leader of party
                            String leader = plugin.getPartyManager().getLeader();

                            // API POST Call to post to discord
                            HttpUtil.sendUniqueDiscord("http://127.0.0.1:8000/on-drop/", partyList, leader, getUniqueItem(itemStack.getId()), screenshotFile); // FIXME

                            SwingUtilities.invokeLater(() ->
                                    showScreenshotNotification("Screenshot taken and uploaded to Discord!")
                            );
                            System.out.println("Screenshot taken and uploaded after delay.");
                        }
                        catch (InterruptedException | IOException | AWTException e)
                        {
                            e.printStackTrace();
                        }
                    }).start();
                    break;
                }
            }
        }
    }

    private boolean isSpecialItem(int itemId)
    {
        return Arrays.stream(SPECIAL_ITEM_IDS).anyMatch(id -> id == itemId);
    }
}
