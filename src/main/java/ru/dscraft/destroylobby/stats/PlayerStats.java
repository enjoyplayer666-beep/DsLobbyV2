package ru.dscraft.destroylobby.stats;

import java.util.UUID;

/** Коины / убийства / смерти одного игрока. */
public class PlayerStats {

    private final UUID uuid;
    private volatile long coins;
    private volatile int kills;
    private volatile int deaths;

    public PlayerStats(UUID uuid, long coins, int kills, int deaths) {
        this.uuid = uuid;
        this.coins = coins;
        this.kills = kills;
        this.deaths = deaths;
    }

    public UUID getUuid() {
        return uuid;
    }

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = Math.max(0, coins);
    }

    public void addCoins(long amount) {
        setCoins(this.coins + amount);
    }

    public int getKills() {
        return kills;
    }

    public void incrementKills() {
        kills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void incrementDeaths() {
        deaths++;
    }
}
