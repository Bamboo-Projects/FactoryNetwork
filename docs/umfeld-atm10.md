# Das Umfeld — was in ATM10 neben uns läuft

Bestandsaufnahme des Modordners von „All the Mods 10", der Zielumgebung für
FactoryNetwork.

Stand: 2026-08-24

---

## 1. Der Pack

**483 Mods, durchgängig Minecraft 1.21.1 auf NeoForge** — dieselbe Plattform,
für die FactoryNetwork gebaut wird.

**Super Factory Manager 4.34.0 liegt im Pack.** Der Ursprung des Vorgängers
läuft dort neben AE2, Refined Storage und einem Dutzend Kabel-Mods. Das ist
die Umgebung, in der sich FactoryNetwork behaupten muss, und zugleich die
Messlatte: Wo Manifold eine Aufgabe umständlicher löst als ein
SFM-Programm, hat der Sprachentwurf ein Problem.

**Drei Annahmen aus früheren Entwürfen stimmen nicht:**

- **Thermal Series ist nicht im Pack.** `entscheidungen.md` nennt „Mekanism,
  Create, Thermal" als Kompatibilitätsziele — das dritte trifft für ATM10
  nicht zu.
- **Die Rezeptanzeige ist JEI, nicht EMI.** Wer eine Integration plant, plant
  sie gegen JEI 19.44.
- **Botania fehlt**, Mana ist also kein Thema. (`botanypots` und
  `botanytrees` sind eine andere Reihe.)

**GuideME ist bereits im Pack** — die Entscheidung vom selben Tag, die
Spielerdokumentation darauf zu bauen, kostet den Spieler damit keine
zusätzliche Abhängigkeit.

---

## 2. Was mit uns konkurriert

Zwei ausgewachsene Netzwerk-Ökosysteme und eine dichte Schicht darunter:

- **AE2 mit rund zwanzig Erweiterungen.** Bemerkenswert sind die
  Ressourcentyp-Brücken: Applied Mekanistics (Chemicals), ArsEnergistique
  (Source), AppliedFlux (FE).
- **Refined Storage 2.0.9**, für 1.21 neu geschrieben, mit eigener
  `ResourceType`-Abstraktion und Mekanism-Integration in einem eigenen Jar.
- **Kabel und Router:** SFM, LaserIO, XNet, Modular Routers, Pipez, Modern
  Dynamics, Integrated Tunnels, Flux Networks.
- **CC:Tweaked mit AdvancedPeripherals** — das ernsthafteste konkurrierende
  Bedienkonzept: eine vollwertige Lua-Umgebung, in der AE2, RS und Mekanism
  als Peripherals bereitstehen. Wer dort Logistik schreibt, schreibt sie in
  echtem Code. **Für uns die realistische Anforderungsliste:** Was Leute dort
  tatsächlich bauen — Bestand prüfen, Crafting anstoßen, Energiepegel lesen —
  muss Manifold können, ohne umständlich zu werden.

Das bestätigt die Entscheidung vom 2026-08-24 gegen Lua von der anderen
Seite: Die Lua-Nische ist besetzt, und zwar gut. Manifolds Berechtigung liegt
in der deklarativen Hälfte und im Überleben von Serverneustarts, nicht darin,
die bessere Skriptsprache zu sein.

---

## 3. Ressourcenarten — was erreichbar ist

Für `chemical:` und alles, was danach kommt.

### Sicher über NeoForge-Capabilities

| Ressource | Mod | Anmerkung |
|---|---|---|
| **Chemical** | Mekanism 10.7.19 | Seit 10.7 sind Gas, Infusion, Pigment und Slurry **ein** Typ. BlockCapability mit Seitenkontext |
| **Heat** | Mekanism | Wärme als eigener Handler — eine Ressource ohne Lagerbestand |
| **Druckluft** | PneumaticCraft 8.2.23 | Leitgröße ist der **Druck**, nicht die Menge: Zielbereich statt Zielmenge |

**Für Chemicals gibt es zwei fremde Umsetzungen im selben Pack**, beide
quelloffen: `Applied-Mekanistics` bindet sie in AE2 ein,
`refinedstorage-mekanism-integration` in RS. Genau unser Problem, zweimal
gelöst.

### Möglich, Mechanismus ungeprüft

Ars Nouveau (Source — `arseng` bindet es in AE2 ein), Theurgy (Mercury Flux),
Forbidden Arcanus (Aureal). Ob Capability oder mod-eigene API, ist vor einer
Planung nachzusehen.

### Nicht über Capabilities

- **Create: Rotation und Stress** laufen über ein eigenes kinetisches Netz.
  Item- und Fluidzugriff auf Create-Blöcke funktioniert normal; Rotation wäre
  eine Create-spezifische Integration und kein Ressourcentyp.
- **AE2 und RS: der Netzinhalt.** Nur die Schnittstellenblöcke tragen
  Standard-Capabilities, nicht das Netz als Ganzes.
- Natures Aura (chunkbasiert), Iron's Spellbooks (Spielerdaten),
  Redstone-Mods (Blockstates).

---

## 4. Zwei Befunde, die Entwürfe von heute betreffen

### Der Ressourcentyp sollte eine offene Registry sein

AE2 löst das über `AEKeyType`: ein registrierbarer Typ mit Serialisierung,
Anzeigename und Mengenformatierung, an den Fremdmods andocken, **ohne dass
AE2 sie kennt**. Refined Storage macht es mit seiner `ResourceType` genauso.
Beide großen Netze für 1.21.1 sind zu demselben Schluss gekommen.

**Das betrifft `docs/strom.md` unmittelbar.** Dort ist `power` als fünfter
Wert von `Expr.Selector.Kind` geplant — also fest verdrahtet, wie `item`,
`fluid` und `chemical`. Das funktioniert, macht aber jede weitere
Ressourcenart zu einer Änderung an der Sprache.

Eine offene Registry hieße: `item:`, `fluid:`, `chemical:` und `power` wären
Einträge und keine Aufzählungswerte, und Dritte könnten `source:` oder
`pressure:` nachrüsten. Der Preis: Der Übersetzer kennt die gültigen Präfixe
dann erst zur Laufzeit, und die Vervollständigung im Editor muss sie sich
holen statt sie zu wissen.

**Das ist eine Entscheidung für den Projektinhaber**, keine, die nachts
nebenbei fällt. Sie steht deshalb hier und nicht im Strom-Entwurf.

### Draconic Evolution überläuft eine `int`-Energie

Operational Potential ist ein Superset von FE: Draconic-Maschinen sind über
die vorhandene Energie-Anbindung bereits erreichbar. Aber die Beträge
erreichen `long`-Größenordnungen. **Zu prüfen:** ob `NetworkPower`,
`InternalBuffer` und die geplante Stromverteilung das aushalten oder still
überlaufen.

---

## 5. Was sich lohnt anzusehen

- **Mekanisms Seiten-Konfiguration:** Jede Maschinenseite ist einzeln als
  Eingang, Ausgang oder beides schaltbar. Genau die Granularität, die die
  Geräteerkennung abbilden muss.
- **Creates Ponder:** animierte Erklärszenen im Spiel, das wirksamste
  Lehrformat im Modding. Für eine Mod mit eigener Programmiersprache ist die
  Einstiegshürde das größte Risiko — und GuideME kann 3D-Szenen.
- **Jade:** im Pack der Standard für Blockinfo im Fadenkreuz. Ein Provider für
  Controller, Kabel und Laufwerke gibt es bereits (`compat/jade`).
- **XNet, LaserIO, Modular Routers im Vergleich:** dreimal dieselbe Aufgabe,
  drei verschiedene Antworten auf die Frage, wo die Konfiguration wohnt. Wer
  Connectoren entwirft, sollte wissen, wie viele Klicks die Konkurrenz für
  eine typische Aufgabe braucht.
