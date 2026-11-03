# Kompatibilität mit weiteren Mods, 03.11.2026

Geprüft auf 26.1.2 mit `./gradlew runServer -Pacceptance -Pextramods` und
`/fnprobe`, dazu echte Worker auf dem Dev-Server. Die Mods sind nur mit
`-Pextramods` dabei und gehören nicht zum Build.

| Mod | Version | Ergebnis |
|---|---|---|
| Functional Storage (mit Titanium) | 26.1-1.6.4 | Schubladen und Storage Controller bieten Items auf allen Seiten, Flüssigkeitsschubladen Flüssigkeiten. Ein Worker hat 64 Kohle in eine Schublade gelegt. |
| Storage Drawers | 26.1.0.2 (CurseForge 223852/8915908) | Alle Schubladen bieten Items auf allen Seiten. Ein Worker hat 64 Eisen in eine Schublade gelegt. |
| Powah (mit Cloth Config) | 7.0.4-alpha | Energiezellen, Generatoren und Furnator bieten Strom; Magmator und Thermo-Generator zusätzlich Flüssigkeit. `from source to network filter power` lädt das Netz aus einer Creative-Zelle, `from network to target filter power` füllt eine Basic-Zelle. |
| Ender IO | 9.0.6-alpha | Maschinen bieten Items und Strom, Tank, Vat, Drain, Niard und Soul Binder auch Flüssigkeit. Ein Worker legt Erz ins Eingangsfach der SAG-Mühle und den Kondensator in sein Fach (Fach 6). |

## Was man wissen muss

- **Ender-IO-Maschinen nehmen ohne Kondensator keinen Strom an.** Das ist
  Ender IO, nicht Factory Network: Die Strom-Worker melden dann „Ziel voll“.
  Ein Worker kann den Kondensator selbst einsetzen.
- **Strom fließt nur über das Netz:** `from network` oder `to network`, nie
  von Maschine zu Maschine (siehe Guide, *Kanäle und Strom*).
- **Powah-Reaktor, Energizing Orb und ähnliche Mehrblock-Teile** bieten an
  einzelnen Blöcken nichts oder nur an einer Seite an; angesprochen wird der
  Block, der die Fähigkeit trägt.
- **Ein leerer Dedicated Server pausiert** nach 60 Sekunden, auch mit
  forceloaded Chunks. Für solche Tests `pause-when-empty-seconds=0` in
  `build/server/server.properties` (der Dev-Server steht jetzt so).

Eigenen Code braucht keine der vier Mods: Sie sprechen die Standard-Fähigkeiten
von NeoForge, und genau die liest ein Connector.
