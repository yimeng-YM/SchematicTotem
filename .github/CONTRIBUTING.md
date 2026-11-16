# 开发与贡献

[项目首页](../README.md) · [多版本维护](../docs/多版本说明.md) · [GitHub 发布流程](../docs/GitHub发布说明.md)

## 开发环境

- JDK 21：运行 Gradle，并构建 1.x 目标。
- JDK 25：构建 26.x 目标的编译器工具链。
- PowerShell 7：运行仓库中的矩阵脚本；单版本 Gradle 命令也可在 Linux / macOS 上执行。
- Python 3.10 或更新版本：运行测试样例生成和发布整理脚本。

使用仓库内的 Gradle Wrapper。首次构建需要联网下载 Minecraft、Fabric 和其他依赖；Mod Menu 编译接口及其上游许可证位于 `libs/modmenu/`。

## 分支与源码

`main` 维护当前源码，`mc/<版本>` 分支设置对应的 `minecraft_version` 默认值。任一当前版本分支均可通过 `-Pmc=<版本>` 覆盖目标。历史基线位于 `baseline/1.21.11` 和 `v1.2.1-mc1.21.11`。

| 路径 | 职责 |
|---|---|
| `src/main/` | 1.21.11 基线代码与共享资源 |
| `ports/modern/` | 26.x 使用 Mojang 名称的基线 |
| `ports/` 其他目录 | API 家族或具体版本的差异覆盖 |
| `versioning/versions.json` | Minecraft、Java、Fabric 和 Mod Menu 依赖锁 |
| `versioning/prepare.gradle` / `transform.gradle` | 源码分层组合与版本转换 |
| `src/test/` | 离线测试与原创投影样例 |
| `src/smoke/` | 真实客户端 GUI 检查 |
| `src/gametest/` | 1.21.11 世界内场景检查 |

生成源码位于 `build/versions/<版本>/generated/`。修改应落在基线、差异层或转换规则中，不应直接编辑生成目录。通用逻辑修复应同时检查 `src/main/` 与 `ports/modern/`，并验证受影响的 API 家族。

## 构建

Linux / macOS：

```bash
./gradlew build -Pmc=1.21.11
./gradlew build -Pmc=26.3 '-Porg.gradle.java.installations.paths=/path/to/jdk-21,/path/to/jdk-25'
```

Windows PowerShell：

```powershell
./gradlew.bat build '-Pmc=1.20.1'
./gradlew.bat build '-Pmc=26.3' '-Porg.gradle.java.installations.paths=<JDK21路径>,<JDK25路径>'
```

安装路径仅应保存在本机环境或本机 Gradle 配置中。生产成品位于 `build/versions/<版本>/libs/`；选择不带 `-dev`、`-sources` 后缀的 jar。

## 测试

`build` 默认执行三项 JUnit 检查：三种投影格式的一致性、读取体积上限、读取与模型生成管线。样例位于 `src/test/resources/fixtures/`；管线测试可用 `-Pschematics=<目录>` 指定其他投影。

PowerShell 7：

```powershell
./scripts/build-matrix.ps1
./scripts/build-matrix.ps1 -Minecraft '1.20.1','26.3'
./scripts/smoke-matrix.ps1
./gradlew.bat runSmokeClient '-Psmoke' '-Pmc=1.21.11'
./gradlew.bat runClientGameTest '-Pmc=1.21.11' '-PtestFile=src/test/resources/fixtures/original-house.litematic'
```

矩阵脚本支持 `-GradleArgument` 传入工具链参数，并自动读取系统代理；`-NoSystemProxy` 可禁用该行为。单版本命令在 Linux / macOS 上使用 `./gradlew`。

- 构建矩阵结果：`build/verification/matrix/`。
- GUI 检查结果：`run/compat/<版本>/`；检查会打开游戏窗口，载入样例、遍历设置与预览、截图后退出。
- 世界场景测试仅配置于 1.21.11，支持 `-PbigTestFile=<投影>`、`-Psweep=true` 和 `-PwithMods=<jar>` 等附加选项。
- GitHub Actions 在 Linux 上检查完整版本矩阵；GUI 与世界场景检查需另行执行，不能用编译结果代替。

## 提交变更

1. 从当前 `main` 创建工作分支；描述解决的问题、重现条件和预期行为。
2. 为行为变更运行相应的构建与测试；涉及渲染 API 或版本转换时覆盖受影响的版本家族。
3. 在 Pull Request 中列出目标版本、验证方式和未覆盖的场景。文档修改检查命令、相对链接与说明是否对应当前实现。
4. 新增测试投影应为原创或具备再分发许可，并注明来源与许可证。

提交内容应保持构建可移植，避免包含本机路径、存档、个人投影、缓存、访问令牌或反编译研究资料。遵循现有代码风格，避免与变更无关的大范围格式调整。

## 问题报告

通过 [GitHub Issues](https://github.com/yimeng-YM/SchematicTotem/issues) 提供版本信息、重现步骤、预期与实际结果、日志及相关截图。涉及其他模组时附上模组列表；投影文件仅在具备分享权限时提供。

## 发布

运行 `./gradlew sourceRelease` 可生成包含所有版本差异层的源码 ZIP。完整发布的验证、清单和附件整理流程见 [GitHub 发布流程](../docs/GitHub发布说明.md)。

发布标签用于对应已发布的源码与附件；后续文档更新可继续提交到开发分支。代码与文档贡献采用项目的 [MIT License](../LICENSE)。
