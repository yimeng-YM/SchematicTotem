"""Create missing mc/<version> branches from one clean, committed multi-version tree."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="feature/multiversion")
    parser.add_argument("--output", type=Path, default=root / "dist/1.3.0/branches.json")
    args = parser.parse_args()

    def git(*arguments, data=None, env=None):
        return subprocess.run(["git", *arguments], cwd=root, input=data, capture_output=True, check=True, env=env).stdout.decode("utf-8").strip()

    if git("status", "--porcelain"):
        raise SystemExit("Commit the source changes first. Existing branches are never overwritten.")
    base = git("rev-parse", args.base)
    properties = git("show", f"{base}:gradle.properties") + "\n"
    versions = json.loads(git("show", f"{base}:versioning/versions.json"))
    existing = set(git("for-each-ref", "--format=%(refname:short)", "refs/heads").splitlines())
    wanted = {f"mc/{version}" for version in versions}
    if wanted & existing:
        raise SystemExit("Version branches already exist: " + ", ".join(sorted(wanted & existing)))
    index = (root / "build/version-branch.index").resolve()
    assert index.is_relative_to(root / "build")
    index.parent.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, GIT_INDEX_FILE=str(index))
    rows = []
    for mc in versions:
        content = re.sub(r"(?m)^minecraft_version=.*$", f"minecraft_version={mc}", properties)
        if content == properties:
            commit = base
        else:
            git("read-tree", base, env=env)
            blob = git("hash-object", "-w", "--stdin", data=content.encode("utf-8"))
            git("update-index", "--add", "--cacheinfo", f"100644,{blob},gradle.properties", env=env)
            tree = git("write-tree", env=env)
            commit = git("commit-tree", tree, "-p", base, "-m", f"build: default to Minecraft {mc}")
        branch = f"mc/{mc}"
        git("branch", branch, commit)
        rows.append({"minecraft": mc, "branch": branch, "commit": commit, "sharedBase": base})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Created {len(rows)} local version branches; working tree remains on {git('branch', '--show-current')}.")


if __name__ == "__main__":
    main()
