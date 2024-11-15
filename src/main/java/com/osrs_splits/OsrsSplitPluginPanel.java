package com.osrs_splits;

import com.osrs_splits.PartyManager.PartyManager;
import com.osrs_splits.PartyManager.PlayerInfo;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.Map;

public class OsrsSplitPluginPanel extends PluginPanel
{
    private final JButton createPartyButton = new JButton("Create Party");
    private final JButton joinPartyButton = new JButton("Join Party");
    private final JButton leavePartyButton = new JButton("Leave Party");
    private final JLabel passphraseLabel = new JLabel("Passphrase: N/A");
    private final JPanel memberListPanel = new JPanel();
    private final JButton screenshotButton = new JButton("Screenshot and Upload");

    private final OsrsSplitPlugin plugin;

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

        screenshotButton.addActionListener(e -> screenshotAndUpload()); // Action on click
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

    private void screenshotAndUpload()
    {
        System.out.println("Screenshot taken and uploaded to Discord.");
    }
}
