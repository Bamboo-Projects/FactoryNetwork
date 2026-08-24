package dev.devpanda.factorynetwork.lang.parse;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Lexer;
import dev.devpanda.factorynetwork.lang.Span;
import dev.devpanda.factorynetwork.lang.Token;
import dev.devpanda.factorynetwork.lang.TokenType;
import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.ast.Stmt;

import java.util.ArrayList;
import java.util.List;

/**
 * Liest Token und baut daraus den Syntaxbaum.
 *
 * <p>Von Hand geschrieben, nicht erzeugt. Der Grund steht in
 * {@code docs/entscheidungen.md}: Ein Generator weiß an der Fehlerstelle nur,
 * welche Token dort erlaubt gewesen wären. Hier lässt sich stattdessen sagen,
 * was der Spieler vermutlich gemeint hat.
 *
 * <p>Nach einem Fehler wird nicht abgebrochen, sondern bis zur nächsten
 * Anweisung oder Deklaration weitergelesen. Der Editor braucht einen Baum auch
 * für Code, der noch nicht fertig ist — sonst kann er nicht vervollständigen.
 */
public final class Parser {

    private final List<Token> tokens;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private int index;
    /**
     * Steht der Parser gerade in einer Bedingung? Nur dort ist ein einzelnes
     * Gleichheitszeichen ein Tippfehler — auf Anweisungsebene ist es eine
     * Zuweisung, und die ist der häufigere Fall.
     */
    private int conditionDepth;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public record ParseResult(Program program, List<Diagnostic> diagnostics) {
        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(Diagnostic::isError);
        }
    }

    public static ParseResult parse(String source) {
        Lexer.LexResult lexed = Lexer.tokenize(source);
        Parser parser = new Parser(lexed.tokens());
        parser.diagnostics.addAll(lexed.diagnostics());
        Program program = parser.parseProgram();
        return new ParseResult(program, List.copyOf(parser.diagnostics));
    }

    // ---- Programm ---------------------------------------------------------

    private Program parseProgram() {
        List<Decl> declarations = new ArrayList<>();
        skipNewlines();
        while (!at(TokenType.EOF)) {
            Decl declaration = parseDeclaration();
            if (declaration != null) {
                declarations.add(declaration);
            }
            skipNewlines();
        }
        return new Program(List.copyOf(declarations));
    }

    private Decl parseDeclaration() {
        Token start = peek();
        return switch (start.type()) {
            case WORKER -> parseWorker();
            case GROUP -> parseGroup();
            case MULTIBLOCK -> parseMultiblock();
            case EVENT -> parseEvent();
            case DISPLAY -> parseDisplay();
            case FN -> parseFn();
            case ON -> parseOn();
            case GLOBAL -> parseGlobal();
            case IMPORT -> {
                error(start.span(), "Module gibt es noch nicht.",
                        "import ist für später reserviert. Alle .mf-Dateien eines Projekts "
                                + "teilen ohnehin einen Namensraum.");
                recoverToDeclaration();
                yield null;
            }
            default -> {
                error(start.span(),
                        "Hier wird eine Deklaration erwartet, gefunden wurde " + describe(start) + ".",
                        "Auf oberster Ebene stehen worker, group, multiblock, event, display, "
                                + "fn, on und global. Anweisungen gehören in eine Funktion.");
                recoverToDeclaration();
                yield null;
            }
        };
    }

    // ---- Worker -----------------------------------------------------------

    private Decl parseWorker() {
        Token keyword = advance();
        String name = expectName("Worker");
        if (!expect(TokenType.LBRACE, "Nach dem Namen des Workers fehlt die geschweifte Klammer.")) {
            recoverToDeclaration();
            return new Decl.Invalid(name, keyword.span());
        }
        List<Decl.Worker.Entry> entries = new ArrayList<>();
        skipNewlines();
        while (!at(TokenType.RBRACE) && !at(TokenType.EOF)) {
            Decl.Worker.Entry entry = parseWorkerEntry();
            if (entry != null) {
                entries.add(entry);
            }
            skipNewlines();
        }
        Token end = expectBrace(keyword, "worker");
        return new Decl.Worker(name, List.copyOf(entries), keyword.span().to(end.span()));
    }

    private Decl.Worker.Entry parseWorkerEntry() {
        Token start = peek();
        switch (start.type()) {
            case FROM -> {
                advance();
                return entry(Decl.Worker.Entry.Kind.FROM, parseTarget(), start);
            }
            case TO -> {
                advance();
                return entry(Decl.Worker.Entry.Kind.TO, parseTarget(), start);
            }
            case FILTER -> {
                advance();
                return entry(Decl.Worker.Entry.Kind.FILTER, parseExpression(), start);
            }
            case MAINTAIN -> {
                advance();
                return entry(Decl.Worker.Entry.Kind.MAINTAIN, parseExpression(), start);
            }
            case WHEN -> {
                advance();
                return entry(Decl.Worker.Entry.Kind.WHEN, parseExpression(), start);
            }
            case PRIORITY -> {
                advance();
                return entry(Decl.Worker.Entry.Kind.PRIORITY, parseExpression(), start);
            }
            case STRATEGY -> {
                advance();
                return entry(Decl.Worker.Entry.Kind.STRATEGY, parseExpression(), start);
            }
            case RATE -> {
                advance();
                Expr count = parseExpression();
                if (!expect(TokenType.PER, "Nach der Menge fehlt per.")) {
                    return null;
                }
                Expr interval = parseExpression();
                return new Decl.Worker.Entry(Decl.Worker.Entry.Kind.RATE, count, interval,
                        start.span().to(interval.span()));
            }
            case OVERFLOW -> {
                advance();
                expect(TokenType.TO, "Nach overflow fehlt to.");
                return entry(Decl.Worker.Entry.Kind.OVERFLOW, parseTarget(), start);
            }
            default -> {
                error(start.span(),
                        describe(start) + " ist keine Angabe, die ein Worker kennt.",
                        "Erlaubt sind from, to, filter, maintain, rate, when, priority, "
                                + "strategy und overflow.");
                recoverToLineEnd();
                return null;
            }
        }
    }

    private Decl.Worker.Entry entry(Decl.Worker.Entry.Kind kind, Expr value, Token start) {
        return new Decl.Worker.Entry(kind, value, null, start.span().to(value.span()));
    }

    /** Ein Ziel ist ein Name oder eines der eingebauten Geräte. */
    private Expr parseTarget() {
        Token token = peek();
        return switch (token.type()) {
            case STORAGE, CRAFTING, WORLD, NETWORK, WORKERS, MULTIBLOCKS -> parsePrimary();
            case NAME, ESCAPED_NAME -> {
                advance();
                yield new Expr.Name(token.text(), token.span());
            }
            default -> {
                error(token.span(),
                        "Hier wird ein Gerät erwartet, gefunden wurde " + describe(token) + ".",
                        "Ein Ziel ist ein Connector, eine Gruppe, storage oder crafting.");
                advance();
                yield new Expr.Invalid(token.span());
            }
        };
    }

    // ---- Gruppe, Multiblock, Ereignis -------------------------------------

    private Decl parseGroup() {
        Token keyword = advance();
        String name = expectName("Gruppe");
        if (!expect(TokenType.LBRACE, "Nach dem Namen der Gruppe fehlt die geschweifte Klammer.")) {
            recoverToDeclaration();
            return new Decl.Invalid(name, keyword.span());
        }
        List<Expr> members = new ArrayList<>();
        String strategy = null;
        skipNewlines();
        while (!at(TokenType.RBRACE) && !at(TokenType.EOF)) {
            Token start = peek();
            if (start.is(TokenType.MEMBERS)) {
                advance();
                members.addAll(parseMemberList());
            } else if (start.is(TokenType.STRATEGY)) {
                advance();
                Token value = peek();
                if (value.is(TokenType.NAME)) {
                    advance();
                    strategy = value.text();
                } else {
                    error(value.span(), "Nach strategy fehlt der Name einer Verteilung.",
                            "Erlaubt sind round_robin, first_available, least_filled, "
                                    + "random und priority.");
                    recoverToLineEnd();
                }
            } else {
                error(start.span(),
                        describe(start) + " ist keine Angabe, die eine Gruppe kennt.",
                        "Erlaubt sind members und strategy.");
                recoverToLineEnd();
            }
            skipNewlines();
        }
        Token end = expectBrace(keyword, "group");
        return new Decl.Group(name, List.copyOf(members), strategy,
                keyword.span().to(end.span()));
    }

    private List<Expr> parseMemberList() {
        List<Expr> members = new ArrayList<>();
        do {
            Token token = peek();
            if (token.is(TokenType.NAME) || token.is(TokenType.ESCAPED_NAME)) {
                advance();
                members.add(new Expr.Name(token.text(), token.span()));
            } else if (token.is(TokenType.NAME_PATTERN)) {
                advance();
                members.add(new Expr.NamePattern(token.text(), token.span()));
            } else {
                error(token.span(),
                        "Hier wird ein Connector erwartet, gefunden wurde " + describe(token) + ".",
                        "Erlaubt sind Namen und Muster wie furnace_*.");
                recoverToLineEnd();
                break;
            }
        } while (match(TokenType.COMMA));
        return members;
    }

    private Decl parseMultiblock() {
        Token keyword = advance();
        String name = expectName("Multiblock");
        if (!expect(TokenType.LBRACE, "Nach dem Namen fehlt die geschweifte Klammer.")) {
            recoverToDeclaration();
            return new Decl.Invalid(name, keyword.span());
        }
        List<String> devices = new ArrayList<>();
        List<Decl.Fn> functions = new ArrayList<>();
        skipNewlines();
        while (!at(TokenType.RBRACE) && !at(TokenType.EOF)) {
            Token start = peek();
            if (start.is(TokenType.DEVICES)) {
                advance();
                devices.addAll(parseDeviceBlock());
            } else if (start.is(TokenType.FN)) {
                Decl parsed = parseFn();
                if (parsed instanceof Decl.Fn fn) {
                    functions.add(fn);
                }
            } else {
                error(start.span(),
                        describe(start) + " gehört nicht in einen Multiblock.",
                        "Ein Multiblock enthält devices und Funktionen.");
                recoverToLineEnd();
            }
            skipNewlines();
        }
        Token end = expectBrace(keyword, "multiblock");
        return new Decl.Multiblock(name, List.copyOf(devices), List.copyOf(functions),
                keyword.span().to(end.span()));
    }

    private List<String> parseDeviceBlock() {
        List<String> devices = new ArrayList<>();
        if (!expect(TokenType.LBRACE, "Nach devices fehlt die geschweifte Klammer.")) {
            return devices;
        }
        skipNewlines();
        while (!at(TokenType.RBRACE) && !at(TokenType.EOF)) {
            Token token = peek();
            if (token.is(TokenType.NAME) || token.is(TokenType.ESCAPED_NAME)) {
                advance();
                devices.add(token.text());
            } else {
                error(token.span(),
                        "Hier wird ein Gerätename erwartet, gefunden wurde " + describe(token) + ".");
                recoverToLineEnd();
            }
            skipNewlines();
        }
        expect(TokenType.RBRACE, "Der devices-Block wird nicht geschlossen.");
        return devices;
    }

    private Decl parseEvent() {
        Token keyword = advance();
        String name = expectName("Ereignis");
        List<Decl.Param> parameters = parseParamList(true);
        return new Decl.Event(name, parameters, keyword.span().to(previous().span()));
    }

    private Decl parseDisplay() {
        Token keyword = advance();
        String name = expectName("Display");
        if (!expect(TokenType.LBRACE, "Nach dem Namen fehlt die geschweifte Klammer.")) {
            recoverToDeclaration();
            return new Decl.Invalid(name, keyword.span());
        }
        List<Decl.Display.Entry> entries = new ArrayList<>();
        skipNewlines();
        while (!at(TokenType.RBRACE) && !at(TokenType.EOF)) {
            Decl.Display.Entry entry = parseDisplayEntry();
            if (entry != null) {
                entries.add(entry);
            }
            skipNewlines();
        }
        Token end = expectBrace(keyword, "display");
        return new Decl.Display(name, List.copyOf(entries), keyword.span().to(end.span()));
    }

    private Decl.Display.Entry parseDisplayEntry() {
        Token start = peek();
        Decl.Display.Entry.Kind kind = switch (start.type()) {
            case TITLE -> Decl.Display.Entry.Kind.TITLE;
            case ROW -> Decl.Display.Entry.Kind.ROW;
            case TEXT -> Decl.Display.Entry.Kind.TEXT;
            case PROGRESS -> Decl.Display.Entry.Kind.PROGRESS;
            case INDICATOR -> Decl.Display.Entry.Kind.INDICATOR;
            case LIST -> Decl.Display.Entry.Kind.LIST;
            case BUTTON -> Decl.Display.Entry.Kind.BUTTON;
            default -> null;
        };
        if (kind == null) {
            error(start.span(), describe(start) + " ist kein Baustein für ein Display.",
                    "Erlaubt sind title, row, text, progress, indicator, list und button.");
            recoverToLineEnd();
            return null;
        }
        advance();

        // title und text tragen nur einen Wert, alles andere Beschriftung und Wert.
        if (kind == Decl.Display.Entry.Kind.TITLE) {
            Token label = peek();
            if (!label.is(TokenType.STRING)) {
                error(label.span(), "Nach title fehlt die Überschrift in Anführungszeichen.");
                recoverToLineEnd();
                return null;
            }
            advance();
            return new Decl.Display.Entry(kind, label.text(), null,
                    start.span().to(label.span()));
        }
        if (kind == Decl.Display.Entry.Kind.TEXT) {
            Expr value = parseExpression();
            return new Decl.Display.Entry(kind, null, value, start.span().to(value.span()));
        }
        Token label = peek();
        if (!label.is(TokenType.STRING)) {
            error(label.span(),
                    "Nach " + start.text() + " fehlt die Beschriftung in Anführungszeichen.",
                    "Zum Beispiel: " + start.text() + " \"Eisen\" storage.count(item:iron_ingot)");
            recoverToLineEnd();
            return null;
        }
        advance();
        Expr value = parseExpression();
        return new Decl.Display.Entry(kind, label.text(), value, start.span().to(value.span()));
    }

    /**
     * {@code global modus = "tag"}
     *
     * <p>Die einzige Deklaration ohne {@code parseBlock}: Die Zeile endet mit
     * dem Wert. Ob der ein Literal ist, prüft nicht der Parser, sondern
     * {@code GlobalCheck} — hier steht die Form, dort die Regel.
     */
    private Decl parseGlobal() {
        Token keyword = advance();
        String name = expectName("globalen Wert");
        boolean hasEquals = expect(TokenType.EQ,
                "Nach " + name + " fehlt das Gleichheitszeichen.");

        // <b>Der Wert muss auf derselben Zeile stehen.</b>
        //
        // Nach einem Gleichheitszeichen erzeugt der Lexer absichtlich kein
        // Zeilenende (siehe {@code Lexer.breaksStatement}): Ein Ausdruck muss
        // folgen, und er darf umbrechen. Bei einem Block stimmt das; hier
        // verschlingt es die nächste Deklaration. Aus „global kaputt ="
        // gefolgt von „worker erz {" würde sonst ein globaler Wert namens
        // „worker", und der Worker wäre weg — samt allem, was in ihm steht.
        //
        // Geprüft wird deshalb die Zeile und nicht das Zeilenende.
        if (!hasEquals || peek().span().line() != previous().span().line()) {
            error(peek().span(), "Nach " + name + " fehlt der Wert.",
                    "Ein globaler Wert bekommt seinen Typ aus dem, was hier steht — "
                            + "etwa \"tag\" oder 0. Er muss in derselben Zeile stehen.");
            recoverToDeclaration();
            return new Decl.Invalid(name, keyword.span().to(peek().span()));
        }

        Expr value = parseExpression();
        return new Decl.Global(name, value, keyword.span().to(value.span()));
    }

    private Decl parseFn() {
        Token keyword = advance();
        String name = expectName("Funktion");
        List<Decl.Param> parameters = parseParamList(true);
        Block body = parseBlock();
        return new Decl.Fn(name, parameters, body, keyword.span().to(body.span()));
    }

    private Decl parseOn() {
        Token keyword = advance();
        String name = expectName("Ereignis");
        List<Decl.Param> parameters = parseParamList(false);
        Block body = parseBlock();
        List<String> names = parameters.stream().map(Decl.Param::name).toList();
        return new Decl.On(name, names, body, keyword.span().to(body.span()));
    }

    /**
     * Parameterliste. Bei {@code fn} und {@code event} mit Typangabe, bei
     * {@code on} ohne — dort sind die Typen durch die Ereignisdeklaration
     * bekannt.
     */
    private List<Decl.Param> parseParamList(boolean typed) {
        List<Decl.Param> parameters = new ArrayList<>();
        if (!expect(TokenType.LPAREN, "Hier fehlt die runde Klammer.")) {
            return parameters;
        }
        if (match(TokenType.RPAREN)) {
            return parameters;
        }
        do {
            Token name = peek();
            if (!name.is(TokenType.NAME) && !name.is(TokenType.ESCAPED_NAME)) {
                error(name.span(),
                        "Hier wird ein Name erwartet, gefunden wurde " + describe(name) + ".");
                recoverToClosingParen();
                return parameters;
            }
            advance();
            String type = null;
            if (typed) {
                if (expect(TokenType.COLON, "Nach dem Parameter " + name.text()
                        + " fehlt der Typ.", "Zum Beispiel: " + name.text() + ": Item")) {
                    Token typeToken = peek();
                    if (typeToken.is(TokenType.NAME)) {
                        advance();
                        type = typeToken.text();
                    } else {
                        error(typeToken.span(), "Hier wird ein Typ erwartet.");
                    }
                }
            } else if (at(TokenType.COLON)) {
                Token colon = advance();
                if (at(TokenType.NAME)) {
                    advance();
                }
                error(colon.span(), "Bei on werden keine Typen angegeben.",
                        "Sie stehen schon in der Deklaration des Ereignisses.");
            }
            parameters.add(new Decl.Param(name.text(), type, name.span()));
        } while (match(TokenType.COMMA));
        expect(TokenType.RPAREN, "Die Klammer wird nicht geschlossen.");
        return List.copyOf(parameters);
    }

    // ---- Anweisungen ------------------------------------------------------

    private Block parseBlock() {
        Token open = peek();
        if (!expect(TokenType.LBRACE, "Hier fehlt die geschweifte Klammer.")) {
            return new Block(List.of(), open.span());
        }
        List<Stmt> statements = new ArrayList<>();
        skipNewlines();
        while (!at(TokenType.RBRACE) && !at(TokenType.EOF)) {
            Stmt statement = parseStatement();
            if (statement != null) {
                statements.add(statement);
            }
            skipNewlines();
        }
        Token close = expectBrace(open, "Block");
        return new Block(List.copyOf(statements), open.span().to(close.span()));
    }

    private Stmt parseStatement() {
        Token start = peek();

        // Ein Schlüsselwort mit einem Punkt oder einer Klammer dahinter kann
        // nur ein Connector sein, den jemand ohne Rückstriche geschrieben hat.
        // Das muss hier stehen und nicht erst bei den Ausdrücken, weil sonst
        // "for.insert(…)" als Schleife gelesen wird und die Meldung von der
        // fehlenden Schleifenvariable spricht.
        if (isRealKeyword(start)
                && (peekAt(1).is(TokenType.DOT) || peekAt(1).is(TokenType.LPAREN))) {
            error(start.span(), quote(start.text()) + " ist ein Schlüsselwort.",
                    "Meinst du den Connector gleichen Namens? Dann schreibe ihn in "
                            + "Rückstriche: `" + start.text() + "`");
            recoverToLineEnd();
            return new Stmt.Invalid(start.span());
        }

        switch (start.type()) {
            case LET -> {
                advance();
                String name = expectName("Variable");
                expect(TokenType.EQ, "Nach " + name + " fehlt das Gleichheitszeichen.");
                Expr value = parseExpression();
                return new Stmt.Let(name, value, start.span().to(value.span()));
            }
            case IF -> {
                return parseIf();
            }
            case FOR -> {
                advance();
                String variable = expectName("Schleifenvariable");
                expect(TokenType.IN, "Nach " + variable + " fehlt in.");
                Expr iterable = parseExpression();
                Block body = parseBlock();
                return new Stmt.For(variable, iterable, body, start.span().to(body.span()));
            }
            case WHILE -> {
                advance();
                Expr condition = parseCondition();
                Block body = parseBlock();
                return new Stmt.While(condition, body, start.span().to(body.span()));
            }
            case RETURN -> {
                advance();
                if (at(TokenType.NL) || at(TokenType.RBRACE)) {
                    return new Stmt.Return(null, start.span());
                }
                Expr value = parseExpression();
                return new Stmt.Return(value, start.span().to(value.span()));
            }
            case BREAK -> {
                advance();
                return new Stmt.Break(start.span());
            }
            case CONTINUE -> {
                advance();
                return new Stmt.Continue(start.span());
            }
            case MOVE -> {
                return parseMove();
            }
            case EMIT -> {
                advance();
                String name = expectName("Ereignis");
                List<Expr.Argument> arguments = parseArguments();
                return new Stmt.Emit(name, arguments, start.span().to(previous().span()));
            }
            case SLEEP -> {
                advance();
                Expr duration = parseExpression();
                return new Stmt.Sleep(duration, start.span().to(duration.span()));
            }
            default -> {
                Expr expr = parseExpression();
                if (at(TokenType.EQ)) {
                    advance();
                    Expr value = parseExpression();
                    return new Stmt.Assign(expr, value, expr.span().to(value.span()));
                }
                warnIfWithoutEffect(expr);
                return new Stmt.ExprStmt(expr, expr.span());
            }
        }
    }

    private Stmt parseIf() {
        Token keyword = advance();
        Expr condition = parseCondition();
        Block thenBody = parseBlock();
        Object elseBody = null;
        if (at(TokenType.ELSE)) {
            advance();
            elseBody = at(TokenType.IF) ? parseIf() : parseBlock();
        }
        Span end = elseBody instanceof Block block ? block.span()
                : elseBody instanceof Stmt.If nested ? nested.span() : thenBody.span();
        return new Stmt.If(condition, thenBody, elseBody, keyword.span().to(end));
    }

    private Stmt parseMove() {
        Token keyword = advance();
        Expr amount = parseAmount();
        Expr from = null;
        if (match(TokenType.FROM)) {
            from = parseTarget();
        }
        if (!expect(TokenType.TO, "Bei move fehlt das Ziel.",
                "Zum Beispiel: move 64 item:iron_ore from chest to crusher_1")) {
            return new Stmt.Invalid(keyword.span());
        }
        Expr to = parseTarget();
        return new Stmt.Move(amount, from, to, keyword.span().to(to.span()));
    }

    /** Eine Auswahl mit wahlweise vorangestellter Menge. */
    private Expr parseAmount() {
        Token start = peek();
        Long count = null;
        if (start.is(TokenType.INT)) {
            advance();
            count = Long.parseLong(start.text());
        }
        Expr selection = parseExpression();
        if (count == null) {
            return selection;
        }
        return new Expr.Amount(count, selection, start.span().to(selection.span()));
    }

    // ---- Ausdrücke --------------------------------------------------------

    /** Ein Ausdruck in Bedingungsstellung — dort ist ein einzelnes = ein Fehler. */
    private Expr parseCondition() {
        conditionDepth++;
        try {
            return parseExpression();
        } finally {
            conditionDepth--;
        }
    }

    private Expr parseExpression() {
        if (at(TokenType.AWAIT)) {
            return parseAwait();
        }
        return parseOr();
    }

    private Expr parseAwait() {
        Token keyword = advance();
        String eventName = expectName("Ereignis");
        Expr where = null;
        if (match(TokenType.WHERE)) {
            where = parseOr();
        }
        Expr timeout = null;
        Block elseBody = null;
        if (at(TokenType.TIMEOUT)) {
            Token timeoutToken = advance();
            timeout = parseOr();
            if (at(TokenType.ELSE)) {
                advance();
                elseBody = parseBlock();
            } else {
                error(timeoutToken.span().to(timeout.span()),
                        "Nach timeout fehlt der else-Zweig.",
                        "Ohne ihn stünde nach Ablauf ein Wert da, den es nie gab. "
                                + "Schreibe: timeout " + timeout(timeout) + " else { return }");
            }
        }
        return new Expr.Await(eventName, where, timeout, elseBody,
                keyword.span().to(previous().span()));
    }

    private static String timeout(Expr expr) {
        return expr instanceof Expr.DurationLit duration ? duration.written() : "30s";
    }

    private Expr parseOr() {
        Expr left = parseAnd();
        while (at(TokenType.OR_OR)) {
            advance();
            Expr right = parseAnd();
            left = new Expr.Binary(Expr.Binary.Op.OR, left, right, left.span().to(right.span()));
        }
        return left;
    }

    private Expr parseAnd() {
        Expr left = parseComparison();
        while (at(TokenType.AND_AND)) {
            advance();
            Expr right = parseComparison();
            left = new Expr.Binary(Expr.Binary.Op.AND, left, right, left.span().to(right.span()));
        }
        return left;
    }

    private Expr parseComparison() {
        Expr left = parseAdditive();
        Expr.Binary.Op op = switch (peek().type()) {
            case EQ_EQ -> Expr.Binary.Op.EQ;
            case BANG_EQ -> Expr.Binary.Op.NEQ;
            case LT -> Expr.Binary.Op.LT;
            case LT_EQ -> Expr.Binary.Op.LTE;
            case GT -> Expr.Binary.Op.GT;
            case GT_EQ -> Expr.Binary.Op.GTE;
            case EQ -> {
                if (conditionDepth == 0) {
                    // Auf Anweisungsebene ist das eine Zuweisung; der Aufrufer
                    // liest sie. Hier darf nichts gemeldet werden.
                    yield null;
                }
                // In einer Bedingung dagegen ist es der häufige Griff daneben.
                error(peek().span(), "Zum Vergleichen braucht es zwei Gleichheitszeichen.",
                        "Ein einzelnes weist zu. Gemeint ist vermutlich ==.");
                yield Expr.Binary.Op.EQ;
            }
            default -> null;
        };
        if (op == null) {
            return left;
        }
        advance();
        Expr right = parseAdditive();
        return new Expr.Binary(op, left, right, left.span().to(right.span()));
    }

    private Expr parseAdditive() {
        Expr left = parseMultiplicative();
        while (at(TokenType.PLUS) || at(TokenType.MINUS)) {
            Expr.Binary.Op op = advance().is(TokenType.PLUS)
                    ? Expr.Binary.Op.ADD : Expr.Binary.Op.SUB;
            Expr right = parseMultiplicative();
            left = new Expr.Binary(op, left, right, left.span().to(right.span()));
        }
        return left;
    }

    private Expr parseMultiplicative() {
        Expr left = parseUnary();
        while (at(TokenType.STAR) || at(TokenType.SLASH) || at(TokenType.PERCENT)) {
            Token token = advance();
            Expr.Binary.Op op = switch (token.type()) {
                case STAR -> Expr.Binary.Op.MUL;
                case SLASH -> Expr.Binary.Op.DIV;
                default -> Expr.Binary.Op.MOD;
            };
            Expr right = parseUnary();
            left = new Expr.Binary(op, left, right, left.span().to(right.span()));
        }
        return left;
    }

    private Expr parseUnary() {
        Token token = peek();
        if (token.is(TokenType.BANG) || token.is(TokenType.MINUS)) {
            advance();
            Expr operand = parseUnary();
            Expr.Unary.Op op = token.is(TokenType.BANG)
                    ? Expr.Unary.Op.NOT : Expr.Unary.Op.NEGATE;
            return new Expr.Unary(op, operand, token.span().to(operand.span()));
        }
        return parsePostfix();
    }

    private Expr parsePostfix() {
        Expr expr = parsePrimary();
        while (true) {
            if (at(TokenType.DOT)) {
                advance();
                Token name = peek();
                // Nach dem Punkt gilt die Schlüsselwortliste nicht: Was dort
                // steht, vergibt das System, nicht der Spieler.
                if (name.is(TokenType.EOF) || name.is(TokenType.NL)) {
                    error(name.span(), "Nach dem Punkt fehlt der Name.");
                    return expr;
                }
                advance();
                Span span = expr.span().to(name.span());
                if (at(TokenType.LPAREN)) {
                    List<Expr.Argument> arguments = parseArguments();
                    expr = new Expr.Call(new Expr.Member(expr, name.text(), span),
                            arguments, span.to(previous().span()));
                } else {
                    expr = new Expr.Member(expr, name.text(), span);
                }
            } else if (at(TokenType.EXCEPT)) {
                advance();
                Expr exclusion = parseUnary();
                expr = new Expr.Except(expr, List.of(exclusion), expr.span().to(exclusion.span()));
            } else {
                return expr;
            }
        }
    }

    private Expr parsePrimary() {
        Token token = peek();
        switch (token.type()) {
            case INT -> {
                advance();
                // Eine Zahl vor einer Auswahl ist die Menge: 64 item:iron_ingot
                if (at(TokenType.SELECTOR)) {
                    Expr selection = parsePostfix();
                    return new Expr.Amount(Long.parseLong(token.text()), selection,
                            token.span().to(selection.span()));
                }
                return new Expr.IntLit(Long.parseLong(token.text()), token.span());
            }
            case FLOAT -> {
                advance();
                return new Expr.FloatLit(Double.parseDouble(token.text()), token.span());
            }
            case STRING -> {
                advance();
                return new Expr.StringLit(token.text(), token.span());
            }
            case DURATION -> {
                advance();
                return parseDuration(token);
            }
            case TRUE, FALSE -> {
                advance();
                return new Expr.BoolLit(token.is(TokenType.TRUE), token.span());
            }
            case IT -> {
                advance();
                return new Expr.It(token.span());
            }
            case SELECTOR -> {
                advance();
                return parseSelector(token);
            }
            // „power" steht allein, weil Strom keine Sorte hat. Ein echtes
            // Schlüsselwort und kein Name: Wer seinen Connector so nennt,
            // schreibt ihn in Rückstrichen — dieselbe Regel wie bei „for".
            case POWER -> {
                advance();
                return new Expr.Selector(Expr.Selector.Kind.POWER, "", "", token.span());
            }
            case NAME_PATTERN -> {
                advance();
                return new Expr.NamePattern(token.text(), token.span());
            }
            case NAME, ESCAPED_NAME -> {
                advance();
                if (at(TokenType.LPAREN)) {
                    Expr callee = new Expr.Name(token.text(), token.span());
                    List<Expr.Argument> arguments = parseArguments();
                    return new Expr.Call(callee, arguments, token.span().to(previous().span()));
                }
                return new Expr.Name(token.text(), token.span());
            }
            case STORAGE -> { advance(); return builtin(Expr.Builtin.Kind.STORAGE, token); }
            case CRAFTING -> { advance(); return builtin(Expr.Builtin.Kind.CRAFTING, token); }
            case WORLD -> { advance(); return builtin(Expr.Builtin.Kind.WORLD, token); }
            case NETWORK -> { advance(); return builtin(Expr.Builtin.Kind.NETWORK, token); }
            case WORKERS -> { advance(); return builtin(Expr.Builtin.Kind.WORKERS, token); }
            case MULTIBLOCKS -> { advance(); return builtin(Expr.Builtin.Kind.MULTIBLOCKS, token); }
            case LPAREN -> {
                advance();
                Expr inner = parseExpression();
                expect(TokenType.RPAREN, "Die Klammer wird nicht geschlossen.");
                return inner;
            }
            default -> {
                // Ein Schlüsselwort an dieser Stelle ist meistens ein Connector,
                // den jemand ohne Rückstriche geschrieben hat.
                if (TokenType.isKeyword(token.text())) {
                    error(token.span(),
                            quote(token.text()) + " ist ein Schlüsselwort.",
                            "Meinst du den Connector gleichen Namens? Dann schreibe ihn in "
                                    + "Rückstriche: `" + token.text() + "`");
                } else {
                    error(token.span(),
                            "Hier wird ein Wert erwartet, gefunden wurde " + describe(token) + ".");
                }
                advance();
                return new Expr.Invalid(token.span());
            }
        }
    }

    private Expr builtin(Expr.Builtin.Kind kind, Token token) {
        return new Expr.Builtin(kind, token.span());
    }

    /** Rechnet eine Zeitangabe in Ticks um und meldet, was nicht aufgeht. */
    private Expr parseDuration(Token token) {
        String text = token.text();
        int split = 0;
        while (split < text.length()
                && (Character.isDigit(text.charAt(split)) || text.charAt(split) == '.')) {
            split++;
        }
        double value = Double.parseDouble(text.substring(0, split));
        String unit = text.substring(split);
        double ticks = switch (unit) {
            case "t" -> value;
            case "s" -> value * 20;
            case "min" -> value * 20 * 60;
            case "h" -> value * 20 * 60 * 60;
            default -> value;
        };
        if (Math.abs(ticks - Math.round(ticks)) > 1e-9) {
            error(token.span(),
                    quote(text) + " geht nicht in ganzen Ticks auf.",
                    "Ein Tick ist der zwanzigste Teil einer Sekunde. Am nächsten liegt "
                            + Math.round(ticks) + "t.");
        }
        return new Expr.DurationLit(Math.round(ticks), text, token.span());
    }

    private Expr parseSelector(Token token) {
        String text = token.text();
        int colon = text.indexOf(':');
        Expr.Selector.Kind kind = switch (text.substring(0, colon)) {
            case "item" -> Expr.Selector.Kind.ITEM;
            case "fluid" -> Expr.Selector.Kind.FLUID;
            case "chemical" -> Expr.Selector.Kind.CHEMICAL;
            default -> Expr.Selector.Kind.TAG;
        };
        String rest = text.substring(colon + 1);
        int slash = rest.indexOf('/');
        String namespace = null;
        String path = rest;
        // Beim Tag ist der erste Abschnitt immer der Namensraum (tag:c/ores).
        if (slash >= 0 && (kind == Expr.Selector.Kind.TAG || !rest.startsWith("*"))) {
            namespace = rest.substring(0, slash);
            path = rest.substring(slash + 1);
        }
        return new Expr.Selector(kind, namespace, path, token.span());
    }

    private List<Expr.Argument> parseArguments() {
        List<Expr.Argument> arguments = new ArrayList<>();
        if (!expect(TokenType.LPAREN, "Hier fehlt die runde Klammer.")) {
            return arguments;
        }
        if (match(TokenType.RPAREN)) {
            return arguments;
        }
        do {
            Token start = peek();
            String name = null;
            // Ein benanntes Argument: strategy: least_filled
            if (start.is(TokenType.NAME) && peekAt(1).is(TokenType.COLON)) {
                advance();
                advance();
                name = start.text();
            } else if (start.is(TokenType.STRATEGY) && peekAt(1).is(TokenType.COLON)) {
                advance();
                advance();
                name = "strategy";
            }
            Expr value = parseLambdaOrExpression();
            arguments.add(new Expr.Argument(name, value, start.span().to(value.span())));
        } while (match(TokenType.COMMA));
        expect(TokenType.RPAREN, "Die Klammer wird nicht geschlossen.");
        return List.copyOf(arguments);
    }

    /** Erkennt die Pfeilform {@code m => …}, die nur verschachtelt gebraucht wird. */
    private Expr parseLambdaOrExpression() {
        Token start = peek();
        if (start.is(TokenType.NAME) && peekAt(1).is(TokenType.EQ) && peekAt(2).is(TokenType.GT)) {
            advance();
            advance();
            advance();
            Expr body = parseExpression();
            return new Expr.Lambda(start.text(), body, start.span().to(body.span()));
        }
        return parseExpression();
    }

    // ---- Fehlerbehebung ---------------------------------------------------

    /**
     * Liest bis zur nächsten Deklaration weiter. Der Editor braucht auch für
     * unfertigen Code einen Baum, sonst kann er nicht vervollständigen.
     */
    private void recoverToDeclaration() {
        while (!at(TokenType.EOF)) {
            switch (peek().type()) {
                case WORKER, GROUP, MULTIBLOCK, EVENT, DISPLAY, FN, ON, GLOBAL -> {
                    return;
                }
                default -> advance();
            }
        }
    }

    private void recoverToLineEnd() {
        while (!at(TokenType.EOF) && !at(TokenType.NL) && !at(TokenType.RBRACE)) {
            advance();
        }
    }

    private void recoverToClosingParen() {
        int depth = 1;
        while (!at(TokenType.EOF) && depth > 0) {
            if (at(TokenType.LPAREN)) {
                depth++;
            } else if (at(TokenType.RPAREN)) {
                depth--;
            }
            advance();
        }
    }

    // ---- Hilfen -----------------------------------------------------------

    private String expectName(String what) {
        Token token = peek();
        if (token.is(TokenType.NAME) || token.is(TokenType.ESCAPED_NAME)) {
            advance();
            return token.text();
        }
        if (TokenType.isKeyword(token.text())) {
            error(token.span(),
                    "Der Name der " + what + " fehlt — " + quote(token.text())
                            + " ist ein Schlüsselwort.",
                    "Soll die " + what + " wirklich so heißen? Dann schreibe sie in "
                            + "Rückstriche: `" + token.text() + "`");
        } else {
            error(token.span(),
                    "Hier wird der Name der " + what + " erwartet, gefunden wurde "
                            + describe(token) + ".");
        }
        return "?";
    }

    private Token expectBrace(Token opened, String what) {
        Token token = peek();
        if (token.is(TokenType.RBRACE)) {
            return advance();
        }
        error(token.span(),
                "Die geschweifte Klammer von " + what + " wird nicht geschlossen.",
                "Sie wurde in Zeile " + opened.span().line() + " geöffnet.");
        return token;
    }

    private boolean expect(TokenType type, String message) {
        return expect(type, message, null);
    }

    private boolean expect(TokenType type, String message, String hint) {
        if (at(type)) {
            advance();
            return true;
        }
        error(peek().span(), message, hint);
        return false;
    }

    /**
     * Steht hier wirklich ein Schlüsselwort? Ein Name in Rückstrichen trägt
     * denselben Text, ist aber ausdrücklich als Name gemeint.
     */
    private static boolean isRealKeyword(Token token) {
        return TokenType.keyword(token.text()) == token.type();
    }

    private boolean match(TokenType type) {
        if (at(type)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean at(TokenType type) {
        return peek().is(type);
    }

    private Token peek() {
        return peekAt(0);
    }

    private Token peekAt(int offset) {
        int target = index + offset;
        return target < tokens.size() ? tokens.get(target) : tokens.get(tokens.size() - 1);
    }

    private Token previous() {
        return index > 0 ? tokens.get(index - 1) : tokens.get(0);
    }

    private Token advance() {
        Token token = peek();
        if (index < tokens.size() - 1) {
            index++;
        }
        return token;
    }

    private void skipNewlines() {
        while (at(TokenType.NL)) {
            advance();
        }
    }

    /** Beschreibt ein Token so, wie ein Spieler es lesen würde. */
    private static String describe(Token token) {
        return switch (token.type()) {
            case EOF -> "das Ende der Datei";
            case NL -> "das Zeilenende";
            case INT, FLOAT -> "die Zahl " + token.text();
            case STRING -> "der Text " + quote(token.text());
            case DURATION -> "die Zeitangabe " + token.text();
            case SELECTOR -> "die Auswahl " + token.text();
            case POWER -> "power";
            case NAME, ESCAPED_NAME -> quote(token.text());
            case LBRACE -> "eine geschweifte Klammer";
            case RBRACE -> "eine schließende geschweifte Klammer";
            case LPAREN -> "eine runde Klammer";
            case RPAREN -> "eine schließende runde Klammer";
            default -> quote(token.text());
        };
    }

    private static String quote(String text) {
        return "„" + text + "“";
    }

    /**
     * Warnt vor einer Anweisung, die nichts tun kann.
     *
     * <p>Der Anlass ist {@code log "hallo"}: Das sieht aus wie ein Aufruf,
     * ist aber ein Name und eine Zeichenkette nebeneinander — zwei
     * Anweisungen, die beide nichts bewirken. Der Parser nahm sie klaglos
     * an, und das Programm lief, ohne etwas zu schreiben. <b>Ein Fehler,
     * den man nicht findet, weil nichts passiert.</b>
     *
     * <p>Eine Warnung und kein Fehler: Das Programm wird übernommen, die
     * Zeile steht im Reiter Code. Zum Fehler zu machen, was heute
     * durchgeht, würde Programme brechen, die außer dieser Zeile in Ordnung
     * sind — und die Zeile tut ja nichts.
     *
     * <p>Nur die eindeutigen Fälle: Ein Name, ein Wert, eine Auswahl. Ein
     * Aufruf und ein Zugriff auf ein Feld können Wirkung haben.
     */
    private void warnIfWithoutEffect(Expr expr) {
        String was = switch (expr) {
            case Expr.Name ignored -> "Ein Name allein";
            case Expr.StringLit ignored -> "Eine Zeichenkette allein";
            case Expr.IntLit ignored -> "Eine Zahl allein";
            case Expr.FloatLit ignored -> "Eine Zahl allein";
            case Expr.BoolLit ignored -> "Ein Wahrheitswert allein";
            case Expr.Selector ignored -> "Eine Auswahl allein";
            default -> null;
        };
        if (was == null) {
            return;
        }
        diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, expr.span(),
                was + " bewirkt nichts.",
                "Fehlen Klammern? Ein Aufruf heißt log(\"hallo\") und nicht log \"hallo\"."));
    }

    private void error(Span span, String message) {
        error(span, message, null);
    }

    private void error(Span span, String message, String hint) {
        diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, span, message, hint));
    }
}
