package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;


@SuppressWarnings("unused")
public class SamplePropNetStateMachine extends StateMachine {
    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
            propNet = OptimizingPropNetFactory.create(description, true);
            OptimizingPropNetFactory.removeAnonymousPropositions(propNet);
            roles = propNet.getRoles();
            ordering = getOrdering();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state) {
    	markBases(state);
        return markProposition(propNet.getTerminalProposition());
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     */
    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
        // TODO: Compute the goal for role in state.
    	Set<Proposition> goalProps = propNet.getGoalPropositions().get(role);
    	int goalReward = 0;
    	boolean foundTrue = false;

    	for (Proposition p : goalProps) {
    		if (p.getValue()) {
    			goalReward = getGoalValue(p);
    			foundTrue = true;
    		}
    	}

    	if (!foundTrue)
    		throw new GoalDefinitionException(state, role);

        return goalReward;
    }

    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     */
    @Override
    public MachineState getInitialState() {
        // TODO: Compute the initial state.
    	System.out.println("Getting initial state");

    	Proposition initProp = propNet.getInitProposition();
    	initProp.setValue(true);
    	propNet.renderToFile("ghi.dot");
    	return getStateFromBase();
    }

    /**
     * Computes all possible actions for role.
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
    	System.out.println("Finding actions.");
        // TODO: Compute legal moves.
    	Set<Proposition> legalProps = propNet.getLegalPropositions().get(role);
    	List<Move> allMoves = new ArrayList<Move>();

    	for (Proposition p : legalProps) {
    		Move m = getMoveFromProposition(p);
    		allMoves.add(m);
    	}

        return allMoves;
    }

    /**
     * Computes the legal moves for role in state.
     */
    @Override
    public List<Move> getLegalMoves(MachineState state, Role role)
            throws MoveDefinitionException {
    	markBases(state);
    	System.out.println("Getting legal moves for " + role.toString());

        // TODO: Compute legal moves.
    	Set<Proposition> legalProps = propNet.getLegalPropositions().get(role);
    	List<Move> legalMoves = new ArrayList<Move>();

    	for (Proposition p : legalProps) {
    		if (markProposition(p)) {
    			Move m = getMoveFromProposition(p);
    			legalMoves.add(m);
    			System.out.print(m + ", ");
    		}
    	}

    	System.out.println();

        return legalMoves;
    }

    /**
     * Computes the next state given state and the list of moves.
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
        // TODO: Compute the next state.
    	// FIXME: We need to do something to state here with moves before we mark bases.
    	markActions(moves);
    	markBases(state);
    	System.out.println("I'm trying to go to the next state");
    	System.out.println("First state:");

        System.out.println(getStateFromBase());
    	for (int i = 0; i < ordering.size(); i++) {
    		Proposition p = ordering.get(i);
    		boolean oldVal = p.getValue();
    		boolean newVal = markProposition(p);
    		p.setValue(newVal);

    		if (oldVal != newVal) {
    			//System.out.println(p.getName() + " changed (" + oldVal + " -> " + newVal + ")");
    		}
    	}

        Set<GdlSentence> contents = state.getContents();
    	for (Proposition p : propNet.getBasePropositions().values()) {
    		boolean oldVal = p.getValue();
    		boolean newVal = markProposition(p.getSingleInput().getSingleInput());
    		p.setValue(newVal);

    		if (oldVal != newVal) {
    			System.out.println();
    			System.out.println(p.getName().toString() + " value = " + oldVal);
    			System.out.println("Now it is: " + newVal);
    			System.out.println(p.getSingleInput().getValue());
    			System.out.println();
    		} else {
    			System.out.println(p.getName() + " is still " + oldVal);
    		}

    		GdlSentence pSentence = p.getName();
    		if (oldVal && !newVal) {
    			contents.remove(pSentence);
    		} else if (!oldVal && newVal) {
    			contents.add(pSentence);
    		}
    	}

    	MachineState nextState = new MachineState(contents);
    	System.out.println("Final state:");
        System.out.println(nextState);
    	return nextState;
    	//System.out.println("Final state:");
        //System.out.println(getStateFromBase());
        //return getStateFromBase();
    }

    /**
     * This should compute the topological ordering of propositions.
     * Each component is either a proposition, logical gate, or transition.
     * Logical gates and transitions only have propositions as inputs.
     *
     * The base propositions and input propositions should always be exempt
     * from this ordering.
     *
     * The base propositions values are set from the MachineState that
     * operations are performed on and the input propositions are set from
     * the Moves that operations are performed on as well (if any).
     *
     * @return The order in which the truth values of propositions need to be set.
     */
    public List<Proposition> getOrdering()
    {
        // List to contain the topological ordering.
        List<Proposition> order = new LinkedList<Proposition>();

        // All of the components in the PropNet
        List<Component> components = new ArrayList<Component>(propNet.getComponents());

        // All of the propositions in the PropNet.
        List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

        // Compute the topological ordering.
        Set<Component> compsAlreadyExamined = new HashSet<Component>();
        List<Component> compsToExamine = new ArrayList<Component>();
        for (Component c : components) {
        	if (c instanceof Proposition) {
        		Proposition p = (Proposition) c;
        		// Exclude base + input propositions, add everything else
	        	if (p.getType().equals("base") || p.getType().equals("input"))
	        		compsAlreadyExamined.add(p);
	        	else
	        		compsToExamine.add(p);
        	} else {
        		compsToExamine.add(c);
        	}
        }

        while(compsToExamine.size() != 0) {
        	System.out.println("Another iteration of ordering!");

        	// add a Component if all its inputs are already part of the ordering
        	// (aka no dependencies)
        	for (int i = 0; i < compsToExamine.size(); i++) {
        		boolean allInputsExamined = true;
        		Component c = compsToExamine.get(i);

        		for (Component cInput : c.getInputs()) {
        			if (!compsAlreadyExamined.contains(cInput)) {
        				allInputsExamined = false;
        				break;
        			}
        		}

        		if (allInputsExamined) {
        			compsAlreadyExamined.add(c);
        			compsToExamine.remove(c);
        			i--;

        			if (c instanceof Proposition) {
        				Proposition p = (Proposition) c;

        				order.add(p);
        			}
        		}
        	}
        }

        return order;
    }

    /* Already implemented for you */
    @Override
    public List<Role> getRoles() {
        return roles;
    }

    /* Helper methods */

    private boolean markProposition(Component c) {
    	String type = c.getType();
    	//System.out.println(type);

    	if (type.equals("input") || type.equals("base") || type.equals("constant")) {
    		return c.getValue();
    	} else if (type.equals("view")) {
    		return markProposition(c.getSingleInput());
    	} else if (type.equals("not")) {
    		return !markProposition(c.getSingleInput());
    	} else if (type.equals("and")) {
    		for (Component nextComp : c.getInputs()) {
    			boolean nextVal = markProposition(nextComp);
    			if (!nextVal) { return false; }
    		}
    		return true;
    	} else if (type.equals("or")) {
    		for (Component nextComp : c.getInputs()) {
    			boolean nextVal = markProposition(nextComp);
    			if (nextVal) { return true; }
    		}
    		return false;
    	} else if (type.equals("legal")) {
    		return markProposition(c.getSingleInput());
    	} else if (type.equals("terminal") || type.equals("goal") ||
    			type.equals("init") || type.equals("does") || type.equals("not set") || type.equals("transition")) {
    		if (c.getInputs().size() == 0) return c.getValue();
    		else return markProposition(c.getSingleInput());
    	} else {
    		System.out.println("[markProposition] Unknown component type: " + c.getType());
    		return c.getValue();
    	}
    }

    private void markBases(MachineState state) {
    	Set<GdlSentence> stateGDL = state.getContents();
    	System.out.println("Calling markBases();");

    	//System.out.println("State GDL:");
    	for(GdlSentence x : stateGDL) {
    		//System.out.println(x);
    	}

    	Map<GdlSentence, Proposition> baseProps = propNet.getBasePropositions();
    	//System.out.println("All GDL:");

    	for (GdlSentence gdl : baseProps.keySet()) {
    		//System.out.println(gdl);
    		if (stateGDL.contains(gdl)) {
    			baseProps.get(gdl).setValue(true);
    		} else {
    			baseProps.get(gdl).setValue(false);
    		}
    	}
    }

    /* Note: this function looks good for now
     * It uses a new auxillary data structure that I added
     */
    private void markActions(List<Move> actions) {
    	Map<GdlSentence, Proposition> baseProps = propNet.getInputPropositions();

    	for(Proposition p : baseProps.values()) {
    		p.setValue(false);
    	}

    	List<GdlSentence> actionSentences = toDoes(actions);
    	for (int i = 0; i < actionSentences.size(); i++) {
    		baseProps.get(actionSentences.get(i)).setValue(true);
    	}

    	/*for(int i = 0; i < actions.size(); i++) {
    		Role nextRole = roles.get(i);
    		Move m = actions.get(i);

    		String concat = nextRole.toString() + "|" + m.toString();
    		if (baseProps.get(concat) == null) {
    			System.out.println("[markActions] Not found in moveToProp??");
    			continue;
    		}

    		baseProps.get(concat).setValue(true);
    	}*/
    }

    private void clearPropnet() {
    	for (Proposition p : propNet.getBasePropositions().values()) {
    		p.setValue(false);
    	}
    }


    /**
     * The Input propositions are indexed by (does ?player ?action).
     *
     * This translates a list of Moves (backed by a sentence that is simply ?action)
     * into GdlSentences that can be used to get Propositions from inputPropositions.
     * and accordingly set their values etc.  This is a naive implementation when coupled with
     * setting input values, feel free to change this for a more efficient implementation.
     *
     * @param moves
     * @return
     */
    private List<GdlSentence> toDoes(List<Move> moves)
    {
        List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
        Map<Role, Integer> roleIndices = getRoleIndices();

        for (int i = 0; i < roles.size(); i++)
        {
            int index = roleIndices.get(roles.get(i));
            doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
        }
        return doeses;
    }

    /**
     * Takes in a Legal Proposition and returns the appropriate corresponding Move
     * @param p
     * @return a PropNetMove
     */
    public static Move getMoveFromProposition(Proposition p)
    {
        return new Move(p.getName().get(1));
    }

    /**
     * Helper method for parsing the value of a goal proposition
     * @param goalProposition
     * @return the integer value of the goal proposition
     */
    private int getGoalValue(Proposition goalProposition)
    {
        GdlRelation relation = (GdlRelation) goalProposition.getName();
        GdlConstant constant = (GdlConstant) relation.get(1);
        return Integer.parseInt(constant.toString());
    }

    /**
     * A Naive implementation that computes a PropNetMachineState
     * from the true BasePropositions.  This is correct but slower than more advanced implementations
     * You need not use this method!
     * @return PropNetMachineState
     */
    public MachineState getStateFromBase()
    {
        Set<GdlSentence> contents = new HashSet<GdlSentence>();
        for (Proposition p : propNet.getBasePropositions().values())
        {
            p.setValue(p.getSingleInput().getValue());
            if (p.getValue())
            {
                contents.add(p.getName());
            }

        }
        return new MachineState(contents);
    }
}