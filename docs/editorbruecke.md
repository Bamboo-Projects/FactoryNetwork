# Die Brücke zu VS Code auf einem Server

Punkt 4.1, zweiter Teil. Der Einzelspielerfall läuft seit dem 26.08. über
`.fn-status.json` neben den Programmdateien. Auf einem Server gibt es diesen
Weg nicht: Der Ordner liegt dort, wo der Spieler keinen Dateizugriff hat.

**Was die Schnittstelle darf, ist entschieden** (26.08.): lesen und schreiben,
und der Serverbetreiber wählt in der Konfiguration zwischen *aus*, *lesen* und
*schreiben*. Siehe `entscheidungen.md`.

**Was offen ist, ist die Technik.** Dieses Dokument misst die Möglichkeiten und
legt eine Entscheidung vor. Es baut nichts.

---

## 1. Die Aufgabe, genau

VS Code läuft auf dem Rechner des Spielers. Der Controller läuft auf dem
Server. Dazwischen ist ein Minecraft-Protokoll, das nur der Spielclient
spricht.

Gebraucht werden zwei Richtungen:

- **Hin:** ein Programmtext aus VS Code wird zum Entwurf des Controllers.
- **Zurück:** Fehler mit Datei, Zeile und Spalte, dazu die Namen der
  Connectoren und Anzeigen und die Präfixe der Ressourcenarten. Also genau
  das, was `.fn-status.json` heute trägt.

**Wer darf das?** Derselbe Spieler, der auch im Terminal davorstehen dürfte —
`FnProtection` beantwortet das schon, mit `OFF`, `OWNER`, `OPS`. Die Brücke
darf keine zweite, mildere Antwort erfinden.

---

## 2. Drei Wege, gemessen

### A — Ein Port im Client

Der Spielclient öffnet auf `127.0.0.1` einen Port. VS Code verbindet sich
dorthin; der Client reicht durch, was er ohnehin darf.

| | |
|---|---|
| **Dafür** | Die Rechte sind schon geklärt: Was der Client darf, darf die Brücke. Kein neuer Weg in den Server. |
| **Dagegen** | Ein offener Port auf dem Rechner des Spielers. Jedes Programm auf diesem Rechner kann anklopfen — auch eines, das der Spieler nicht kennt. |
| **Kosten** | Ein kleiner Server im Client, ein Protokoll, ein Schalter in der Clientkonfiguration. |

**Der Einwand ist ernst.** Ein Port auf `localhost` ist für *jedes* Programm
des Benutzers erreichbar, nicht nur für VS Code. Wer ihn öffnet, gibt einer
beliebigen Anwendung die Möglichkeit, Programmtext in eine fremde Welt zu
schreiben — mit den Rechten des Spielers.

Abhilfe: ein **Geheimnis**, das der Client beim Öffnen erzeugt und in eine
Datei legt, die nur VS Code lesen muss. Wer den Port kennt, aber nicht die
Datei, kommt nicht durch. Das ist dasselbe Muster, das Jupyter und der
Gradle-Daemon benutzen.

### B — Ein Ordner, den der Client schreibt

Der Client legt die Projektdateien lokal ab — dort, wo VS Code sie ohnehin
öffnen würde — und hält sie mit dem Server im Gleichstand. Kein Port, keine
Verbindung: **derselbe Kanal wie im Einzelspieler**, nur dass der Client den
Ordner führt statt des Servers.

| | |
|---|---|
| **Dafür** | Nichts Neues nach außen. Der Weg, den `ProgramStatus` schon geht, gilt dann überall. VS Code braucht keine Erweiterungsänderung. |
| **Dagegen** | Der Gleichstand ist Arbeit: Wer gewinnt, wenn beide Seiten geändert haben? |
| **Kosten** | Zwei Pakete (Text hin, Status zurück), ein Ordner je Server und Welt, eine Regel für den Konflikt. |

**Der Konflikt ist die eigentliche Frage**, und sie hat eine ehrliche Antwort,
die es schon gibt: Der Controller hat einen Entwurf und einen laufenden Stand.
Was aus VS Code kommt, wird **Entwurf** — genau wie heute im Einzelspieler.
Übernommen wird im Spiel, mit Strg+Eingabe. Damit gewinnt niemand
automatisch, und der Spieler sieht, was er übernimmt.

### C — Gar nichts

Wer auf einem Server in einem Editor arbeiten will, arbeitet im Terminal im
Spiel. Das ist kein Scherz: Der Editor im Spiel kann Vervollständigung,
Tooltips, Sprungmarken und Fehler an der richtigen Zeile.

| | |
|---|---|
| **Dafür** | Kostet nichts und bricht nichts. |
| **Dagegen** | Der Punkt steht seit dem 24.08. auf der Liste, weil jemand ihn wollte. |

---

## 3. Empfehlung: B

Drei Gründe, in dieser Reihenfolge:

1. **Es macht nichts nach außen auf.** Der Einwand gegen A ist nicht
   ausgeräumt, sondern nur gemildert — ein Geheimnis in einer Datei ist
   genau so lange gut, wie niemand anderes diese Datei lesen darf. Auf einem
   gemeinsam genutzten Rechner ist das nicht mehr wahr.
2. **Es benutzt den Weg, der sich bewährt hat.** `.fn-status.json` und der
   Ordner neben der Welt laufen seit dem 25.08. Der Serverfall wird damit
   derselbe Fall und kein zweiter.
3. **Die Konfliktfrage hat schon eine Antwort.** Entwurf gegen laufenden
   Stand ist die Trennung, die diese Mod ohnehin macht. B erbt sie; A müsste
   sie nachbauen.

Der Preis von B, offen benannt: **Es ist mehr Code als A**, weil der
Gleichstand über zwei Pakete läuft und der Ordner je Server und Welt getrennt
liegen muss. Und es bleibt an den Spielclient gebunden — wer VS Code offen
hat, aber Minecraft geschlossen, sieht nichts. Bei A wäre das genauso.

---

## 4. Wie B in Schnitte zerfällt

1. **Der Ordner im Client.** Ein Verzeichnis je Serveradresse und Welt, unter
   dem Spielordner. Der Client schreibt hinein, was der Server ihm schickt.
2. **Der Rückweg.** Ein Paket vom Server zum Client mit demselben Inhalt, den
   `ProgramStatus` heute schreibt. Der Client legt daraus die Datei an — der
   Schreiber existiert schon und ist geprüft.
3. **Der Hinweg.** Ein Dateiwächter im Client schickt geänderten Text an den
   Server; der Server prüft `FnProtection` und legt ihn als **Entwurf** ab.
   Die Stufe `lesen` überspringt diesen Schnitt.
4. **Die Konfiguration.** Erst jetzt: `bridge = OFF | READ | WRITE` auf dem
   Server, ein Schalter im Client. Vorher wäre sie ein leerer Abschnitt und
   damit eine Frage an den Betreiber, die niemand beantworten kann — dieselbe
   Regel wie bei 4.2.

Schnitt 1 und 2 sind für sich brauchbar: Sie bringen Fehler und Gerätenamen
auf den Rechner des Spielers, ohne dass irgendetwas hineinschreiben kann.
Das ist die Stufe `lesen`, und sie ist der größere Teil des Nutzens.

---

## 5. Was zu entscheiden ist

**Ob B statt A.** Alles andere folgt daraus. Die Empfehlung steht oben; der
Gegeneinwand wäre, dass A weniger Code ist und ein Sprachserver ohnehin
irgendwann einen Port braucht — dann wäre B ein Umweg.

Zweitens, falls B: **Ob der Ordner auch ohne offenes Spiel bestehen bleibt.**
Dafür spricht, dass man dann wenigstens den letzten Stand lesen kann; dagegen,
dass ein veralteter Stand schlimmer ist als keiner, weil man ihm ansieht,
dass er da ist, aber nicht, dass er alt ist.
