package com.osrs_splits;

import com.osrs_splits.PartyManager.PartyManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;


public class OsrsSplitPluginPanel extends PluginPanel
{
    private final JButton createPartyButton = new JButton("Create Party");
    private final JButton joinPartyButton = new JButton("Join Party");
    private final JButton leavePartyButton = new JButton("Leave Party");
    private final JLabel passphraseLabel = new JLabel("Passphrase: N/A");

    private final OsrsSplitPlugin plugin;

    public OsrsSplitPluginPanel(OsrsSplitPlugin plugin)
    {
        this.plugin = plugin;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel partyButtonsPanel = new JPanel();
        partyButtonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 0));
        partyButtonsPanel.add(createPartyButton);
        partyButtonsPanel.add(joinPartyButton);

        JPanel leavePanel = new JPanel();
        leavePanel.setLayout(new BoxLayout(leavePanel, BoxLayout.Y_AXIS));
        leavePanel.add(Box.createVerticalStrut(10));
        leavePanel.add(leavePartyButton);
        leavePanel.add(Box.createVerticalStrut(5));
        leavePanel.add(passphraseLabel);

        add(partyButtonsPanel);
        add(Box.createVerticalStrut(10));
        add(leavePanel);

        leavePartyButton.setVisible(false);
        passphraseLabel.setVisible(false);

        createPartyButton.addActionListener(e -> createParty());
        joinPartyButton.addActionListener(e -> joinParty());
        leavePartyButton.addActionListener(e -> leaveParty());
    }

    private void createParty()
    {
        String playerName = plugin.client.getLocalPlayer() != null ? plugin.client.getLocalPlayer().getName() : null;
        if (playerName != null)
        {
            boolean success = PartyManager.getInstance().createParty(playerName);
            if (success)
            {
                passphraseLabel.setText("Passphrase: " + playerName);
                leavePartyButton.setVisible(true);
                passphraseLabel.setVisible(true);
                JOptionPane.showMessageDialog(this, "Party created successfully with leader: " + playerName, "Party Created", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        else
        {
            JOptionPane.showMessageDialog(this, "You must be logged in to create a party.", "Login Required", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void joinParty()
    {
        // joinParty logic remains the same
    }

    private void leaveParty()
    {
        PartyManager.getInstance().leaveParty();
        leavePartyButton.setVisible(false);
        passphraseLabel.setVisible(false);
    }

    public void updatePassphraseLabel(String passphrase)
    {
        passphraseLabel.setText("Passphrase: " + passphrase);
    }
}

