package plc.project;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Global> globals = new ArrayList<>();
        List<Ast.Function> functions = new ArrayList<>();

        while (peek("LIST")|peek("VAL")|peek("VAR")){
            globals.add(parseGlobal());
        }
        while (match("FUN")){
            functions.add(parseFunction());
        }
        Ast.Source ret = new Ast.Source(globals, functions);
        return ret;

    }

    /**
     * Parses the {@code global} rule. This method should only be called if the
     * next tokens start a global, aka {@code LIST|VAL|VAR}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        Ast.Global ret = null;
        if (match("LIST")){
            ret = parseList();
        }
        else if (match("VAL")){
            ret =  parseImmutable();
        }
        else if (match("VAR")){
            ret =  parseMutable();
        }
        if(!match(";")){
            throw new ParseException("Missing ;",tokens.index);
        }

        return ret;
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        String name;
        String type = "";
        boolean hastype = false;
        if(!peek(Token.Type.IDENTIFIER)){
            throw new ParseException("No identifier after List statement declaration",tokens.index);
        }
        name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);

        if(match(":")){
            if(!peek(Token.Type.IDENTIFIER)){
                throw new ParseException("No type after :", tokens.index);
            }
            type = tokens.get(0).getLiteral();
            match(Token.Type.IDENTIFIER);
            hastype = true;
        }

        if(!match("=","[")){
            throw new ParseException("No [ after List statement declaration",tokens.index);
        }

        List<Ast.Expression> values = new ArrayList<>();
        values.add(parseExpression());
        while (match(",")){
            values.add(parseExpression());
        }
        if(!match("]")){
            throw new ParseException("No ] after List statement declaration",tokens.index);
        }

        Ast.Expression.PlcList list = new Ast.Expression.PlcList(values);
        //list.setType(Environment.Type.);
        if(hastype){
            Ast.Global ret = new Ast.Global(name, type, true, Optional.of(list));
            return ret;
        }
        else {
            Ast.Global ret = new Ast.Global(name, true, Optional.of(list));
            return ret;
        }
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        String name;
        if(!peek(Token.Type.IDENTIFIER)){
            throw new ParseException("No identifier after List statement declaration",tokens.index);
        }
        name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);

        Ast.Expression exp;
        String type = "";
        if(match("=")){
            exp = parseExpression();
            Ast.Global ret = new Ast.Global(name, true, Optional.ofNullable(exp));
            return ret;
        } else if (match(":")) {
            type = tokens.get(0).getLiteral();
            //System.out.println(type);
            match(Token.Type.IDENTIFIER);
            if(peek("=")) {
                match("=");
            }
            else{
                //throw new ParseException("Value must be defined for mutable",tokens.index);
                Ast.Global ret = new Ast.Global(name, type, true, Optional.empty());
                return ret;
            }

            exp = parseExpression();
            Ast.Global ret = new Ast.Global(name, type, true, Optional.ofNullable(exp));
            return ret;
        }
        else{
            //throw new ParseException("Value must be defined for mutable",tokens.index);
            Ast.Global ret = new Ast.Global(name, type, true, Optional.empty());
            return ret;
        }
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
        String name;
        if(!peek(Token.Type.IDENTIFIER)){
            throw new ParseException("No identifier after List statement declaration",tokens.index);
        }
        name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);

        Ast.Expression exp;
        String type = "";
        if(match("=")){
            exp = parseExpression();
            Ast.Global ret = new Ast.Global(name, false, Optional.ofNullable(exp));
            return ret;
        } else if (match(":")) {
            type = tokens.get(0).getLiteral();
            //System.out.println(type);
            match(Token.Type.IDENTIFIER);
            if(peek("=")) {
                match("=");
            }
            else{
                throw new ParseException("Value must be defined for immutable",tokens.index);
            }

            exp = parseExpression();
            Ast.Global ret = new Ast.Global(name, type, false, Optional.ofNullable(exp));
            return ret;
        }
        else{
            throw new ParseException("Value must be defined for immutable",tokens.index);
        }


    }

    /**
     * Parses the {@code function} rule. This method should only be called if the
     * next tokens start a method, aka {@code FUN}.
     */
    public Ast.Function parseFunction() throws ParseException {
        match("FUN");
        String name;
        String type = "";
        boolean hastype = false;
        if(!peek(Token.Type.IDENTIFIER)){
            throw new ParseException("No identifier after List statement declaration",tokens.index);
        }
        name = tokens.get(0).getLiteral();
        //System.out.println(name);
        match(Token.Type.IDENTIFIER);
        if(!match("(")){
            throw new ParseException("No (",tokens.index);
        }

        List<String> values = new ArrayList<>();
        List<String> parametertypes = new ArrayList<>();
        if(peek(Token.Type.IDENTIFIER)) {

            values.add(tokens.get(0).getLiteral());
            match(Token.Type.IDENTIFIER);
            if(match(":")){
                parametertypes.add(tokens.get(0).getLiteral());
                match(Token.Type.IDENTIFIER);
            }
            while (match(",")) {
                if (!peek(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Identifier missing", tokens.index);
                }
                values.add(tokens.get(0).getLiteral());
                match(Token.Type.IDENTIFIER);
                if(match(":")){
                    parametertypes.add(tokens.get(0).getLiteral());
                    match(Token.Type.IDENTIFIER);
                }
            }

        }
        if (!match(")")) {
            throw new ParseException("No )", tokens.index);
        }

        if(match(":")){
            type = tokens.get(0).getLiteral();
            //System.out.println(type);
            match(Token.Type.IDENTIFIER);
            hastype = true;
        }


        if(!match("DO")){
            throw new ParseException("Missing DO",tokens.index);
        }
        List<Ast.Statement> statements = parseBlock();
        if(!match("END")){
            throw new ParseException("Missing END",tokens.index);
        }
        if(hastype){

            Ast.Function ret = new Ast.Function(name, values, parametertypes, Optional.ofNullable(type), statements);
            return ret;
        }
        else {
            Ast.Function ret = new Ast.Function(name, values, statements);
            return ret;
        }

    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block of statements.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        List<Ast.Statement> statements = new ArrayList<>();
        while(!peek("END") && !peek("DEFAULT")&& !peek(";")&& !peek("ELSE")&& !peek("CASE")){
            statements.add(parseStatement());
        }
        return statements;
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {

        if (peek("LET")){
            return parseDeclarationStatement();
        }
        else if (peek("SWITCH")){
            return parseSwitchStatement();
        }
        else if (peek("IF")){
            return parseIfStatement();
        }
        else if (peek("WHILE")){
            return parseWhileStatement();
        }
        else if (peek("RETURN")){
            return parseReturnStatement();
        }
        else {
            Ast.Expression expression = parseExpression();
            if (match("="))
            {
                Ast.Expression value = parseExpression();
                if (!match(";"))
                    throw new ParseException("Expected ';'", tokens.index);

                return new Ast.Statement.Assignment(expression, value);
            }
            else if (match(";"))
                return new Ast.Statement.Expression(expression);
        }



        throw new UnsupportedOperationException();

    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {


        String name;
        String type= "";
        boolean hastype = false;
        match("LET");
        if(peek(Token.Type.IDENTIFIER)){
            name = tokens.get(0).getLiteral();
            match(Token.Type.IDENTIFIER);
        }
        else{
            throw new ParseException("No identifier!",tokens.index);
        }
        if(match(";")){
            Ast.Statement.Declaration state = new Ast.Statement.Declaration(name, Optional.empty());
            return state;
        }

        if(match(":")){
            type = tokens.get(0).getLiteral();
            match(Token.Type.IDENTIFIER);

            if(match("=")){
                Ast.Expression exp = parseExpression();
                Ast.Statement.Declaration state = new Ast.Statement.Declaration(name, Optional.ofNullable(type), Optional.ofNullable(exp));
                return state;
            }
            else{
                Ast.Statement.Declaration state = new Ast.Statement.Declaration(name, Optional.ofNullable(type), Optional.empty());
                return state;
            }

        }

        if(match("=")){
            Ast.Expression exp = parseExpression();
            Ast.Statement.Declaration state = new Ast.Statement.Declaration(name, Optional.ofNullable(exp));
            return state;
        }

        throw new ParseException("Improper Declaration",tokens.index);
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        match("IF");
        Ast.Expression name = null;

        List<Ast.Statement> statement1;
        List<Ast.Statement> statement2;

        if(peek(Token.Type.IDENTIFIER)){
            //we need to make generalizable; it might not just be one identifier, there may be much stuff to do
            name = parseExpression();
            Boolean x = match("DO");
            statement1 = parseBlock();
            Boolean y = match(";");
        }
        else{
            throw new ParseException("Condition missing",tokens.index);
        }
        if(match("END")){
            Ast.Statement.If ret = new Ast.Statement.If(
                    name,
                    statement1,
                    Arrays.asList()
            );
            return ret;
        }
        else if(match("ELSE")){
            if(peek(Token.Type.IDENTIFIER)){
                //same thing here
                statement2 = parseBlock();
                match(";", "END");
                Ast.Statement.If ret = new Ast.Statement.If(
                        name,
                        statement1,
                        statement2
                );
                return ret;
            }
            else{
                throw new ParseException("Improper else statement",tokens.index);
            }
        }
        else{
            throw new ParseException("Improper Ending to if statement",tokens.index);
        }



    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        List<Ast.Statement.Case> cases = new ArrayList<>();;


        match("SWITCH");
        Ast.Expression cond = parseExpression();

        while(peek("CASE")){
            Ast.Statement.Case newcase = parseCaseStatement();
            cases.add(newcase);
        }

        if(peek("DEFAULT")) {
            Ast.Statement.Case newcase = parseCaseStatement();
            cases.add(newcase);
        }
        else{
            throw new ParseException("No Default Statement",tokens.index);
        }
        if(match("END")) {}
        else{
            throw new ParseException("No End Statement",tokens.index);
        }

        Ast.Statement.Switch s = new Ast.Statement.Switch(cond, cases);
        return s;

    }

    /**
     * Parses a case or default statement block from the {@code switch} rule.
     * This method should only be called if the next tokens start the case or
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
        Ast.Expression exp;
        if(match("CASE")){
            exp = parseExpression();
            if(!match(":")){
                throw new ParseException("No termination in Case statement",tokens.index);
            }
            if(!peek(Token.Type.IDENTIFIER)){
                throw new ParseException("No statement in Case statement",tokens.index);
            }
            List<Ast.Statement> statements = parseBlock();
            //if(!match(";")){
            //    throw new ParseException("No termination in Case statement",tokens.index);
            //}

            Ast.Statement.Case ret = new Ast.Statement.Case(Optional.ofNullable(exp), statements);
            return ret;
        }
        else if(match("DEFAULT")){
            if(!peek(Token.Type.IDENTIFIER)){
                throw new ParseException("No statement in Case statement",tokens.index);
            }
            List<Ast.Statement> statements = parseBlock();
            //if(!match(";")){
                //throw new ParseException("No termination in Case statement",tokens.index);
            //}

            Ast.Statement.Case ret = new Ast.Statement.Case(Optional.empty(), statements);
            return ret;
        }



        throw new ParseException("Case Statement not working yet",tokens.index);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        match("WHILE");
        Ast.Expression exp = parseExpression();
        if(!match("DO")){
            throw new ParseException("No DO expression",tokens.index);
        }
        List<Ast.Statement> statements = parseBlock();
        if(!match("END")){
            throw new ParseException("No END to while loop",tokens.index);
        }

        Ast.Statement.While ret = new Ast.Statement.While(exp, statements);
        return ret;
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        match("RETURN");
        if(match(";")){
            throw new ParseException("Return Statement empty",tokens.index);
        }
        Ast.Expression exp = parseExpression();
        Ast.Statement.Return ret = new Ast.Statement.Return(exp);
        if(!match(";")){
            throw new ParseException("No ; at end of Return statement",tokens.index);
        }
        return ret;
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException
    {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */

    public Ast.Expression parseLogicalExpression() throws ParseException
    {
        Ast.Expression left = parseComparisonExpression();

        while(match("&&") || match("||"))
        {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseComparisonExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }

        return left;
    }

    /**
     * Parses the {@code comparison-expression} rule.
     */
    //comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )*;

    public Ast.Expression parseComparisonExpression() throws ParseException
    {
        Ast.Expression left = parseAdditiveExpression();

        while(match("<") || match( ">") || match( "==") ||
                match( "!="))
        {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseAdditiveExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }

        return left;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    //unary → ( "!" | "-" ) unary | primary ;
    public Ast.Expression parseAdditiveExpression() throws ParseException
    {
        Ast.Expression left = parseMultiplicativeExpression();

        while(match("+") || match( "-"))
        {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseMultiplicativeExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }

        return left;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException
    {
        Ast.Expression left = parsePrimaryExpression();

        while(match("*") || match("/") | match( "^"))
        {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parsePrimaryExpression();

            left = new Ast.Expression.Binary(operator, left, right);
        }

        return left;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */


    public Ast.Expression parsePrimaryExpression() throws ParseException
    {
        if (match("NIL"))
            return new Ast.Expression.Literal(null);
            //return parselitExpression(1);

        else if (match("TRUE"))
            return new Ast.Expression.Literal(true);
            //return parselitExpression(1);


        else if (match("FALSE"))
            return new Ast.Expression.Literal(false);
            //return parselitExpression(1);


        else if (match(Token.Type.INTEGER))
            return new Ast.Expression.Literal(new BigInteger(tokens.get(-1).getLiteral()));

        else if (match(Token.Type.DECIMAL))
            return new Ast.Expression.Literal(new BigDecimal(tokens.get(-1).getLiteral()));

        else if (match(Token.Type.CHARACTER))
        {
            String literal = tokens.get(-1).getLiteral();
            return new Ast.Expression.Literal(literal.charAt(1));

        }

        else if (match(Token.Type.STRING))
        {
            String literal = tokens.get(-1).getLiteral();
            String unquoted = literal.substring(1, literal.length() - 1).replace("\\n", "\n").replace("\\t", "\t");
            return new Ast.Expression.Literal(unquoted);
            //return new Ast.Expression.Literal(literal);
        }
        else if (match(Token.Type.IDENTIFIER))
        {
            String identifier = tokens.get(-1).getLiteral();
            if (match("("))
            {
                List<Ast.Expression> arguments = new ArrayList<>();
                if (!peek(")"))
                {
                    do
                    {
                        arguments.add(parseExpression());
                    }
                    while (match(","));
                }
                if (!match(")")) throw new ParseException("Expected ')'", tokens.index);
                return new Ast.Expression.Function(identifier, arguments);
            }
            else if (match("["))
            {
                Ast.Expression index = parseExpression();
                if (!match("]")) throw new ParseException("Expected ']' after index expression", tokens.index);
                Ast.Expression.Access baseAccess = new Ast.Expression.Access(Optional.empty(), identifier);
                return new Ast.Expression.Access(Optional.of(index), baseAccess.getName());
            }
            else
                return new Ast.Expression.Access(Optional.empty(), identifier);

        }
        else if (match("("))
        {
            Ast.Expression expression = parseExpression();
            if (!match(")")) throw new ParseException("Expected ')'", tokens.index);
            return new Ast.Expression.Group(expression);
        }

        else
            throw new ParseException("Expected a primary expression", tokens.index);
    }

    //primary → NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")" ;


    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */


    private boolean peek(Object... patterns)
    {
        for(int i = 0; i < patterns.length; i++)
        {
            if(!tokens.has(i))
            {
                return false;
            }
            else if (patterns[i] instanceof Token.Type)
            {
                if (patterns[i] != tokens.get(i).getType())
                    return false;

            }
            else if (patterns[i] instanceof String)
            {
                if(!patterns[i].equals(tokens.get(i).getLiteral()))
                    return false;
            }
            else
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());

        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns)
    {
        boolean peek = peek(patterns);

        if (peek)
        {
            for(int i = 0; i < patterns.length; i++)
                tokens.advance();
        }

        return peek;
    }

    private static final class TokenStream
    {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
