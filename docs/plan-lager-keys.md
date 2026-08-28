# Das Lager merkt sich Gegenstände statt Kennungen — Umsetzungsplan

**Ziel:** Ein verzaubertes Buch, ein benanntes Werkzeug, eine angeschlagene
Spitzhacke gehen ins Netzlager und kommen unverändert zurück.

**Auftrag:** „ja das fix das lager! schau schon bei AE2 wie man das gut
macht" (28.08.). Das Ob ist entschieden; dies ist der Arbeitsplan.

**Heute:** Das Lager führt `Map<Item, Long>` — eine Registry-Kennung und eine
Zahl. Alles, was ein Stapel darüber hinaus trägt, fällt beim Einlagern weg.
Seit dem 28.08. lehnen alle vier Wege solche Stapel ab (`StorageKeys`), statt
sie stillschweigend zu entkernen. **Diese Notbremse fällt am Ende dieses
Plans** — sie ist der Beweis, dass der Umbau wirkt.

## Das Muster von AE2

`AEItemKey` (im Klon unter `appeng/api/stacks/AEItemKey.java`):

```java
public final class AEItemKey extends AEKey {
    private final ItemStack stack;      // immer Menge 1, privat, nie herausgegeben
    private final int hashCode;         // ItemStack.hashItemAndComponents(stack)
    private final int maxStackSize;     // einmal vorgerechnet
    private final int damage;
}
```

Und das Format auf der Platte: `id` (die Item-Kennung) plus ein optionales
`components` (ein `DataComponentPatch`).

**Zwei Dinge daran sind kein Zufall und werden übernommen:**

1. **Der Stack ist privat und wird kopiert.** Ein Key, dessen Stack sich
   nachträglich ändert, ändert seinen Hash — während er als Schlüssel in
   einer Map liegt. Der Bestand ist dann da und unauffindbar zugleich.
2. **`maxStackSize` steht am Key, nicht am Item.** Die Komponente
   `MAX_STACK_SIZE` kann ihn ändern, und das Entnehmen im Terminal rechnet
   damit.

## Verifizierter Bestand

| Was | Wo |
|---|---|
| `ItemStack.hashItemAndComponents(ItemStack)`, `isSameItemSameComponents`, `copyWithCount` | per javap bestätigt, 1.21.1 |
| `DataComponentPatch.CODEC` und `.STREAM_CODEC` (letzteres über `RegistryFriendlyByteBuf`) | ebenso |
| Zellen speichern `"Cell"` → Liste aus `"Item"` und `"Count"` | `storage/CellFormat.java` |
| Der Netzindex ist `Map<Item, Long>` | `network/NetworkStorage.java:47` |
| Vier Wege schreiben ins Lager | Terminal-Umschaltklick, Ablegepaket, `WorkerRuntime`, `WorldHost` |
| Die Fertigung legt zurück, was sie aus dem Lager nahm | `ControllerBlockEntity:999,1067` — kein eigener Verlust |

## Entscheidungen, die dieser Plan trifft

Beide sind so gewählt, dass **bestehende Welten und Programme sich nicht
ändern**. Ein Veto kostet eine Zeile.

**Alte Zellen bleiben lesbar, ohne Migrationslauf.** Das Format behält seinen
Namen `"Cell"`; jeder Eintrag bekommt ein *optionales* Feld `components`. Ein
Eintrag ohne dieses Feld ist ein Gegenstand ohne Komponenten — genau das, was
heute drinsteht. Der Versprechen im Kommentar von `CellFormat` bleibt wahr.

**`move 64 eisenbarren` meint weiter alle Varianten.** Ein Name ohne Zusatz
spricht die Kennung an, nicht eine bestimmte Ausführung. Heute liegen ohnehin
nur nackte Gegenstände im Lager, also ändert sich für jedes bestehende
Programm nichts. Wer eine bestimmte Ausführung meint, braucht einen Filter —
das ist eine eigene Frage und nicht Teil dieses Plans.

## Vorgehen: Adapter statt Bruch

**Der Baum bleibt nach jeder Aufgabe grün.** Der User spielt; ein Umbau, der
den Bestand für drei Stunden rot lässt, nimmt ihm den Neustart.

Dafür bekommen `NetworkStorage` und `CellInventory` Übergangs-Überladungen:
`insert(Item, long)` ruft `insert(ItemKey.bare(item), long)`. Alle 329
bestehenden Prüfläufe laufen unverändert weiter, während die Schichten
darunter umziehen. Am Ende fallen die Überladungen.

- [ ] **1. Der Schlüssel.** `storage/ItemKey.java` plus reiner JUnit-Test:
      Gleichheit, Hash, Codec hin und zurück — je einmal mit und ohne
      Komponenten. Dazu die Falle aus Punkt 1 oben als eigene Probe: Ein
      Stapel, der nach `of()` verändert wird, darf den Key nicht ändern.
- [ ] **2. Der Netzindex.** `NetworkStorage` intern auf `ItemKey`, mit
      Überladungen. Prüflauf: unverändert grün.
- [ ] **3. Die Zellen.** `CellFormat`/`CellContents`/`CellInventory` auf
      `ItemKey` — mit dem optionalen `components`-Feld. **Achtung:**
      `DataComponentPatch.CODEC` braucht `RegistryOps`; die Signaturen
      brauchen einen `HolderLookup.Provider`, durchgereicht von der
      BlockEntity (`level.registryAccess()`). Prüflauf: eine alte Zelle
      (ohne das Feld) liest sich weiterhin.
- [ ] **4. Die Leitung.** `StorageSnapshotPacket` und `StorageActionPacket`
      tragen den Key statt der Kennung. Der Wächter aus dem Terminalfenster
      gilt hier genauso: schreiben und lesen zusammen prüfen.
- [ ] **5. Die Anzeige.** Der Speicher-Reiter zeigt je Key eine Zeile, mit
      dem echten Stapel — Verzauberungen und Namen werden sichtbar.
- [ ] **6. Die Sprache.** Selektoren und Bedingungen rechnen weiter je
      Kennung; ein Aggregat über alle Varianten bleibt.
- [ ] **7. Die Notbremse fällt.** `StorageKeys.storable` verschwindet, und
      `aWorkerLeavesItemsWithDataAlone` dreht sich um: Die benannte Hacke
      wandert jetzt samt Namen ins Lager und kommt samt Namen zurück. **Das
      ist der Beweis-Commit des ganzen Umbaus** — nicht früher lösen, sonst
      steht das Verlustloch zwischendurch wieder offen.

## Was danach billig wird

**AE2-Zellen auslesen.** Deren Format ist dasselbe Muster — Item plus
`DataComponentPatch`. Ein `compat/ae2`-Leser kann eine fremde Zelle in unser
Lager schütten, ohne dabei etwas zu zerstören. Vorher wäre genau das
unmöglich gewesen.
