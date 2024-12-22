package com.osrs_splits.PartyManager;

import com.Utils.HttpUtil;
import com.Utils.PlayerVerificationStatus;
import com.osrs_splits.OsrsSplitPlugin;
import com.osrs_splits.OsrsSplitsConfig;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class PartyManager {
    @Getter
    private String passphrase; // Passphrase of the current party
    @Getter
    private String leader;
    private final Map<String, PlayerVerificationStatus> verificationCache = new HashMap<>();

    @Getter
    private final Map<String, PlayerInfo> members = new HashMap<>();

    private final OsrsSplitPlugin plugin;
    private final OsrsSplitsConfig config;
    @Getter
    @Setter
    private String currentPartyPassphrase;

    // Constructor accepting OsrsSplitsConfig
    public PartyManager(OsrsSplitsConfig config, OsrsSplitPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }


    public boolean createParty(String leaderName, String passphrase) {
        if (this.leader != null) {
            System.out.println("A party already exists.");
            return false;
        }

        if (leaderName == null || leaderName.isEmpty() || passphrase == null || passphrase.isEmpty()) {
            System.out.println("Cannot create a party without a valid leader or passphrase.");
            return false;
        }

        int world = plugin.getClient().getWorld(); // Get the current world

        // Query the database for rank and verification status
        PlayerVerificationStatus status = plugin.getPlayerVerificationStatus(plugin.getConfig().apiKey());
        int rank = status.getRank();
        boolean verified = status.isVerified();

        this.leader = leaderName;
        this.passphrase = passphrase;
        members.put(leaderName, new PlayerInfo(leaderName, world, rank, verified, false)); // Updated constructor
        System.out.println("Party created with leader: " + leaderName + " in world " + world + " with rank " + rank + " and verified: " + verified);
        return true;
    }



    public void updateCurrentParty(String passphrase, Map<String, PlayerInfo> members) {
        this.currentPartyPassphrase = passphrase;
        this.members.clear();
        this.members.putAll(members);
        System.out.println("Updated current party: " + passphrase + " with members: " + members.size());
    }

    public void leaveParty(String playerName) {
        if (!members.containsKey(playerName)) {
            System.out.println(playerName + " is not in the party.");
            return;
        }

        members.remove(playerName);
        System.out.println(playerName + " has left the party.");

        // If the last member leaves, disband the party
        if (members.isEmpty()) {
            disbandParty();
        }
    }


    private void disbandParty() {
        System.out.println("Party with passphrase " + passphrase + " has been disbanded.");
        this.passphrase = null;
        members.clear();
    }


    public void updatePlayerData(String playerName, int world) {
        PlayerInfo player = members.get(playerName);
        if (player != null) {
            // Query database for updated verification and rank
            PlayerVerificationStatus status = plugin.getPlayerVerificationStatus(plugin.getConfig().apiKey());
            player.setWorld(world);
            player.setVerified(status.isVerified());
            player.setRank(status.getRank());

            // Update the API with the new player data
            JSONObject payload = new JSONObject();
            payload.put("passphrase", currentPartyPassphrase);
            payload.put("leader", leader);

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
            plugin.getWebSocketClient().send("party_update", payload.toString());
        }
    }




    public boolean isInParty(String playerName) {
        return members.containsKey(playerName);
    }


    public void toggleSplitConfirmation(String playerName) {
        PlayerInfo player = members.get(playerName);
        if (player != null) {
            boolean currentStatus = player.isConfirmedSplit();
            player.setConfirmedSplit(!currentStatus); // Toggle the status
            System.out.println(playerName + "'s split confirmation toggled to: " + !currentStatus);
        } else {
            System.out.println("Player not found in party: " + playerName);
        }
    }


    public boolean allPlayersConfirmedAndSameWorld() {
        if (members.isEmpty()) {
            return false;
        }

        int leaderWorld = members.values().iterator().next().getWorld(); // Get leader's world
        for (PlayerInfo player : members.values()) {
            if (!player.isConfirmedSplit() || player.getWorld() != leaderWorld) {
                return false;
            }
        }
        return true;
    }


    public void clearMembers() {
        members.clear();
        System.out.println("All members have been cleared from the party.");
    }


    public void addMember(PlayerInfo playerInfo) {
        if (playerInfo == null || playerInfo.getName() == null) {
            System.out.println("Warning: Attempted to add null or invalid member.");
            return;
        }
        if (!members.containsKey(playerInfo.getName())) {
            members.put(playerInfo.getName(), playerInfo);
            System.out.println("Member added: " + playerInfo.getName());
        } else {
            System.out.println("Member already exists: " + playerInfo.getName());
        }
    }


    public void setMembers(Map<String, PlayerInfo> updatedMembers) {
        if (updatedMembers == null) {
            System.out.println("No members to update.");
            return;
        }

        members.clear();
        members.putAll(updatedMembers);
        System.out.println("Party members updated for passphrase: " + currentPartyPassphrase + ". New member count: " + members.size());
    }

    public Map<String, PlayerInfo> getMembers() {
        if (members.isEmpty()) {
            System.out.println("Warning: Members map is empty.");
        }
        return members;
    }


    public void fetchPartyState(String passphrase) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                String apiUrl = "http://127.0.0.1:5000/party-status/" + passphrase;
                String response = HttpUtil.getRequest(apiUrl); // Implement `HttpUtil.getRequest` for GET requests
                JSONObject partyState = new JSONObject(response);
                updatePartyFromApi(partyState);
                return null;
            }

            @Override
            protected void done() {
                try {
                    System.out.println("Party state updated successfully.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }


    public void updatePartyFromApi(JSONObject partyState) {
        String passphrase = partyState.getString("passphrase");
        if (!passphrase.equals(currentPartyPassphrase)) {
            System.out.println("Ignored update for mismatched passphrase.");
            return; // Ignore updates for other passphrases
        }

        JSONArray membersArray = partyState.getJSONArray("members");
        Map<String, PlayerInfo> updatedMembers = new HashMap<>();

        for (int i = 0; i < membersArray.length(); i++) {
            JSONObject memberData = membersArray.getJSONObject(i);
            String name = memberData.optString("name", null);
            if (name == null) {
                System.out.println("Skipped member with null name.");
                continue;
            }

            int world = memberData.optInt("world", -1);
            boolean verified = memberData.optBoolean("verified", false);
            int rank = memberData.optInt("rank", 0);
            boolean confirmedSplit = memberData.optBoolean("confirmedSplit", false);

            PlayerInfo playerInfo = new PlayerInfo(name, world, rank, verified, confirmedSplit);
            updatedMembers.put(name, playerInfo);
        }

        this.members.clear();
        this.members.putAll(updatedMembers);
        System.out.println("Party updated for passphrase: " + currentPartyPassphrase);
    }

    public PlayerVerificationStatus getCachedVerification(String playerName) {
        return verificationCache.get(playerName);
    }

    public void cacheVerification(String playerName, PlayerVerificationStatus status) {
        verificationCache.put(playerName, status);
    }

}
