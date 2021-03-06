package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.ggp.base.util.UnionFind;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Constant;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
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
    private List<Component> ordering;
    /** The player roles */
    private List<Role> roles;
    /** Components organized into disjoint sets of factors */
    private List<Set<Component>> factors;

    private Map<Proposition, Boolean> latches;

    private boolean stateIsCorrect;

    private MachineState lastMachineState; // checks using .equals to see if this was last one marked

    // metrics that I am measuring and printing out in the players
    private int totalCalls;
    private int callsAvoided;
    private int totalMarkPropositions;
    private int markPropositionsAvoided;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
        	latches = new HashMap<Proposition, Boolean>();
            propNet = OptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            //if(roles.size() == 1) ordering = getOrdering(factorPropnet());
            //else ordering = getOrdering(new ArrayList<Component>(propNet.getComponents()));
            //factorPropnet();
            ordering = getOrdering(new ArrayList<Component>(propNet.getComponents()));
            System.out.println(ordering.size());

            //computeLatches();
            //removeLatchesFromOrdering();

            propNet = OptimizingPropNetFactory.create(description);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeLatchesFromOrdering() {
    	for (Proposition p : latches.keySet()) {
    		ordering.remove(p);
    		p.setIsCorrect(true);
    		p.setValue(latches.get(p));
    	}
    }

    public void computeLatches() {
    	System.out.println("Beginning latch computation.");
    	for(Component c : ordering) {
    		if (c instanceof Proposition) {
        		Proposition p = (Proposition) c;

        		if(c.getInputs().size() == 1 && !(c.getSingleInput() instanceof Transition)) {
    	        	isLatch(p);
        		}
    		}
    	}
    	System.out.println("Latch computation complete. There are " + latches.size() + " latches.");
    }

    public void isLatch(Proposition p) {
    	//System.out.println("Checking if " + p + " is a latch.");
    	List<Component> dependencies = new LinkedList<Component>();

    	Queue<Component> toCheck = new LinkedList<Component>();

    	toCheck.add(p);

    	while(toCheck.size() != 0) {
    		Component cur = toCheck.remove();
    		if(cur.getType() == "base" || cur.getType() == "input") {
    			dependencies.add(cur);
    		}
    		else if(cur.getType() == "or") {
    			toCheck.add(cur.getSingleInput());
    		}
    		else {
        		for(Component dependency : cur.getInputs()) {
        			toCheck.add(dependency);
        		}
    		}
    	}

    	boolean true_latch = true;
    	boolean false_latch = true;

    	//Check if true
    	for(int i = 0; i < Math.pow(2, dependencies.size() - 1); i++){
        	clearPropnet();

    		setBooleans(dependencies, i);

    		boolean value = markProposition(p);

    		if(!value) {
    			//System.out.println(p + " is not a true latch.");
    			true_latch = false;
    			break;
    		}
    	}
    	//Check if false
    	for(int i = 0; i < Math.pow(2, dependencies.size() - 1); i++){
        	clearPropnet();

    		setBooleans(dependencies, i);

    		boolean value = markProposition(p);

    		if(value) {
    			//System.out.println(p + " is not a false latch.");
    			false_latch = false;
    			break;
    		}
    	}

    	if(true_latch) {
    		latches.put(p, true);
    		//System.out.println(p + " is a true latch.");
    	}
    	if(false_latch) {
    		latches.put(p, false);
    		//System.out.println(p + " is a false latch.");
    	}

    	//System.out.println("There were " + dependencies.size() + " dependencies for this proposition.");

    	clearPropnet();
    }

    public void setBooleans(List<Component> to_set, int bits) {
    	for(int i = 0; i < to_set.size(); i++){
    		Boolean value;
    		if(((bits >> i) & 1) == 0) value = false;
    		else value = true;

    		to_set.get(i).setVal(value);
    	}
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state) {
    	if (lastMachineState == null || !lastMachineState.equals(state)) {
			clearPropnet();
			markBases(state);
		} else {
			callsAvoided++;
		}
		totalCalls++;

    	Proposition terminalProp = propNet.getTerminalProposition();
    	return markProposition(terminalProp);
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
    	if (lastMachineState == null || !lastMachineState.equals(state)) {
			clearPropnet();
			markBases(state);
		} else {
			callsAvoided++;
		}
		totalCalls++;

    	Set<Proposition> goalProps = propNet.getGoalPropositions().get(role);
    	int goalReward = 0;
    	boolean foundTrue = false;

    	for (Proposition p : goalProps) {
    		if (markProposition(p)) {
    			goalReward = getGoalValue(p);
    			foundTrue = true;
    			break;
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
    	clearPropnet();
    	Proposition initProp = propNet.getInitProposition();
    	initProp.setValue(true);
    	return computeNextState(null);
    }

    /**
     * Computes all possible actions for role.
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
    	System.out.println("Finding actions.");
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
    	if (lastMachineState == null || !lastMachineState.equals(state)) {
			clearPropnet();
			markBases(state);
		} else {
			callsAvoided++;
		}
		totalCalls++;

    	Map<String, Proposition> moveToProp = propNet.getMoveToProp();
    	List<Move> legalMoves = new ArrayList<Move>();

    	Set<Proposition> legalProps = propNet.getLegalPropositions().get(role);
    	for (Proposition p : legalProps) {
    		if (p == null)
    			break;

    		//propNet.setPropAncestorsNotCorrect(p);
    		if (markProposition(p)) {
    			Move m = getMoveFromProposition(p);
    			String concat = role + "|" + m.toString();
        		if (moveToProp.get(concat) != null) {
        			legalMoves.add(m);
        		}
    		}
    	}

        return legalMoves;
    }

    /**
     * Computes the next state given state and the list of moves.
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
    	totalCalls++;
		clearPropnet();
		markBases(state);

    	markActions(moves);
    	return computeNextState(state);

    	/*for (int i = 0; i < ordering.size(); i++) {
    		Component c = ordering.get(i);

    		boolean oldVal = c.getVal();
    		boolean newVal = markProposition(c);
    		c.setVal(newVal);
    		//c.setIsCorrect(true);
    	}

    	// tries to optimize from getStateFromBase() by building from pre-existing contents
    	MachineState nextState = state.clone();
        Set<GdlSentence> contents = nextState.getContents();
    	for (Proposition p : propNet.getBasePropositions().values()) {
    		boolean oldVal = p.getValue();
    		boolean newVal = p.getSingleInput().getValue();
    		p.setValue(newVal);

    		GdlSentence pSentence = p.getName();
    		if (oldVal && !newVal) {
    			contents.remove(pSentence);
    		} else if (!oldVal && newVal) {
    			contents.add(pSentence);
    		}
    	}

        return nextState;*/
    }

    /**
     * Here we check if the terminal component is connected to an
     * 'or' or 'and' component. If it is connected to an 'or' or
     * 'and' component then we do not include those in the disjoint
     * set forest. For 'or' components, we choose the smallest
     * branch to make our new toplogical order. For 'and' components,
     * we choose both branches to be part of our topological order.
     *
     * @return nothing, should update Component list to reflect factored
     * game.
     */
    private List<Component> factorPropnet() {
    	Component terminal = propNet.getTerminalProposition();
    	Component[] propNet_comps = propNet.getComponentsArray();
    	Set<Component> exclude = new HashSet<Component>();
    	Component joint_or_component = null;
    	Component joint_and_component = null;
    	int num_ors = 0;
    	int num_ands = 0;

    	for(Component c : terminal.getInputs()) {
    		if(c.getType() == "or") {
    			//exclude.add(c);
    			joint_or_component = c;
    			num_ors++;
    		}
    		if(c.getType() == "and") {
    			//exclude.add(c);
    			joint_and_component = c;
    			num_ands++;
    		}
    	}
    	for(Component c : propNet_comps) {
    		if(c.getType() == "goal") {
    			exclude.add(c);
    			for(Component dependent : c.getInputs()) {
    				exclude.add(dependent);
    			}
    		}
    	}

    	UnionFind groups = new UnionFind(propNet_comps.length);
    	ArrayList<Component> constant_comps = new ArrayList<Component>();

    	for(int i = 0; i < propNet_comps.length; i++) {
    		Component cur = propNet_comps[i];

    		if(cur.getType() == "constant") {
    			System.out.println("Found a constant");
    			constant_comps.add(cur);
    			for(int j = 0; j < cur.getOutputs().size(); j++) {
    				groups.union(i, Arrays.asList(propNet_comps).indexOf(cur.getOutputs().toArray()[j]));
    			}
    		}
    		if(exclude.contains(cur)) {
    			//pass
    		}
    		else if(cur.getInputs().size() == 1) {
    			groups.union(i, Arrays.asList(propNet_comps).indexOf(cur.getSingleInput()));
    		}
    		else if(cur.getInputs().size() >= 1) {
    			for(int j = 0; j < cur.getInputs().size(); j++) {
    				groups.union(i, Arrays.asList(propNet_comps).indexOf(cur.getInputs().toArray()[j]));
    			}
    		}
    	}

    	List<Component> result = new ArrayList<Component>();

    	int terminal_index = Arrays.asList(propNet_comps).indexOf(terminal);
    	int init_index = Arrays.asList(propNet_comps).indexOf(propNet.getInitProposition());

    	if(num_ands == 1) {
    		for(int j = 0; j < terminal.getInputs().size(); j++) {
				groups.union(terminal_index, Arrays.asList(propNet_comps).indexOf(terminal.getInputs().toArray()[j]));
			}
    	}

    	else if(num_ors == 1) {
    		groups.print_num_groups();

			groups.union(init_index, terminal_index);
    		groups.print_num_groups();
			System.out.println(groups.find(init_index) + " " + groups.find(terminal_index));

			for(int j = 0; j < terminal.getInputs().size(); j++) {
    			if(groups.find(Arrays.asList(propNet_comps).indexOf(terminal.getInputs().toArray()[j])) != groups.find(init_index)) {
    				groups.union(init_index, Arrays.asList(propNet_comps).indexOf(terminal.getInputs().toArray()[j]));
    				break;
    			}
    		}
    	}

    	System.out.println("Excluded: " + exclude);

    	//Adding in constant components
    	for(int i = 0; i < constant_comps.size(); i++) {
    		groups.union(Arrays.asList(propNet_comps).indexOf(constant_comps.get(i)), Arrays.asList(propNet_comps).indexOf(terminal));
    	}

    	for(int i = 0; i < propNet_comps.length; i++) {
    		if(groups.find(i) == groups.find(terminal_index)) {
    			result.add(propNet_comps[i]);
    		}
    	}

    	groups.print_num_groups();

    	System.out.println("Result size before adding excluded: " + result.size());

    	//Adding in excluded
    	for(Component excluded : exclude) {
    		result.add(excluded);
    	}

    	System.out.println("Non Factored: ");

    	for(int i = 0; i < propNet_comps.length; i++) {
    		System.out.print(propNet_comps[i].getType() + ", ");
    	}

    	System.out.println("\nFactored: ");

    	for(int i = 0; i < result.size(); i++) {
    		System.out.print(result.get(i).getType() + ", ");
    	}

    	System.out.println(groups.toString());
    	//propNet.renderToFile("bestbal");
    	propNet = new PropNet(getRoles(), new HashSet<Component>(result));
    	System.out.println("Result size: " + result.size());

    	result = new ArrayList<Component>(new HashSet<Component>(result));
    	System.out.println("Result size: " + result.size());
    	System.out.println("Components size: " + propNet_comps.length);
    	//System.exit(0);
    	return result;
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
    public List<Component> getOrdering(List<Component> components)
    {
        // List to contain the topological ordering.
        List<Component> order = new ArrayList<Component>();

        // All of the propositions in the PropNet.
        List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

        // All of the components in the PropNet.
        //List<Component> components = new ArrayList<Component>(propNet.getComponents());

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

        			order.add(c);
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

    	totalMarkPropositions++;

    	if (c.isCorrect()) {
    		markPropositionsAvoided++;
    		return c.getVal();
    	}

    	if (c instanceof Proposition) {
			if (c.getInputs().size() == 1 && !(c.getSingleInput() instanceof Transition)) {
				return markProposition(c.getSingleInput());
			} else {
				return c.getValue();
			}
		} else if (c instanceof And) {
			for (Component nextComp : c.getInputs()) {
				if (!markProposition(nextComp)) return false;
			}
			return true;
		} else if (c instanceof Or) {
			for (Component nextComp : c.getInputs()) {
				if (markProposition(nextComp)) return true;
			}
			return false;
		} else if (c instanceof Not) {
			return !markProposition(c.getSingleInput());
		} else if (c instanceof Constant) {
			return c.getValue();
		} else if (c instanceof Transition) {
			return markProposition(c.getSingleInput());
		} else {
    		if (c.getInputs().size() == 0) return c.getValue();
    		else return markProposition(c.getSingleInput());
    	}
    }

    private void markBases(MachineState state) {
    	//if (stateIsCorrect)
    	//	return;

    	Set<GdlSentence> stateGDL = state.getContents();
    	Map<GdlSentence, Proposition> baseProps = propNet.getBasePropositions();

    	for (GdlSentence nextGdl : stateGDL) {
    		Proposition p = baseProps.get(nextGdl);
    		p.setValue(true);

    		/*for (Component c : propNet.getPropAncestors(p)) {
    			c.setIsCorrect(false);
    		}*/
    	}

    	lastMachineState = state.clone();
    }

    /* Note: this function looks good for now
     * It uses a new auxillary data structure that I added
     */
    private void markActions(List<Move> actions) {
    	Map<String, Proposition> moveToProp = propNet.getMoveToProp();
    	for(int i = 0; i < actions.size(); i++) {
    		Role nextRole = roles.get(i);
    		Move m = actions.get(i);

    		String concat = nextRole.toString() + "|" + m.toString();
    		if (moveToProp.get(concat) == null) {
    			System.out.println("[markActions] Not found in moveToProp??");
    			continue;
    		}

    		Proposition p = moveToProp.get(concat);
    		p.setValue(true);

    		/*for (Component c : propNet.getPropAncestors(p)) {
    			c.setIsCorrect(false);
    		}*/
    	}
    }

    private void clearPropnet() {
    	for (Proposition p: propNet.getBasePropositions().values()){
			p.setValue(false);
		}
		for (Proposition p: propNet.getInputPropositions().values()){
			p.setValue(false);
		}

		for (Component c : ordering) {
			c.setIsCorrect(false);
		}

		propNet.getInitProposition().setValue(false);
    }

    public boolean stateIsCorrect() {
    	return stateIsCorrect;
    }

    public void setStateCorrect(boolean newValue) {
    	stateIsCorrect = newValue;
    }

    private MachineState computeCurrentState(){
		Set<GdlSentence> contents = new HashSet<GdlSentence>();

		for (GdlSentence s: propNet.getBasePropositions().keySet()){
			if (propNet.getBasePropositions().get(s).getValue()){
				contents.add(s);
			}
		}

		return new MachineState(contents);
	}


	private MachineState computeNextState(MachineState currState) {
		int n = propNet.getBasePropositions().values().size();
		Proposition[] bases = propNet.getPropositionArray();
		boolean[] vals = new boolean[bases.length];

		for (int i = 0; i < ordering.size(); i++) {
    		Component c = ordering.get(i);

    		boolean newVal = markProposition(c);
    		c.setVal(newVal);
    		c.setIsCorrect(true);
    	}

    	for (int i = 0; i < bases.length; i++) {
			vals[i] = markProposition(bases[i].getSingleInput());
		}

    	if (currState == null) {
    		for (int i = 0; i < bases.length; i++) {
    			bases[i].setValue(vals[i]);
    		}
    		return computeCurrentState();
    	}

    	// tries to optimize from getStateFromBase() by building from pre-existing contents
		MachineState nextState = currState.clone();
        Set<GdlSentence> nextContents = nextState.getContents();

		for (int i = 0; i < bases.length; i++) {
			if (vals[i] == bases[i].getValue()) continue;

			bases[i].setValue(vals[i]);
			GdlSentence pSentence = bases[i].getName();
			if (vals[i]) {
				nextContents.add(pSentence);
			} else {
				nextContents.remove(pSentence);
			}
		}

		return nextState;
	}

	public int getTotalCalls() {
		return totalCalls;
	}

	public int getCallsAvoided() {
		return callsAvoided;
	}

	public int getTotalMarkPropositions() {
		return totalMarkPropositions;
	}

	public int getMarkPropositionsAvoided() {
		return markPropositionsAvoided;
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