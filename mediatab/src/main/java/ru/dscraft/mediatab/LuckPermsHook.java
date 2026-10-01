package ru.dscraft.mediatab;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

/** Префикс, суффикс и вес игрока из LuckPerms. Создаётся только если LuckPerms установлен. */
final class LuckPermsHook {

    private final LuckPerms api = LuckPermsProvider.get();

    private User user(Player player) {
        return api.getUserManager().getUser(player.getUniqueId());
    }

    String prefix(Player player) {
        User user = user(player);
        String p = user == null ? null : user.getCachedData().getMetaData().getPrefix();
        return p == null ? "" : p;
    }

    String suffix(Player player) {
        User user = user(player);
        String s = user == null ? null : user.getCachedData().getMetaData().getSuffix();
        return s == null ? "" : s;
    }

    /** Префикс самой группы (без наследования игрока), "" - нет. */
    String groupPrefix(String name) {
        Group group = api.getGroupManager().getGroup(name);
        String p = group == null ? null : group.getCachedData().getMetaData().getPrefix();
        return p == null ? "" : p;
    }

    /** Суффикс самой группы, "" - нет. */
    String groupSuffix(String name) {
        Group group = api.getGroupManager().getGroup(name);
        String s = group == null ? null : group.getCachedData().getMetaData().getSuffix();
        return s == null ? "" : s;
    }

    /** Свой префикс игрока (/prefix set) - он главнее оформления группы. */
    boolean hasOwnPrefix(Player player) {
        User user = user(player);
        return user != null && !user.getNodes(net.luckperms.api.node.NodeType.PREFIX).isEmpty();
    }

    /** Наибольший weight среди групп, активных у игрока в его текущем контексте (мир и т.д.). */
    int weight(Player player) {
        User user = user(player);
        if (user == null) return 0;
        int max = 0;
        for (Group group : user.getInheritedGroups(api.getContextManager().getQueryOptions(player))) {
            max = Math.max(max, group.getWeight().orElse(0));
        }
        return max;
    }
}
