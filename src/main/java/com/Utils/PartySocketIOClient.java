package com.Utils;

import com.osrs_splits.OsrsSplitPlugin;
import com.osrs_splits.OsrsSplitPluginPanel;
import com.osrs_splits.PartyManager.PlayerInfo;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONArray;
import java.util.Set;
import org.json.JSONObject;

import java.util.*;


import javax.swing.*;
import java.net.URISyntaxException;
import java.util.Timer;

public class PartySocketIOClient
{
    private final OsrsSplitPlugin plugin;
    private Socket socket;
    private String lastAction = null;

    public PartySocketIOClient(String serverUrl, OsrsSplitPlugin plugin)
    {
        this.plugin = plugin;

        try
        {
            socket = IO.socket(serverUrl);

            socket.on(Socket.EVENT_CONNECT, args -> {
                System.out.println("Socket.IO Connected to the server.");
            });

            // Listen for party_update
            socket.on("party_update", args -> {
                try
                {
                    JSONObject partyData = new JSONObject(args[0].toString());
                    System.out.println("Party Update Received: " + partyData);
                    processPartyUpdate(partyData);
                    socket.emit("ack_party_update", "Party update processed successfully");
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            });

            // Listen for joinPartyError
            socket.on("joinPartyError", args -> {
                SwingUtilities.invokeLater(() -> {
                    try
                    {
                        JSONObject obj = new JSONObject(args[0].toString());
                        String msg = obj.optString("message", "Party join error");
                        System.out.println("Received 'joinPartyError': " + obj);

                        plugin.getPartyManager().setCurrentPartyPassphrase(null);

                        plugin.getPanel().getCreatePartyButton().setEnabled(true);
                        plugin.getPanel().getJoinPartyButton().setEnabled(true);

                        plugin.getPanel().getStatusLabel().setText("Error: " + msg);
                        plugin.getPanel().getStatusLabel().setVisible(true);
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                });
            });

            // Listen for "response" from server
            socket.on("response", args -> {
                SwingUtilities.invokeLater(() -> {
                    try {
                        JSONObject obj = new JSONObject(args[0].toString());
                        String status = obj.optString("status", "");
                        String message = obj.optString("message", "");
                        System.out.println("Received 'response': " + obj);

                        OsrsSplitPluginPanel panel = plugin.getPanel();

                        if ("success".equalsIgnoreCase(status)) {
                            // if user just created or joined
                            if ("create-party".equalsIgnoreCase(lastAction) || "join-party".equalsIgnoreCase(lastAction)) {
                                String passphrase = panel.getLastProposedPassphrase();
                                String localPlayer = (plugin.getClient().getLocalPlayer() != null)
                                        ? plugin.getClient().getLocalPlayer().getName()
                                        : null;

                                // set passphrase in the local manager so that we accept the next update :)
                                plugin.getPartyManager().setCurrentPartyPassphrase(passphrase);
                                // create local placeholder
                                plugin.getPartyManager().createParty(localPlayer, passphrase);
                                System.out.println("Party created locally with leader=" + localPlayer + " passphrase=" + passphrase);

                                // *** Force server to broadcast final party data
                                JSONObject fetchPayload = new JSONObject();
                                fetchPayload.put("passphrase", passphrase);
                                fetchPayload.put("apiKey", plugin.getConfig().apiKey());
                                requestPartyUpdate(fetchPayload);
                            }

                            panel.getStatusLabel().setText("");
                            panel.getStatusLabel().setVisible(false);
                            panel.enableLeaveParty();
                            panel.updatePartyMembers();
                        }
                        else {
                            // revert logic
                            plugin.getPartyManager().setCurrentPartyPassphrase(null);
                            plugin.getPartyManager().clearMembers();

                            panel.getStatusLabel().setText("Error: " + message);
                            panel.getStatusLabel().setVisible(true);

                            panel.getCreatePartyButton().setEnabled(true);
                            panel.getJoinPartyButton().setEnabled(true);
                            panel.getLeavePartyButton().setVisible(false);
                            panel.getScreenshotButton().setVisible(false);

                            System.out.println("Create/Join party error -> revert local. Reason: " + message);
                        }

                        lastAction = null;
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            });



            // Connect logs
            socket.on("connect_error", args -> System.err.println("Socket.IO Connection Error: " + args[0]));
            socket.on("connect_timeout", args -> System.err.println("Socket.IO Connection Timeout."));
            socket.on(Socket.EVENT_DISCONNECT, args -> System.out.println("Socket.IO Disconnected."));

            socket.connect();
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
    }



    public PartySocketIOClient(OsrsSplitPlugin plugin)
    {
        this.plugin = plugin;
    }

    public void requestPartyUpdate(JSONObject payload) {
        System.out.println("Sending request_party_update with payload: " + payload);
        socket.emit("request_party_update", payload);
    }

    public void sendCreateParty(String passphrase, String rsn, int world, String apiKey)
    {
        // Mark the action
        lastAction = "create-party";

        JSONObject payload = new JSONObject();
        payload.put("passphrase", passphrase);
        payload.put("rsn", rsn);
        payload.put("world", world);
        payload.put("apiKey", apiKey);

        plugin.getPanel().setLastProposedPassphrase(passphrase);

        System.out.println("Creating party with payload: " + payload);
        socket.emit("create-party", payload);
    }


    public void sendJoinParty(String passphrase, String rsn, int world, String apiKey)
    {
        lastAction = "join-party";

        JSONObject payload = new JSONObject();
        payload.put("passphrase", passphrase);
        payload.put("rsn", rsn);
        payload.put("world", world);
        payload.put("apiKey", apiKey);

        plugin.getPanel().setLastProposedPassphrase(passphrase);

        System.out.println("Sending join-party with payload: " + payload);
        socket.emit("join-party", payload);
    }

    public void sendLeaveParty(String passphrase, String rsn)
    {
        lastAction = "leave-party";

        JSONObject payload = new JSONObject();
        payload.put("passphrase", passphrase);
        payload.put("rsn", rsn);

        System.out.println("Sending leave-party event: " + payload.toString());
        socket.emit("leave-party", payload);
    }


    public void disconnect() {
        if (socket != null && socket.connected()) {
            socket.disconnect();
            System.out.println("Socket.IO client disconnected.");
        } else {
            System.out.println("Socket.IO client is already disconnected.");
        }
    }

    private void processPartyUpdate(JSONObject json)
    {
        SwingUtilities.invokeLater(() ->
        {
            try
            {
                String action = json.optString("action", "");
                String passphrase = json.optString("passphrase", "");
                String leader = json.optString("leader", null);
                JSONArray membersArray = json.optJSONArray("members");

                //  Grab local passphrase first
                String localPassphrase = plugin.getPartyManager().getCurrentPartyPassphrase();

                // If incoming passphrase doesn't match our local passphrase, ignore
                if (!passphrase.equals(localPassphrase))
                {
                    System.out.println(
                            "Ignoring update for mismatched passphrase: "
                                    + passphrase
                                    + " (local is " + localPassphrase + ")"
                    );
                    return;
                }

                // If "party_disband" => we disband the local party
                if ("party_disband".equals(action))
                {
                    System.out.println("Received party_disband for " + passphrase);
                    plugin.getPartyManager().clearMembers();
                    plugin.getPartyManager().setCurrentPartyPassphrase(null);
                    plugin.getPartyManager().setLeader(null);

                    plugin.getPanel().getCreatePartyButton().setEnabled(true);
                    plugin.getPanel().getJoinPartyButton().setEnabled(true);
                    plugin.getPanel().getLeavePartyButton().setVisible(false);
                    plugin.getPanel().getScreenshotButton().setVisible(false);

                    plugin.getPanel().updatePartyMembers();
                    plugin.getPanel().updatePassphraseLabel("");
                    plugin.getPanel().getPassphraseLabel().setVisible(false);
                    return;
                }

                // --- Build updated members
                Map<String, PlayerInfo> updatedMembers = new HashMap<>();
                if (membersArray != null)
                {
                    for (int i = 0; i < membersArray.length(); i++)
                    {
                        JSONObject mem = membersArray.getJSONObject(i);
                        PlayerInfo pInfo = new PlayerInfo(
                                mem.getString("name"),
                                mem.optInt("world", -1),
                                mem.optInt("rank", -1),
                                mem.optBoolean("verified", false),
                                mem.optBoolean("confirmedSplit", false)
                        );
                        updatedMembers.put(pInfo.getName(), pInfo);
                    }
                }

                // If empty => disband
                if (updatedMembers.isEmpty())
                {
                    System.out.println("No members in party " + passphrase + ". Clearing local data.");
                    plugin.getPartyManager().clearMembers();
                    plugin.getPartyManager().setCurrentPartyPassphrase(null);
                    plugin.getPartyManager().setLeader(null);

                    plugin.getPanel().getCreatePartyButton().setEnabled(true);
                    plugin.getPanel().getJoinPartyButton().setEnabled(true);
                    plugin.getPanel().getLeavePartyButton().setVisible(false);
                    plugin.getPanel().getScreenshotButton().setVisible(false);

                    plugin.getPanel().updatePartyMembers();
                    plugin.getPanel().updatePassphraseLabel("");
                    plugin.getPanel().getPassphraseLabel().setVisible(false);
                    return;
                }

                // If local user not in updated => parted ways
                String localPlayer = (plugin.getClient().getLocalPlayer() != null)
                        ? plugin.getClient().getLocalPlayer().getName()
                        : null;

                if (localPlayer != null && !updatedMembers.containsKey(localPlayer))
                {
                    System.out.println(
                            "Local player " + localPlayer + " is not in updated list. Clearing local data..."
                    );
                    plugin.getPartyManager().clearMembers();
                    plugin.getPartyManager().setCurrentPartyPassphrase(null);
                    plugin.getPartyManager().setLeader(null);

                    plugin.getPanel().getCreatePartyButton().setEnabled(true);
                    plugin.getPanel().getJoinPartyButton().setEnabled(true);
                    plugin.getPanel().getLeavePartyButton().setVisible(false);
                    plugin.getPanel().getScreenshotButton().setVisible(false);

                    plugin.getPanel().updatePartyMembers();
                    plugin.getPanel().updatePassphraseLabel("");
                    plugin.getPanel().getPassphraseLabel().setVisible(false);
                    return;
                }

                // normal update => create or update the local membership
                plugin.getPartyManager().updateCurrentParty(passphrase, updatedMembers);
                plugin.getPartyManager().setLeader(leader);

                System.out.println("Updated local party: " + passphrase
                        + " with " + updatedMembers.size() + " members.");
                System.out.println("Leader updated to: " + leader);

                // Hide “Loading…” text, if any
                plugin.getPanel().getStatusLabel().setText("");
                plugin.getPanel().getStatusLabel().setVisible(false);

                // Show passphrase label & update UI
                plugin.getPanel().updatePassphraseLabel(passphrase);
                plugin.getPanel().getPassphraseLabel().setVisible(true);

                plugin.getPanel().updatePartyMembers();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
    }





    public void send(String event, String payload) {
        socket.emit(event, payload);
        System.out.println("Sent event [" + event + "] with payload: " + payload);
    }

    public void sendPartyUpdate(String passphrase, Map<String, PlayerInfo> members) {
        JSONObject payload = new JSONObject();
        payload.put("action", "party_update");
        payload.put("passphrase", passphrase);

        JSONArray memberArray = new JSONArray();
        for (PlayerInfo member : members.values()) {
            JSONObject memberData = new JSONObject();
            memberData.put("name", member.getName());
            memberData.put("world", member.getWorld());
            memberData.put("rank", member.getRank());
            memberData.put("verified", member.isVerified());
            memberData.put("confirmedSplit", member.isConfirmedSplit());
            memberArray.put(memberData);
        }

        payload.put("members", memberArray);

        socket.emit("party_update", payload.toString());
        System.out.println("Sent party_update event: " + payload);
    }



    public void sendDisbandParty(String passphrase) {
        JSONObject payload = new JSONObject();
        payload.put("action", "party_disband");
        payload.put("passphrase", passphrase);

        // Emit the event
        socket.emit("party_disband", payload.toString());
        System.out.println("Sent party_disband event: " + payload);
    }


}
