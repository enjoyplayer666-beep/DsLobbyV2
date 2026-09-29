package ru.dscraft.mediatab;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** MediaTab: таб (шапка, низ, строки игроков, ники над головой) и скорборд справа. */
public class MediaTabPlugin extends JavaPlugin implements Listener {

    private Settings settings;
    private PlayerBoardService boards;
    private TabManager tab;
    private ScoreboardManager scoreboard;
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override
    public void onEnable() {
        boolean freshConfig = !new File(getDataFolder(), "config.yml").exists();
        saveDefaultConfig();
        if (freshConfig) importOldSettings();

        settings = new Settings(this);
        boards = new PlayerBoardService();
        LuckPermsHook luckPerms = null;
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPerms = new LuckPermsHook();
            } catch (Throwable e) {
                getLogger().warning("LuckPerms не подключился, префиксы в табе выключены: " + e.getMessage());
            }
        } else {
            getLogger().warning("LuckPerms не найден: в табе не будет префиксов и сортировки по группам.");
        }
        tab = new TabManager(settings, luckPerms, boards);
        scoreboard = new ScoreboardManager(settings, boards);

        getServer().getPluginManager().registerEvents(this, this);
        startTasks();
        for (Player online : Bukkit.getOnlinePlayers()) {
            tab.handleJoin(online);
            scoreboard.handleJoin(online);
        }
    }

    @Override
    public void onDisable() {
        stopTasks();
    }

    private void startTasks() {
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, tab::tick, 20L, settings.tabInterval()));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, scoreboard::refreshAll, 20L, settings.scoreboardInterval()));
    }

    private void stopTasks() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    // ---------------- перенос из DestroyLobby и DestroyChat ----------------

    /**
     * Первый запуск: миры, таб и скорборд из plugins/DestroyLobby/config.yml,
     * цвета ников и группы команды проекта из plugins/DestroyChat/config.yml.
     */
    private void importOldSettings() {
        FileConfiguration cfg = getConfig();
        File plugins = getDataFolder().getParentFile();
        boolean changed = false;

        File lobbyFile = new File(plugins, "DestroyLobby/config.yml");
        if (lobbyFile.exists()) {
            YamlConfiguration lobby = YamlConfiguration.loadConfiguration(lobbyFile);
            for (String section : new String[]{"worlds", "tab", "scoreboard"}) {
                changed |= copy(lobby.getConfigurationSection(section), cfg, section);
            }
            if (changed) getLogger().info("Таб, скорборд и миры перенесены из DestroyLobby/config.yml.");
        }

        File chatFile = new File(plugins, "DestroyChat/config.yml");
        if (chatFile.exists()) {
            YamlConfiguration chat = YamlConfiguration.loadConfiguration(chatFile);
            if (chat.isString("tab.name-color")) cfg.set("tab.game.name-color", chat.getString("tab.name-color"));
            if (chat.isString("tab.staff-name-color")) cfg.set("tab.game.staff-name-color", chat.getString("tab.staff-name-color"));
            ConfigurationSection groups = chat.getConfigurationSection("group-formats");
            if (groups != null && !groups.getKeys(false).isEmpty()) {
                cfg.set("tab.game.staff-groups", new ArrayList<>(groups.getKeys(false)));
            }
            changed = true;
            getLogger().info("Цвета ников и группы команды проекта перенесены из DestroyChat/config.yml.");
        }
        if (changed) saveConfig();
    }

    /** Копирует все значения раздела (со вложенными) поверх наших. */
    private static boolean copy(ConfigurationSection from, FileConfiguration to, String path) {
        if (from == null) return false;
        for (String key : from.getKeys(true)) {
            if (!from.isConfigurationSection(key)) to.set(path + "." + key, from.get(key));
        }
        return true;
    }

    // ---------------- события ----------------

    // MONITOR - после DestroyLobby, игрок уже стоит в лобби
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tab.handleJoin(player);
        scoreboard.handleJoin(player);
        // страховка: DestroyLobby может перенести игрока в лобби через пару тиков
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            tab.updateHeaderFooter(player);
            tab.updatePlayer(player, true);
            scoreboard.update(player);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tab.handleQuit(event.getPlayer());
        scoreboard.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // портал лобби -> SkyPvP: сразу меняем таб и скорборд, не ждём таймера
        tab.updateHeaderFooter(player);
        tab.updatePlayer(player, false);
        scoreboard.update(player);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            stopTasks();
            startTasks();
            tab.refreshAll();
            scoreboard.refreshAll();
            sender.sendMessage("§a[MediaTab] Конфиг перезагружен.");
            return true;
        }
        sender.sendMessage("§e/mediatab reload §7- перезагрузить конфиг");
        return true;
    }
}
