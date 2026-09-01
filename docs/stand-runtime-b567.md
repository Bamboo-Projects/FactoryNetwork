# B5, B6, B7: Verwalter, Wächter, Reihenfolge

Stand: 1. September 2026. Die drei Blöcke stehen und greifen ineinander: Der
Verwalter weiß, welche Browser offen sind; die Reihenfolge beim Beenden nutzt
dieses Wissen; und der Wächter fängt den Fall auf, für den keine Reihenfolge
mehr hilft — den harten Abbruch.

---

## In einem Satz

Ein normaler Ausgang lässt **null** Hilfsprozesse zurück, ein harter Abbruch
mit Wächter ebenfalls, und der Verwalter zählt über einen ganzen Lebenslauf
hinweg sauber auf null zurück.

---

## B5 — BrowserManager

**Zwei Listen, nicht eine.** Eine Sitzung ist nicht zu, wenn jemand
`close()` gerufen hat: `CefBrowser.close(true)` ist eine Bitte, und die
Bestätigung — `onBeforeClose` — kommt später aus der Nachrichtenschleife. Wer
nur eine Liste führt, hält beim Herunterfahren entweder zu früh für fertig
oder wartet auf etwas, das er gerade selbst weggeworfen hat.

```text
register  →  offen
closing   →  offen − 1, wartet + 1      (close(true) ist raus)
closed    →  wartet − 1                 (onBeforeClose ist da)
```

### Gemessen im Spiel

Ein Lebenslauf mit vier Sitzungen (Selbsttest plus drei Zyklen):

```text
Browser registriert: Sitzung 1 (256x256)    — offen: 1
Browser schließt:    Sitzung 1              — offen: 0, warten: 1
Browser entfernt:    Sitzung 1              — offen: 0, warten: 0
… dasselbe für Sitzung 2, 3 und 4 (1920x1062)
```

**Die Bestätigung kam jedes Mal in derselben Sekunde** wie das Schließen. Der
Zähler stand nach jedem Zyklus wieder auf null.

### Und was das Spiel nicht zeigen konnte

`closeAll()` sah im Spiel **immer eine leere Liste**: Die Bildschirme schließen
ihre Browser selbst, bevor das Herunterfahren beginnt. Der interessante Fall
ist der andere, und der steht in sieben Prüfläufen
(`BrowserManagerTest`, alle grün):

| Prüfung | wogegen sie schützt |
|---|---|
| `closeAll` mit fünf offenen Sitzungen | jede meldet sich mitten in der Schleife ab — ohne Kopie eine `ConcurrentModificationException` |
| `closeAll` dreimal hintereinander | ein zweiter Aufruf darf nichts kaputtmachen |
| aus dem leeren Zustand | beim Beenden darf nichts werfen |
| Warten auf eine Bestätigung, die erst beim Pumpen kommt | das ist Chromiums echtes Verhalten |
| Frist, wenn die Bestätigung ausbleibt | sonst hinge das Spiel für immer |
| eine Sitzung, die beim Schließen wirft | die anderen müssen trotzdem zugehen |

---

## B6 — ProcessGuard

**Was hilft, ist nichts, was der Prozess tut, sondern etwas, was das
Betriebssystem für ihn tut.** Windows kennt Job Objects: eine Klammer um eine
Gruppe von Prozessen. Verschwindet die letzte Handhabe darauf, beendet Windows
alles darin. Beim Sterben eines Prozesses verschwindet sie immer — auch beim
härtesten Abbruch, denn Handhaben schließt das Betriebssystem, nicht das
Programm.

```text
CreateJobObject
SetInformationJobObject(JobObjectExtendedLimitInformation,
                        LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE)
AssignProcessToJobObject(eigener Prozess)
```

**Vor `CefApp.startup()`**, denn Kindprozesse erben die Zugehörigkeit — wer
später klammert, klammert die schon gestarteten Helfer nicht mehr ein.

### Warum JNA und ein eigenes Interface

Die Fremdfunktionsschnittstelle ist unter Java 21 eine Vorschau und verlangt
`--enable-preview`; Spieler setzen keine JVM-Schalter, und eine Mod kann sie
nicht erzwingen. JNA liegt ohnehin im Laufzeitpfad, weil Minecraft es über
OSHI mitbringt.

JNAs eigenes `Kernel32` kennt die drei Job-Funktionen nicht und `WinNT` die
Konstanten auch nicht — nachgesehen im Jar, nicht vermutet. Also ein eigenes
Interface mit drei Funktionen und einer Struktur.

**Ein falsches Bild von der Struktur schaltet den Wächter ab, statt den Start
zu kosten.** `JOBOBJECT_EXTENDED_LIMIT_INFORMATION` misst auf 64-Bit-Windows
144 Bytes; stimmt das nicht, gibt es eine Zeile im Protokoll und keinen Job.
Ein Wächter, der den Start verhindert, wäre schlimmer als keiner.

### Gemessen

```text
ProcessGuard: aktiv — Job Object mit KILL_ON_JOB_CLOSE (schon im Job: nein)
```

**„Schon im Job: nein" ist die wichtigere Hälfte der Zeile.** Sie sagt, dass
Gradle den Client nicht bereits in einer eigenen Klammer hält — die früheren
Null-Messungen waren also nicht Gradles Verdienst, und die Klammer ist jetzt
unsere. Unter einem echten Launcher kann das anders aussehen; verschachtelte
Jobs gehen seit Windows 8, ein Fehlschlag käme als Fehler 5 mit
entsprechendem Text ins Protokoll.

| Messung | Helfer vorher | danach |
|---|---|---|
| harter Abbruch mit Wächter | 5 | **0** |
| normaler Ausgang | 4 | **0** |

Zum Vergleich ohne Wächter: dreimal gemessen, einmal eine Waise.

---

## B7 — Geordnetes Herunterfahren

**Die Reihenfolge ist der ganze Inhalt.**

```text
1. BrowserManager.closeAll()          alle bitten, zuzugehen
2. awaitClosed(2000 ms, WebPump)      pumpen, bis jede bestätigt hat
3. backend.close()                    erst dann abräumen
```

Wer bei 3 anfängt, räumt CEF ab, während es noch Browser schließen wollte —
und genau daraus entstehen die Hilfsprozesse, die stehenbleiben.

**Das läuft im Renderthread, und das muss es:** Gepumpt wird dort, und nur wer
pumpt, bekommt die Bestätigungen zu sehen. Der Threadname steht deshalb in der
Protokollzeile — ein späterer Aufrufer vom falschen Thread soll auffallen,
statt still zu hängen.

Die Frist von zwei Sekunden ist großzügig für den Normalfall (im Spiel kam die
Bestätigung in derselben Sekunde) und kurz genug, dass niemand denkt, das
Spiel hänge. Läuft sie ab, schreibt sie auf, wer gefehlt hat.

### Gemessen

```text
Web-Runtime fährt herunter — im Thread Render thread, offen: 0
Chromium ist unten: TERMINATED
Web-Runtime ist unten — offen: 0, ohne Bestätigung: 0
jcef_helper nach normalem Ausgang: 0
```

Der Ausgang wurde über ein Schließen des Fensters ausgelöst, nicht über einen
Abbruch — der Client beendete sich mit Rückgabewert null.

---

## Die Meldung ohne Stapel

Unverändert offen. Neu ist nur das Werkzeug: `-Ptrace` schreibt jetzt je
Browser eine Spur mit Nummer und Threadnamen —

```text
createImmediately · createBrowser zurück · resize · setFocus
erstes onPaint · onBeforeClose
```

— damit sich die Kopfzeilen dazwischen einordnen lassen. **Gelaufen ist diese
Spur noch nicht:** Die Sitzung des Rechners verlor ihren Bildschirm
(`glfwGetPrimaryMonitor failed`), und ohne Bildschirm startet der Client
nicht. Drei Versuche, dreimal derselbe Abbruch.

Was vorher bekannt war, gilt weiter: nur auf dem neuen Weg, ein- bis siebenmal
je Browsererzeugung, kein eigener Rückruf meldet etwas, kein Stapel auch mit
`-XX:-OmitStackTraceInFastThrow`, `-Xcheck:jni` bricht den Prozess vorher ab,
keine messbare Folge.

---

## Was noch offen ist

```text
harter Abbruch mit Wächter    einmal gemessen, Wiederholungen blockiert
Spur um die Ausnahme          gebaut, nicht gefahren
Handprüfung in Monaco         Liste steht, nicht gefahren
```

Alle drei hängen an derselben Ursache: Der Client startet auf diesem Rechner
gerade nicht, weil die Sitzung keinen Bildschirm mehr hat. Das ist eine
Umgebungsfrage, kein Befund über den Code.

Die Liste für die Handprüfung liegt als `handpruefung-monaco.md` bereit —
zwanzig Zeilen, je eine Spalte für beide Wege.

---

## Vorschlag für den nächsten Schritt

Nicht ausgeführt, nur vorgeschlagen:

```text
1. die drei blockierten Läufe nachholen, sobald ein Bildschirm da ist
   ./gradlew runClient -Pfnruntime -Pide -Ptrace     Spur zur Ausnahme
   harter Abbruch, drei Wiederholungen               Wächter
2. Handprüfung in Monaco fahren
danach erst
B8  MCEF-Importe raus
B9  MCEF entfernen
```
