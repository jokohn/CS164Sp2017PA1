package edu.berkeley.eecs.cs164.pa1;


/**
 * This class parses a simple regular expression syntax into an NFA
 */
public class RegexParser {
	
    private RegexParser() {
    }

    /*
    Grammar:
    expr -> term ('|' term)*
    term -> factor*
    factor -> atom'*'|atom'?'|atom'+'|atom
    atom -> any character besides "()?|+*"
    atom -> "(" expr ")"
    */

    private static int pos;
    private static char[] input;
    private static char token;
    
    // token2 will be used in the event that we are dealing with an
    // escape character, which is 2 characters, but we want to treat as
    // a single character. Is null if token isn't \
    private static char token2;

    /**
     * This is the main function of this object. It kicks off
     * whatever "compilation" process you write for converting
     * regex strings to NFAs.
     *
     * @param pattern the pattern to compile
     * @return an NFA accepting the pattern
     * @throws RegexParseException upon encountering a parse error
     */
    public static Automaton parse(String pattern) {
        pos = 0;
        input = pattern.toCharArray();

        // put the first character(s) into the holder, and then advance pos for the next
        // call to advance
        advance();
        
        Automaton completeNFA = expr();
        if (pos < input.length) {
            throw new RegexParseException("Parsing failed to process entire input string.");
        }
        return completeNFA;
    }


    private static char getToken() {
        if (pos < input.length) {
            return input[pos++];
        } else {
            return 0;
        }
    }

    private static void advance() {
        token2 = '\0';
        token = getToken();
        // if our current token is a \ character, then it means that we need to also
        // consider then next character in our construction in certain functions
        if (token == '\\') {
            token2 = getToken();
        }
    }

    private static void match(char t) {
        if (token == t) {
            advance();
        } else {
            throw new RegexParseException("Unexpected token: " + token + ". Expecting: " + t + ".");
        }
    }


    // Generates an NFA that that can go into 1 or more term NFAs in order to implement
    // the alternation operand in regular expressions
    private static Automaton expr() {

        // create start and end states for our terms to branch out and in
        AutomatonState exprStart = new AutomatonState();
        AutomatonState exprOut = new AutomatonState();

        // get our first term NFA starting at the first character that expr can apply to
        Automaton termNFA = term();
    
        // allow our in progress NFA to transition into our term NFA and allow that NFA
        // to transition out into our out state
        exprStart.addEpsilonTransition(termNFA.getStart());
        termNFA.getOut().addEpsilonTransition(exprOut);

        // iterate through possible additional terms that can be part of our alternation
        // operator
        while (token == '|') {

            //advance past "|" to the character(s) term can apply to
            advance();

            // get a new term NFA for whatever comes after '|'
            Automaton newTerm = term();

            // connect our start state up newTerm and new newTerm up to our end state
            exprStart.addEpsilonTransition(newTerm.getStart());
            newTerm.getOut().addEpsilonTransition(exprOut);

        }

        

        // once all the possible alternatives have been made enterable from our start
        // state and exitable into our out state, create a new NFA and return it
        return new Automaton(exprStart, exprOut);
    }

    // the term operator generates a daisy chain of NFAs to implement concatenation.
    // this chain can be have 0 or more transitons
    private static Automaton term() {
        // We will need 3 states to create our term chain, a start state, an initial out
        // state and a current out state, which can be updated as we get more factors to
        // chain together.
        AutomatonState startState = new AutomatonState();
        AutomatonState initialout = new AutomatonState();;
        AutomatonState currentOut = initialout;

        // we will connect together our start state and initial out state with an epsilon
        // transition in case we aren't actually matching anything in this term
        startState.addEpsilonTransition(initialout);

        // check to see if we can actually put anything in the nfa
        if (token == ')' || token == '|' || token == 0) {
            return new Automaton(startState, currentOut);
        }

        // we will loop through the input until we find a symbol that indicates the end
        // of our current term ("|" or ")") or the end of the input
        while (true) {

            // advance the token into the first character in factor
            // we will create a new NFA from whatever we are currently looking at
            Automaton newFactor = factor();

            // we will then place newFactor at the end of our current chain of factors
            currentOut.addEpsilonTransition(newFactor.getStart());

            // having added a new term to our chain, we will now update it to be our new
            // outstate
            currentOut = newFactor.getOut();

            // advance out token to either the end of factor (and possibly input), or the
            // next factor to be created
            //advance();

            // check if our new token indicates that we are done making factors
            if(token == ')' || token == '|' || token == 0) {
                // having chained together our terms, we can now return a valid term NFA
                return new Automaton(startState, currentOut);
            }
        }
    }

    // Factor creates an atom NFA, and then proceeds to apply one of the 3 quantifier
    // operations by adding extra epsilon transitions, or else just returning the atom
    // if the next token is not one of said quanifier characters
    private static Automaton factor() {
        // create the atom NFA
        Automaton atomNFA = atom();

        // create start and out states to control the flow into or past the atom
        AutomatonState factorStart = new AutomatonState();
        AutomatonState factorOut = new AutomatonState();

        // if the next token is a Kleene star we need to make it possible to either cycle
        // back to the start of the atom NFA or exit 
        if (token == '*') {
            // To implement Kleene closure, we need to add 3 epsilon transitions:
            // 1. A transition from the our in-progress NFA's start to the atom's start
            //    (to allow matching to atom)
            // 2. A transition from the atom's out to our start state (to facilitate
            //    indefinite repitition)
            // 3. A transition from our in progress NFA's start state to our end state
            //    (in case we just skip the atom entirely)
            factorStart.addEpsilonTransition(atomNFA.getStart());
            atomNFA.getOut().addEpsilonTransition(factorStart);
            factorStart.addEpsilonTransition(factorOut);

            // with the necessary transitions to allow for 0 or more consecutive atom checks
            // we can create and return our NFA
            // We will also advance our input for the next operation
            advance();
            return new Automaton(factorStart, factorOut);
        }

        // if the next token in a "+" we add epsilon transitions to our states such that we
        // match against atom at least once
        else if (token == '+') {
            // To implement our "+" operation we need to add 3 epsilon transitions:
            // 1. A transition from our atom out state to our factor NFA's out state
            //    (we place it here to ensure atom is matched at least once)
            // 2. A transition from our in-progress NFA's start to atom's start (to
            //    make atom enterable and matchable to begin with)
            // 3. A transition from atom's out state to our factor start state (to
            //    enable repition at least once)
            atomNFA.getOut().addEpsilonTransition(factorOut);
            factorStart.addEpsilonTransition(atomNFA.getStart());
            atomNFA.getOut().addEpsilonTransition(factorStart);

            // with the necessary transitions to allow for 1 or more consecutive atom checks
            // we can create and return our NFA
            // We will also advance our input for the next operation
            advance();
            return new Automaton(factorStart, factorOut);

        }

        // if the next token is a ?, then we must add epsilon transitions so that we are
        // able to match atom 0 or 1 times.
        else if (token == '?') {
            // To implement our "?" operation we need to add 1 epsilon transition:
            // 1. A transition from our atom start state to our atom out state to allow
            // us to skip matching strings against atom if so desired
            atomNFA.getStart().addEpsilonTransition(atomNFA.getOut());

            // Having added the transition to allow us to skip matching atom, atom now
            // implements the "?" quantifier (we could technically cut out factorstart/out
            // for + and * as well, but I think keeping them makes it cleaner to read)
            // We will also advance our input for the next operation
            advance();
            return atomNFA;
        }


        // if the character after atom is not one of the quantifiers, then we can just return atom
        // we also avoid advancing as we are likely currently on a new atom or other Non-terminal's input
        else {
            return atomNFA;
        }
    }

    // this function should either make a parenthesized expr or make a 2 state NFA with a non-epsilon
    // transition, assuming that the input is in our grammar
    private static Automaton atom() {
        // if the first character atom looks at is a (, then we are dealing with a neted expression
        // and will need to construct a new expr
        if (token == '(') {

            // advance our token the first character in expr
            advance();

            // try to create our expr
            Automaton exprNFA = expr();

            match(')');

            // if the match succeeds, then we have a valid nest expression and we can return the automaton
            return exprNFA;  

        // check to make sure that our current token is non-special character (i.e. it is not a
        // quantifier alternator, or an nested expression closer). Note that a nested expression
        // opener "(" is not allowed either, but will always be caught by the preceding conditional
        }
        else if (token == '\\') {
            // We are still performing a character match, just in a special case of a character, so we
            // will need a start and out state
            AutomatonState escapeStart = new AutomatonState();
            AutomatonState escapeOut = new AutomatonState();
    
            // we need to check that the character following the escape is one of the permitted
            // escape characters
            if (token2 == 'n'){escapeStart.addTransition('\n', escapeOut);}
            else if (token2 == 't') {escapeStart.addTransition('\t', escapeOut);}
            else if (token2 == '|') {escapeStart.addTransition('|', escapeOut);}
            else if (token2 == '(') {escapeStart.addTransition('(', escapeOut);}
            else if (token2 == ')') {escapeStart.addTransition(')', escapeOut);}
            else if (token2 == '*') {escapeStart.addTransition('*', escapeOut);}
            else if (token2 == '+') {escapeStart.addTransition('+', escapeOut);}
            else if (token2 == '?') {escapeStart.addTransition('?', escapeOut);}
            else if(token2 == '\\') {escapeStart.addTransition('\\', escapeOut);}

            // if the second character doesn't match one of the characters our grammar allows escaping for,
            // then the regex must be malformed and we throw an error.
            else {
                throw new RegexParseException("Unexpected escape character: " + token + token2);
            }
            
            // having proccessed our portion of input, we advance so the previous call is in its part
            advance();

            // Once we have added the appropriate escaped character transition, we return our completed NFA
            return new Automaton(escapeStart, escapeOut);
        }
        // if the term our atom is looking at is a special operator, then we have likely misparsed somewhere
        else if (token == '?' || token == '+' || token == '*' || token == ')' || token == '|') {

            // if the current character is not an expression opener or a regular character (i.e. it is a nested
            // expression closer ")" or one of the regex operators  *+?|), something has gone wrong. Either an
            // invalid regular expression was given, or a term was expanded into a factor chain when it should
            // have been an empty string 
            throw new RegexParseException("Unexpected token: " + token + ". Expecting: a character or '('.");
        }
        // We haven't started off with a special character or misparsed at some point, then we should just make
        // an NFA that matches the current chracter
        else {

            // create 2 NFA states with a non-epsilon transition between them to match the character
            // expressed in the current pos on the input
            AutomatonState atomNFAStart = new AutomatonState();
            AutomatonState atomNFAOut = new AutomatonState();
            atomNFAStart.addTransition(token, atomNFAOut);

            // having processed our material here, we advance so the calling function is outside our stuff
            advance();

            // return an automaton
            return new Automaton(atomNFAStart, atomNFAOut);
        }
    }
}