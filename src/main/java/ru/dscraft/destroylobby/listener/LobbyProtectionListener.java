package ru.dscraft.destroylobby.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.config.ConfigManager;
import ru.dscraft.destroylobby.util.ColorUtil;

import java.util.List;
import java.util.Locale;

/**
 * В лобби обычному игроку (не опу и без спец-права) доступны только вход в портал
 * и открытие таба - всё остальное здесь блокируется: стройка/лом блоков,
 * дроп/подбор предметов, открытие сундуков и т.п. контейнеров, урон, голод,
 * любые команды кроме явно разрешённых в конфиге.
 * <p>
 * Байпас получают опы (если {@code ops-bypass: true}) и любой игрок с правом
 * {@link #BYPASS_PERMISSION} (например куратор, у которого доступ "на уровне опа",
 * но сам он не op) - выдаётся через LuckPerms: {@code /lp group куратор permission set destroylobby.lobby.bypass true}.
 * <p>
 * Спавн в лобби при заходе/респавне вынесен в {@link SpawnListener}.
 */
public class LobbyProtectionListener implements Listener {

    public static final String BYPASS_PERMISSION = "destroylobby.lobby.bypass";

    private final DestroyLobbyPlugin plugin;
    private final ConfigManager configManager;

    public LobbyProtectionListener(DestroyLobbyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    private boolean bypasses(Player player) {
        if (player.hasPermission(BYPASS_PERMISSION)) return true;
        return configManager.lobbyOpsBypass() && player.isOp();
    }

    private boolean inLobby(Player player) {
        return configManager.isLobbyWorld(player.getWorld().getName());
    }

    private void deny(Player player, String rawMessage) {
        Component component = ColorUtil.parse(rawMessage);
        player.sendMessage(component);
    }

    // ---- блокировка стройки/разрушения ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!configManager.lobbyProtectionEnabled() || !configManager.lobbyBlockBreak()) return;
        Player player = event.getPlayer();
        if (player.isOp() || !inLobby(player)) return; // ломать/ставить в лобби - только оп
        event.setCancelled(true);
        deny(player, configManager.lobbyBlockedActionMessage());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!configManager.lobbyProtectionEnabled() || !configManager.lobbyBlockBuild()) return;
        Player player = event.getPlayer();
        if (player.isOp() || !inLobby(player)) return; // ломать/ставить в лобби - только оп
        event.setCancelled(true);
        deny(player, configManager.lobbyBlockedActionMessage());
    }

    // ---- предметы ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!configManager.lobbyProtectionEnabled() || !configManager.lobbyBlockDropItems()) return;
        Player player = event.getPlayer();
        if (bypasses(player) || !inLobby(player)) return;
        event.setCancelled(true);
        deny(player, configManager.lobbyBlockedActionMessage());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!configManager.lobbyProtectionEnabled() || !configManager.lobbyBlockPickupItems()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (bypasses(player) || !inLobby(player)) return;
        event.setCancelled(true);
    }

    // ---- сундуки/контейнеры (свой инвентарь игрока не трогаем) ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!configManager.lobbyProtectionEnabled() || !configManager.lobbyBlockInventoryInteract()) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (bypasses(player) || !inLobby(player)) return;

        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) {
            return; // это собственный инвентарь/крафт-сетка игрока, не контейнер
        }
        event.setCancelled(true);
        deny(player, configManager.lobbyBlockedActionMessage());
    }

    // ---- урон и голод ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!configManager.lobbyProtectionEnabled() || !configManager.lobbyBlockDamage()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (bypasses(player) || !inLobby(player)) return;
        event.setCancelled(true);
    }

    /**
     * В лобби умереть нельзя никому (и опам): любой урон по игроку отменяется,
     * упал в пустоту - сразу на спавн лобби.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !inLobby(player)) return;
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            org.bukkit.Location spawn = configManager.getLobbySpawnLocation();
            if (spawn != null) {
                player.setFallDistance(0);
                player.teleport(spawn);
            }
        }
    }

    /** Из лобби нельзя нанести урон никому - ни игрокам, ни мобам (и опам тоже). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyAttack(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker != null && inLobby(attacker)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!configManager.lobbyProtectionEnabled() || !configManager.lobbyBlockHunger()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (bypasses(player) || !inLobby(player)) return;
        event.setCancelled(true);
    }

    // ---- Tab в лобби: только /login и /changepassword (кроме опов) ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || !inLobby(player)) return;
        List<String> visible = configManager.lobbyTabCommands().stream()
                .map(c -> c.toLowerCase(Locale.ROOT).replace("/", "")).toList();
        event.getCommands().removeIf(c -> !visible.contains(c.toLowerCase(Locale.ROOT)));
    }

    /** Список команд для "/" пересылается при смене мира: в лобби - урезанный, в игре - обычный. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refreshCommandsLater(event.getPlayer(), 2L);
    }

    /** При входе список "/" уходит игроку раньше, чем он оказывается в лобби и получает права - пересылаем. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        refreshCommandsLater(event.getPlayer(), 10L);
        refreshCommandsLater(event.getPlayer(), 40L);
    }

    private void refreshCommandsLater(Player player, long ticks) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.updateCommands();
        }, ticks);
    }

    // ---- команды: разрешён только вход в портал (обычно это физический блок, не команда) ----
    // ---- и явно перечисленные в конфиге команды ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!configManager.lobbyProtectionEnabled()) return;
        Player player = event.getPlayer();
        if (bypasses(player) || !inLobby(player)) return;

        String message = event.getMessage().substring(1); // убираем "/"
        if (message.isEmpty()) return;
        String label = message.split(" ")[0].toLowerCase(Locale.ROOT);
        // телепорты DestroyLobby работают и в лобби
        if (label.equals("hub") || label.equals("lobby") || label.equals("spawn")) return;
        // в лобби префикс не меняется никогда: /prefix и /chatprefix закрыты, даже если есть в allowed-commands
        if (label.equals("prefix") || label.equals("chatprefix")) {
            event.setCancelled(true);
            deny(player, configManager.lobbyBlockedCommandMessage());
            return;
        }

        List<String> allowed = configManager.lobbyAllowedCommands();
        for (String allowedCommand : allowed) {
            if (allowedCommand.equalsIgnoreCase(label)) {
                return; // команда разрешена
            }
        }

        event.setCancelled(true);
        deny(player, configManager.lobbyBlockedCommandMessage());
    }
}
