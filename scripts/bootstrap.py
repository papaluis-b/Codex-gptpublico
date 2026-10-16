#!/usr/bin/env python3
"""Linux x86_64 cloud toolchain. TLS and upstream artifact digests stay enabled."""
import hashlib
import os
from pathlib import Path
import shutil
import stat
import tarfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = Path(os.environ.get("SONDA_TOOLS", str(ROOT / ".tooling")))
TOOLS.mkdir(parents=True, exist_ok=True)
DOWNLOADS = TOOLS / "downloads"
DOWNLOADS.mkdir(exist_ok=True)


def read(url):
    with urllib.request.urlopen(url, timeout=90) as response:
        return response.read()


def download(url, algorithm, expected):
    target = DOWNLOADS / url.rsplit("/", 1)[1]
    if not target.exists() or hashlib.new(algorithm, target.read_bytes()).hexdigest() != expected:
        print("Downloading", target.name, flush=True)
        temporary = target.with_suffix(target.suffix + ".partial")
        with urllib.request.urlopen(url, timeout=120) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
        if hashlib.new(algorithm, temporary.read_bytes()).hexdigest() != expected:
            temporary.unlink()
            raise RuntimeError("Upstream digest mismatch: " + target.name)
        temporary.replace(target)
    print("Verified", target.name, flush=True)
    return target


def unzip(archive, destination, strip=0):
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as source:
        for entry in source.infolist():
            parts = Path(entry.filename).parts[strip:]
            if not parts:
                continue
            path = destination.joinpath(*parts)
            if not path.resolve().is_relative_to(destination.resolve()):
                raise RuntimeError("Unsafe archive path")
            if entry.is_dir():
                path.mkdir(parents=True, exist_ok=True)
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                with source.open(entry) as input_file, path.open("wb") as output:
                    shutil.copyfileobj(input_file, output)
                mode = entry.external_attr >> 16
                if mode:
                    path.chmod(stat.S_IMODE(mode))


if not (TOOLS / "jdk/bin/javac").exists():
    url = ("https://github.com/adoptium/temurin21-binaries/releases/download/"
           "jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz")
    digest = read(url + ".sha256.txt").decode().split()[0]
    archive = download(url, "sha256", digest)
    with tarfile.open(archive) as source:
        source.extractall(TOOLS / "jdk-unpack", filter="data")
    folders = list((TOOLS / "jdk-unpack").iterdir())
    folders[0].rename(TOOLS / "jdk")
    (TOOLS / "jdk-unpack").rmdir()

if not (TOOLS / "gradle-8.13/bin/gradle").exists():
    url = "https://services.gradle.org/distributions/gradle-8.13-bin.zip"
    digest = read(url + ".sha256").decode().strip()
    unzip(download(url, "sha256", digest), TOOLS)

manifest = ET.fromstring(read("https://dl.google.com/android/repository/repository2-1.xml"))
packages = [
    ("cmdline-tools;19.0", "commandlinetools-linux-13114758_latest.zip", "cmdline-tools/19.0"),
    ("platforms;android-36", "platform-36_r02.zip", "platforms/android-36"),
    ("build-tools;36.0.0", "build-tools_r36_linux.zip", "build-tools/36.0.0"),
    ("platform-tools", "platform-tools_r37.0.1-linux.zip", "platform-tools"),
]
for package, filename, destination in packages:
    target = TOOLS / "android-sdk" / destination
    if target.exists():
        continue
    found = False
    for node in manifest.findall("remotePackage"):
        if node.get("path") != package:
            continue
        for complete in node.findall("./archives/archive/complete"):
            if complete.findtext("url") == filename:
                archive = download("https://dl.google.com/android/repository/" + filename,
                                   "sha1", complete.findtext("checksum"))
                unzip(archive, target, strip=1)
                found = True
    if not found:
        raise RuntimeError("Pinned SDK package is absent from official metadata: " + filename)
print("Toolchain installed. Accept Android SDK licenses using sdkmanager before building.")
