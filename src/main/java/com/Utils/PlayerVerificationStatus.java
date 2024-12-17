package com.Utils;

public class PlayerVerificationStatus {
    private final String rsn;
    private final boolean verified;
    private final int rank;

    public PlayerVerificationStatus(String rsn, boolean verified, int rank) {
        this.rsn = rsn;
        this.verified = verified;
        this.rank = rank;
    }

    public String getRsn() {
        return rsn;
    }

    public boolean isVerified() {
        return verified;
    }

    public int getRank() {
        return rank;
    }
}
