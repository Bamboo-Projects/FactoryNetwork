# Änderungen

## 1.0.0 — 26.08.2026

Die Erweiterung kann jetzt das, was man von einem Editor für eine Sprache
erwartet.

**Neu:**

- **Gliederung.** Was eine Datei erklärt, steht in der Übersicht — Funktionen,
  Worker, Ereignisse, Anzeigen, globale Werte, Gruppen, Vorlagen und
  Multiblöcke, jedes mit seiner Art.
- **Sprung zur Deklaration.** Über das ganze Projekt und nicht nur über die
  eigene Datei: Der Namensraum ist einer, und ein `fn` aus `erz/brecher.mf`
  wird von `main.mf` gerufen. Ist ein Name doppelt vergeben, kommen beide
  Stellen — geraten wird nicht.
- **Umbenennen.** Ebenfalls über das ganze Projekt. Nur erklärte Namen lassen
  sich umbenennen: Gerätenamen stehen am Block in der Welt und nicht in einer
  Datei.
- **Schnellkorrekturen.** Wo das Spiel weiß, was gemeint war — `chemiacl:` statt
  `chemical:` —, steht der Vorschlag als anwendbare Korrektur da. Der Vorschlag
  kommt aus dem Übersetzer; die Erweiterung rät nicht selbst.

**Geändert:**

- Der Prüflauf `check.js` läuft mit `./gradlew test` mit. Vorher musste ihn
  jemand von Hand starten, und niemand tat es zuverlässig.

## 0.2.0

- Vervollständigung, Erklärung beim Zeigen, Signaturhilfe
- Fehler aus dem Spiel als Marker im Editor, über `.fn-status.json`
- Die Ressourcenarten kommen aus dem laufenden Spiel; ohne Spiel bleiben die
  eingebauten

## 0.1.0

- Syntaxhervorhebung und Bausteine für `.mf`
