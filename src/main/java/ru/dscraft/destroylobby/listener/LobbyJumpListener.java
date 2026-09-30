package ru.dscraft.destroylobby.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Прыжок в лобби (вместо двойного).
 * <p>
 * Обычный прыжок с земли (пробел) сразу подбрасывает игрока. Сила зависит от того, куда он смотрит:
 * <ul>
 *   <li>смотрит вниз - маленький прыжок;</li>
 *   <li>прямо - средний;</li>
 *   <li>вверх - максимально высоко и чуть дальше вперёд.</li>
 * </ul>
 * Считается линейно от угла взгляда (pitch от +90 до -90) между {@code lobby-jump.vertical.min/max}
 * и {@code lobby-jump.forward.min/max}. Направление вперёд берётся по повороту головы (yaw), поэтому
 * даже при взгляде строго вверх игрок немного летит вперёд.
 * <p>
 * Визуал: кольцо синего огня у ног + взрыв частиц в момент прыжка, густой след синего огня и душ
 * каждый тик полёта, "хлопок" огня при приземлении.
 * <p>
 * Присев (Shift) + пробел - обычный ванильный прыжок (для паркура), отключается в конфиге.
 */
public class LobbyJumpListener implements Listener {

    private final DestroyLobbyPlugin plugin;
    private final ConfigManager configManager;

    private final Map<UUID, BukkitTask> trails = new HashMap<>();
    private final Map<UUID, Integer> lastJumpTick = new HashMap<>();

    public LobbyJumpListener(DestroyLobbyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    // ---- служебное: убрать "полёт", который выдавала старая версия (двойной прыжок) ----

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // через тик: SpawnListener к этому моменту уже перенёс игрока в лобби
        Bukkit.getScheduler().runTaskLater(plugin, () -> resetOldFlight(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> resetOldFlight(event.getPlayer()));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        stopTrail(event.getPlayer().getUniqueId());
        resetOldFlight(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        stopTrail(uuid);
        lastJumpTick.remove(uuid);
    }

    /**
     * Старый двойной прыжок держал allowFlight=true в лобби, и этот флаг сохраняется в playerdata.
     * Снимаем его у обычных игроков в лобби, если у них нет права на /fly.
     */
    private void resetOldFlight(Player player) {
        if (!player.isOnline()) return;
        if (!configManager.jumpEnabled()) return;
        if (!configManager.isLobbyWorld(player.getWorld().getName())) return;
        GameMode gm = player.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return;
        String keep = configManager.jumpFlyKeepPermission();
        if (keep != null && !keep.isEmpty() && player.hasPermission(keep)) return;
        if (player.getAllowFlight()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    // ---- сам прыжок ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!configManager.jumpEnabled()) return;
        if (!configManager.isLobbyWorld(player.getWorld().getName())) return;

        GameMode gm = player.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return;
        if (player.isFlying() || player.isGliding() || player.isSwimming() || player.isInsideVehicle()) return;
        if (configManager.jumpSneakForNormal() && player.isSneaking()) return;

        String perm = configManager.jumpPermission();
        if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) return;

        int now = Bukkit.getCurrentTick();
        Integer last = lastJumpTick.get(player.getUniqueId());
        if (last != null && now - last < configManager.jumpCooldownTicks()) return;
        lastJumpTick.put(player.getUniqueId(), now);

        launch(player);
    }

    private void launch(Player player) {
        Location loc = player.getLocation();

        // pitch: -90 = смотрит вверх, 0 = прямо, +90 = вниз  ->  look: 1.0 .. 0.5 .. 0.0
        double look = (90.0 - loc.getPitch()) / 180.0;
        look = Math.max(0.0, Math.min(1.0, look));

        double vertical = lerp(configManager.jumpVerticalMin(), configManager.jumpVerticalMax(), look);
        double forward = lerp(configManager.jumpForwardMin(), configManager.jumpForwardMax(), look);

        double yawRad = Math.toRadians(loc.getYaw());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);

        player.setVelocity(new Vector(dirX * forward, vertical, dirZ * forward));
        player.setFallDistance(0f);

        // эффекты только в момент прыжка, на месте отрыва - за игроком не летят
        spawnLaunchEffects(player, loc);
        playSound(player, loc);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ---- эффекты ----

    private void spawnLaunchEffects(Player player, Location loc) {
        World world = loc.getWorld();
        Particle main = particle(configManager.jumpParticle());
        Particle secondary = particle(configManager.jumpSecondaryParticle());
        if (main == null) return;

        Location feet = loc.clone().add(0, 0.1, 0);

        // 1) кольцо синего огня, разлетающееся от ног
        int points = Math.max(0, configManager.jumpRingPoints());
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double cx = Math.cos(angle);
            double cz = Math.sin(angle);
            // count = 0 -> offset работает как направление полёта частицы, extra - скорость
            world.spawnParticle(main, feet.getX() + cx * 0.4, feet.getY(), feet.getZ() + cz * 0.4,
                    0, cx, 0.05, cz, 0.15);
        }

        // 2) облако огня вокруг игрока
        world.spawnParticle(main, loc.clone().add(0, 0.4, 0), configManager.jumpBurstCount(),
                0.35, 0.25, 0.35, 0.04);

        // 3) немного синих душ для объёма
        if (secondary != null) {
            world.spawnParticle(secondary, feet, 4, 0.3, 0.1, 0.3, 0.02);
        }
    }

    private void startTrail(Player player) {
        UUID uuid = player.getUniqueId();
        stopTrail(uuid);

        Particle main = particle(configManager.jumpParticle());
        Particle secondary = particle(configManager.jumpSecondaryParticle());
        if (main == null) return;

        int maxTicks = Math.max(10, configManager.jumpTrailMaxTicks());
        int trailCount = Math.max(1, configManager.jumpTrailCount());

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || !configManager.isLobbyWorld(player.getWorld().getName())) {
                    stopTrail(uuid);
                    return;
                }

                Location at = player.getLocation();
                World world = at.getWorld();

                // приземлился (первые тики игнорируем - игрок ещё отрывается от земли)
                if ((ticks > 4 && player.isOnGround()) || ticks > maxTicks) {
                    world.spawnParticle(main, at.clone().add(0, 0.1, 0), configManager.jumpLandingCount(),
                            0.45, 0.05, 0.45, 0.03);
                    stopTrail(uuid);
                    return;
                }

                // густой след у ног и немного на уровне тела
                world.spawnParticle(main, at.clone().add(0, 0.15, 0), trailCount, 0.18, 0.08, 0.18, 0.01);
                world.spawnParticle(main, at.clone().add(0, 0.9, 0), Math.max(1, trailCount / 3),
                        0.22, 0.3, 0.22, 0.005);
                if (secondary != null && ticks % 5 == 0) {
                    world.spawnParticle(secondary, at.clone().add(0, 0.2, 0), 1, 0.15, 0.05, 0.15, 0.01);
                }
            }
        }, 1L, 1L);

        trails.put(uuid, task);
    }

    private void stopTrail(UUID uuid) {
        BukkitTask task = trails.remove(uuid);
        if (task != null) task.cancel();
    }

    private void playSound(Player player, Location loc) {
        try {
            Sound sound = Sound.valueOf(configManager.jumpSound());
            loc.getWorld().playSound(loc, sound, configManager.jumpSoundVolume(), configManager.jumpSoundPitch());
        } catch (Exception ignored) {
            // неверное имя звука в конфиге - просто без звука
        }
    }

    private Particle particle(String name) {
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("none")) return null;
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    // ---- без урона от падения после прыжка в лобби ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!configManager.jumpEnabled() || !configManager.jumpNoFallDamage()) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!configManager.isLobbyWorld(player.getWorld().getName())) return;
        event.setCancelled(true);
    }
}
