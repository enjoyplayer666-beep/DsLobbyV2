package ru.dscraft.destroylobby.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import ru.dscraft.destroylobby.config.ConfigManager;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Обёртка над LuckPerms API.
 * Префикс/суффикс читаются напрямую из LuckPerms в рантайме - при изменении
 * групп/весов/мета через /lp плагин ничего не нужно перекомпилировать.
 * <p>
 * Личный ("кастом") префикс, который игрок ставит себе сам через /prefix set,
 * реализован не отдельным хранилищем, а обычной LuckPerms prefix-нодой с высоким
 * приоритетом на самом пользователе - поэтому он автоматически перекрывает
 * групповой префикс и сразу виден везде, где используется %luckperms_prefix%
 * (чат-плагин, тэб, ник над головой), без всякой лишней синхронизации.
 */
public class LuckPermsHook {

    private final LuckPerms api; // может быть null, если LuckPerms не установлен
    private final ConfigManager configManager;

    public LuckPermsHook(LuckPerms api, ConfigManager configManager) {
        this.api = api;
        this.configManager = configManager;
    }

    public boolean isEnabled() {
        return api != null;
    }

    private User getUser(Player player) {
        if (api == null) return null;
        return api.getUserManager().getUser(player.getUniqueId());
    }

    /** Префикс игрока (например "&cAdmin "), либо пустая строка если LuckPerms недоступен/префикс не задан. */
    public String getPrefix(Player player) {
        User user = getUser(player);
        if (user == null) return "";
        CachedMetaData metaData = user.getCachedData().getMetaData();
        String prefix = metaData.getPrefix();
        return prefix == null ? "" : prefix;
    }

    /** Суффикс игрока (например " &a&l✔"), либо пустая строка если не задан. */
    public String getSuffix(Player player) {
        User user = getUser(player);
        if (user == null) return "";
        CachedMetaData metaData = user.getCachedData().getMetaData();
        String suffix = metaData.getSuffix();
        return suffix == null ? "" : suffix;
    }

    /** Имя основной группы игрока (primary group), например "legend". */
    public String getPrimaryGroupName(Player player) {
        User user = getUser(player);
        if (user == null) return "default";
        return user.getPrimaryGroup();
    }

    /**
     * Вес (weight) игрока для сортировки в табе. Берётся не из "сохранённой"
     * primary-группы, а из групп, РЕАЛЬНО активных у игрока в его текущем
     * контексте (мир и т.д.) - берётся максимальный weight среди них.
     */
    public int getWeight(Player player) {
        if (api == null) return 0;
        User user = getUser(player);
        if (user == null) return 0;

        QueryOptions queryOptions = api.getContextManager().getQueryOptions(player);
        Collection<Group> activeGroups = user.getInheritedGroups(queryOptions);

        int maxWeight = 0;
        for (Group group : activeGroups) {
            maxWeight = Math.max(maxWeight, group.getWeight().orElse(0));
        }
        return maxWeight;
    }

    /**
     * Ставит игроку личный префикс с приоритетом из конфига (customprefix.priority),
     * перекрывающий групповой. Предыдущие ноды с этим же приоритетом удаляются,
     * чтобы у игрока не копились старые префиксы при повторных /prefix set.
     *
     * @return true если успешно применено (LuckPerms доступен)
     */
    public boolean setCustomPrefix(Player player, String rawPrefix) {
        User user = getUser(player);
        if (user == null) return false;

        int priority = configManager.customPrefixPriority();
        // старый личный префикс убираем, новый ставим
        removeAllOwnPrefixes(user);

        PrefixNode node = PrefixNode.builder(rawPrefix, priority).build();
        DataMutateResult result = user.data().add(node);
        api.getUserManager().saveUser(user);
        return result.wasSuccessful();
    }

    /** Убирает личный префикс игрока (сохранённые ноды с приоритетом customprefix.priority). */
    public boolean clearCustomPrefix(Player player) {
        User user = getUser(player);
        if (user == null) return false;

        boolean removed = removeAllOwnPrefixes(user);
        if (removed) {
            api.getUserManager().saveUser(user);
        }
        return removed;
    }

    /** Мета чат-префикса из DestroyChat (/prefix chat). */
    private static final String CHAT_PREFIX_META = "destroy-chat-prefix";

    /**
     * /prefix reset ник: снять игроку (и не в сети) личный префикс и чат-префикс.
     * @return true - было что снимать
     */
    public CompletableFuture<Boolean> resetPrefixes(UUID uuid) {
        if (api == null) return CompletableFuture.completedFuture(false);
        return api.getUserManager().loadUser(uuid).thenApply(user -> {
            boolean removed = removeAllOwnPrefixes(user);
            Set<MetaNode> metas = user.getNodes(NodeType.META).stream()
                    .filter(n -> n.getMetaKey().equals(CHAT_PREFIX_META))
                    .collect(Collectors.toSet());
            for (MetaNode node : metas) user.data().remove(node);
            removed |= !metas.isEmpty();
            if (removed) api.getUserManager().saveUser(user);
            return removed;
        });
    }

    /**
     * Сброс личного префикса (/prefix set): только ноды с приоритетом customprefix.priority (и старым).
     * Остальные префиксы на игроке (например, выданные персоналу через /lp user ... setprefix) не трогаем.
     */
    private boolean removeAllOwnPrefixes(User user) {
        int priority = configManager.customPrefixPriority();
        Set<PrefixNode> toRemove = user.getNodes(NodeType.PREFIX).stream()
                .filter(n -> n.getPriority() == priority || n.getPriority() == ConfigManager.LEGACY_CUSTOM_PREFIX_PRIORITY)
                .collect(Collectors.toSet());
        for (PrefixNode node : toRemove) {
            user.data().remove(node);
        }
        return !toRemove.isEmpty();
    }
}
