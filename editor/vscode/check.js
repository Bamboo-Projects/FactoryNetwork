// Prüft die Vervollständigung der Erweiterung.
//
// Dieselben Fälle wie CompletionsTest im Mod-Projekt: Die Logik steht
// zweimal da — einmal in Java für den Editor im Spiel, einmal hier für
// VS Code —, und zwei Fassungen derselben Regel laufen auseinander, wenn
// niemand nachmisst.
//
// Ohne Abhängigkeiten: node check.js. Das Modul "vscode" gibt es außerhalb
// von VS Code nicht, also steht hier eine Attrappe.
const Module = require('module');
const path = require('path');

const original = Module._load;
Module._load = function (request) {
    if (request === 'vscode') {
        const stub = function (label, kind) { return { label, kind }; };
        return {
            CompletionItem: function (label, kind) { this.label = label; this.kind = kind; },
            CompletionItemKind: { Keyword: 0, Variable: 1, EnumMember: 2, Property: 3 },
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
        };
    }
    return original.apply(this, arguments);
};

const providers = {};
const extensionPath = process.argv[2] || __dirname;
const extension = require(path.join(extensionPath, 'extension.js'));

const subscriptions = [];
extension.activate({ extensionPath, subscriptions });

/** Ein Dokument aus Zeilen, so viel davon, wie die Erweiterung anfasst. */
function doc(lines) {
    return {
        lineAt: (i) => ({ text: lines[i] }),
        getText: () => '',
        getWordRangeAtPosition: () => null,
    };
}

function complete(lines) {
    const last = lines.length - 1;
    const position = { line: last, character: lines[last].length };
    const result = providers.completion.provideCompletionItems(doc(lines), position) || [];
    return result.map(entry => entry.label);
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

function contains(name, lines, word, shouldHave) {
    const got = complete(lines);
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
    ['online', 'name', 'redstone', 'count']);
contains('Nach dem Punkt kein from', ['fn test() {', '    if crusher_1.'], 'from', false);
contains('Ohne Punkt keine Mitglieder', ['fn test() {', '    '], 'online', false);
contains('Eine Zahl mit Punkt ist kein Zugriff',
    ['fn test() {', '    let x = 3.'], 'online', false);

console.log(failures === 0 ? '\nalle Faelle stimmen' : '\n' + failures + ' Abweichungen');
process.exit(failures === 0 ? 0 : 1);
