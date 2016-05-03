package org.ggp.base.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;

public class GamestateNode {
	GamestateNode parent;
	List<GamestateNode> children;
	Map<Role, Move> previousMoves;

	double utility;
	int visits;

	MachineState state;
	boolean isValidState;

	public void init() {
		visits = 0;
		utility = 0;

		children = new ArrayList<GamestateNode>();
		previousMoves = new HashMap<Role, Move>();
	}

	public GamestateNode(GamestateNode parent, MachineState state) {
		init();
		this.parent = parent;
		this.state = state;
		isValidState = true;
	}

	public GamestateNode(GamestateNode parent) {
		init();
		this.parent = parent;
		state = null;
		isValidState = false;
	}

	public void addPreviousMove(Role role, Move move) {
		previousMoves.put(role, move);
	}

	public void addChild(GamestateNode newNode) {
		children.add(newNode);
	}

	public void setUtility(double updatedUtility) {
		utility = updatedUtility;
	}

	public void setVisits(int updatedVisits) {
		visits = updatedVisits;
	}

	public double getUtility() {
		return utility;
	}

	public int getVisits() {
		return visits;
	}

	public GamestateNode getParent() {
		return parent;
	}

	public List<GamestateNode> getChildren() {
		return children;
	}

	public MachineState getState() {
		return state;
	}

	public Map<Role, Move> getPreviousMoves() {
		return previousMoves;
	}

	public Move getPreviousMove(Role role) {
		if (previousMoves.containsKey(role)) {
			return previousMoves.get(role);
		} else {
			return null;
		}
	}

	public boolean isValidState() {
		return isValidState;
	}
}
