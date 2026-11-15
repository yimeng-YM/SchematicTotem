# 编译依赖

`modmenu/` 是 TerraformersMC 的 Mod Menu 各版本原始 jar，仅用于编译配置按钮接口，不会嵌入 Schematic Totem 成品。游戏中可选安装对应版本的 Mod Menu。

- 上游：https://github.com/TerraformersMC/ModMenu
- 许可：MIT；每个 jar 旁的 `.LICENSE.txt` 原样保留上游许可。
- 锁定版本、上游下载地址和 SHA-512：`versioning/versions.json` 中的 `modmenu` 项。
- 根目录旧的 `modmenu-17.0.0.jar` 与 `LICENSE-ModMenu.txt` 保留自 1.2.1，供历史版本使用；当前矩阵使用子目录中的接口。

其他依赖由 Gradle 下载。源码发布包不包含 Minecraft、Fabric 二进制、个人模组或反编译代码。
