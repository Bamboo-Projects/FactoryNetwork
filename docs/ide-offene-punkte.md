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
