# Lifecycle — was beim Öffnen und Schließen wirklich passiert

Stand: 31. August 2026, lokal gemessen, 1920×1080, Browser mit 60 Hz.

---

## Die kurze Antwort

**Kein Leck beim Schließen.** Die Prozesse verschwinden in unter einer
Sekunde, und über drei vollständige Zyklen wächst nichts.

Das Wachstum aus Schritt I — zehn Renderer, rund 660 MB — kam
**ausschließlich vom Navigieren**. Damit ist es kein Hindernis für die
Laufzeitumgebung, sondern eine Anweisung an unseren eigenen Entwurf.

---

## Der Versuch

`web/ide/LifecycleBenchmark.java`, gestartet mit `-Pide -Plifecycle`. Drei
Zyklen, je fünfzehn Sekunden offen und fünfzehn geschlossen, **immer dieselbe
Adresse** — es wird nie navigiert, damit ausschließlich das Schließen des
Browsers gemessen wird.

Der Ablauf hängt am Takt des Clients und nicht am Bild: Ein geschlossener
Bildschirm zeichnet nicht, und ein Ablauf, der nur beim Zeichnen weiterläuft,
bliebe genau an der Stelle stehen, die er messen soll.

Von außen schreibt `tools/procwatch.ps1` jede Sekunde mit, welche Prozesse
laufen und wie viel Speicher sie halten. Ein Prozess, der auf sein eigenes
Aufräumen wartet, ist ein schlechter Zeuge.

## Der Verlauf

```text
Zeit       Zustand              renderer  gpu  utility   Summe
18:39:24   vor dem 1. Öffnen        0      1      1       77 MB
18:39:45   Zyklus 1 offen           2      1      2      333 MB
18:39:54   Zyklus 1 geschlossen     1      1      2      186 MB
18:40:15   Zyklus 2 offen           2      1      2      342 MB
18:40:30   Zyklus 2 geschlossen     1      1      2      190 MB
18:40:45   Zyklus 3 offen           2      1      2      346 MB
18:41:00   Zyklus 3 geschlossen     1      1      2      191 MB
18:41:37   dreißig Sekunden später  1      1      2      191 MB
```

**Über drei Zyklen: 186 → 190 → 191 MB.** Fünf Megabyte Zuwachs auf drei
vollständige Runden, und der letzte Wert bleibt danach eine halbe Minute lang
unverändert. Das ist Rauschen, kein Wachstum.

## Wie lange das Aufräumen dauert

Sekundengenau um das Schließen von Zyklus 1 und 2:

```text
18:39:52   2 Renderer   340 MB
18:39:53   ← geschlossen (Marke 18:39:53,4)
18:39:54   1 Renderer   186 MB

18:40:22   2 Renderer   343 MB
18:40:23   ← geschlossen (Marke 18:40:23,4)
18:40:23   1 Renderer   190 MB
```

**Unter einer Sekunde**, beide Male. Der Speicher fällt im selben Schritt
zurück. Beim Öffnen dauert es etwas länger: Die Marke steht bei 18:40:08,4,
der zweite Renderer erscheint bei 18:40:09 mit 295 MB und ist bei 18:40:10
mit 345 MB vollständig — **eine bis zwei Sekunden**.

## Der Grundzustand, der bleibt

Vor dem allerersten Browser laufen 0 Renderer und 77 MB. Nach dem ersten
Zyklus bleibt dauerhaft **ein Renderer mit rund 190 MB** stehen, auch wenn
nichts offen ist. Er wächst nicht und ist
kein Leck; vermutlich ist es Chromiums Reserve-Renderer, ein vorgehaltener
Prozess für das nächste Öffnen. Für die Beurteilung kommt es auf die Deutung
nicht an: Gemessen ist, dass er konstant bleibt, und das allein trägt die
Schlussfolgerung.

Für die Beurteilung heißt das: **Ein Leck wäre monotones Wachstum über die
Zyklen**, nicht „mehr als null Renderer nach dem Schließen".

---

## Die Unterscheidung, um die es ging

| | Befund |
|---|---|
| **Navigation** (`location.href`) | Renderer bleiben liegen — zehn Stück, rund 660 MB nach einer Messreihe (Schritt I) |
| **Browser-Lebenslauf** (öffnen/schließen) | **sauber** — Rückkehr auf den Ausgangswert in unter einer Sekunde |

**Folge für den Entwurf der Oberfläche:** Sie darf zwischen ihren Ansichten
nicht vollständig navigieren. Kein `location.href` für den Wechsel von einem
Programm zum nächsten, von der Übersicht in den Editor, von einem Reiter zum
anderen. Stattdessen eine Anwendung, die geladen bleibt und ihren Inhalt
austauscht — Routing im Browser, Zustand im DOM.

Das ist keine Einschränkung, sondern die Bauform, die eine Editor-Oberfläche
ohnehin haben will: Monaco neu zu laden kostet jedes Mal seinen vollen
Startaufwand.

Die Messabläufe dieses Projekts navigieren weiterhin — dort ist es richtig,
weil jede Stufe einen frischen Zustand braucht. Sie sind der Grund für den
Befund aus Schritt I und kein Hinweis auf die Oberfläche.

---

## Der Cleanup-Vertrag

Abgelesen am Quelltext des Forks, nicht aus der Dokumentation.

### Was `close()` tut

```java
public void close(boolean force) {
    if (isClosing_ || isClosed_) return;
    if (force) isClosing_ = true;
    N_Close(force);          // kehrt sofort zurück
}
```

**Asynchron.** Der Aufruf kehrt zurück, bevor irgendetwas geschlossen ist.

### Was danach kommt

```java
public synchronized boolean doClose() {
    if (closeAllowed_) return false;   // Schließen darf fortfahren
    return true;                       // Schließen abbrechen
}

public synchronized void onBeforeClose() {
    isClosed_ = true;
    if (request_context_ != null) request_context_.dispose();
    // DevTools des Elternbrowsers abräumen
}
```

CEF ruft erst `doClose()`, dann `onBeforeClose()`. Beide kommen später und aus
CEFs eigenem Takt.

**Deshalb steht `setCloseAllowed()` in unserem Konstruktor** und nicht beim
Schließen: Ohne dieses Flag gibt `doClose()` `true` zurück, CEF bricht ab, und
der Browser schließt nie. Es ist eine Erlaubnis, die vorher vorliegen muss.

### Wann wir was freigeben dürfen

Unser `close()`:

```java
closed = true;                 // zuerst
cursorSink = null;
browser.close(true);           // asynchron
texture.close();               // sofort danach
popupTexture.close();
```

Die Texturen werden freigegeben, während CEF noch schließt. **Das ist sicher,
aber nicht selbstverständlich**, und der Grund gehört festgehalten:

1. `closed = true` steht **vor** allem anderen.
2. `frame()` prüft `closed` als Erstes und kehrt sofort zurück.
3. `onPaint` kommt im Render-Thread an — demselben Thread, auf dem `close()`
   läuft. Es kann also gar kein Bild zwischen Punkt 1 und der Freigabe
   dazwischenkommen.

Fiele eine dieser drei Bedingungen weg — etwa wenn Chromiums Schleife je aus
einem anderen Thread gepumpt würde —, wäre das Freigeben ein Fehler. Das ist
die Stelle, die bei einem Umbau der Laufzeitumgebung als Erstes zu prüfen ist.

| | wann |
|---|---|
| Eingabe und Fokus abmelden | vor `close()`; geschieht in `removed()` des Bildschirms |
| Java-Referenz auf den Browser loslassen | nach `close()`; die Sitzung hält sie bis zum Ende |
| Haupttextur freigeben | nach `closed = true`, siehe oben |
| Popup-Textur freigeben | ebenso; sie hängt an derselben Sitzung |
| `CefClient` freigeben | nie je Browser — er gehört der Laufzeitumgebung |

### Wenn Minecraft beendet wird

MCEF hängt sich mit einem Mixin in `Minecraft.close` (TAIL) und ruft dort
`CefApp.dispose()`. Beim geordneten Beenden läuft das durch.

**Beim harten Abbruch nicht.** In dieser Sitzung wurde der Client mehrfach mit
`Stop-Process -Force` beendet — dabei blieben **acht Chromium-Hilfsprozesse
stehen** und mussten einzeln abgeräumt werden. Das ist kein Fehler unseres
Codes, sondern die Folge davon, dass Chromiums Kindprozesse ihren Elternteil
überleben, wenn niemand sie abmeldet.

Für die eigene Laufzeitumgebung ist das eine Anforderung: Die Hilfsprozesse
brauchen eine Bindung an den Elternprozess, die auch ein Absturz einhält —
unter Windows eine Job-Object-Zuordnung mit
`JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`. Sonst sammeln sich bei jedem Absturz
eines Spielclients mehrere hundert Megabyte an Waisen an.

## Ist der vorhandene Zustandsautomat genug?

Ja. Die Sitzung kennt genau ein Flag (`closed`), das früh gesetzt wird und
alles Weitere abschneidet. Ein größerer Automat mit Zuständen für „schließt
gerade" und „geschlossen" würde nichts absichern, was nicht schon durch die
Thread-Zugehörigkeit abgesichert ist.

Was fehlt, ist nicht Struktur, sondern eine **Prüfung**: Es gibt keinen
Rückruf, der bestätigt, dass `onBeforeClose` tatsächlich kam. Solange die
Prozesszahlen stimmen, ist das verschmerzbar; bei der eigenen
Laufzeitumgebung sollte ein `CefLifeSpanHandler` das melden, damit ein
hängengebliebener Browser auffällt, statt still Speicher zu halten.
