package plc.project;

import java.util.ArrayList;
import java.util.List;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the character which is
 * invalid.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are * helpers you need to use, they will make the implementation a lot easier. */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        List<Token> list = new ArrayList<>();
        while(peek(".") || peek("[\s|\b|\n|\t|\n]")) {
            if (match("[\s|\b|\n|\t|\r]")){

            }
            else {
                Token l = lexToken();
                list.add(l);
            }
        }
        //throw new UnsupportedOperationException(); //TODO
        return list;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        if(peek("('@'|[A-Za-z])[A-Za-z0-9_-]*")){
            return lexIdentifier();
        }
        else if(peek("[-]|[0-9]")){
            return lexNumber();
        }
        else if(peek("[-]|[0-9]")){
            return lexNumber();
            //peek("'-'?|(0[1-9])|[1-9][0-9]*)'.'[0-9]+")
        }
        else if(peek("[']")){
            return lexCharacter();
        }
        else if(peek("\"")){
            return lexString();
        }
        else if(peek("[\b|\n|\r|\t]")){
            match("[\b|\n|\r|\t]");
        }
        else if(peek("[!=]=?|&&|[||]|.")){
            return lexOperator();
        }

        return lexOperator();

    }

    public Token lexIdentifier() {
        Token.Type idtype = Token.Type.IDENTIFIER;
        int ind_old = chars.index;
        boolean x = match("('@'|[A-Za-z])");
        int ind_new = ind_old+1;
        while (match("[A-Za-z0-9_-]")) {
            ind_new++;
        }
        //int ind_new = chars.index;
        String lit = chars.input.substring(ind_old,ind_new);

        Token ret = new Token(idtype, lit, ind_old);

        return ret;

    }

    public Token lexNumber() {
        Token.Type idtype;
        int ind_old = chars.index;
        int ind_new = ind_old;
        if(match("-")){
            ind_new++;
            if(!peek("[0-9]")) {
                idtype = Token.Type.OPERATOR;
                String lit = chars.input.substring(ind_old,ind_new);
                Token ret = new Token(idtype, lit, ind_old);
                return ret;
            }
        }
        if(peek("0")){
            if(peek("0","[.]","[0-9]")){
                match("0","[.]","[0-9]");
                idtype = Token.Type.DECIMAL;
                ind_new = ind_new+3;
                while(match("[0-9]")){
                    ind_new++;
                }
            }
            else{
                idtype = Token.Type.INTEGER;
                ind_new++;
            }
        }

        else if (match("[1-9]")){
            ind_new++;
            idtype = Token.Type.INTEGER;
            while (match("[0-9]")) {
                ind_new++;
            }
            if(match("[.]")){
                if(match("[0-9]")) {
                    ind_new = ind_new+2;
                    idtype = Token.Type.DECIMAL;
                    while(match("[0-9]")){
                        ind_new++;
                    }
                }
            }
        }
        else{
            throw new ParseException("Number not matched", chars.index); //TODO
        }
        //int ind_new = chars.index;
        String lit = chars.input.substring(ind_old,ind_new);
        match(lit);
        Token ret = new Token(idtype, lit, ind_old);

        return ret;
    }

    public Token lexCharacter() {
        int ind_old = chars.index;
        int ind_new = ind_old;
        Token.Type idtype;
        idtype = Token.Type.CHARACTER;


        match("[']");
        ind_new = ind_new+1;
        if (match("\\\\")){
            if(match("[b]|[n]|[r]|[t]|[']|[\"]|[\\\\]]", "[']")){
                ind_new = ind_new+3;
            }
            else{
                throw new ParseException("Invalid char!", chars.index);
            }
        }
        else if (match(".","[']")){
            ind_new = ind_new+2;
        }
        else{
            throw new ParseException("Invalid char!", chars.index);
        }

        String lit = chars.input.substring(ind_old,ind_new);
        Token ret = new Token(idtype, lit, ind_old);
        return ret;
    }

    public Token lexString() {
        int ind_old = chars.index;
        int ind_new = ind_old;
        Token.Type idtype;
        idtype = Token.Type.STRING;

        match("[\"]");
        ind_new = ind_new+1;
        while ((!match("[\"]"))){

            if (match("\\\\")){
                if(match("[b]|[n]|[r]|[t]|[']|[\"]|[\\\\]]")){
                    ind_new = ind_new+2;

                }
                else{
                    throw new ParseException("Invalid string!", chars.index);
                }
            }
            else if (match("[A-Za-z0-9,!?. ]")){
                ind_new = ind_new+1;
                if(!peek(".")){
                    throw new ParseException("Invalid string!", chars.index);
                }
            }
            else{
                throw new ParseException("Invalid string!", chars.index);
            }
        }
        ind_new = ind_new+1;

        String lit = chars.input.substring(ind_old,ind_new);
        //System.out.println(lit);
        Token ret = new Token(idtype, lit, ind_old);
        return ret;
    }

    public void lexEscape() {
        throw new UnsupportedOperationException(); //TODO
    }

    public Token lexOperator() {
        Token.Type idtype = Token.Type.OPERATOR;
        int ind_old = chars.index;
        int ind_new = ind_old;
        boolean x;
        if (match("=","=") || match("!","=")|| match("&","&")||match("|","|")){
             x = true;
        }
        else{
            x = false;
        }
        if (x){
            ind_new = ind_new+2;
        }

        else{
            match(".");
            ind_new ++;
        }
        //int ind_new = chars.index;
        String lit = chars.input.substring(ind_old,ind_new);

        Token ret = new Token(idtype, lit, ind_old);

        return ret;
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns)
    {
        for (int i = 0; i < patterns.length; i++)
        {
            if(!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])) {
                //String x = String.valueOf(chars.get(i));
                //String y = String.valueOf(chars.get(i));
                return false;
            }
        }
        return true;
    }

    /**

     Returns true in the same way as {@link #peek(String...)}, but also
     advances the character stream past all matched characters if peek returns
     true. Hint - it's easiest to have this method simply call peek.*/
    public boolean match(String... patterns){
        boolean peek = peek(patterns);

        if(peek)
        {
            for(int i = 0; i < patterns.length; i++)
                chars.advance();
        }
        return peek;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */


    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }

    }

}
