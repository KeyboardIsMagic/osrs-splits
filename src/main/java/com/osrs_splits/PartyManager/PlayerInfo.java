package com.osrs_splits.PartyManager;

import lombok.Getter;
import lombok.Setter;

@Getter
public class PlayerInfo
{
    private String name;
    @Setter
    private int combatLevel;
    @Setter
    private int world;
    @Setter
    private boolean confirmedSplit;

    public PlayerInfo(String name, int combatLevel, int world)
    {
        this.name = name;
        this.combatLevel = combatLevel;
        this.world = world;
        this.confirmedSplit = false;
    }


}