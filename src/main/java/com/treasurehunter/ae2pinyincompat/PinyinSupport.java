package com.treasurehunter.ae2pinyincompat;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 拼音兜底的"外围"逻辑：环境判断、查询过滤、异常保护。
 *
 * <p>这个类<b>不直接引用</b> JEC 的任何类型，所以 JEC 缺失时它也能正常加载；
 * 真正碰到 JEC 的只有 {@link PinyinMatcher}，并且只在 JEC 存在时才会被调用。</p>
 */
public final class PinyinSupport {

    private static final Logger LOGGER = LogManager.getLogger("ae2pinyincompat");

    private static Boolean jecharactersLoaded;
    private static volatile boolean disabled;

    private PinyinSupport() {
    }

    /** JEC 是否在场（结果缓存一次）。 */
    public static boolean available() {
        Boolean cached = jecharactersLoaded;
        if (cached == null) {
            cached = ModList.get() != null && ModList.get().isLoaded("jecharacters");
            jecharactersLoaded = cached;
        }
        return cached;
    }

    /**
     * 这条查询是否交给拼音兜底。
     *
     * <p>带前缀的查询（{@code @ # $ *}）和正则模式（{@code /}）一律交还原实现，
     * 避免改变 ae2searchimprovementsbackport 已有的语义。</p>
     */
    public static boolean shouldHandle(String query) {
        if (disabled || !available()) {
            return false;
        }
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return !isPrefixed(trimmed.charAt(0));
    }

    private static boolean isPrefixed(char head) {
        switch (head) {
            case '@':
            case '#':
            case '$':
            case '*':
            case '/':
                return true;
            default:
                return false;
        }
    }

    /** 物品显示名是否命中查询（多词按空格 AND）。 */
    public static boolean matchesName(GridInventoryEntry entry, String query) {
        if (disabled) {
            return false;
        }
        try {
            AEKey what = entry.getWhat();
            if (what == null) {
                return false;
            }
            return PinyinMatcher.containsAll(what.getDisplayName().getString(), query);
        } catch (LinkageError | RuntimeException e) {
            // JEC 版本变化 / 内部 API 改名时不让搜索崩掉：只停用自己的兜底。
            disabled = true;
            LOGGER.error("[ae2pinyincompat] 拼音匹配不可用，已自动停用兜底（原版搜索不受影响）", e);
            return false;
        }
    }
}
