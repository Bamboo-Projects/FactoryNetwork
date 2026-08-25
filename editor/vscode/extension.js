// Manifold in VS Code — Vervollständigung, Hinweise und Formanzeige.
//
// Bewusst reines JavaScript und kein TypeScript: Die Erweiterung wird
// kopiert, nicht gebaut. Ein Übersetzungsschritt hieße npm install und tsc,
// und dann kopiert sie niemand mehr.
//
// Was sie über die Sprache weiß, steht in data/signatures.json. Diese Datei
// wird aus Signatures.java erzeugt; ein Test im Mod-Projekt hält beide
// gleich. Damit gibt es die Regel „hinter row kommt ein Text und dann ein
// Ausdruck" weiterhin einmal und nicht zweimal.
//
// Was sie über das einzelne Programm weiß, liest sie aus dem Ordner: Alle
// .mf-Dateien darin teilen einen Namensraum, und ohne die Nachbardateien
// kennt sie die Hälfte der Namen nicht, die man gerade tippen will.
//
// Was sie nicht kann: Fehler melden. Dafür bräuchte es den Übersetzer, und
// der ist in Java. Fehler zeigt das Terminal im Spiel.

const vscode = require('vscode');
const fs = require('fs');
const path = require('path');

let table = { blocks: {}, strategies: [], declarations: [], members: [],
              listMembers: [], builtinEvents: [], freeFunctions: [], topLevel: [] };

/** Zu welchen Blockarten Anweisungen gehören statt fester Angaben. */
const CODE_BLOCKS = ['fn', 'on', 'multiblock'];

/** Was an einer Ausdrucksstelle immer geht. */
// Nur, was der Interpreter auch auswertet. Die Sprache parst mehr —
// world, network, workers, multiblocks —, aber wer sie hinschreibt, bekommt
// eine Fehlermeldung. Ein Vorschlag, der dorthin führt, ist schlechter als
// keiner. Sie kommen zurück, sobald sie etwas tun.
const BUILTINS = ['storage'];

/** Was nur als Quelle taugt: `from crafting` bestellt, was fehlt. */
// Und nur dort. Gefertigt wird in den Speicher, und von dort holt es ein
// zweiter Worker ab — `to crafting` führte in eine Meldung, die dem Vorschlag
// widerspricht, der sie ausgelöst hat.
const SOURCES = ['crafting'];

/**
 * Woran eine Zeile zu erkennen ist, die einen Namen vergibt.
 *
 * Über den Text und nicht über einen Parser: Der steht in Java, und ihn
 * hier ein zweites Mal zu schreiben hieße, zwei Fassungen derselben
 * Grammatik gleich zu halten. Die erste Zeile einer Deklaration ändert sich
 * selten — für sie reicht eine Zeilenform.
 *
 * Auch eingerückt: Ein fn steckt auch in einem multiblock, und von dort aus
 * wird es genauso gerufen wie eines auf oberster Ebene.
 *
 * Und großzügig gelesen: Die Klammer hinter dem Namen ist nicht Bedingung.
 * Wer gerade tippt, hat sie noch nicht — und ein Name zu viel in der Liste
 * ist der kleinere Fehler als ein fehlender.
 */
const DECLARED_NAMES = [
    { keyword: 'fn', pattern: /^fn\s+([A-Za-z_][A-Za-z0-9_]*)/ },
    { keyword: 'worker', pattern: /^worker\s+([A-Za-z_][A-Za-z0-9_]*)/ },
    { keyword: 'group', pattern: /^group\s+([A-Za-z_][A-Za-z0-9_]*)/ },
    { keyword: 'filter', pattern: /^filter\s+([A-Za-z_][A-Za-z0-9_]*)/ },
    { keyword: 'multiblock', pattern: /^multiblock\s+([A-Za-z_][A-Za-z0-9_]*)/ },
    { keyword: 'event', pattern: /^event\s+([A-Za-z_][A-Za-z0-9_]*)/ },
    { keyword: 'display', pattern: /^display\s+([A-Za-z_][A-Za-z0-9_]*)/ },
    // global ist die einzige Deklaration ohne Block, und hier die einzige
    // mit Bedingung: Ohne das Gleichheitszeichen ist die Zeile keine
    // Erklärung, sondern eine halb getippte — der Übersetzer sagt dazu
    // dasselbe.
    { keyword: 'global', pattern: /^global\s+([A-Za-z_][A-Za-z0-9_]*)\s*=/ },
];

/** Wie lange ein einmal gelesener Ordner gilt. */
const FOLDER_MS = 2000;

/** Was in den Ordnern steht: Pfad -> { files: {Dateiname: Namen}, stamp }. */
const folders = new Map();

/** Und wo die Wurzel eines Projekts liegt, je Datei. */
const roots = new Map();

function load(context) {
    const file = path.join(context.extensionPath, 'data', 'signatures.json');
    table = JSON.parse(fs.readFileSync(file, 'utf8'));
}

/**
 * Steht der Cursor hinter einem Punkt, der auf einen Namen folgt?
 *
 * <p>Eine Zahl davor zählt nicht: `3.5` ist kein Punktzugriff.
 */
function afterDot(document, position) {
    const upToCursor = document.lineAt(position.line).text.substring(0, position.character);
    return /[a-zA-Z_][a-zA-Z0-9_]*\.[a-zA-Z0-9_]*$/.test(upToCursor);
}

/**
 * Steht der Cursor hinter dem Punkt einer Liste?
 *
 * <p>Hinter `storage.items().` steht eine Liste und kein Gerät. Vor
 * afterDot zu prüfen: Dort wird ein Name vor dem Punkt erwartet, hier
 * steht eine schließende Klammer, und deshalb griff bisher gar nichts.
 */
function afterListCall(document, position) {
    const upToCursor = document.lineAt(position.line).text.substring(0, position.character);
    return /\)\s*\.[a-zA-Z0-9_]*$/.test(upToCursor);
}

/** Die Formen, die in dieser Blockart gelten. */
function shapesFor(block) {
    if (!block) {
        return [];
    }
    if (CODE_BLOCKS.includes(block)) {
        return table.blocks.fn || [];
    }
    return table.blocks[block] || [];
}

/**
 * Welche Deklaration die Zeile umgibt.
 *
 * Rückwärts bis zur ersten Zeile, die auf Spalte null mit einem
 * Deklarationswort anfängt — Deklarationen stehen nicht ineinander.
 */
function enclosingBlock(document, lineNumber) {
    for (let i = lineNumber; i >= 0; i--) {
        const text = document.lineAt(i).text;
        if (/^\s/.test(text) || text.trim() === '') {
            continue;
        }
        const first = text.trim().split(/\s+/)[0];
        if (table.declarations.includes(first)) {
            return first;
        }
    }
    return null;
}

/** Die Wörter hinter dem Schlüsselwort; ein Text in Anführungszeichen ist eines. */
function splitWords(rest) {
    const words = [];
    let i = 0;
    while (i < rest.length) {
        while (i < rest.length && rest[i] === ' ') {
            i++;
        }
        if (i >= rest.length) {
            break;
        }
        const start = i;
        if (rest[i] === '"') {
            i++;
            while (i < rest.length && rest[i] !== '"') {
                i++;
            }
            i = Math.min(i + 1, rest.length);
        } else {
            while (i < rest.length && rest[i] !== ' ') {
                i++;
            }
        }
        words.push(rest.substring(start, i));
    }
    return words;
}

/**
 * Welche Stelle nach diesen Wörtern dran ist.
 *
 * Steht dabei eine Stelle an, die ein festes Wort sein *kann*, und das Wort
 * passt nicht dazu, fällt sie samt ihrem Wert weg: „move 64 to kiste" hat
 * kein „from", und ohne diese Regel landete das „to" auf der Stelle der
 * Quelle.
 */
function slotAfter(signature, words) {
    let slot = 0;
    for (const word of words) {
        while (slot < signature.slots.length) {
            const candidate = signature.slots[slot];
            if (candidate.optional && candidate.label !== word) {
                slot += 2;
                continue;
            }
            break;
        }
        slot++;
    }
    return slot;
}

/** Wo der Cursor in einer Angabe steht, oder null. */
function whereAt(document, position) {
    const block = enclosingBlock(document, position.line);
    const upToCursor = document.lineAt(position.line).text.substring(0, position.character);
    const text = upToCursor.replace(/^\s+/, '');
    const wordEnd = text.indexOf(' ');
    if (wordEnd < 0) {
        return null;
    }
    const keyword = text.substring(0, wordEnd);
    const signature = shapesFor(block).find(entry => entry.keyword === keyword);
    if (!signature) {
        return null;
    }
    const rest = text.substring(wordEnd);
    let words = splitWords(rest);
    if (rest.length > 0 && rest[rest.length - 1] !== ' ' && words.length > 0) {
        words = words.slice(0, words.length - 1);
    }
    const index = slotAfter(signature, words);
    return { signature, index, slot: signature.slots[index] || null };
}

function item(label, kind, detail, doc) {
    const entry = new vscode.CompletionItem(label, kind);
    if (detail) {
        entry.detail = detail;
    }
    if (doc) {
        entry.documentation = doc;
    }
    return entry;
}

/** Die Namen, die dieser Text vergibt. */
function symbolsIn(text, file) {
    const found = [];
    for (const raw of text.split('\n')) {
        const line = raw.trim();
        for (const declaration of DECLARED_NAMES) {
            const match = declaration.pattern.exec(line);
            if (match) {
                found.push({ keyword: declaration.keyword, name: match[1], file });
                // Eine Zeile erklärt höchstens einen Namen.
                break;
            }
        }
    }
    return found;
}

/**
 * Die Namen der Nachbardateien, höchstens alle zwei Sekunden neu gelesen.
 *
 * Bei jedem Tastendruck den Ordner zu lesen wäre bezahlbar, aber unnötig;
 * ihn einmal zu lesen und für immer zu behalten wäre falsch: In denselben
 * Ordner schreibt auch das Spiel, und wer zuletzt geschrieben hat, gewinnt.
 * Eine kurze Frist trifft beides — und wer hier speichert, wirft den
 * Zwischenspeicher ohnehin sofort weg.
 */
function filesOf(folder) {
    const cached = folders.get(folder);
    if (cached && Date.now() - cached.stamp < FOLDER_MS) {
        return cached.files;
    }
    const files = {};
    collect(folder, '', files, 0);
    folders.set(folder, { files, stamp: Date.now() });
    return files;
}

/** Wie tief unter der Wurzel gesucht wird — Bremse gegen einen Verweiskreis. */
const MAX_DEPTH = 16;

/**
 * Sammelt die Dateien unter einem Ordner, mitsamt Unterordnern.
 *
 * Ein Projekt darf gegliedert sein — `erz/brecher.mf` —, und die Ordner sind
 * reine Ordnung für den Menschen: Der Namensraum bleibt einer. Wer nur den
 * eigenen Ordner liest, schlägt in einem gegliederten Projekt genau den Teil
 * vor, den man gerade nicht braucht.
 *
 * Der Name trägt den Pfad mit Schrägstrichen, so wie im Spiel. Auf Windows
 * liefert `path.join` den Rückstrich, und `Steht in erz\brecher.mf` wäre ein
 * Name, den es im Projekt nicht gibt.
 */
function collect(folder, prefix, files, depth) {
    if (depth > MAX_DEPTH) {
        return;
    }
    let names = [];
    try {
        names = fs.readdirSync(folder);
    } catch (error) {
        // Kein Ordner, keine Nachbarn. Beim Tippen ist ein fehlender
        // Vorschlag die bessere Antwort als eine Fehlermeldung.
        return;
    }
    for (const name of names) {
        const full = path.join(folder, name);
        let isFolder = false;
        try {
            isFolder = fs.statSync(full).isDirectory();
        } catch (error) {
            // Zwischen Auflisten und Nachsehen kann der Eintrag weg sein.
            continue;
        }
        if (isFolder) {
            collect(full, prefix + name + '/', files, depth + 1);
            continue;
        }
        if (!name.endsWith('.mf')) {
            continue;
        }
        try {
            const text = fs.readFileSync(full, 'utf8');
            files[prefix + name] = symbolsIn(text, prefix + name);
        } catch (error) {
            // Zwischen Auflisten und Lesen kann die Datei weg sein: Das
            // Spiel schreibt und löscht in demselben Ordner.
        }
    }
}

/**
 * Die Wurzel des Projekts, zu dem diese Datei gehört.
 *
 * Nicht ihr Ordner: In `erz/brecher.mf` steht ein `fn`, das `main.mf` eine
 * Ebene höher ruft, und beide teilen einen Namensraum.
 *
 * Nach oben gegangen wird, solange der Ordner darüber selbst Programmdateien
 * enthält — sonst landete man bei jemandem, der zufällig eine `.mf` im
 * Stammverzeichnis liegen hat. Und Schluss ist in jedem Fall an einem Ordner,
 * der `controller_` heißt: Das ist die Wurzel, die die Brücke neben der Welt
 * anlegt, und darüber liegen die Ordner der anderen Controller.
 */
function projectRootOf(file) {
    const cached = roots.get(file);
    if (cached && Date.now() - cached.stamp < FOLDER_MS) {
        return cached.root;
    }
    let dir = path.dirname(file);
    for (let step = 0; step < MAX_DEPTH; step++) {
        if (path.basename(dir).startsWith('controller_')) {
            break;
        }
        const parent = path.dirname(dir);
        if (parent === dir || !hasProgram(parent)) {
            break;
        }
        dir = parent;
    }
    roots.set(file, { root: dir, stamp: Date.now() });
    return dir;
}

/** Liegt in diesem Ordner unmittelbar eine Programmdatei? */
function hasProgram(folder) {
    try {
        return fs.readdirSync(folder).some(name => name.endsWith('.mf'));
    } catch (error) {
        return false;
    }
}

/** Der Name einer Datei im Projekt: ihr Pfad unter der Wurzel, mit Schrägstrichen. */
function nameUnder(root, file) {
    return path.relative(root, file).split(path.sep).join('/');
}

/**
 * Alle Namen, die das Projekt um dieses Dokument herum vergibt.
 *
 * Ein Projekt ist ein Ordner, und alle Dateien darin teilen einen
 * Namensraum. Die Funktion, die hier gerufen wird, steht deshalb meistens
 * nebenan — und es gibt kein import, das sie nennt. Wer nur die offene
 * Datei liest, schlägt in einem Projekt aus acht Dateien ein Achtel vor.
 *
 * Die offene Datei kommt aus dem Puffer statt von der Platte: Wer gerade
 * `fn heizen()` geschrieben hat, ruft es zwei Zeilen später auf, ohne
 * vorher zu speichern.
 */
function projectSymbols(document) {
    const own = document.uri && document.uri.scheme === 'file'
        ? document.uri.fsPath : null;
    const symbols = [];
    let self = null;
    if (own) {
        const root = projectRootOf(own);
        self = nameUnder(root, own);
        const files = filesOf(root);
        for (const name of Object.keys(files)) {
            if (name !== self) {
                symbols.push(...files[name]);
            }
        }
    }
    symbols.push(...symbolsIn(document.getText(), self));
    return symbols;
}

/**
 * Die Namen dieser Deklarationsarten als Vorschläge, jeder einmal.
 *
 * Zwei Dateien dürfen denselben Namen nicht zweimal vergeben; dass sie es
 * doch tun, meldet der Übersetzer. Hier steht der Name deswegen trotzdem
 * nur einmal in der Liste — zweimal dasselbe Wort hilft niemandem.
 */
function symbolItems(symbols, keywords, kind) {
    const items = [];
    const seen = [];
    for (const symbol of symbols) {
        if (!keywords.includes(symbol.keyword) || seen.includes(symbol.name)) {
            continue;
        }
        seen.push(symbol.name);
        items.push(item(symbol.name, kind, symbol.keyword,
            symbol.file ? 'Steht in ' + symbol.file : undefined));
    }
    return items;
}

/** Was an dieser Stelle stehen darf. */
/**
 * Die Ereignisse: erst die vier des Netzes, dann die des Projekts.
 *
 * Die vier stehen in keiner Datei und kommen deshalb aus der Tabelle.
 */
function eventItems(symbols) {
    return (table.builtinEvents || [])
        .map(name => item(name, vscode.CompletionItemKind.Event))
        .concat(symbolItems(symbols, ['event'], vscode.CompletionItemKind.Event));
}

function completionsFor(where, symbols) {
    const slot = where.slot;
    if (!slot) {
        return [];
    }
    switch (slot.kind) {
        case 'SELECTION':
            // Gegenstände und Tags kennt nur das laufende Spiel. Was die
            // Erweiterung beisteuern kann, sind die Filter-Vorlagen des
            // Projekts — und die sind an dieser Stelle oft das Gemeinte.
            return symbolItems(symbols, ['filter'], vscode.CompletionItemKind.Variable);
        case 'STRATEGY':
            return table.strategies.map(name =>
                item(name, vscode.CompletionItemKind.EnumMember));
        case 'FUNCTION':
            return symbolItems(symbols, ['fn'], vscode.CompletionItemKind.Function);
        case 'EVENT':
            return eventItems(symbols);
        case 'MEMBERS':
            // Mitglieder sind Connectoren, und die kennt nur das laufende
            // Spiel. Was die Erweiterung beisteuern kann, sind die Namen aus
            // dem Projekt: Gruppen und Multiblocks stehen hier genauso.
            return symbolItems(symbols, ['group', 'multiblock'],
                vscode.CompletionItemKind.Variable);
        case 'TARGET': {
            const names = where.signature.keyword === 'from'
                ? BUILTINS.concat(SOURCES) : BUILTINS;
            return names.map(name => item(name, vscode.CompletionItemKind.Variable))
                .concat(symbolItems(symbols, ['group', 'multiblock'],
                    vscode.CompletionItemKind.Variable));
        }
        case 'EXPR':
            return BUILTINS.map(name => item(name, vscode.CompletionItemKind.Variable))
                .concat(symbolItems(symbols, ['global'],
                    vscode.CompletionItemKind.Variable));
        case 'LITERAL': {
            const words = [slot.label];
            const next = where.signature.slots[where.index + 2];
            if (slot.optional && next && next.kind === 'LITERAL') {
                words.push(next.label);
            }
            return words.map(word => item(word, vscode.CompletionItemKind.Keyword));
        }
        default:
            // Für einen Text, eine Zahl, eine Dauer oder einen neuen Namen
            // gibt es nichts vorzuschlagen. Die Formanzeige sagt trotzdem,
            // was hier steht.
            return [];
    }
}

function activate(context) {
    load(context);

    context.subscriptions.push(vscode.languages.registerCompletionItemProvider('manifold', {
        provideCompletionItems(document, position) {
            // Nach einem Punkt: was an einem Gerät steht. Vor der Prüfung auf
            // die Stelle in einer Angabe, weil „to crusher_1." sonst als
            // angefangener Zielname gelesen würde.
            //
            // Hier ohne Prüfung, ob der Name wirklich ein Connector ist: Die
            // Erweiterung sieht kein laufendes Spiel und kennt die Namen im
            // Netz nicht. Im Spiel prüft der Editor sie, hier wird angeboten.
            if (afterListCall(document, position)) {
                return table.listMembers.map(member => item(
                    member.name, vscode.CompletionItemKind.Method,
                    member.shape, member.help));
            }
            if (afterDot(document, position)) {
                return table.members.map(member => item(
                    member.name, vscode.CompletionItemKind.Property,
                    member.shape, member.help));
            }
            const symbols = projectSymbols(document);
            const where = whereAt(document, position);
            if (where) {
                return completionsFor(where, symbols);
            }
            const block = enclosingBlock(document, position.line);
            const indented = /^\s/.test(document.lineAt(position.line).text);
            // Nach „on " steht ein Ereignisname und keine Deklaration. Ein
            // Block mit vertipptem Namen wird übernommen und läuft nie —
            // hier lässt sich das verhindern statt melden.
            if (!indented && /^on\s+[a-zA-Z0-9_]*$/.test(
                    document.lineAt(position.line).text.substring(0, position.character))) {
                return eventItems(symbols);
            }
            if (!indented) {
                // Die meisten Deklarationen öffnen einen Block und haben
                // keine Form; global ist die Ausnahme und bringt seine mit.
                return table.declarations.map(word => {
                    const shape = (table.topLevel || []).find(s => s.keyword === word);
                    return shape
                        ? item(word, vscode.CompletionItemKind.Keyword,
                            shape.shape.substring(word.length).trim(), shape.help)
                        : item(word, vscode.CompletionItemKind.Keyword);
                });
            }
            const entries = shapesFor(block).map(signature => item(
                signature.keyword,
                vscode.CompletionItemKind.Keyword,
                signature.shape.substring(signature.keyword.length).trim(),
                signature.help));
            if (CODE_BLOCKS.includes(block)) {
                for (const name of BUILTINS) {
                    entries.push(item(name, vscode.CompletionItemKind.Variable));
                }
                for (const word of ['else', 'break', 'continue']) {
                    entries.push(item(word, vscode.CompletionItemKind.Keyword));
                }
                // Die Funktionen ohne Empfänger: log und die drei Stufen
                // daneben. Sie standen in keiner Tabelle und wurden deshalb
                // nie vorgeschlagen — man musste wissen, dass es sie gibt.
                for (const fn of table.freeFunctions || []) {
                    entries.push(item(fn.name, vscode.CompletionItemKind.Function,
                        fn.shape, fn.help));
                }
                // In einer Funktion ist ein Ausdruck auch eine Anweisung —
                // und der Aufruf einer Funktion aus der Nachbardatei steht
                // genau hier. Ohne diese Zeile findet ihn niemand: Es gibt
                // kein import, das sie nennt.
                entries.push(...symbolItems(symbols, ['fn'],
                    vscode.CompletionItemKind.Function));
                entries.push(...symbolItems(symbols, ['global'],
                    vscode.CompletionItemKind.Variable));
            }
            return entries;
        }
    }));

    context.subscriptions.push(vscode.languages.registerHoverProvider('manifold', {
        provideHover(document, position) {
            const range = document.getWordRangeAtPosition(position, /[a-zA-Z_][a-zA-Z0-9_]*/);
            if (!range) {
                return null;
            }
            const word = document.getText(range);
            const block = enclosingBlock(document, position.line);
            const signature = shapesFor(block).find(entry => entry.keyword === word);
            if (!signature) {
                return null;
            }
            const markdown = new vscode.MarkdownString();
            markdown.appendCodeblock(signature.shape, 'manifold');
            markdown.appendText(signature.help);
            return new vscode.Hover(markdown, range);
        }
    }));

    context.subscriptions.push(vscode.languages.registerSignatureHelpProvider('manifold', {
        provideSignatureHelp(document, position) {
            const where = whereAt(document, position);
            if (!where) {
                return null;
            }
            const help = new vscode.SignatureHelp();
            const signature = new vscode.SignatureInformation(
                where.signature.shape, where.signature.help);
            // Die Stellen als Parameter, damit VS Code die aktive
            // hervorhebt. Das Schlüsselwort selbst ist keine.
            let offset = where.signature.keyword.length + 1;
            for (const slot of where.signature.slots) {
                signature.parameters.push(new vscode.ParameterInformation(
                    [offset, offset + slot.label.length]));
                offset += slot.label.length + 1;
            }
            help.signatures = [signature];
            help.activeSignature = 0;
            help.activeParameter = Math.min(where.index,
                where.signature.slots.length - 1);
            return help;
        }
    }, ' ', '"'));

    // Beim Speichern die gelesenen Ordner vergessen. Wer eine Funktion
    // anlegt und die Datei speichert, will sie im nächsten Vorschlag sehen
    // und nicht erst, wenn die Frist von selbst abläuft.
    //
    // Alle Ordner und nicht nur der eine: Welcher betroffen ist, wäre
    // auszurechnen, und es sind selten mehr als zwei.
    context.subscriptions.push(
        vscode.workspace.onDidSaveTextDocument(() => {
            folders.clear();
            roots.clear();
        }));
}

function deactivate() {
}

module.exports = { activate, deactivate };
