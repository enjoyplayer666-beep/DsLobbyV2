package ru.dscraft.destroylobby.stats;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит коины/убийства/смерти игроков. Простое файловое хранилище (yml),
 * этого достаточно для отображения на скорборде; при необходимости легко
 * заменить на SQL/Vault, интерфейс наружу (get/addCoins и т.д.) не изменится.
 */
public class StatsManager implements Listener {

    private final DestroyLobbyPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();

    public StatsManager(DestroyLobbyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.file", "playerdata.yml"));
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
        }
    }

    public PlayerStats get(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), uuid -> load(uuid));
    }

    public void unload(Player player) {
        PlayerStats stats = cache.remove(player.getUniqueId());
        if (stats != null) {
            save(stats);
        }
    }

    private PlayerStats load(UUID uuid) {
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = uuid.toString();
        long coins = yaml.getLong(path + ".coins", 0);
        int kills = yaml.getInt(path + ".kills", 0);
        int deaths = yaml.getInt(path + ".deaths", 0);
        return new PlayerStats(uuid, coins, kills, deaths);
    }

    private void save(PlayerStats stats) {
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = stats.getUuid().toString();
        yaml.set(path + ".coins", stats.getCoins());
        yaml.set(path + ".kills", stats.getKills());
        yaml.set(path + ".deaths", stats.getDeaths());
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить статистику игрока " + stats.getUuid() + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (PlayerStats stats : cache.values()) {
            save(stats);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        get(victim).incrementDeaths();

        Player killer = victim.getKiller();
        if (killer != null) {
            get(killer).incrementKills();
        }
    }
}
