package ru.dscraft.destroylobby.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Читает config.yml и отдаёт значения остальным менеджерам.
 * Префикс/суффикс игрока в игровом мире здесь НЕ хранятся - они читаются из LuckPerms.
 */
public class ConfigManager {

    private final DestroyLobbyPlugin plugin;
    private FileConfiguration cfg;

    public ConfigManager(DestroyLobbyPlugin plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }


    // ---- миры ----

    public String getLobbyWorld() {
        return cfg.getString("worlds.lobby", "world");
    }

    /** ключ = имя мира на сервере, значение = отображаемое имя (например "SkyPvP") */
    public Map<String, String> getGameWorlds() {
        Map<String, String> map = new LinkedHashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("worlds.game-worlds");
        if (sec == null) return map;
        for (String key : sec.getKeys(false)) {
            map.put(key, sec.getString(key, key));
        }
        return map;
    }

    public boolean isLobbyWorld(String worldName) {
        return getLobbyWorld().equalsIgnoreCase(worldName);
    }

    public String getDisplayNameForWorld(String worldName) {
        if (isLobbyWorld(worldName)) return cfg.getString("worlds.lobby-display-name", "lobby");
        return getGameWorlds().getOrDefault(worldName, worldName);
    }

    /** true - игроки с правом обхода защиты лобби и OP при заходе остаются там, где вышли. */
    public boolean joinBypass() {
        return cfg.getBoolean("lobby-protection.join-bypass", false);
    }

    public boolean isGameWorld(String worldName) {
        return getGameWorlds().containsKey(worldName);
    }

    // ---- изоляция лобби / игровых миров (таб + чат) ----

    public boolean isolationEnabled() {
        return cfg.getBoolean("isolation.enabled", true);
    }

    /** "lobby-vs-game" - все игровые миры видят друг друга; "per-world" - каждый мир отдельно. */
    public String isolationMode() {
        return cfg.getString("isolation.mode", "lobby-vs-game");
    }

    public boolean isolationChat() {
        return cfg.getBoolean("isolation.chat", true);
    }

    public String isolationSeeAllPermission() {
        return cfg.getString("isolation.see-all-permission", "destroylobby.seeall");
    }

    public String isolationChatSeeAllPermission() {
        return cfg.getString("isolation.chat-see-all-permission", "destroylobby.chat.seeall");
    }

    // таб и скорборд - в плагине MediaTab

    // ---- прыжок в лобби ----

    public boolean jumpEnabled() {
        return cfg.getBoolean("lobby-jump.enabled", true);
    }

    public boolean jumpSneakForNormal() {
        return cfg.getBoolean("lobby-jump.sneak-for-normal-jump", true);
    }

    public String jumpPermission() {
        return cfg.getString("lobby-jump.permission", "");
    }

    public int jumpCooldownTicks() {
        return Math.max(0, cfg.getInt("lobby-jump.cooldown-ticks", 20));
    }

    public double jumpVerticalMin() {
        return cfg.getDouble("lobby-jump.vertical.min", 0.55);
    }

    public double jumpVerticalMax() {
        return cfg.getDouble("lobby-jump.vertical.max", 1.30);
    }

    public double jumpForwardMin() {
        return cfg.getDouble("lobby-jump.forward.min", 0.35);
    }

    public double jumpForwardMax() {
        return cfg.getDouble("lobby-jump.forward.max", 0.85);
    }

    public boolean jumpNoFallDamage() {
        return cfg.getBoolean("lobby-jump.no-fall-damage", true);
    }

    public String jumpFlyKeepPermission() {
        return cfg.getString("lobby-jump.keep-flight-permission", "essentials.fly");
    }

    public String jumpSound() {
        return cfg.getString("lobby-jump.sound.name", "ENTITY_BLAZE_SHOOT");
    }

    public float jumpSoundVolume() {
        return (float) cfg.getDouble("lobby-jump.sound.volume", 0.5);
    }

    public float jumpSoundPitch() {
        return (float) cfg.getDouble("lobby-jump.sound.pitch", 1.6);
    }

    public String jumpParticle() {
        return cfg.getString("lobby-jump.particles.main", "SOUL_FIRE_FLAME");
    }

    public String jumpSecondaryParticle() {
        return cfg.getString("lobby-jump.particles.secondary", "SOUL");
    }

    public int jumpBurstCount() {
        return cfg.getInt("lobby-jump.particles.burst-count", 20);
    }

    public int jumpRingPoints() {
        return cfg.getInt("lobby-jump.particles.ring-points", 12);
    }

    public int jumpTrailCount() {
        return cfg.getInt("lobby-jump.particles.trail-count", 3);
    }

    public int jumpTrailMaxTicks() {
        return cfg.getInt("lobby-jump.particles.trail-max-ticks", 100);
    }

    public int jumpLandingCount() {
        return cfg.getInt("lobby-jump.particles.landing-count", 10);
    }

    // ---- chat ----

    public boolean chatBlockInLobby() {
        return cfg.getBoolean("chat.block-in-lobby", true);
    }

    public String getChatLobbyBlockedMessage() {
        return cfg.getString("chat.lobby-blocked-message",
                "<yellow>❗</yellow> <gray>Вы находитесь в <gold>лобби</gold>, здесь чат недоступен</gray>");
    }

    /** Сохраняет точку спавна лобби и пишет config.yml на диск. */
    public void setLobbySpawn(Location loc) {
        cfg.set("lobby-protection.spawn.world", loc.getWorld().getName());
        cfg.set("lobby-protection.spawn.x", loc.getX());
        cfg.set("lobby-protection.spawn.y", loc.getY());
        cfg.set("lobby-protection.spawn.z", loc.getZ());
        cfg.set("lobby-protection.spawn.yaw", loc.getYaw());
        cfg.set("lobby-protection.spawn.pitch", loc.getPitch());
        plugin.saveConfig();
    }

    // ---- lobby protection ----

    public boolean lobbyProtectionEnabled() {
        return cfg.getBoolean("lobby-protection.enabled", true);
    }

    public boolean forceSpawnOnJoin() {
        return cfg.getBoolean("lobby-protection.force-spawn-on-join", true);
    }

    /** Куда возрождать после смерти: "world" - в мире смерти, "lobby" - в лобби. */
    public boolean respawnInLobby() {
        return "lobby".equalsIgnoreCase(cfg.getString("lobby-protection.respawn-mode", "world"));
    }

    public Location getLobbySpawnLocation() {
        String worldName = cfg.getString("lobby-protection.spawn.world", "");
        if (worldName == null || worldName.isEmpty()) {
            worldName = getLobbyWorld();
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = cfg.getDouble("lobby-protection.spawn.x", world.getSpawnLocation().getX());
        double y = cfg.getDouble("lobby-protection.spawn.y", world.getSpawnLocation().getY());
        double z = cfg.getDouble("lobby-protection.spawn.z", world.getSpawnLocation().getZ());
        float yaw = (float) cfg.getDouble("lobby-protection.spawn.yaw", 0.0);
        // ровно по центру блока, взгляд прямо (не вверх/вниз) и строго по стороне света
        yaw = Math.round(yaw / 90f) * 90f;
        return new Location(world, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5, yaw, 0f);
    }

    public boolean lobbyBlockBuild() {
        return cfg.getBoolean("lobby-protection.block-build", true);
    }

    public boolean lobbyBlockBreak() {
        return cfg.getBoolean("lobby-protection.block-break", true);
    }

    public boolean lobbyBlockDropItems() {
        return cfg.getBoolean("lobby-protection.block-drop-items", true);
    }

    public boolean lobbyBlockPickupItems() {
        return cfg.getBoolean("lobby-protection.block-pickup-items", true);
    }

    public boolean lobbyBlockInventoryInteract() {
        return cfg.getBoolean("lobby-protection.block-inventory-interact", true);
    }

    public boolean lobbyBlockDamage() {
        return cfg.getBoolean("lobby-protection.block-damage", true);
    }

    public boolean lobbyBlockHunger() {
        return cfg.getBoolean("lobby-protection.block-hunger", true);
    }

    public List<String> lobbyAllowedCommands() {
        return cfg.getStringList("lobby-protection.allowed-commands");
    }

    /** Что видно по "/" в лобби всем, кроме опов. */
    public List<String> lobbyTabCommands() {
        // вход и регистрация видны всегда, плюс что дописано в конфиге
        java.util.Set<String> out = new java.util.LinkedHashSet<>(List.of("login", "l", "reg", "register", "changepassword"));
        out.addAll(cfg.getStringList("lobby-protection.tab-commands"));
        return new java.util.ArrayList<>(out);
    }

    public String lobbyBlockedActionMessage() {
        return cfg.getString("lobby-protection.blocked-action-message",
                "<yellow>❗</yellow> <gray>В лобби это действие недоступно</gray>");
    }

    public String lobbyBlockedCommandMessage() {
        return cfg.getString("lobby-protection.blocked-command-message",
                "<yellow>❗</yellow> <gray>Эта команда недоступна в лобби</gray>");
    }

    public boolean lobbyOpsBypass() {
        return cfg.getBoolean("lobby-protection.ops-bypass", true);
    }

    // ---- luckperms / оформление ----

    /**
     * Цвет ника по имени primary-группы ("&7", "&#CDCDFF", "<gray>").
     * Если для группы явно не задан цвет - берётся "luckperms.nick-colors.default".
     */
    public String getNickColorForGroup(String primaryGroupName) {
        String path = "luckperms.nick-colors." + primaryGroupName.toLowerCase();
        String direct = cfg.getString(path, null);
        if (direct != null && !direct.isEmpty()) return direct;
        return cfg.getString("luckperms.nick-colors.default", "&#CDCDFF");
    }

    // ---- кастом-префикс (/prefix set|reset) ----

    public String customPrefixPermission() {
        return cfg.getString("customprefix.permission", "destroylobby.customprefix");
    }

    public int customPrefixMaxLength() {
        return cfg.getInt("customprefix.max-length", 24);
    }

    /** Старое значение по умолчанию: оно ниже, чем 1000 у префиксов персонала, и в табе проигрывало им. */
    public static final int LEGACY_CUSTOM_PREFIX_PRIORITY = 999;

    public int customPrefixPriority() {
        int priority = cfg.getInt("customprefix.priority", 100000);
        return priority == LEGACY_CUSTOM_PREFIX_PRIORITY ? 100000 : priority;
    }

    // ---- storage ----

    public String getStorageFileName() {
        return cfg.getString("storage.file", "playerdata.yml");
    }
}
