# Schematic Totem · 投影图腾

[![Build](https://github.com/yimeng-YM/SchematicTotem/actions/workflows/build.yml/badge.svg)](https://github.com/yimeng-YM/SchematicTotem/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

将 Minecraft 不死图腾显示为投影文件生成的微缩模型。支持 Litematica `.litematic`、Sponge `.schem` 和原版结构 `.nbt`，可按投影调整朝向、尺寸、细节和显示姿势。

模组运行于 Fabric 客户端，可用于单人世界和多人服务器。显示效果由各客户端本地配置，服务器无需安装；投影文件和外观设置不会自动同步给其他玩家。

[下载发布版](https://github.com/yimeng-YM/SchematicTotem/releases/latest) · [配置与命令](docs/配置与命令.md) · [开发与贡献](.github/CONTRIBUTING.md) · [更新日志](CHANGELOG.md) · [报告问题](https://github.com/yimeng-YM/SchematicTotem/issues)

## 功能

- **投影导入**：拖放文件到游戏窗口，或通过客户端命令加载投影。
- **模型设置**：为各投影保存正面朝向、旋转、尺寸、位置和面数预算。
- **场景姿势**：分别调整物品栏、左右手持、掉落物、展示框、图腾触发动画等场景，支持左右手同步。
- **交互预览**：在设置界面旋转、缩放模型，切换预览模式；在世界中调整时可实时查看手持效果。
- **细节控制**：后台读取与模型生成、不可见面剔除、四级细节，以及受安全上限约束的 1:1 完全还原模式。
- **名称绑定**：为不同自定义物品名绑定不同投影。

## 支持版本与依赖

**1.3.0** 为 Minecraft Java Edition **1.20 至 26.3 的 24 个正式版**提供独立 jar。每个文件仅声明支持其文件名中的 Minecraft 版本；完整目标及依赖版本见 [版本配置](versioning/versions.json)。

| Minecraft 版本 | Java 最低版本 |
|---|---|
| 1.20–1.20.4 | 17 |
| 1.20.5–1.21.11 | 21 |
| 26.1–26.3 | 25 |

必需依赖：Fabric Loader **≥ 0.19.5**，以及对应 Minecraft 版本的 **Fabric API**。各版 Fabric API 最低版本由 jar 元数据和版本配置指定。

[Mod Menu](https://github.com/TerraformersMC/ModMenu) 为可选依赖，提供模组列表中的配置入口。部分显示场景仅在相应 Minecraft 版本提供该功能时可用。

## 安装

1. 为游戏实例安装 Fabric Loader 和对应版本的 Fabric API。
2. 从 [Releases](https://github.com/yimeng-YM/SchematicTotem/releases) 下载与游戏版本一致的 `schematic-totem-<模组版本>+mc<游戏版本>.jar`。
3. 将该 jar 放入游戏实例的 `mods/` 目录并启动游戏。

同一游戏实例只应安装本模组的一个对应版本。完整发布 ZIP 的 `mods/` 目录同时收录多个游戏版本的成品，应从中选择所需文件。

### 发布文件

| 文件 | 用途 |
|---|---|
| `schematic-totem-<版本>+mc<游戏版本>.jar` | 单个 Minecraft 版本的安装文件 |
| `schematic-totem-<版本>-release.zip` | 完整发布包，含成品、源码、文档与验证附件 |
| `schematic-totem-<版本>-multiversion-src.zip` | 可独立构建的完整多版本源码 |
| `schematic-totem-<版本>-branches.bundle` | 含版本分支和历史提交的离线 Git 备份 |
| `schematic-totem-<版本>-verification.zip` | 构建日志、测试结果、截图和验证报告 |
| `MANIFEST.json` / `SHA256SUMS.txt` | 版本、依赖与文件信息 / 下载附件校验和 |

## 使用

1. 将 `.litematic`、`.schem` 或结构 `.nbt` 拖入游戏窗口，等待模型生成。也可将文件放入游戏实例的 `schematics/` 目录，通过命令选择：

   ```text
   /totemmodel list
   /totemmodel load house.litematic
   ```

2. 持有不死图腾，运行 `/totemmodel gui` 打开设置。也可使用 Mod Menu 的配置按钮，或在「选项 → 控制 → 按键绑定」中设置打开配置界面的快捷键。
3. 在「模型」「姿势」「常规」三页中调整外观。预览支持拖动旋转、滚轮缩放和双击复位。

清除默认投影：

```text
/totemmodel clear
```

名称绑定可单独管理；暂时关闭所有模型替换可使用 `/totemmodel off`。

配置保存在游戏实例的 `config/schematictotem.json`。完整命令、姿势标识、名称绑定及精细度说明见 [配置与命令](docs/配置与命令.md)。

## 性能与限制

模型复杂度、显示数量和细节设置会影响性能。大型投影可降低面数预算或调整远处细节；完全还原模式可能显著增加顶点和绘制开销。

- 1:1 完全还原受 **100 万面 / 1 亿格**的生成上限约束，超限时会使用可容纳的精度并提示。
- 投影中的实体不参与渲染；告示牌文字、旗帜图案及部分墙面、悬挂式特殊方块存在显示限制。
- 液体使用简化盒状几何；物品栏中的液体贴图保持静止。
- 微缩模型不显示附魔光效或发光轮廓。
- 不支持旧式 MCEdit `.schematic` 格式。

模组为接管第一人称物品渲染的模组提供独立姿势。与 Punchy、Sodium、Iris、ImmediatelyFast 的历史联用记录来自 1.21.11；其他版本和组合应分别验证。

## 构建

使用 **JDK 21** 运行 Gradle；构建 26.x 时还需可被 Gradle 工具链发现的 **JDK 25**。仓库包含 Gradle Wrapper，无需单独安装 Gradle；首次构建需要联网下载依赖。

```bash
git clone https://github.com/yimeng-YM/SchematicTotem.git
cd SchematicTotem
./gradlew build -Pmc=1.21.11
```

Windows PowerShell：

```powershell
./gradlew.bat build '-Pmc=1.21.11'
```

成品位于 `build/versions/<Minecraft版本>/libs/`。`-Pmc` 可指定 [版本配置](versioning/versions.json) 中的任一目标；省略时使用分支的默认版本。

`main` 维护当前多版本源码，`mc/<版本>` 提供对应的默认构建目标。源码分层、工具链配置、测试命令和提交要求见[开发与贡献](.github/CONTRIBUTING.md)及[多版本维护说明](docs/多版本说明.md)。

## 测试与问题反馈

GitHub Actions 在 Linux 上执行完整版本矩阵的构建、JUnit 测试、成品元数据与字节码检查。Release 验证附件另含 Windows 客户端预览、1.21.11 世界场景和源码独立重建记录。

这些检查不覆盖所有游戏场景、第三方模组组合或显卡驱动。报告问题时请提供 Minecraft、模组、Fabric Loader、Fabric API 版本，重现步骤、相关日志及其他模组列表；共享投影文件前应确认分享权限。

问题与功能建议请提交至 [GitHub Issues](https://github.com/yimeng-YM/SchematicTotem/issues)。代码贡献请参阅 [贡献指南](.github/CONTRIBUTING.md)。

## 许可证

项目采用 [MIT License](LICENSE)。`libs/modmenu/` 中的第三方编译接口保留各自上游许可证，且不嵌入本模组的运行 jar；参阅[编译依赖说明](libs/README.md)。

## English summary

Schematic Totem is a client-side Fabric mod that renders schematic builds in place of the Totem of Undying. It supports Litematica `.litematic`, Sponge `.schem`, and vanilla structure `.nbt` files, with per-build appearance settings, per-context poses, and configurable detail levels.

Version 1.3.0 provides separate jars for 24 stable Minecraft releases from 1.20 through 26.3. Install the jar matching the exact game version together with Fabric Loader and Fabric API. See [Releases](https://github.com/yimeng-YM/SchematicTotem/releases), the [configuration reference](docs/配置与命令.md), and the [contributor guide](.github/CONTRIBUTING.md).
