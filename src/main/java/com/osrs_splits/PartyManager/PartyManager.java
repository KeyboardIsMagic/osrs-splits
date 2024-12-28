package com.osrs_splits.PartyManager;

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

        int world = plugin.getClient().getWorld();
        PlayerVerificationStatus status = plugin.getPlayerVerificationStatus(plugin.getConfig().apiKey());

        this.leader = leaderName;
        this.passphrase = passphrase;

        members.put(leaderName, new PlayerInfo(leaderName, world, status.getRank(), status.isVerified(), false));
        plugin.getWebSocketClient().sendPartyUpdate(passphrase, members); // Use the new method
        System.out.println("Party created successfully.");

        return true;
    }



    public void updateCurrentParty(String passphrase, Map<String, PlayerInfo> members) {
        if (passphrase == null || members == null) {
            System.err.println("Invalid update: passphrase or members map is null.");
            return;
        }

        this.currentPartyPassphrase = passphrase;
        this.members.clear(); // Clear old data
        this.members.putAll(members); // Add new data

        System.out.println("Updated party: " + passphrase + " with members: " + members.size());
    }



    public void leaveParty(String playerName) {
        if (!members.containsKey(playerName)) {
            System.out.println(playerName + " is not in the party.");
            return;
        }

        members.remove(playerName);
        System.out.println(playerName + " has left the party.");

        if (members.isEmpty()) {
            disbandParty();
        } else {
            plugin.getWebSocketClient().sendPartyUpdate(passphrase, members); // Use the new method
        }
    }


    private void disbandParty() {
        System.out.println("Party with passphrase " + passphrase + " has been disbanded.");
        if (passphrase != null) {
            plugin.getWebSocketClient().sendDisbandParty(passphrase); // Notify server to update Redis
        }
        this.passphrase = null;
        members.clear();
    }



    public void updatePlayerData(String playerName, int world) {
        PlayerInfo player = members.get(playerName);
        if (player != null) {
            PlayerVerificationStatus cachedStatus = getCachedVerification(playerName);
            if (cachedStatus == null || cachedStatus.getRank() == -1) {
                PlayerVerificationStatus status = plugin.getPlayerVerificationStatus(plugin.getConfig().apiKey());
                cacheVerification(playerName, status);
                player.setVerified(status.isVerified());
                player.setRank(status.getRank());
            }
            player.setWorld(world);
            synchronizePartyWithRedis(); // Ensure Redis reflects the updated data
        }
    }






    public boolean isInParty(String playerName) {
        return members.containsKey(playerName);
    }


    public void toggleSplitConfirmation(String playerName) {
        PlayerInfo player = members.get(playerName);
        if (player != null) {
            boolean currentStatus = player.isConfirmedSplit();
            player.setConfirmedSplit(!currentStatus);

            System.out.println(playerName + "'s split confirmation toggled to: " + !currentStatus);
            synchronizePartyWithRedis(); // Notify server of changes
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
        if (playerInfo == null || playerInfo.getName() == null || playerInfo.getRank() < 0) {
            System.err.println("Warning: Attempted to add null or invalid member.");
            return;
        }
        members.put(playerInfo.getName(), playerInfo);
        System.out.println("Added member: " + playerInfo.getName());
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



    public void synchronizePartyWithRedis() {
        JSONObject payload = new JSONObject();
        payload.put("passphrase", currentPartyPassphrase);
        payload.put("leader", leader);

        JSONArray membersArray = new JSONArray();
        for (PlayerInfo member : members.values()) {
            JSONObject memberData = new JSONObject();
            memberData.put("name", member.getName());
            memberData.put("world", member.getWorld());
            memberData.put("rank", member.getRank());
            memberData.put("verified", member.isVerified());
            memberData.put("confirmedSplit", member.isConfirmedSplit());
            membersArray.put(memberData);
        }

        payload.put("members", membersArray);
        plugin.getWebSocketClient().send("party_update", payload.toString());
    }


//    public void updatePartyFromApi(JSONObject partyState) {
//        String passphrase = partyState.getString("passphrase");
//        if (!passphrase.equals(currentPartyPassphrase)) {
//            System.out.println("Ignored update for mismatched passphrase.");
//            return; // Ignore updates for other passphrases
//        }
//
//        JSONArray membersArray = partyState.getJSONArray("members");
//        Map<String, PlayerInfo> updatedMembers = new HashMap<>();
//
//        for (int i = 0; i < membersArray.length(); i++) {
//            JSONObject memberData = membersArray.getJSONObject(i);
//            String name = memberData.optString("name", null);
//            if (name == null) {
//                System.out.println("Skipped member with null name.");
//                continue;
//            }
//
//            int world = memberData.optInt("world", -1);
//            boolean verified = memberData.optBoolean("verified", false);
//            int rank = memberData.optInt("rank", 0);
//            boolean confirmedSplit = memberData.optBoolean("confirmedSplit", false);
//
//            PlayerInfo playerInfo = new PlayerInfo(name, world, rank, verified, confirmedSplit);
//            updatedMembers.put(name, playerInfo);
//        }
//
//        this.members.clear();
//        this.members.putAll(updatedMembers);
//        System.out.println("Party updated for passphrase: " + currentPartyPassphrase);
//    }

    public PlayerVerificationStatus getCachedVerification(String playerName) {
        return verificationCache.get(playerName);
    }

    public void cacheVerification(String playerName, PlayerVerificationStatus status) {
        verificationCache.put(playerName, status);
    }


    public void setLeader(String leader) {
        if (leader == null || leader.isEmpty()) {
            System.out.println("Leader cannot be null or empty.");
            this.leader = null;
        } else {
            this.leader = leader;
            System.out.println("Leader updated to: " + leader);
        }
    }




}
