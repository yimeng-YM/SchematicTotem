# 编译依赖

`modmenu-17.0.0.jar` 为 TerraformersMC 的 Mod Menu 17.0.0，仅通过 `modCompileOnly` 提供配置按钮接口。
该 jar 随源码包提供，不会嵌入 Schematic Totem 成品；游戏运行时可选安装 Mod Menu。

- 上游：https://github.com/TerraformersMC/ModMenu
- 许可：MIT，见 jar 内的 `LICENSE`，并原样另附于 `LICENSE-ModMenu.txt`。

其他构建依赖通过 Gradle 仓库下载；不要把 Minecraft、Fabric、个人游戏模组或反编译代码复制到源码发布包。
