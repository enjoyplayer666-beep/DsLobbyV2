package com.destroycraft.motd;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.util.CachedServerIcon;

public class MotdListener implements Listener {

    private final DestroyCraftMotdPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MotdListener(DestroyCraftMotdPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        String line1 = plugin.getConfig().getString("motd.line1", "");
        String line2 = plugin.getConfig().getString("motd.line2", "");

        Component motd = miniMessage.deserialize(line1)
                .append(Component.newline())
                .append(miniMessage.deserialize(line2));

        event.motd(motd);

        if (plugin.getConfig().getBoolean("fake-max-players.enabled", false)) {
            event.setMaxPlayers(plugin.getConfig().getInt("fake-max-players.max", event.getMaxPlayers()));
        }

        CachedServerIcon icon = plugin.getServerIcon();
        if (icon != null) {
            event.setServerIcon(icon);
        }
    }
}
