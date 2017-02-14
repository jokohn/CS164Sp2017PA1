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

        System.out.print("Our set starts with " + Integer.toString(currentStates.size()) + " elements.\n");

        // before we start iteration over our input, we will want to find out what states you can
        // get to by simply following epsilon transitions
        currentStates = getEpsilonClosure(currentStates);

        System.out.print("After preliminary epsilon enclosure, our set has " + Integer.toString(currentStates.size()) + " elements.\n");

        // we will iterate through the input
        while(i < text.length()) {

            // first we will lookup what the character we will be trying to match in this iteration
            // is going to be
            char currentCharacter = text.charAt(i);

            reachableStates = getCharacterEnclosure(currentStates, currentCharacter);

            // if we could not reach any states given our current states, the we can conclude that
            // the input is not in the language defined by our NFA
            if (reachableStates.isEmpty()) {
                System.out.print("At the " + Integer.toString(i + 1) + "th character out of " + Integer.toString(text.length()) + " in " + text + " we found that it is not in the language.\n");
                return false;
            }

            //System.out.print("Before in-loop epsilon enclosure our set had " + Integer.toString(reachableStates.size()) + " elements.\n");
            // if we could reach states from our transition, we will perform epsilon enclosure to
            // ensure that if the next character can transition us to new states, we will have access
            // to them in the next iteration
            currentStates = getEpsilonClosure(reachableStates);

            //System.out.print("After in-loop epsilon enclosure our set has " + Integer.toString(currentStates.size()) + " elements.\n");
            
            // Once we have finished finding reachable states in our NFA, we will increment i for
            // the purposes of looking at the next character in the input
            i++;

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

    // This function will return a set of all states that can be reached by taking 0 or more epsilon
    // transitions from one of the states in our input 
    private HashSet<AutomatonState> getEpsilonClosure(HashSet<AutomatonState> oldStates) {

        // create a new HashSet to contain all the states reachable from our initial state. Since any
        // state can reach itself with 0 epsilon transitions, epsilonReachables will contain everything
        // in oldStates.
        HashSet<AutomatonState> epsilonReachables = new HashSet();

        // turns out you have to do this because simple assignment makes epsilon reachables reference the
        // same object as oldStates
        Iterator<AutomatonState> adder = oldStates.iterator();
        while (adder.hasNext()){
            epsilonReachables.add(adder.next());
        }

        // set up a loop that will iterate until a break condition is met
        while (true) {

            // create an iterable that will loop through all the states in oldStates
            Iterator<AutomatonState> checkStates = oldStates.iterator();

            // cycle through the elements of checkStates
            while (checkStates.hasNext()) {

                // pop the next element off the iterator to be processed
                AutomatonState currentState = checkStates.next();

                // create an iterator of the current state's epsilon transitions to place individually
                // into epsilonReachables
                Iterator<AutomatonState> epsilonStates = currentState.getEpsilonTransitions().iterator();

                while (epsilonStates.hasNext()) {

                    // add the current epsilon reachable state to epsilonReachables
                    epsilonReachables.add(epsilonStates.next());                    
                }

            }

            // If after going through all the checkStates, we have not added any new states to epsilonReachables
            // we have reached epsilon closure for the starting set of states
            if (oldStates.size() == epsilonReachables.size()) {
                break;
            }

            // if we added some amount of new states during this iteration of the loop, we will set the
            // two sets equal for the next iteration of the loop (which will break if it can't add any
            // new states likw we did here)
            Iterator<AutomatonState> setMatcher = epsilonReachables.iterator();
            while (setMatcher.hasNext()){
                oldStates.add(setMatcher.next());
            }
        }

        // once epsilon closure is reached we will return the set of epsilon reachable states
        return epsilonReachables;
    }

    // this function will take a set of states and return all states reachable via by follow a tranisiton
    // from a given character 
    private HashSet<AutomatonState> getCharacterEnclosure(HashSet<AutomatonState> startingStates, char character) {

        // We will make a container for the states the can be reached via character transition. (Note that
        // unlike with epsilon closure, a state cannot necessarily reach itself via a character transition)
        HashSet<AutomatonState> reachableStates = new HashSet();

        // We will create an iterable of our starting states to cycle through to find reachable states
        Iterator<AutomatonState> stateIterator = startingStates.iterator();

        // cycle through the states
        while (stateIterator.hasNext()) {

            AutomatonState checkState = stateIterator.next();

            // get an iterator of the states reachable via character transition from checkstate
            Iterator<AutomatonState> characterIterator = checkState.getTransitions(character).iterator();

            // loop through all states and add them to the reachableStates
            while (characterIterator.hasNext()) {

                // add a state to reachableStates
                reachableStates.add(characterIterator.next());
            }
        }

        // once we have gotten the reachable states from our loop, we will return them (if there are any)
        return reachableStates;
    }
}
