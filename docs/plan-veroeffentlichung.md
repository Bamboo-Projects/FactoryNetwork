# Veröffentlichung und Versionierung (FactoryNetwork)

Geplant am 2. September 2026. Noch nichts läuft — erst muss die Trennung von
BambooCEFMC fertig sein (siehe `plan-split-bamboocef.md`). Der ausführliche
Plan mit den zwei Pipelines steht im BambooCEFMC-Repo unter
`docs/plan-veroeffentlichung.md`; hier nur, was für FactoryNetwork abweicht.

## Branches

Wie bei BambooCEFMC: `dev` zum Arbeiten, `main` als stabile Linie, eine
Branch je MC-Version (`1.21.1`, `1.21.4`, …), Tags `v<semver>+mc<version>`.
Gearbeitet wird auf `dev`.

## Was hier anders ist

- **FactoryNetwork hängt an BambooCEFMC.** Die Modrinth-/CurseForge-Angaben
  nennen `bamboocef` als Pflicht-Abhängigkeit (`required`). Ein Release von
  FactoryNetwork für eine MC-Version setzt ein passendes BambooCEFMC für
  dieselbe MC-Version voraus.
- **Größere Jar.** FactoryNetwork liefert die Monaco-Weboberfläche mit; die
  native Runtime kommt dagegen aus BambooCEFMC (R2), nicht aus dieser Jar.
- **Monaco-Lizenz (MIT):** vor der ersten öffentlichen Release eine
  `NOTICE`/`THIRD-PARTY`-Datei für den mitgelieferten VS-Code-Editor beilegen.

## Pipeline

Dieselbe Mod-Jar-Pipeline wie in BambooCEFMC (GitHub Actions, Tag `v*+mc*`,
Minotaur → Modrinth/CurseForge, `action-gh-release` → GitHub Releases). Die
native Runtime-Pipeline gibt es hier **nicht** — die gehört zu BambooCEFMC.

## Reihenfolge

```
1. Trennung fertig, FactoryNetwork baut gegen BambooCEFMC
2. Minotaur + publish.yml, Abhängigkeit auf bamboocef eintragen
3. NOTICE für Monaco beilegen
4. Erste Vorab-Release parallel zu einem BambooCEFMC-Release derselben MC-Version
```
