#!/usr/bin/env python3
"""Package the approved application, reusable libraries, sources and an external consumer."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.2.0"
DSP_VERSION = "0.2.0"
APPROVED_VERSION = "0.2.0"
APPROVED_COMMIT = "01eb7fab88218c9361dd3a9b4e305ffe086d2ce5"
APPROVED_APK_SHA256 = "1b1ded5b76804b469e00965aec8eb6d02447e556de854cb8301c47a14f218fe3"
APPROVED_SOURCE_SHA256 = "abd106c856ecb0fa3d413688be316f89902f80b288693595654e6b9f2104d329"
REPOSITORY = "https://github.com/papaluis-b/Codex-gptpublico"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def copy(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def package(output: Path) -> Path:
    if not (ROOT / ".git").exists():
        raise SystemExit("Inicializa Git en las fuentes portátiles (git init y git add .) o usa un clon del repositorio.")
    maven = ROOT / "build/generator-kit-repository"
    kit = maven / f"es/sonda/generator-kit/{VERSION}"
    dsp = maven / f"es/sonda/dsp/{DSP_VERSION}"
    aar = kit / f"generator-kit-{VERSION}.aar"
    jar = dsp / f"dsp-{DSP_VERSION}.jar"
    for folder, artifact, version in ((kit, "generator-kit", VERSION), (dsp, "dsp", DSP_VERSION)):
        for suffix in ("pom", "module", "sources.jar"):
            separator = "-" if suffix == "sources.jar" else "."
            path = folder / f"{artifact}-{version}{separator}{suffix}"
            if not path.is_file():
                raise SystemExit(f"Falta publicar {path}; ejecuta las tareas publishReleasePublicationToKitRepository.")
        pom = ET.parse(folder / f"{artifact}-{version}.pom").getroot()
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        assert pom.findtext("m:groupId", namespaces=ns) == "es.sonda"
        assert pom.findtext("m:artifactId", namespaces=ns) == artifact
        assert pom.findtext("m:version", namespaces=ns) == version
        assert "ui-test-manifest" not in (folder / f"{artifact}-{version}.pom").read_text()
        assert "ui-test-manifest" not in (folder / f"{artifact}-{version}.module").read_text()
    with zipfile.ZipFile(aar) as archive:
        assert archive.testzip() is None
        xml = ET.fromstring(archive.read("AndroidManifest.xml"))
        assert not xml.findall("uses-permission")
        application = xml.find("application")
        assert application is None or not list(application)
        with zipfile.ZipFile(io.BytesIO(archive.read("classes.jar"))) as classes:
            names = classes.namelist()
            assert "es/sonda/generador/audio/GeneratorOutputEngine.class" in names
            assert "es/sonda/generador/kit/ui/GeneratorScreenKt.class" in names
            assert "es/sonda/generador/MainActivity.class" not in names
    with zipfile.ZipFile(jar) as archive:
        assert archive.testzip() is None
        assert "es/sonda/dsp/SignalGenerator.class" in archive.namelist()

    baseline = ROOT / "artifacts"
    if not baseline.is_dir():
        # The portable source tree uses its sibling baseline instead of duplicating binaries.
        baseline = ROOT.parent / "baseline"
    approved_apk = baseline / f"SONDA-Generador-{APPROVED_VERSION}-debug.apk"
    approved_source = baseline / f"SONDA-Generador-{APPROVED_VERSION}-codigo.zip"
    assert digest(approved_apk) == APPROVED_APK_SHA256, "El APK aprobado debe permanecer intacto."
    assert digest(approved_source) == APPROVED_SOURCE_SHA256, "Las fuentes originales deben permanecer intactas."
    source_core = {
        path.relative_to(ROOT).as_posix(): digest(path)
        for directory in ("dsp/src/main/kotlin", "generator-kit/src/main/java/es/sonda/generador/audio")
        for path in (ROOT / directory).rglob("*.kt")
    }
    approved_core = {}
    with zipfile.ZipFile(approved_source) as original:
        prefix = f"SONDA-Generador-{APPROVED_VERSION}/"
        for name in original.namelist():
            relative = name.removeprefix(prefix)
            if not name.startswith(prefix) or not relative.endswith(".kt"):
                continue
            if relative.startswith("generator/src/main/java/es/sonda/generador/audio/"):
                relative = relative.replace("generator/", "generator-kit/", 1)
            elif not relative.startswith("dsp/src/main/kotlin/"):
                continue
            approved_core[relative] = hashlib.sha256(original.read(name)).hexdigest()
    core_matches_approved = source_core == approved_core
    if VERSION == APPROVED_VERSION:
        assert core_matches_approved, "La extracción inicial debe conservar todo el motor y DSP aprobados."

    output.mkdir(parents=True, exist_ok=True)
    folder = output / f"SONDA-Generador-Kit-{VERSION}"
    # Only this script's staging directory is replaced; no repository or approved artifact is removed.
    if folder.exists():
        shutil.rmtree(folder)
    folder.mkdir()
    shutil.copytree(maven, folder / "maven-repository")
    copy(aar, folder / "libraries" / aar.name)
    copy(jar, folder / "libraries" / jar.name)
    copy(approved_apk, folder / "baseline" / approved_apk.name)
    copy(approved_source, folder / "baseline" / approved_source.name)

    tracked = subprocess.check_output(
        ["git", "ls-files", "-co", "--exclude-standard", "-z"], cwd=ROOT
    ).decode().split("\0")
    for name in sorted(set(tracked) - {""}):
        if name.startswith("artifacts/") or not (ROOT / name).is_file():
            continue
        assert ".tooling/" not in name and not name.endswith((".jks", ".pyc")) and "/build/" not in name
        copy(ROOT / name, folder / "sources" / name)
        if name.startswith("examples/generator-host/"):
            copy(ROOT / name, folder / "example" / name.removeprefix("examples/generator-host/"))
        if name.startswith("examples/dsp-jvm/"):
            copy(ROOT / name, folder / "jvm-example" / name.removeprefix("examples/dsp-jvm/"))
        if name.startswith("docs/"):
            copy(ROOT / name, folder / name)
    for name in ("DEPENDENCIES.md", "LICENSE_STATUS.md"):
        copy(ROOT / name, folder / name)
    copy(ROOT / "docs/14_MODULO_REUTILIZABLE.md", folder / "LEEME.md")

    manifest = {
        "name": "SONDA Generador Kit",
        "version": VERSION,
        "repository": REPOSITORY,
        "kitTag": f"generator-kit-v{VERSION}",
        "approvedApplication": {
            "version": APPROVED_VERSION,
            "tag": f"generador-app-v{APPROVED_VERSION}",
            "commit": APPROVED_COMMIT,
            "applicationId": "es.sonda.generador",
            "apkSha256": APPROVED_APK_SHA256,
            "sourceZipSha256": APPROVED_SOURCE_SHA256,
        },
        "coordinates": {"android": f"es.sonda:generator-kit:{VERSION}", "jvm": f"es.sonda:dsp:{DSP_VERSION}"},
        "examples": {"android": "example", "jvm": "jvm-example"},
        "minimumAndroidApi": 28,
        "bytecodeJava": 17,
        "compileSdk": 36,
        "physicalHardwareValidated": False,
        "approvedCoreFilesSha256": approved_core,
        "sourceCoreFilesSha256": source_core,
        "coreMatchesApproved": core_matches_approved,
        "licenseStatus": "See LICENSE_STATUS.md and DEPENDENCIES.md; no general project license assigned.",
    }
    (folder / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    checksums = []
    for path in sorted(folder.rglob("*")):
        if path.is_file():
            checksums.append(f"{digest(path)}  {path.relative_to(folder).as_posix()}")
    (folder / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n")
    archive_path = output / f"SONDA-Generador-{VERSION}-kit.zip"
    with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(folder.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(output))
    with zipfile.ZipFile(archive_path) as archive:
        assert archive.testzip() is None
        apk_data = archive.read(f"{folder.name}/baseline/{approved_apk.name}")
        assert hashlib.sha256(apk_data).hexdigest() == APPROVED_APK_SHA256
    print(f"Kit: {archive_path}\nSHA-256: {digest(archive_path)}\nArchivos verificados: {len(checksums)}")
    return archive_path


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "build/generator-kit-dist")
    package(parser.parse_args().output_dir.resolve())
