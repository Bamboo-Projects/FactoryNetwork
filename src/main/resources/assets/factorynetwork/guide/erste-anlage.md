---
navigation:
  title: Die erste Anlage
  position: 15
---

# Die erste Anlage

Einmal der ganze Weg: von der ersten Kiste bis zu einer Linie, die von selbst
Erz einschmilzt. Alles darin steht ausführlicher auf einer eigenen Seite —
hier steht es in der Reihenfolge, in der man es braucht.

Was am Ende dasteht: Eine Kiste, in die du Erz wirfst. Ein Ofen, der es
verhüttet. Und ein Netz, das beides verbindet, ohne dass du noch einmal
hinsiehst.

## Was du brauchst

Sieben Dinge, und **keines davon ist entbehrlich**:

| | wofür |
|---|---|
| Controller | die Wurzel. Genau einer je Netz |
| Terminal | direkt an den Controller, hier schreibst du |
| Serverschrank | ohne ihn nimmt das Netz **kein Programm an** |
| Servergehäuse + Rechenwerk + Speicher + Datenträger | erst alle drei ergeben einen Server |
| Laufwerk + Speicherzelle | ohne Zelle lagert das Netz nichts |
| Brennkammer + Kohle | 40 FE/t, reicht für den Anfang |
| Kabel + drei Connectoren | die Zuständigkeit bis zur Maschine |

Dazu die **Beschriftungspistole** und eine Truhe und einen Ofen als die beiden
Maschinen.

Die häufigste Enttäuschung beim ersten Versuch ist der Serverschrank: Er ist
zwei Blöcke hoch, hat zwölf Einschübe, und ein Einschub mit zwei von drei
Bauteilen trägt **gar nichts** — nicht anteilig. Er steht dann gelb an der
Front, und das Terminal sagt „Kein Serverschrank im Netz", wenn du übernehmen
willst.

## 1. Der Kern

Setz den **Controller** hin und das **Terminal** unmittelbar daneben. Das
Terminal sucht seinen Controller in der Nachbarschaft und nicht über das
Kabel — ein Terminal am Ende eines Kabelstrangs findet nichts.

An eine andere Seite des Controllers kommt der **Serverschrank**, an eine
dritte das **Laufwerk**, an eine vierte die **Brennkammer**. Alle drei dürfen
auch weiter weg am Kabel hängen; direkt am Controller ist nur der Anfang
kürzer.

Leg eine Zelle ins Laufwerk (Rechtsklick öffnet das Regal) und Kohle in die
Brennkammer.

**Wenn jetzt nichts passiert, ist das richtig so.** Das Netz sammelt erst
Strom und braucht dann drei Sekunden zum Hochfahren. Es kehrt außerdem erst
zurück, wenn genug beisammen ist, um danach auch zu laufen — sonst gäbe es ein
Blinken, das wie ein Fehler aussieht.

## 2. Das Kabel und die zwei Maschinen

Zieh ein **Kabel** vom Controller weg. Es trägt keine Gegenstände, sondern
Zuständigkeit: Was daran hängt, gehört zum Netz.

Setz eine **Truhe** und einen **Ofen** dorthin, wo sie stehen sollen, und an
jede einen **Connector**, der am Kabel hängt. Die Vorderseite des Connectors
zeigt dorthin, wo du beim Setzen hingeklickt hast — und **an genau dieser
Seite** muss die Maschine annehmen, was du ihr schickst.

Beim Ofen beißt diese Regel zum ersten Mal, und deshalb braucht er **zwei**
Connectoren. Ein Vanilla-Ofen verhält sich für das Netz genau so wie für einen
Trichter:

| Seite | was dort geht |
|---|---|
| oben | Material hinein |
| unten | Ergebnis heraus |
| seitlich | Brennstoff hinein |

Setz also einen Connector **über** den Ofen, der nach unten auf ihn zeigt, und
einen **unter** ihn, der nach oben zeigt. Beide müssen am Kabel hängen. Mit
nur einem oben holte der zweite Worker gleich wieder das Erz heraus, das der
erste eben eingelegt hat.

Den Brennstoff legst du für den Anfang von Hand nach. Wer ihn auch aus dem
Netz haben will, hängt einen dritten Connector an eine **Seite** des Ofens.

## 3. Namen vergeben

Ein Connector ohne Namen hängt im Netz, kostet seinen Kanal und ist im Code
**nicht ansprechbar**.

Nimm die **Beschriftungspistole** und klick alle Connectoren an. Sie schlägt
einen Namen aus der Maschine dahinter vor — nimm ihn oder tipp einen eigenen.
Sagen wir `lager` für die Truhe, `ofen_ein` für den oberen und `ofen_aus` für
den unteren. Ein Rechtsklick mit leerer Hand tut dasselbe.

Zwei Namen für dieselbe Maschine sind kein Behelf, sondern die Wahrheit: Das
Netz spricht nicht mit Öfen, sondern mit Stellen, an denen etwas hinein- oder
herauskommt.

## 4. Das erste Programm

Öffne das Terminal, geh in den Reiter **Code** und schreib:

```
worker einschmelzen {
    from lager
    to ofen_ein
    filter tag:c/raw_materials
    rate 8 per 20t
}

worker abholen {
    from ofen_aus
    to storage
}
```

**Strg+Eingabe** übernimmt. Solange der Übersetzer ablehnt, läuft der alte
Stand einfach weiter — ein Tippfehler hält die Fabrik nicht an.

Zwei Worker und nicht einer: Der erste füttert den Ofen, der zweite räumt ihn
aus. `rate 8 per 20t` heißt acht Stück je Sekunde — genug, damit der Ofen
nicht leerläuft, und wenig genug, dass nicht der halbe Vorrat im Ofen liegt.
Der zweite hat keinen Filter und nimmt deshalb, was da ist; an `ofen_aus`
liegt ohnehin nur das Ergebnis.

Wirf jetzt Roherz in die Truhe. Nach ein paar Sekunden steht Barren im Reiter
**Speicher**.

## 5. Wenn nichts passiert

In dieser Reihenfolge:

1. **Kein Strom.** Im Reiter *Netz* steht der Vorrat. Ist er leer, liegt keine
   Kohle in der Brennkammer.
2. **Kein Server.** Steht beim Übernehmen „Kein Serverschrank im Netz", fehlt
   eines der drei Bauteile im Einschub.
3. **Kein Kanal.** Jedes Gerät zieht auf seinem ganzen Weg zum Controller
   einen Kanal. Ein einfaches Kabel trägt sechzehn.
4. **Der Connector zeigt auf die falsche Seite.** Das ist bei einem Ofen der
   wahrscheinlichste Fehler. Zeig im Editor auf seinen Namen: Das Terminal
   sagt dir, was die Maschine an dieser Seite annimmt und herausgibt — steht
   dort nichts, zeigt er falsch herum.
5. **Die Auswahl trifft nichts.** `tag:c/raw_materials` gibt es in einem
   großen Pack; in einer leeren Vanilla-Welt heißt es vielleicht anders. Zeig
   im Editor auf die Auswahl — dort steht, wie viele Arten sie trifft.

Dass ein Worker nichts bewegt, ist dagegen **kein Fehler**. Die Quelle kann
leer sein und das Ziel voll; beides ist normal und meldet sich nicht.

## Und dann?

Von hier führen drei Wege weiter, und keiner ist der richtige für alle:

- **Mehr Maschinen an dieselbe Linie** — dafür gibt es Gruppen, damit nicht
  jeder Ofen eine eigene Zeile braucht. Siehe *Werte und Gruppen*.
- **Reagieren statt dauernd nachsehen** — ein `on device_output(ofen_aus)`
  läuft, wenn der Ofen etwas ausgegeben hat, statt jeden Tick zu fragen. Siehe
  *Ereignisse*.
- **Etwas bestellen, statt es vorzuhalten** — der Fabricator baut auf Zuruf,
  was gerade fehlt. Siehe *Fertigung*.
