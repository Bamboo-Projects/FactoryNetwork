# Der Netzwerk-Reiter bekommt eine Form — Umsetzungsplan

**Auftrag:** „kannst du die seite noch besser aufbauen als nur so alles plan
los untereinander zu klatschen?" (29.08.)

---

## Was heute dasteht

**Acht Abschnitte, alle gleich, alle volle Breite, alle untereinander:**

Verkehr · Im Netz · Anzeigen · Worker · Flüssigkeiten · Anlagen · Abläufe ·
Globale Werte · Speicher

**Drei Probleme, die daraus folgen:**

1. **Nichts ist wichtiger als etwas anderes.** „Kein Serverschrank" — der
   Grund, warum gar nichts läuft — steht in derselben Schrift wie eine Liste
   von Anlagennamen.
2. **Die Fläche wird nicht genutzt.** 238 × 123 Pixel, und alles steht in
   einer Spalte. Nur die Anschlussliste ist zweispaltig.
3. **Alles ist immer da.** Ein Netz ohne Flüssigkeiten zeigt trotzdem die
   Überschrift; ein Netz ohne Anlagen auch.

## Die neue Form

**Oben ein Kopf, darunter zwei Spalten.**

```
┌────────────────────────────────────────────────┐
│ ● 20,0k FE     1 KB/s ▁▂▅▃▁▂  0 B gesamt       │  Kopf: eine Zeile
├──────────────────────┬─────────────────────────┤
│ WORKER          3/3  │ IM NETZ            12   │
│  ● haul_erz     2 KB │  ofen_1    quarry_out   │
│  ● schmelzen  340 B  │  depot     kiste_3      │
│  ○ nachts       0 B  │  …                      │
│                      │                         │
│ ABLÄUFE         1/4  │ ANZEIGEN            2   │
│  ● start             │  wand_1    wand_2       │
├──────────────────────┴─────────────────────────┤
│ ⚠ Kein Serverschrank — das Netz rechnet nicht  │  nur wenn nötig
└────────────────────────────────────────────────┘
```

**Der Kopf trägt, was immer gilt:** Strom, aktueller Durchsatz mit einer
kleinen Kurve, Gesamtmenge. Eine Zeile, immer an derselben Stelle.

**Links, was arbeitet.** Worker und Abläufe — die Frage „läuft es".

**Rechts, was da ist.** Anschlüsse, Anzeigen — die Frage „was hängt dran".

**Unten, was klemmt.** Warnungen in Rot, und nur wenn es welche gibt: kein
Serverschrank, doppelte Namen, unbenannte Geräte.

**Was verschwindet, wenn es leer ist:** Flüssigkeiten, Anlagen, globale Werte.
Ein Abschnitt mit „none" darunter kostet zwei Zeilen und sagt nichts.

## Die Entscheidungen dahinter

**Das große Diagramm fällt weg.** Es kostete ein Drittel der Fläche und zeigte
bei einem ruhigen Netz eine leere Box. Was bleibt, ist eine Kurve von
vierzig Pixeln im Kopf — genug, um eine Spitze zu sehen. **Die
Verbraucherliste wandert zu den Workern**, wo sie ohnehin hingehört: Der
Verbrauch je Worker steht neben dem Worker.

**Zwei Spalten statt einer.** 238 Pixel tragen zwei Spalten zu je 115 — genug
für einen Namen und eine Zahl.

**Zahlen an den Überschriften.** „WORKER 3/3" sagt in vier Zeichen, was drei
Zeilen Liste sagen würden.

## Die Aufgaben

- [ ] **1. Der Kopf.** Eine Zeile: Strom, Durchsatz mit Minikurve,
      Gesamtmenge. Ersetzt das große Diagramm.
- [ ] **2. Zwei Spalten.** Ein kleiner Helfer, der eine Spalte zeichnet und
      ihre Höhe zurückgibt — die Spalten wachsen unabhängig.
- [ ] **3. Warnungen nach unten.** Kein Serverschrank, doppelte Namen,
      unbenannte Geräte: rot, gesammelt, und nur wenn es sie gibt.
- [ ] **4. Leeres verschwindet.** Kein Abschnitt ohne Inhalt.
- [ ] **5. Der Verbrauch zu den Workern.** Je Worker seine Menge, statt einer
      eigenen Rangliste.

## Was ich nicht ändere

**Die Reiterleiste und die Statuszeile.** Sie gehören dem Terminal, nicht
diesem Reiter — und sie funktionieren.

**Das Rollen bleibt.** Auch mit zwei Spalten kann ein großes Netz länger sein
als das Fenster.
