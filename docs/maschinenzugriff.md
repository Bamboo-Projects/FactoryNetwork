# Die zweite Achse: eine fremde Art an einer fremden Maschine

Der letzte Rest von Punkt 1.19. Die Registry ist am 26.08. gebaut worden und
beantwortet **eine** von zwei Fragen: Ein Eintrag sagt, wie seine Art aussieht,
wie sie sich auflöst und wo sie im Netz lagert. Was er nicht sagt, ist, wie man
sie an einer Maschine liest und schreibt.

Ohne diese Antwort kann eine fremde Art im Netz liegen und sich in einem
Programm nennen lassen — bewegen lässt sie sich nicht. **Ars Nouveau (7.5)
hängt genau daran.**

---

## 1. Warum es zwei Achsen sind und nicht eine

Der Speicher gehört dieser Mod. Zellen in Laufwerken, ein Index davor — das
ist überall dieselbe Mechanik, und deshalb ließ sie sich hinter
`ResourceStore` bringen.

Die Maschine gehört jemand anderem. Was dort steht, sind drei Schnittstellen
aus drei Mods, die nichts miteinander zu tun haben:

| Art | Schnittstelle | woher |
|---|---|---|
| Gegenstände | `IItemHandler` | NeoForge |
| Flüssigkeiten | `IFluidHandler` | NeoForge |
| Chemikalien | `IChemicalHandler` | Mekanism |

Sie heißen an jeder Methode anders, rechnen in verschiedenen Einheiten und
haben verschiedene Vorstellungen davon, was ein Fach ist. Ein gemeinsamer
Obertyp existiert nicht und lässt sich auch nicht herbeireden.

**Was sie gemeinsam haben, ist nicht der Typ, sondern die Handlung.** Genau
darauf zielt dieser Entwurf.

---

## 2. Was heute je Art dasteht

Gemessen am 26.08. Drei Wege, dreimal dieselbe Tabelle:

| | Speicher → Gerät | Gerät → Speicher | Gerät → Gerät | zählen |
|---|---|---|---|---|
| Gegenstände | `move` (Zweig `fromStorage`) | `drainInto` | `transfer` | `countItems` |
| Flüssigkeiten | `fillFromNetwork` | `drainIntoNetwork` | `transferFluid` | `countFluids` |
| Chemikalien | `ChemicalStores.fillFrom` | `ChemicalStores.drainInto` | drain + fill über den Speicher | `ChemicalStores.amountAt` |

Dazu je Art ein Zugang am Connector: `machineInventory()`, `machineTank()`,
und für Chemikalien `MekTanks.at` innerhalb des Kompatibilitätsmoduls.

Und ein vierter Verbraucher, den man leicht übersieht: **das Geräteprofil**.
`DeviceProfile.Access.Ability` kennt `ITEMS`, `FLUIDS`, `ENERGY` — daraus
entstehen die Warnung „Der Connector zeigt auf eine Seite, die das nicht
kann" und die Auskunft im Editor. Eine fremde Art taucht dort nicht auf, und
`NetworkCheck` sagt über sie deshalb ausdrücklich nichts.

---

## 3. Die Form: drei Handlungen, kein Typ

Vorschlag:

```java
public interface MachineAccess {

    /** Wie viel davon in der Maschine liegt. */
    long count(Level level, BlockPos pos, Direction side, Collection<?> keys);

    /** Aus dem Netzspeicher in die Maschine. Liefert, wie viel ankam. */
    long fill(ResourceStore from, Level level, BlockPos pos, Direction side,
              Collection<?> keys, long limit);

    /** Aus der Maschine in den Netzspeicher. Liefert, wie viel kam. */
    long drain(Level level, BlockPos pos, Direction side,
               Collection<?> keys, ResourceStore into, long limit);
}
```

`ResourceKind.machine()` liefert ihn, mit einer Vorgabe, die nichts kann:
**Eine Art darf im Netz liegen, ohne dass eine Maschine sie kennt** — genauso
wie sie sich bewegen darf, ohne lagerbar zu sein.

### Warum genau diese drei

Es sind die Handlungen, die `ChemicalStores` bereits hat — als einzige der
drei Arten, weil sie als letzte gebaut wurde und dabei die Naht zum
Kompatibilitätsmodul brauchte. Die Form ist also nicht erfunden, sondern
abgelesen.

**Erst fragen, dann ziehen** steckt in `drain` und nicht daneben: Was der
Speicher nicht nimmt, darf gar nicht erst aus dem Behälter kommen. Bei
Gegenständen ließe sich der Rest zurücklegen, bei einem Gas nicht — und eine
Schnittstelle, die den Unterschied dem Aufrufer überlässt, wird beim vierten
Eintrag falsch benutzt.

### Was nicht dazugehört

**Gerät zu Gerät.** Bei Chemikalien läuft es heute über den Netzspeicher als
Zwischenstation; bei Gegenständen und Flüssigkeiten direkt. Das ist ein
Unterschied im Verhalten und nicht nur im Code: Der Umweg braucht Platz im
Netz, der direkte Weg nicht. Ihn zu vereinheitlichen ist eine
**Produktentscheidung** und gehört nicht in diesen Schnitt.

Für eine fremde Art heißt das: Sie geht über den Speicher, wie die
Chemikalien. Das ist ehrlich und dokumentiert.

**Der Schlüsselbund.** `keys` ist eine `Collection<?>`, deren Einträge die
Form haben, die `ResourceKind.type()` nennt. Leer heißt **nicht** „alles" —
dieser Fehler hat am 26.08. schon einmal ein Gas verwechselt. Wer alles meint,
sagt es mit einer vollen Liste.

---

## 4. Was es kostet

| Schritt | Umfang |
|---|---|
| `MachineAccess` und `ResourceKind.machine()` | klein |
| Chemikalien: Umhüllung um `ChemicalStores` | klein, die Methoden gibt es |
| Flüssigkeiten: `fillFromNetwork`/`drainIntoNetwork`/`countFluids` umziehen | mittel |
| Gegenstände: dasselbe, aber mit Fächern und `ItemStack` | mittel |
| `WorldHost.move` und `countIn` auf den generischen Weg | mittel, **heikel** |

**Heikel ist die letzte Zeile**, und deshalb steht sie zuletzt. `move` ist die
Stelle, an der diese Mod Gegenstände in der Hand hält; ein Fehler dort kostet
einen Bestand und keine Meldung. Die Commits vom 26.08. sind test-grün und
ungespielt.

---

## 5. Die Schnitte

1. ~~**Die Schnittstelle.**~~ **Gebaut** (26.08.). `MachineAccess` mit den
   drei Handlungen und `NONE` als Vorgabe; `ResourceKind.machine()` liefert
   sie. **Keine Umhüllungen für die eingebauten drei** — anders als hier
   zuerst geplant: Sie hätten den bestehenden Code verdoppelt, und
   Verdoppelung ist genau das, was diese Nacht dreimal als Fehlerquelle
   nachgewiesen hat. Die eingebauten liefern deshalb ebenfalls `NONE`, und
   ihr Weg bleibt vorerst, wo er ist.
2. ~~**Der generische Weg für fremde Arten.**~~ **Gebaut** (26.08.). `move`
   und `countIn` fragen `machine()` genau dann, wenn die Art keine der
   eingebauten drei ist. An den drei gespielten Wegen ändert sich keine
   Zeile. Eine fremde Art ohne Zugriff bekommt eine Meldung statt einer
   stillen Null: *„source lässt sich an keiner Maschine bewegen."*
3. **Die eingebauten drei ziehen nach.** Erst wenn jemand gespielt hat.
   Danach ist `machine()` nicht mehr nur der Erweiterungspunkt, sondern der
   einzige Weg.
4. **Das Geräteprofil.** `Ability` wird offen, damit `NetworkCheck` auch über
   eine fremde Art etwas sagen kann. Braucht eine Probe je Art und ist
   deshalb eigenes Gebiet.

**Damit ist Ars Nouveau baubar** — eine Mod kann Art, Speicher und
Maschinenzugriff mitbringen, ohne dass der Kern angefasst wird.

### Was daran ungeprüft bleibt, offen benannt

Der Weg einer fremden Art durch `move` lässt sich **im Prüflauf nicht
laufen**: Anmelden geht nur beim Laden, und ein GameTest läuft danach — das
ist dieselbe Zusage, die `theresourceKindsAreClosedInArunningGame` festhält.
Geprüft sind der Vertrag (`MachineAccess.NONE` gibt überall null, die Vorgabe
ist sie) und die Verzweigung durch Lesen. Dieselbe Art von Lücke wie beim
Mekanism-Tank, den `setBlock` nicht mit Seitenkonfiguration hinstellt — und
sie schließt sich, wenn die erste echte fremde Mod da ist.
