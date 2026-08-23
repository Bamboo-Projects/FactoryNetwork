// Manifold in VS Code — Vervollständigung, Hinweise und Formanzeige.
//
// Bewusst reines JavaScript und kein TypeScript: Die Erweiterung wird
// kopiert, nicht gebaut. Ein Übersetzungsschritt hieße npm install und tsc,
// und dann kopiert sie niemand mehr.
//
// Was sie weiß, steht in data/signatures.json. Diese Datei wird aus
// Signatures.java erzeugt; ein Test im Mod-Projekt hält beide gleich. Damit
// gibt es die Regel „hinter row kommt ein Text und dann ein Ausdruck"
// weiterhin einmal und nicht zweimal.
//
// Was sie nicht kann: Fehler melden. Dafür bräuchte es den Übersetzer, und
// der ist in Java. Fehler zeigt das Terminal im Spiel.

const vscode = require('vscode');
const fs = require('fs');
const path = require('path');

let table = { blocks: {}, strategies: [], declarations: [] };

/** Zu welchen Blockarten Anweisungen gehören statt fester Angaben. */
const CODE_BLOCKS = ['fn', 'on', 'multiblock'];

/** Was an einer Ausdrucksstelle immer geht. */
const BUILTINS = ['storage', 'crafting', 'world', 'network', 'workers', 'multiblocks'];

function load(context) {
    const file = path.join(context.extensionPath, 'data', 'signatures.json');
    table = JSON.parse(fs.readFileSync(file, 'utf8'));
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

/** Was an dieser Stelle stehen darf. */
function completionsFor(where) {
    const slot = where.slot;
    if (!slot) {
        return [];
    }
    switch (slot.kind) {
        case 'STRATEGY':
            return table.strategies.map(name =>
                item(name, vscode.CompletionItemKind.EnumMember));
        case 'EXPR':
        case 'TARGET':
            return BUILTINS.map(name => item(name, vscode.CompletionItemKind.Variable));
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
            const where = whereAt(document, position);
            if (where) {
                return completionsFor(where);
            }
            const block = enclosingBlock(document, position.line);
            const indented = /^\s/.test(document.lineAt(position.line).text);
            if (!indented) {
                return table.declarations.map(word =>
                    item(word, vscode.CompletionItemKind.Keyword));
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
}

function deactivate() {
}

module.exports = { activate, deactivate };
