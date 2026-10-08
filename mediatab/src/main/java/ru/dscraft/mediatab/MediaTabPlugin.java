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
public class MediaTabPlugin extends ru.dscraft.destroylobby.module.Module implements Listener {

    private Settings settings;
    private PlayerBoardService boards;
    private TabManager tab;
    private ScoreboardManager scoreboard;
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override
    public void onEnable() {
        boolean freshConfig = !new File(getDataFolder(), "config.yml").exists();
        saveDefaultConfig();
        // новые настройки (group-formats, default-name-color) дописываются в старый config.yml
        getConfig().options().copyDefaults(true);
        saveConfig();
        if (freshConfig) importOldSettings();
        migrate();

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
        // /glow - меню свечения
        glowMenu = new GlowMenu(this, settings);
        tab.glow(glowMenu);
        if (getCommand("glow") != null) getCommand("glow").setExecutor(glowMenu);
        // /tabemoji <ник> <эмодзи|off> - эмодзи у ника, ставит команда проекта
        this.luckPermsHook = luckPerms;
        getServer().getPluginManager().registerEvents(glowMenu, this);
        scoreboard = new ScoreboardManager(settings, boards);

        getServer().getPluginManager().registerEvents(this, this);
        startTasks();
        for (Player online : Bukkit.getOnlinePlayers()) {
            tab.handleJoin(online);
            scoreboard.handleJoin(online);
        }
    }

    private GlowMenu glowMenu;
    private LuckPermsHook luckPermsHook;

    /** Сразу обновить строку игрока (цвет свечения и т.п.). */
    void refresh(Player player) {
        if (tab != null) tab.updatePlayer(player, true);
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
        if (command.getName().equalsIgnoreCase("tabemoji")) return tabEmoji(sender, args);
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

    /** Команда проекта (группы tab.game.staff-groups), опы и право mediatab.emoji. */
    private boolean canSetEmoji(CommandSender sender) {
        if (!(sender instanceof Player p) || p.isOp() || p.hasPermission("mediatab.emoji")) return true;
        for (String g : settings.staffGroups()) if (p.hasPermission("group." + g)) return true;
        return false;
    }

    private boolean tabEmoji(CommandSender sender, String[] args) {
        if (!canSetEmoji(sender)) {
            sender.sendMessage(ColorUtil.parse("<#C9C9FB>Нет такой команды :/</#C9C9FB>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<#C7C4B7>/tabemoji <ник> <эмодзи> <dark_gray>-</dark_gray> поставить, /tabemoji <ник> off <dark_gray>-</dark_gray> убрать</#C7C4B7>"));
            return true;
        }
        if (luckPermsHook == null) {
            sender.sendMessage(ColorUtil.parse("<red>LuckPerms не найден."));
            return true;
        }
        org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            sender.sendMessage(ColorUtil.parse("<#E53232>◆</#E53232> <#C7C4B7>Игрок <white>" + args[0] + "</white> не найден.</#C7C4B7>"));
            return true;
        }
        String value = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        boolean off = value.equalsIgnoreCase("off") || value.equalsIgnoreCase("reset") || value.equals("-");
        if (!off && value.length() > 16) {
            sender.sendMessage(ColorUtil.parse("<#E53232>◆</#E53232> <#C7C4B7>Слишком длинно - максимум 16 символов.</#C7C4B7>"));
            return true;
        }
        String name = target.getName() == null ? args[0] : target.getName();
        java.util.UUID uuid = target.getUniqueId();
        luckPermsHook.setEmoji(uuid, off ? null : value).thenRun(() -> Bukkit.getScheduler().runTask(this, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) tab.updatePlayer(online, true);
            sender.sendMessage(ColorUtil.parse(off
                    ? "<#55FF55>Эмодзи у <white>" + name + "</white> убрано.</#55FF55>"
                    : "<#55FF55>Эмодзи у <white>" + name + "</white>:</#55FF55> ").append(off ? net.kyori.adventure.text.Component.empty() : ColorUtil.rich("&f" + value)));
        }));
        return true;
    }

    /**
     * config-version 2: смайлик лобби - символ ☺ из шрифта игры (прежний символ не из пака показывался квадратом);
     * вверху таба и скорборда всегда "SkyPvP", а не имя мира.
     */
    private void migrate() {
        var cfg = getConfig();
        // config-version 4: вверху таба - "На сервере N игроков"
        if (cfg.getInt("config-version", 1) == 3) {
            for (String path : new String[]{"tab.lobby.header", "tab.game.header"}) {
                List<String> lines = new ArrayList<>(cfg.getStringList(path));
                if (lines.stream().noneMatch(l -> l.contains("{online}"))) lines.add(0, ONLINE_LINE);
                cfg.set(path, lines);
            }
            cfg.set("config-version", 4);
            saveConfig();
            return;
        }
        // config-version 3: суффиксы - команда проекта &a&l✔, Elite SP &6&l✔
        if (cfg.getInt("config-version", 1) == 2) {
            cfg.set("tab.game.staff-suffix", "&a&l✔");
            if (cfg.isConfigurationSection("tab.game.group-formats.elitesp")) {
                cfg.set("tab.game.group-formats.elitesp.suffix", "&6&l✔");
            }
            cfg.set("config-version", 3);
            saveConfig();
            migrate();
            return;
        }
        if (cfg.getInt("config-version", 1) >= 2) return;
        cfg.set("tab.lobby.shared-prefix", cfg.getString("tab.lobby.shared-prefix", "").replace("\uE030", "☺"));
        for (String path : new String[]{"tab.lobby.header", "tab.game.header"}) {
            List<String> lines = new ArrayList<>();
            for (String l : cfg.getStringList(path)) lines.add(l.replace("{world}", "SkyPvP"));
            cfg.set(path, lines);
        }
        cfg.set("scoreboard.game.title", cfg.getString("scoreboard.game.title", "").replace("{world}", "SkyPvP"));
        cfg.set("config-version", 3);
        saveConfig();
        migrate();
    }

    static final String ONLINE_LINE = "<#E6E6F0>На сервере</#E6E6F0> <#FFB347><bold>{online} {players_word}</bold></#FFB347>";
}
