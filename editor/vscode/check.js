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

const original = Module._load;
Module._load = function (request) {
    if (request === 'fs') {
        const stub = Object.create(realFs);
        stub.readdirSync = (folder) => folder === PROJECT
            ? Object.keys(projectFiles)
            : realFs.readdirSync(folder);
        stub.readFileSync = (file, encoding) => {
            if (path.dirname(file) !== PROJECT) {
                return realFs.readFileSync(file, encoding);
            }
            const name = path.basename(file);
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
            languages: {
                registerCompletionItemProvider: (...a) => providers.completion = a[1],
                registerHoverProvider: (...a) => providers.hover = a[1],
                registerSignatureHelpProvider: (...a) => providers.signature = a[1],
            },
            workspace: {
                // Festgehalten statt weggeworfen: Damit lässt sich prüfen,
                // dass der Zwischenspeicher beim Speichern verfällt — ohne
                // zwei Sekunden zu warten.
                onDidSaveTextDocument: (handler) => {
                    providers.saved = handler;
                    return { dispose: () => { } };
                },
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

// Nach dem Punkt: die vier Dinge, die ein Gerät hat. Dieselbe Liste wie in
// Signatures.MEMBERS — hier kommt sie aus signatures.json, und der
// Java-Test hält beide gleich.
check('Nach dem Punkt die Gerätemitglieder',
    complete(['fn test() {', '    if crusher_1.']),
    ['online', 'name', 'redstone', 'count', 'insert', 'items']);
contains('Nach dem Punkt kein from', ['fn test() {', '    if crusher_1.'], 'from', false);
contains('Ohne Punkt keine Mitglieder', ['fn test() {', '    '], 'online', false);
contains('Eine Zahl mit Punkt ist kein Zugriff',
    ['fn test() {', '    let x = 3.'], 'online', false);

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
contains('Vor der Änderung gibt es kuehlen nicht',
    ['fn main() {', '    '], 'kuehlen', false, 'main.mf');
project({ 'werte.mf': 'fn kuehlen() {\n}\n' });
contains('Nach dem Speichern steht die neue Funktion da',
    ['fn main() {', '    '], 'kuehlen', true, 'main.mf');
contains('Nach dem Speichern ist die alte weg',
    ['fn main() {', '    '], 'heizen', false, 'main.mf');

console.log(failures === 0 ? '\nalle Faelle stimmen' : '\n' + failures + ' Abweichungen');
process.exit(failures === 0 ? 0 : 1);
