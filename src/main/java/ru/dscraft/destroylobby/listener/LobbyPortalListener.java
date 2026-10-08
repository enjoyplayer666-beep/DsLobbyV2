package ru.dscraft.destroylobby.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Свой портал из лобби в игровой мир: зашёл в область - сразу перенос, без надписей, звуков и частиц.
 * Портал лобби: /destroylobby portal pos1|pos2|target|on. Другие порталы (сколько угодно, в любых мирах):
 * /destroylobby portal <имя> pos1|pos2|target|on|off|remove, список - /destroylobby portal list.
 */
public class LobbyPortalListener implements Listener {

    private final DestroyLobbyPlugin plugin;
    private final Map<UUID, Long> last = new HashMap<>();

    public LobbyPortalListener(DestroyLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockY() == e.getFrom().getBlockY()
                && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        // старый портал лобби (lobby-portal) и именованные порталы (portals.<имя>)
        ConfigurationSection hit = inside(cfg().getConfigurationSection("lobby-portal"), to);
        ConfigurationSection all = cfg().getConfigurationSection("portals");
        if (hit == null && all != null) {
            for (String name : all.getKeys(false)) {
                hit = inside(all.getConfigurationSection(name), to);
                if (hit != null) break;
            }
        }
        if (hit == null) return;
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long prev = last.get(p.getUniqueId());
        if (prev != null && now - prev < 2000) return;
        last.put(p.getUniqueId(), now);
        Location target = target(hit);
        if (target == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> p.teleport(target));
    }

    /** Портал включён и точка внутри его области (углы включительно). */
    private static ConfigurationSection inside(ConfigurationSection c, Location to) {
        if (c == null || !c.getBoolean("enabled", false)) return null;
        if (!to.getWorld().getName().equalsIgnoreCase(c.getString("world", ""))) return null;
        int x1 = c.getInt("pos1.x"), y1 = c.getInt("pos1.y"), z1 = c.getInt("pos1.z");
        int x2 = c.getInt("pos2.x"), y2 = c.getInt("pos2.y"), z2 = c.getInt("pos2.z");
        int x = to.getBlockX(), y = to.getBlockY(), z = to.getBlockZ();
        if (x < Math.min(x1, x2) || x > Math.max(x1, x2) || y < Math.min(y1, y2) || y > Math.max(y1, y2)
                || z < Math.min(z1, z2) || z > Math.max(z1, z2)) return null;
        return c;
    }

    /** Точка назначения: сохранённая командой, иначе спавн мира target.world. */
    private static Location target(ConfigurationSection c) {
        String wn = c.getString("target.world", "");
        World w = wn.isEmpty() ? null : Bukkit.getWorld(wn);
        if (w == null) return null;
        if (!c.contains("target.x")) return w.getSpawnLocation().add(0.5, 0, 0.5);
        return new Location(w, c.getDouble("target.x"), c.getDouble("target.y"), c.getDouble("target.z"),
                (float) c.getDouble("target.yaw"), (float) c.getDouble("target.pitch"));
    }
}
