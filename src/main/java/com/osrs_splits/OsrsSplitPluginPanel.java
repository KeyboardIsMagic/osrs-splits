package com.osrs_splits;

import com.osrs_splits.PartyManager.PartyManager;
import com.osrs_splits.PartyManager.PlayerInfo;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class OsrsSplitPluginPanel extends PluginPanel
{
    private final JButton createPartyButton = new JButton("Create Party");
    private final JButton joinPartyButton = new JButton("Join Party");
    private final JButton leavePartyButton = new JButton("Leave Party");
    private final JLabel passphraseLabel = new JLabel("Passphrase: N/A");
    private final JPanel memberListPanel = new JPanel();

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

        leavePartyButton.setVisible(false);
        passphraseLabel.setVisible(false);

        createPartyButton.addActionListener(e -> createParty());
        joinPartyButton.addActionListener(e -> joinParty());
        leavePartyButton.addActionListener(e -> leaveParty());
    }

    private void createParty()
    {
        if (plugin.client.getLocalPlayer() != null)
        {
            String playerName = plugin.client.getLocalPlayer().getName();
            int combatLevel = plugin.client.getLocalPlayer().getCombatLevel();
            int world = plugin.client.getWorld();

            boolean success = PartyManager.getInstance().createParty(playerName);
            if (success)
            {
                PartyManager.getInstance().updatePlayerData(playerName, combatLevel, world); // Directly update player data
                updatePassphraseLabel(playerName);
                enableLeaveParty();
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
        if (plugin.client.getLocalPlayer() != null)
        {
            String playerName = plugin.client.getLocalPlayer().getName();
            int combatLevel = plugin.client.getLocalPlayer().getCombatLevel();
            int world = plugin.client.getWorld();

            boolean success = PartyManager.getInstance().joinParty(playerName, combatLevel, world);
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
        if (plugin.client.getLocalPlayer() != null)
        {
            String playerName = plugin.client.getLocalPlayer().getName();
            PartyManager.getInstance().leaveParty(playerName);
        }

        leavePartyButton.setVisible(false);
        passphraseLabel.setVisible(false);
        memberListPanel.removeAll();
        memberListPanel.revalidate();
        memberListPanel.repaint();
        joinPartyButton.setEnabled(true); // Re-enable Join Party button
    }


    public void enableLeaveParty()
    {
        leavePartyButton.setVisible(true);
        joinPartyButton.setEnabled(false);
    }

    // Disable leave party button and re-enable join party button
    public void disableJoinParty()
    {
        leavePartyButton.setVisible(false);
        joinPartyButton.setEnabled(true);
    }

    // Show a warning message if the user is not logged in
    public void showLoginWarning()
    {
        JOptionPane.showMessageDialog(this, "You must be logged in to create or join a party.", "Login Required", JOptionPane.WARNING_MESSAGE);
    }

    // Show a failure message if joining the party failed
    public void showJoinFailure()
    {
        JOptionPane.showMessageDialog(this, "Could not join the party. It may be full or not exist.", "Join Failed", JOptionPane.WARNING_MESSAGE);
    }

    void updatePartyMembers()
    {
        memberListPanel.removeAll();
        Map<String, PlayerInfo> members = PartyManager.getInstance().getMembers();
        for (PlayerInfo player : members.values())
        {
            JPanel playerPanel = new JPanel();
            playerPanel.setLayout(new GridLayout(1, 2, 5, 5)); // Adjusted layout for better fit
            JLabel playerInfo = new JLabel("<html>Name: " + player.getName() + "<br>Level: " + player.getCombatLevel() + "<br>World: " + player.getWorld() + "</html>");
            JButton confirmButton = new JButton("Confirm Split");
            confirmButton.addActionListener(e -> {
                System.out.println(player.getName() + " has confirmed the split.");
                confirmButton.setEnabled(false);
            });
            playerPanel.add(playerInfo);
            playerPanel.add(confirmButton);

            memberListPanel.add(playerPanel);
        }

        memberListPanel.revalidate();
        memberListPanel.repaint();
    }

    public void updatePassphraseLabel(String passphrase)
    {
        passphraseLabel.setText("Passphrase: " + passphrase);
    }
}
