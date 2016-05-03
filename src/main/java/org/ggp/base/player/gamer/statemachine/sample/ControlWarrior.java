//Currently implements MCTS

package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.GamestateNode;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class ControlWarrior extends SampleGamer {
	private Random r = new Random();
	private Map<String, Double> goalCache = new HashMap<String, Double>();
	private ConcurrentMap<MachineState, Integer> nodeVisits = new ConcurrentHashMap<MachineState, Integer>();
	private ConcurrentMap<MachineState, Integer> nodeUtility = new ConcurrentHashMap<MachineState, Integer>();
	private ConcurrentMap<MachineState, MachineState> nodeParent = new ConcurrentHashMap<MachineState, MachineState>();
	private long finishBy;
	private List<GamestateNode> allNodes = new ArrayList<GamestateNode>();
	private Map<MachineState, GamestateNode> stateToNode = new HashMap<MachineState, GamestateNode>();

	private boolean isMyTurn;
	private boolean setMovedFirst = false;

	/* Arbitrary settings that can be modified on-the-go */
	private int limit = 5;
	private long finishByTime = 3000;

	@Override
	public String getName() {
        return "ControlWarrior";
    }

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		System.out.println("-------- Starting new move -------");
		StateMachine game = getStateMachine();
        long start = System.currentTimeMillis();
        finishBy = timeout - finishByTime;

        List<Move> actions = game.findLegals(getRole(), getCurrentState());
        int actionSize = actions.size();

        if (setMovedFirst) isMyTurn = !isMyTurn;

        if (actionSize == 1 && actions.get(0).toString().equals("noop")) {
        	isMyTurn = false;
        	setMovedFirst = true;
        } else {
        	isMyTurn = true;
        	setMovedFirst = true;
        }

        if (isMyTurn)
        	System.out.println("My move!");
        else
        	System.out.println("Their move!");

        Move selection = (actions.get(r.nextInt(actionSize)));

        MachineState currentState = getCurrentState();
        GamestateNode startNode = null;

        if (stateToNode.containsKey(currentState)) {
        	startNode = stateToNode.get(currentState);
        }

        else {
        	startNode = new GamestateNode(null, currentState);
        	allNodes.add(startNode);
            stateToNode.put(currentState, startNode);
        }

        boolean stopSearching = false;
        int count = 0;
        while(true) {
        	if (stopSearching) break;
        	if (System.currentTimeMillis() > finishBy) break;
        	stopSearching = mcts(startNode);
        	count++;
        }

        System.out.println("[MCTS] " + count + " iterations");
        double score = 0;

        for(int i = 0; i < startNode.getChildren().size(); i++) {
        	GamestateNode nextChild = startNode.getChildren().get(i);
        	double utility = nextChild.getUtility() / nextChild.getVisits();

        	System.out.println(nextChild.getPreviousMove(getRole()).toString() + " , " + utility);

        	if (utility > score) {
        		score = utility;
        		selection = nextChild.getPreviousMove(getRole());
        	}
        }

        // evaluates which action has highest utility
        for(int i = 0; i < actions.size(); i++) {
        	/*List<List<Move>> nextMoves = game.getLegalJointMoves(currentState, getRole(), actions.get(i));

        	int minimum = Integer.MAX_VALUE;
        	for (int j = 0; j < nextMoves.size(); j++) {
        		MachineState nextState = game.findNext(nextMoves.get(j), currentState);
        		int nextStateUtility = nodeUtility.get(nextState);

                if (nextStateUtility < minimum) minimum = nextStateUtility;
        	}

        	if (minimum > score) {
        		score = minimum;
        		selection = actions.get(i);
        	}*/

        	/*List<Move> only_my_move = new ArrayList<Move>();
        	for(int j = 0; j < game.getRoles().size(); j++) {
        		if (game.getRoles().get(j).equals(getRole()))
        			only_my_move.add(actions.get(i));
        		else
        			only_my_move.add(null);
    		}

        	MachineState nextState = game.findNext(only_my_move, currentState);

        	List<Move> t = game.findLegals(getRole(), nextState);
        	System.out.println(t);

        	int nextUtil = nodeUtility.get(nextState);

        	if (nextUtil > score) {
        		score = nextUtil;
        		selection = actions.get(i);
        	}*/
        }

        System.out.println("[Score]: " + score);
        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(actions, selection, stop - start));
		return selection;
    }

	public boolean mcts(GamestateNode stateNode) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		//System.out.println("Starting mcts! " + allNodes.size());

		// selection
		GamestateNode selectedNode = select(stateNode);

		if (selectedNode == null) return true;

		// expansion
		expand(selectedNode);

		// simulate
		int value = montecarlo(getRole(), selectedNode.getState(), 9);

		// back-propagate
		propagate(selectedNode, value);
		return false;
	}

	public GamestateNode select(GamestateNode stateNode) throws MoveDefinitionException {
		if (stateNode == null) return null;
		if (stateNode.getVisits() == 0 && stateNode.isValidState()) return stateNode;

		for (int i = 0; i < stateNode.getChildren().size(); i++) {
			GamestateNode nextChild = stateNode.getChildren().get(i);
			if (nextChild.getVisits() == 0 && nextChild.isValidState()) return nextChild;
			else if (nextChild.getVisits() == 0) return select(nextChild);
		}

		GamestateNode result = null;
		boolean isMyMove = isOurMove(stateNode);

		double score = Integer.MAX_VALUE;
		if (isMyMove)
			score = Integer.MIN_VALUE;

		for (int i = 0; i < stateNode.getChildren().size(); i++) {
			GamestateNode nextChild = stateNode.getChildren().get(i);
			double nextChildScore = selectFn(nextChild);

			if (isMyMove && nextChildScore > score) {
				score = nextChildScore;
				result = nextChild;
			}

			if (!isMyMove && nextChildScore < score) {
				score = nextChildScore;
				result = nextChild;
			}
		}
		return select(result);
	}

	// does not work in simultaneous games!
	public boolean isOurMove(GamestateNode stateNode) throws MoveDefinitionException {
		StateMachine game = getStateMachine();

		if (stateNode.isValidState()) {
			if (game.isTerminal(stateNode.getState())) {
				return false;
			}

			List<Move> legalMoves = game.getLegalMoves(stateNode.getState(), getRole());
			if (legalMoves.size() == 1 && legalMoves.get(0).toString().equals("noop")) {
				return false;
			}
		} else {
			if (stateNode.getPreviousMove(getRole()).toString().equals("noop")) {
				return false;
			}
		}

		return true;
	}

	public double selectFn(GamestateNode stateNode) {
		if (stateNode == null) {
			System.out.println("[Error] Ran selectFn on a null state");
			return 0;
		} else if (stateNode.getParent() == null) {
			System.out.println("[Error] Parent is a null state");
			return 0;
		}

		GamestateNode parent = stateNode.getParent();
		return stateNode.getUtility() + Math.sqrt(2 * Math.log(parent.getVisits()) / (1 + stateNode.getVisits()));
	}

	public void expand(GamestateNode selectedNode) throws MoveDefinitionException, TransitionDefinitionException {
		// occurs if a node is selected that doesn't have a MachineState
		if (!selectedNode.isValidState()) {
			System.out.println("[ERROR] Didn't filter out non-valid states in expand()");
			return;
		}

		MachineState selectedState = selectedNode.getState();
		StateMachine game = getStateMachine();

		if (game.isTerminal(selectedState)) {
			return;
		}

		List<Move> ourLegalMoves = game.getLegalMoves(selectedState, getRole());

		for (int i = 0; i < ourLegalMoves.size(); i++) {
			GamestateNode newNode = new GamestateNode(selectedNode);
			newNode.addPreviousMove(getRole(), ourLegalMoves.get(i));
			selectedNode.addChild(newNode);
			allNodes.add(newNode);

			List<List<Move>> jointLegalMoves = game.getLegalJointMoves(selectedState, getRole(), ourLegalMoves.get(i));
			for (int j = 0; j < jointLegalMoves.size(); j++) {
				List<Move> nextJointMove = jointLegalMoves.get(j);
				MachineState nextState = game.findNext(nextJointMove, selectedState);
				GamestateNode nextNewNode = new GamestateNode(newNode, nextState);
				newNode.addChild(nextNewNode);
				allNodes.add(nextNewNode);
				stateToNode.put(nextState, nextNewNode);
			}
		}
	}

	public void propagate(GamestateNode selectedNode, int value) {
		selectedNode.setVisits(selectedNode.getVisits() + 1);
		selectedNode.setUtility(selectedNode.getUtility() + value);
		if (selectedNode.getParent() != null)
			propagate(selectedNode.getParent(), value);
	}

	public int montecarlo(Role role, MachineState state, int count) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		int total = 0;
		for(int i = 0; i < count; i++) {
			total = total + depthcharge(role, state);
		}
		return total / count;
	}

	public int depthcharge(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		StateMachine game = getStateMachine();
    	if (System.currentTimeMillis() > finishBy) {
    		return mixedEvalFn(state);
    	}

		if(game.isTerminal(state)) {
			return game.findReward(role, state);
		}

		List<Move> moves_to_sim = new ArrayList<Move>();

		for(int i = 0; i < game.getRoles().size(); i++) {
			List<Move> options = game.findLegals(game.getRoles().get(i), state);
			moves_to_sim.add(options.get(r.nextInt(options.size())));
		}
		MachineState newState = game.findNext(moves_to_sim, state);
		return depthcharge(role, newState);
	}

	/*public double selectFn(MachineState state) {
		if (state == null) {
			System.out.println("[Error] Ran selectFn on a null state");
			return 0;
		}

		MachineState parentNode = nodeParent.get(state);
		if (parentNode == null) {
			return 0;
		}

		return nodeUtility.get(state) + Math.sqrt(2 * Math.log(nodeVisits.get(parentNode)) / nodeVisits.get(state));
	}*/

	public int mixedEvalFn(MachineState state) {
		// scores should be from 0 to 100
		List<Integer> scores = new ArrayList<Integer>();
		scores.add(evalOurMobility(state, true));
		scores.add(evalOpponentMobility(state));
		scores.add(evalGoalProximity(state));

		// weights should add up to 1
		List<Double> weights = new ArrayList<Double>();
		weights.add(0.25);
		weights.add(0.25);
		weights.add(0.5);

		double compositeScore = 0;
		for (int i = 0; i < scores.size(); i++) {
			compositeScore += scores.get(i) * weights.get(i);
		}

		return (int)compositeScore;
	}

	/* Heuristic function to evaluate a state based on our player's mobility.
	 *
	 * arguments : state that we are evaluating currently,
	 *             whether or not we favor mobility (true) or focus (false)
	 */
	public int evalOurMobility(MachineState state, boolean favorMobility) {
		StateMachine machine = getStateMachine();
		int nLegalMoves, nMoves;

		try {
			nLegalMoves = machine.getLegalMoves(state, getRole()).size();
			nMoves = machine.findActions(getRole()).size();
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}

		double percentageMobility = 0;

		if (nMoves != 0)
			percentageMobility = ((double) nLegalMoves) / nMoves * 100;

		if (!favorMobility)
			percentageMobility = 100 - percentageMobility;

		return (int)percentageMobility;
	}

	/* Heuristic function to evaluate a state based on our opponent's mobility (less choices = better).
	 * Should work for more than 2 player games as well (not tested).
	 *
	 * arguments: state that we are evaluating currently,
	 */
	public int evalOpponentMobility(MachineState state) {
		StateMachine machine = getStateMachine();
		int nLegalMoves = 0;
		int nMoves = 0;

		for (Role r : machine.findRoles()) {
			if (r.equals(getRole())) continue;

			try {
				nLegalMoves += machine.getLegalMoves(state, r).size();
				nMoves += machine.findActions(r).size();
			} catch (Exception e) {
				e.printStackTrace();
				return -1;
			}
		}

		double percentageMobility = 0;

		if (nMoves != 0)
			percentageMobility = 100 - (((double) nLegalMoves) / nMoves * 100);

		return (int)percentageMobility;
	}

	/* Heuristic function to evaluate a state based on the GDL constraints.
	 * Compares state to terminal states found in meta gaming.
	 *
	 * arguments: state that we are evaluating currently.
	 */
	public int evalGoalProximity(MachineState state) {
		StateMachine game = getStateMachine();
		// if no meta gaming was done or if not enough time was given in the metagame
		if (goalCache.size() == 0) {
			System.out.println("Cache was empty");
			return 0;
		}

		Set<GdlSentence> currGdl = state.getContents();
		double score = 0;

		for (GdlSentence g : currGdl) {
			List<GdlTerm> gdlStatements = g.getBody();
			if (gdlStatements.size() < 1) continue;
			String statement = gdlStatements.get(0).toString();

			if (!goalCache.containsKey(statement)) {
				continue;
			}

			score += goalCache.get(statement);
		}

		score = (score / currGdl.size()) * 100;
		//System.out.println("Score: " + score);
		//System.out.println("-------------------------");

		try {
			score += game.getGoal(state, getRole());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (int)score;
	}

	/* Simulates playing games during metagame period. When we reach a terminal state,
	 * we look at the GDL statements and record it in the class variable goalCache.
	 *
	 * This is used in evalGoalProximity() as a heuristic.
	 */
	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        int nGamesPlayed = 0;
        StateMachine game = getStateMachine();
        finishBy = timeout - finishByTime;

        if(game.getRoles().size() > 1) {
        	limit = 2;
        }
        else limit = 5;

        while (true) {
        	MachineState nextState = game.getInitialState();

        	if (System.currentTimeMillis() > finishBy)
        		break;

        	boolean gameFinished = false;
        	while(!gameFinished) {
        		if (System.currentTimeMillis() > finishBy)
            		gameFinished = true;

        		if (game.isTerminal(nextState)) {
        			// reached end of simulated game - time to update goalCache
        			gameFinished = true;
        			nGamesPlayed++;
        			if (game.findReward(getRole(), nextState) == 0) break;

        			Set<GdlSentence> gdl = nextState.getContents();
        			for (GdlSentence g : gdl) {
        				List<GdlTerm> gdlStatements = g.getBody();
        				if (gdlStatements.size() < 1) continue;
        				String statement = gdlStatements.get(0).toString();

        				if (!goalCache.containsKey(statement))
        					goalCache.put(statement, 0.0);

        				goalCache.put(statement, goalCache.get(statement) + 1);
        			}

        			break;
        		}

        		List<MachineState> possibleNextStates = game.getNextStates(nextState);
        		nextState = (possibleNextStates.get(r.nextInt(possibleNextStates.size())));
        	}
        }

        System.out.println("[META] We played: " + nGamesPlayed + " games.");

        if (nGamesPlayed != 0) {
        	for (String s : goalCache.keySet()) {
        		double currentValue = goalCache.get(s);
        		goalCache.put(s, currentValue / nGamesPlayed);
        	}
        }
    }
}
