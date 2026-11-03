# Ideen aus anderen Mods, 03.11.2026

Gesichtet: AE2 mit ExtendedAE, AE2 Things und Applied Flux, Refined Storage 2,
Integrated Dynamics und Tunnels, XNet, CC: Tweaked mit Advanced Peripherals,
Super Factory Manager, Create 6, Mekanism, PneumaticCraft, Modular Routers,
Laser IO, Pipez und Compact Machines. Quellen stehen unten. Was nur aus der
Erinnerung stammt und nicht belegt ist, steht als *(unbelegt)* da.

Aufgenommen ist nur, was Factory Network noch nicht hat. Schon da und deshalb
nicht aufgeführt: Verteilstrategien (`round_robin`, `least_filled` …),
Bauplan/Klonen, Hallen, Brücken über Dimensionen, Analysator, Rechte für
Programme (`protection.programs`).

## Vom User freigegeben („kann alles rein“, 03.11.)

| Nr. | Idee | Vorbild | Größe |
|---|---|---|---|
| A | **Live-Werte im Editor**, Haltepunkte in Abläufen | PneumaticCraft-Debugger *(unbelegt)*, CC | M–L |
| B | **Hallen vergrößern** per Upgrade, Inhalt bleibt | Compact Machines (Raumvorlagen) | S |
| C | **Rezepte aus JEI** als Fertigungsmuster übernehmen | AE2, RS2 | M |
| D | **Durchsatz-Diagramme** je Worker und Kabel über Zeit | Integrated Dynamics (Diagnose) | M |
| E | **Hallen-Ereignisse** `hall_entered`, `hall_empty` | eigene Idee | S |

## Neu aus der Recherche, nach Nutzen

1. **Bauteile mit Daten auswählen (NBT/Komponenten).** Verzauberung,
   Haltbarkeit, Trank, Name. Heute trifft `item:diamond_sword` jedes
   Diamantschwert gleich. Siehe eigener Abschnitt unten. *ExtendedAE
   (Tag- und Mod-Busse), RS2 (Grid-Suche); NBT-Filter in Pipez und AE2
   (unbelegt).* **M**
2. **Fluss in der Welt sichtbar machen.** Zuschaltbare Linien entlang der
   Wege, gefärbt nach Ressource; jedes Gerät zeigt aktiv, untätig oder Fehler.
   Ein Programm ist unsichtbar, hier sieht der Spieler, was es tut.
   *Integrated Tunnels (Zustand der Parts), Laser IO (Strahlen, unbelegt).* **M**
3. **Bebilderte Anleitung im Spiel.** Animierte Szenen „Code links, Netz
   rechts“, aus dem Editor per Stichwort aufrufbar. Programmieren ist die
   größte Einstiegshürde. *Create Ponder, AE2-Guide mit 3D-Szenen.* **M–L**
4. **Fertigungsbaum vorab und Fortschritt je Schritt.** Vor dem Start der
   ganze Baum; während des Laufs, welcher Fabricator woran hängt und warum.
   Passt zu einem Ereignis `craft_stuck`. *RS2 Autocrafting Monitor.* **M**
5. **Welt-Verben für Worker:** abbauen, setzen, benutzen, angreifen. Macht
   aus „Dinge bewegen“ „die Welt bedienen“. `click()` ist der erste Schritt.
   *Integrated Tunnels, Modular Routers.* **M**
6. **Eine Suchsprache für Terminal und Programm.** Dieselben Ausdrücke im
   Terminal-Suchfeld wie im `filter`, z. B. `tag:c/ingots except item:*_nugget`.
   Einmal lernen, überall nutzen. *RS2 (`#` Tags, `$` Tooltip).* **S**
7. **Profiler mit Wärmebild.** Tickkosten je Worker und Gerät, farbig in der
   Welt und am Rand im Editor. Wer selbst programmiert, baut sich selbst Lag
   und muss ihn finden. *Integrated Dynamics.* **S–M**
8. **Einstellungen kopieren.** Eine Karte, die Name, Filter und Redstone eines
   Connectors auf den nächsten überträgt. *RS2 Configuration Card.* **S**
9. **Bestell-Knopf für Spieler.** Ein Block oder Display-Knopf, an dem ein
   Spieler „64 Eisen“ anfordert und geliefert bekommt. *Create Redstone
   Requester / Stock Ticker.* **S**
10. **Brille mit Werten.** Ausgewählte Programmwerte schweben in der Welt über
    den Maschinen. *Advanced Peripherals AR (unbelegt).* **M**

Bewusst nicht aufgenommen: Rechte je Spieler per Karte (`protection.programs`
deckt das Wichtigste ab, Rest machen Schutzmods), HTTP aus Programmen
(Sicherheitsfrage auf Servern, nur auf ausdrücklichen Wunsch).

## Arbeiten mit NBT, Vorschlag

Seit 1.20.5 heißt NBT an Gegenständen **Datenkomponenten**. Lager und
Netz unterscheiden sie schon (`ItemKey` trägt die Komponenten), aber eine
Auswahl kann sie nicht ansprechen: `ItemSelection` löst nur zu Gegenstandsarten
auf.

Vorschlag, gelesen wie der Rest der Sprache:

```
filter item:diamond_sword enchanted                 nur verzauberte
filter item:* enchanted:sharpness                   mit Schärfe, jede Stufe
filter item:enchanted_book enchanted:mending
filter tag:c/tools damaged                          beschädigt
filter tag:c/tools damaged > 50%                    mehr als halb kaputt
filter item:potion potion:healing
filter item:* named                                 umbenannt
filter item:* named "Erzkiste"
filter item:* component:mekanism/upgrade            beliebige Komponente, für Mod-Daten
```

Dazu der schnelle Weg ohne Tippen: **Strg-Klick auf einen Gegenstand im
Terminal** kopiert die passende Auswahl mitsamt Komponenten in den Editor, die
man dann kürzt. So wie JEI-Kennungen schon herüberkopiert werden können.

## Quellen

- Applied Energistics 2, Guide: https://guide.appliedenergistics.org/1.21.1/index
- ExtendedAE: https://www.curseforge.com/minecraft/mc-mods/ex-pattern-provider
- AE2 Things: https://www.curseforge.com/minecraft/mc-mods/ae2-things-forge
- Applied Flux: https://www.curseforge.com/minecraft/mc-mods/applied-flux
- Refined Storage 2: https://refinedmods.com/refined-storage/news/20250308-whats-new-in-refined-storage-2.html
- Integrated Dynamics: https://www.curseforge.com/minecraft/mc-mods/integrated-dynamics
- Integrated Tunnels: https://www.curseforge.com/minecraft/mc-mods/integrated-tunnels
- XNet: https://www.curseforge.com/minecraft/mc-mods/xnet
- CC: Tweaked: https://tweaked.cc/
- Advanced Peripherals: https://docs.advanced-peripherals.de/0.7/
- Super Factory Manager: https://www.curseforge.com/minecraft/mc-mods/super-factory-manager
- Create 6.0.0: https://modrinth.com/mod/create/version/1.21.1-6.0.0
- Mekanism Logistical Sorter: https://wiki.aidancbrady.com/wiki/Logistical_Sorter
- Mekanism QIO: https://wiki.aidancbrady.com/wiki/QIO
- PneumaticCraft: https://www.curseforge.com/minecraft/mc-mods/pneumaticcraft-repressurized
- Modular Routers: https://www.curseforge.com/minecraft/mc-mods/modular-routers
- Laser IO: https://www.curseforge.com/minecraft/mc-mods/laserio
- Pipez: https://www.curseforge.com/minecraft/mc-mods/pipez
- Compact Machines: https://www.curseforge.com/minecraft/mc-mods/compact-machines
