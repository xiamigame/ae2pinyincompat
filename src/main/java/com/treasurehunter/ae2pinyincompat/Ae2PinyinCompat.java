package com.treasurehunter.ae2pinyincompat;

import net.minecraftforge.fml.common.Mod;

/**
 * AE2 拼音搜索兼容（客户端模组）。
 *
 * <p>本模组不注册任何内容，只通过 Mixin 在 AE2 的搜索谓词外面包一层
 * 「拼音兜底」，让 {@code ae2searchimprovementsbackport} 接管搜索后
 * 仍然能用拼音（JEC）匹配中文物品名。</p>
 */
@Mod(Ae2PinyinCompat.MODID)
public final class Ae2PinyinCompat {

    public static final String MODID = "ae2pinyincompat";

    public Ae2PinyinCompat() {
        // 无需注册任何东西；Mixin 由 MANIFEST 的 MixinConfigs 挂载。
    }
}
