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
              listMembers: [], networkMembers: [], entryMembers: [],
              builtinEvents: [], freeFunctions: [], topLevel: [] };

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

/** Was das Spiel zuletzt neben die Dateien geschrieben hat, je Wurzel. */
const status = new Map();

/** So heißt die Datei, die das Spiel schreibt. */
const STATUS_FILE = '.fn-status.json';

/**
 * Was das laufende Spiel über dieses Projekt weiß.
 *
 * Zwei Dinge, die diese Erweiterung allein nicht haben kann: die Fehler, so
 * wie der echte Übersetzer sie sieht, und die Gerätenamen aus der Welt. Sie
 * stehen in keiner Programmdatei — sie kommen aus der Beschriftungspistole.
 *
 * Geliefert werden sie über den Ordner, den es ohnehin gibt. Kein Port, keine
 * Verbindung, nichts einzuschalten: Wer die Dateien sieht, sieht auch das.
 * Im Mehrspielerbetrieb liegt der Ordner beim Server, und dort gibt es das
 * hier nicht — das ist der bekannte Schnitt.
 */
function statusOf(root) {
    const cached = status.get(root);
    if (cached && Date.now() - cached.stamp < FOLDER_MS) {
        return cached.value;
    }
    let value = { diagnostics: {}, connectors: [], displays: [], prefixes: [] };
    try {
        value = JSON.parse(fs.readFileSync(path.join(root, STATUS_FILE), 'utf8'));
    } catch (error) {
        // Kein Spiel, kein Status. Beim Tippen ist eine fehlende Auskunft die
        // bessere Antwort als eine Fehlermeldung.
    }
    status.set(root, { value, stamp: Date.now() });
    return value;
}

/** Die Gerätenamen aus der Welt, für die Datei, an der jemand arbeitet. */
function connectorsFor(document) {
    if (!document.uri || document.uri.scheme !== 'file') {
        return [];
    }
    return statusOf(projectRootOf(document.uri.fsPath)).connectors || [];
}

/**
 * Die Präfixe, die in diesem Pack eine Ressourcenart benennen.
 *
 * Seit dem 26.08. ist die Liste offen: Eine fremde Mod meldet ihre eigene Art
 * an, und was dann gilt, weiß nur das laufende Spiel. Es schickt sie über die
 * Statusdatei mit.
 *
 * Ohne Spiel bleiben die eingebauten. Das ist keine Vollständigkeit, sondern
 * das, was sich ohne Nachfrage sagen lässt — und deshalb steht es auch
 * dabei, wenn jemand den Vorschlag ansieht.
 */
const BUILTIN_PREFIXES = ['chemical', 'fluid', 'fluidtag', 'item', 'tag'];

function prefixesFor(document) {
    if (!document.uri || document.uri.scheme !== 'file') {
        return { names: BUILTIN_PREFIXES, fromGame: false };
    }
    const known = statusOf(projectRootOf(document.uri.fsPath)).prefixes;
    return known && known.length
        ? { names: known.slice().sort(), fromGame: true }
        : { names: BUILTIN_PREFIXES, fromGame: false };
}

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
 * Steht der Cursor hinter dem Punkt eines bestimmten Wortes?
 *
 * <p>Fuer `network.`: Das ist ein Punktzugriff wie an einem Geraet, aber die
 * Mitglieder sind andere. Ohne die Unterscheidung boete die Erweiterung an
 * einem Netz redstone() an.
 *
 * <p>Der Wortanfang wird mitgeprueft, sonst passte `mein_network.` auch.
 */
function afterDotOn(document, position, word) {
    const upToCursor = document.lineAt(position.line).text.substring(0, position.character);
    return new RegExp('(^|[^a-zA-Z0-9_])' + word + '\\.[a-zA-Z0-9_]*$').test(upToCursor);
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

/**
 * Die Namen, die dieser Text vergibt — mit der Stelle, an der sie stehen.
 *
 * Zeile und Spalte zählen ab null, wie in VS Code. Sie kosten hier nichts und
 * sind die Grundlage für Gliederung, Sprung zur Deklaration und Umbenennen:
 * Ohne sie weiß der Index, dass es einen Namen gibt, aber nicht, wo.
 *
 * Die Spalte wird hinter dem Schlüsselwort gesucht und nicht im ganzen
 * Fundstück. `global l = 1` hat ein `l` in `global`, und wer von vorn sucht,
 * springt in das Schlüsselwort statt auf den Namen.
 */
function symbolsIn(text, file) {
    const found = [];
    const lines = text.split('\n');
    for (let number = 0; number < lines.length; number++) {
        const raw = lines[number];
        const line = raw.trim();
        const indent = raw.length - raw.trimStart().length;
        for (const declaration of DECLARED_NAMES) {
            const match = declaration.pattern.exec(line);
            if (match) {
                const at = line.indexOf(match[1], declaration.keyword.length);
                found.push({
                    keyword: declaration.keyword,
                    name: match[1],
                    file,
                    line: number,
                    column: indent + at,
                });
                // Eine Zeile erklärt höchstens einen Namen.
                break;
            }
        }
    }
    return found;
}

/** Die Art, die VS Code in der Gliederung neben einen Namen malt. */
function symbolKind(keyword) {
    switch (keyword) {
        case 'fn':
            return vscode.SymbolKind.Function;
        case 'worker':
            return vscode.SymbolKind.Method;
        case 'event':
            return vscode.SymbolKind.Event;
        case 'global':
            return vscode.SymbolKind.Variable;
        case 'display':
            return vscode.SymbolKind.Interface;
        default:
            return vscode.SymbolKind.Struct;
    }
}

/** Die Stelle eines Symbols als Bereich — der Name, nicht die ganze Zeile. */
function rangeOf(symbol) {
    return new vscode.Range(symbol.line, symbol.column,
        symbol.line, symbol.column + symbol.name.length);
}

/**
 * Die Datei, in der ein Symbol steht.
 *
 * Der Index führt den Namen unter dem Projekt mit Schrägstrichen — so wie das
 * Spiel ihn schreibt. Zurück auf die Platte geht es über den Ordner, aus dem
 * er gelesen wurde.
 */
function fileOf(symbol, root, self) {
    const name = symbol.file || self;
    if (!name) {
        return null;
    }
    return path.join(root, ...name.split('/'));
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

/**
 * Jede Datei des Projekts mit ihrem Text.
 *
 * Nicht zwischengespeichert: Umbenannt wird selten, und ein veralteter Text
 * schriebe hier an die falsche Stelle. Der Zwischenspeicher der
 * Vervollständigung darf altern, dieser Weg nicht.
 */
function textsOf(root) {
    const found = {};
    readTexts(root, '', found, 0);
    return found;
}

function readTexts(folder, prefix, found, depth) {
    if (depth > MAX_DEPTH) {
        return;
    }
    let names = [];
    try {
        names = fs.readdirSync(folder);
    } catch (error) {
        return;
    }
    for (const name of names) {
        const full = path.join(folder, name);
        let isFolder = false;
        try {
            isFolder = fs.statSync(full).isDirectory();
        } catch (error) {
            continue;
        }
        if (isFolder) {
            readTexts(full, prefix + name + '/', found, depth + 1);
            continue;
        }
        if (!name.endsWith('.mf')) {
            continue;
        }
        try {
            found[prefix + name] = fs.readFileSync(full, 'utf8');
        } catch (error) {
            // Zwischen Auflisten und Lesen kann die Datei weg sein.
        }
    }
}

/**
 * Jede Stelle, an der dieses Wort <b>als ganzes Wort</b> steht.
 *
 * Als ganzes Wort, weil ein Name in einem längeren steckt: Wer `kiste` in
 * `kiste_1` mit umbenennt, hat ein Programm zerschrieben, das vorher lief.
 */
function occurrences(text, word) {
    const found = [];
    const lines = text.split('\n');
    for (let number = 0; number < lines.length; number++) {
        const line = lines[number];
        let at = line.indexOf(word);
        while (at >= 0) {
            const before = at === 0 ? '' : line[at - 1];
            const after = line[at + word.length] || '';
            if (!/[A-Za-z0-9_]/.test(before) && !/[A-Za-z0-9_]/.test(after)) {
                found.push({ line: number, column: at });
            }
            at = line.indexOf(word, at + 1);
        }
    }
    return found;
}

/**
 * Das Wort unter dem Zeiger, oder nichts.
 *
 * Ohne Datei gibt es kein Projekt und damit keinen Sprung: Eine Datei, die nie
 * gespeichert wurde, liegt in keinem Ordner.
 */
function wordAt(document, position) {
    if (!document.uri || document.uri.scheme !== 'file') {
        return null;
    }
    const range = document.getWordRangeAtPosition(position);
    return range ? document.getText(range) : null;
}

/** Erklärt dieses Projekt diesen Namen irgendwo? */
function declaredIn(document, word) {
    return projectSymbols(document).some(symbol => symbol.name === word);
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

function completionsFor(where, symbols, devices, prefixes) {
    const slot = where.slot;
    if (!slot) {
        return [];
    }
    switch (slot.kind) {
        case 'SELECTION':
            // Gegenstände und Tags kennt nur das laufende Spiel. Was die
            // Erweiterung beisteuern kann, sind die Filter-Vorlagen des
            // Projekts — und die sind an dieser Stelle oft das Gemeinte.
            //
            // Dazu die Präfixe: Sie sind das erste, was hier hingehört, und
            // seit die Ressourcenarten offen sind, weiß nur das Spiel, welche
            // es gibt. Ohne Spiel stehen die eingebauten da, und der Zusatz
            // sagt, dass es nicht die ganze Liste sein muss.
            return prefixes.names.map(name => item(name + ':',
                vscode.CompletionItemKind.Keyword,
                prefixes.fromGame ? 'Ressourcenart' : 'Ressourcenart (ohne Spiel)'))
                .concat(symbolItems(symbols, ['filter'],
                    vscode.CompletionItemKind.Variable));
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
                // Die Geräte aus der Welt: Sie stehen in keiner Datei, und
                // ohne sie schlägt die Erweiterung an genau der Stelle nichts
                // vor, an der man am ehesten etwas braucht.
                .concat(devices.map(name => item(name,
                    vscode.CompletionItemKind.Variable, 'Gerät im Netz')))
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
            // Vor den Geraetemitgliedern: network ist auch ein Punktzugriff,
            // aber kein Geraet. Ohne diese Zeile bot die Erweiterung an einem
            // Netz redstone() an.
            // „it" ist ein Posten und kein Gerät: Daran stehen die Menge und
            // die Sorte, nicht redstone(). signatures.json trug die Liste
            // schon; gezeigt hat sie hier nie jemand.
            if (afterDotOn(document, position, 'it')) {
                return table.entryMembers.map(member => item(
                    member.name, vscode.CompletionItemKind.Property,
                    member.shape, member.help));
            }
            if (afterDotOn(document, position, 'network')) {
                return table.networkMembers.map(member => item(
                    member.name, vscode.CompletionItemKind.Property,
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
                return completionsFor(where, symbols, connectorsFor(document),
                    prefixesFor(document));
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
    // Die Fehler aus dem Spiel. Sie kommen ueber die Statusdatei, die der
    // Controller neben die Programme schreibt — kein Port, keine Verbindung,
    // nichts einzuschalten.
    /**
     * Die Gliederung: was diese Datei erklärt.
     *
     * Nur die eigene Datei — die Gliederung gehört zum Editorfenster, und
     * darin steht eine. Was die Nachbardateien erklären, findet der Sprung
     * zur Deklaration.
     */
    context.subscriptions.push(vscode.languages.registerDocumentSymbolProvider('manifold', {
        provideDocumentSymbols(document) {
            return symbolsIn(document.getText(), null).map(symbol => {
                const range = rangeOf(symbol);
                return new vscode.DocumentSymbol(symbol.name, symbol.keyword,
                    symbolKind(symbol.keyword), range, range);
            });
        },
    }));

    /**
     * Der Sprung zur Deklaration.
     *
     * Über das ganze Projekt und nicht nur über die Datei: Der Namensraum ist
     * einer, und ein `fn` aus `erz/brecher.mf` wird von `main.mf` gerufen.
     *
     * Mehrere Treffer werden alle zurückgegeben. Zwei Dateien dürfen denselben
     * Namen nicht zweimal vergeben — dass sie es doch tun, meldet der
     * Übersetzer, und bis dahin ist eine Auswahl ehrlicher als ein geratener
     * Treffer.
     */
    context.subscriptions.push(vscode.languages.registerDefinitionProvider('manifold', {
        provideDefinition(document, position) {
            const wanted = wordAt(document, position);
            if (!wanted) {
                return null;
            }
            const found = [];
            for (const symbol of projectSymbols(document)) {
                if (symbol.name !== wanted || symbol.line === undefined) {
                    continue;
                }
                const file = fileOf(symbol, projectRootOf(document.uri.fsPath),
                    nameUnder(projectRootOf(document.uri.fsPath), document.uri.fsPath));
                if (file) {
                    found.push(new vscode.Location(vscode.Uri.file(file), rangeOf(symbol)));
                }
            }
            return found;
        },
    }));

    /**
     * Umbenennen — über das ganze Projekt.
     *
     * <b>Warum nicht nur in dieser Datei:</b> Der Namensraum ist einer. Ein
     * `fn`, das in drei Dateien gerufen wird, hieße nach einer Umbenennung in
     * nur einer Datei an zwei Stellen anders — und das Programm liefe nicht
     * mehr.
     *
     * <b>Nur erklärte Namen.</b> Über einem Schlüsselwort oder einem
     * Gerätenamen sagt {@code prepareRename} nein: Gerätenamen stehen am
     * Block in der Welt, nicht in einer Datei, und ein Schlüsselwort
     * umzubenennen hieße, die Sprache umzubenennen.
     */
    context.subscriptions.push(vscode.languages.registerRenameProvider('manifold', {
        prepareRename(document, position) {
            const range = document.getWordRangeAtPosition(position);
            const word = range ? document.getText(range) : null;
            if (!word || !declaredIn(document, word)) {
                throw new Error('Nur erklärte Namen lassen sich umbenennen — '
                    + 'Gerätenamen stehen am Block in der Welt.');
            }
            return range;
        },
        provideRenameEdits(document, position, newName) {
            const wanted = wordAt(document, position);
            if (!wanted || !declaredIn(document, wanted)) {
                return null;
            }
            if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(newName)) {
                throw new Error('„' + newName + '" ist kein Name: Buchstaben, '
                    + 'Ziffern und Unterstrich, und keine Ziffer am Anfang.');
            }
            const root = projectRootOf(document.uri.fsPath);
            const texts = textsOf(root);
            const change = new vscode.WorkspaceEdit();
            for (const name of Object.keys(texts)) {
                const file = vscode.Uri.file(path.join(root, ...name.split('/')));
                for (const at of occurrences(texts[name], wanted)) {
                    change.replace(file, new vscode.Range(at.line, at.column,
                        at.line, at.column + wanted.length), newName);
                }
            }
            return change;
        },
    }));

    /**
     * Schnellkorrekturen — was das Spiel vorschlägt, mit einem Klick.
     *
     * <b>Nicht selbst geraten.</b> Der Vorschlag kommt aus der Statusdatei und
     * damit aus dem Übersetzer, der als Einziger weiß, was gemeint war. Hier
     * steht nur, wie man ihn anwendet.
     */
    context.subscriptions.push(vscode.languages.registerCodeActionsProvider('manifold', {
        provideCodeActions(document, range, actionContext) {
            const actions = [];
            for (const problem of actionContext.diagnostics || []) {
                if (!problem.fix) {
                    continue;
                }
                const action = new vscode.CodeAction(
                    'Ersetzen durch „' + problem.fix.text + '"',
                    vscode.CodeActionKind.QuickFix);
                action.edit = new vscode.WorkspaceEdit();
                action.edit.replace(document.uri,
                    new vscode.Range(problem.fix.line, problem.fix.column,
                        problem.fix.line, problem.fix.column + problem.fix.length),
                    problem.fix.text);
                action.diagnostics = [problem];
                action.isPreferred = true;
                actions.push(action);
            }
            return actions;
        },
    }, { providedCodeActionKinds: [vscode.CodeActionKind.QuickFix] }));

    const problems = vscode.languages.createDiagnosticCollection('manifold');
    context.subscriptions.push(problems);

    const refreshAll = () => {
        status.clear();
        for (const open of vscode.workspace.textDocuments) {
            refreshDiagnostics(problems, open);
        }
    };

    context.subscriptions.push(
        vscode.workspace.onDidSaveTextDocument(() => {
            folders.clear();
            roots.clear();
            status.clear();
        }));
    if (vscode.workspace.onDidOpenTextDocument) {
        context.subscriptions.push(vscode.workspace.onDidOpenTextDocument(
            open => refreshDiagnostics(problems, open)));
    }
    // Nachsehen im Sekundentakt, nicht ueberwachen: Ein Dateiwaechter braeuchte
    // eine Entprellung gegen die Doppelereignisse und ein verlaessliches
    // Aufraeumen. Dieselbe Ueberlegung wie auf der Spielseite, und dieselbe
    // Frist.
    if (typeof setInterval === 'function') {
        const timer = setInterval(refreshAll, 1000);
        // Ein laufender Takt haelt einen Node-Prozess am Leben. In VS Code
        // faellt das nicht auf, im Pruefskript schon: Es kaeme nie zum Ende.
        if (timer && typeof timer.unref === 'function') {
            timer.unref();
        }
        context.subscriptions.push({ dispose: () => clearInterval(timer) });
    }
    refreshAll();
}

/**
 * Traegt die Fehler des Spiels in den Editor ein.
 *
 * Nicht selbst gerechnet: Es gibt genau einen Uebersetzer fuer Manifold, und
 * der laeuft im Spiel. Eine zweite Fassung derselben Regeln in JavaScript
 * waere ein zweiter Ort, an dem sie auseinanderlaufen — dieselbe Ueberlegung
 * wie bei der Formtabelle, die aus Signatures.java erzeugt wird.
 *
 * Was hier steht, ist Uebersetzung im Wortsinn: Zeile und Spalte zaehlen im
 * Spiel ab eins, in VS Code ab null.
 */
function refreshDiagnostics(collection, document) {
    if (!document || document.languageId !== 'manifold'
            || !document.uri || document.uri.scheme !== 'file') {
        return;
    }
    const root = projectRootOf(document.uri.fsPath);
    const name = nameUnder(root, document.uri.fsPath);
    const found = (statusOf(root).diagnostics || {})[name] || [];
    collection.set(document.uri, found.map(problem => {
        const line = Math.max(0, (problem.line || 1) - 1);
        const from = Math.max(0, (problem.column || 1) - 1);
        const range = new vscode.Range(line, from, line,
            from + Math.max(1, problem.length || 1));
        const entry = new vscode.Diagnostic(range,
            problem.hint ? problem.message + ' ' + problem.hint : problem.message,
            problem.severity === 'warning'
                ? vscode.DiagnosticSeverity.Warning
                : vscode.DiagnosticSeverity.Error);
        entry.source = 'Factory Network';
        // Der anwendbare Vorschlag reist an der Meldung mit: Die
        // Schnellkorrektur bekommt von VS Code genau diese Meldungen und
        // sonst nichts.
        if (problem.fixText) {
            entry.fix = {
                text: problem.fixText,
                line: Math.max(0, (problem.fixLine || 1) - 1),
                column: Math.max(0, (problem.fixColumn || 1) - 1),
                length: Math.max(1, problem.fixLength || 1),
            };
        }
        return entry;
    }));
}

function deactivate() {
}

module.exports = { activate, deactivate };
