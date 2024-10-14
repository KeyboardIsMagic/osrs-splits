package com.osrs_splits.PartyManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PartyManager
{
    private static PartyManager instance;

    private String leader;
    private final int maxPartySize = 5;
    private final Map<String, PlayerInfo> members = new HashMap<>();

    // Singleton pattern for PartyManager instance
    public static PartyManager getInstance()
    {
        if (instance == null)
        {
            instance = new PartyManager();
        }
        return instance;
    }

    private PartyManager() {}

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
        PlayerInfo leaderInfo = new PlayerInfo(leaderName, 0, 0); // Set initial values
        members.put(leaderName, leaderInfo);  // Add player info with placeholders
        System.out.println("Party created with leader: " + leaderName);
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

    public boolean isLeader(String playerName)
    {
        return playerName.equals(leader);
    }

    public Map<String, PlayerInfo> getMembers()
    {
        return members;
    }
}


