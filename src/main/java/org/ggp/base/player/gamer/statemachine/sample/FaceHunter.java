package org.ggp.base.player.gamer.statemachine.sample;

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

	@Override
	public String getName() {
        return "";
    }

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		StateMachine thisMachine = getStateMachine();
        long start = System.currentTimeMillis();
        long finishBy = timeout - 1000;

        List<Move> actions = thisMachine.findLegals(getRole(), getCurrentState());
        Move selection = (actions.get(r.nextInt(actions.size())));

        int score = 0;

        for(int i = 0; i < actions.size(); i++) {
        	int result = minscore(getRole(), actions.get(i), getCurrentState());
        }

        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(actions, selection, stop - start));
		return selection;
    }

	public int minscore(Role role, Move action, MachineState state) {


		return 0;
	}

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        // Paladins don't do no metagaming at the beginning of the match.
    }
}
