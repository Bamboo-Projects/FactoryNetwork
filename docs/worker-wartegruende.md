# Worker: tatsächliche Wartegründe im Terminal

Stand: 6. September 2026.

Der Netzwerk-Tab erhält Workerstatus und Detailtext jetzt mit dem regelmäßig
aktualisierten `FlowStatePacket`, nicht nur beim Öffnen als Zeichenkette
`name: STATUS`. Die Übertragung enthält getrennt Name, Status, Detail und die
bisher bewegte Gesamtmenge. Die neue Wire-Fassung muss auf Client und Server
übereinstimmen.

Im Terminal bleibt der Workername neben seinem Datenverbrauch stehen; darunter
wird der Grund vollständig umgebrochen. Der Tab zählt auch außerhalb des
sichtbaren Ausschnitts liegende Worker, damit man sie durch Scrollen erreicht.
Die Statusbezeichnungen kommen aus `de_de`/`en_us`; ergänzende Gerätemeldungen
verwenden weiterhin den vorhandenen Runtime-Detailtext.

Gemessene Gründe:

- `WAITING_CONDITION`: `when` wurde erfolgreich als falsch ausgewertet.
  Ein Auswertungsfehler bleibt `HALTED` mit Fehlerdetail.
- `WAITING_SOURCE`: Die ausgewählte Quelle hat nichts Passendes zur Entnahme
  geliefert; ein sichtbar gefülltes, aber nicht entnehmbares Inventar gilt nicht
  als leere Quelle und wird entsprechend allgemein beschrieben.
- `WAITING_TARGET`: Das Ziel hat die angebotenen Ressourcen nicht angenommen.
  Insbesondere bedeutet das nicht zwingend, dass jedes Fach voll ist.
- `WAITING_BANDWIDTH`: Das errechnete Bytebudget des konkreten Pfads war erschöpft.
- `WAITING_ENERGY`: Der abgefragte Netzwerk-Stromvorrat hat keine Energie geliefert.
- `WAITING_PATH`: Die errechnete Anfangslatenz des Pfads läuft noch ab.
- `NOT_RUN`: Der deklarierte Worker hatte noch keinen Ausführungsversuch.

Der Controller kann einen gemessenen Gesamtstillstand (`WAITING_SERVER`,
`WAITING_BOOT`, `WAITING_ENERGY`) beim Snapshot überlagern. Das verändert nicht
den letzten Transferzustand in der Runtime. Bei Chemikalien, deren API lediglich
eine Nullmenge zurückgibt, wird bewusst kein genauer Quell- oder Zielgrund
behauptet.

Frühere Warte-/Fehlerzustände werden bei einem neuen Versuch zurückgesetzt. Ein
wieder erreichbarer Connector kann dadurch wieder arbeiten. Nicht rückholbare
Handoff-Fehler bleiben dagegen bis zum Programmneustart verriegelt, damit ein
fehlerhafter Fremdhandler nicht wiederholt zusätzliche Ressourcen hinterlässt.

Zusätzliche Handoff-Korrektur: Wenn die Quelle bereits die simulierte Entnahme
verweigert, wird das Ziel nicht angefasst und nicht als voll gemeldet. Weitere
Quellslots werden trotzdem geprüft.

Gezielte Regressionstests:

```powershell
.\gradlew.bat test --tests '*WorkerStatusTest' --tests '*WorkerStatePacketTest'
.\gradlew.bat runGameTestServer
```

Die Tests decken Zustandswechsel, Fehlervorrang, verriegelte Handoff-Fehler,
Client-Zurücksetzen, Paket-Roundtrip, globale Pause ohne Runtime-Mutation und
nicht entnehmbare Quellslots ab. Die zwei Handoff-Fälle liegen in der eigenen
`HandoffGameTests`-Klasse: echte Item-Registries benötigen einen gestarteten
NeoForge-Loader. Ein manueller Vanilla-Bootstrap im JUnit-Prozess reicht dafür
nicht. Die zehn State-/Packet-Unit-Tests wurden erfolgreich ausgeführt.
Der tatsächliche Ingame-Test des Terminals
gehört zur gemeinsamen Client-/Server-Abnahme.
