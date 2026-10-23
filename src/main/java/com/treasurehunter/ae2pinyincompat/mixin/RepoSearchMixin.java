package com.treasurehunter.ae2pinyincompat.mixin;

import appeng.client.gui.me.search.RepoSearch;
import appeng.menu.me.common.GridInventoryEntry;
import com.treasurehunter.ae2pinyincompat.PinyinFallbackPredicate;
import com.treasurehunter.ae2pinyincompat.PinyinSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * 给 AE2 的搜索谓词追加一层「拼音兜底」。
 *
 * <p>AE2 11.7.6 的 {@code RepoSearch#setSearchString}：</p>
 *
 * <pre>
 * if (s.equals(this.searchString)) return;
 * this.search = SearchPredicates.fromString(s, this);   // 这里可能被 backport 换掉返回值
 * this.searchString = s;
 * this.cache.clear();
 * </pre>
 *
 * <p>在方法末尾（TAIL）读取<b>最终</b>的 {@code search} 字段再包一层，因此：</p>
 * <ul>
 *   <li>谓词是 AE2 原生生成、还是 {@code ae2searchimprovementsbackport} 生成，都无所谓；</li>
 *   <li>与其它模组在同一个方法上的 Mixin 执行顺序无关（读的是最终值）；</li>
 *   <li>不引用 backport 的任何类，backport 升级/移除都不会破坏本模组。</li>
 * </ul>
 */
@Mixin(value = RepoSearch.class, remap = false)
public abstract class RepoSearchMixin {

    @Shadow
    private Predicate<GridInventoryEntry> search;

    /** 已经包裹过的查询串，避免同一次输入被重复包裹。 */
    @Unique
    private String ae2pinyincompat$wrappedFor;

    @Inject(
            method = "setSearchString(Ljava/lang/String;)V",
            at = @At("TAIL"),
            remap = false)
    private void ae2pinyincompat$addPinyinFallback(String query, CallbackInfo ci) {
        if (query == null || query.isEmpty()) {
            // 清空搜索后必须复位，否则再输入同样的词不会重新生效。
            this.ae2pinyincompat$wrappedFor = null;
            return;
        }
        if (query.equals(this.ae2pinyincompat$wrappedFor)) {
            return;
        }
        if (!PinyinSupport.shouldHandle(query)) {
            return;
        }

        Predicate<GridInventoryEntry> original = this.search;
        if (original == null) {
            return;
        }

        this.ae2pinyincompat$wrappedFor = query;
        this.search = new PinyinFallbackPredicate(original, query);
    }
}
