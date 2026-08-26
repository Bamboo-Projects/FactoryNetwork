# Applied Energistics als Quelle und Ziel

Gewünscht am 26.08. (Punkt 7.7). **Der Punkt, an dem das Vorgängerprojekt
angefangen hat** — und die Frage, die die Liste stellt: Lagert unser Netz *im*
ME-Netz, oder steht es *daneben*?

Dieses Dokument beantwortet sie. Alles unter „Was AE2 hergibt" ist am 26.08.
aus `ae2-19.2.17.jar` abgelesen und nicht erinnert.

---

## 1. Was AE2 hergibt

AE2 meldet fünf eigene Block-Capabilities an (`appeng.api.AECapabilities`),
und zwei davon sind für uns die Tür:

| Capability | Typ | An welchen Blöcken |
|---|---|---|
| `ME_STORAGE` | `BlockCapability<MEStorage, Direction>` | ME Interface, ME Chest, Condenser |
| `GENERIC_INTERNAL_INV` | `BlockCapability<GenericInternalInventory, Direction>` | ME Interface, Pattern Provider, Cable Bus |

`MEStorage` ist der ganze Netzinhalt:

```java
long insert(AEKey what, long amount, Actionable mode, IActionSource source);
long extract(AEKey what, long amount, Actionable mode, IActionSource source);
void getAvailableStacks(KeyCounter into);
```

**Und AE2 trägt daneben ganz gewöhnliche Handler.** In
`appeng.init.InitCapabilityProviders` stehen siebzehn Anmeldungen für
`Capabilities.ItemHandler` und dreizehn für `Capabilities.FluidHandler` — an
Inscriber, Charger, Sky Stone Tank, Interface und anderen.

---

## 2. Was heute schon geht

**Ein Connector an einem AE2-Block funktioniert, ohne dass eine Zeile
geschrieben wird.** Er sieht, was der Block als `IItemHandler` hergibt:

- am **Pattern Provider** dessen Fächer,
- am **ME Interface** dessen neun Konfigurationsplätze,
- an **Inscriber** und **Charger** deren Ein- und Ausgabe.

Das ist nicht nichts: Wer AE2 Muster anliefern will, kann das heute.

**Was heute nicht geht, ist das Netz selbst.** Die neun Plätze eines Interface
sind nicht sein Bestand — `move all from me_netz to storage` holt neun
Stapel und nicht das Lager. Genau dafür ist `ME_STORAGE` da, und genau die
Capability fragt heute niemand.

---

## 3. Die Frage der Liste: daneben, nicht darin

**Unser Netz lagert nicht im ME-Netz.** Es steht daneben, und das ME-Netz ist
für uns ein **Gerät** wie eine Kiste — eines, das sehr viel hält.

```
worker nachschub {
    from me_netz
    to ofen_1
    filter item:iron_ore
    rate 8 per second
}
```

Vier Gründe, in der Reihenfolge ihres Gewichts:

**1. Zwei Netze, die sich einen Speicher teilen, beantworten „wo ist mein
Zeug" nicht mehr.** Das ist dieselbe Überlegung, die beim Speicherbus schon
einmal gefallen ist: durchgereicht statt gespiegelt. Ein Bestand, der an zwei
Stellen gezählt wird, ist an einer davon falsch.

**2. AE2s Lager ist AE2s Spielfortschritt.** Zellen, Laufwerke, Kanäle — wer
das übernimmt, macht unsere Laufwerke zu Beiwerk. Unsere Zellen haben eigene
Stufen und eigene Rezepte; sie neben ein ME-Netz zu stellen, das alles hält,
hieße, den eigenen Weg abzuschaffen.

**3. `MEStorage` verlangt eine Handlungsquelle.** Jeder Zugriff nimmt ein
`IActionSource` — AE2s Weg, Sicherheitsterminal und Zugriffsrechte
durchzusetzen. Ein Netzspeicher, den unser Netz als seinen eigenen führt,
müsste diese Frage bei jedem Zugriff beantworten und hätte keine Antwort.
Als **Gerät** ist die Frage dagegen genau richtig gestellt: Ein Gerät gehört
jemandem, und wer darauf zugreifen darf, entscheidet dessen Netz.

**4. Die Abhängigkeit bleibt einseitig.** Ein Pack ohne AE2 verliert ein
Gerät. Ein Pack, in dem unser Speicher *ein* ME-Netz *ist*, verlöre seinen
Speicher.

**Verworfen:** unser Netz als AE2-Speicherzelle anzumelden (`StorageCells`).
Technisch ginge es — AE2s Zellregistrierung ist offen. Es wäre aber die
Umkehrung derselben Frage und hätte dieselben vier Antworten gegen sich.

---

## 4. Die Form: eine Brücke, kein neuer Wert

Die Brücke ist **kein** neuer `ResourceKind`. Gegenstände bleiben Gegenstände,
Flüssigkeiten bleiben Flüssigkeiten — auch wenn sie in einem ME-Netz liegen.
Was neu ist, ist der **Weg dorthin**: ein `MachineAccess`, der statt eines
`IItemHandler` die `MEStorage`-Capability befragt.

```
ResourceKind                    AEKeyType
    item:      ←──────────────→   AEKeyType.items()
    fluid:     ←──────────────→   AEKeyType.fluids()
    chemical:  ←──────────────→   (Applied Mekanistics)
    source:    ←──────────────→   (ArsEnergistique)
```

**Beide Seiten haben eine offene Registry**, und das ist der Glücksfall dieses
Punktes: `AEKeyType` ist bei AE2 ein Registry-Eintrag
(`AEKeyType.REGISTRY_KEY`), so wie `ResourceKind` bei uns seit dem 26.08.
Die Brücke ist damit eine **Tabelle zwischen zwei Registries** und keine
Fallunterscheidung — und wenn jemand Applied Mekanistics dazulegt, wächst sie
von selbst.

---

## 5. Der Haken: die eingebauten drei gehen noch nicht über `machine()`

`MachineAccess` wird heute **nur für Arten befragt, die keine der eingebauten
drei sind** (`WorldHost.moveForeign`, `WorldHost.count`). Der Weg für
Gegenstände und Flüssigkeiten steht weiter direkt in `WorldHost` und greift
fest auf `IItemHandler` und `IFluidHandler`.

Damit gilt: **Ein ME-Netz als Quelle für `item:` verlangt zuerst Schnitt 3 aus
`maschinenzugriff.md`** — „die eingebauten drei ziehen nach". Der steht dort
seit dem 26.08. mit der Auflage „erst wenn jemand gespielt hat", und dieser
Punkt ist der Anlass, ihn zu holen.

Ohne ihn ginge nur ein Umweg, und er wäre falsch: eine Sonderabfrage für AE2
mitten im Gegenstandsweg. Das ist genau die Kopie, gegen die diese Sitzung
dreimal angetreten ist.

---

## 6. Was der Spieler entscheiden muss

**Die Handlungsquelle.** `IActionSource.empty()` greift ohne Spieler und ohne
Maschine — AE2s Sicherheitsterminal sieht dann niemanden. `ofMachine(...)`
verlangt einen `IActionHost`, also einen Block, der zu AE2s Netz gehört; unser
Connector ist keiner.

Drei Wege, und die Wahl ist eine Produktentscheidung:

1. **`empty()`** — unser Netz greift zu wie ein Trichter. Einfach, und es
   umgeht AE2s Rechteverwaltung.
2. **Ein eigener Block, der AE2s Netz beitritt** (ein `IInWorldGridNodeHost`).
   Dann ist unser Zugriff eine Maschine im ME-Netz, das Sicherheitsterminal
   greift, und der Block kostet einen AE2-Kanal. Ehrlicher, aber ein Block
   mehr.
3. **Der Spieler hinterlegt sich am Controller.** Dann greift unser Netz mit
   seinen Rechten. Passt zu Servern, verlangt aber ein neues Konzept
   („wem gehört ein Netz"), das es bei uns nicht gibt.

**Empfehlung: 2.** Sie ist die einzige, die auf einem Server niemanden
überrascht, und sie macht die Kanalkosten sichtbar — ein Zugriff auf ein
fremdes Netz ist kein Nebenbei.

---

## 7. Die Schnitte

1. **Die eingebauten drei ziehen auf `machine()` um.** Schnitt 3 aus
   `maschinenzugriff.md`. Danach ist `machine()` der einzige Weg zu einer
   Maschine, und AE2 ist ein Eintrag statt einer Ausnahme.
2. **Die Brücke für Gegenstände.** `MEStorage` lesen und schreiben,
   `AEItemKey` ↔ `Item`. Ein Connector an einem ME Interface sieht dann den
   Netzbestand.
3. **Flüssigkeiten dazu.** Dieselbe Brücke, `AEFluidKey`.
4. **Der Zugriffsblock** (Entscheidung oben, Weg 2). Bis dahin `empty()` mit
   einem Vermerk im Handbuch.
5. **Die Tabelle zwischen den Registries.** Erst wenn eine dritte Art
   dazukommt — vorher ist sie zwei Zeilen und keine Tabelle.

---

## 8. Was ungeprüft bleibt

Alles. Dieses Dokument ist aus dem Jar gelesen und nicht gespielt: AE2 liegt
seit dem 26.08. im Prüfstand, aber kein Prüflauf und keine Runde hat je ein
ME-Netz an einen Connector gehängt.

**Der erste Handgriff, der etwas beweist:** einen Connector an ein ME
Interface setzen und `move all from me_netz to storage` laufen lassen. Kommen
neun Stapel, stimmt Abschnitt 2. Kommt nichts, stimmt er nicht, und alles
darüber steht neu zur Debatte.
