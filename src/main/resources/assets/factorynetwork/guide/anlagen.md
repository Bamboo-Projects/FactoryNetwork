---
navigation:
  title: Anlagen
  position: 80
---

# Anlagen

Wer drei Erzanlagen baut, will sie nicht dreimal programmieren. Genau dafür
gibt es Vorlagen: **einmal schreiben, beliebig oft bauen.**

## Die Vorlage

```
multiblock Werk {
    devices {
        eingang
        ausgang
    }

    fn schleusen() {
        move 3 item:cobblestone from eingang to ausgang
    }
}
```

`multiblock Werk` ist eine **Vorlage, keine Maschine**. In der Welt steht davon
nichts. Sie sagt nur zweierlei:

- **`devices`** — welche Rollen eine Anlage hat. Das sind Namen, keine
  Connectoren; welches Gerät dahintersteckt, entscheidet sich erst beim Bauen.
- **`fn`** — was die Anlage kann. Das ist ihre Schnittstelle nach außen.

Eine dritte Angabe braucht es nicht, und es gibt auch keine: In eine Vorlage
gehören `devices` und Funktionen, sonst nichts. Kein Worker, keine Anzeige, kein
globaler Wert. Die Trennung zwischen innen und außen fällt damit mit der
zwischen Gerät und Funktion zusammen.

## Gebaut wird mit der Beschriftungspistole

Eine Anlage entsteht allein dadurch, dass du ihre Connectoren benennst — mit
dem **Namen der Anlage, einem Schrägstrich und der Rolle**:

```
werk_1/eingang
werk_1/ausgang
werk_2/eingang
werk_2/ausgang
```

Damit stehen zwei Anlagen in der Welt. Es braucht dafür keinen weiteren Block
und keinen Bereich, den man abstecken müsste — und die Geräte einer Anlage
dürfen quer durchs Gebäude verteilt sein, was in großen Packs der Normalfall
ist.

**Den Schrägstrich musst du nicht tippen.** Das Fenster am Connector hat zwei
Felder: oben die **Anlage**, darunter den **Namen**. Bleibt das obere leer,
gehört das Gerät zu keiner Anlage. Unter beiden steht, was am Ende
herauskommt — bei einer Anlage ist das etwas anderes als das, was du gerade
tippst.

## Oder: ein Gateway hinstellen

Bei zwölf Geräten wird das Wiederholen lästig, und beim Umbenennen gehst du
sie alle noch einmal ab. Dafür gibt es den **Gateway**: ein Kabelstück mit
einem Namensschild.

```
Controller ── Kabel ── [Gateway "werk_1"] ── Kabel ── Connector "eingang"
                                              └────── Connector "ausgang"
```

Rechtsklick auf den Gateway, Namen eintragen — **alles, was dahinter am Kabel
hängt, gehört zu dieser Anlage.** An den Connectoren steht weiterhin nur
`eingang` und `ausgang`; das Netz kennt sie als `werk_1/eingang` und
`werk_1/ausgang`. Im Code ändert sich dadurch nichts.

Umbenennen ist damit **ein Block statt zwölf**.

### Drei Regeln, die du kennen solltest

- **Die Beschriftung gewinnt.** Steht am Connector schon ein Schrägstrich, ist
  die Anlage gesagt, und der Gateway lässt sie in Ruhe. Ein hingestellter
  Block darf nicht still verschieben, was dein Programm über ein Gerät sagt.
- **Ein zweiter Gateway ist die Grenze.** Zwei Anlagen nebeneinander sind zwei
  Gateways, und dazwischen hört jede auf. Der Controller ist auch eine
  Grenze — sonst zöge sich eine Anlage über ihn hinweg in jeden anderen
  Strang.
- **Erreicht ein Gerät zwei Gateways, gehört es zu keinem.** Welcher gewönne,
  hinge daran, wo die Suche anfängt. Geraten wird nicht.

**Kanäle vermehrt er nicht.** Er trägt so viel wie ein dichtes Kabel —
vierundsechzig — und keinen mehr. Dieselbe Regel wie beim Controller-Anbau:
Ein Kanalvermehrer zum Hinstellen machte die Kanalgrenze bedeutungslos.

### Wann welcher Weg

| | |
|---|---|
| **Beschriftung** | Geräte quer durchs Gebäude verteilt, wenige je Anlage |
| **Gateway** | Eine Anlage steht beieinander, viele Geräte, häufiges Umbauen |

Beides geht nebeneinander, auch im selben Netz.

**Der Name der Vorlage steht nirgends in der Beschriftung.** Zu welcher Vorlage
eine Anlage gehört, wird aus ihren Rollen erschlossen. Die Alternative hätte
jeden Namen länger gemacht und wäre bei jedem Umbenennen einer Vorlage
gebrochen.

## Innen der kurze Name, außen der lange

```
fn beide() {
    werk_1.schleusen()
    werk_2.schleusen()
}
```

Außen heißt es `werk_1`, innen `eingang`. **Der Schrägstrich steht nur in der
Beschriftung — im Code wird er nie geschrieben.**

Das ist der eigentliche Zweck der ganzen Übung: `eingang` in `werk_1` und
`eingang` in `werk_2` sind verschiedene Geräte, und die Vorlage muss davon
nichts wissen. Trennte man Vorlage und Anlage nicht, stünde dieselbe Logik
dreimal im Code — und ginge nach dem ersten Umbau dreimal auseinander.

Ein Name in einer Vorlage wird deshalb **zuerst als eigenes Gerät gelesen**:
`eingang` meint `werk_1/eingang`, solange `werk_1` eines dieses Namens hat.
Erst wenn nicht, zählt der Rest des Netzes. Ohne diese Reihenfolge wäre aus
einer Vorlage heraus weder der Netzspeicher noch ein Connector erreichbar, den
sich alle Anlagen teilen:

```
multiblock Werk {
    devices {
        eingang
        ausgang
    }

    fn befüllen() {
        move 64 item:iron_ore from storage to eingang
    }
}
```

## Was das Terminal sagt

Im Reiter **Netz** steht unter *ANLAGEN* jede gebaute Anlage mit ihrem Zustand:

- `werk_1: Werk` — vollständig, sie nimmt Aufrufe an.
- `werk_1: es fehlt ausgang` — gelb. **Solange etwas fehlt, nimmt sie keine
  Aufrufe an.** Das ist besser als ein Aufruf, der halb durchläuft und in der
  Mitte auf ein fehlendes Gerät trifft — dann stünden Gegenstände in einer
  Maschine, und niemand wüsste, warum.
- `werk_1: mehrere Vorlagen passen` — ihre Rollen decken mehr als eine Vorlage
  ab. Zu raten wäre schlimmer als zu fragen; benenne ein Gerät um oder gib
  einer Vorlage eine Rolle mehr.

Eine unvollständige Anlage bliebe sonst unsichtbar: Sie tut nichts und sagt
nichts, und man sucht den Fehler im Programm statt an der Beschriftung.

## Eine Anlage darf warten

Die Funktionen einer Vorlage sind Abläufe wie alle anderen: Sie dürfen `sleep`
und `await`, und sie überstehen den Serverneustart.

Dabei schreibt der Ablauf mit auf, **zu welcher Anlage er gehört**. Nach einem
Neustart bedient er wieder `werk_2` und nicht `werk_1` — ohne das wüsste ein
wartender Ablauf nicht mehr, welche der drei Anlagen er gerade in der Hand
hatte. Was sonst noch für wartende Abläufe gilt, steht unter *Abläufe*.

## Wann sich das lohnt

**Ab der zweiten gleichen Anlage.** Bei einer einzigen ist die Vorlage ein
Umweg: Zwei Connectoren benennen und eine Funktion schreiben tut dasselbe mit
weniger Worten.

**Wenn die Anlagen wirklich gleich sind.** Zwei Anlagen, die sich in einem
Schritt unterscheiden, sind zwei Vorlagen. Eine Vorlage mit einem Sonderfall
darin ist schnell unübersichtlicher als zwei ohne.

**Nicht, um Geräte bloß zu gruppieren.** Eine Vorlage ist geteilter Code. Wer
nur mehrere Öfen auf einmal ansprechen will, braucht keine.

Und ein Satz zum Merken, weil er später Ärger spart: **Die Zuordnung hängt am
Namen.** Wer `werk_1/ausgang` in `ausgang_werk_1` umbenennt, hat der Anlage ein
Gerät weggenommen — sie steht danach im Terminal als unvollständig und nimmt
keine Aufrufe mehr an.
