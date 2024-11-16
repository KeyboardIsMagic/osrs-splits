package com.osrs_splits;

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

    private void createParty()
    {
        if (plugin.getClient().getLocalPlayer() != null)
        {
            String playerName = plugin.getClient().getLocalPlayer().getName();
            int combatLevel = plugin.getClient().getLocalPlayer().getCombatLevel();
            int world = plugin.getClient().getWorld();

            boolean success = plugin.getPartyManager().createParty(playerName);
            if (success)
            {
                plugin.getPartyManager().updatePlayerData(playerName, combatLevel, world);
                updatePassphraseLabel(playerName);
                enableLeaveParty();
                screenshotButton.setVisible(true);
                JOptionPane.showMessageDialog(this, "Party created successfully with leader: " + playerName, "Party Created", JOptionPane.INFORMATION_MESSAGE);
                updatePartyMembers();
            }
        }
        else
        {
            showLoginWarning();
        }
    }

    private void joinParty()
    {
        if (plugin.getClient().getLocalPlayer() != null)
        {
            String playerName = plugin.getClient().getLocalPlayer().getName();
            int combatLevel = plugin.getClient().getLocalPlayer().getCombatLevel();
            int world = plugin.getClient().getWorld();

            boolean success = plugin.getPartyManager().joinParty(playerName, combatLevel, world);
            if (success)
            {
                JOptionPane.showMessageDialog(this, "Joined the party successfully!", "Party Joined", JOptionPane.INFORMATION_MESSAGE);
                disableJoinParty();
                updatePartyMembers();
            }
            else
            {
                showJoinFailure();
            }
        }
    }

    private void leaveParty()
    {
        if (plugin.getClient().getLocalPlayer() != null)
        {
            String playerName = plugin.getClient().getLocalPlayer().getName();
            plugin.getPartyManager().leaveParty(playerName);
        }

        leavePartyButton.setVisible(false);
        passphraseLabel.setVisible(false);
        screenshotButton.setVisible(false);
        memberListPanel.removeAll();
        memberListPanel.revalidate();
        memberListPanel.repaint();
        joinPartyButton.setEnabled(true);
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

    void updatePartyMembers()
    {
        memberListPanel.removeAll();
        Map<String, PlayerInfo> members = plugin.getPartyManager().getMembers();
        boolean allConfirmed = true;
        boolean sameWorld = true;
        int leaderWorld = members.get(plugin.getPartyManager().getLeader()).getWorld();

        for (PlayerInfo player : members.values())
        {
            JPanel playerPanel = new JPanel(new GridBagLayout());
            playerPanel.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(Color.GRAY, 1, true),
                    new EmptyBorder(2, 5, 2, 5)
            ));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(2, 5, 2, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Row 1: Name and World
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1.0;
            JLabel nameLabel = new JLabel(player.getName() + " (level-" + player.getCombatLevel() + ")");
            playerPanel.add(nameLabel, gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            JLabel worldLabel = new JLabel("World " + player.getWorld());
            worldLabel.setForeground(player.getWorld() == leaderWorld ? Color.GREEN : Color.RED);
            playerPanel.add(worldLabel, gbc);

            // Row 2: Discord and Verification Status
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weightx = 1.0;
            JLabel discordLabel = new JLabel("Discord Verification:");
            discordLabel.setHorizontalAlignment(SwingConstants.CENTER);
            playerPanel.add(discordLabel, gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            JLabel verificationLabel = new JLabel();
            if (plugin.isDiscordVerified(player.getName()))
            {
                verificationLabel.setText("Verified");
                verificationLabel.setForeground(Color.GREEN);
            }
            else
            {
                verificationLabel.setText("Not Verified");
                verificationLabel.setForeground(Color.RED);
            }
            verificationLabel.setHorizontalAlignment(SwingConstants.CENTER);
            playerPanel.add(verificationLabel, gbc);

            // Row 3: Confirm Split Button, Yes/No
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.weightx = 0.5;
            JButton confirmButton = new JButton("Confirm Split");
            confirmButton.setPreferredSize(new Dimension(110, 20));
            confirmButton.addActionListener(e -> {
                player.setConfirmedSplit(true);
                updatePartyMembers();
            });
            playerPanel.add(confirmButton, gbc);

            gbc.gridx = 1;
            gbc.weightx = 0.5;
            JLabel confirmationStatus = new JLabel(player.isConfirmedSplit() ? "Yes" : "No", SwingConstants.LEFT);
            confirmationStatus.setForeground(player.isConfirmedSplit() ? Color.GREEN : Color.RED);
            playerPanel.add(confirmationStatus, gbc);

            memberListPanel.add(playerPanel);

            if (!player.isConfirmedSplit()) allConfirmed = false;
            if (player.getWorld() != leaderWorld) sameWorld = false;
        }

        memberListPanel.revalidate();
        memberListPanel.repaint();

        screenshotButton.setEnabled(allConfirmed && sameWorld);
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


