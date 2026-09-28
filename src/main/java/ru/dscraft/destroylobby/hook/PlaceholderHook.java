package ru.dscraft.destroylobby.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.config.ConfigManager;
import ru.dscraft.destroylobby.stats.PlayerStats;
import ru.dscraft.destroylobby.stats.StatsManager;

/**
 * Регистрирует %destroy_...% плейсхолдеры, чтобы этой статистикой/оформлением
 * можно было пользоваться в других плагинах (чат, holograms и т.д.):
 *  - coins / kills / deaths       - статистика игрока;
 *  - group                        - имя primary-группы из LuckPerms;
 *  - namecolor                    - легаси-цвет ника по группе (например "&f"), из config.yml;
 *  - nick                         - ник игрока, уже покрашенный в namecolor (готов к вставке в формат чата).
 * <p>
 * Персональный/групповой ПРЕФИКС и СУФФИКС здесь специально не дублируются -
 * для них уже есть стандартный %luckperms_prefix% / %luckperms_suffix% из самого LuckPerms,
 * и личный префикс, поставленный через /prefix, тоже приходит через них же.
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final DestroyLobbyPlugin plugin;
    private final StatsManager statsManager;
    private final LuckPermsHook luckPermsHook;
    private final ConfigManager configManager;

    public PlaceholderHook(DestroyLobbyPlugin plugin, StatsManager statsManager,
                            LuckPermsHook luckPermsHook, ConfigManager configManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.luckPermsHook = luckPermsHook;
        this.configManager = configManager;
    }

    @Override
    public String getIdentifier() {
        return "destroy";
    }

    @Override
    public String getAuthor() {
        return "DestroyCraft";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";

        switch (params.toLowerCase()) {
            case "coins", "kills", "deaths" -> {
                PlayerStats stats = statsManager.get(player);
                return switch (params.toLowerCase()) {
                    case "coins" -> String.valueOf(stats.getCoins());
                    case "kills" -> String.valueOf(stats.getKills());
                    default -> String.valueOf(stats.getDeaths());
                };
            }
            case "group" -> {
                return luckPermsHook.getPrimaryGroupName(player);
            }
            case "namecolor" -> {
                return configManager.getNickColorForGroup(luckPermsHook.getPrimaryGroupName(player));
            }
            case "nick" -> {
                String color = configManager.getNickColorForGroup(luckPermsHook.getPrimaryGroupName(player));
                return color + player.getName();
            }
            default -> {
                return null;
            }
        }
    }
}
