"""Collect an already verified full matrix; never label missing checks as passed."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import shutil
import zipfile


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def copy_file(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if source.resolve() != destination.resolve():
        shutil.copy2(source, destination)


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--matrix", type=Path, default=root / "build/verification/matrix/matrix.json")
    parser.add_argument("--smoke", type=Path, default=root / "build/verification/smoke/matrix.json")
    parser.add_argument("--source", type=Path, default=root / "build/versions/1.21.11/distributions/schematic-totem-1.3.0-multiversion-src.zip")
    parser.add_argument("--game-test", type=Path)
    args = parser.parse_args()
    versions = read_json(root / "versioning/versions.json")
    builds = {r["minecraft"]: r for r in read_json(args.matrix)}
    clients = {r["minecraft"]: r for r in read_json(args.smoke)}
    require(set(builds) == set(versions) == set(clients), "Both reports must cover the exact complete version matrix.")
    require(args.source.is_file(), "Build sourceRelease before packaging.")
    for mc, config in versions.items():
        build, client = builds[mc], clients[mc]
        require(build["status"] == "passed" and build["tests"] >= 3 and build["skipped"] == 0, f"Build/test not verified: {mc}")
        require(client["status"] == "passed" and client["report"].get("guiActions") == 11, f"Full GUI smoke not verified: {mc}")
        artifact = Path(build["artifact"])
        require(digest(artifact) == build["sha256"], f"Artifact changed after verification: {mc}")
        with zipfile.ZipFile(artifact) as jar:
            metadata = json.loads(jar.read("fabric.mod.json"))
            require(metadata["version"] == f"1.3.0+mc{mc}" and metadata["depends"]["minecraft"] == f"={mc}", f"Wrong metadata: {mc}")
            for name in jar.namelist():
                if name.endswith(".class"):
                    require(name.startswith("dev/yimeng/schematictotem/"), f"Unexpected bundled code: {name}")
                    require(int.from_bytes(jar.read(name)[6:8], "big") == config["java"] + 44, f"Wrong Java target: {mc}/{name}")

    with zipfile.ZipFile(args.source) as archive:
        names = archive.namelist()
        top = "schematic-totem-1.3.0-multiversion-src/"
        require(all(n.startswith(top) and ".." not in Path(n).parts for n in names), "Invalid source archive layout.")
        require(top + ".gitattributes" in names and top + ".gitignore" in names, "Source Git settings are absent.")
        require(all(not any(p in {".git", ".gradle", "build", "run", "research", "dist", "test-schematics"} for p in Path(n[len(top):]).parts) for n in names), "Local files leaked into source archive.")
        properties = archive.read(top + "gradle.properties").decode("utf-8")
        allowed = {"org.gradle.jvmargs", "org.gradle.parallel", "minecraft_version", "mod_version", "maven_group", "archives_base_name"}
        require(all(line.split("=", 1)[0].strip() in allowed for line in properties.splitlines() if line.strip() and not line.lstrip().startswith("#")), "Nonportable Gradle properties in archive.")
        for config in versions.values():
            dependency = config["modmenu"]
            data = archive.read(top + dependency["file"])
            require(hashlib.sha512(data).hexdigest() == dependency["sha512"], "Mod Menu dependency hash mismatch.")
            license_name = str(Path(dependency["file"]).with_suffix(".LICENSE.txt")).replace("\\", "/")
            require(top + license_name in names, "Missing third-party license.")
            license_data = archive.read(top + license_name)
            with zipfile.ZipFile(io.BytesIO(data)) as dependency_jar:
                require(any(dependency_jar.read(n) == license_data for n in dependency_jar.namelist() if Path(n).name.lower().startswith("license")), "Third-party license text was altered.")

    release = root / "dist/1.3.0"
    evidence = release / "验证记录"
    evidence.mkdir(parents=True, exist_ok=True)
    rows = []
    for mc, config in versions.items():
        build, client = builds[mc], clients[mc]
        artifact = Path(build["artifact"])
        copy_file(artifact, release / artifact.name)
        copy_file(Path(build["log"]), evidence / "build" / f"{mc}.log")
        copy_file(args.smoke.parent / f"{mc}.log", evidence / "client" / f"{mc}.log")
        screenshots = sorted(Path(client["screenshot"]).parent.glob("*.png"), key=lambda p: p.stat().st_mtime, reverse=True)[:3]
        require(len(screenshots) == 3, f"Missing GUI screenshots: {mc}")
        images = []
        for name, image in zip(["gui", "pop", "files"], reversed(screenshots)):
            target = evidence / "screenshots" / mc / f"{name}.png"
            copy_file(image, target)
            images.append(target.relative_to(evidence).as_posix())
        test_root = artifact.parents[2] / f"build/versions/{mc}/test-results/test"
        for report in test_root.glob("TEST-*.xml"):
            copy_file(report, evidence / "junit" / mc / report.name)
        rows.append({"minecraft": mc, "java": config["java"], "branch": f"mc/{mc}", "build": "passed", "tests": build["tests"],
                     "skipped": 0, "client": "passed", "guiActions": client["report"]["guiActions"], "screenshots": images,
                     "artifact": artifact.name, "sha256": build["sha256"]})
    game_status = "未提供游戏内场景报告"
    if args.game_test:
        game = read_json(args.game_test)
        require(game.get("status") == "PASS" and game.get("failures") == [] and game.get("sceneItemFrames") == 2, "The 1.21.11 world-scene test did not pass.")
        copy_file(args.game_test, evidence / "1.21.11-world-scene.json")
        game_status = "1.21.11 世界内场景测试通过：两只手的姿势与独立缩放、物品栏、展示框（确认两个实体）、掉落物和触发动画。"
    (evidence / "matrix.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    copy_file(args.source, release / args.source.name)
    copy_file(root / "docs/多版本说明.md", release / "多版本说明.md")
    summary = ["# 1.3.0 验证记录", "", "全部 24 个正式版本均通过生产构建、每版三项 JUnit 测试（共 72 项，无跳过），以及真实客户端 GUI 检查。",
               "客户端使用真实资源模型加载原创样例，遍历设置三页与四种预览模式，打开文件选择界面并保存三张截图后退出。", "", game_status, "",
               "| Minecraft | Java 字节码目标 | 分支 | JUnit | 客户端 |", "|---|---|---|---|---|"]
    summary += [f"| {r['minecraft']} | {r['java']} | `{r['branch']}` | {r['tests']} 通过 | 通过 |" for r in rows]
    summary += ["", "运行环境：Windows；1.x 客户端使用 Java 21，26.x 使用 Java 25。Java 17 为旧版本编译目标，未另用 Java 17 客户端重测。",
                "检查未覆盖每一版本的全部世界内场景、第三方模组组合或所有驱动。历史性能数值不是本次跨版本性能承诺。",
                "日志、JUnit XML、逐版截图和精确 SHA-256 见 `验证记录/`。原创样例、重现脚本和依赖锁包含在源码包中。", ""]
    (release / "验证记录.md").write_text("\n".join(summary), encoding="utf-8")
    files = sorted(p for p in release.iterdir() if p.is_file() and p.suffix in {".jar", ".zip", ".bundle"})
    (release / "SHA256SUMS.txt").write_text("".join(f"{digest(p)}  {p.name}\n" for p in files), encoding="utf-8")
    proof = sorted(p for p in evidence.rglob("*") if p.is_file() and p.name != "SHA256SUMS.txt")
    (evidence / "SHA256SUMS.txt").write_text("".join(f"{digest(p)}  {p.relative_to(evidence).as_posix()}\n" for p in proof), encoding="utf-8")
    print(f"Packaged {len(versions)} verified Minecraft targets: {release}")


if __name__ == "__main__":
    main()
