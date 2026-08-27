# Fernzugriff: Sendemast, Wireless Terminal und Laptop

Gewünscht am 27.08. **Die Idee dahinter in einem Satz:** Unterwegs kommt man
ans Lager, aber nicht an den Code — dafür braucht man einen Laptop.

Dieses Dokument beschreibt, wie das aussieht. Es teilt `konzept.md` §29 auf:
Dort steht heute, das Wireless Terminal habe dieselben fünf Bereiche wie das
Terminal, Code eingeschlossen. Das gilt nicht mehr.

**Der Terminal-Block bleibt unberührt.** Er behält alle fünf Bereiche. Die
Teilung betrifft allein den Zugriff aus der Ferne.

---

## 1. Was dazukommt

| | |
|---|---|
| **Sendemast** | Block am Kabel. Ein Kanal, Strom nach Ausbau. Vier Steckplätze. Von ihm geht das Funksignal aus. |
| **Wireless Terminal** | Gegenstand. Storage, Crafting, Network, Dashboards. Zwei Steckplätze, Akku. |
| **Laptop** | Gegenstand. Dieselben vier Bereiche **plus Code**. Vier Steckplätze, Akku. |
| **Funk-Modul** | Macht eine Anzeigetafel kabellos. |
| **Reichweitenkarte** | Hebt die Reichweite. Gleiche addieren sich. |
| **Infinity-Karte** | Nur im Sendemast. Hebt Entfernung und Dimensionsgrenze für das ganze Netz auf. |

---

## 2. Modul und Karte sind nicht dasselbe

Die Unterscheidung trägt das ganze Ausbausystem, und sie ist scharf:

- **Ein Modul gibt eine Fähigkeit, die vorher nicht da war.** Eine
  Anzeigetafel kann ohne Funk-Modul keinen Funk. Kein Wert, den man drehen
  könnte — entweder sie kann es oder nicht.
- **Eine Karte hebt einen Wert an einer Fähigkeit, die schon da ist.** Der
  Laptop funkt auch ohne Karte, nur nicht weit.

Daran hängt auch, warum die Infinity-Karte eine Karte ist und kein Modul: Sie
schafft nichts Neues, sie hebt eine Grenze auf.

**Steckplätze sind gemeinsam.** Ein Gerät hat eine feste Zahl davon, und
Module wie Karten belegen je einen. Wer alles will, muss entscheiden, was er
weglässt — das ist der Sinn der festen Zahl.

**Gleiche Karten addieren sich.** Vier Reichweitenkarten reichen viermal so
weit wie eine. Kein Stufensystem mit immer besseren Einzelkarten: Das führt
dazu, dass die alte Karte wertlos wird, sobald die neue da ist.

---

## 3. Reichweite

```text
        [Sendemast]  Grund 16, je Karte +16, voll 80
             |
        ~~~ Funk ~~~
             |
        [Laptop]     Grund 0, je Karte +8, voll 32
```

**Beide Seiten addieren sich.** Wie weit ein Gerät kommt, hängt damit an
seiner Zahl freier Plätze:

| Gerät | Plätze | davon für Reichweite | zusammen mit vollem Mast |
|---|---|---|---|
| Laptop | 4 | 4 | 80 + 32 = **112** |
| Wireless Terminal | 2 | 2 | 80 + 16 = **96** |
| Anzeigetafel | 2 | 1 (einer trägt das Modul) | 80 + 8 = **88** |

Das ist kein Nebeneffekt, sondern der zweite Grund für den Laptop: Er reicht
weiter, weil er mehr Plätze hat.

Warum nicht nur eine Seite: Sonst gäbe es genau eine sinnvolle Stelle zum
Ausbauen. So kann ein gut ausgerüsteter Spieler in einer schwachen Basis
arbeiten und umgekehrt — und wer sich ein zweites Gerät baut, muss es nicht
wieder voll bestücken, wenn der Mast schon stark ist.

**Der Strom skaliert mit dem Ausbau.** Ein Mast ohne Karten zieht wenig, ein
voller spürbar mehr. Reichweite ist damit eine Entscheidung und kein Häkchen.

**Die Infinity-Karte steckt im Mast** und gilt für jedes Gerät des Netzes.
Sie ist Infrastruktur, die man einmal baut, und der Mast ist die
Infrastruktur. Steckte sie im Gerät, bräuchte sie auf einem Server jeder
einzeln.

---

## 4. Die Geräte

**Gekoppelt wird per Rechtsklick auf den Sendemast.** Das Gerät merkt sich das
Netz und zeigt dessen Namen im Tooltip. Ohne Kopplung sagt es beim Öffnen,
dass es kein Netz kennt — nicht einfach nichts.

**Beide haben einen Akku, und der ist eine Standard-Capability.** Angeboten
wird `IEnergyStorage` am ItemStack, nicht eine eigene Erfindung. Damit laden
Powah, Flux Networks und jede andere Mod, die Gegenstände im Inventar lädt,
das Gerät von selbst — ohne eine Zeile Kompatibilitätscode. Dasselbe Prinzip
wie bei den Connectoren: die Standard-Capability nehmen, und alles, was sie
spricht, kommt gratis mit. Im eigenen Netz lädt der Sendemast.

**Verbrauch:** wenig je Tick, solange ein Fenster offen ist, mehr je Handlung
— ein Stapel bewegen, ein Programm speichern. Leerer Akku heißt: Das Fenster
geht nicht auf, mit einer Meldung statt eines schwarzen Bildschirms.

---

## 5. Die Anzeigetafel mit Funk-Modul

Ohne Modul bleibt sie, was sie ist: hängt am Kabel, zeigt an, was das Netz ihr
schickt.

Mit Funk-Modul braucht sie kein Kabel. Sie koppelt sich wie die Geräte an
einen Sendemast und holt ihre Daten von dort — eine Tafel am
Bergwerkseingang, dreihundert Blöcke von der Basis, ohne dass jemand Kabel
dorthin legt. Zwei Steckplätze: einer fürs Modul, einer für eine
Reichweitenkarte, falls der Mast allein nicht bis zu ihr reicht.

Ihr Strom kommt dann über den Funk mit. Eine Tafel ohne Kabel und ohne Modul,
aber mit Karte, tut nichts und sagt das auch.

---

## 6. Was ausdrücklich nicht dazugehört

**Der Serverschrank bleibt, wie er ist.** Er hat feste Rollen — CPU, RAM,
Platte —, und die sind kein Ausbausystem mit freien Plätzen. Sein
Steckplatzbehälter wird nicht herausgezogen und nicht geteilt. Die Doppelung
ist der Preis dafür, dass beide bleiben, was sie sind.

**Kein Push über Funk in dieser Fassung.** `konzept.md` §30 sieht
Benachrichtigungen vor, die das Wireless Terminal anzeigt. Das bleibt
liegen, bis der Fernzugriff selbst steht.

---

## 7. In welcher Reihenfolge

Drei Teile, und der erste trägt die anderen:

1. **Das Ausbausystem** — Steckplätze, Modul, Karte, und die Regel, dass ein
   Modul eine Fähigkeit gibt und eine Karte einen Wert hebt. Ohne Funk, ohne
   Gerät: nur der Behälter und die zwei Abfragen darauf.
2. **Der Fernzugriff** — Sendemast, Wireless Terminal, Laptop, Reichweiten-
   und Infinity-Karte.
3. **Die Anzeigetafel** — das Funk-Modul und was es an ihr ändert.

Jeder Teil bekommt seinen eigenen Plan. Zusammen entworfen sind sie, weil die
Regel aus Teil 1 sich nicht sinnvoll festlegen lässt, ohne zu wissen, was
Teil 2 und 3 von ihr brauchen.

---

## 8. Was beim Bauen zu prüfen ist

1. Ein Gerät ohne Netz, ohne Akku und außerhalb der Reichweite muss dreimal
   verschieden reagieren — jedes Mal mit einer Meldung, die den Grund nennt.
2. Die Reichweite ist an zwei Stellen gespeichert, im Mast und im Gerät. Ein
   Prüflauf rechnet beide gegen die Zahlen dieses Dokuments.
3. Jede Karte wirkt in genau einem Punkt, jedes Modul schaltet genau eine
   Fähigkeit frei. Ein Prüflauf hält das fest, sonst wächst hier über die
   Zeit ein zweites Regelwerk neben dem ersten.
4. Der Akku muss sich von einer Fremdmod laden lassen. Ohne Powah oder Flux
   Networks im Testaufbau ist das nur behauptet.
5. Wer den Mast abbaut, während ein Fenster offen ist, bekommt es geschlossen
   und erfährt, warum.
