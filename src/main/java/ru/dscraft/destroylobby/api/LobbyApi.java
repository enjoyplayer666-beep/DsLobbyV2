package ru.dscraft.destroylobby.api;

import org.bukkit.entity.Player;
import ru.dscraft.destroylobby.stats.StatsManager;

/**
 * Для других плагинов через рефлексию, без зависимости при сборке:
 * MediaTab берёт отсюда коины, убийства и смерти для скорборда,
 * MediaItems списывает коины за покупки у НПС.
 */
public final class LobbyApi {

    private static volatile StatsManager stats;

    private LobbyApi() {
    }

    public static void init(StatsManager manager) {
        stats = manager;
    }

    public static long coins(Player player) {
        StatsManager s = stats;
        return s == null ? 0 : s.get(player).getCoins();
    }

    /** Списать коины; false, если не хватает (тогда ничего не списывается). */
    public static boolean takeCoins(Player player, long amount) {
        StatsManager s = stats;
        if (s == null) return false;
        var data = s.get(player);
        if (data.getCoins() < amount) return false;
        data.addCoins(-amount);
        return true;
    }

    public static int kills(Player player) {
        StatsManager s = stats;
        return s == null ? 0 : s.get(player).getKills();
    }

    public static int deaths(Player player) {
        StatsManager s = stats;
        return s == null ? 0 : s.get(player).getDeaths();
    }
}
