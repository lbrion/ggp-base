package org.ggp.base.player.gamer.statemachine.sample;

import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class SecretPyuladinGamer extends SampleGamer {
	private Random r = new Random();

	@Override
	public String getName() {
        return "Secret Pyuladin";
    }

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		StateMachine thisMachine = getStateMachine();
        long start = System.currentTimeMillis();
        long finishBy = timeout - 1000;

        List<Move> moves = thisMachine.findLegals(getRole(), getCurrentState());
        Move selection = (moves.get(r.nextInt(moves.size())));

        for (Move consideredMove : moves) {
        	//MachineState consideredState = thisMachine.findNext(consideredMove, getCurrentState());
        }

        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
    }

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        // Paladins don't do no metagaming at the beginning of the match.
    }
}
