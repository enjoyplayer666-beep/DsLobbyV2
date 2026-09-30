package ru.dscraft.destroylobby.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.config.ConfigManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Лобби:
 * - пустой инвентарь (кроме опов): вещи и броня с сервера снимаются при входе в лобби и возвращаются при переходе в игровой мир
 *   (хранятся в plugins/DestroyLobby/inventories/<uuid>.yml, переживают перезапуск);
 * - полная защита: ломать/ставить/трогать что-либо может только оп;
 * - погода никогда не меняется (всегда ясно, без грозы) - во всех мирах.
 */
public class LobbyWorldListener implements Listener {

    private final DestroyLobbyPlugin plugin;
    private final ConfigManager configManager;
    private final File folder;

    public LobbyWorldListener(DestroyLobbyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.folder = new File(plugin.getDataFolder(), "inventories");
        for (World w : Bukkit.getWorlds()) clearWeather(w);
    }

    private boolean lobby(World w) {
        return w != null && configManager.isLobbyWorld(w.getName());
    }

    private static boolean op(Player p) {
        return p.isOp();
    }

    // ---------------- инвентарь: в лобби пусто ----------------

    private File file(Player p) {
        return new File(folder, p.getUniqueId() + ".yml");
    }

    private static boolean empty(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() != Material.AIR) return false;
        }
        return true;
    }

    /** Снять вещи в файл и очистить инвентарь (если уже есть сохранённые - лобби-вещи просто убираются). */
    private void stash(Player p) {
        File f = file(p);
        if (!f.exists() && !empty(p)) {
            folder.mkdirs();
            YamlConfiguration y = new YamlConfiguration();
            y.set("contents", List.of(p.getInventory().getContents()));
            y.set("level", p.getLevel());
            y.set("exp", (double) p.getExp());
            try {
                y.save(f);
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось сохранить инвентарь " + p.getName() + ": " + e.getMessage());
                return; // не чистим, чтобы ничего не потерять
            }
        }
        p.getInventory().clear();
        p.setItemOnCursor(null);
    }

    /** Вернуть вещи из файла (игрок ушёл из лобби в игровой мир). */
    @SuppressWarnings("unchecked")
    private void restore(Player p) {
        File f = file(p);
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<?> list = y.getList("contents");
        if (list != null) {
            ItemStack[] contents = new ItemStack[p.getInventory().getContents().length];
            for (int i = 0; i < contents.length && i < list.size(); i++) {
                if (list.get(i) instanceof ItemStack it) contents[i] = it;
            }
            p.getInventory().setContents(contents);
        }
        p.setLevel(y.getInt("level", p.getLevel()));
        p.setExp((float) y.getDouble("exp", p.getExp()));
        if (!f.delete()) plugin.getLogger().warning("Не удалось удалить " + f.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (op(p)) return; // опов не трогаем
        boolean fromLobby = lobby(e.getFrom());
        boolean toLobby = lobby(p.getWorld());
        if (!fromLobby && toLobby) stash(p);
        else if (fromLobby && !toLobby) restore(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        // перенос в лобби при входе может случиться позже - проверяем через тик
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = e.getPlayer();
            if (!p.isOnline() || op(p)) return;
            if (lobby(p.getWorld())) stash(p);
            else restore(p);
        }, 5L);
    }

    // ---------------- защита: только оп ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (op(p) || !lobby(p.getWorld())) return;
        if (e.getAction() == Action.PHYSICAL) {
            // вытаптывание грядок, нажимные плиты не трогаем
            if (e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.FARMLAND) e.setCancelled(true);
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Material m = e.getClickedBlock().getType();
            String n = m.name();
            // двери, люки, калитки, рычаги, кнопки, горшки, рамки, кнопки... - всё закрыто
            if (m.isInteractable() || n.endsWith("_SIGN") || n.contains("CANDLE") || n.endsWith("_POT")) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (op(e.getPlayer()) || !lobby(e.getPlayer().getWorld())) return;
        switch (e.getRightClicked().getType()) {
            case ITEM_FRAME, GLOW_ITEM_FRAME, ARMOR_STAND, PAINTING, MINECART, BOAT -> e.setCancelled(true);
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (!op(e.getPlayer()) && lobby(e.getPlayer().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (!lobby(e.getEntity().getWorld())) return;
        if (e instanceof HangingBreakByEntityEvent by && by.getRemover() instanceof Player p && op(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        // рамки, стойки, мобы-декорации в лобби не ломаются
        if (!lobby(e.getEntity().getWorld()) || e.getEntity() instanceof Player) return;
        if (e.getDamager() instanceof Player p && op(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!op(e.getPlayer()) && lobby(e.getPlayer().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!op(e.getPlayer()) && lobby(e.getPlayer().getWorld())) e.setCancelled(true);
    }

    // ---------------- мир сам по себе не меняется ----------------

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (lobby(e.getEntity().getWorld())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!lobby(e.getBlock().getWorld())) return;
        if (e.getPlayer() != null && op(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (lobby(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (lobby(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (lobby(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeaves(LeavesDecayEvent e) {
        if (lobby(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!lobby(e.getBlock().getWorld())) return;
        if (e.getEntity() instanceof Player p && op(p)) return;
        e.setCancelled(true);
    }

    // ---------------- погода: всегда ясно ----------------

    private static void clearWeather(World w) {
        w.setStorm(false);
        w.setThundering(false);
        w.setClearWeatherDuration(Integer.MAX_VALUE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (e.toWeatherState()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent e) {
        if (e.toThunderState()) e.setCancelled(true);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        clearWeather(e.getWorld());
    }
}
