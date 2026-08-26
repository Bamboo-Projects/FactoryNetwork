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
3. ~~**Die Registry selbst.**~~ **Gebaut** (26.08.), in zwei Hälften:
   erst `ResourceKind` als Schnittstelle und `ResourceKinds` als Registry
   (Abschnitt 5c), dann der Übersetzer, der sie fragt statt vier eigene
   Listen zu führen (Abschnitt 5d).
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

## 5c. Schritt 3, erste Hälfte: die Registry steht

Gebaut am 26.08., nachdem die Haltungsfrage mit **Ja** beantwortet war.

`ResourceKind` ist keine Aufzählung mehr, sondern eine Schnittstelle;
`ResourceKinds` hält die angemeldeten Arten und die eingebauten drei. Angemeldet
wird im Mod-Konstruktor, und mit `FMLCommonSetupEvent` ist Schluss —
`freeze()`.

### Was eine fremde Art jetzt kostet

| | |
|---|---|
| eine Klasse, die `ResourceKind` erfüllt | zehn Methoden, zwei davon mit Vorgabe |
| ein Aufruf `ResourceKinds.register(…)` | im Mod-Konstruktor |
| Zeilen im Kern dieser Mod | **keine** |

Gemessen wird das nicht an den eigenen drei — die liefen vorher auch. Es ist
an einer **vierten** gemessen, die nirgends im Kern steht:
`ForeignResourceKindTest` erfindet `testsource`, meldet sie an und prüft
danach Wert, Anzeigetext, Platte, Konstruktorprüfung und Speicher. Der Kern
wurde dafür nicht angefasst.

### Was die Registry hart ablehnt

- **Ein Präfix gehört einer Art.** Zwei Einträge mit demselben Wort sind ein
  Fehler und keine Reihenfolgefrage. Welcher gewönne, hinge daran, welche Mod
  zuerst lädt — keine Erklärung, die ein Spieler lesen kann.
- **`tag`, `fluidtag`, `power` und `all` gehören der Sprache.** Wer sie
  belegte, machte bestehende Programme mehrdeutig.
- **Nach dem Laden ist zu.** Was ein Programm bedeutet, darf nicht davon
  abhängen, wann jemand etwas anmeldet. Geprüft im laufenden Spiel:
  `theresourceKindsAreClosedInArunningGame`.
- **Auch die Namen auf der Platte sind vergeben.** Zwei Arten mit demselben
  NBT-Namen lösen einen wartenden Ablauf beim Neustart in die falsche auf.

### Was noch fehlt, ehrlich benannt

**Die zweite Achse gibt es nicht.** Ein Eintrag sagt, wie seine Art aussieht,
wie sie sich auflöst und wo sie lagert — nicht, wie man sie an einer fremden
Maschine liest und schreibt. Steht in `entscheidungen.md` als die benannte
Grenze dieses Schnitts.

---

## 5d. Schritt 3, zweite Hälfte: der Übersetzer fragt

Gebaut am 26.08. Ein Programm darf jetzt hinschreiben, was eine fremde Mod
angemeldet hat — belegt in `ForeignResourceKindTest`: `move 5
testsource:mana` übersetzt ohne einen Fehler, und `testsource` steht nirgends
im Kern.

### Die Liste stand viermal da, mit drei verschiedenen Antworten

| Stelle | was sie mit einem unbekannten Wort tat |
|---|---|
| `Lexer.SELECTOR_KINDS` | klebte es nicht zusammen |
| `Parser.parseSelector` | machte einen **Tag** daraus |
| `Selectors.parse` | gab `null` |
| `Value.Request.kind()` | gab `UNKNOWN` |

Vier Kopien, und keine zwei waren sich einig. Jetzt gibt es
`ResourceKinds.kindOf(prefix)` und `selectorPrefixes()`, und alle vier fragen
dort. Der `default`-Zweig im Parser, der jedes fremde Wort zu einem Tag
machte, ist damit ersatzlos weg: Was der Lexer nicht zusammenklebt, kommt dort
gar nicht an.

`Expr.Selector` trägt sein Präfix jetzt selbst. Für die eingebauten fünf
Schreibweisen ist das eine Wiederholung der Aufzählung; für eine fremde Art
ist es die einzige Wahrheit, und `Kind.CUSTOM` sagt nur noch, dass es keine
eingebaute ist.

### Der Fehler, der nicht sagte, was los ist

Beim Messen fiel auf, was ein **Tippfehler** heute kostet.
`move 5 chemiacl:hydrogen from lager to tank` erzeugte:

```
Bei move fehlt das Ziel. Zum Beispiel: move 64 item:iron_ore from …
Hier wird ein Wert erwartet, gefunden wurde „:".
Ein Name allein bewirkt nichts. Fehlen Klammern?
„from" ist ein Schlüsselwort. Meinst du den Connector gleichen Namens?
Ein Name allein bewirkt nichts. Fehlen Klammern?
„to" ist ein Schlüsselwort. Meinst du den Connector gleichen Namens?
```

Sechs Meldungen, und keine nennt den Tippfehler. **Es ist dieselbe Falle, die
am 25.08. für eine aus JEI kopierte Kennung behoben wurde** —
`item:mekanism:steel_ingot`, sieben Meldungen; der Vermerk dazu steht bis
heute im Lexer. Nur galt die Reparatur für die eine Form und nicht für die
andere.

Jetzt ist es eine Meldung: *„chemiacl" ist keine Ressourcenart. Meinst du
chemical:?* Und wenn nichts nah genug liegt, steht die Liste dessen da, was es
gibt.

Der Parser erkennt sie daran, dass ein Name unmittelbar — ohne Leerzeichen —
von einem Doppelpunkt gefolgt wird, **an einer Stelle, an der ein Wert
erwartet wird**. Das ist der Unterschied, den der Lexer nicht machen kann:
`fn f(x:Int)` steht in einer Parameterliste, `sort(strategy: x)` hat sein Paar
schon vorher verbraucht. Beides ist als Prüfung festgehalten, denn beides war
vorher gültig und muss es bleiben.

### Und eine Kopie mehr, im Editor

`Completions` bot vier Präfixe an — `item:`, `tag:`, `fluid:`, `fluidtag:` —
und kannte `chemical:` nicht, obwohl es das seit dem 26.08. gibt. Eine fünfte
Kopie derselben Liste, die niemand nachgezogen hatte. Sie kommt jetzt aus der
Registry.

### Und VS Code fragt jetzt auch

Der Weg aus Abschnitt 4, gebaut am 26.08.: `.fn-status.json` trägt neben den
Fehlern und den Gerätenamen jetzt die **Präfixe**, und die Erweiterung bietet
sie dort an, wo eine Auswahl hingehört. Vorher schlug sie dort gar keine vor.

**Ohne Spiel bleiben die eingebauten, und das steht dabei.** Der Vorschlag
trägt dann den Zusatz *„Ressourcenart (ohne Spiel)"* — es ist nicht falsch,
was dort steht, aber es kann unvollständig sein, und ein Editor, der das
verschweigt, lügt über seine eigene Reichweite.

Kein Port, keine neue Erlaubnis: derselbe Kanal wie für die Fehler. Wer die
Programmdateien sieht, sieht auch das.

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

## 6. Was zu entscheiden war — beantwortet am 26.08.

**Ja.** Fremde Mods dürfen die Sprache erweitern; die Ressourcenart wird eine
offene Registry. Die Begründung und das, was daran nicht umkehrbar ist, stehen
in `entscheidungen.md`, „Fremde Mods dürfen die Sprache erweitern".

Was unten steht, ist die Frage, wie sie gestellt war.

---

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
