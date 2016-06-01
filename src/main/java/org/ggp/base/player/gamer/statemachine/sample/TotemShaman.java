//Currently implements MCTS

package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.GamestateNode;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.propnet.SamplePropNetStateMachine;

public class TotemShaman extends SampleGamer {
	private Random r = new Random();
	private long finishBy;
	private List<GamestateNode> allNodes = new ArrayList<GamestateNode>();
	private Map<MachineState, GamestateNode> stateToNode = new HashMap<MachineState, GamestateNode>();

	private boolean isMyTurn;
	private boolean setMovedFirst = false;
	private int nMetaGames = 0;
	private int nDepthChargesTurn = 0;
	private int nDepthChargesGame = 0;

	/* Arbitrary settings that can be modified on-the-go */
	private int nGamesPerSimulation = 6; // currently can't be controlled here
	private long finishByTime = 2000;

	@Override
	public String getName() {
        return "TotemShaman";
    }

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		System.out.println();
		System.out.println("-------- Starting new move -------");
		StateMachine game = getStateMachine();
		SamplePropNetStateMachine propNet = (SamplePropNetStateMachine)((CachedStateMachine) getStateMachine()).getBackedMachine();
        long start = System.currentTimeMillis();
        finishBy = timeout - finishByTime;

        List<Move> actions = game.findLegals(getRole(), getCurrentState());
        int actionSize = actions.size();
        nDepthChargesTurn = 0;

        // keeps track of whose turn it is - for now not used for anything
        // probably doesn't work in simultaneous game or 2+ person game!
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

        // sets all nodes to visit again so that algorithm
        // will go back and run more depth charges
        for (int i = 0; i < allNodes.size(); i++) {
        	allNodes.get(i).markShouldVisit(true);
        }

        MachineState currentState = getCurrentState();
        GamestateNode startNode = null;

        // if we find that our state has already been explored in mcts
        if (stateToNode.containsKey(currentState)) {
        	startNode = stateToNode.get(currentState);
        }

        else {
        	startNode = new GamestateNode(null, currentState);
        	allNodes.add(startNode);
            stateToNode.put(currentState, startNode);
        }

        // main loop to run mcts
        boolean stopSearching = false;
        int count = 0;
        while(true) {
        	if (stopSearching) break;
        	if (System.currentTimeMillis() > finishBy) break;
        	stopSearching = mcts(startNode);
        	count++;
        }

        nDepthChargesGame += nDepthChargesTurn;
        System.out.println("[MCTS] " + count + " iterations");
        System.out.println("[SIMULATED] " + nDepthChargesTurn + " games, [TOTAL] " + nDepthChargesGame);
        System.out.println("[PROPNET CALLS] " + propNet.getTotalCalls() + ", [AVOIDED] " + propNet.getCallsAvoided() +
        		" === " + ((double) propNet.getCallsAvoided() * 100) / propNet.getTotalCalls() + "%");
        System.out.println("[MARKP CALLS] " + propNet.getTotalMarkPropositions() + ", [CACHED] " + propNet.getMarkPropositionsAvoided() +
        		" === " + ((double) propNet.getMarkPropositionsAvoided() * 100) / propNet.getTotalMarkPropositions() + "%");
        double score = 0;

        // find max node over our actions
        for(int i = 0; i < startNode.getChildren().size(); i++) {
        	GamestateNode nextChild = startNode.getChildren().get(i);
        	double utility = nextChild.getUtility() / nextChild.getVisits();

        	System.out.println(nextChild.getPreviousMove(getRole()).toString() + " , " + utility);

        	if (utility > score) {
        		score = utility;
        		selection = nextChild.getPreviousMove(getRole());
        	}
        }

        System.out.println("[Score]: " + score);

        long stop = System.currentTimeMillis();
        System.out.println("Used " + (stop - start) + " milliseconds. ");
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
		int value = simulate(selectedNode, nGamesPerSimulation);

		// back-propagate
		propagate(selectedNode, value);
		selectedNode.markShouldVisit(false);
		return false;
	}

	/* Selects the next GamestateNode to look at.
	 *
	 * Essentially same as algorithm from lecture.
	 */
	public GamestateNode select(GamestateNode stateNode) throws MoveDefinitionException {
		if (stateNode == null) return null;
		//if (stateNode.getVisits() == 0) return stateNode;
		if (stateNode.shouldVisit()) return stateNode;

		for (int i = 0; i < stateNode.getChildren().size(); i++) {
			GamestateNode nextChild = stateNode.getChildren().get(i);
			//if (nextChild.getVisits() == 0) return nextChild;
			if (nextChild.shouldVisit()) return nextChild;
		}

		GamestateNode result = null;
		boolean isMyMove = isOurMove(stateNode);

		// selects a min node if opponent is moving and a
		// max node if we are moving
		// NOTE: not sure if this is even correct, but it seems to work!?!?!
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

		// slightly modified eqn from lecture
		return (stateNode.getUtility() / stateNode.getVisits()) + Math.sqrt(2 * Math.log(parent.getVisits()) / (1 + stateNode.getVisits()));
	}

	/* Expands out node tree from selectedNode. Works if selectedNode is
	 * a partial or complete node.
	 *
	 * Adds children and links them to selectedNode.
	 */
	public void expand(GamestateNode selectedNode) throws MoveDefinitionException, TransitionDefinitionException {
		MachineState selectedState = selectedNode.getState();
		StateMachine game = getStateMachine();

		if (game.isTerminal(selectedState)) {
			return;
		}

		boolean partialNode = !selectedNode.isValidState();

		// behaves differently depending on whether node is a partial move or a full move
		if (partialNode) {
			List<List<Move>> legalMoves = game.getLegalJointMoves(selectedState, getRole(), selectedNode.getPreviousMove(getRole()));

			// if all children have already been added, then no problem
			// NOTE: this could be a problem if a few children are added but not all
			if (legalMoves.size() == selectedNode.getChildren().size())
				return;

			for (int i = 0; i < legalMoves.size(); i++) {
				MachineState nextState = game.findNext(legalMoves.get(i), selectedState);
				GamestateNode nextNode = new GamestateNode(selectedNode, nextState);
				selectedNode.addChild(nextNode);
				allNodes.add(nextNode);
				stateToNode.put(nextState, nextNode);
			}
		} else {
			List<Move> ourLegalMoves = game.getLegalMoves(selectedState, getRole());

			if (ourLegalMoves.size() == selectedNode.getChildren().size())
				return;

			for (int i = 0; i < ourLegalMoves.size(); i++) {
				GamestateNode nextNode = new GamestateNode(selectedNode);
				nextNode.addPreviousMove(getRole(), ourLegalMoves.get(i));
				selectedNode.addChild(nextNode);
				allNodes.add(nextNode);
			}
		}
	}

	/* Simulates [count] number of games from the starting node.
	 *
	 * Starting node can be a partial or complete state.
	 */
	public int simulate(GamestateNode selectedNode, int count) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		MachineState state = selectedNode.getState();
		//System.out.println("[simulate] " + count);

		int total = 0;
		for (int i = 0; i < count; i++) {
			nDepthChargesTurn++;

			if (!selectedNode.isValidState()) {
				Move firstMove = selectedNode.getPreviousMove(getRole());
				total = total + depthcharge(getRole(), firstMove, state);
			} else {
				total = total + depthcharge(getRole(), null, state);
			}

			if (System.currentTimeMillis() > finishBy) {
				return total / (i + 1);
			}
		}

		return total / count;
	}

	/* Runs back up the node tree and sets visits and utility.
	 *
	 * Argument: GamestateNode to start with
	 * 			 utility value found in simulation
	 */
	public void propagate(GamestateNode selectedNode, int value) {
		selectedNode.setVisits(selectedNode.getVisits() + 1);
		selectedNode.setUtility(selectedNode.getUtility() + value);
		if (selectedNode.getParent() != null)
			propagate(selectedNode.getParent(), value);
	}

	/* Simulates playing a game out to completion.
	 *
	 * Arguments: Role of player we want to play as (used to get reward at end)
	 * 			  [OPTIONAL] First move for this player (used in partial states)
	 * 					- if not used, pass in null
	 * 			  MachineState corresponding to start of game
	 */

	public int depthcharge(Role role, Move firstMove, MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		StateMachine game = getStateMachine();

		// returns a neutral value if timed out
    	if (System.currentTimeMillis() > finishBy) {
    		return 50;
    	}

		if(game.isTerminal(state)) {
			return game.findReward(role, state);
		}

		List<Move> moves_to_sim = new ArrayList<Move>();

		for(int i = 0; i < game.getRoles().size(); i++) {
			Role nextRole = game.getRoles().get(i);

			// special case just to allow for partial states to be simulated
			if (nextRole.equals(role) && firstMove != null) {
				moves_to_sim.add(firstMove);
				continue;
			}

			List<Move> options = game.findLegals(nextRole, state);

			// saves time if we only have one option
			if (options.size() == 1) {
				moves_to_sim.add(options.get(0));
				continue;
			}

			boolean foundWinningMove = false;

			// looks to see if any roles have a winning move
			for (int j = 0; j < options.size(); j++) {
				List<List<Move>> possibleFutures = game.getLegalJointMoves(state, nextRole, options.get(j));

				for (int k = 0; k < possibleFutures.size(); k++) {
					MachineState futureState = game.findNext(possibleFutures.get(k), state);

					if (game.isTerminal(futureState)) {
						if (game.findReward(nextRole, futureState) == 100) {
    						moves_to_sim.add(options.get(j));
    						foundWinningMove = true;
    						break;
						}
					} else {
						// this part is for Pentago and other games where a role
						// moves twice in a row
						List<Move> futureOptions = game.findLegals(nextRole, futureState);
						if (futureOptions.size() == 1 && futureOptions.get(0).toString().equals("noop")) {
							continue;
						} else {
							for (int x = 0; x < futureOptions.size(); x++) {
								List<List<Move>> possibleFutureFutures = game.getLegalJointMoves(futureState, nextRole, futureOptions.get(x));

								for (int y = 0; y < possibleFutureFutures.size(); y++) {
									MachineState futureFutureState = game.findNext(possibleFutureFutures.get(y), futureState);

									if (game.isTerminal(futureFutureState)) {
										if (game.findReward(nextRole, futureFutureState) == 100) {
				    						moves_to_sim.add(options.get(j));
				    						foundWinningMove = true;
				    						break;
										}
									}
								}

								if (foundWinningMove) break;
							}
						}

						if (foundWinningMove) break;
					}
				}

				if (foundWinningMove) break;
			}

			// if no role has a winning move, then just play random
			if (!foundWinningMove)
				moves_to_sim.add(options.get(r.nextInt(options.size())));
		}
		MachineState newState = game.findNext(moves_to_sim, state);
		return depthcharge(role, null, newState);
	}

	/* Simulates playing games during metagame period. Counts number of games played
	 * per second which is used while playing the game.
	 */
	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        // clear variables from game to game
		allNodes.clear();
        stateToNode.clear();
        nMetaGames = 0;
        nDepthChargesGame = 0;

		int nGamesPlayed = 0;
        StateMachine game = getStateMachine();
        finishBy = timeout - finishByTime;
        long totalTime = timeout - System.currentTimeMillis();

        while (true) {
        	MachineState state = game.getInitialState();

        	if (System.currentTimeMillis() > finishBy)
        		break;

        	depthcharge(getRole(), null, state);
        	nGamesPlayed++;
        }

        System.out.println("[META] We played: " + nGamesPlayed + " games in " + (totalTime / 1000) + " seconds.");

        // calculates nGames / second
        nMetaGames = nGamesPlayed;
        double gamesPerSecond = ((double) nMetaGames) / totalTime;
        gamesPerSecond *= 1000;

        // arbitrary setting based on nGames / second
        if (gamesPerSecond > 100000) {
        	nGamesPerSimulation = 50000;
        } else if (gamesPerSecond > 1000) {
        	nGamesPerSimulation = 500;
        } else if (gamesPerSecond > 100) {
        	nGamesPerSimulation = 50;
        } else if (gamesPerSecond > 10) {
        	nGamesPerSimulation = 5;
        } else {
        	nGamesPerSimulation = 2;
        }
    }
}
