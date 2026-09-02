# Die Trennung: BambooCEF wird eine eigene Mod

Festgelegt am 2. September 2026. Die CEF/Web-Runtime wird eine eigenständige
NeoForge-Mod **BambooCEF** (Mod-Id `bamboocef`), eigenes Projekt/Jar;
FactoryNetwork hängt als Abhängigkeit daran und ist nur noch ein Nutzer der
Schnittstelle.

Nicht verwechseln mit `D:\Projekte\BambooCEF` — das ist der Unity/.NET-CEF-Fork
der BambooEngine. Die Minecraft-Mods leben unter `D:\Projekte\MinecraftMods\`.

## Zielbild

```
D:\Projekte\MinecraftMods\
  BambooCEF\        bamboocef  — API, Runtime, WebPanel-Block, Runtime-Verteilung
  FactoryNetwork\   factorynetwork — Fabrik, Sprache, Editor, Displays; hängt an bamboocef
```

## Was nach BambooCEF zieht

| Aus FactoryNetwork | Was es ist |
|---|---|
| `web.api.*` | die zugesagte Schnittstelle |
| `web.runtime`, `web.view`, `web.frame`, `web.texture`, `web.input`, `web.measure`, `web.capture` | der Unterbau |
| `src/runtime/java` | java-cef-Anbindung, ProcessGuard |
| `tools/runtime/*`, `dist.properties`, Downloader, `RuntimeManifest`/`RuntimeInstall` | die Runtime-Verteilung (R2) |
| `WebPanelBlock` samt Registrierung | der generische Web-Display-Block |
| die fünf Dev-Klassen | `WebProofChain`, `WebDevTools`, Dev-Befehle, Harness-Ticks |

## Was in FactoryNetwork bleibt

Fabriklogik, Manifold-Sprache, Maschinen, die **eigenen** Display-Blöcke, der
Editor (`EditorApp`, schon auf der API), die Client-Verdrahtung der eigenen
Features. Nur noch Zugriff auf `bamboocef`s öffentliche API — heute schon von
`ModBoundaryTest` erzwungen (mit den fünf benannten Ausnahmen, die mit umziehen).

## Reihenfolge

```
1. BambooCEF-Projekt anlegen, leere Mod baut            ← in Arbeit
2. Runtime + API + Verteilung hinüberziehen, BambooCEF baut allein
3. FactoryNetwork als Abhängigkeit verdrahten (includeBuild),
   ModBoundaryTest fällt weg (die Grenze ist jetzt echt)
4. Dev-Start mit beiden Mods (moddev, zweite Mod auf dem Mod-Pfad)
5. WebPanel-Block + Dev-Klassen nach BambooCEF; FactoryNetworks
   eigene Display-Blöcke bleiben und nutzen die API
```

Nichts wird entfernt, bevor der neue Weg trägt — dieselbe Regel wie bisher.
Der Editor läuft schon über die API, also ist Schritt 3 kein Bruch für ihn.

## Der heikle Punkt

**Zwei NeoForge-Mods im Dev-Start.** ModDevGradle lädt die Mod des eigenen
Projekts; die zweite muss als Mod auf den Mod-Pfad. Der Weg dorthin
(Composite-Build `includeBuild`, oder die gebaute Jar als `runtimeOnly` plus
`modFolders`) ist das, was Schritt 4 zu prüfen hat, bevor Schritt 5 Altes
entfernt. Und `web/` kann `FactoryNetwork.MOD_ID` nicht mehr importieren —
darum standen die Dev-Klassen bisher auf der Mod-Seite; in BambooCEF nennen
sie ihre eigene Mod-Id.
