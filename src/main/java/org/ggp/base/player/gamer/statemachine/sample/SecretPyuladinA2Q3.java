package org.ggp.base.player.gamer.statemachine.sample;

import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class SecretPyuladinA2Q3 extends SampleGamer {
	private Random r = new Random();

	@Override
	public String getName() {
        return "SecretPyuladinA2Q3";
    }

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		StateMachine thisMachine = getStateMachine();
        long start = System.currentTimeMillis();
        long finishBy = timeout - 1000;

        List<Move> moves = thisMachine.findLegals(getRole(), getCurrentState());
        Move selection = (moves.get(r.nextInt(moves.size())));

        for (int i = 0; i < moves.size(); i++) {
    		if (System.currentTimeMillis() > finishBy)
    			break;

    		//Perform search on move in question, if it has a possible winning move
    		//in its move tree, it will return 1 and the move will be selected.
    		//If it's possible to tie from the moves, it will return 0.
    		//If the move guarantees a loss, it will return -1.
    		int theScore = searchMove(getCurrentState(), moves.get(i));

    		if(theScore == 1) {
    			selection = moves.get(i);
    			System.out.println("We found definite victory");
    			break;
    		}
    		else if(theScore == 0) {
    			selection = moves.get(i);
    			System.out.println("Updated to make best move. Still anyone's game.");
    		}

    		else {
    			System.out.println("We lose to best play.");
    		}
        }

        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
    }

	int searchMove(MachineState theState, Move myMove) {
		StateMachine theMachine = getStateMachine();

		// recursive base case
		if (theMachine.isTerminal(theState)) {
			int reward = 0;

			try {
				reward = theMachine.findReward(getRole(), theState);
			} catch (GoalDefinitionException e) {
		        // should never reach this point - if is terminal then there is reward
				e.printStackTrace();
				return 0;
			}

			if (reward == 100) {
				//System.out.println("We will win!");
				return 1;
			} else if (reward == 0) {
				//System.out.println("We will lose.");
				return -1;
			}

			//System.out.println("It's a tie");
			return 0;
		}

		//TODO: Do the recursion correctly and should work, read comment
		//above.
		try {
			int myMoveScore = 0;
			boolean isDefiniteVictory = true;
			boolean isDefiniteDefeat = false;

			for(List<Move> jointMove: theMachine.getLegalJointMoves(theState, getRole(), myMove)) {
				MachineState nextState = theMachine.findNext(jointMove, theState);

				if (theMachine.isTerminal(nextState)) {
					return searchMove(nextState, null);
				}

				List<Move> moves = theMachine.findLegals(getRole(), nextState);

				boolean victoryInBranch = false;
				boolean tieInBranch = false;

				for (Move nextNextMove : moves) {
					int nextNextMoveScore = searchMove(nextState, nextNextMove);

					if (nextNextMoveScore == 1) {
						victoryInBranch = true;
					} else if (nextNextMoveScore == 0) {
						tieInBranch = true;
					}
				}

				if (!victoryInBranch) {
					isDefiniteVictory = false;

					if (!tieInBranch) {
						isDefiniteDefeat = true;
					}
				}
			}

			if (isDefiniteVictory)
				myMoveScore = 1;

			if (isDefiniteDefeat)
				myMoveScore = -1;

			return myMoveScore;
		} catch (MoveDefinitionException e) {
			e.printStackTrace();
		} catch (TransitionDefinitionException e) {
			e.printStackTrace();
		}

		return -1;
	}

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        // Paladins don't do no metagaming at the beginning of the match.
    }
}
