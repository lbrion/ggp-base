//Currently implements Depth-Limited Search with minimax

package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class FaceHunter extends SampleGamer {
	private Random r = new Random();
	private int limit = 3; //Arbitrary int depth limit
	private Map<String, Double> goalCache = new HashMap<String, Double>();

	@Override
	public String getName() {
        return "Face Hunter";
    }

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		System.out.println("Starting");
		StateMachine game = getStateMachine();
        long start = System.currentTimeMillis();
        long finishBy = timeout - 1000;

        List<Move> actions = game.findLegals(getRole(), getCurrentState());
        Move selection = (actions.get(r.nextInt(actions.size())));

        int score = 0;

        for(int i = 0; i < actions.size(); i++) {
        	if (System.currentTimeMillis() > finishBy) break;

        	int result = minscore(getRole(), actions.get(i), getCurrentState(), 0);
        	if(result > score) {
        		score = result;
        		selection = actions.get(i);
        	}
        }
        System.out.println(score);
        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(actions, selection, stop - start));
		return selection;
    }

	public int maxscore(Role role, MachineState state, int level) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException
	{
		//System.out.println("Maxing");
		StateMachine game = getStateMachine();

		if(game.isTerminal(state)) {
			return game.findReward(role, state);
		}

		if(level >= limit)
			return mixedEvalFn(state); //This is where you would return eval function
			//return 0;
			//return evalOurMobility(state, true);
			//return evalGoalProximity(state);
			//return evalOpponentMobility(state);

		int score = 0;

		List<Move> actions = game.findLegals(role, state);

		for(int i = 0; i < actions.size(); i++) {
			int result = minscore(role, actions.get(i), state, level);
			if(result == 100) {
				return 100;
			}
			if(result > score) {
				score = result;
			}
		}

		return score;
	}

	public int minscore(Role role, Move action, MachineState state, int level) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException
	{
		//System.out.println("Mining");

		StateMachine game = getStateMachine();

		Role opponent = null;
		//Get opponent role (only works in dual player)
		for(Role possible: game.findRoles()) {
			if(!possible.equals(getRole())) {
				opponent = possible;
			}
		}

		if(opponent == null) opponent = role;

		List<Move> actions = game.findLegals(opponent, state);

		int score = 100;

		for(int i = 0; i < actions.size(); i++) {
			List<Move> move = new ArrayList<Move>();
			if(role.equals(game.getRoles().get(0))) {
				move.add(action);
				move.add(actions.get(i));
			}
			else {
				move.add(actions.get(i));
				move.add(action);
			}

			MachineState nextState = game.findNext(move, state);

			int result = maxscore(role, nextState, level + 1);

			if(result == 0) return 0;
			if(result < score) score = result;
		}

		return score;
	}

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

        while (true) {
        	MachineState nextState = game.getInitialState();

        	if (System.currentTimeMillis() > timeout - 1000)
        		break;

        	boolean gameFinished = false;
        	while(!gameFinished) {
        		if (System.currentTimeMillis() > timeout - 1000)
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
