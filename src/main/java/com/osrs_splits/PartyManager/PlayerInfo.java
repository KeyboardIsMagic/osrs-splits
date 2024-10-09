package com.osrs_splits.PartyManager;

import lombok.Getter;
import lombok.Setter;

public class PlayerInfo
{
    // Getters and Setters
    @Getter
    private String name;
    @Getter
    private int combatLevel;
    @Setter
    @Getter
    private int world;
    private boolean isConfirmed;
    @Setter
    @Getter
    private boolean isLeader;

    // Constructor
    public PlayerInfo(String name, int combatLevel, int world)
    {
        this.name = name;
        this.combatLevel = combatLevel;
        this.world = world;
        this.isConfirmed = false; // Default confirmation status
    }

    public boolean isConfirmed()
    {
        return isConfirmed;
    }

    public void setConfirmed(boolean confirmed)
    {
        isConfirmed = confirmed;
    }

}
