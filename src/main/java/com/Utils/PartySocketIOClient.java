package com.Utils;

import com.osrs_splits.OsrsSplitPlugin;
import com.osrs_splits.PartyManager.PlayerInfo;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONArray;
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



    public void sendJoinParty(String passphrase, String rsn) {
        // Emit 'join-party' event with JSON payload
        JSONObject payload = new JSONObject();
        payload.put("passphrase", passphrase);
        payload.put("rsn", rsn);

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
            String action = json.getString("action");

            if ("party_disband".equals(action)) {
                System.out.println("Party disbanded: " + json.getString("passphrase"));
                SwingUtilities.invokeLater(() -> {
                    plugin.getPartyManager().clearMembers();
                    plugin.getPanel().updatePartyMembers();
                    JOptionPane.showMessageDialog(null, "Party has been disbanded.", "Party Disbanded", JOptionPane.INFORMATION_MESSAGE);
                });
                return; // No further processing needed for disband action
            }

            if (!"party_update".equals(action)) {
                System.out.println("Unknown action: " + action);
                return;
            }

            // Process party_update action
            String passphrase = json.getString("passphrase");
            if (!passphrase.equals(plugin.getPartyManager().getCurrentPartyPassphrase())) {
                System.out.println("Ignoring update for mismatched passphrase.");
                return;
            }

            JSONArray membersArray = json.getJSONArray("members");
            Map<String, PlayerInfo> updatedMembers = new HashMap<>();
            for (int i = 0; i < membersArray.length(); i++) {
                JSONObject memberJson = membersArray.getJSONObject(i);
                PlayerInfo playerInfo = new PlayerInfo(
                        memberJson.getString("name"),
                        memberJson.getInt("world"),
                        memberJson.getInt("rank"),
                        memberJson.getBoolean("verified"),
                        memberJson.getBoolean("confirmedSplit")
                );
                updatedMembers.put(playerInfo.getName(), playerInfo);
            }

            SwingUtilities.invokeLater(() -> {
                plugin.getPartyManager().setMembers(updatedMembers);
                plugin.getPanel().updatePartyMembers();
            });
        } catch (Exception e) {
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
                        plugin.getClient().getLocalPlayer().getName()
                );
            }
        }
    }


    public void send(String event, String payload) {
        socket.emit(event, payload);
        System.out.println("Sent event [" + event + "] with payload: " + payload);
    }


}
