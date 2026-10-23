package com.treasurehunter.ae2pinyincompat;

import me.towdium.jecharacters.utils.Match;

import java.util.Locale;

/**
 * 唯一直接接触 JEC 内部 API 的类。
 *
 * <p>{@code Match#contains(String, CharSequence)} 就是 JEC 改写 {@code String.contains}
 * 时调用的同一个入口（内部走 {@code PinIn.contains}），所以这里的匹配结果与 JEI 里的
 * 拼音搜索完全一致。</p>
 *
 * <p>注意：{@code PinIn.contains} 本身不做大小写折叠，调用方需要自己统一小写。
 * JEC 在 {@code Match#matcher} 里也是这么处理的。</p>
 */
final class PinyinMatcher {

    private PinyinMatcher() {
    }

    static boolean containsAll(String text, String query) {
        String haystack = text.toLowerCase(Locale.ROOT);
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        for (String term : needle.split("\\s+")) {
            if (!Match.contains(haystack, term)) {
                return false;
            }
        }
        return true;
    }
}
