package ru.dscraft.destroylobby.visibility;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.config.ConfigManager;

/**
 * Разделяет лобби и игровые миры: игрок в SkyPvP не видит в табе тех, кто стоит в лобби,
 * и наоборот (на скрине-образце в табе лобби только ты сам).
 * <p>
 * Используется штатный {@link Player#hidePlayer(org.bukkit.plugin.Plugin, Player)} с ключом
 * нашего плагина - он убирает игрока из таба. Скрытие других плагинов (ваниш Essentials и т.п.)
 * при этом не ломается: {@code showPlayer} снимает только НАШЕ скрытие.
 * <p>
 * Режимы ({@code isolation.mode}):
 * <ul>
 *   <li>lobby-vs-game - лобби отдельно, все игровые миры (SkyPvP, его незер и т.д.) вместе;</li>
 *   <li>per-world - каждый мир сам по себе.</li>
 * </ul>
 * Право {@code destroylobby.seeall} - видеть всех (для модерации).
 */
public class VisibilityManager {

    private final DestroyLobbyPlugin plugin;
    private final ConfigManager configManager;

    public VisibilityManager(DestroyLobbyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /** Группа мира: игроки в одной группе видят друг друга. */
    public String bucketOf(World world) {
        String name = world.getName();
        if (configManager.isLobbyWorld(name)) return "lobby";
        if ("per-world".equalsIgnoreCase(configManager.isolationMode())) return "world:" + name.toLowerCase();
        return "game";
    }

    public boolean sameBucket(Player a, Player b) {
        return bucketOf(a.getWorld()).equals(bucketOf(b.getWorld()));
    }

    public boolean shouldSee(Player viewer, Player target) {
        if (!configManager.isolationEnabled()) return true;
        if (viewer.hasPermission(configManager.isolationSeeAllPermission())) return true;
        return sameBucket(viewer, target);
    }

    /** Пересчитать видимость между этим игроком и всеми остальными (в обе стороны). */
    public void update(Player player) {
        if (!player.isOnline()) return;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            apply(player, other);
            apply(other, player);
        }
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    /** При выключении плагина - вернуть всем видимость, которую скрывали мы. */
    public void showEveryone() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(target)) viewer.showPlayer(plugin, target);
            }
        }
    }

    private void apply(Player viewer, Player target) {
        if (shouldSee(viewer, target)) {
            viewer.showPlayer(plugin, target);
        } else {
            viewer.hidePlayer(plugin, target);
        }
    }
}
