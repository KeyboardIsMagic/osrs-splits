package com.Utils;

import com.osrs_splits.OsrsSplitPlugin;
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

    public PartySocketIOClient(String serverUrl, OsrsSplitPlugin plugin) {
        this.plugin = plugin;

        try {
            // Initialize the Socket.IO client
            socket = IO.socket(serverUrl);

            // Event: Connected
            socket.on(Socket.EVENT_CONNECT, args -> {
                System.out.println("Socket.IO Connected to the server.");
            });

            // Event: Party Update
            socket.on("party_update", args -> {
                try {
                    // Parse received party data
                    JSONObject partyData = new JSONObject(args[0].toString());
                    System.out.println("Party Update Received: " + partyData);

                    // Process the received update
                    processPartyUpdate(partyData);

                    // Send acknowledgment back to the server
                    socket.emit("ack_party_update", "Party update processed successfully");
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Failed to process party update: " + e.getMessage());
                }
            });

            // Event: Error
            socket.on("connect_error", args -> {
                System.err.println("Socket.IO Connection Error: " + args[0]);
            });

            socket.on("connect_timeout", args -> {
                System.err.println("Socket.IO Connection Timeout.");
            });

            // Event: Disconnect
            socket.on(Socket.EVENT_DISCONNECT, args -> {
                System.out.println("Socket.IO Disconnected.");
            });

            // Connect to the server
            socket.connect();

        } catch (URISyntaxException e) {
            System.err.println("Invalid server URL: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public PartySocketIOClient(OsrsSplitPlugin plugin)
    {
        this.plugin = plugin;
    }


    public void sendCreateParty(String passphrase, String rsn, int world) {
        if (rsn == null || rsn.isEmpty()) {
            System.err.println("Invalid RSN: " + rsn);
            return;
        }

        JSONObject payload = new JSONObject();
        payload.put("passphrase", passphrase);
        payload.put("rsn", rsn);
        payload.put("world", world);

        socket.emit("create-party", payload);
        System.out.println("Sent create-party event with payload: " + payload);
    }




    public void sendJoinParty(String passphrase, String rsn, int world, String apiKey) {
        JSONObject payload = new JSONObject();
        payload.put("passphrase", passphrase);
        payload.put("rsn", rsn);
        payload.put("world", world);
        payload.put("apiKey", apiKey); // Include the API key for server verification

        socket.emit("join-party", payload);
        System.out.println("Sent join-party event with payload: " + payload);
    }


    public void disconnect() {
        if (socket != null && socket.connected()) {
            socket.disconnect();
            System.out.println("Socket.IO client disconnected.");
        } else {
            System.out.println("Socket.IO client is already disconnected.");
        }
    }

    private void processPartyUpdate(Object data) {
        SwingUtilities.invokeLater(() -> {
            try {
                JSONObject json = new JSONObject(data.toString());
                String passphrase = json.getString("passphrase");

                // Ensure the update is only processed for the current party
                if (!passphrase.equals(plugin.getPartyManager().getCurrentPartyPassphrase())) {
                    System.out.println("Ignoring update for mismatched passphrase: " + passphrase);
                    return;
                }

                String leader = json.optString("leader", null);
                JSONArray membersArray = json.getJSONArray("members");

                Map<String, PlayerInfo> updatedMembers = new HashMap<>();
                for (int i = 0; i < membersArray.length(); i++) {
                    JSONObject memberJson = membersArray.getJSONObject(i);
                    PlayerInfo playerInfo = new PlayerInfo(
                            memberJson.getString("name"),
                            memberJson.optInt("world", -1),
                            memberJson.optInt("rank", -1),
                            memberJson.optBoolean("verified", false),
                            memberJson.optBoolean("confirmedSplit", false)
                    );
                    updatedMembers.put(playerInfo.getName(), playerInfo);
                }

                plugin.getPartyManager().updateCurrentParty(passphrase, updatedMembers);
                plugin.getPartyManager().setLeader(leader);
                plugin.getPanel().updatePartyMembers();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Failed to process party update: " + e.getMessage());
            }
        });
    }












    public void fetchBatchVerification(Set<String> rsns, String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("API key is missing. Cannot verify RSNs.");
            return;
        }

        JSONObject payload = new JSONObject();
        payload.put("apiKey", apiKey);
        payload.put("rsns", new JSONArray(rsns));

        try {
            String response = HttpUtil.postRequest("http://127.0.0.1:5000/verify-batch", payload.toString());
            JSONObject jsonResponse = new JSONObject(response);

            if (jsonResponse.optBoolean("verified", false)) {
                JSONArray rsnData = jsonResponse.optJSONArray("rsnData");
                if (rsnData != null) {
                    for (int i = 0; i < rsnData.length(); i++) {
                        JSONObject rsnObject = rsnData.optJSONObject(i);
                        if (rsnObject != null && rsnObject.has("name")) {
                            String name = rsnObject.optString("name", null);
                            int rank = rsnObject.optInt("rank", -1);
                            boolean verified = rsnObject.optBoolean("verified", false);

                            if (name != null) {
                                PlayerInfo playerInfo = new PlayerInfo(name, -1, rank, verified, false);
                                plugin.getPartyManager().addMember(playerInfo);
                            } else {
                                System.err.println("Invalid RSN data: missing or null name.");
                            }
                        } else {
                            System.err.println("Invalid RSN object: " + rsnObject);
                        }
                    }

                    // Update UI
                    plugin.getPanel().updatePartyMembers();
                } else {
                    System.err.println("No rsnData array in batch verification response.");
                }
            } else {
                System.err.println("Batch verification failed or unverified.");
            }
            // Immediately update UI
                plugin.getPanel().updatePartyMembers();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to fetch batch verification: " + e.getMessage());
        }
    }







    public void emitClientState() {
        Timer timer = new Timer(); // Create a Timer instance
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (plugin.getPartyManager().isInParty(plugin.getClient().getLocalPlayer().getName())) {
                    JSONObject payload = new JSONObject();
                    payload.put("passphrase", plugin.getPartyManager().getCurrentPartyPassphrase());
                    payload.put("name", plugin.getClient().getLocalPlayer().getName());
                    payload.put("world", plugin.getClient().getWorld());
                    plugin.getWebSocketClient().send("client_state_update", payload.toString());
                }
            }
        }, 0, 5000); // Delay of 0 ms, repeat every 5000 ms (5 seconds)
    }





    public void sendLeaveParty(String passphrase, String rsn) {
        JSONObject payload = new JSONObject();
        payload.put("passphrase", passphrase);
        payload.put("rsn", rsn);

        socket.emit("leave-party", payload);
        System.out.println("Sent leave-party event: " + payload);
    }

    public void sendUpdateParty(String payload) {
        socket.emit("party_update", payload);
        System.out.println("Sent party_update event: " + payload);
    }

    public boolean isOpen() {
        return socket.connected();
    }

    public void reconnect() {
        if (!socket.connected()) {
            socket.connect();
            System.out.println("Reconnecting to Socket.IO server...");
            if (plugin.getPartyManager().getCurrentPartyPassphrase() != null) {
                sendJoinParty(
                        plugin.getPartyManager().getCurrentPartyPassphrase(),
                        plugin.getClient().getLocalPlayer().getName(),
                        plugin.getClient().getWorld(), // Add the current world
                        plugin.getConfig().apiKey()    // Add the API key
                );
            }
        }
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
