package com.osrs_splits.PartyManager;

import com.Utils.PlayerVerificationStatus;
import com.osrs_splits.OsrsSplitPlugin;
import com.osrs_splits.OsrsSplitsConfig;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PartyManager
{
    @Getter
    private String passphrase;
    @Getter
    private String leader;

    private final Map<String, PlayerVerificationStatus> verificationCache = new HashMap<>();
    @Getter
    private final Map<String, PlayerInfo> members = new HashMap<>();

    private final OsrsSplitPlugin plugin;
    private final OsrsSplitsConfig config;

    @Getter @Setter
    private String currentPartyPassphrase;

    public PartyManager(OsrsSplitsConfig config, OsrsSplitPlugin plugin)
    {
        this.config = config;
        this.plugin = plugin;
    }

    public boolean createParty(String leaderName, String passphrase)
    {
        if (this.leader != null)
        {
            System.out.println("A local party is already set. Clearing members for new party...");
            clearMembers();
        }

        if (leaderName == null || leaderName.isEmpty() || passphrase == null || passphrase.isEmpty())
        {
            System.out.println("Cannot create a local party without valid leader or passphrase.");
            return false;
        }

        int world = plugin.getClient().getWorld();

        // default rank/verified => rely on server to fill correct values
        PlayerInfo pLeader = new PlayerInfo(
                leaderName,
                world,
                -1,
                false,
                false
        );

        this.leader = leaderName;
        this.passphrase = passphrase;
        this.currentPartyPassphrase = passphrase;

        members.clear();
        members.put(leaderName, pLeader);

        System.out.println("Party created locally with leader=" + leaderName + " passphrase=" + passphrase);
        return true;
    }

    public void updateCurrentParty(String passphrase, Map<String, PlayerInfo> newMembers)
    {
        if (passphrase == null || newMembers == null)
        {
            System.err.println("Invalid update: passphrase or members is null. Skipping.");
            return;
        }
        this.currentPartyPassphrase = passphrase;
        this.members.clear();
        this.members.putAll(newMembers);

        System.out.println("Updated local party: " + passphrase + " with " + newMembers.size() + " members.");
    }

    public void leaveParty(String playerName)
    {
        if (!members.containsKey(playerName))
        {
            System.out.println(playerName + " is not in the party. Nothing to remove locally.");
            return;
        }
        members.remove(playerName);
        System.out.println(playerName + " has left the local party.");

        if (members.isEmpty())
        {
            disbandParty();
        }
        else
        {
            // inform server
            plugin.getSocketIoClient().sendPartyUpdate(passphrase, members);
        }
    }

    private void disbandParty()
    {
        System.out.println("Local party with passphrase " + passphrase + " is disbanded.");
        if (passphrase != null)
        {
            // notify server
            plugin.getSocketIoClient().sendDisbandParty(passphrase);
        }
        this.passphrase = null;
        this.leader = null;
        members.clear();
    }

    public void updatePlayerData(String playerName, int newWorld)
    {
        PlayerInfo p = members.get(playerName);
        if (p != null)
        {
            // remove local verification logic
            p.setWorld(newWorld);
            synchronizePartyWithRedis();
        }
    }

    public boolean isInParty(String playerName)
    {
        return members.containsKey(playerName);
    }

    public void toggleSplitConfirmation(String playerName)
    {
        PlayerInfo p = members.get(playerName);
        if (p != null)
        {
            p.setConfirmedSplit(!p.isConfirmedSplit());
            System.out.println(playerName + " toggled confirmSplit to " + p.isConfirmedSplit());
            synchronizePartyWithRedis();
        }
    }

    public boolean allPlayersConfirmedAndSameWorld()
    {
        if (members.isEmpty()) return false;
        int firstWorld = members.values().iterator().next().getWorld();
        for (PlayerInfo p : members.values())
        {
            if (!p.isConfirmedSplit() || p.getWorld() != firstWorld) return false;
        }
        return true;
    }

    public void clearMembers()
    {
        members.clear();
        System.out.println("All members have been cleared from the party.");
    }

    public void addMember(PlayerInfo p)
    {
        if (p == null || p.getName() == null)
        {
            System.err.println("Warning: Attempted to add null or invalid member.");
            return;
        }
        members.put(p.getName(), p);
        System.out.println("Added member: " + p.getName());
    }

    public void setMembers(Map<String, PlayerInfo> updated)
    {
        if (updated == null)
        {
            System.out.println("No members to set.");
            return;
        }
        members.clear();
        members.putAll(updated);
        System.out.println("Local members replaced. New size: " + updated.size());
    }

    public void synchronizePartyWithRedis()
    {
        if (passphrase == null || passphrase.isEmpty())
        {
            return;
        }

        // Build full party data
        JSONObject payload = new JSONObject();
        payload.put("passphrase", passphrase);
        payload.put("leader", leader);

        JSONArray arr = new JSONArray();
        for (PlayerInfo m : members.values())
        {
            JSONObject o = new JSONObject();
            o.put("name", m.getName());
            o.put("world", m.getWorld());
            o.put("rank", m.getRank());
            o.put("verified", m.isVerified());
            o.put("confirmedSplit", m.isConfirmedSplit());
            arr.put(o);
        }
        payload.put("members", arr);

        // optional: add apiKey if you want server re-verification
        payload.put("apiKey", plugin.getConfig().apiKey());

        plugin.getSocketIoClient().send("party_update", payload.toString());
    }


    // no local verification by default
    public PlayerVerificationStatus getCachedVerification(String playerName)
    {
        return verificationCache.get(playerName);
    }

    public void cacheVerification(String playerName, PlayerVerificationStatus status)
    {
        verificationCache.put(playerName, status);
    }

    public void setLeader(String leader)
    {
        if (leader == null || leader.isEmpty())
        {
            System.out.println("Leader cannot be null or empty.");
            this.leader = null;
        }
        else
        {
            this.leader = leader;
            System.out.println("Leader updated to: " + leader);
        }
    }
}
