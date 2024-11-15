package com.osrs_splits.PartyManager;

import com.osrs_splits.OsrsSplitsConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class PartyManager
{
    @Getter
    private String leader;
    private int dynamicPartySizeLimit;
    @Getter
    private final Map<String, PlayerInfo> members = new HashMap<>();

    private final OsrsSplitsConfig config;

    // Constructor accepting OsrsSplitsConfig
    public PartyManager(OsrsSplitsConfig config)
    {
        this.config = config;
        this.dynamicPartySizeLimit = config.partySizeLimit();
    }

    public boolean createParty(String leaderName)
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
        this.dynamicPartySizeLimit = config.partySizeLimit();
        PlayerInfo leaderInfo = new PlayerInfo(leaderName, 0, 0);
        members.put(leaderName, leaderInfo);
        System.out.println("Party created with leader: " + leaderName);
        return true;
    }


    public boolean updatePartySizeLimit(int newLimit)
    {
        if(newLimit < members.size())
        {
            System.out.println("Party size must be greater than or equal to current party members");
            return false;
        }

        this.dynamicPartySizeLimit = newLimit;
        System.out.println("Party size limit updated to: " + newLimit);
        return true;
    }

    public void updatePlayerData(String playerName, int combatLevel, int world)
    {
        PlayerInfo player = members.get(playerName);
        if (player != null)
        {
            player.setCombatLevel(combatLevel);
            player.setWorld(world);
            System.out.println("Updated player data for: " + playerName);
        }
    }

    public boolean joinParty(String playerName, int combatLevel, int world)
    {
        int maxPartySize = dynamicPartySizeLimit;

        if (this.leader == null)
        {
            System.out.println("No active party to join.");
            return false;
        }

        if (members.size() >= maxPartySize)
        {
            System.out.println("Party is full.");
            return false;
        }

        if (members.containsKey(playerName))
        {
            System.out.println(playerName + " is already in the party.");
            return false;
        }

        PlayerInfo player = new PlayerInfo(playerName, combatLevel, world);
        members.put(playerName, player);
        System.out.println(playerName + " has joined the party.");
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


}
