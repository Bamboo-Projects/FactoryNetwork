# Die Ressourcenart als offene Registry

Punkt 1.19. Die Frage steht seit dem 24.08. in `umfeld-atm10.md` und ist
seither nicht entschieden. Am 26.08. hat sie einen Anlass bekommen: Ars
Nouveau, Industrial Foregoing und Applied Energistics sollen angebunden
werden (7.5 bis 7.7), und **Source aus Ars Nouveau ist eine Art, die der Kern
nicht kennen kann**.

Dieses Dokument ist ein Entwurf. Es sagt, was es kostet, was es einbringt, und
wie der Weg dahin aussähe.

---

## 1. Wie es heute ist

Eine Ressourcenart ist ein Aufzählungswert:

```java
enum Kind { ITEM, FLUID, CHEMICAL, TAG, FLUIDTAG, POWER }
```

Das ist übersichtlich, der Übersetzer kennt jede gültige Schreibweise, und der
Editor kann sie vorschlagen, ohne irgendwo nachzufragen. Solange die Liste
feststeht, ist es die richtige Bauform.

**Sie steht nicht fest.** Am 26.08. kam `chemical:` dazu, und was das kostet,
ist heute nachgemessen statt geschätzt.

---

## 2. Was eine neue Art heute kostet

Der Beleg ist der Commit „Eine Chemikalie ist jetzt ein Wert" vom 26.08.
Chemikalien konnten sich vorher schon bewegen und zählen lassen — es fehlte
nur der **Wert**. Dafür waren nötig:

| Stelle | Was dazukam |
|---|---|
| `Value` | `ChemicalValue`, `ChemicalSelection` — Zwillinge zu `FluidValue`/`FluidSelection` |
| `Value.describe` | zwei Fälle |
| `Interpreter.entriesOf` | zweimal, für Ausdruck und Wert |
| `Interpreter.withAmount` | zwei Fälle |
| `Interpreter.resolvedSelection` | ein Zweig samt eigener Fehlermeldung |
| `Interpreter.entryMember` | ein Block für `it.chemical` |
| `Interpreter.amountOf` | ein Fall |
| `ValueCodec` | Schreiben und Lesen, plus ein eigener Leser |
| `Signatures.ENTRY_MEMBERS` | ein Eintrag |
| beide Editoren | je eine Liste |

**Zehn Stellen für eine Art, die es schon gab.** Neun davon sind
Zwillinge — Kopien dessen, was für Flüssigkeiten dasteht, mit anderen Typen.
Die versiegelte Schnittstelle hat genau eine davon erzwungen (den Codec); die
übrigen mussten von Hand gefunden werden, über die Fluid-Fälle.

Bei `source:` aus Ars Nouveau wäre es wieder dieselbe Liste. Bei `pressure:`
aus PneumaticCraft ebenfalls. **Und keine dieser Mods kann sie selbst
schreiben** — es ist der Kern, der sie kennen muss.

Dazu kommt, was der Speicherbus vom 26.08. zeigt: Er ist auf `Item` fest
verdrahtet. Für ein ME-Netz als Quelle (7.7) müsste er es nicht sein.

---

## 3. Wie andere es lösen

**AE2: `AEKeyType`.** Ein registrierbarer Typ mit Serialisierung, Anzeigename
und Mengenformatierung. Fremdmods docken an, ohne dass AE2 sie kennt —
`Applied-Mekanistics` bringt so die Chemikalien hinein, `arseng` das Source
aus Ars Nouveau. Beides sind fremde Mods, und AE2 musste für keine davon
angefasst werden.

**Refined Storage: `ResourceType`.** Dasselbe Muster, anderer Name.

Beide großen Netze für 1.21.1 sind unabhängig zu demselben Schluss gekommen.
Das ist kein Beweis, aber es ist das stärkste Argument, das ein Umfeld
liefern kann.

---

## 4. Wie es hier aussähe

### Der Kern

Eine Ressourcenart wird zu einem Eintrag mit einer Kennung und ein paar
Fähigkeiten:

```java
record ResourceKind(
        ResourceLocation id,        // "factorynetwork:item", "arsnouveau:source"
        String prefix,              // "item", "source" — was vor dem Doppelpunkt steht
        Component name,             // für Anzeigen
        UnitFormat units)           // 64 Stück, 1000 mB, 500 FE
```

Dazu je Art ein Zugriff: Wie zähle ich, wie lagere ich ein, wie hole ich
heraus. Das ist dieselbe Schnittstelle, die `NetworkStorage`, `NetworkFluids`
und `ChemicalStore` heute schon dreimal getrennt erfüllen.

### In der Sprache

Nichts ändert sich an dem, was jemand schreibt. `item:iron_ore` bleibt
`item:iron_ore`. Neu ist nur, dass `source:mana` möglich wird, ohne dass der
Kern es kennt.

### Der Preis, ehrlich benannt

**Der Übersetzer kennt die gültigen Präfixe erst zur Laufzeit.** Heute weiß
er, dass `chemiacl:` ein Tippfehler ist. Danach weiß er es nur, wenn die
Registry gefragt werden kann — im Spiel ja, in VS Code nicht.

Das ist beherrschbar, und der Weg dafür steht schon: `.fn-status.json` trägt
bereits die Namen aus der Welt zur Erweiterung. Die Präfixe kämen mit
derselben Datei. Ohne sie fällt VS Code auf die eingebauten vier zurück und
sagt es.

**Die Vervollständigung muss fragen statt zu wissen.** Im Spiel ist das ein
Registry-Zugriff. In VS Code eine Liste aus der Statusdatei.

**Ein Selektor kann nichts mehr treffen, weil die Art fehlt.** `source:mana`
ohne Ars Nouveau. Die Antwort darauf steht schon: Genau so verhält sich
`chemical:` ohne Mekanism seit dem 26.08. — die Meldung sagt, welche Mod
fehlt, statt so zu tun, als sei das Pack schuld.

---

## 5. Der Weg dahin, in Schnitten

Der Umbau muss nicht am Stück geschehen, und er sollte es nicht.

1. ~~**Die Zwillinge zusammenlegen.**~~ **Gebaut** (26.08.). Aus sechs Records
   wurden zwei: `Value.Resource(kind, key)` und
   `Value.Selection(kind, keys, amount)`, dazu `ResourceKind` für das, was je
   Art verschieden ist. Nachgemessen steht darunter in Abschnitt 5a.
2. ~~**Die Speicher hinter eine Schnittstelle.**~~ **Gebaut** (26.08.).
   `ResourceStore` heißt sie, `NetworkStores` hält sie nach Art. Der Satz
   „danach ist ein vierter Speicher ein Eintrag und keine Klasse" war zu
   stark — nachgemessen in Abschnitt 5b.
3. **Die Registry selbst.** Erst jetzt, und dann ist sie klein: Was die
   Einträge können müssen, steht nach Schritt 1 und 2 fest.
4. **Ein Fremdeintrag als Beweis.** Ars Nouveau Source, in `compat/ars` —
   und wenn der Kern dafür angefasst werden muss, ist die Registry nicht
   fertig.

**Schritt 1 und 2 sind auch ohne Entscheidung richtig.** Sie machen den Code
kleiner, egal was danach kommt. Wer die Registry ablehnt, hat trotzdem etwas
davon.

---

## 5a. Was Schritt 1 wirklich gekostet und gebracht hat

Gebaut am 26.08. Die Tabelle aus Abschnitt 2, noch einmal — diesmal mit dem,
was heute dasteht:

| Stelle | vorher | jetzt |
|---|---|---|
| `Value` | zwei Records je Art | keiner; die Art ist ein Feld |
| `Value.describe` | zwei Fälle | keiner |
| `Interpreter.entriesOf` | zweimal | keiner |
| `Interpreter.withAmount` | zwei Fälle | keiner |
| `Interpreter.resolvedSelection` | ein Zweig samt Meldung | die Meldung „Dafür fehlt Mekanism" bleibt |
| `Interpreter.entryMember` | ein Block | keiner |
| `Interpreter.amountOf` | ein Fall | keiner |
| `ValueCodec` | Schreiben, Lesen, eigener Leser | keiner; die Namen trägt die Art |
| `Signatures.ENTRY_MEMBERS` | ein Eintrag | **bleibt** |
| beide Editoren | je eine Liste | **bleibt** |

**Von zehn Stellen bleiben drei, und alle drei sind Sprachfläche.** Neu
dazugekommen ist eine: der Aufzählungswert in `ResourceKind`, wo der
Übersetzer vier Fragen stellt (Kennung schreiben, Kennung lesen, Anzeigename,
Auflösung). Die vier stehen in einer Datei und werden erzwungen — anders als
die neun Zwillinge, die von Hand über die Fluid-Fälle gesucht werden mussten.

Was daneben weiter je Art dasteht, ist die Anbindung an den Speicher: drei
Verzweigungen in `WorldHost` nach `NetworkStorage`, `NetworkFluids` und
`ChemicalStore`. Genau das ist Schritt 2.

### Der Beleg, den niemand bestellt hatte

Die Messung in Abschnitt 2 zählte Arbeit. Beim Bauen kam ein zweites Argument
dazu, und es wiegt schwerer: **Die Zwillinge waren schon auseinandergelaufen.**

`move` entscheidet an der Art über den Weg, und die Frage danach stand
zweimal da. Die für Flüssigkeiten hatte den Nachtrag für die schon aufgelöste
Auswahl bekommen, die für Chemikalien nicht. Eine Chemikalie aus einer
Schleife ging damit in die Gegenstandsauflösung, traf dort nichts — und keine
Auswahl heißt dort *alles*. Die Kiste wurde leergeräumt, ohne Meldung.
Dasselbe in `count` und in `gerät.count(…)`.

Das ist kein Versehen, das jemandem zuzuschreiben wäre; es ist das, was drei
Kopien mit der Zeit tun. Der Fehler ist mit dem Schnitt behoben, weil es die
Frage nur noch einmal gibt.

### Was der Schnitt nicht angefasst hat

Die Sprachfläche: `item:iron_ore`, `it.item`, `signatures.json`, die
Referenzseite, beide Editoren. Und die Haltungsfrage aus Abschnitt 6 — sie ist
weder beantwortet noch vorweggenommen. `ResourceKind` ist ein
Aufzählungswert und darf einer bleiben.

---

## 5b. Was Schritt 2 wirklich gekostet und gebracht hat

Gebaut am 26.08., unmittelbar nach Schritt 1.

`ResourceStore` ist die Schnittstelle: `count`, `room`, `insert`, `extract`,
`contents`, dazu `setDrives`, `hasDrives` und der Änderungsmelder. Sie ist
nicht neu erfunden — `ChemicalStore` war schon genau das, nur für eine Art.
Übrig geblieben ist sie, umbenannt und mit `Object` als Schlüssel, wie im
Wertemodell. `NetworkStores` hält die drei nach `ResourceKind`.

### Der Satz aus Abschnitt 5 war zu stark

Dort stand: „Danach ist ein vierter Speicher ein Eintrag und keine Klasse."
**Die Klasse bleibt.** Was ein Speicher tut, ist eine Index-Mechanik von rund
sechzig Zeilen — Bestand über alle Zellen, Vergleich der Laufwerksstände,
zwei Durchläufe beim Ablegen —, und eine Schnittstelle nimmt sie niemandem ab.
Sie steht heute dreimal fast gleich da.

Was tatsächlich zu einem Eintrag geworden ist, ist **alles um sie herum**:

| Ein vierter Speicher kostete… | vorher | jetzt |
|---|---|---|
| die Klasse selbst | eine | eine |
| Feld und Zugang im Controller | ein Feld, ein Zugang | ein Eintrag in `NetworkStores` |
| `setDrives` beim Neuaufbau | eine Zeile mehr | keine |
| `WorldHost` | Feld, Setter, Aufruf | keine |
| `WorkerRuntime` | Feld, Setter, Aufruf | keine |
| `count` im Netz | ein Zweig | keiner |

Vorher waren das fünf Stellen in vier Dateien, und drei davon waren
Setter, die jemand einzeln aufrufen musste — einer davon wurde beim
Chemikalienpfad tatsächlich einmal vergessen. Jetzt ist es ein Eintrag.

### Was noch je Art dasteht, und warum

**Die Maschinenseite.** `IItemHandler`, `IFluidHandler` und Mekanisms
`IChemicalHandler` haben nichts miteinander zu tun; sie gehören verschiedenen
Mods und heißen an jeder Methode anders. `move`, `countIn` und die Zutat aus
einem `recipe` verzweigen deshalb weiter nach der Art. **Das ist die zweite
Achse**, und eine Registry braucht sie: Ein Eintrag muss sagen können, wie er
lagert *und* wie er an einer Maschine gelesen und geschrieben wird. Schritt 2
liefert nur das erste.

**Die Auflösung einer Auswahl.** `itemsOf`, `fluidsOf` und `chemicalsOf` in
`WorldHost` sehen aus wie Zwillinge, sind aber keine: Sie sagen
Verschiedenes, wenn nichts getroffen wird — ein Gegenstand fehlt im Pack,
fließendes Wasser zählt nicht als Flüssigkeit, und eine Chemikalie fehlt
vielleicht nur, weil Mekanism fehlt. Sie stehen jetzt hinter einem `keysOf`,
das nach der Art aussucht; zusammengelegt werden sie nicht, solange die
Meldungen verschieden sein sollen.

**Die gemeinsame Index-Mechanik**, siehe oben. Sie wäre der nächste Schnitt
und ist bewusst nicht Teil dieses: Die vierzehn Commits vom 26.08. sind
test-grün und ungespielt, und der Speicher ist die Stelle, an der ein Fehler
einen Bestand kostet statt einer Meldung.

### Die dritte Abdrift saß woanders

Gesucht wurde in den Speichern, gefunden wurde sie in den **Auflösern**.
`itemsOf` und `fluidsOf` in `WorldHost` werfen, wenn eine Auswahl nichts
trifft; `chemicalsOf` gab eine leere Liste zurück, solange Mekanism nur
installiert war.

Und leer heißt weiter unten **alles**: `MekTanks.matches` lässt jede Sorte
durch, wenn keine dasteht, und `fillIntoHandler` nimmt dann den ganzen
Netzbestand. Ein Tippfehler in `chemical:…` füllte damit irgendein Gas in die
Maschine, ohne ein Wort zu sagen — dieselbe Klasse Fehler wie in Schnitt 1,
nur eine Schicht tiefer.

Behoben am 26.08.: `chemicalsOf` wirft jetzt wie die anderen beiden, mit einer
eigenen Meldung. Der Prüflauf dazu heißt
`achemicalSelectionThatHitsNothingSaysSo` und prüft die **Meldung** — der
Fehler muss fallen, bevor irgendein Behälter gefragt wird, und ein per
`setBlock` gestellter Mekanism-Tank gibt ohnehin keine Capability heraus.

**Damit ist die Geschichte vollständig:** Schnitt 1 fand die erste Abdrift im
Wertemodell, die Suche in Schnitt 2 fand keine in den Speichern, und die
dritte saß in den Auflösern. Drei Kopien, drei Stände.

### Abgedriftet war in den Speichern nichts

In Schritt 1 hatte der Umbau einen Fehler zutage gefördert. In den drei
Speichern wurde danach gesucht — drei Verdachtsstellen, an denen die Kopien
hätten auseinanderlaufen können: der zweite Durchlauf beim Ablegen, die Meldung an
das Laufwerk (ohne sie ginge ein Bestand in einem fremden Klotz beim Neustart
verloren) und der Vergleich der Laufwerksstände. **Alle drei stimmen in allen
drei Speichern überein.** Der einzige Unterschied war ein fehlendes `room`
beim Gegenstandsspeicher, und das hatte einen Grund: Ein Gegenstand lässt sich
zurücklegen, ein Gas nicht. Es steht jetzt trotzdem da — die Schnittstelle
verlangt es —, und zwar über die Zellen und ohne die Speicherbusse: Eine
fremde Kiste beantwortet die Frage nicht, ohne dass man es versucht. Die
Antwort ist damit zu niedrig und nie zu hoch.

---

## 6. Was zu entscheiden ist

Nicht die Technik — die steht oben. Sondern:

**Ob die Mod fremden Mods erlaubt, ihre Sprache zu erweitern.** Eine offene
Registry heißt, dass `source:` in einem Programm stehen kann, das der Kern
nie gesehen hat. Das ist eine Öffnung, und sie ist nicht umkehrbar: Was einmal
registriert werden darf, kann man später nicht mehr einsammeln, ohne fremde
Programme zu brechen.

**Die Gegenposition ist vertretbar:** Vier feste Arten, und jede weitere
kommt als Kompatibilitätsmodul in diese Mod, wie Mekanism es am 26.08. wurde.
Das ist mehr Arbeit je Mod und behält die Kontrolle darüber, was die Sprache
bedeutet.

Der Unterschied ist keiner der Machbarkeit, sondern einer der Haltung — und
deshalb steht er hier und wird nicht nebenbei entschieden.
