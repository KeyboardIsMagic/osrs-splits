package com.Utils;

import com.osrs_splits.OsrsSplitPlugin;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

public class PartyWebSocketClient extends WebSocketClient
{

    private final OsrsSplitPlugin plugin;
    private boolean reconnecting = false; // Tracks if reconnect is ongoing
    private int reconnectAttempts = 0;    // Tracks the reconnect attempts

    public PartyWebSocketClient(String serverUri, OsrsSplitPlugin plugin) throws URISyntaxException
    {
        super(new URI(serverUri));
        this.plugin = plugin;
    }

    @Override
    public void onOpen(ServerHandshake handshake)
    {
        System.out.println("WebSocket connected to the server.");
        reconnectAttempts = 0; // Reset reconnect attempts
        reconnecting = false;  // Reset the reconnecting flag
    }

    @Override
    public void onMessage(String message) {
        System.out.println("WebSocket Message Received: " + message);
        try {
            if (!message.trim().isEmpty()) {
                plugin.getPartyManager().processWebSocketMessage(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error processing WebSocket message: " + e.getMessage());
        }
    }


    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("WebSocket closed: " + reason + " (Code: " + code + ")");
        if (!isOpen()) {
            reconnectWithDelay();
        }
    }


    @Override
    public void onError(Exception e)
    {
        System.err.println("WebSocket Error: " + e.getMessage());
        reconnect();
    }

    private void reconnectWithDelay() {
        final int maxAttempts = 5;
        final int baseDelaySeconds = 2;

        new Thread(() -> {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    System.out.println("Reconnecting WebSocket... Attempt: " + attempt);
                    TimeUnit.SECONDS.sleep(baseDelaySeconds * attempt);

                    if (!isOpen()) {
                        connectBlocking();
                        System.out.println("WebSocket reconnected successfully.");
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Reconnection attempt failed: " + e.getMessage());
                }
            }
            System.err.println("Max reconnection attempts reached. WebSocket connection failed.");
        }).start();
    }


}

