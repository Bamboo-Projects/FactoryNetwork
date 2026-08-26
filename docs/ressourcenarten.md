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

1. **Die Zwillinge zusammenlegen.** `Value.Selection`, `FluidSelection` und
   `ChemicalSelection` werden ein Record mit einem Art-Feld; dasselbe für die
   Einzelwerte. Das ist der Schnitt, der die zehn Stellen aus Abschnitt 2 auf
   eine reduziert — **und er bringt für sich allein schon etwas**, auch wenn
   die Registry nie kommt.
2. **Die Speicher hinter eine Schnittstelle.** `NetworkStorage`,
   `NetworkFluids` und `ChemicalStore` erfüllen dieselben vier Methoden
   dreimal. Danach ist ein vierter Speicher ein Eintrag und keine Klasse.
3. **Die Registry selbst.** Erst jetzt, und dann ist sie klein: Was die
   Einträge können müssen, steht nach Schritt 1 und 2 fest.
4. **Ein Fremdeintrag als Beweis.** Ars Nouveau Source, in `compat/ars` —
   und wenn der Kern dafür angefasst werden muss, ist die Registry nicht
   fertig.

**Schritt 1 und 2 sind auch ohne Entscheidung richtig.** Sie machen den Code
kleiner, egal was danach kommt. Wer die Registry ablehnt, hat trotzdem etwas
davon.

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
