package com.osrs_splits.PartyManager;

import com.Utils.PlayerVerificationStatus;
import com.osrs_splits.OsrsSplitPlugin;
import com.osrs_splits.OsrsSplitsConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PartyManager
{
    @Getter
    private String leader;

    @Getter
    private final Map<String, PlayerInfo> members = new HashMap<>();
    private final OsrsSplitPlugin plugin;

    private final OsrsSplitsConfig config;

    // Constructor accepting OsrsSplitsConfig
    public PartyManager(OsrsSplitsConfig config, OsrsSplitPlugin plugin)
    {
        this.config = config;
        this.plugin = plugin;
    }

    public boolean createParty(String leaderName, int rank)
    {
        if (this.leader != null)
        {
            System.out.println("A party already exists.");
            return false;
        }

        if (leaderName == null || leaderName.isEmpty())
        {
            System.out.println("Cannot create a party without a valid leader.");
            return false;
        }

        this.leader = leaderName;
        members.put(leaderName, new PlayerInfo(leaderName, rank, plugin.getClient().getWorld()));
        System.out.println("Party created with leader: " + leaderName);
        return true;
    }


    public void updatePlayerData(String playerName, int world) {
        PlayerVerificationStatus status = plugin.getPlayerVerificationStatus(plugin.getConfig().apiKey());
        updatePlayerData(playerName, world, status);
    }

    public void updatePlayerData(String playerName, int world, PlayerVerificationStatus status) {
        PlayerInfo player = members.get(playerName);

        if (player != null) {
            player.setWorld(world);
            player.setVerified(status.isVerified());
            player.setRank(status.getRank());
            player.setConfirmedSplit(false); // Reset confirmation on world change
            System.out.println("Updated player data for: " + playerName);
        }
    }



    public void processWebSocketMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String action = json.getString("action");

            if ("party_update".equals(action)) {
                JSONArray members = json.getJSONArray("members");
                clearMembers();

                for (int i = 0; i < members.length(); i++) {
                    JSONObject member = members.getJSONObject(i);
                    PlayerInfo player = new PlayerInfo(
                            member.getString("name"),
                            member.getInt("world"),
                            member.getInt("rank")
                    );
                    player.setVerified(member.getBoolean("verified"));
                    player.setConfirmedSplit(member.getBoolean("confirmedSplit"));
                    addMember(player);
                }

                System.out.println("Party updated: " + members);
                plugin.getOsrsSplitPluginPanel().updatePartyMembers();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }





    public boolean joinParty(String playerName, int world, int rank) // Rank added
    {


        if (this.leader == null)
        {
            System.out.println("No active party to join.");
            return false;
        }


        if (members.containsKey(playerName))
        {
            System.out.println(playerName + " is already in the party.");
            return false;
        }

        PlayerInfo player = new PlayerInfo(playerName, world, rank);
        members.put(playerName, player);
        System.out.println(playerName + " (Rank " + rank + ") has joined the party.");
        return true;
    }

    public void leaveParty(String playerName)
    {
        if (members.containsKey(playerName))
        {
            members.remove(playerName);
            System.out.println(playerName + " has left the party.");

            // If the leader is leaving, disband the party
            if (playerName.equals(leader))
            {
                disbandParty();
            }
        }
    }

    private void disbandParty()
    {
        this.leader = null;
        members.clear();
        System.out.println("Party has been disbanded.");
    }

    public boolean isLeader(String playerName) {
        return leader != null && leader.equals(playerName);
    }

    public boolean isInParty(String playerName)
    {
        return members.containsKey(playerName);
    }


    public void clearMembers() {
        members.clear();
        System.out.println("All members have been cleared from the party.");
    }


    public void addMember(PlayerInfo playerInfo) {
        if (!members.containsKey(playerInfo.getName())) {
            members.put(playerInfo.getName(), playerInfo);
            System.out.println("Member added: " + playerInfo.getName());
        } else {
            System.out.println("Member already exists: " + playerInfo.getName());
        }
    }


}
