# B8 und B9: MCEF ist raus

Stand: 1. September 2026, unmittelbar nach der bestandenen Handprüfung. Die
beiden Blöcke sind klein, und das ist die eigentliche Nachricht.

---

## In einem Satz

Der Schalter `-Pfnruntime` ist weg, weil es nichts mehr umzuschalten gibt: Der
Client startet ohne jedes Zutun auf der eigenen Laufzeitumgebung, und
`com.cinemamod` wird nirgends mehr aufgelöst.

---

## Warum das fast nichts war

**Die `-Pfnruntime`-Läufe hatten MCEF längst nicht mehr im Klassenpfad.** Die
beiden Artefakte lagen im `else`-Zweig der Abhängigkeiten — wer den Schalter
setzte, bekam ausschließlich das selbstgebaute `jcef.jar`. Die Handprüfung ist
also bereits auf genau der Verdrahtung gelaufen, die jetzt der Normalfall ist.

Damit war B8/B9 keine Umstellung des Verhaltens, sondern das Entfernen einer
Weiche, an der nur noch ein Weg hing. Ein Rauchtest reicht als Nachweis; die
Handprüfung noch einmal zu fahren, hätte dieselbe Konfiguration zweimal
geprüft.

---

## B8 — der Quelltext

```text
gelöscht   src/mcef/java/…   fünf Klassen gegen MCEFs Fassung von org.cef
fest       src/runtime/java  dieselben fünf Namen gegen upstream java-cef
unbedingt  jcef.jar im Übersetzungs- und im Laufklassenpfad
unbedingt  fn.runtime.dir und der PATH auf den Ordner der Laufzeitumgebung
```

Der Rest des Projekts kennt weiterhin keine der beiden Fassungen und hat sich
nicht geändert — die Grenze verläuft dort, wo sie beim Umbau gezogen wurde.

**Der Bau bricht jetzt ab, wenn die Laufzeitumgebung fehlt.** Das trifft jeden
Gradle-Aufruf, `clean` eingeschlossen. Es ist die bessere Meldung als Dutzende
fehlender Symbole aus dem Compiler, aber es ist eine echte Hürde für einen
frischen Klon — siehe unten.

## B9 — die Abhängigkeiten

```text
raus   maven mcef-download.cinemamod.com   das Maven der CinemaMod Group
raus   com.cinemamod:mcef                  compileOnly
raus   com.cinemamod:mcef-neoforge         runtimeOnly
raus   mcef_version                        gradle.properties
```

---

## Die Nachweise

### Bau

```text
./gradlew build     übersetzt, Prüfläufe grün, nichts von com.cinemamod aufgelöst
```

### Rauchtest, ohne jeden Schalter

`./gradlew runClient -Pide -Ptrace`

```text
Mod-Liste                      MCEF kommt null Mal vor
ProcessGuard                   aktiv — Job Object mit KILL_ON_JOB_CLOSE
Chromium ist da                JCEF Version = 146.0.10.1
erstes onPaint                 1920x1080
Ausnahmen                      0
Ausgang über das Fenster       5 Hilfsprozesse → 0
```

### Automatik, als maschineller Beleg daneben

`./gradlew runClient -Pide -Pidebench` — **26 Stufen, alle durchgelaufen**, von
„Ruhe — nichts tun" bis „Schriftgröße". Zwei Zahlen daraus, auf dem Stand nach
B9 gemessen:

| Stufe | Eingabe bis Bild p50 | Takt | Upload p50 |
|---|---|---|---|
| Normales Tippen | 32,4 ms | 12,5 Bilder/s (nur was sich ändert) | 12,8 ms |
| Schnell rollen, 10.000 Zeilen | **2,9 ms** | 46,2 Bilder/s | 10,5 ms |

Das Tippen sieht dabei langsamer aus, als es ist: Wer eine Taste drückt,
wartet auf Chromiums nächstes Bild, und bei ruhendem Editor kommen die
Bilder nur, wenn sich etwas ändert. Beim Rollen läuft der Strom, und dann
liegt die Antwort bei knapp drei Millisekunden.

---

## Was das kostet

**Ein frischer Klon baut nicht mehr ohne Vorarbeit.** Vorher zog Gradle MCEF
aus dem Netz; jetzt braucht es

```text
pwsh -File tools/runtime/build-jcef.ps1     ein bis zwei Stunden, einmalig
```

Das ist die gewollte Folge davon, Chromium selbst zu bauen — und es ist genau
die Lücke, die der Block **Runtime Distribution** schließen soll. Der ist
heute nicht angefasst; `tools/runtime/package-runtime.ps1` liegt bereit und
wartet auf ihn.

---

## Was offen bleibt

```text
Runtime Distribution   der nächste Block: gebaute Laufzeitumgebung ausliefern,
                       statt sie von jedem bauen zu lassen
echter Launcher        ProcessGuard dort ungeprüft; der Protokolltext für den
                       Fall „schon in einem Job" steht bereit
p95 in Stufe B         vier Millisekunden hinter MCEF, bei anderer Monitorlage
                       als in B4 gemessen — nachmessen, wenn der Rechner in
                       einem festen Zustand ist
logs/ im Repo          logs/debug.log und logs/latest.log sind eingecheckt und
                       ändern sich bei jedem Lauf. Sieht nach Versehen aus
```

---

## Nachgetragen am selben Tag

Zwei Punkte aus der Liste oben sind noch erledigt worden:

**Das Paket heißt jetzt `web.runtime`.** Es trug den Namen der Mod, die es
gerade losgeworden war. Vierzehn Klassen sind umgezogen, fünfundzwanzig
Dateien nennen den neuen Namen; der Bau und die Prüfläufe blieben grün.

**Die Protokolle des Clients sind aus der Versionsverwaltung.**
`logs/debug.log` und `logs/latest.log` waren verfolgt und änderten sich bei
jedem Lauf. Die `.gitignore` kannte `logs/` längst — die beiden Dateien waren
nur älter als der Eintrag.

Was in den Kommentaren noch von MCEF spricht, ist bewusst stehengeblieben:
Vieles davon erklärt, warum etwas so ist, wie es ist, und bleibt damit richtig.
Einige Stellen reden allerdings im Präsens über einen Betrieb, den es nicht
mehr gibt — das ist eine eigene Durchsicht wert und keine Nebensache eines
Umbenennens.

---

Die Punkte der IDE stehen getrennt in [`ide-offene-punkte.md`](ide-offene-punkte.md)
und zählen für die Laufzeitumgebung nicht — die Grenze steht in
[`grenze-runtime-ide.md`](grenze-runtime-ide.md).
