# Manifold in VS Code

Syntaxhervorhebung, Klammernpaare und Bausteine für `.mf`-Dateien — die
Sprache, in der Factory Network programmiert wird.

## Wozu

Der Controller legt sein Programm als Datei neben die Welt:

```
<Weltordner>/factorynetwork/controller_overworld_10_64_-20.mf
```

Diese Datei ist das Programm. Wer sie speichert, hat sie eine Sekunde später
im Spiel übernommen — und was im Terminal übernommen wird, steht sofort in der
Datei. **Wer zuletzt geschrieben hat, gewinnt.**

Damit kann man alles benutzen, was der Bildschirm im Spiel nicht kann:
mehrere Cursor, Suchen über alle Dateien, Git.

## Einbauen

Ohne Marktplatz, weil die Erweiterung nichts tut, was einen Marktplatz
rechtfertigt — sie ist eine Grammatik und ein Satz Bausteine.

**Windows**

```
xcopy /E /I editor\vscode %USERPROFILE%\.vscode\extensions\manifold-0.1.0
```

**Linux und macOS**

```
cp -r editor/vscode ~/.vscode/extensions/manifold-0.1.0
```

Danach VS Code neu starten. Eine `.mf`-Datei sollte farbig sein; unten rechts
steht „Manifold".

## Bausteine

Tippen und Tabulator drücken:

| Kürzel | Was daraus wird |
|---|---|
| `worker` | ein Worker mit `from`, `to`, `filter` und `rate` |
| `workermaintain` | ein Worker, der einen Sollstand hält |
| `fn` | eine Funktion |
| `on` | ein Ereignisblock, mit Auswahl der bekannten Ereignisse |
| `event` | ein eigenes Ereignis |
| `await` | auf ein Ereignis warten |
| `awaittimeout` | warten, aber nicht ewig |
| `display` | eine Anzeigetafel |
| `group` | mehrere Connectoren unter einem Namen |
| `multiblock` | eine Anlage aus mehreren Geräten |
| `move` | ein einzelner Transport |

## Was fehlt

Keine Fehlerprüfung und keine Vervollständigung von Gerätenamen. Beides
bräuchte einen Sprachserver, der weiß, was gerade in der Welt steht — und
damit eine Verbindung zum laufenden Spiel. Solange es die nicht gibt, ist das
Terminal die Stelle, an der Fehler stehen: Was nicht übersetzt, wird nicht
übernommen, und im Reiter **Code** steht, warum.
