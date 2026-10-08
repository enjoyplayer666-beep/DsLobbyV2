package ru.dscraft.destroylobby;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dscraft.destroylobby.command.PrefixCommand;
import ru.dscraft.destroylobby.config.ConfigManager;
import ru.dscraft.destroylobby.hook.LuckPermsHook;
import ru.dscraft.destroylobby.hook.PlaceholderHook;
import ru.dscraft.destroylobby.listener.ChatListener;
import ru.dscraft.destroylobby.listener.LobbyJumpListener;
import ru.dscraft.destroylobby.listener.LobbyProtectionListener;
import ru.dscraft.destroylobby.listener.GameWelcomeListener;
import ru.dscraft.destroylobby.listener.TeleportCommandListener;
import ru.dscraft.destroylobby.listener.UnknownCommandListener;
import ru.dscraft.destroylobby.listener.PlayerTimeMenuListener;
import ru.dscraft.destroylobby.listener.PlayerConnectionListener;
import ru.dscraft.destroylobby.listener.SpawnListener;
import ru.dscraft.destroylobby.stats.StatsManager;
import ru.dscraft.destroylobby.api.LobbyApi;
import ru.dscraft.destroylobby.visibility.VisibilityManager;

import java.io.File;
import java.util.List;

public final class DestroyLobbyPlugin extends JavaPlugin {

    private ru.dscraft.destroylobby.module.Modules modules;

    private static DestroyLobbyPlugin instance;

    /** Версия формата config.yml. Старые конфиги (без этого ключа) заменяются новым. */
    private static final int CONFIG_VERSION = 2;

    /** Что переносим из старого конфига: миры, точка спавна, разрешённые команды, настройки /prefix. */
    private static final List<String> MIGRATED_PATHS = List.of(
            "worlds.lobby",
            "worlds.game-worlds",
            "lobby-protection.spawn",
            "lobby-protection.allowed-commands",
            "customprefix");

    private ConfigManager configManager;
    private LuckPermsHook luckPermsHook;
    private StatsManager statsManager;
    private VisibilityManager visibilityManager;

    @Override
    public void onLoad() {
        // раньше плагин назывался DestroyLobby
        ru.dscraft.destroylobby.module.Modules.adoptOldFolder(this, "DestroyLobby");
        Rebrand.apply(this);
    }

    @Override
    public void onEnable() {
        instance = this;

        migrateConfigIfOld();
        saveDefaultConfig();
        // дописываем в существующий config.yml новые настройки (чат и т.п.), не трогая старые значения
        getConfig().options().copyDefaults(true);
        migrateJumpSettings();
        saveConfig();
        this.configManager = new ConfigManager(this);

        // Хук LuckPerms - обязателен для префиксов/суффиксов/веса и для /prefix, но плагин не падает если его нет
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            LuckPerms api = LuckPermsProvider.get();
            this.luckPermsHook = new LuckPermsHook(api, configManager);
            getLogger().info("LuckPerms найден, префиксы/суффиксы в табе и /prefix включены.");
        } else {
            this.luckPermsHook = new LuckPermsHook(null, configManager);
            getLogger().warning("LuckPerms не найден! Префиксы/суффиксы и /prefix работать не будут.");
        }

        logIntegration("Multiverse-Core");
        logIntegration("AdvancedPortals");
        logIntegration("MediaDestroyChat");

        this.statsManager = new StatsManager(this);
        LobbyApi.init(statsManager); // таб и скорборд - в плагине MediaTab, он берёт отсюда коины/убийства/смерти
        this.visibilityManager = new VisibilityManager(this, configManager);

        // PlaceholderAPI - регистрируем экспаншен, если плагин установлен
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this, statsManager, luckPermsHook, configManager).register();
            getLogger().info("PlaceholderAPI найден, плейсхолдеры %destroy_...% зарегистрированы.");
        }

        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(this, statsManager, visibilityManager), this);
        getServer().getPluginManager().registerEvents(new GameWelcomeListener(this, configManager), this);
        getServer().getPluginManager().registerEvents(new ru.dscraft.destroylobby.listener.LobbyWorldListener(this, configManager), this);
        getServer().getPluginManager().registerEvents(new ru.dscraft.destroylobby.listener.LobbyPortalListener(this), this);
        getServer().getPluginManager().registerEvents(new UnknownCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerTimeMenuListener(this), this);
        TeleportCommandListener teleports = new TeleportCommandListener(this, configManager);
        getServer().getPluginManager().registerEvents(teleports, this);
        if (getCommand("hub") != null) getCommand("hub").setExecutor(teleports);
        if (getCommand("spawn") != null) {
            getCommand("spawn").setExecutor(teleports);
            // /spawn есть и у Essentials - забираем себе, чтобы в чате он был известной командой (не красным)
            getServer().getScheduler().runTask(this, () -> {
                org.bukkit.command.PluginCommand ours = getCommand("spawn");
                if (ours != null) getServer().getCommandMap().getKnownCommands().put("spawn", ours);
            });
        }
        getServer().getPluginManager().registerEvents(
                new LobbyJumpListener(this, configManager), this);
        getServer().getPluginManager().registerEvents(
                new ChatListener(configManager, visibilityManager), this);
        getServer().getPluginManager().registerEvents(
                new LobbyProtectionListener(this, configManager), this);
        getServer().getPluginManager().registerEvents(
                new SpawnListener(this, configManager), this);
        getServer().getPluginManager().registerEvents(
                statsManager, this);

        if (getCommand("prefix") != null) {
            PrefixCommand prefixCommand = new PrefixCommand(configManager, luckPermsHook);
            getCommand("prefix").setExecutor(prefixCommand);
            getCommand("prefix").setTabCompleter(prefixCommand);
        }

        // подхватываем игроков, которые уже онлайн (например после /reload плагинов)
        visibilityManager.updateAll();

        getLogger().info("DestroyLobby включен.");

        // модули в этом же jar: таб/скорборд и MOTD (папки plugins/DestroyLobby/MediaTab, /DestroyCraftMOTD)
        modules = new ru.dscraft.destroylobby.module.Modules(this);
        modules.enable(ru.dscraft.mediatab.MediaTabPlugin::new, "MediaTab", "mediatab", "glow", "tabemoji");
        modules.enable(com.destroycraft.motd.DestroyCraftMotdPlugin::new, "DestroyCraftMOTD", "destroymotd");
    }

    @Override
    public void onDisable() {
        if (modules != null) modules.disableAll();
        if (visibilityManager != null) visibilityManager.showEveryone();
        if (statsManager != null) {
            statsManager.saveAll();
        }
        LobbyApi.init(null);
        getLogger().info("DestroyLobby выключен.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("destroylobby")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                configManager.reload();
                visibilityManager.updateAll();
                sender.sendMessage("§a[DestroyLobby] Конфиг перезагружен.");
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("setspawn")) {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                configManager.setLobbySpawn(player.getLocation());
                sender.sendMessage("§a[DestroyLobby] Точка спавна лобби установлена в текущей позиции. "
                        + "Рекомендуется также выполнить /mvsetspawn в этом же месте.");
                return true;
            }

            if (args.length >= 2 && args[0].equalsIgnoreCase("portal")) {
                var cfg = getConfig();
                String first = args[1].toLowerCase(java.util.Locale.ROOT);
                if (first.equals("list")) {
                    var all = cfg.getConfigurationSection("portals");
                    sender.sendMessage("§7Порталы: §fлобби §7(" + (cfg.getBoolean("lobby-portal.enabled") ? "§aвкл" : "§cвыкл") + "§7)");
                    if (all != null) {
                        for (String n : all.getKeys(false)) {
                            sender.sendMessage("§7- §f" + n + " §7" + all.getString(n + ".world", "?") + " -> "
                                    + all.getString(n + ".target.world", "?") + " ("
                                    + (all.getBoolean(n + ".enabled") ? "§aвкл" : "§cвыкл") + "§7)");
                        }
                    }
                    return true;
                }
                // /destroylobby portal pos1 - портал лобби; /destroylobby portal <имя> pos1 - именованный
                java.util.Set<String> actions = java.util.Set.of("pos1", "pos2", "target", "on", "off", "remove");
                String base;
                String action;
                String portalName;
                if (actions.contains(first)) {
                    base = "lobby-portal";
                    action = first;
                    portalName = "лобби";
                } else {
                    if (args.length < 3 || !actions.contains(args[2].toLowerCase(java.util.Locale.ROOT))) {
                        sender.sendMessage("§7/destroylobby portal [имя] pos1|pos2|target|on|off|remove, /destroylobby portal list");
                        return true;
                    }
                    base = "portals." + first;
                    action = args[2].toLowerCase(java.util.Locale.ROOT);
                    portalName = first;
                }
                if (action.equals("remove")) {
                    cfg.set(base, null);
                    if (base.equals("lobby-portal")) cfg.set("lobby-portal.enabled", false);
                    saveConfig();
                    sender.sendMessage("§a[DestroyLobby] Портал " + portalName + " удалён.");
                    return true;
                }
                if (action.equals("on") || action.equals("off")) {
                    cfg.set(base + ".enabled", action.equals("on"));
                    saveConfig();
                    sender.sendMessage("§a[DestroyLobby] Портал " + portalName + " " + (action.equals("on") ? "включён" : "выключен") + ".");
                    return true;
                }
                Player player = requirePlayer(sender);
                if (player == null) return true;
                org.bukkit.Location l = player.getLocation();
                if (action.equals("target")) {
                    cfg.set(base + ".target.world", l.getWorld().getName());
                    cfg.set(base + ".target.x", l.getBlockX() + 0.5);
                    cfg.set(base + ".target.y", l.getY());
                    cfg.set(base + ".target.z", l.getBlockZ() + 0.5);
                    cfg.set(base + ".target.yaw", Math.round(l.getYaw() / 90f) * 90f);
                    cfg.set(base + ".target.pitch", 0f);
                    sender.sendMessage("§a[DestroyLobby] Портал " + portalName + ": точка, куда он ведёт, поставлена здесь.");
                } else {
                    cfg.set(base + ".world", l.getWorld().getName());
                    cfg.set(base + "." + action + ".x", l.getBlockX());
                    cfg.set(base + "." + action + ".y", l.getBlockY());
                    cfg.set(base + "." + action + ".z", l.getBlockZ());
                    sender.sendMessage("§a[DestroyLobby] Портал " + portalName + ": угол " + action + " поставлен: "
                            + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
                }
                saveConfig();
                return true;
            }

            sender.sendMessage("§7Использование: /destroylobby reload|setspawn|portal [имя] <pos1|pos2|target|on|off|remove>|portal list");
            return true;
        }

        if (command.getName().equalsIgnoreCase("coins")) {
            return handleCoinsCommand(sender, args);
        }

        return false;
    }

    /**
     * copyDefaults не меняет уже записанные значения, поэтому новые паузу прыжка (1 секунда)
     * и меньшее количество синего огня переносим в старый config.yml один раз - по lobby-jump.settings-version.
     */
    private void migrateJumpSettings() {
        FileConfiguration cfg = getConfig();
        Object current = cfg.get("lobby-jump.settings-version", null); // без значений по умолчанию
        int latest = cfg.getDefaults() == null ? 1 : cfg.getDefaults().getInt("lobby-jump.settings-version", 1);
        if (current instanceof Number n && n.intValue() >= latest) return;
        for (String path : new String[]{"lobby-jump.cooldown-ticks", "lobby-jump.particles.burst-count",
                "lobby-jump.particles.ring-points", "lobby-jump.particles.trail-count",
                "lobby-jump.particles.landing-count", "lobby-jump.vertical.min", "lobby-jump.vertical.max",
                "lobby-jump.forward.min", "lobby-jump.forward.max"}) {
            cfg.set(path, cfg.getDefaults().get(path));
        }
        cfg.set("lobby-jump.settings-version", latest);
        getLogger().info("Прыжок в лобби: новые настройки силы прыжка.");
    }

    /**
     * Старый config.yml (от прошлой версии) содержит старое оформление таба/скорборда,
     * например лишнюю строку в футере лобби. Такой конфиг переименовывается в
     * config-old-*.yml, на его место кладётся новый, а миры/спавн/команды переносятся.
     */
    private void migrateConfigIfOld() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) return;

        YamlConfiguration old = YamlConfiguration.loadConfiguration(file);
        if (old.getInt("config-version", 0) >= CONFIG_VERSION) return;

        File backup = new File(getDataFolder(), "config-old-" + System.currentTimeMillis() + ".yml");
        if (!file.renameTo(backup)) {
            getLogger().warning("Не удалось переименовать старый config.yml - удали его вручную, "
                    + "иначе останется старое оформление таба.");
            return;
        }

        saveDefaultConfig();
        reloadConfig();
        FileConfiguration cfg = getConfig();
        for (String path : MIGRATED_PATHS) {
            if (!old.contains(path)) continue;
            if (old.isConfigurationSection(path)) {
                cfg.set(path, null); // убираем значения по умолчанию, чтобы не смешались со старыми
                for (String key : old.getConfigurationSection(path).getKeys(true)) {
                    String full = path + "." + key;
                    if (!old.isConfigurationSection(full)) cfg.set(full, old.get(full));
                }
            } else {
                cfg.set(path, old.get(path));
            }
        }
        saveConfig();
        getLogger().info("Старый config.yml сохранён как " + backup.getName()
                + ", создан новый. Миры, точка спавна и разрешённые команды перенесены.");
    }

    private void logIntegration(String name) {
        if (getServer().getPluginManager().getPlugin(name) != null) {
            getLogger().info(name + " найден, работаем в паре.");
        } else {
            getLogger().warning(name + " не найден.");
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage("§cЭту команду можно выполнить только находясь в игре.");
        return null;
    }

    private boolean handleCoinsCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§7Использование: /coins <give|take|set> <ник> <сумма>");
            return true;
        }
        String action = args[0].toLowerCase();
        Player target = getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден или не в сети.");
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cСумма должна быть числом.");
            return true;
        }

        switch (action) {
            case "give" -> statsManager.get(target).addCoins(amount);
            case "take" -> statsManager.get(target).addCoins(-amount);
            case "set" -> statsManager.get(target).setCoins(amount);
            default -> {
                sender.sendMessage("§7Использование: /coins <give|take|set> <ник> <сумма>");
                return true;
            }
        }
        sender.sendMessage("§aОК. Коины игрока " + target.getName() + ": " + statsManager.get(target).getCoins());
        return true;
    }

    public static DestroyLobbyPlugin getInstance() {
        return instance;
    }
}
