package ru.dscraft.destroylobby.api;

import org.bukkit.entity.Player;
import ru.dscraft.destroylobby.stats.StatsManager;

/**
 * Для других плагинов через рефлексию, без зависимости при сборке:
 * MediaTab берёт отсюда коины, убийства и смерти для скорборда.
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

    public static int kills(Player player) {
        StatsManager s = stats;
        return s == null ? 0 : s.get(player).getKills();
    }

    public static int deaths(Player player) {
        StatsManager s = stats;
        return s == null ? 0 : s.get(player).getDeaths();
    }
}
