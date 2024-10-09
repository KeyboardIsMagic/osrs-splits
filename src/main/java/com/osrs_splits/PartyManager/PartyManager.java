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
    private final Set<String> confirmedMembers = new HashSet<>();

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

    // Method to create a new party with an initial leader name
    public boolean createParty(String leaderName)
    {
        if (this.leader != null) // Check if a party already exists
        {
            System.out.println("A party already exists. Please join the existing party or wait for it to disband.");
            return false;
        }

        this.leader = leaderName;
        PlayerInfo leaderInfo = new PlayerInfo(leaderName, 0, 0);
        members.put(leaderName, leaderInfo);
        System.out.println("Party created with leader: " + leaderName);
        return true;
    }

    // Method to update the party leader's name, used when the player logs in
    public void updatePartyLeader(String leaderName)
    {
        if (this.leader == null)
        {
            System.out.println("Updating party leader to: " + leaderName);
            this.leader = leaderName;
            members.put(leaderName, new PlayerInfo(leaderName, 0, 0));
        }
    }

    // Method for joining an existing party
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

    // Method for a player to leave the party
    public void leaveParty()
    {
        if (this.leader != null)
        {
            System.out.println(leader + " has left the party.");
            disbandParty();
        }
    }

    // Method to disband the party
    private void disbandParty()
    {
        this.leader = null;
        members.clear();
        confirmedMembers.clear();
        System.out.println("Party has been disbanded.");
    }

    // Getter for party members
    public Map<String, PlayerInfo> getMembers()
    {
        return members;
    }
}

