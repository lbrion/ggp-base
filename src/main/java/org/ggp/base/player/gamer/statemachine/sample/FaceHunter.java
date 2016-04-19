//Currently implements Depth-Limited Search with minimax

package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
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
        	//if (System.currentTimeMillis() > finishBy) break;

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
		System.out.println("Maxing");
		StateMachine game = getStateMachine();

		if(game.isTerminal(state)) {
			return game.findReward(role, state);
		}

		if(level >= limit) return 0; //This is where you would return eval function

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
		System.out.println("Mining");

		StateMachine game = getStateMachine();

		Role opponent = null;
		//Get opponent role (only works in dual player)
		for(Role possible: game.findRoles()) {
			if(!possible.equals(getRole())) {
				opponent = possible;
			}
		}

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

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        // Paladins don't do no metagaming at the beginning of the match.
    }
}
