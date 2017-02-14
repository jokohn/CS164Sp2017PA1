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
    term -> factor+ | epsilon
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
        // put the first character(s) into the holder, and then advance pos for the next call to advance
        advance();
        Automaton completeNFA = expr();
        if (pos < input.length) {
            throw new RegexParseException("Parsing failed to process entire input string.");
        }
        System.out.print(completeNFA.toString());
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
    
        // advance past whatever term ended at (which might be "|")
        advance();
    
        // allow our in progress NFA to transition into our term NFA and allow that NFA
        // to transition out into our out state
        exprStart.addEpsilonTransition(termNFA.getStart());
        termNFA.getOut().addEpsilonTransition(exprOut);

        // iterate through possible additional terms that can be part of our alternation
        // operator
        while (token == '|') {

            //advance past "|" to the characters term can apply to
            advance();

            // get a new term NFA for whatever comes after '|'
            Automaton newTerm = term();

            // connect our start state up newTerm and new newTerm up to our end state
            exprStart.addEpsilonTransition(newTerm.getStart());
            newTerm.getOut().addEpsilonTransition(exprOut);

            // advance to our next character in the sequence to see it it's "|"
            advance();
        }

        // once all the possible alternatives have been made enterable from our start
        // state and exitable into our out state, create a new NFA and return it
        return new Automaton(exprStart, exprOut);
    }

    // the term operator does one of 2 things:
    // Either it generates a daisy chain of NFAs to implement concatenation.
    // OR else, if the above generates an error because it doesn't work,
    // it generates a null NFA where the start and end are the same state
    private static Automaton term() {
        // create a start state for our in-progress NFA (we do not need to create
        // an out state as one will be made for us)
        AutomatonState termStart = new AutomatonState();

        // try to create a foncatenated chain of factor NFAs, which may fail.
        // we will need be able to reset the parsing in the event that this
        // term was actually meant to go to epsilon
        int oldPos = pos;
        char oldToken = token;
        char oldToken2 = token2;
        try{

            // our term NFA will consist of 1 or more factor NFAs chained together, so we
            // need to create the first one outside any loob
            Automaton newFactor = factor();

            // we will advance to the token we need to look at to create our factor NFA
            advance();

            // we will connect the new factor to our start state via an epsilon transition
            termStart.addEpsilonTransition(newFactor.getStart());

            // in order to concatentate properly, we will need to know what the current end
            // of our chain of concatenated NFAs is. To do this, we will need a outState
            // container for our latest NFA
            AutomatonState outHolder = newFactor.getOut();

            // since term is always part of an expr, the only characters that can signal the
            // end of a term are "|" (if term is part of an alternation series), ")" (
            // if expr is itself a nested expression coming from atom), or the end of the input
            // if our factor has consumed the whole regex
            while (token != ')' && token != '|' && token != 0) {
                // create a factor NFA
                newFactor = factor();

                // advance the input to allow the next NFA to be created
                advance();

                // concatentate our new factor to the end of our NFA chain
                outHolder.addEpsilonTransition(newFactor.getStart());

                // make newFactor's out state the new outHolder
                outHolder = newFactor.getOut();

            }

            // once we have hit a termination character for term, we can construct our NFA
            return new Automaton(termStart, outHolder);
        }
        // if concatenation was the incorrect production to use for this term
        // we will backtrack and instead give an epsilon NFA
        catch (RegexParseException e) {
            pos = oldPos;
            token = oldToken;
            token2 = oldToken2;

            // Since we want our NFA to essentially do nothing, we can just return an
            // NFA where the start and out state are the same automton state
            return new Automaton(termStart, termStart);
        }
    }

    // Factor creates an atom NFA, and then proceeds to apply one of the 3 quantifier
    // operations by adding extra epsilon transitions, or else just returning the atom
    // if the next token is not one of said quanifier characters
    private static Automaton factor() {
        // create the atom NFA and then advance to the next token to figure out what, if
        // anything should be done to that NFA
        Automaton atomNFA = atom();
        advance();

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
            return atomNFA;
        }
        // if the character after atom is not one of the quantifiers, then we can just return atom
        else {
            return atomNFA;
        }
    }

    // this function should either make a parenthesized expr or make a 2 state NFA with a non-epsilon
    // transition, assuming that the input is in our grammar
    private static Automaton atom() {
        // if the first character atom looks at is a (
        if (token == '(') {
            // create our expr NFA and then advance to our next term
            Automaton returnNFA = expr();
            advance();

            // check to make sure that our NFA is properly closed upon
            match(')');
            return returnNFA;

        // check to make sure that our current token is non-special character (i.e. it is not a
        // quantifier alternator, or an nested expression closer). Note that a nested expression
        // opener "(" is not allowed either, but will always be caught by the preceding conditional
        } else if (token != '?' && token != '+' && token != '*' && token != ')' && token != '|') {
            // create 2 NFA states with a non-epsilon transition between them to match the character
            // expressed in the current pos on the input
            AutomatonState atomNFAStart = new AutomatonState();
            AutomatonState atomNFAOut = new AutomatonState();
            atomNFAStart.addTransition(token, atomNFAOut);

            // if we aren't dealing with an escaped character, then we can just return the NFA with 2 states
            if (token != '\\') {
                return new Automaton(atomNFAStart, atomNFAOut);
            }
            // if the token is a \, it indicates that we are actually trying to match an escaped
            // character and need to look at the next character in order to properly form our NFA
            else {
                // we need to check that the character following the escape is one of the permitted
                // escape characters
                if (token2 == 'n' || token2 == 't' || token2 == '|' || token2 == '(' || token2 == ')' ||
                    token2 == '*' || token2 == '+' || token2 == '?' || token2 == '\\') {

                    // create a new state to match to against the second piece of the escaped character
                    AutomatonState escapeState = new AutomatonState();

                    // add a transition from the atomNFAOut to the escape state
                    atomNFAOut.addTransition(token2, escapeState);

                    // return a 3 concatentated NFA
                    return new Automaton(atomNFAStart, escapeState);
                }
                // if the second character doesn't match one of the characters our grammar allows escaping for,
                // then the regex must be malformed and we throw an error.
                else {
                    throw new RegexParseException("Unexpected escape character: " + token + token2);
                }
            }
        // if the current character is not an expression opener or a regular character (i.e. it is a nested
        // expression closer ")" or one of the regex operators  *+?|), something has gone wrong. Either an
        // invalid regular expression was given, or a term was expanded into a factor chain when it should
        // have been an empty string 
        } else {
            throw new RegexParseException("Unexpected token: " + token + ". Expecting: a character or '('.");
        }
    }

}
