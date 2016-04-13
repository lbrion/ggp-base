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
    			break;
    		}
    		else if(theScore == 0) {
    			selection = moves.get(i);
    		}
        }

        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
    }

	int searchMove(MachineState theState, Move myMove) {
		StateMachine theMachine = getStateMachine();

		//TODO: Do the recursion correctly and should work, read comment
		//above.
		try {
			for(Move move: theMachine.findLegals(getRole(), theState)) {
				List<Move> nextMove = new List<Move>();
				nextMove.add(move);
				if(theMachine.isTerminal(theMachine.getNextState(theState, nextMove))) {

				}
			}
		} catch (MoveDefinitionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		} catch (TransitionDefinitionException e) {
			// TODO Auto-generated catch block
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
