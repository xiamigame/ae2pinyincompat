# AE2 拼音搜索兼容（ae2pinyincompat）

> 修复「装了 **ae2searchimprovementsbackport** 之后，**AE2 终端**里的拼音搜索失效」的**纯客户端**模组。
> 目标环境：Minecraft 1.18.2 / Forge 40.3.11 / AE2 11.7.6 / Just Enough Characters 4.5.9

---

## 它解决什么问题

| 环节 | 行为 |
|---|---|
| JEC（拼音模组） | 只会改写 **AE2 原生** `SearchPredicates` 里的两个 lambda（`$3` / `$4`） |
| `ae2searchimprovementsbackport` | 用 Mixin 把 `RepoSearch#setSearchString` 里对 `SearchPredicates.fromString(...)` 的调用**整个替换**成自己的一套谓词 |
| 结果 | JEC 改写的代码永远不会被执行 → AE2 终端搜不到拼音；JEI 不受影响 |

本模组在 `RepoSearch#setSearchString` 的**末尾**，给最终生效的搜索谓词再包一层：

```java
最终谓词 = 原谓词(由 AE2 或 backport 生成) 命中
        || 拼音命中(物品显示名, 由 JEC 的 Match.contains 提供)
```

因为读的是方法末尾的**最终值**，所以：
- 谓词是谁生成的都无所谓，与其它模组的 Mixin 顺序也无关；
- **不引用** `ae2searchimprovementsbackport` 的任何类——它升级、移除都不影响；
- 只做加法：不会隐藏原本能搜到的物品，也不会改变 `@` / `#` / `$` / `*` / `/o/` 的语义。

---

## 安装

1. 关闭游戏。
2. 把 `dist\ae2pinyincompat-1.0.0.jar` 复制到：
   ```
   E:\Game\Minecraft\WV\Wold's-Vaults-0.34.1\.minecraft\versions\Wold's Vaults - Vault Hunters Expansion\mods\
   ```
3. 启动游戏，进入 AE2 终端，直接输入拼音即可（**不需要** `/o/` 前缀）。

- **只需装在客户端**。专用服务器、局域网主机都不需要装。
- 无物品、方块、贴图、网络包；`displayTest=IGNORE_ALL_VERSION`，不会在服务器列表里报红字。

---

## 前置与兼容性

| 组件 | 版本 | 关系 |
|---|---|---|
| Forge | 1.18.2-40.x | 必需（`loaderVersion="[40,)"`） |
| AE2 | 11.7.x | 必需（`[11.7,12)`）；注入点基于 `RepoSearch` |
| Just Enough Characters | 4.5.x | **可选**；没装时模组静默不生效，不会崩 |
| ae2searchimprovementsbackport | 1.0+ | **可选**；装或不装都正常 |

---

## 使用与行为边界

直接输入即可：

| 输入 | 结果 |
|---|---|
| `gong` | 命中「弓」 |
| `zuanshigao` | 命中「钻石镐」 |
| `zuan shi gao` | 多词 AND，同样命中 |
| `GoNg` | 大小写不敏感 |
| `弓` | 中文直搜，行为不变 |
| `@ae2` / `#xxx` / `$tag` / `*minecraft:stone` | **不介入**，完全保持 backport 原有语义 |
| `/o/...` | **不介入**，保持正则模式原样 |

> 设计原则：拼音兜底只在「原谓词判定为不匹配」时才计算，因此结果集只会**变大**，不会变小。

---

## 进游戏后的验收清单

| # | 操作 | 预期 |
|---|---|---|
| T1 | ME 终端输入 `gong` | 出现「弓」等物品 |
| T2 | 输入 `zuanshigao` / `zuan shi gao` | 出现「钻石镐」 |
| T3 | 输入 `GoNg` | 同上 |
| T4 | 输入中文「弓」 | 与未装模组时一致 |
| T5 | `@ae2` / `#xxx` / `$minecraft:logs` / `*minecraft:stone` | 与 backport 原行为一致 |
| T6 | `/o/...` | 与 backport 原行为一致 |
| T7 | 清空搜索框后再输入 `gong` | 仍能命中 |
| T8 | 快速连续输入/删除 | 无卡顿、无报错 |
| T9 | 合成终端、编码终端 | 同样支持拼音 |
| T10 | 大型网络（数千条目） | 搜索可接受 |
| T11 | 移除 backport 后启动 | 仍正常（走 AE2 原生 + JEC 路径） |
| T12 | 移除 JEC 后启动 | 正常加载，搜索退化为原行为 |

---

## 排错

1. 启动后 `logs/latest.log` 搜索 `ae2pinyincompat`，应能看到模组加载与 `Mixing RepoSearchMixin into appeng.client.gui.me.search.RepoSearch`。
2. 需要看改写后的字节码：JVM 参数加 `-Dmixin.debug.export=true`，产物在实例目录的 `.mixin.out\`。
3. 若日志出现：
   ```
   [ae2pinyincompat] 拼音匹配不可用，已自动停用兜底（原版搜索不受影响）
   ```
   说明 JEC 的内部 API 变了（`Match.contains` 签名不匹配）。此时搜索退回原行为，不会崩；把日志发我即可适配。
4. 搜不到时先确认是不是「前缀查询」：`@`/`#`/`$`/`*`/`/` 开头不参与拼音兜底（这是有意为之）。

---

## MDK 工程（ForgeGradle，推荐）

根目录现在是**官方 MDK**（`forge-1.18.2-40.3.11-mdk.zip` 解压后整合），和离线 javac 共用同一份 `src/`：

| 文件 | 说明 |
|---|---|
| `build.gradle` / `settings.gradle` / `gradle.properties` | 官方 MDK 原样；只改了三处：`gradle.properties` 的 mod 信息、`dependencies` 里加 AE2/JEC 的 `compileOnly`、`jar` 的 manifest 加 `MixinConfigs` |
| `gradlew` / `gradlew.bat` / `gradle/wrapper/` | 官方 MDK 原样（Gradle 8.8 + ForgeGradle 6.x） |
| `libs/` | 从整合包复制来的 AE2 11.7.6、JEC 4.5.9，作为编译期依赖 |
| `src/` | 本模组源码（两种构建方式共用） |

构建：

```powershell
$env:JAVA_HOME = 'D:\Tool\Java\jdk-17.0.4+8'   # 必须是 JDK 17
.\gradlew.bat build
```

产物：`build\libs\ae2pinyincompat-1.0.0.jar`（本目录的 `dist\ae2pinyincompat-1.0.0.jar` 就是它，已实测 `BUILD SUCCESSFUL`）。

> 首次构建会下载 Gradle 8.8、ForgeGradle 6.x、Forge 1.18.2 userdev 和 mappings（几百 MB），需要联网。下载物缓存在 `GRADLE_USER_HOME` 里，本项目用 `.gradle-home\`（已 gitignore，约 800 MB，可随时删）。
> `mods.toml` 特意**不做** `processResources` 模板展开：正文里的 `$` 会破坏 Groovy 模板解析，而 `version="${file.jarVersion}"` 由 Forge 在运行时从 manifest 解析。

开发调试：

```powershell
.\gradlew.bat runClient
```

> `runClient` 默认只加载本模组；要同时加载 AE2 / JEC，把它们的 jar 放进 `run\mods\`，或取消 `build.gradle` 里 `runtimeOnly files('libs/...')` 的注释（可能还需它们各自的前置）。

---

## 离线构建（javac，备用）

不需要 Gradle / 外网：

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

脚本默认值（都可用参数覆盖）：

| 参数 | 默认值 |
|---|---|
| `-JdkHome` | `D:\Tool\Java\jdk-17.0.4+8` |
| `-ForgeLibs` | 实例的 `.minecraft\libraries` |
| `-ModsDir` | 实例的 `mods` 目录（自动按文件名找 AE2 与 JEC） |
| `-ModVersion` | `1.0.0` |

> 构建脚本会把 AE2 / JEC 复制成 `build\deps\ae2.jar`、`build\deps\jecharacters.jar` 再编译——
> 因为 javac 17 读 `@argfile` 用的是系统编码，中文路径会乱码。

产物：`dist\ae2pinyincompat-1.0.0.jar`。

---

## 文件结构

```text
pingyin_search_for_vault/
├─ PLAN.md                    # 原因分析 + 方案对比 + 设计文档
├─ README.md                  # 本文件
├─ JEC-反馈文案.md            # 给 JEC 的 issue 文案（可直接提交）
├─ build.ps1                  # 离线构建脚本
├─ dist/
│  └─ ae2pinyincompat-1.0.0.jar
└─ src/main/
   ├─ java/com/treasurehunter/ae2pinyincompat/
   │  ├─ Ae2PinyinCompat.java          # @Mod 入口（空实现）
   │  ├─ PinyinSupport.java            # 环境判断 / 查询过滤 / 异常保护
   │  ├─ PinyinMatcher.java            # 唯一接触 JEC 的类（懒加载）
   │  ├─ PinyinFallbackPredicate.java  # 「原谓词 OR 拼音」组合谓词
   │  └─ mixin/RepoSearchMixin.java    # 核心注入
   └─ resources/
      ├─ META-INF/mods.toml
      ├─ ae2pinyincompat.mixins.json
      └─ ae2pinyincompat.refmap.json
```

---

## 已知限制

1. `@中文模组名`、`#tooltip` 这类**前缀查询**不做拼音（保持 backport 语义）。
2. AE2 **接口终端**（Interface Terminal）的物品名过滤在 `itemStackMatchesSearchTerm` 里，不在 JEC 的改写名单中，本模组 1.0 **未覆盖**（见 `PLAN.md` 第 15.1 条，可作为 1.1 扩展）。
3. 多音字/生僻字命中范围由 JEC 词典决定，与 JEI 表现一致。

---

## 卸载

删除 `mods\ae2pinyincompat-1.0.0.jar` 即可。无配置文件、无存档数据、无服务端残留。
