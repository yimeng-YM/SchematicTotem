"""Assemble GitHub assets from the verified local release, without credentials or network access."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def checksums(folder):
    files = sorted(p for p in folder.rglob("*") if p.is_file() and p.name != "SHA256SUMS.txt")
    (folder / "SHA256SUMS.txt").write_text("".join(f"{sha(p)}  {p.relative_to(folder).as_posix()}\n" for p in files), encoding="utf-8")


def archive(folder, destination, prefix):
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for path in sorted(p for p in folder.rglob("*") if p.is_file()):
            info = zipfile.ZipInfo(prefix + "/" + path.relative_to(folder).as_posix(), (1980, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(info, path.read_bytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", default="1.3.0")
    parser.add_argument("--repo", default="yimeng-YM/SchematicTotem")
    args = parser.parse_args()
    version = args.version
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise SystemExit("Expected a stable semantic version.")
    base = ROOT / "dist" / version
    stage = ROOT / "build/github-publish" / version
    output = ROOT / "dist/github" / ("v" + version)
    if stage.exists() or output.exists():
        raise SystemExit("Publication staging already exists; choose a fresh version or review the existing staging first.")
    stage.mkdir(parents=True)
    output.mkdir(parents=True)
    proof = base / "验证记录"
    rows = read_json(proof / "matrix.json")
    versions = read_json(ROOT / "versioning/versions.json")
    if len(rows) != 24 or set(r["minecraft"] for r in rows) != set(versions):
        raise SystemExit("Expected the verified complete 24-version matrix.")
    source = base / f"schematic-totem-{version}-multiversion-src.zip"
    bundle = base / f"schematic-totem-{version}-branches.bundle"
    if sha(source) != read_json(proof / "source-verification.json")["sourceArchiveSha256"]:
        raise SystemExit("Source archive no longer matches its verification.")
    if sha(bundle) != read_json(proof / "bundle-verification.json")["bundleSha256"]:
        raise SystemExit("Git bundle no longer matches its verification.")

    def redact(text):
        for old, replacement in [(str(ROOT), "${PROJECT_DIR}"), (str(Path.home()), "${USER_HOME}"), ("D:/HMCL/Java", "${JDK_DIR}")]:
            for path in {old, old.replace("\\", "/"), old.replace("/", "\\")}:
                text = text.replace(path, replacement)
        return text

    public_proof = stage / "verification"
    for path in proof.rglob("*"):
        if not path.is_file() or path.name == "SHA256SUMS.txt":
            continue
        target = public_proof / path.relative_to(proof)
        target.parent.mkdir(parents=True, exist_ok=True)
        if path.suffix in {".json", ".xml", ".log", ".md", ".txt"}:
            if path.suffix == ".json":
                def scrub(value):
                    if isinstance(value, str): return redact(value)
                    if isinstance(value, list): return [scrub(x) for x in value]
                    if isinstance(value, dict): return {k: scrub(v) for k, v in value.items()}
                    return value
                write_json(target, scrub(read_json(path)))
            else:
                target.write_text(redact(path.read_text(encoding="utf-8-sig", errors="replace")), encoding="utf-8")
        else:
            shutil.copy2(path, target)
    checksums(public_proof)
    qa = output / f"schematic-totem-{version}-verification.zip"
    archive(public_proof, qa, f"schematic-totem-{version}-verification")

    complete = stage / "complete"
    files = []
    def include(path, relative, asset=True):
        target = complete / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        if asset and path.resolve() != (output / path.name).resolve():
            shutil.copy2(path, output / path.name)
        files.append({"path": relative, "sha256": sha(path), "bytes": path.stat().st_size,
                      "githubAsset": path.name if asset else None})

    for row in rows:
        path = base / row["artifact"]
        if sha(path) != row["sha256"] or row["build"] != "passed" or row["client"] != "passed":
            raise SystemExit("A production jar is missing its verified result: " + path.name)
        include(path, "mods/" + path.name)
    include(source, "source/" + source.name)
    include(bundle, "source/" + bundle.name)
    include(qa, "verification/" + qa.name)
    include(base / "branches.json", "branches.json")
    include(ROOT / "LICENSE", "LICENSE", asset=False)
    include(ROOT / "CHANGELOG.md", "docs/CHANGELOG.md")
    include(base / "多版本说明.md", "docs/多版本说明.md", asset=False)
    include(base / "验证记录.md", "docs/验证记录.md", asset=False)
    instructions = (base / "使用说明.md").read_text(encoding="utf-8").replace(
        "`main`、`baseline/1.21.11` 与旧标签保留 1.2.1 源码。", "`main` 展示当前多版本源码，`baseline/1.21.11` 与旧标签保留 1.2.1 源码。")
    instructions = instructions.replace("`SHA256SUMS.txt` 校验本目录的生产 jar、源码 ZIP 与 Git bundle。", "`SHA256SUMS.txt` 校验完整包中的文件；GitHub Release 另附同名清单，校验所有下载附件。")
    instructions += "\n完整包的 `mods/` 同时收录 24 个版本，仅安装与你的游戏版本一致的一个 jar。源码与 Git 备份在 `source/`，验证附件在 `verification/`，说明文档在 `docs/`。\n"
    instructions = instructions.replace("./schematic-totem-1.3.0-branches.bundle", "./source/schematic-totem-1.3.0-branches.bundle")
    (complete / "README.md").write_text(instructions, encoding="utf-8")
    shutil.copy2(complete / "README.md", output / "README.md")
    files.append({"path": "README.md", "sha256": sha(complete / "README.md"),
                  "bytes": (complete / "README.md").stat().st_size, "githubAsset": "README.md"})
    manifest = {"schemaVersion": 1, "project": "Schematic Totem", "version": version, "tag": "v" + version,
                "repository": "https://github.com/" + args.repo,
                "sourceCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                "license": "MIT", "loader": "Fabric", "environment": "client", "files": files,
                "targets": [{"minecraft": mc, "javaMinimum": c["java"], "fabricLoaderMinimum": c["loader"],
                             "fabricApiMinimum": c["fabric_api"], "artifact": f"schematic-totem-{version}+mc{mc}.jar"}
                            for mc, c in versions.items()]}
    write_json(complete / "MANIFEST.json", manifest)
    shutil.copy2(complete / "MANIFEST.json", output / "MANIFEST.json")
    checksums(complete)
    archive(complete, output / f"schematic-totem-{version}-release.zip", f"schematic-totem-{version}-release")
    checksums(output)
    print("Prepared publication assets:", output)
    print("Files:", len(list(output.iterdir())))


if __name__ == "__main__":
    main()
