"""Holt Monaco und legt es unter unsere Web-Ressourcen.

Aufruf:   python tools/monaco.py

Was es tut: laedt das npm-Paket in einer festen Fassung, entpackt es und
kopiert die Teile, die wir wirklich brauchen, nach

    src/main/resources/assets/factorynetwork/web/ide/vs/

Dazu schreibt es ein Verzeichnis aller kopierten Dateien. Das braucht die
Laufzeit: Ein Ordner im Klassenpfad laesst sich nicht zuverlaessig auflisten,
wohl aber eine Datei mit den Namen darin lesen.

Warum nicht zur Laufzeit laden: Eine Oberflaeche, die beim ersten Oeffnen ins
Netz greift, funktioniert im Zug nicht und in einem gesperrten Netz nie. Das
Bundle gehoert in die Mod.
"""

import io
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile

# Feste Fassung. Ein "neueste nehmen" macht zwei Baeume unvergleichbar.
VERSION = "0.56.0"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGET = os.path.join(ROOT, "src", "main", "resources", "assets",
                      "factorynetwork", "web", "ide", "vs")

# <b>Alles kommt mit.</b>
#
# Der erste Versuch nahm nur den Editorkern und liess die Sprachdienste weg —
# TypeScript allein ist dreizehn Megabyte, und wir faerben unsere Sprache ueber
# Monarch ein. Es scheiterte zweimal: Erst fehlten die "monaco.contribution"-
# Anmeldungen, dann die Arbeitsdateien, die diese ihrerseits nachladen.
# editor.main haengt an einem Abhaengigkeitsgeflecht, das sich mit einer
# Dateiliste nicht sauber schneiden laesst.
#
# Sauber schneiden koennte das nur ein Bundler, der den Graphen kennt
# (bun build, esbuild). Das ist ein eigener Bauschritt und gehoert nicht in
# einen Spike, der herausfinden soll, ob Monaco sich gut anfuehlt. Also: alles,
# und die Groesse als Befund im Bericht.
SKIP_DIRS = set()
ALWAYS_KEEP = ()
SKIP_SUFFIXES = ()
SKIP_MARKERS = ()
KEEP_ROOT_PREFIXES = ()


def fetch(into):
    """Laedt das Paket und gibt den entpackten Ordner zurueck."""
    print(f"Lade monaco-editor@{VERSION} ...")
    subprocess.run(["npm", "pack", f"monaco-editor@{VERSION}", "--silent"],
                   cwd=into, check=True, shell=(os.name == "nt"))
    tarball = os.path.join(into, f"monaco-editor-{VERSION}.tgz")
    with tarfile.open(tarball) as archive:
        archive.extractall(into)
    return os.path.join(into, "package", "min", "vs")


def wanted(relative):
    """Ob diese Datei mitkommt. Zurzeit: jede."""
    return True


def copy(source, target):
    """Kopiert und gibt die Liste der Dateien zurueck, relativ zum Ziel."""
    if os.path.isdir(target):
        shutil.rmtree(target)
    os.makedirs(target)

    copied = []
    total = 0
    for folder, _, names in os.walk(source):
        for name in sorted(names):
            full = os.path.join(folder, name)
            relative = os.path.relpath(full, source).replace("\\", "/")
            if not wanted(relative):
                continue
            destination = os.path.join(target, relative)
            os.makedirs(os.path.dirname(destination), exist_ok=True)
            shutil.copy2(full, destination)
            copied.append(relative)
            total += os.path.getsize(full)
    return copied, total


def main():
    with tempfile.TemporaryDirectory() as scratch:
        source = fetch(scratch)
        copied, total = copy(source, TARGET)

    # Das Verzeichnis fuer die Laufzeit. Eine Zeile je Datei, dazu die Fassung
    # in der ersten Zeile — daran erkennt der Auspacker, ob sein Ordner noch
    # zum Bundle passt.
    listing = os.path.join(os.path.dirname(TARGET), "files.txt")
    with io.open(listing, "w", encoding="utf-8", newline="\n") as out:
        out.write(f"# monaco-editor {VERSION}\n")
        for relative in copied:
            out.write(f"vs/{relative}\n")

    print(f"{len(copied)} Dateien, {total / 1048576:.1f} MB nach")
    print(f"  {os.path.relpath(TARGET, ROOT)}")
    print(f"Verzeichnis: {os.path.relpath(listing, ROOT)}")


if __name__ == "__main__":
    sys.exit(main())
