package com.Utils;

import com.osrs_splits.OsrsSplitPlugin;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONObject;


import java.net.URISyntaxException;

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

    public void sendCreateParty(String passphrase, String rsn) {
        JSONObject payload = new JSONObject();
        payload.put("passphrase", passphrase);
        payload.put("rsn", rsn);

        // Emit 'create-party' as a Socket.IO event
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
        System.out.println("Processing Party Update: " + data);
        // You can parse the JSON and call relevant plugin methods here
    }

    public static void main(String[] args) {
        OsrsSplitPlugin plugin = new OsrsSplitPlugin(); // Replace with your actual plugin instance
        PartySocketIOClient client = new PartySocketIOClient("http://127.0.0.1:5000", plugin);

        // Example usage
        client.sendCreateParty("pop", "opk");
        client.sendJoinParty("pop", "opk");
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
        }
    }

    public void send(String event, String payload) {
        socket.emit(event, payload);
        System.out.println("Sent event [" + event + "] with payload: " + payload);
    }


}
