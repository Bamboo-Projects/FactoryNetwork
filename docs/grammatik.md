# Manifold — Grammatik

Formale Fassung von `sprache.md`. Bei Widersprüchen gilt diese Datei, weil sie
genauer ist; inhaltliche Begründungen stehen dort.

Stand: 2026-08-20

Notation: `=` definiert, `|` trennt Alternativen, `[ ]` ist optional, `{ }`
wiederholt sich beliebig oft, `' '` steht für sich selbst. GROSSBUCHSTABEN
sind Token, die der Lexer liefert.

---

## 1. Zeilen statt Semikolons

Manifold beendet Anweisungen mit dem Zeilenende. Der Lexer liefert dafür ein
Token `NL`. Damit Ausdrücke trotzdem umbrechen dürfen, unterdrückt er es:

- nach einem Operator (`+`, `==`, `&&`, `,`, …),
- nach einer öffnenden Klammer, bis sie geschlossen ist,
- vor `else` und vor einem Punkt am Zeilenanfang.

```
let x = a +
        b            // eine Anweisung

let y = foo()
        .where(...)  // eine Anweisung
```

Das ist die einzige Stelle, an der der Lexer den Zeilenumbruch bewertet. Sie
ist hier festgehalten, weil sie sonst im Parser landet und dort niemand mehr
findet.

---

## 2. Programm

```
program     = { NL } { declaration { NL } } EOF

declaration = workerDecl | groupDecl | filterDecl | multiblockDecl
            | eventDecl  | displayDecl | fnDecl | onDecl
            | globalDecl | constDecl
```

Ein Programm besteht nur aus Deklarationen. Anweisungen stehen immer in einer
Funktion oder einem Ereignisblock — es gibt kein Hauptprogramm, das beim Laden
losläuft.

---

## 3. Deklarationen

### Worker

```
workerDecl  = 'worker' NAME '{' NL { workerEntry NL } '}'

workerEntry = 'from'     target
            | 'to'       target
            | 'filter'   selection
            | 'maintain' INT
            | 'rate'     INT 'per' DURATION
            | 'when'     expr
            | 'priority' INT
            | 'strategy' NAME
            | 'overflow' 'to' target

target      = NAME [ '.' 'slots' '(' expr ')' ] | 'storage' | 'crafting'
```

`from` und `to` müssen genau einmal vorkommen, alles andere höchstens einmal.
Das prüft nicht die Grammatik, sondern der Übersetzer — so kann er sagen,
welche Angabe fehlt, statt nur „unerwartetes Zeichen".

### Auswahl von Strom

```
selection   = itemSel | fluidSel | chemicalSel | tagSel | 'power'
```

**`power` steht allein, ohne Doppelpunkt.** Alle anderen Auswahlausdrücke
tragen eine Sorte dahinter — `item:iron_ore`, `fluid:water`. Strom hat keine:
Es gibt nur FE. Ein `power:` mit leerem Rest wäre eine Lüge über die Form.

Damit ist `power` ein Schlüsselwort. Wer seinen Connector so nennt, schreibt
ihn in Rückstrichen — dieselbe Regel wie für `for`.

### Globaler Wert

```
globalDecl  = 'global' NAME '=' literal
constDecl   = 'const'  NAME '=' literal
```

**Die einzige Deklaration ohne Block.** Alle anderen sammeln Angaben zwischen
geschweiften Klammern; diese erklärt einen Wert und ist mit der Zeile fertig.

Der Wert muss ein Literal sein und **in derselben Zeile stehen**. Das ist
keine Schönheitsregel: Nach einem Gleichheitszeichen erzeugt der Lexer kein
Zeilenende, weil ein Ausdruck folgen muss und umbrechen darf — ohne die Regel
verschlänge `global kaputt =` die nächste Deklaration.

### Gruppe

```
groupDecl   = 'group' NAME '{' NL { groupEntry NL } '}'

groupEntry  = 'members'  memberList
            | 'strategy' NAME

memberList  = memberRef { ',' memberRef }
memberRef   = NAME | NAMEPATTERN
```

### Filter-Vorlage

```
filterDecl  = 'filter' NAME '{' NL { filterEntry NL } '}'

filterEntry = [ 'except' ] selection
```

Dasselbe Wort wie die Worker-Angabe; unterschieden wird nach dem Ort. Eine
Zeile ist für sich eine `selection` und darf deshalb ihr eigenes `except`
enthalten — steht `except` dagegen am Zeilenanfang, gehört es zur Vorlage.

### Multiblock

```
multiblockDecl = 'multiblock' NAME '{' NL
                     [ 'devices' '{' NL { NAME NL } '}' NL ]
                     { fnDecl NL }
                 '}'
```

### Ereignis

```
eventDecl   = 'event' NAME '(' [ paramList ] ')'

paramList   = param { ',' param }
param       = NAME ':' typeName
typeName    = NAME [ '<' typeName '>' ]
```

### Display

```
displayDecl = 'display' NAME '{' NL { displayEntry NL } '}'

displayEntry = 'title'     STRING
             | 'row'       STRING expr
             | 'text'      expr
             | 'progress'  STRING expr
             | 'indicator' STRING expr
             | 'list'      STRING expr
             | 'button'    STRING NAME
```

Ein `displayEntry` enthält Ausdrücke, aber keine Anweisungen — kein `if`,
keine Schleife, kein `await`.

### Funktion und Ereignisblock

```
fnDecl      = 'fn' NAME '(' [ paramList ] ')' block
onDecl      = 'on' NAME '(' [ nameList ] ')' block

nameList    = NAME { ',' NAME }
```

Bei `fn` und `event` stehen Typen, bei `on` nicht — dort sind sie durch die
Ereignisdeklaration bekannt.

---

## 4. Anweisungen

```
block       = '{' NL { statement NL } '}'

statement   = letStmt | ifStmt | forStmt | whileStmt
            | returnStmt | 'break' | 'continue'
            | moveStmt | emitStmt | sleepStmt
            | assignStmt | exprStmt

letStmt     = 'let' NAME '=' expr
assignStmt  = lvalue '=' expr
lvalue      = NAME { '.' NAME }

ifStmt      = 'if' expr block [ 'else' ( ifStmt | block ) ]
forStmt     = 'for' NAME 'in' expr block
whileStmt   = 'while' expr block
returnStmt  = 'return' [ expr ]

moveStmt    = moveExpr
moveExpr    = 'move' amount [ 'from' target ] 'to' target
emitStmt    = 'emit' NAME '(' [ argList ] ')'
sleepStmt   = 'sleep' DURATION

exprStmt    = expr
```

Bedingungen stehen ohne runde Klammern; ein Block ist Pflicht. Damit ist
`if x { }` eindeutig und `if (x) y` gibt es nicht.

---

## 5. Ausdrücke

Von schwach nach stark bindend:

```
expr        = rangeExpr [ awaitTail ]

rangeExpr   = orExpr [ '..' orExpr ]

orExpr      = andExpr   { '||' andExpr }
andExpr     = cmpExpr   { '&&' cmpExpr }
cmpExpr     = addExpr   [ ( '==' | '!=' | '<' | '<=' | '>' | '>=' ) addExpr ]
addExpr     = mulExpr   { ( '+' | '-' ) mulExpr }
mulExpr     = unaryExpr { ( '*' | '/' | '%' ) unaryExpr }
unaryExpr   = [ '!' | '-' ] postfixExpr

postfixExpr = primary { '.' NAME [ '(' [ argList ] ')' ] }

primary     = INT | FLOAT | STRING | DURATION | 'true' | 'false' | 'it'
            | selection
            | NAME | ESCAPEDNAME
            | 'storage' | 'crafting' | 'world' | 'network'
            | 'workers' | 'multiblocks'
            | NAME '(' [ argList ] ')'
            | moveExpr
            | '(' expr ')'

argList     = arg { ',' arg }
arg         = [ NAME ':' ] expr
```

Ein Argument mit vorangestelltem Namen ist ein benanntes Argument
(`strategy: least_filled`).

### Warten

```
awaitStmt   = 'await' NAME [ 'where' expr ] [ 'timeout' DURATION elseBlock ]
elseBlock   = 'else' block
```

`timeout` ohne `else` ist ein Fehler: Sonst stünde nach Ablauf ein Wert da,
den es nie gab. Der `else`-Block muss den Ablauf verlassen; auch das prüft der
Übersetzer, nicht die Grammatik.

---

## 6. Auswahl von Gegenständen

```
amount      = [ INT ] selection

selection   = selTerm { 'except' selTerm }

selTerm     = KIND ':' [ NAMESPACE ( '/' | ':' ) ] pathPattern | 'all'

KIND        = 'item' | 'fluid' | 'chemical' | 'tag' | 'fluidtag'
pathPattern = ( NAMECHAR | '*' ) { NAMECHAR | '*' | '/' }
```

**`all` steht allein, wie `power`.** Es ist die Auswahl, die nichts aussucht:
was auch immer darin liegt. Damit ist auch `all` ein Schlüsselwort — wer
seinen Connector so nennt, schreibt ihn in Rückstrichen.

`*` darf an jeder Stelle stehen, auch mehrfach.

Namensraum und Pfad trennt ein Schrägstrich oder ein Doppelpunkt — die zweite
Form ist die, die JEI anzeigt. Ein Pfad behält seine weiteren Schrägstriche:
`tag:c:ingots/iron` meint den Namensraum `c` und den Pfad `ingots/iron`.

**Der Selektor ist ein einziges Token, und der Leerraum entscheidet darüber.**
`item:iron_ingot` und `fn craft(item: Item)` beginnen gleich — dieselben vier
Buchstaben, derselbe Doppelpunkt. Der Lexer liest einen Selektor nur dann,
wenn direkt hinter dem Doppelpunkt ein Pfadzeichen folgt; steht dort ein
Leerzeichen, sind es drei Token: Name, Doppelpunkt, Name.

```
item:iron_ingot     ein Token   SELECTOR
item: Item          drei Token  NAME COLON NAME
```

Das fiel erst beim Schreiben des Lexers auf. Die Alternative wäre gewesen,
Parameter nicht `item` nennen zu dürfen — eine Regel, die kein Spieler
erwartet und die der Editor bei jeder Vervollständigung erklären müsste.

**Fehlt der Namensraum, hängt die Bedeutung davon ab, ob ein `*` vorkommt:**
ohne Platzhalter gilt `minecraft`, mit Platzhalter alle Namensräume. Das ist
eine Regel des Übersetzers, nicht der Grammatik — die Form ist dieselbe.

### Was eine vorangestellte Menge bedeutet

```
move 64 tag:c/ores from chest to storage    64 Stück insgesamt
worker { filter tag:c/coals  maintain 64 }  64 je Art
```

Das ist nicht dasselbe, und das ist Absicht. `move` ist ein einmaliger
Transfer — wer 64 schreibt, meint einen Stapel. `maintain` ist ein Vorrat —
wer 64 schreibt, meint von jeder Sorte genug. Eine einheitliche Regel wäre in
einem der beiden Fälle die falsche.

---

## 7. Token

```
NAME         = ( BUCHSTABE | '_' ) { BUCHSTABE | ZIFFER | '_' }
ESCAPEDNAME  = '`' { beliebig außer '`' } '`'
NAMEPATTERN  = NAME mit mindestens einem '*'
INT          = ZIFFER { ZIFFER }
FLOAT        = INT '.' INT
DURATION     = ( INT | FLOAT ) ( 't' | 's' | 'min' | 'h' )
STRING       = '"' { beliebig außer '"' } '"'
KOMMENTAR    = '//' bis Zeilenende
```

`BUCHSTABE` ist alles, was Unicode als Buchstaben führt — `ofen_süd` ist ein
gültiger Name. Namen werden nach NFC verglichen.

`DURATION` erkennt der Lexer, nicht der Parser: `30s` ist ein Token, nicht die
Zahl 30 gefolgt von einem Namen. Sonst wäre `30 s` dasselbe wie `30s`, und
`sleep 30 s` sollte kein gültiger Code sein.

---

## 8. Was die Grammatik nicht entscheidet

Bewusst dem Übersetzer überlassen, weil er an der Fehlerstelle mehr sagen kann
als „unerwartetes Zeichen":

- ob `from` und `to` beim Worker vorhanden sind,
- ob eine Angabe doppelt vorkommt,
- ob ein `else` nach `timeout` den Ablauf verlässt,
- ob ein Name ein Connector, eine Variable oder unbekannt ist,
- ob ein Muster ohne Namensraum alle Namensräume meint,
- ob `when` nur beobachtbare Zustände liest.

Eine Grammatik, die das alles erzwingen wollte, könnte an der Fehlerstelle nur
sagen, dass dort etwas nicht hingehört. Genau das soll Manifold nicht.
