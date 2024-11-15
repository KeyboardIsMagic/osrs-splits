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
        screenshotButton.setVisible(false); // Initially hidden

        createPartyButton.addActionListener(e -> createParty());
        joinPartyButton.addActionListener(e -> joinParty());
        leavePartyButton.addActionListener(e -> leaveParty());

        screenshotButton.addActionListener(e -> {
            screenshotButton.setEnabled(false); // Disable the button during execution
            sendChatMessages(() -> attemptScreenshot(() -> screenshotButton.setEnabled(true))); // Re-enable after the screenshot
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
                screenshotButton.setVisible(true); // Show screenshot button when party is created
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
        screenshotButton.setVisible(false); // Hide screenshot button when party is disbanded
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
            JPanel playerPanel = new JPanel(new GridLayout(3, 2, 5, 2)); // Adjusted gap between cells
            playerPanel.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(Color.GRAY, 1, true), // Outer gray border
                    new EmptyBorder(5, 10, 5, 10)        // Inner padding
            ));

            // Column 1 content
            JLabel nameLabel = new JLabel(player.getName() + " (level-" + player.getCombatLevel() + ")");

            // Set the color of the world label based on world match with the leader
            JLabel worldLabel = new JLabel("World " + player.getWorld());
            if (player.getWorld() == leaderWorld) {
                worldLabel.setForeground(Color.GREEN); // Green if in the same world as the leader
            } else {
                worldLabel.setForeground(Color.RED); // Red if in a different world
            }

            JButton confirmButton = new JButton("Confirm Split");
            confirmButton.setPreferredSize(new Dimension(90, 20));
            confirmButton.setForeground(Color.LIGHT_GRAY); // Normal button text color
            confirmButton.setContentAreaFilled(true); // Standard button background
            confirmButton.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            confirmButton.addActionListener(e -> {
                player.setConfirmedSplit(true);
                updatePartyMembers(); // Refresh to show the updated status
            });

            // Column 2 content with right alignment for confirmation status
            JLabel confirmationStatus = new JLabel(player.isConfirmedSplit() ? "Yes" : "No", SwingConstants.RIGHT);
            confirmationStatus.setForeground(player.isConfirmedSplit() ? Color.GREEN : Color.RED);
            confirmationStatus.setHorizontalAlignment(SwingConstants.CENTER);

            // Add elements to the grid with inner padding and adjusted spacing
            playerPanel.add(nameLabel);
            playerPanel.add(new JLabel("")); // Empty cell for alignment
            playerPanel.add(worldLabel);
            playerPanel.add(new JLabel("")); // Empty cell for alignment
            playerPanel.add(confirmButton);
            playerPanel.add(confirmationStatus); // Confirmation status text in bottom-right

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

        // Send announcement in special text
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage("<col=ff0000>*** Nex Splits Kodai ***</col>")
                .build());

        // Send normal messages for each party member
        plugin.getPartyManager().getMembers().forEach((playerName, playerInfo) -> {
            // Trim playerName and construct the message
            playerName = playerName.trim();
            String message = playerName + " Confirm split World " + playerInfo.getWorld();

            // Queue the message
            chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.PUBLICCHAT)
                    .runeLiteFormattedMessage(message)
                    .build());
        });

        // Add a delay before executing the screenshot logic
        Timer delayTimer = new Timer(500, e -> afterMessagesSent.run()); // 1.5-second delay
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
            afterScreenshot.run(); // Re-enable the button
        }
    }


// Display popup notification that screenshot was taken
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
        // Obtain the RuneLite client window
        Window clientWindow = SwingUtilities.getWindowAncestor(plugin.getClient().getCanvas());

        if (clientWindow != null) {
            // Capture only the RuneLite client area
            Rectangle clientBounds = clientWindow.getBounds();
            Robot robot = new Robot();
            return robot.createScreenCapture(clientBounds);
        } else {
            System.out.println("Error: Unable to capture RuneLite client window.");
            return null; // Return null if the client window is not found
        }
    }


    // Save screenshot to a file
    private File saveScreenshot(BufferedImage screenshot) throws IOException
    {
        // Define the directory path within the RuneLite directory
        Path runeliteDir = Paths.get(System.getProperty("user.home"), ".runelite", "screenshots", "osrs_splits");
        File screenshotDir = runeliteDir.toFile();

        // Create the directory if it doesn't exist
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs();
        }

        // Create a unique filename based on the current date and time
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "screenshot_" + timestamp + ".png";

        // Create the full path for the screenshot file
        File screenshotFile = new File(screenshotDir, filename);

        // Write the image to the specified path
        ImageIO.write(screenshot, "png", screenshotFile);

        System.out.println("Screenshot saved at: " + screenshotFile.getAbsolutePath()); // Debugging

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
        // Get the NPC from the event
        NPC npc = event.getNpc();

        // Check if the NPC is the target one
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


