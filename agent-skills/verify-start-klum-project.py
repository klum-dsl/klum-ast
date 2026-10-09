#!/usr/bin/env python3
"""Check the copyable #469 bootstrap package and its frozen source links."""

from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parent.parent
SKILL = ROOT / "agent-skills/start-klum-project"
FIXTURE = ROOT / "agent-skills/fixtures/direct-schema-public-4.0.1"
CATWALK_SHOWCASE = (
    "https://github.com/klum-dsl/klum-catwalk/tree/"
    "519404ebc259e24bb24086f86c2ef6322d8bcbb7/showcases/domain-first-smart-home"
)
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
    readme = (FIXTURE / "README.md").read_text()
    require("id 'com.blackbuild.klum-ast-schema' version '4.0.1'" in build, "Public plugin pin is missing")
    require("mavenCentral()" in build and "gradlePluginPortal()" in settings, "Public repositories are missing")
    require(not re.search(r"includeBuild|mavenLocal\(|files\(|project\(", build + settings), "Public mission has a local product source")
    require("./gradlew --no-daemon test" in readme, "Public mission must use its own wrapper")
    require(not any(path.is_symlink() for path in FIXTURE.rglob("*")), "Public mission has an external symlink")
    with tempfile.TemporaryDirectory() as temporary:
        copied_fixture = Path(temporary) / FIXTURE.name
        shutil.copytree(FIXTURE, copied_fixture)
        wrapper = copied_fixture / "gradlew"
        require(wrapper.is_file() and wrapper.stat().st_mode & 0o111, "Copyable Gradle wrapper is missing or not executable")
        require((copied_fixture / "gradlew.bat").is_file(), "Copyable Windows Gradle wrapper is missing")
        wrapper_jar = copied_fixture / "gradle/wrapper/gradle-wrapper.jar"
        require(wrapper_jar.is_file(), "Copyable Gradle wrapper JAR is missing")
        with zipfile.ZipFile(wrapper_jar) as jar:
            require("org/gradle/wrapper/GradleWrapperMain.class" in jar.namelist(), "Copyable Gradle wrapper JAR is invalid")
        properties = (copied_fixture / "gradle/wrapper/gradle-wrapper.properties").read_text()
        require(
            re.search(r"^distributionUrl=https\\://services\.gradle\.org/distributions/gradle-8\.14\.4-all\.zip$", properties, re.MULTILINE),
            "Copyable Gradle wrapper must pin public Gradle 8.14.4",
        )
    for page in ("Gradle-Onboarding.md", "Testing-Models-and-Schemas.md"):
        text = (ROOT / "docs/user" / page).read_text()
        links = re.findall(r"\]\(([^)]+)\)", text)
        for target in ("agent-skills/start-klum-project", "agent-skills/fixtures/direct-schema-public-4.0.1"):
            require(any(link.endswith(target) for link in links), f"{page} does not link to {target}")
    for source in (
        SKILL / "SKILL.md",
        *(ROOT / "docs/user" / page for page in ("Gradle-Onboarding.md", "Domain-First-Modeling.md", "Layer3.md")),
    ):
        require(
            CATWALK_SHOWCASE in re.findall(r"\]\(([^)]+)\)", source.read_text()),
            f"{source.relative_to(ROOT)} does not link to the immutable Catwalk showcase",
        )
    print("#469 portable skill, authority, public mission, and user links: OK")


if __name__ == "__main__":
    main()
