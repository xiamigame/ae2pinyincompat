package com.treasurehunter.ae2pinyincompat;

import appeng.menu.me.common.GridInventoryEntry;

import java.util.function.Predicate;

/**
 * 「原谓词命中 OR 拼音命中」的组合谓词。
 *
 * <p>只做加法：原谓词能命中的物品行为完全不变；只有原谓词漏掉的，
 * 才用拼音再判断一次。因此它既不会隐藏物品，也不会改变
 * {@code @}/{@code #}/{@code $}/{@code *}/{@code /o/} 等既有语义。</p>
 */
public final class PinyinFallbackPredicate implements Predicate<GridInventoryEntry> {

    private final Predicate<GridInventoryEntry> original;
    private final String query;

    public PinyinFallbackPredicate(Predicate<GridInventoryEntry> original, String query) {
        this.original = original;
        this.query = query;
    }

    @Override
    public boolean test(GridInventoryEntry entry) {
        return original.test(entry) || PinyinSupport.matchesName(entry, query);
    }
}
