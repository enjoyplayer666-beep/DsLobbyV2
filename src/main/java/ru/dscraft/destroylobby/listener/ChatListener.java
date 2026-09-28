package ru.dscraft.destroylobby.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.dscraft.destroylobby.config.ConfigManager;
import ru.dscraft.destroylobby.util.ColorUtil;
import ru.dscraft.destroylobby.visibility.VisibilityManager;

/**
 * 1) В лобби обычный чат отключён (право destroylobby.chat.bypass - писать можно).
 * 2) Сообщение получают только игроки из той же группы миров: то, что пишут в SkyPvP,
 *    не видно в лобби, и наоборот. Право destroylobby.chat.seeall - видеть всё.
 *    Консоль получает всё как обычно.
 */
public class ChatListener implements Listener {

    public static final String BYPASS_PERMISSION = "destroylobby.chat.bypass";

    private final ConfigManager configManager;
    private final VisibilityManager visibilityManager;

    public ChatListener(ConfigManager configManager, VisibilityManager visibilityManager) {
        this.configManager = configManager;
        this.visibilityManager = visibilityManager;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChatBlock(AsyncChatEvent event) {
        if (!configManager.chatBlockInLobby()) return;

        Player player = event.getPlayer();
        if (!configManager.isLobbyWorld(player.getWorld().getName())) return;
        if (player.hasPermission(BYPASS_PERMISSION)) return;

        event.setCancelled(true);
        player.sendMessage(ColorUtil.parse(configManager.getChatLobbyBlockedMessage()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChatIsolate(AsyncChatEvent event) {
        if (!configManager.isolationEnabled() || !configManager.isolationChat()) return;

        Player sender = event.getPlayer();
        String seeAll = configManager.isolationChatSeeAllPermission();
        event.viewers().removeIf(audience ->
                audience instanceof Player viewer
                        && !viewer.equals(sender)
                        && !viewer.hasPermission(seeAll)
                        && !visibilityManager.sameBucket(sender, viewer));
    }
}
