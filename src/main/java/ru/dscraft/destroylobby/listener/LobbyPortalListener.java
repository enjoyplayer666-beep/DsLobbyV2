package ru.dscraft.destroylobby.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
 * Область и точка назначения задаются командами /destroylobby portal pos1|pos2|target.
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
        if (!cfg().getBoolean("lobby-portal.enabled", false)) return;
        Location to = e.getTo();
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockY() == e.getFrom().getBlockY()
                && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        if (!to.getWorld().getName().equalsIgnoreCase(cfg().getString("lobby-portal.world", ""))) return;
        int x1 = cfg().getInt("lobby-portal.pos1.x"), y1 = cfg().getInt("lobby-portal.pos1.y"), z1 = cfg().getInt("lobby-portal.pos1.z");
        int x2 = cfg().getInt("lobby-portal.pos2.x"), y2 = cfg().getInt("lobby-portal.pos2.y"), z2 = cfg().getInt("lobby-portal.pos2.z");
        int x = to.getBlockX(), y = to.getBlockY(), z = to.getBlockZ();
        if (x < Math.min(x1, x2) || x > Math.max(x1, x2) || y < Math.min(y1, y2) || y > Math.max(y1, y2)
                || z < Math.min(z1, z2) || z > Math.max(z1, z2)) return;
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long prev = last.get(p.getUniqueId());
        if (prev != null && now - prev < 2000) return;
        last.put(p.getUniqueId(), now);
        Location target = target();
        if (target == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> p.teleport(target));
    }

    /** Точка назначения: сохранённая командой, иначе спавн мира target.world. */
    Location target() {
        String wn = cfg().getString("lobby-portal.target.world", "");
        World w = wn.isEmpty() ? null : Bukkit.getWorld(wn);
        if (w == null) return null;
        if (!cfg().contains("lobby-portal.target.x")) return w.getSpawnLocation().add(0.5, 0, 0.5);
        return new Location(w, cfg().getDouble("lobby-portal.target.x"), cfg().getDouble("lobby-portal.target.y"),
                cfg().getDouble("lobby-portal.target.z"), (float) cfg().getDouble("lobby-portal.target.yaw"),
                (float) cfg().getDouble("lobby-portal.target.pitch"));
    }
}
