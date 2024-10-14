package com.osrs_splits.PartyManager;

public class PlayerInfo
{
    private String name;
    private int combatLevel;
    private int world;

    public PlayerInfo(String name, int combatLevel, int world)
    {
        this.name = name;
        this.combatLevel = combatLevel;
        this.world = world;
    }

    public String getName()
    {
        return name;
    }

    public int getCombatLevel()
    {
        return combatLevel;
    }

    public int getWorld()
    {
        return world;
    }


    public void setCombatLevel(int combatLevel)
    {
        this.combatLevel = combatLevel;
    }

    public void setWorld(int world)
    {
        this.world = world;
    }
}

