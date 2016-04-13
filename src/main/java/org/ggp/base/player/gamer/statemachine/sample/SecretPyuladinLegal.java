package org.ggp.base.player.gamer.statemachine.sample;

import java.util.List;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class SecretPyuladinLegal extends SampleGamer {

	@Override
	public String getName() {
		return "Secret Pyuladin Legal";
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		StateMachine thisMachine = getStateMachine();
		long start = System.currentTimeMillis();
		long finishBy = timeout - 1000;

		List<Move> moves = thisMachine.findLegals(getRole(),  getCurrentState());
		Move selection = (moves.get(0));

		long stop = System.currentTimeMillis();
		notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
	}


}
