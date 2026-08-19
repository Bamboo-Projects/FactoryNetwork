# Spike: Trägt ein key-basierter ME-Zugriff?

Stand: 2026-08-18. Status: **abgeschlossen, Frage beantwortet.**

Der Prototyp selbst ist Wegwerfcode und liegt bewusst außerhalb der Codebasis
(Scratchpad, `MEAccessSpike.java`). Übernommen wird nur die Erkenntnis.

## Frage

TeamDmans AE2-Anbindung wurde wegen Performance deaktiviert (siehe
`analyse-sfm-upstream.md`, Befund 1). Trägt ein key-basierter Zugriffspfad ein
ME-Netz mit zehntausenden Ressourcentypen im Tick-Budget — oder muss die Sprache
anders geschnitten werden?

## Der eigentliche Befund: SFM läuft in die falsche Richtung

`InputStatement.gatherSlotsForCap()` iteriert über die **Quelle** und fragt für
jeden gefundenen Stack, ob ein Filter ihn haben will:

```java
for (int slot = 0; slot < type.getSlots(capability); slot++) {
    STACK stack = type.getStackInSlot(capability, slot);
    if (shouldCreateSlot(type, stack)) {
        for (IInputResourceTracker tracker : trackers) {
            if (tracker.matchesStack(stack)) { ... }
```

Für eine Kiste mit 27 Slots ist das genau richtig. Für ein ME-Netz ist es
strukturell falsch: `INPUT 64 item::iron_ingot FROM me_network` meint genau einen
Key, müsste aber zehntausend Einträge durchgehen, um ihn zu finden.

Ein rein key-basierter Zugriff behebt nur die quadratische Emulation, nicht die
Richtung. Die Richtungsumkehr ist der eigentliche Hebel.

## Messung

Drei Zugriffsmuster, Filter auf genau einen Ressourcentyp (der Normalfall).
Zeit pro Ausführung in Millisekunden, ein Minecraft-Tick beträgt 50 ms:

| Typen im Netz | A Slot-Emulation | B Key-Iteration | C Key-Abfrage |
|--------------:|-----------------:|----------------:|--------------:|
| 100 | 0,0088 | 0,0030 | 0,000274 |
| 1.000 | 0,4863 | 0,0077 | 0,000041 |
| 10.000 | **65,41** | 0,0902 | 0,000039 |
| 50.000 | **1504,06** | 0,8105 | 0,000031 |

- **A** = heutiger Stand: ME-Netz als slot-basierter Handler, `getSlots()` und
  `getStackInSlot(i)` iterieren jeweils den ganzen Bestand. O(n²).
- **B** = einmalige Iteration über den Bestand, Filter pro Eintrag. O(n).
- **C** = Filter nennt seine Keys, diese werden direkt nachgeschlagen. O(k).

Manager pro Tick-Budget bei 10.000 Typen:

| Variante | Zeit | passt in 50 ms |
|---|---:|---|
| A Slot-Emulation | 67,78 ms | **0,74** — ein einziger Manager sprengt den Tick |
| B Key-Iteration | 0,096 ms | 521 Manager |
| C Key-Abfrage | 0,000036 ms | über eine Million |

## Antwort

**Ja, es trägt — aber nur mit der Richtungsumkehr.**

Variante A ist als Ursache bestätigt und nicht reparabel. Bei 10.000 Typen
verbraucht eine einzige Auswertung mehr als einen ganzen Tick. TeamDmans
Entscheidung, das abzuschalten, war richtig.

Variante B ist ein Gewinn um Faktor 700 und für sich genommen benutzbar, aber
sie wächst mit dem Netz: bei 50.000 Typen bleiben nur noch rund 60 Manager im
Budget. Ein ME-Netz wächst im Lategame unbegrenzt — B verschiebt das Problem
nur nach hinten.

Variante C ist **konstant**, unabhängig von der Netzgröße. Das ist der
eigentliche Punkt, nicht der Faktor.

## Der Baustein dafür existiert bereits

Variante C setzt voraus, dass ein Filter seine gewünschten Keys aufzählen kann
statt nur "passt/passt nicht" zu antworten. Genau das kann SFM schon:

`ResourceIdentifier.expand()` löst einen Filter in die konkrete Liste der
gemeinten Ressourcen auf, inklusive Regex-Mustern (über
`resourceType.getRegistryKeys()`), und hat dafür bereits einen
`expansionCache`.

Die Richtungsumkehr braucht also kein neues Konzept, sondern nur einen
Ausführungspfad, der `expand()` nutzt, statt über die Quelle zu iterieren.

## Empfehlung für das Design

**Zweigleisiger Zugriffspfad, Entscheidung zur Laufzeit anhand der Mengen.**

`expand()` expandiert gegen die Registry, nicht gegen den Netzbestand. Ein
Muster wie `item::minecraft:.*` ergibt tausende Keys — dann ist B günstiger
als C. Beide Größen sind vorher bekannt, die Heuristik ist also simpel:

- `|expand()|` klein gegenüber dem Netzbestand → **C**, direkte Key-Abfrage
- sonst oder bei offenem Filter (`INPUT FROM me_network` ohne Ressourcenangabe)
  → **B**, einmalige Iteration über `getAvailableStacks()`

Variante A wird ersatzlos gestrichen; das ME-Netz bekommt keinen
slot-emulierenden Adapter.

`expand()` sitzt auf `ResourceIdentifier`, deckt also `WITH TAG`-Klauseln nicht
mit ab. Ein Tag-Filter fällt entweder in den B-Zweig oder filtert die expandierte
Menge nach — das ist im Design festzulegen, nicht im Spike.

Zusätzlich sollte der Linter den teuren Fall sichtbar machen: ein offener
Filter gegen ein ME-Netz durchläuft zwangsläufig den gesamten Bestand. Das ist
nicht falsch, aber der Spieler sollte es im Editor sehen, statt es im TPS-Einbruch
zu merken.

## Nachtrag: die AE2-API im Detail

Der Benchmark modellierte Variante C als `storage.getAmount(key)`. **Eine solche
Methode gibt es auf `MEStorage` nicht.** Die tatsächliche API:

```java
default KeyCounter getAvailableStacks()                  // voller Bestand
default void getAvailableStacks(KeyCounter out)          // füllt eine vorhandene
default long extract(AEKey what, long amount, Actionable mode, IActionSource source)
default long insert(AEKey what, long amount, Actionable mode, IActionSource source)
```

Das Ergebnis des Spikes ändert sich dadurch nicht, der Weg dorthin schon. Drei
Wege stehen offen, alle besser als gedacht:

**1. `IStorageService.getCachedInventory()` → `KeyCounter`.** AE2 pflegt bereits
einen Bestandscache des gesamten Netzes, der laut Doku höchstens einmal pro Tick
aktualisiert wird. Ein `KeyCounter` ist eine Key-auf-Menge-Struktur
(`appeng.api.stacks`) — der Lookup darin ist der O(1)-Zugriff aus Variante C.
Wir müssen also keinen eigenen Index bauen; AE2 hat ihn schon.

Das entschärft zugleich Variante B: Eine Iteration über den offenen Filter läuft
über AE2s vorhandenen Cache, nicht über einen Neuaufbau pro Zugriff.

**2. `extract(key, amount, Actionable.SIMULATE, source)`** beantwortet
"wie viel von diesem Key bekomme ich?" ohne jede Aufzählung. Für die eigentliche
Bewegung ohnehin der richtige Aufruf.

**3. `IStackWatcher` (`appeng.api.networking`, Methode `add(AEKey)`).** Man
abonniert einzelne Keys und wird bei Änderung benachrichtigt — der Mechanismus,
mit dem der Level Emitter arbeitet. Damit könnte `IF me HAS LT 1000 iron` ganz
ohne Polling laufen: Der Manager wird nur dann aktiv, wenn sich der beobachtete
Bestand wirklich ändert.

Noch zu verifizieren, am besten gegen die AE2-Quellen (über SFMs eigenes
`dependency source acquire`): die genaue Signatur von `KeyCounter.get(AEKey)`
und das Callback-Interface zu `IStackWatcher`.

## Grenzen dieser Messung

Isolierter JVM-Benchmark, kein laufendes Minecraft. Gemessen wurde das
algorithmische Verhalten der Zugriffsmuster, nicht AE2s echter `KeyCounter`,
nicht Netzwerk-Locking, nicht `IActionSource`-Overhead und nicht die Kosten von
`insert`/`extract` selbst. Die absoluten Zahlen sind daher als Größenordnung zu
lesen, nicht als Zusage.

Belegt ist damit: die Größenordnungen und die Notwendigkeit der
Richtungsumkehr. Nicht belegt ist, dass eine echte Implementierung diese
absoluten Werte erreicht. Der nächste Beleg wäre ein GameTest gegen ein echtes
ME-Netz — sinnvoll erst, wenn der Zugriffspfad steht.
