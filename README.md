# 投影图腾 Schematic Totem（Fabric 多版本）

把**不死图腾**的模型换成任意投影文件的**微缩模型**：Litematica 的 `.litematic`，也支持 Sponge `.schem` 和原版结构 `.nbt`。
纯客户端：单人和服务器都能用，服务器不用装。其他玩家看到的仍是原版图腾，除非他们也装了这个模组并加载了同一个文件。

1.3.0 为 Minecraft **1.20 至 26.3 的 24 个正式版**分别构建独立 jar。请安装文件名中与你游戏版本完全一致的一个文件。
成品位于 `dist/1.3.0/`，构建输出位于 `build/versions/<MC版本>/libs/`。
依赖：Fabric Loader ≥ 0.19.5、该游戏版本的 Fabric API；可选 Mod Menu（提供“配置”按钮）。
Java：1.20–1.20.4 需要 Java 17 或更新；1.20.5–1.21.11 需要 Java 21 或更新；26.x 需要 Java 25 或更新。

版本、分支和验证范围见 [多版本说明](docs/多版本说明.md)。下文的历史性能数据与 Punchy、Sodium、Iris、ImmediatelyFast 联用记录来自原 1.21.11 版，其他版本的第三方模组组合仍需分别验证。

## 快速上手

1. 把 `.litematic` 文件拖进游戏窗口（游戏中或任意界面都行）。文件会被复制到 `schematics/`，后台生成模型，聊天栏会提示完成。
2. 打开设置界面调整朝向和姿势。下面三种方式任选：
   - `/totemmodel gui`；
   - 模组菜单里本模组的“配置”按钮；
   - 在 控制 → 按键 里给“打开投影图腾设置”绑一个键。

## 设置界面

左边三页设置，右边是三维预览。在世界里打开时，背景不模糊，手里的图腾会**实时**跟着设置变化。

| 页 | 内容 |
|---|---|
| **模型**（每个投影单独保存） | 选择投影文件；**正面朝向**（自动 / 南 / 西 / 北 / 东 / 上 / 下）；左右转动、前后倾斜、侧向倾斜；大小；**精细度**（自动、500–48000 面，或拉到最右的**完全还原**；松手半秒后自动重新生成）；位置（居中 / 底部站立）；重置 |
| **姿势**（所有投影共用） | 选一个场景（物品栏、第一人称左右手、**第一人称左右手（Punchy 等模组）**、第三人称左右手、掉落物、展示框、图腾触发动画、头部、展示架），调旋转 X/Y/Z、平移 X/Y/Z、缩放；**左右手同步**；重置 |
| **常规** | 总开关、其他实体手里的也替换、面数预算、远处细节、剔除封闭内部、显示箱子/床/告示牌、发光方块、草和水的颜色 |

**三维预览**
- 可以切换“物品栏 / 展示框 / 触发 / 掉落”四种显示。
- 拖动旋转，滚轮缩放，双击复位。
- 下方“视角”按钮可在第一人称、背后、正面之间切换，用来看第三人称手持的效果。

**姿势页**
- 打开时自动选中手上图腾**正在用的那个场景**（比如装了 Punchy 时是“第一人称 · 右手（Punchy 等模组）”）。
- 预览框下方会提示：绿色“✔ 手上的图腾正在用这个姿势”；黄色表示正在用的是另一个场景，并写出是哪个。
- **左右手同步**（默认开）：改一只手，另一只手跟着变，两手对称。

**精细度：完全还原**
- 最高一级就是投影本身，1:1，一个方块对一个方块，不做任何缩放或重采样，面数不设上限；远处用的三级仍按它的 1/4、1/16、1/64。
- 小雕像本来就是 1:1（蓝色大肥鱼“自动”就是 1:1，5750 面），选不选都一样；大建筑会多出很多面，见下面“性能”。
- 安全上限：1:1 超过 100 万面，或投影的包围盒超过 1 亿格时，改用能放下的最高精度，并在聊天栏说明。超过 15 万面时也会提醒可能掉帧。

“正面朝向”选“自动”时，模组会自己猜建筑的正面：
- 平铺的地图画朝上，一格厚的墙朝向它的薄边方向；
- 人物、雕像和大多数建筑左右对称，正面在对称面的法线方向上；
- 再从剩下的面里，挑颜色变化最多的那一面（脸、门窗）。

蓝色大肥鱼猜出来是“东”，与实际一致。猜错时点一下“正面朝向”就能换。

## 和 Punchy 等第一人称模组一起用

Punchy 会隐藏原版的第一人称手臂，自己画手臂，再用**第三人称**的方式画手里的物品。
所以装了 Punchy 时，“第一人称”姿势不起作用，以前改“第三人称”反而会影响第一人称。1.2.0 起：
- 模组能识别这种情况，第一人称改用单独的姿势“第一人称 · 左/右手（Punchy 等模组）”；第三人称视角仍用“第三人称”姿势，两者互不影响。
- 默认值由游戏里实测的 Punchy 手部坐标算出：模型立在手掌上，正面朝向自己，向画面中间转 20°。
- 没装这类模组时，这两个姿势不会被用到，原来的第一人称照旧。

## 命令一览（都是客户端命令，服务器不需要装模组）

| 命令 | 作用 |
|---|---|
| `/totemmodel` 或 `/totemmodel info` | 查看当前投影、尺寸、各级面数、朝向和设置 |
| `/totemmodel gui` | 打开图形设置界面 |
| `/totemmodel load <文件>` | 使用一个投影；Tab 补全 `schematics/` 里的文件，也可以写绝对路径 |
| `/totemmodel list` | 列出所有投影文件，点击即可使用 |
| `/totemmodel clear` | 恢复原版不死图腾 |
| `/totemmodel on` / `off` | 开启 / 关闭替换 |
| `/totemmodel reload` | 重新读取配置文件和投影文件（手动改过文件后用） |
| `/totemmodel front <auto/south/west/north/east/up/down>` | 当前投影的正面朝向 |
| `/totemmodel turn <左右> [前后] [侧向]` | 当前投影额外转动的角度（度） |
| `/totemmodel size <倍数>` | 当前投影在手里、展示框、地上的大小（物品栏图标总是自动撑满格子） |
| `/totemmodel anchor <center/bottom>` | 居中，或者“站”在底部 |
| `/totemmodel detail <auto/面数/full>` | 当前投影的**精细度**：自己的面数上限（如 `detail 16000` 更精细，`detail 2000` 更省性能）；`auto` 用全局预算；`full` 是**完全还原**（1:1，不缩放） |
| `/totemmodel pose <场景> rotation <x> <y> <z>` | 某个场景的旋转（度） |
| `/totemmodel pose <场景> translation <x> <y> <z>` | 某个场景的平移（单位 1/16 格） |
| `/totemmodel pose <场景> scale <倍数>` | 某个场景的缩放 |
| `/totemmodel pose <场景> reset` | 恢复这个场景的默认姿势 |
| `/totemmodel budget <面数>` | 全局面数预算（没单独设精细度的投影都用它），默认 8000 |
| `/totemmodel interior <true/false>` | 剔除封闭空间内部的面（默认开） |
| `/totemmodel blockentities <true/false>` | 显示箱子、床、告示牌、旗帜、头颅、潜影盒、饰纹陶罐、潮涌核心 |
| `/totemmodel biome <群系>` | 草、树叶、水的颜色按哪个群系算，默认平原 |
| `/totemmodel others <true/false>` | 其他玩家、生物、盔甲架手里的图腾也替换（展示框和掉落物总是替换） |
| `/totemmodel name "名字" <文件>` | 在铁砧里改成这个名字的图腾显示另一个投影（中文名要加引号） |
| `/totemmodel unname "名字"` | 取消名字绑定 |
| `/totemmodel folder` | 打开 `schematics` 文件夹 |

`/totemmodel pose` 的 `<场景>` 可以是：
- `gui`
- `firstperson_right`、`firstperson_left`
- `firstperson_mod_right`、`firstperson_mod_left`（Punchy 等模组接管第一人称时）
- `thirdperson_right`、`thirdperson_left`
- `ground`
- `fixed`（展示框）
- `totem_pop`（触发动画）
- `head`
- `on_shelf`

所有设置保存在 `config/schematictotem.json`。

## 默认姿势（2026-11-04 在游戏里逐个场景截图调出来的）

- **物品栏**：3/4 侧面，正面朝外，按模型的**实际轮廓**撑满格子（不是按包围盒），所以人物、圆形建筑的图标也够大。
  用原版“平面物品”的正面打光：原版方块用的 3D 打光光从上方和后方来，会让从正面看的模型暗到看不清。
- **第一人称**：立着、正面朝向自己，稍微侧转，比手持方块小一些、位置高一些，整个模型都在画面里。
- **第一人称（Punchy 等模组）**：同上，模型立在 Punchy 画的手掌上；由测到的手部坐标算出（Punchy 的手几乎不旋转，只侧倾约 10°），左右手数值相同。
- **第三人称**：竖直站在手上，正面朝前，左右手对称。
  - 原版对左手的处理是镜像，套在立体模型上会让左手那个背对别人，所以左手默认用相反的转向。
- **展示框和触发动画**：正面朝外，撑满画面。

## 性能设计

1. **后台生成**：读文件、生成模型都在一个低优先级后台线程。71 万方块的浮空岛约 0.9 秒，期间不卡游戏。
2. **只留看得见的面**：
   - 被不透明方块挡住的面不画；
   - 树叶之间的面不画；
   - 从外面进不去的封闭空间（掏空的山体、没窗的房间）不画。
3. **面数预算 + 4 级细节**：
   - 最高级不超过 8000 面，其余三级是 1/4、1/16、1/64。
   - 超出预算时按 2^(1/6) 的步长逐级重采样，每一格选“占格体积 × 是否在表面”最大的方块。
4. **按屏幕大小选细节**：
   - 别人手里、展示框里、地上的图腾，选每格在屏幕上至少 3 像素的最细一级（“远处细节”可调）。
   - 物品栏、第一人称、触发动画用最高级。
5. **物品栏零开销**：原版 1.21.11 会把物品栏图标画进一张缓存图集；模型不标记“动画”，所以每个图标只画一次。
6. **每帧直接写顶点**：
   - 模型预先展开成扁平数组，每帧只做一次矩阵乘法和批量写顶点，没有逐面对象。
   - 用 Sodium 时会把水、岩浆等动画贴图报告给 Sodium，让它们照常动起来。
7. **不闪烁**：方块图集带 mipmap 采样。
8. **资源包重载**：重载后旧模型立即停用，后台自动重新生成。

### 实测

离线测试用你 `schematics/` 里的 27 个投影：
- 方块数与 Litematica 元数据完全一致；
- 新写顶点方式约 40 ns/面（原版逐面方式 55–65 ns/面）。

### 游戏内测试

条件：开发环境，无 Sodium，同一台机器。测的是渲染线程每帧 CPU。场景：双手各一个图腾，面前 2 个展示框和 1 个掉落的图腾。

| 场景 | 每帧 CPU |
|---|---|
| 原版图腾 | 0.51 ms |
| 蓝色大肥鱼（最高级 5750 面），改用直接写顶点之前 | 6.7 ms |
| 蓝色大肥鱼，现在 | 1.7–2.0 ms |
| 71 万方块的浮空岛（最高级 7115 面） | 1.7 ms |

- 现在双手那一项里，第一人称双手共 11500 面占 1.13 ms，上传和绘制约 0.37 ms。
- 展示框和地上的图腾按屏幕大小自动用了 449 面那一级。
- 只在副手拿一个图腾，大约是上面一半。
- 还想更省：把精细度调低，例如 `/totemmodel detail 2000`。
- 装 Punchy 时（双手各一个大肥鱼）：2.4 ms，原版图腾 0.63 ms。

### 完全还原的代价

最高一级的面数（“自动”= 8000 面预算下的面数 → 完全还原），离线统计，游戏里略有出入：

| 投影 | 自动 | 完全还原 |
|---|---|---|
| 蓝色大肥鱼 | 5573（已是 1:1） | 5573 |
| 【进阶】特莉波卡 | 6505（1:2.24） | 59537 |
| 月亮魔女小屋 | 7411（1:1.26） | 21634 |
| 雾月小教堂 | 7374（1:2.83） | 277445 |
| 71 万方块的浮空岛 | 6661（1:5.66） | 353956（游戏里 371477，生成 1.2 秒） |
| 随机算法生成（60 万方块） | 7456（1:8） | 超过上限，用 1:1.26（887909 面） |

- 每帧 CPU 大约随面数线性增长：浮空岛完全还原、双手各拿一个（共 74 万面）时每帧 49 ms，约 20 帧。
- 几万面以内的雕像、房子可以放心用。

## 已知限制

- 墙上的告示牌、旗帜、头颅，以及悬挂告示牌、铜傀儡雕像，不显示；告示牌文字、旗帜图案不显示。
- 投影里的实体（盔甲架、画、展示框）不显示。
- 液体画成方块盒子，没有流动坡度；物品栏里的水和岩浆是静止的（为了让物品栏能缓存）。
- 手里的微缩模型不显示附魔光效，也不参与“发光”效果的轮廓。
- 不支持 1.12 及更早的 MCEdit `.schematic`。

## 构建与测试

构建 Gradle 时使用 JDK 21；构建 26.x 时还需安装 JDK 25，并让 Gradle 工具链能够发现它。
可在本机 `gradle.properties` 设置 `org.gradle.java.home` 与 `org.gradle.java.installations.paths`；源码包会过滤这些机器路径。
源码已包含 Gradle Wrapper、Mod Menu 编译接口和三种格式的原创测试样例。首次构建需联网下载 Gradle、Minecraft 与 Fabric 依赖。

Windows PowerShell 示例（Linux / macOS 将 `./gradlew.bat` 换成 `./gradlew`）：

```powershell
./gradlew.bat build '-Pmc=1.20.1'
./gradlew.bat build '-Pmc=26.3' '-Porg.gradle.java.installations.paths=<JDK25路径>,<JDK21路径>'
./scripts/build-matrix.ps1                    # 全 24 版本构建、测试和 jar 元数据检查
./gradlew.bat sourceRelease                  # 单个多版本源码 ZIP
./gradlew.bat runSmokeClient '-Psmoke' '-Pmc=1.20.1' # 真实客户端：载入样例、预览、截图后退出
./gradlew.bat runClientGameTest '-Pmc=1.21.11' '-PtestFile=src/test/resources/fixtures/original-house.litematic'
```

- `-Pmc` 覆盖分支默认版本；完整锁定的依赖在 `versioning/versions.json`。`mc/<版本>` 分支默认构建对应版本。
- 每版默认运行三项 JUnit 测试：三种格式一致性、读取体积上限、读取与模型生成管线。原创样例不是个人投影或游戏存档。
- `-Pschematics=<目录>` 可让管线额外读取自己的投影。自备文件不进入发布包。
- `build-matrix.ps1 -Minecraft '1.20','26.3'` 可选择版本；`-GradleArgument '<参数>'` 可传入工具链路径；脚本自动读取系统代理，可用 `-NoSystemProxy` 关闭。
- `runSmokeClient -Psmoke` 只在独立的 `run/compat/<版本>/` 写配置、截图和 `compatibility-smoke.json`，不会加载个人存档。它验证真实资源模型和 GUI 绘制；世界内完整场景测试目前使用 1.21.11 的 `runClientGameTest`。
- 1.21.11 游戏内测试还支持 `-PbigTestFile=<大型投影>`、`-Psweep=true`、`-PwithMods=<jar>`；参数的相对路径均相对项目根目录。

源码发布包为 `build/versions/<默认版本>/distributions/schematic-totem-1.3.0-multiversion-src.zip`。
包含共享源码、API 差异层、依赖锁、测试、构建脚本、文档、许可证及 Mod Menu 编译接口；不包含 Minecraft、个人模组、存档、缓存、反编译资料或本机 JDK/代理路径。
采用稳定文件顺序和固定时间戳；`gradlew` 保留 Unix 可执行权限。

## 许可

MIT，见 `LICENSE`。

`libs/modmenu/` 内是各版本 TerraformersMC Mod Menu 的编译接口（MIT，每个 jar 旁附上游原样许可）。
它只在编译时提供“配置”按钮的接口，不会打包进成品。依赖说明见 `libs/README.md`。

---

**English summary.** Client-only Fabric mod with separate artifacts for all 24 stable Minecraft releases from 1.20 through 26.3: the Totem of Undying shows a miniature of a `.litematic`
(also `.schem` / structure `.nbt`). Drop the file onto the game window or use `/totemmodel load <file>`.

**Settings**: `/totemmodel gui`, the Mod Menu config button, or a keybind.
- Per-schematic: front side (auto-detected), turn, size, anchor.
- Per-context poses for the inventory, both hands in first and third person, ground, item frame, totem pop, head and shelf.
- First-person mods like Punchy draw held items with the third-person context; this is detected and gets its own
  first-person poses (defaults computed from Punchy's measured hand frame). The pose tab opens on the pose in use.
- Detail "exact" (`/totemmodel detail full`): the finest level is the build 1:1, up to 1M quads.
- A 3D preview, with your own hand updating live behind the screen.

**Performance**:
- Loading and baking run off-thread.
- Hidden and sealed-interior faces are culled.
- Four detail levels fit a quad budget; distant or small totems pick a level by size on screen.
- The GUI atlas caches inventory icons.
- Vertices are written directly from flat arrays.
