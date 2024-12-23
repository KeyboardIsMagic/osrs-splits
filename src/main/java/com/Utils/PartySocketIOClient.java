package com.Utils;

import com.osrs_splits.OsrsSplitPlugin;
import com.osrs_splits.PartyManager.PlayerInfo;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Timer;
import java.util.TimerTask;


import javax.swing.*;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

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
            socket.on("party_update", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    System.out.println("Party Update Received: " + args[0]);
                    processPartyUpdate(args[0]);
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
        try {
            JSONObject json = new JSONObject(data.toString());

            String passphrase = json.getString("passphrase");
            if (!passphrase.equals(plugin.getPartyManager().getCurrentPartyPassphrase())) {
                System.out.println("Ignoring update for mismatched passphrase.");
                return;
            }

            JSONArray membersArray = json.optJSONArray("members");
            if (membersArray == null) {
                System.err.println("No members array in the party update payload.");
                return;
            }

            String leader = json.optString("leader", null);

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

            SwingUtilities.invokeLater(() -> {
                plugin.getPartyManager().updateCurrentParty(passphrase, updatedMembers);
                plugin.getPartyManager().setLeader(leader); // Update leader
                plugin.getPanel().updatePartyMembers();
            });
        } catch (JSONException e) {
            System.err.println("Error processing party update: " + e.getMessage());
            e.printStackTrace();
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
