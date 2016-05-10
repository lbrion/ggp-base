//Currently implements Depth-Limited Search with minimax

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

public class MiracleRogue extends SampleGamer {
	private Random r = new Random();

	@Override
	public String getName() {
        return "Miracle Rogue";
    }

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		StateMachine game = getStateMachine();
        long start = System.currentTimeMillis();
        //long finishBy = timeout - 1000;

        List<Move> actions = game.findLegals(getRole(), getCurrentState());
        System.out.println(actions);

        Move selection = (actions.get(r.nextInt(actions.size())));

        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(actions, selection, stop - start));
		return selection;
    }

	/* Creates a propnet
	 */
	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        //int nGamesPlayed = 0;
        StateMachine game = getStateMachine();
        MachineState initState = game.getInitialState();
    }
}
