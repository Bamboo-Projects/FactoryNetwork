# FactoryNetwork 0.1.2 — Stabilisierung und Abnahme

Stand: 6. September 2026. Minecraft 1.21.1, NeoForge 21.1.248, Java 21.

## Änderungen

- Der Netzwerk-Tab erhält regelmäßig strukturierte Workerzustände samt Grund
  vom Server. Gründe sind umgebrochen und die Statusbezeichnungen in Deutsch
  und Englisch übersetzt. Die genaue Messbasis steht in
  [worker-wartegruende.md](worker-wartegruende.md).
- Vorübergehende Workerfehler können sich beim nächsten Versuch erholen.
  Nicht rückholbare Transferfehler bleiben bis zum Programmneustart angehalten.
  Eine verweigerte Quellentnahme wird nicht mehr als volles Ziel ausgegeben.
- `ControllerProjectPersistence` übernimmt Projekt-NBT, Legacy-Lesen und die
  Dateisynchronisierung. Der Controller behält die Entscheidung über Übernahmen.
  Das ist der erste abgegrenzte Schritt seiner Aufteilung, kein vollständiger Umbau.
- Fehlende Berechtigung wird beim Übernehmen geprüft, bevor eine Laptop-Batterie
  belastet wird.
- GameTests liegen in `src/gametest` als eigene Entwicklungsmod. Sie werden
  nicht mehr mit FactoryNetwork ausgeliefert. Der minimale Client braucht damit
  keine optionalen Mods, nur um Testklassen laden zu können.
- FactoryNetwork hat Netzwerkprotokoll **3**. Client und Server benötigen die
  passende Version 0.1.2. BambooCEF bleibt bei 0.1.1 und wird nur vom Client benötigt.

## Automatisierte Prüfung

`gradlew test build acceptanceJar runGameTestServer` prüft Build und beide Testebenen.
611 JUnit-Tests sowie 369 erforderliche GameTests bilden den aktuellen Satz.
Die acht Node-Tests des Web-Editors laufen über `webEditorCheck` zusätzlich mit.

Neue Regressionen betreffen Projekt-Persistenz, Workerzustände und Paket-Roundtrip.
Drei Mehrspieler-GameTests verwenden zwei unabhängige FakePlayer, echte
TerminalMenus und die tatsächlichen SaveDraft-/DeployProgram-Handler:
konkurrierende Bearbeitung mit Dateisperren, Freigabe beim Menüschließen,
OWNER-Berechtigung und Erhalt beider Projektstände bei einer fehlerhaften Übernahme.
Zwei weitere GameTests prüfen nicht entnehmbare Quellslots mit echten ItemStackHandlern.
Diese Simulation ist von zwei tatsächlich angemeldeten Clients zu unterscheiden.

## Dedizierter Server

Der isolierte Server läuft unter `build/acceptance-server` und bindet ausschließlich
`127.0.0.1:25576`. Für den lokalen Entwicklungsclient ist dort `online-mode=false`
gesetzt. Das ist keine Vorlage für einen öffentlich erreichbaren Server.
Die Installation folgt der [NeoForge-Serveranleitung](https://docs.neoforged.net/user/docs/server/).

Geladen wurden nur Minecraft, NeoForge, FactoryNetwork und die separat gebaute
Testhilfe `factorynetwork_acceptance`. BambooCEF war nicht installiert.

Nachgewiesen:

1. Der Worker verschob alle 64 Eisenbarren zwischen zwei echten Kisten.
2. Ein fehlerhafter Entwurf wurde durch den Speicherhandler gespeichert; seine
   Übernahme durch den Übernahmehandler abgelehnt. Das laufende Programm blieb gleich.
3. Nach `save-all flush`, vollständigem Prozessende und erneutem Serverstart
   waren laufendes Programm und fehlerhafter Entwurf unverändert vorhanden.
4. Nach Nachfüllen wurden weitere 64 Barren übertragen. Zu diesem Zeitpunkt
   waren null Spieler angemeldet. Die Testhilfe hielt den Testchunk geladen
   und versorgte den Controller mit Energie.

Der maschinenlesbare Nachweis liegt in
[validation/acceptance-server-2026-09-06.json](validation/acceptance-server-2026-09-06.json).
`node tools/tests/acceptance-evidence.cjs` überprüft die zugehörigen lokalen
Logs einschließlich Projektvergleich und Inventarmengen.

## Nativer Editor am echten Server

Ein angemeldeter Minecraft-Entwicklungsclient mit BambooCEF 0.1.1 öffnete den
Monaco-Editor im nativen CEF-Fenster. Die automatisierte Bedienung über den lokalen
CEF-Debugport bestätigte Speichern mit Serverquittung, Übersetzen und Übernehmen
eines gültigen Programms. Anschließend wurde ein fehlerhafter Entwurf automatisch
gespeichert, mit echten Compilerdiagnosen markiert und seine Übernahme im Editor
gesperrt. Die Prüfungen verwenden keine simulierten Serverantworten.

Nach vollständigem Serverneustart blieben der über diesen Client geschriebene
Entwurf und das zuvor übernommene Programm getrennt erhalten. Der neu gestartete
Client zeigte beim erneuten Öffnen denselben gespeicherten Entwurf.
Der Nachweis steht in
[validation/native-cef-2026-09-06.json](validation/native-cef-2026-09-06.json),
die tatsächliche Editoraufnahme in [previews/editor-native.png](previews/editor-native.png).

Das prüft die echte Verbindung zwischen Oberfläche, Java und Server automatisiert.
Manuelle Maus-/Tastaturbedienung sowie zwei gleichzeitig angemeldete echte Clients
wurden nicht geprüft; die Zweispielerfälle laufen mit FakePlayern.

Auf diesem Rechner verhinderte NeoForges frühes Ladefenster den Start wegen einer
fehlenden Primärmonitor-Erkennung. Ausschließlich im isolierten Clientverzeichnis
wurde deshalb in `config/fml.toml` `earlyWindowControl=false` gesetzt. Danach liefen
Minecraft und der native Editor mit Chromium 146. Die Installation des normalen
Spielclients wurde dabei nicht verändert.

## Prüfstand erneut verwenden

```powershell
.\gradlew.bat build acceptanceJar
.\tools\acceptance-server.ps1 -Java 'C:/Program Files/Java/jdk-21.0.12.1/bin/java.exe'
```

Nur bei einer neuen Installation benötigt das Skript zusätzlich `-Install` und
eine angenommene Minecraft-EULA (`-AcceptEula`). Es verändert ausschließlich
das eigene Testverzeichnis und entfernt keine anderen Moddateien oder Spielstände.

Serverkonsole: `fnacceptance setup` erzeugt die Fabrik nur, wenn dort noch kein
Testcontroller steht. `fnacceptance snapshot` liest den Zustand;
`fnacceptance saveBrokenDraft` prüft Speichern/Ablehnung;
`fnacceptance refill` füllt die Quellkiste auf. `stop` beendet den Server sauber.

Der Clientprüfstand startet mit `gradlew runAcceptanceClient` in einem eigenen
Spielverzeichnis. Er verwendet ausschließlich FactoryNetwork und BambooCEF.
Die optionale Testhilfe kann zusätzlich nach `build/acceptance-client/mods`
kopiert werden. `fnacceptance open FNAcceptance` auf dem Server öffnet dann das
echte Terminal-Menü; die Testhilfe öffnet daraus den echten BambooCEF-Editor.
`node tools/tests/editor-native.cjs --exercise` prüft diese Verbindung über CEFs
lokalen Debugport. Es akzeptiert ausschließlich die Seite im Acceptance-Verzeichnis.
Nach Speichern, Serverneustart, erneutem Clientstart und Öffnen prüft
`node tools/tests/editor-native.cjs --verify-reopen` den gespeicherten Entwurf lesend.
`node tools/tests/native-evidence.cjs` vergleicht die lokalen Clientnachweise mit
den Server-Snapshots vor und nach dem Neustart. Der Debugport gehört ausschließlich
zum lokalen Prüfstand und ist keine Voraussetzung für den normalen Editorbetrieb.
