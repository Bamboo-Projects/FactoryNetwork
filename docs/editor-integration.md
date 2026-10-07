# Terminal, BambooCEF und Geräte — Stand 0.1.1

Historischer Integrationsnachweis. Der neuere Prüfstand steht in
[stabilisierung-0.1.2.md](stabilisierung-0.1.2.md).

Der große Codeeditor am Terminal öffnet die lokale Monaco-Oberfläche über
BambooCEF. Er verwendet das Projekt des geöffneten Terminal-Menüs, dessen
Berechtigungen, den Manifold-Compiler und die vorhandene Controller-Laufzeit.
Die Entwicklungsdemo bleibt getrennt als `demo.html` erreichbar.

## Was zusammenarbeitet

- Projektdateien und Ordner, Tabs, Vervollständigung mit Namen aus dem Netzwerk
  und Compilerdiagnosen mit Sprung zur Quellstelle.
- Speichern mit Strg+S, Prüfen mit Strg+Umschalt+B, Übernehmen mit Strg+Eingabe.
  Entwürfe werden außerdem nach einer Sekunde ohne Eingabe gespeichert.
- Nummerierte Speicherbestätigungen verhindern, dass verspätete Antworten neue
  Eingaben ersetzen. Eine Ablehnung bleibt sichtbar und der Text lokal erhalten.
- Eine fehlgeschlagene Übernahme verändert das laufende Programm nicht. Der
  Entwurf wird getrennt gespeichert; Dateisperren gelten auch beim Übernehmen.
- Die Browsersitzung ist an das konkrete geöffnete Terminal-Menü gebunden.
  Beim Schließen kehrt sie zum Terminal zurück. Während BambooCEF vorbereitet
  wird, stehen Wiederholen, klassischer Editor und Zurück zur Verfügung.
- Terminal mit eigener Tastatur-, Bildschirm- und Oberseitentextur; dunkle leere
  Laufwerksschächte und eine eigene Darstellung für Chemikalienzellen.

## Versionen und Paket

FactoryNetwork und BambooCEF sind auf **0.1.1** angehoben. FactoryNetwork verlangt
BambooCEF ab 0.1.1 auf dem Client, weil die Brücke nun `WebSurface.postMessage`
verwendet. Der FactoryNetwork-Netzwerkkanal ist Version **2**: Client und Server
müssen die passende neue FactoryNetwork-Version verwenden. BambooCEF bleibt
eine separate Client-Mod. Monaco und sein Worker liegen lokal im Mod-Paket.

## Nachweise

- `gradlew test build :BambooCEFMC:test`: gemeinsamer Build erfolgreich.
- FactoryNetwork: **595 JUnit-Tests**, BambooCEF: **156 JUnit-Tests**, keine Fehler.
- `gradlew test runGameTestServer`: **364 erforderliche GameTests erfolgreich**,
  einschließlich abgelehnter Übernahme, Entwurf/Persistenz und Dateisperren.
- **8 Node-Tests** für den Editorzustand. `webEditorCheck` läuft auch nach dem
  Gradle-Testlauf, sofern Node installiert ist.
- Browserprüfung mit echtem Monaco unter `file://`, einschließlich
  Speicherrückmeldung und kompakter Darstellung; Einzelheiten in
  [web-editor-2026-09-06.md](web-editor-2026-09-06.md).
- Texturen, Modellreferenzen und UVs geprüft; Erzeugung und Vorschau sind in
  [geraete-assets.md](geraete-assets.md) dokumentiert.

**Noch nicht manuell geprüft:** Öffnen, Texteingabe und Darstellung des neuen
Editors im nativen CEF innerhalb eines laufenden Minecraft-Clients. Die
Browseraufnahme zeigt gekennzeichnete Testdaten; die Gerätevorschau rendert
Modelle ohne Minecraft-Weltbeleuchtung. Beides ersetzt diese Abnahme nicht.

Für die Abnahme: an einem verbundenen Terminal den großen Editor öffnen,
eine Datei bearbeiten und speichern, einen Compilerfehler beheben, übernehmen
und die Worker im Netzwerk prüfen. Danach Editor und Welt neu öffnen und den
gespeicherten Entwurf vergleichen; mit zwei Spielern zusätzlich Dateisperren
prüfen. Falls CEF noch lädt, den klassischen Editor über den Wartebildschirm öffnen.
