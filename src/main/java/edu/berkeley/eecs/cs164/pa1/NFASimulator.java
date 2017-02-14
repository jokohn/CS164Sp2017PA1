package edu.berkeley.eecs.cs164.pa1;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.Collections;
import java.util.Iterator;

/**
 * This class simulates a non-deterministic finite automaton over ASCII strings.
 */
public class NFASimulator {
    private final Automaton nfa;

    /**
     * Create a new simulator from a given NFA structure
     *
     * @param nfa the nfa to simulate
     */
    public NFASimulator(Automaton nfa) {
        this.nfa = nfa;
    }

    /**
     * Determines whether or not the given text is accepted by the NFA
     *
     * @param text the text to try matching
     * @return true if the text is accepted by the NFA, else false
     */
    public boolean matches(String text) {
        // this function will work by starting at a starting state, finding all
        // the states that can be reached by performing only epsilon transitions
        // and then finding all the states reachable with a transition with the current
        // character, then repeating.

        // We will use i to track where in the input we are presently
        int i = 0;

        // we will define our start and end states
        AutomatonState startState = this.nfa.getStart();
        AutomatonState outState = this.nfa.getOut();        

        // we will have 2 sets of states, a current and a reachable. These sets will be
        // used for our to represent the states reachable by a single transition,
        // whether epsilon or character specific.
        HashSet<AutomatonState> currentStates = new HashSet();
        HashSet<AutomatonState> reachableStates = new HashSet();

        // When we start our NFA we will start at the start state of the NFA, so at first
        // that will be the only item in currentStates
        currentStates.add(startState);

        // we will iterate through the input
        while(i < text.length()) {
            // if we couldn't reach any new states after our transitions scan in the previous
            // iteration it must mean our text is not a valid expression
            if (currentStates.isEmpty()) {
                System.out.print("Couldn't reach any states for input " + text.charAt(i) + ".\n");
                return false;
            }

            // in order to find all the states reachable by epsilon* transitions, we will
            // needget the epsilon transitions for all our current states. Since this can
            // go on a long time for large regexes, we will stop looping once our current
            // and reachable sets are identical, as this means that we can reach no new
            // states with epsilon transitions given our current states
            while (!(currentStates.equals(reachableStates))) {
                // replace the contents of currentStates with the contents of reachableStates
                // Note that reachableStates will always contain everything in currentStates
                // during this epsilon transition loop because all states can reach themselves
                currentStates = reachableStates;

                // having added our current States to the set of epsilon transition reachable
                // states we will iterate through the current states and add all states
                // that we can reach via epsilon transitions
                Iterator<AutomatonState> currentStatesIterator = currentStates.iterator();

                while (currentStatesIterator.hasNext()) {

                    // get a state from the currentStates set
                    AutomatonState currentState = currentStatesIterator.next();

                    // add all all the states reachable via an epsilon transition from
                    // currentState to reachable states
                    reachableStates.addAll(currentState.getEpsilonTransitions());
                }
            }

            // Having found all the states reachable via 0 or more epsilon transitions from
            // the set of states we started this iteration of the loop with, we will now
            // see what states we can reach via transitions from the current character in
            // our input string.
            char currentCharacter = text.charAt(i);

            // unlike epsilon transitions, it may not be possible to reach our current
            // state(s) with a character transition. So we will need an empty reachableStates
            // set
            reachableStates = new HashSet();

            // We now need to iterate through our current states to get our reachable states.
            // We will use an iterator for this purpose
            Iterator<AutomatonState> currentStatesIterator =  currentStates.iterator();

            while (currentStatesIterator.hasNext()) {
                AutomatonState currentState = currentStatesIterator.next();

                // If we can reach any states with a current character transition in the
                // current state, we will add them to our reachable states
                reachableStates.addAll(currentState.getTransitions(currentCharacter));
            }

            // having finished our state processing for our current input we will replace
            // current's contents with reachables (since we don't care about those anymore)
            currentStates = reachableStates;

            // we will zero out our reachable states so we can fill them in the next cycle
            reachableStates = new HashSet();

            // finally, we will increment i so we can look at the next character in text
            i++;
        }

        // having exhausted the input we will need to perform one final series of epsilon transition
        // in case the out state is reachable via our current states, but isn't a part of them
        while (!(currentStates.equals(reachableStates))) {
                currentStates = reachableStates;

                Iterator<AutomatonState> currentStatesIterator = currentStates.iterator();

                while (currentStatesIterator.hasNext()) {

                    AutomatonState currentState = currentStatesIterator.next();

                    reachableStates.addAll(currentState.getEpsilonTransitions());
                }
        }

        // once we have iterated through the whole input and found the states we reach at the end
        // of the cycles of ε* character transitions, we need to see if we could have reached the
        // out state while doing that.
        if (currentStates.contains(outState)) {
        	System.out.print("Reached end of input. Made to out state. Valid regex.\n");
            return true;
        }
        else {
            System.out.print("Reached end of input. Couldn't reach out state.\n");
            return false;
        }

    }
}
