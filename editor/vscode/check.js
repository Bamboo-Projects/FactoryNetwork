// Prüft die Vervollständigung der Erweiterung.
//
// Dieselben Fälle wie CompletionsTest im Mod-Projekt: Die Logik steht
// zweimal da — einmal in Java für den Editor im Spiel, einmal hier für
// VS Code —, und zwei Fassungen derselben Regel laufen auseinander, wenn
// niemand nachmisst.
//
// Eine Abweichung ist Absicht und keine Lücke: Nach einem Punkt bietet der
// Editor im Spiel die Gerätemitglieder nur hinter einem Namen an, den das
// Netz kennt. VS Code kennt kein laufendes Spiel und bietet sie hinter
// jedem Namen an — dort ist ein zu großzügiger Vorschlag besser als gar
// keiner. Der Fall „unbekannter Name" steht deshalb nur im Java-Test.
//
//
// Ohne Abhängigkeiten: node check.js. Das Modul "vscode" gibt es außerhalb
// von VS Code nicht, also steht hier eine Attrappe.
const Module = require('module');
const path = require('path');
const realFs = require('fs');

/**
 * Ein Projektordner, den es nicht gibt.
 *
 * Die Erweiterung liest die Nachbardateien über fs; damit der Prüflauf
 * nichts auf die Platte legt, liegen sie hier im Speicher. Alles außerhalb
 * dieses Ordners geht an das echte Modul weiter — die Erweiterung liest
 * ihre data/signatures.json ebenfalls über fs.
 */
const PROJECT = path.join(__dirname, 'erfundenes-projekt');
const projectFiles = {};

/** Liegt dieser Pfad im erfundenen Projekt? */
function inProject(file) {
    return file === PROJECT || file.startsWith(PROJECT + path.sep);
}

/** Der Schluessel in projectFiles: der Pfad unter PROJECT, mit Schraegstrichen. */
function keyOf(file) {
    return file.substring(PROJECT.length + 1).split(path.sep).join('/');
}

/**
 * Was unmittelbar in diesem Ordner liegt — Dateien und Unterordner.
 *
 * Die Schluessel von projectFiles duerfen Schraegstriche tragen; damit hat
 * das erfundene Projekt Unterordner, ohne dass etwas auf der Platte liegt.
 */
function entriesIn(folder) {
    const prefix = folder === PROJECT ? '' : keyOf(folder) + '/';
    const seen = [];
    for (const name of Object.keys(projectFiles)) {
        if (!name.startsWith(prefix)) {
            continue;
        }
        const rest = name.substring(prefix.length);
        const cut = rest.indexOf('/');
        const entry = cut < 0 ? rest : rest.substring(0, cut);
        if (!seen.includes(entry)) {
            seen.push(entry);
        }
    }
    return seen;
}

const original = Module._load;
Module._load = function (request) {
    if (request === 'fs') {
        const stub = Object.create(realFs);
        stub.readdirSync = (folder) => inProject(folder)
            ? entriesIn(folder)
            : realFs.readdirSync(folder);
        stub.statSync = (file) => inProject(file)
            ? { isDirectory: () => file === PROJECT || !(keyOf(file) in projectFiles) }
            : realFs.statSync(file);
        stub.readFileSync = (file, encoding) => {
            if (!inProject(file)) {
                return realFs.readFileSync(file, encoding);
            }
            const name = keyOf(file);
            if (!(name in projectFiles)) {
                // Wie im Spiel: Zwischen Auflisten und Lesen kann eine
                // Datei verschwinden. Die Erweiterung muss das aushalten.
                throw new Error('gibt es nicht: ' + file);
            }
            return projectFiles[name];
        };
        return stub;
    }
    if (request === 'vscode') {
        const stub = function (label, kind) { return { label, kind }; };
        return {
            CompletionItem: function (label, kind) { this.label = label; this.kind = kind; },
            CompletionItemKind: {
                Keyword: 0, Variable: 1, EnumMember: 2, Property: 3,
                Function: 4, Event: 5,
            },
            MarkdownString: function () {
                this.appendCodeblock = () => this;
                this.appendText = () => this;
            },
            Hover: function (contents, range) { this.contents = contents; this.range = range; },
            SignatureHelp: function () { this.signatures = []; },
            SignatureInformation: function (label, doc) {
                this.label = label; this.documentation = doc; this.parameters = [];
            },
            ParameterInformation: function (range) { this.range = range; },
            Range: function (line, from, endLine, to) {
                this.line = line;
                this.from = from;
                this.endLine = endLine;
                this.to = to;
            },
            Diagnostic: function (range, message, severity) {
                this.range = range;
                this.message = message;
                this.severity = severity;
            },
            DiagnosticSeverity: { Error: 0, Warning: 1 },
            languages: {
                registerCompletionItemProvider: (...a) => providers.completion = a[1],
                registerHoverProvider: (...a) => providers.hover = a[1],
                registerSignatureHelpProvider: (...a) => providers.signature = a[1],
                // Festgehalten wie die Provider: Damit laesst sich pruefen,
                // was die Erweiterung wirklich eintraegt.
                createDiagnosticCollection: () => {
                    providers.diagnostics = {
                        entries: new Map(),
                        set(uri, list) {
                            this.entries.set(uri.fsPath, list);
                        },
                        dispose() { },
                    };
                    return providers.diagnostics;
                },
            },
            workspace: {
                // Festgehalten statt weggeworfen: Damit lässt sich prüfen,
                // dass der Zwischenspeicher beim Speichern verfällt — ohne
                // zwei Sekunden zu warten.
                onDidSaveTextDocument: (handler) => {
                    providers.saved = handler;
                    return { dispose: () => { } };
                },
                onDidOpenTextDocument: (handler) => {
                    providers.opened = handler;
                    return { dispose: () => { } };
                },
                textDocuments: [],
            },
        };
    }
    return original.apply(this, arguments);
};

const providers = {};
const extensionPath = process.argv[2] || __dirname;
const extension = require(path.join(extensionPath, 'extension.js'));

const subscriptions = [];
extension.activate({ extensionPath, subscriptions });

/**
 * Ein Dokument aus Zeilen, so viel davon, wie die Erweiterung anfasst.
 *
 * Ohne Dateinamen hat es keine uri und liegt damit in keinem Ordner — dann
 * sieht die Erweiterung nur den Puffer, so wie bei einer Datei, die noch nie
 * gespeichert wurde.
 */
function doc(lines, file) {
    return {
        uri: file ? { scheme: 'file', fsPath: path.join(PROJECT, file) } : undefined,
        // Die Erweiterung traegt Fehler nur in Manifold-Dateien ein.
        languageId: 'manifold',
        lineAt: (i) => ({ text: lines[i] }),
        // Mit Bereich fragt nur die Erklärung beim Zeigen, und die bekommt
        // hier nichts zu lesen; ohne Bereich ist der ganze Puffer gemeint.
        getText: (range) => range ? '' : lines.join('\n'),
        getWordRangeAtPosition: () => null,
    };
}

function complete(lines, file) {
    const last = lines.length - 1;
    const position = { line: last, character: lines[last].length };
    const result = providers.completion
        .provideCompletionItems(doc(lines, file), position) || [];
    return result.map(entry => entry.label);
}

/**
 * Legt die Nachbardateien neu an.
 *
 * Das Speichersignal räumt dabei den Zwischenspeicher der Erweiterung: Ohne
 * das sähe der nächste Fall noch den Ordner des vorigen.
 */
function project(files) {
    for (const name of Object.keys(projectFiles)) {
        delete projectFiles[name];
    }
    Object.assign(projectFiles, files);
    providers.saved({ uri: { scheme: 'file', fsPath: path.join(PROJECT, 'main.mf') } });
}

let failures = 0;
function check(name, actual, expected) {
    const a = JSON.stringify(actual);
    const e = JSON.stringify(expected);
    if (a !== e) {
        console.log('FEHLER  ' + name + '\n   erwartet ' + e + '\n   bekommen ' + a);
        failures++;
    } else {
        console.log('ok      ' + name);
    }
}

function contains(name, lines, word, shouldHave, file) {
    const got = complete(lines, file);
    const has = got.includes(word);
    if (has !== shouldHave) {
        console.log('FEHLER  ' + name + ' — ' + word
            + (shouldHave ? ' fehlt' : ' steht zu Unrecht da') + ': ' + JSON.stringify(got));
        failures++;
    } else {
        console.log('ok      ' + name);
    }
}

// Dieselben Faelle wie in CompletionsTest und SignaturesTest.
contains('Anzeige bietet title', ['display halle {', '    '], 'title', true);
contains('Anzeige bietet scale', ['display halle {', '    '], 'scale', true);
contains('Oberste Ebene bietet recipe', [''], 'recipe', true);
contains('Oberste Ebene bietet store', [''], 'store', true);
contains('Speicher bietet priority', ['store kiste_1 {', '    '], 'priority', true);
contains('Speicher bietet filter', ['store kiste_1 {', '    '], 'filter', true);
contains('Speicher bietet kein in', ['store kiste_1 {', '    '], 'in', false);
contains('Rezept bietet in', ['recipe mahlen at brecher {', '    '], 'in', true);
contains('Rezept bietet out', ['recipe mahlen at brecher {', '    '], 'out', true);
contains('Rezept bietet kein from',
    ['recipe mahlen at brecher {', '    '], 'from', false);
contains('Hinter einer Liste steht plus',
    ['fn test() {', '    let x = storage.items().'], 'plus', true);
contains('Hinter einer Liste steht rest',
    ['fn test() {', '    let x = storage.items().'], 'rest', true);
contains('Anzeige bietet kein if', ['display halle {', '    '], 'if', false);
contains('Anzeige bietet kein from', ['display halle {', '    '], 'from', false);
contains('Worker bietet from', ['worker haul {', '    '], 'from', true);
contains('Worker bietet kein title', ['worker haul {', '    '], 'title', false);
check('Gruppe bietet genau zwei', complete(['group oefen {', '    ']),
    ['members', 'strategy']);
check('Textstelle bietet nichts', complete(['display halle {', '    title ']), []);
contains('Ausdrucksstelle bietet storage', ['display halle {', '    text '], 'storage', true);
contains('strategy bietet round_robin', ['worker haul {', '    strategy '],
    'round_robin', true);
check('Nach move 64 beide Wege', complete(['fn test() {', '    move 64 ']).sort(),
    ['from', 'to']);
contains('Funktion bietet move', ['fn test() {', '    '], 'move', true);
contains('Funktion bietet storage', ['fn test() {', '    '], 'storage', true);
check('Volle Anweisung bietet nichts',
    complete(['worker haul {', '    rate 64 per 5s ']), []);
contains('Oberste Ebene bietet display', ['di'], 'display', true);

// Nach dem Punkt: was ein Gerät hat. Dieselbe Liste wie in
// Signatures.MEMBERS — hier kommt sie aus signatures.json, und der
// Java-Test hält beide gleich.
check('Nach dem Punkt die Gerätemitglieder',
    complete(['fn test() {', '    if crusher_1.']),
    ['online', 'name', 'redstone', 'count', 'insert', 'items', 'slots', 'energy',
     'click']);
contains('Nach dem Punkt kein from', ['fn test() {', '    if crusher_1.'], 'from', false);
contains('Ohne Punkt keine Mitglieder', ['fn test() {', '    '], 'online', false);
contains('Eine Zahl mit Punkt ist kein Zugriff',
    ['fn test() {', '    let x = 3.'], 'online', false);

// Nach network. steht das Netz selbst und kein Geraet. Beides sind
// Punktzugriffe, und ohne diese Unterscheidung boete die Erweiterung an einem
// Netz redstone() an.
check('Nach network. die Netzmitglieder',
    complete(['fn test() {', '    if network.']),
    ['power', 'capacity']);
contains('Nach network. kein redstone',
    ['fn test() {', '    if network.'], 'redstone', false);
contains('Nach einem Geraetepunkt kein power',
    ['fn test() {', '    if crusher_1.'], 'power', false);

// Hinter einer schließenden Klammer steht eine Liste und kein Gerät.
// Dieselbe Unterscheidung wie in Completions.afterListCall.
check('Nach items() die Listenoperationen',
    complete(['fn test() {', '    let x = storage.items().']),
    ['count', 'first', 'sum', 'where', 'sort', 'plus', 'without', 'rest']);
// Was an einem Posten steht. Dieselbe Liste wie Signatures.ENTRY_MEMBERS —
// „it" ist kein Gerät, und redstone() daran wäre Unsinn.
check('Nach it. die Angaben eines Postens',
    complete(['fn test() {', '    log(it.']),
    ['amount', 'item', 'fluid', 'chemical']);
contains('Nach it. kein redstone', ['fn test() {', '    log(it.'], 'redstone', false);
contains('Nach items() kein online',
    ['fn test() {', '    let x = storage.items().'], 'online', false);
contains('Nach einem Gerätepunkt kein where',
    ['fn test() {', '    if crusher_1.'], 'where', false);

// Nach on steht ein Ereignisname und keine Deklaration. Die fünf des Netzes
// stehen in keiner Datei und kommen aus der Tabelle.
check('Nach on die eingebauten Ereignisse', complete(['on ']).sort(),
    ['crafting_failed', 'crafting_finished', 'device_changed', 'device_offline',
        'device_online', 'device_output', 'redstone_changed']);
contains('Nach on kein worker', ['on '], 'worker', false);

// Filter-Vorlagen: eine Deklaration mehr, ein Block mit except, und ihr Name
// überall dort, wo eine Auswahl steht.
contains('Auf oberster Ebene gibt es filter', [''], 'filter', true);
contains('In einer Vorlage steht except', ['filter erze {', '    '], 'except', true);
contains('In einer Vorlage steht kein worker', ['filter erze {', '    '], 'worker', false);
contains('Nach filter im Worker steht die Vorlage',
    ['filter erze {', '    tag:c/ores', '}', '', 'worker holt {', '    from grube',
        '    to storage', '    filter '], 'erze', true);
contains('Nach on auch das eigene Ereignis',
    ['event Fertig(nummer: Int)', '', 'on '], 'Fertig', true);
contains('await bietet die eingebauten Ereignisse',
    ['fn test() {', '    await '], 'device_changed', true);

// Die Funktionen ohne Empfänger — sie standen in keiner Tabelle.
contains('Funktion bietet log', ['fn test() {', '    '], 'log', true);
contains('Funktion bietet warn', ['fn test() {', '    '], 'warn', true);
contains('Ein Worker bietet kein warn', ['worker haul {', '    '], 'warn', false);

// Auf oberster Ebene: die Deklarationen, und global mit seiner Form.
contains('Oberste Ebene bietet global', ['gl'], 'global', true);
contains('In einem Block kein global', ['worker haul {', '    '], 'global', false);

// Namen aus dem ganzen Projekt.
//
// Ein Programm ist ein Ordner aus mehreren Dateien mit einem gemeinsamen
// Namensraum: Was in werte.mf erklärt wird, wird in main.mf gerufen, und es
// gibt kein import, das es nennt. Diese Fälle stehen nur hier — der Editor
// im Spiel liest bisher nur die offene Datei.
project({
    'werte.mf': [
        'global modus = "tag"',
        '',
        'fn heizen() {',
        '    return 1',
        '}',
        '',
        'event Fertig(id: Int)',
        '',
        'group oefen {',
        '    members ofen_1',
        '}',
        '',
        'multiblock Werk {',
        '    devices eingang',
        '',
        '    fn starte() {',
        '        return 1',
        '    }',
        '}',
        '',
        'display wand {',
        '    title "Wand"',
        '}',
        '',
        'worker haul {',
        '    from storage',
        '}',
    ].join('\n'),
    'notiz.txt': 'fn geheim() {\n}\n',
});

contains('Funktion aus der Nachbardatei',
    ['fn main() {', '    '], 'heizen', true, 'main.mf');
contains('Funktion aus einem Multiblock',
    ['fn main() {', '    '], 'starte', true, 'main.mf');
contains('Globaler Wert in der Funktion',
    ['fn main() {', '    '], 'modus', true, 'main.mf');
contains('Knopf bietet die Funktion',
    ['display halle {', '    button "Start" '], 'heizen', true, 'main.mf');
contains('Knopf bietet kein Ereignis',
    ['display halle {', '    button "Start" '], 'Fertig', false, 'main.mf');
contains('emit bietet das Ereignis',
    ['fn main() {', '    emit '], 'Fertig', true, 'main.mf');
contains('Ein Ereignis ist keine Anweisung',
    ['fn main() {', '    '], 'Fertig', false, 'main.mf');
contains('Ziel bietet die Gruppe',
    ['worker neu {', '    to '], 'oefen', true, 'main.mf');
contains('Ziel bietet den Multiblock',
    ['worker neu {', '    to '], 'Werk', true, 'main.mf');
contains('Ziel bietet weiter storage',
    ['worker neu {', '    to '], 'storage', true, 'main.mf');
contains('Quelle bietet crafting',
    ['worker neu {', '    from '], 'crafting', true, 'main.mf');
contains('Ziel bietet crafting nicht',
    ['worker neu {', '    to '], 'crafting', false, 'main.mf');
contains('Mitglieder bieten die Gruppe',
    ['group neu {', '    members '], 'oefen', true, 'main.mf');
contains('Ausdrucksstelle bietet den globalen Wert',
    ['display halle {', '    text '], 'modus', true, 'main.mf');

// Ein Worker und eine Anzeige tragen zwar einen Namen, aber keine Stelle in
// der Sprache nimmt ihn: Gesammelt werden sie, vorgeschlagen nirgends.
contains('Ein Worker steht an keiner Zielstelle',
    ['worker neu {', '    to '], 'haul', false, 'main.mf');
contains('Eine Anzeige steht an keiner Zielstelle',
    ['worker neu {', '    to '], 'wand', false, 'main.mf');

contains('Nur .mf-Dateien zählen',
    ['fn main() {', '    '], 'geheim', false, 'main.mf');
contains('Ohne Dateinamen keine Nachbarn',
    ['fn main() {', '    '], 'heizen', false);
contains('Die ungespeicherte Funktion zählt schon',
    ['fn kuehlen() {', '}', 'fn main() {', '    '], 'kuehlen', true, 'main.mf');

// Die offene Datei kommt aus dem Puffer, nicht von der Platte: Sonst stünde
// eine gerade gelöschte Funktion weiter in der Liste.
project({ 'main.mf': 'fn alt() {\n}\n' });
contains('Die eigene Datei kommt aus dem Puffer',
    ['fn main() {', '    '], 'alt', false, 'main.mf');

// Der Zwischenspeicher hält einen Ordner nur bis zum nächsten Speichern.
project({ 'werte.mf': 'fn heizen() {\n}\n' });
// Der Rueckweg der Bruecke: Was das Spiel neben die Dateien schreibt, traegt
// die Erweiterung ein. Kein zweiter Uebersetzer in JavaScript — es rechnet
// der, der es ohnehin tut.
project({
    'main.mf': 'fn main() {\n}\n',
    '.fn-status.json': JSON.stringify({
        diagnostics: {
            'main.mf': [{
                line: 2, column: 5, length: 4, severity: 'warning',
                message: 'Nichts im Netz heisst kist.', hint: 'Meintest du kiste?',
            }],
        },
        connectors: ['kiste', 'brecher'],
        displays: ['halle'],
    }),
});

contains('Geraetenamen aus der Welt stehen bei to',
    ['worker neu {', '    to '], 'brecher', true, 'main.mf');
contains('Und bei from',
    ['worker neu {', '    from '], 'kiste', true, 'main.mf');

providers.opened(doc(['fn main() {', '}'], 'main.mf'));
const eingetragen = providers.diagnostics.entries.get(
    path.join(PROJECT, 'main.mf')) || [];
check('Der Fehler aus dem Spiel steht im Editor', eingetragen.length, 1);
check('Er steht in der richtigen Zeile', eingetragen[0] && eingetragen[0].range.line, 1);
check('Und in der richtigen Spalte', eingetragen[0] && eingetragen[0].range.from, 4);
check('Eine Warnung bleibt eine Warnung', eingetragen[0] && eingetragen[0].severity, 1);
check('Der Hinweis steht dabei',
    eingetragen[0] && eingetragen[0].message.includes('Meintest du'), true);

// Ordner im Projekt: Alle Dateien teilen einen Namensraum, auch über Ebenen
// hinweg. Wer nur den eigenen Ordner liest, schlägt in einem gegliederten
// Projekt genau den Teil vor, den man gerade nicht braucht.
project({
    'main.mf': 'fn hauptsache() {\n}\n',
    'erz/brecher.mf': 'fn zerkleinern() {\n}\n',
});
contains('Die Wurzel sieht in den Unterordner',
    ['fn main() {', '    '], 'zerkleinern', true, 'main.mf');
contains('Und der Unterordner die Wurzel',
    ['fn main() {', '    '], 'hauptsache', true, 'erz/brecher.mf');

contains('Vor der Änderung gibt es kuehlen nicht',
    ['fn main() {', '    '], 'kuehlen', false, 'main.mf');
project({ 'werte.mf': 'fn kuehlen() {\n}\n' });
contains('Nach dem Speichern steht die neue Funktion da',
    ['fn main() {', '    '], 'kuehlen', true, 'main.mf');
contains('Nach dem Speichern ist die alte weg',
    ['fn main() {', '    '], 'heizen', false, 'main.mf');

// Die Praefixe kommen aus dem Spiel, seit die Ressourcenarten eine offene
// Registry sind: Was eine fremde Mod anmeldet, kann diese Erweiterung nicht
// wissen. Ohne Statusdatei bleiben die eingebauten.
project({
    'main.mf': 'fn main() {\n}\n',
    '.fn-status.json': JSON.stringify({
        diagnostics: {},
        connectors: [],
        displays: [],
        prefixes: ['item', 'tag', 'fluid', 'fluidtag', 'chemical', 'source'],
    }),
});

contains('Die Praefixe aus dem Spiel stehen bei filter',
    ['worker neu {', '    filter '], 'source:', true, 'main.mf');
contains('Und die eingebauten auch',
    ['worker neu {', '    filter '], 'item:', true, 'main.mf');

project({ 'main.mf': 'fn main() {\n}\n' });
contains('Ohne Spiel bleiben die eingebauten',
    ['worker neu {', '    filter '], 'item:', true, 'main.mf');
contains('Und eine fremde Art steht dann nicht da',
    ['worker neu {', '    filter '], 'source:', false, 'main.mf');

console.log(failures === 0 ? '\nalle Faelle stimmen' : '\n' + failures + ' Abweichungen');
process.exit(failures === 0 ? 0 : 1);
