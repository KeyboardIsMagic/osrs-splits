package com.osrs_splits.PartyManager;

import lombok.Getter;
import lombok.Setter;

@Getter
public class PlayerInfo {
    private final String name;
    @Setter
    private int world;
    @Setter
    private int rank;
    @Setter
    private boolean verified;
    @Setter
    private boolean confirmedSplit;

    public PlayerInfo(String name, int world, int rank, boolean verified, boolean confirmedSplit) {
        this.name = name;
        this.world = world;
        this.rank = rank;
        this.verified = verified;
        this.confirmedSplit = confirmedSplit;
    }
}


