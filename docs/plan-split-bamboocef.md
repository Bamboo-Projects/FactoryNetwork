# Plan: Trennung in BambooCEFMC und FactoryNetwork

Stand 2. September 2026. Die CEF/Web-Runtime ist die API-Mod (BambooCEFMC,
Mod-Id `bamboocef`), FactoryNetwork ein Nutzer wie jede fremde Mod.

## Erledigt

- BambooCEFMC-Projekt steht, baut eigenständig: `web/` (79), `src/runtime`
  (7), Prüfläufe (149 grün), Paket `dev.devpanda.bamboocef.web`.
- Beide Repos in der Org Bamboo-Projects, `dev`-Branches, Code-Doku und
  Commit-Nachrichten englisch, Autor-Mail mit devpanda0 verknüpft.
- WebPanel-Block aus FactoryNetwork gelöscht (Gerüst, die API ersetzt ihn).

## Erledigt am 2. September 2026 (Schritte 1–3)

### 1. BambooCEFMC eigenständig machen

- Namensraum `factorynetwork` → `bamboocef`: `SessionSurface` (Texturen
  `web_surface/*`), `WebPage` (Entpackordner `<game>/bamboocef/web/`),
  `RuntimeInstall` (`<game>/bamboocef/runtime/<version>/`),
  `fn-runtime.properties` → `bamboocef-runtime.properties` samt
  `RuntimeManifest` und `build.gradle`. Die R2-Adresse bleibt (echte Domain).
- Mixin hinüber: `dev.devpanda.bamboocef.mixin.KeyboardHandlerMixin`,
  `bamboocef.mixins.json`, `[[mixins]]` in der mods.toml.
- Eigener Client-Lebenslauf (`BambooCEFClient`), portiert aus FactoryNetworks
  `FnClient`/`FactoryNetwork`: Client-Tick (Overlays, WorldSurfaces,
  FramePacer/Benchmark), `RenderGuiEvent.Post` (Overlays zeichnen),
  `RenderLevelStageEvent` nach den durchsichtigen Blöcken (Weltflächen),
  Ausloggen (alles schließen), `GameShuttingDownEvent` (`WebRuntime.shutdown`),
  `WebDebug.requestIfEnabled` beim Start.
- `web/ide` (IdeScreen + Monaco-/Typing-/Probe-/Lifecycle-Benchmark) aus
  BambooCEFMC raus: Sie messen FactoryNetworks IDE und greifen auf deren
  Assets zu. Sie ziehen in Schritt 3 nach FactoryNetwork.
- Prüfung: BambooCEFMC baut, 149 Tests grün.

### 2. FactoryNetwork hängt an BambooCEFMC

- `settings.gradle`: `includeBuild('../BambooCEFMC')`; `build.gradle`:
  `implementation 'dev.devpanda.bamboocef:bamboocef'`. Die Jar mit mods.toml
  auf dem Laufzeit-Klassenpfad ist im Dev-Start automatisch eine Mod.
- Dev-Client: jcef.jar und `fn.runtime.dir` zeigen auf
  `../BambooCEFMC/tools/runtime/build/out/windows-x86_64`.
- Prüfung: FactoryNetwork baut (die eigene `web/`-Kopie stört nicht, andere
  Pakete).

### 3. FactoryNetwork-Kopie löschen, Importe umstellen

- Löschen: `src/main/java/.../web/**` außer `web/ide`, `src/runtime/java`,
  `tools/runtime`, `src/test/.../web/**`, Mixin, jcef-/dist-Verdrahtung im
  `build.gradle` (bis auf den Dev-Klassenpfad aus Schritt 2).
- `web/ide`-Benchmarks nach `client/bench` mit `bamboocef`-Importen.
- Importe `dev.devpanda.factorynetwork.web.*` → `dev.devpanda.bamboocef.web.*`.
- `FnClient`/`FactoryNetwork`: die Runtime-Hooks raus (gehören jetzt
  BambooCEFMC); bleiben: `WorldPointer`, `OverlayProof`, die API-Demos.
- Runtime-Dev-Werkzeuge (`WebProofChain`, `WebDevTools`, ihre Befehle) nach
  BambooCEFMC (`dev.devpanda.bamboocef.dev`); `WebCommands` in FactoryNetwork
  behält nur die API-Demos (`/fnweb overlay|welt|ide`).
- `ModBoundaryTest` wird echt: erlaubt nur `bamboocef.web.api.*` plus
  `WebPage`, `WebAssets`, `BrowserVisibility`; Ausnahme nur `client/bench`.
- Prüfung: beide bauen, beide Testläufe grün (FactoryNetwork ohne Web-Tests).

### 4. Abnahme — bestanden am 3. September 2026

Dev-Start von FactoryNetwork mit beiden Mods (Log `run/logs/latest.log`):

- Mod-Liste: `BambooCEF 0.1.0 (bamboocef)` und `factorynetwork`; keine Fehler
  außer den bekannten Ars-Nouveau-Blockstate-Warnungen.
- Chromium 146.0.7680 aus `BambooCEFMC/tools/runtime/build/out`, ProcessGuard
  aktiv; Start erst mit der ersten Fläche (Bibliothek startet nichts von sich
  aus).
- Editor: `/fnweb ide` → „IDE bereit: 8 Modelle", Doppel-Escape schließt,
  erneutes Öffnen als Sitzung 2 — Lebenslauf wiederholbar.
- Overlay: `/fnweb overlay` + Enter → „Overlay meldet:" (zweimal) — die
  Tasten laufen durch das bamboocef-Mixin und den Kanal Seite→Mod.
- Weltfläche: `/fnweb welt` + Fadenkreuz-Rechtsklick → „Weltfläche meldet:"
  für Maschinen, Programme, Einstellungen.

Gemessen: erstes Öffnen 3,6 s (1,6 s Chromium-Kaltstart + 1,9 s Monaco),
zweites Öffnen 0,8 s. Der Kaltstart fiel früher in den Selbsttest zehn
Sekunden nach Spielstart; als Bibliothek darf BambooCEF das nicht mehr von
sich aus. Antwort: `FnWeb.prepare()` — ein Nutzer, der das Web sicher
braucht, fordert die Runtime früh an, und BambooCEF fährt sie am Titelbild
hoch. FactoryNetwork ruft es im Client-Setup.

Nachgemessen am 3. September 2026 mit `FnWeb.prepare()`: Vorwärmung 17 s
nach dem Ressourcen-Laden am Titelbild (Status READY), erstes Öffnen des
Editors danach **0,99 s** (Browser registriert → „IDE bereit") statt 3,6 s.
Gefahren per Fenster-Skript (`stufe2/probe.ps1`: Welt per Maus, `/fnweb ide`
per Unicode-Tastatureingabe; Vorsicht: Git Bash wandelt ein Argument mit
führendem `/` in einen Pfad um — `MSYS_NO_PATHCONV=1` setzen).

## Offen

- Danach: Minotaur + `publish.yml`, erster Release über `/release`,
  Monaco-NOTICE, Wegwerf-`run/` unter dem alten Pfad löschen.
- Feinschliff (erledigt 3.9.2026): `bamboocef.runtime.dir`, `.devtools`,
  `.benchmark`, `.cef.trace`; Logger „BambooCEF/…"; CEF- und java-cef-Lizenz
  im Runtime-Paket (`files-windows.txt`, `build-jcef.ps1`); BambooCEFMC hat
  LICENSE (MIT), englische README und NOTICE.md; FactoryNetwork hat NOTICE.md
  (Monaco 0.56.0 MIT, JetBrains Mono OFL) und `font/OFL.txt`. `dev` ist per
  Fast-Forward in `main`/`master` beider Repos.
- Restposten im Feinschliff, nichts davon dringend: `WebProofChain` in der
  Bibliothek liest `fn.ide` (ein Schalter des Nutzers) für den Vorrang der
  Editor-Nachweise; Klassennamen `FnBrowser`, `FnCefRuntime`,
  `FnRuntimeBackend`; Umgebungsvariablen `FN_JCEF_BUILD`, `FN_PYTHON`,
  Arbeitsordner `C:njcef`, Temp-Präfix `fn-runtime-probe-`; ein Kommentar
  in `build-jcef.ps1` nennt noch den alten Pfad `D:\Projekte\FactoryNetwork`.
- Runtime-Verteilung umgezogen (4.9.2026, Entscheidung: ein Bucket
  `bamboocef` für die ganze BambooCEF-Familie, Unterordner je Plattform —
  `minecraft/neoforge/`, später `unreal/`, `unity/`; Domain
  `bamboocef.bamboo-srv.de`). Archiv `bamboocef-runtime-<cef>-<plattform>.tar.gz`
  mit den beiden Lizenztexten, Basis-Adresse in `dist.properties` trägt den
  Unterordner. Nachweis im Spiel ohne `bamboocef.runtime.dir`: 173 MB von der
  neuen Adresse in 3,9 s samt Prüfsumme und Entpacken, 21 Dateien im
  Install-Ordner. Beim ersten Lauf fasste die Vorwärmung nach dem Download
  nicht nach — behoben (`WebWarmup` sieht alle fünf Sekunden nach, bis die
  Runtime da ist). Fallen und Befehle stehen im Gedächtnis-Eintrag.
  FactoryNetworks Bucket/Domain bleiben vorerst mit dem alten Archiv.
