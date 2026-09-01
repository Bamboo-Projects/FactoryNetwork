# Offene Punkte der IDE

Nicht die Laufzeitumgebung, sondern die Seite darin — siehe
[`grenze-runtime-ide.md`](grenze-runtime-ide.md). **Keiner dieser Punkte
sperrt die Freigabe der Laufzeitumgebung.**

Gefunden bei der Handprüfung am 1. September 2026
([`handpruefung-monaco.md`](handpruefung-monaco.md)), dort aber ausdrücklich
nicht als Befund gezählt.

---

## 1. Explorer: die Ordner fehlen in der Seitenleiste

**Beobachtet.** Die Seitenleiste zeigt zwei Überschriften — `programme` und
`bibliothek` — und darunter eine flache Liste von Dateien. Ordner als
eigenständige, auf- und zuklappbare Einträge gibt es nicht.

**Warum das heute so ist.** Der Baum steht als festes Gerüst in
`index.html`: drei `div` mit der Klasse `datei`, zwei mit der Klasse `ordner`.
Die Klasse `ordner` ist dabei nur eine Schriftauszeichnung ohne Verhalten —
kein Zuklappen, kein Zeichen davor, keine Einrückung nach Tiefe.

**Was es braucht.** Eine echte Baumdarstellung, die eine Verzeichnisstruktur
entgegennimmt statt eines festen Gerüsts. Damit hängt der Punkt an der Frage,
woher die Dateiliste kommt — heute ist sie erfunden.

## 2. Registerkarten mit der mittleren Maustaste schließen

**Gewünscht.** Ein Klick mit dem Mausrad auf eine Registerkarte oben schließt
sie, so wie in jedem Browser und in VS Code.

**Was dafür schon da ist.** Die Laufzeitumgebung reicht die mittlere Maustaste
durch — sie ist in der Testmatrix (B3e) abgedeckt und kommt als
`CefMouseEvent` mit der richtigen Tastennummer an. Es fehlt allein die
Behandlung in der Seite: ein `auxclick` mit `button === 1` auf `.tab`.

**Klein.** Ein paar Zeilen, sobald es die Registerkarten wirklich als Daten
gibt und nicht als festes Gerüst — dieselbe Voraussetzung wie bei Punkt 1.

---

Gefunden bei der Abnahme des Nachladens am 1. September 2026
([`stand-runtime-auslieferung.md`](stand-runtime-auslieferung.md)). Beides
liegt auf der Seite der Oberfläche; die Laufzeitumgebung liefert die
Auskünfte, die es dafür braucht.

## 3. Während des Downloads: Fortschritt zeigen und von selbst öffnen

**Beobachtet.** Beim ersten Start ohne Laufzeitumgebung zeigt die Oberfläche
„Die Web-Runtime steht nicht bereit." und bleibt dabei — auch, wenn der
Download zwei Sekunden später durch ist. Der Spieler muss die Oberfläche
selbst erneut öffnen und weiß nicht, dass sich das lohnt.

**Was dafür schon da ist.** Der Zustand `NOT_DOWNLOADED` sagt, dass geladen
wird, `RuntimeInstall.downloading()` sagt, ob noch, und das Protokoll kennt
den Fortschritt in Schritten von zwanzig Megabyte. Ein zweiter Griff über
`WebSupport.retry()` geht durch, sobald der Ordner da ist.

**Was es braucht.** Eine Anzeige „wird geladen, x von y MB" statt des
Standsatzes, und ein Öffnen von selbst, wenn `downloading()` auf falsch fällt.

## 4. Nach „Kein Browser zu haben" nicht weitermachen

**Beobachtet.** Bekommt `BrowserScreen` beim Öffnen keine Sitzung, laufen die
Schritte danach trotzdem — Schema anmelden, Konsole anzapfen, Hintergrund
bauen — und jeder davon schreibt eine Warnung mit Stapel ins Protokoll. Drei
Warnungen für einen Grund, der schon in der ersten Zeile stand.

**Klein.** Nach dem ersten Nein aufhören; die drei Aufrufe hängen ohnehin an
einer Sitzung, die es dann nicht gibt.
