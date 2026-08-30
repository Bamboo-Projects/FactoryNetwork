# Der Controller wird das schwächste Glied — Entscheidungsvorlage

**Auftrag:** „wir haben server für die Worker und wie wir wissen im echten
leben ist das Netzwerk nur so gut wie das stärkste Glied als das muss der
Controller sein. wie machen wir das da? machen wir ihn upgradebar?" (30.08.)

---

## Der Befund

**Der Controller begrenzt heute nichts.** `Bandwidth.at` gibt für alles, was
kein Kabel ist, `UNLIMITED`. Man kann sechs dichte Kabel an ihn hängen —
6 × 25,6 MB/s = **153 MB/s**, und er reicht alles durch.

**Und der Anbau hat seinen Zweck verloren.** Er bot Kabelseiten; seit die
Kanäle weg sind, sind Seiten nicht mehr knapp. Er kostet Strom und tut sonst
nichts (`ControllerBlockEntity:416` ist seine einzige Wirkung).

**Beides zusammen ergibt die Lösung:** Der Controller bekommt eine Grenze, und
der Anbau hebt sie. Damit hat der Anbau wieder einen Zweck — und zwar den, den
seine Bauform ohnehin nahelegt.

## Die Analogie, die trägt

In einem echten Netz ist der Switch die Engstelle: Jeder Port kann Gigabit,
aber die **Backplane** trägt nur so viel, wie sie trägt. Wer mehr will, kauft
einen größeren Switch — oder steckt ein Modul dazu.

**Der Controller ist die Backplane.** Was durch ihn hindurchgeht, ist begrenzt
— egal wie dick die Kabel sind, die daran hängen.

## Zahlen (Vorschlag)

| | Durchsatz |
|---|---|
| Controller allein | 25,6 MB/s — genau ein dichtes Kabel |
| je Anbau | +12,8 MB/s |
| Sechs Anbauten | 102,4 MB/s |

**Warum genau ein dichtes Kabel:** Ein Netz mit einer Hauptader läuft ohne
Anbau vollständig. Wer verzweigt, merkt die Grenze — und das ist der Moment,
in dem man etwas dazubaut. Eine Grenze, die man vom ersten Tag an spürt, ist
Schikane; eine, die man nie spürt, ist Dekoration.

## Die offene Frage

**Was passiert, wenn die Grenze erreicht ist?**

- **A: Alles wird langsamer.** Was durch den Controller will, teilt sich, was
  da ist. Ein Netz an der Grenze arbeitet zäh, aber vollständig — dieselbe
  Regel wie am Kabel.
- **B: Wer zuerst kommt.** Die Reihenfolge entscheidet; die letzten Worker
  bekommen nichts. Härter, aber sichtbarer: Man sieht sofort, *wer* leidet.

**Ich würde A bauen.** Es ist die Regel, die am Kabel schon gilt — zwei
verschiedene Antworten auf dieselbe Frage („was, wenn es eng wird") wären eine
Regel zu viel. Und **B hat ein Gerechtigkeitsproblem**, das man nicht erklären
kann: Warum steht *dieser* Worker still und nicht der andere?

> **Entschieden am 30.08.: A.** „okay mach A" — alles wird langsamer, und
> der Anbau bleibt der Weg, die Grenze zu heben.

**Damit steht auch die zweite Frage:** Ja, der Controller ist über den Anbau
erweiterbar. Kein Upgrade-Item, kein Steckplatz — der Anbau ist das Upgrade,
und man sieht einem großen Netz an, dass es groß ist.

## Die Aufgaben

- [x] **1. Der Controller kennt seine Grenze.** `Bandwidth.at` gibt für ihn
      nicht mehr `UNLIMITED`, sondern eine Zahl, die aus der Zahl der Anbauten
      wächst.
- [x] **2. Der Weg zum Controller zählt.** `TickBudget` behandelt ihn wie
      jedes andere Wegstück — er *ist* eines, das war die Fiktion.
- [x] **3. Der Anbau hebt sie.** Prüflauf: Ein Netz mit zwei Anbauten trägt
      mehr als eines ohne.
- [x] **4. Man sieht es.** Der Kopf des Netzwerk-Reiters zeigt die Auslastung
      des Controllers — dort, wo schon der Durchsatz steht.
- [x] **5. Der Analysator nennt den Engpass.** „Der Controller ist die
      Engstelle, nicht das Kabel" ist die Auskunft, die man beim Ausbauen
      braucht.

## Was das nicht ist

**Kein Upgrade-Item.** Du hast gefragt, ob wir ihn upgradebar machen — der
Anbau *ist* das Upgrade, nur als Block statt als Karte. Das passt besser: Man
sieht einem großen Netz an, dass es groß ist.

Ein Steckplatz am Controller wäre die dritte Ausbaumechanik neben Anbau und
Ausbaukarten. **Zwei sind schon eine mehr, als man erklären möchte.**

## Was dabei herauskam

**Neue Mechanik brauchte es keine — aber der Controller fehlte im Weg.**
`pathOf` sammelte nur Kabel, Router und Gateway; der Controller war die
Wurzel und stand auf keinem Pfad. Ohne ihn dort hätte `Bandwidth.at` die
schönste Zahl liefern können, und das Budget hätte sie nie gesehen.

Seit er darauf steht, fällt Variante A von selbst heraus: Alle Wege teilen
sich diesen einen Knoten, also wird an der Grenze alles langsamer.

**Ein Prüflauf sicherte das Gegenteil zu.** „Der Controller begrenzt gar
nichts: Er ist Ziel, nicht Strecke." Er ist beides.

**Drei Prüfläufe halten es jetzt fest:** dass der Controller auf jedem Weg
liegt, dass zwei Anbauten die Grenze heben, und dass er seinen eigenen
Knoten im Budget wiederfindet — ein nachgebauter Knoten, der nur fast
derselbe ist, liefert stumm eine Null.
