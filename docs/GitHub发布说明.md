# GitHub 发布流程

仓库：<https://github.com/yimeng-YM/SchematicTotem>。默认分支 `main`，正式版标签使用 `v<模组版本>`。

1. 运行 `scripts/build-matrix.ps1`、`scripts/smoke-matrix.ps1` 与 1.21.11 世界场景测试；确认完整矩阵、无跳过及成品元数据正确。
2. 运行 `sourceRelease`；解压源码独立构建代表版本，并核对生产 jar 和源码重新打包的一致性。
3. 使用 `scripts/package-release.py` 汇总已验证的本地目录。维护版本分支的默认目标，导出并恢复检查 Git bundle。
4. 运行 `python scripts/prepare-github-release.py`。它生成逐版 jar、源码、仓库备份、清理本机路径后的验证 ZIP、完整发布 ZIP、安装说明、更新日志、清单与校验和。此脚本不联网、不读取 GitHub 凭据。
5. 将源码、全部版本分支和正式标签推送到 GitHub；确认 Actions 的 Linux 构建矩阵通过。
6. 创建草稿 Release，上传全部附件，核对远端文件大小与 SHA-256 后发布为正式版本。

完整发布 ZIP 包含 `mods/`、`source/`、`verification/`、`docs/`、安装说明、`MANIFEST.json`、分支清单和校验和。外部的 `SHA256SUMS.txt` 覆盖所有 Release 下载附件；ZIP 内清单覆盖包内文件，避免归档文件与其自身哈希之间的循环引用。

不要把不同 Minecraft 版本的 jar 合并为一个兼容声明，也不要把所有 jar 同时安装到同一个游戏实例。验证附件应明确区分逐版自动测试、GUI 检查与已执行的世界内场景，不扩大发布说明中的实测范围。
