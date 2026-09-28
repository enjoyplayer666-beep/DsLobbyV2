package ru.dscraft.destroylobby.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.config.ConfigManager;

/**
 * Единственное место, где решается "куда ставить игрока при заходе/респавне".
 * <p>
 * Работает в паре с Multiverse-Core и Advanced-Portals:
 *  - Multiverse отвечает за миры и их загрузку; точка лобби берётся из нашего конфига
 *    (/destroylobby setspawn), а если её нет - из спавна мира лобби (его же задаёт /mvsetspawn);
 *  - Advanced-Portals сам телепортирует игроков между мирами, плагин лишь реагирует
 *    на PlayerChangedWorldEvent (см. PlayerConnectionListener), поэтому своего портала больше нет.
 * <p>
 * Почему раньше спавнило "не туда": телепорт делался через 1 тик после входа, а Multiverse
 * (и последний мир из playerdata) ставил игрока раньше/позже нас. Теперь игрок переносится сразу
 * при входе, плюс страховочная проверка через несколько тиков.
 */
public class SpawnListener implements Listener {

    private final DestroyLobbyPlugin plugin;
    private final ConfigManager configManager;

    public SpawnListener(DestroyLobbyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    private boolean active() {
        return configManager.lobbyProtectionEnabled() && configManager.forceSpawnOnJoin();
    }

    private boolean bypasses(Player player) {
        if (player.hasPermission(LobbyProtectionListener.BYPASS_PERMISSION)) return true;
        return configManager.lobbyOpsBypass() && player.isOp();
    }

    /** При КАЖДОМ заходе ставим игрока на точку лобби (команду проекта тоже, если не join-bypass; OP - нет) (даже если он вышел в самом лобби), + страховка. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        if (!active()) {
            plugin.getLogger().info("[spawn] " + name + ": пропуск, lobby-protection/force-spawn-on-join выключены в конфиге");
            return;
        }
        // операторов не трогаем (ops-bypass: true), как и раньше
        if (configManager.lobbyOpsBypass() && player.isOp()) {
            plugin.getLogger().info("[spawn] " + name + ": пропуск, игрок оператор (ops-bypass: true)");
            return;
        }
        // право обхода защиты лобби (у куратора с "*" оно тоже есть) при заходе больше не оставляет
        // игрока в игровом мире - только если включено lobby-protection.join-bypass
        if (configManager.joinBypass() && player.hasPermission(LobbyProtectionListener.BYPASS_PERMISSION)) {
            plugin.getLogger().info("[spawn] " + name + ": пропуск, право обхода и join-bypass: true");
            return;
        }

        Location spawn = configManager.getLobbySpawnLocation();
        if (spawn == null) {
            plugin.getLogger().warning("[spawn] " + name + ": точка лобби не найдена (мир из lobby-protection.spawn.world не загружен?)");
            return;
        }

        player.teleport(spawn);
        plugin.getLogger().info(String.format("[spawn] %s: перенос в лобби (%s %.1f %.1f %.1f)",
                name, spawn.getWorld().getName(), spawn.getX(), spawn.getY(), spawn.getZ()));

        // повторная проверка через 3 тика, если Multiverse/Essentials успели переставить игрока
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Location cur = player.getLocation();
            boolean wrongWorld = !cur.getWorld().equals(spawn.getWorld());
            boolean far = !wrongWorld && cur.distanceSquared(spawn) > 4.0;
            if (wrongWorld || far) {
                plugin.getLogger().info("[spawn] " + name + ": игрока переставили другим плагином, переношу повторно");
                player.teleport(spawn);
            }
        }, 3L);
    }

    /** Смерть в игровом мире -> возвращаемся в лобби (перекрывает кровать и respawn-настройки Multiverse). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!configManager.lobbyProtectionEnabled() || !configManager.respawnInLobby()) return;
        Player player = event.getPlayer();
        if (bypasses(player)) return;

        Location spawn = configManager.getLobbySpawnLocation();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }
}
