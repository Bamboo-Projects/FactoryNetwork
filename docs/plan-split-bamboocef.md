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

## Offen

### 4. Abnahme

- Dev-Start mit beiden Mods; Overlay, Weltfläche, Editor, Kanal Seite→Mod
  wie in den `stand-web-*.md` beschrieben.
- Danach: Minotaur + `publish.yml`, erster Release über `/release`,
  Monaco-NOTICE, Wegwerf-`run/` unter dem alten Pfad löschen.
- Feinschliff in BambooCEFMC: die Namen `fn.runtime.dir`, `fn.devtools`,
  `fn.benchmark` und die Logger „FactoryNetwork/…" auf `bamboocef` drehen
  (mit den runClient-Skripten im Scratchpad abstimmen).
- FactoryNetworks `neoforge.mods.toml` verlangt `bamboocef` jetzt als
  Client-Abhängigkeit (erledigt, hier nur der Vollständigkeit halber).
