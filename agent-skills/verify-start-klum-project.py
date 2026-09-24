#!/usr/bin/env python3
"""Check the copyable #469 bootstrap package and its frozen source links."""

from pathlib import Path
import re
import shutil
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parent.parent
SKILL = ROOT / "agent-skills/start-klum-project"
FIXTURE = ROOT / "agent-skills/fixtures/direct-schema-public-4.0.1"
TAGGED_BLOBS = {
    "docs/user/Basics.md": "15922d51e4cd24bd85bf3ce588ddf417383db256",
    "docs/user/Builder-First-Migration.md": "51e6acec3c25d937cc616c68ccaa7ba01ed92ad0",
    "docs/user/Validation.md": "cd15be7cf2b80d92b494a2834ccaaa9ea6ff43bb",
    "docs/user/Gradle-Onboarding.md": "68c82a7cf3d4bb26340a66a3333cb4cc9f4a6ba6",
    "docs/user/Gradle-Plugins.md": "01a206e155fbda607291ced659b1de52dc66115f",
    "README.md": "2f0dda36b43955774ca290eeb5d8749b68ceb122",
    "gradle/wrapper/gradle-wrapper.properties": "29a0dd9af1159eb1159aea67393097cfecaad84b",
}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    with tempfile.TemporaryDirectory() as temporary:
        copied = Path(temporary) / "start-klum-project"
        shutil.copytree(SKILL, copied)
        entry = copied / "SKILL.md"
        authority = copied / "references/klum-4.0.1.md"
        require(entry.is_file() and authority.is_file(), "Skill and authority must copy together")
        skill_text = entry.read_text()
        authority_text = authority.read_text()
        require(skill_text.startswith("---\nname: start-klum-project\n"), "Skill frontmatter is missing")
        require("references/klum-4.0.1.md" in skill_text, "Skill does not route to its authority")
        require(not re.search(r"/(Users|home)/", skill_text + authority_text), "Portable package has a local absolute path")
        for link in re.findall(r"\]\(([^)]+)\)", skill_text + authority_text):
            if not re.match(r"https?://", link):
                require((copied / link).is_file(), f"Broken internal skill link: {link}")

    tag_object = subprocess.check_output(["git", "rev-parse", "v4.0.1^{tag}"], cwd=ROOT, text=True).strip()
    require(tag_object == "e77057fdf65b79c2c7f92eaaec051d5e8c393950", "4.0.1 tag object drift")
    tag = subprocess.check_output(["git", "rev-parse", "v4.0.1^{}"], cwd=ROOT, text=True).strip()
    require(tag == "4d85ec2ed7e0a737d71b421af2c0cf597f6830e4", "4.0.1 tag target drift")
    tagged_links = set(re.findall(r"https://github\.com/klum-dsl/klum-ast/blob/v4\.0\.1/([^)]*)", authority_text))
    require(tagged_links == set(TAGGED_BLOBS), "Tagged authority links do not match the frozen source set")
    for path, expected_blob in TAGGED_BLOBS.items():
        actual = subprocess.check_output(["git", "rev-parse", f"v4.0.1:{path}"], cwd=ROOT, text=True).strip()
        require(actual == expected_blob and expected_blob in authority_text, f"Authority mismatch: {path}")

    require((FIXTURE / "build.gradle").is_file(), "Public mission build is missing")
    build = (FIXTURE / "build.gradle").read_text()
    settings = (FIXTURE / "settings.gradle").read_text()
    require("id 'com.blackbuild.klum-ast-schema' version '4.0.1'" in build, "Public plugin pin is missing")
    require("mavenCentral()" in build and "gradlePluginPortal()" in settings, "Public repositories are missing")
    require(not re.search(r"includeBuild|mavenLocal\(|files\(|project\(", build + settings), "Public mission has a local product source")
    for page in ("Gradle-Onboarding.md", "Testing-Models-and-Schemas.md"):
        text = (ROOT / "docs/user" / page).read_text()
        links = re.findall(r"\]\(([^)]+)\)", text)
        for target in ("agent-skills/start-klum-project", "agent-skills/fixtures/direct-schema-public-4.0.1"):
            require(any(link.endswith(target) for link in links), f"{page} does not link to {target}")
    print("#469 portable skill, authority, public mission, and user links: OK")


if __name__ == "__main__":
    main()
