package org.ggp.base.util.propnet.architecture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.gdl.grammar.GdlProposition;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Constant;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.statemachine.Role;


/**
 * The PropNet class is designed to represent Propositional Networks.
 *
 * A propositional network (also known as a "propnet") is a way of representing
 * a game as a logic circuit. States of the game are represented by assignments
 * of TRUE or FALSE to "base" propositions, each of which represents a single
 * fact that can be true about the state of the game. For example, in a game of
 * Tic-Tac-Toe, the fact (cell 1 1 x) indicates that the cell (1,1) has an 'x'
 * in it. That fact would correspond to a base proposition, which would be set
 * to TRUE to indicate that the fact is true in the current state of the game.
 * Likewise, the base corresponding to the fact (cell 1 1 o) would be false,
 * because in that state of the game there isn't an 'o' in the cell (1,1).
 *
 * A state of the game is uniquely determined by the assignment of truth values
 * to the base propositions in the propositional network. Every assignment of
 * truth values to base propositions corresponds to exactly one unique state of
 * the game.
 *
 * Given the values of the base propositions, you can use the connections in
 * the network (AND gates, OR gates, NOT gates) to determine the truth values
 * of other propositions. For example, you can determine whether the terminal
 * proposition is true: if that proposition is true, the game is over when it
 * reaches this state. Otherwise, if it is false, the game isn't over. You can
 * also determine the value of the goal propositions, which represent facts
 * like (goal xplayer 100). If that proposition is true, then that fact is true
 * in this state of the game, which means that xplayer has 100 points.
 *
 * You can also use a propositional network to determine the next state of the
 * game, given the current state and the moves for each player. First, you set
 * the input propositions which correspond to each move to TRUE. Once that has
 * been done, you can determine the truth value of the transitions. Each base
 * proposition has a "transition" component going into it. This transition has
 * the truth value that its base will take on in the next state of the game.
 *
 * For further information about propositional networks, see:
 *
 * "Decomposition of Games for Efficient Reasoning" by Eric Schkufza.
 * "Factoring General Games using Propositional Automata" by Evan Cox et al.
 *
 * @author Sam Schreiber
 */

public final class PropNet
{
    /** References to every component in the PropNet. */
    private final Set<Component> components;
    private Component[] componentsArray;

    /** References to every Proposition in the PropNet. */
    private final List<Proposition> propositions;
    Proposition[] propositionArray;

    //private final Map<GdlSentence, Proposition> viewPropositions;
    /** References to every BaseProposition in the PropNet, indexed by name. */
    private final Map<GdlSentence, Proposition> basePropositions;

    /** References to every InputProposition in the PropNet, indexed by name. */
    private final Map<GdlSentence, Proposition> inputPropositions;

    // Pyul's own inputProp structure
    private Map<String, Proposition> moveToProp;

    /** References to every LegalProposition in the PropNet, indexed by role. */
    private final Map<Role, Set<Proposition>> legalPropositions;
    Proposition[][] legalByRole;

    /** References to every GoalProposition in the PropNet, indexed by role. */
    private final Map<Role, Set<Proposition>> goalPropositions;

    /** A reference to the single, unique, InitProposition. */
    private final Proposition initProposition;

    /** A reference to the single, unique, TerminalProposition. */
    private final Proposition terminalProposition;

    /** A helper mapping between input/legal propositions. */
    private final Map<Proposition, Proposition> legalInputMap;
    private Map<Proposition, Set<Component>> propToAncestors;

    /** A helper list of all of the roles. */
    private final List<Role> roles;

    public void addComponent(Component c)
    {
        components.add(c);
        if (c instanceof Proposition) propositions.add((Proposition)c);
    }

    /**
     * Creates a new PropNet from a list of Components, along with indices over
     * those components.
     *
     * @param components
     *            A list of Components.
     */
    public PropNet(List<Role> roles, Set<Component> components)
    {
    	this.moveToProp = new HashMap<String, Proposition>();
        this.roles = roles;
        this.components = components;
        this.propositions = recordPropositions();
        this.basePropositions = recordBasePropositions();
        this.inputPropositions = recordInputPropositions();
        this.legalPropositions = recordLegalPropositions();
        this.goalPropositions = recordGoalPropositions();
        this.initProposition = recordInitProposition();
        this.terminalProposition = recordTerminalProposition();
        this.legalInputMap = makeLegalInputMap();
        //this.viewPropositions = recordViewPropositions();
        recordAndOrNot();
        fillArrays();
        findAllPropAncestors();
    }

    public List<Role> getRoles()
    {
        return roles;
    }

    public Map<Proposition, Proposition> getLegalInputMap()
    {
        return legalInputMap;
    }

    private Map<Proposition, Proposition> makeLegalInputMap() {
        Map<Proposition, Proposition> legalInputMap = new HashMap<Proposition, Proposition>();
        // Create a mapping from Body->Input.
        Map<List<GdlTerm>, Proposition> inputPropsByBody = new HashMap<List<GdlTerm>, Proposition>();
        for(Proposition inputProp : inputPropositions.values()) {
            List<GdlTerm> inputPropBody = (inputProp.getName()).getBody();
            inputPropsByBody.put(inputPropBody, inputProp);
        }
        // Use that mapping to map Input->Legal and Legal->Input
        // based on having the same Body proposition.
        for(Set<Proposition> legalProps : legalPropositions.values()) {
            for(Proposition legalProp : legalProps) {
                List<GdlTerm> legalPropBody = (legalProp.getName()).getBody();
                if (inputPropsByBody.containsKey(legalPropBody)) {
                    Proposition inputProp = inputPropsByBody.get(legalPropBody);
                    legalInputMap.put(inputProp, legalProp);
                    legalInputMap.put(legalProp, inputProp);
                }
            }
        }
        return legalInputMap;
    }

    /**
     * Getter method.
     *
     * @return References to every BaseProposition in the PropNet, indexed by
     *         name.
     */
    public Map<GdlSentence, Proposition> getBasePropositions()
    {
        return basePropositions;
    }

    /**
     * Getter method.
     *
     * @return References to every Component in the PropNet.
     */
    public Set<Component> getComponents()
    {
        return components;
    }

    /**
     * Getter method.
     *
     * @return References to every GoalProposition in the PropNet, indexed by
     *         player name.
     */
    public Map<Role, Set<Proposition>> getGoalPropositions()
    {
        return goalPropositions;
    }

    /**
     * Getter method. A reference to the single, unique, InitProposition.
     *
     * @return
     */
    public Proposition getInitProposition()
    {
        return initProposition;
    }

    /**
     * Getter method.
     *
     * @return References to every InputProposition in the PropNet, indexed by
     *         name.
     */
    public Map<GdlSentence, Proposition> getInputPropositions()
    {
        return inputPropositions;
    }

    public Map<String, Proposition> getMoveToProp()
    {
    	return moveToProp;
    }

    public void setMoveToProp(Map<String, Proposition> newMoveToProp)
    {
    	moveToProp = newMoveToProp;
    }

    /**
     * Getter method.
     *
     * @return References to every LegalProposition in the PropNet, indexed by
     *         player name.
     */
    public Map<Role, Set<Proposition>> getLegalPropositions()
    {
        return legalPropositions;
    }

    /**
     * Getter method.
     *
     * @return References to every Proposition in the PropNet.
     */
    public List<Proposition> getPropositions()
    {
        return propositions;
    }

    /**
     * Getter method.
     *
     * @return A reference to the single, unique, TerminalProposition.
     */
    public Proposition getTerminalProposition()
    {
        return terminalProposition;
    }

    /**
     * Returns a representation of the PropNet in .dot format.
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("digraph propNet\n{\n");
        for ( Component component : components )
        {
            sb.append("\t" + component.toString() + "\n");
        }
        sb.append("}");

        return sb.toString();
    }

    /**
     * Outputs the propnet in .dot format to a particular file.
     * This can be viewed with tools like Graphviz and ZGRViewer.
     *
     * @param filename the name of the file to output to
     */
    public void renderToFile(String filename) {
        try {
            File f = new File(filename);
            FileOutputStream fos = new FileOutputStream(f);
            OutputStreamWriter fout = new OutputStreamWriter(fos, "UTF-8");
            fout.write(toString());
            fout.close();
            fos.close();
        } catch(Exception e) {
            GamerLogger.logStackTrace("StateMachine", e);
        }
    }

    // and/or/not/constant is marked as a type
    private void recordAndOrNot() {
    	for (Component c : components) {
    		if (c instanceof And) {
    			c.setType("and");
    		} else if (c instanceof Or) {
    			c.setType("or");
    		} else if (c instanceof Not) {
    			c.setType("not");
    		} else if (c instanceof Constant) {
    			c.setType("constant");
    		}
    	}
    }

    private void fillArrays() {
    	fillComponentsArray();
    	fillLegalArray();
    	fillPropositionArray();
    }

    private void fillComponentsArray() {
    	componentsArray = new Component[components.size()];
    	int index = 0;
    	for (Component c : components) {
    		componentsArray[index] = c;
    		index++;
    	}
    }

    private void fillLegalArray() {
    	int largestSize = -1;
    	for (Role r : legalPropositions.keySet()) {
    		int size = legalPropositions.get(r).size();
    		if (size > largestSize)
    			largestSize = size;
    	}

    	legalByRole = new Proposition[roles.size()][largestSize];
    	for (int i = 0; i < roles.size(); i++) {
    		Role nextRole = roles.get(i);
    		for (int j = 0; j < legalByRole[i].length; j++) {
    			legalByRole[i][j] = null;
    		}

    		Set<Proposition> nextLegal = legalPropositions.get(nextRole);
    		int j = 0;
    		for (Proposition p : nextLegal) {
    			legalByRole[i][j] = p;
    			j++;
    		}
    	}
    }

    private void fillPropositionArray() {
    	propositionArray = new Proposition[propositions.size()];
    	for (int i = 0; i < propositions.size(); i++) {
    		Proposition p = propositions.get(i);
    		propositionArray[i] = p;
    	}
    }

    public Proposition[] getPropositionArray() {
    	return propositionArray;
    }

    public Proposition[][] getEntireLegalArray() {
    	return legalByRole;
    }

    public Proposition[] getLegalArray(Role r) {
    	int index = 0;
    	for (Role nextRole : roles) {
    		if (nextRole.equals(r))
    			return legalByRole[index];
    		index++;

    	}
    	System.out.println("[getLegalArray] ERROR");
    	return null;
    }

    public Component[] getComponentsArray() {
    	return componentsArray;
    }

    private void findAllPropAncestors() {
    	propToAncestors = new HashMap<Proposition, Set<Component>>();
    	Map<Proposition, Boolean> allRoots = new HashMap<Proposition, Boolean>();

    	// add terminal prop
    	allRoots.put(terminalProposition, false);

    	// add goal props
    	for (Set<Proposition> s : goalPropositions.values()) {
    		for (Proposition p : s) {
    			allRoots.put(p, false);
    		}
    	}

    	// add legal props
    	for (Proposition[] legalForRole : legalByRole) {
    		for (Proposition p : legalForRole) {
    			allRoots.put(p, false);
    		}
    	}

    	// add input props
    	for (Proposition p : moveToProp.values()) {
    		allRoots.put(p, true);
    	}

    	// add base props
    	for (Proposition p : basePropositions.values()) {
    		allRoots.put(p, true);
    	}

    	for (Proposition p : allRoots.keySet()) {
    		Set<Component> allAncestors = findPropAncestors(p, allRoots.get(p));
    		propToAncestors.put(p, allAncestors);
    	}
    }

    public Map<Proposition, Set<Component>> getPropToAncestors() {
    	return propToAncestors;
    }

    public Set<Component> getPropAncestors(Proposition p) {
    	return propToAncestors.get(p);
    }

    public void setPropAncestorsNotCorrect(Proposition p) {
    	Set<Component> ancestors = getPropAncestors(p);
    	for (Component c : ancestors) {
    		c.setIsCorrect(false);
    	}
    }

    // iteratively finds a component's connected components, sets their isCorrect flags and then
    // finds those components' components
    // @param roots = starting node
    //		  setOutputs = whether we are looking at a components' outputs (false if inputs)
    private Set<Component> findPropAncestors(Component root, boolean setOutputs) {
    	Set<Component> donezo = new HashSet<Component>();
    	List<Component> queue = new ArrayList<Component>();

    	donezo.add(root);
    	queue.add(root);

    	return setAncestors(queue, donezo, setOutputs);
    }

    // decomposition method for findPropAncestors()
    private Set<Component> setAncestors(List<Component> queue, Set<Component> donezo, boolean setOutputs) {
    	Set<Component> ancestors = new HashSet<Component>();
    	while(queue.size() > 0) {
    		Component c = queue.get(0);
    		queue.remove(0);

    		//c.setIsCorrect(false);
    		ancestors.add(c);

    		Set<Component> nextComponents;
    		if (setOutputs) nextComponents = c.getOutputs();
    		else nextComponents = c.getInputs();

    		for (Component next : nextComponents) {
    			if (donezo.contains(next))
    				continue;
    			queue.add(next);
    			donezo.add(next);
    		}
    	}

    	return ancestors;
    }

    /* I added this to find view propositions
     *
     */

    private Map<GdlSentence, Proposition> recordViewPropositions() {
    	Map<GdlSentence, Proposition> viewPropositions = new HashMap<GdlSentence, Proposition>();
    	for (Component c : components) {
    		if (c instanceof Proposition) {
    			Proposition p = (Proposition) c;

    			if (p.getType().equals("not set")) {
	    			boolean foundConnective = false;
		    		for (Component inputs : c.getInputs()) {
		    			if (inputs instanceof And || inputs instanceof Or || inputs instanceof Not) {
		    				foundConnective = true;
		    				break;
		    			}
		    		}

		    		if (foundConnective) {
		    			p.setType("view");
		    			viewPropositions.put(p.getName(), p);
		    		}
    			}
    		}
    	}

    	return viewPropositions;
    }

    /**
     * Builds an index over the BasePropositions in the PropNet.
     *
     * This is done by going over every single-input proposition in the network,
     * and seeing whether or not its input is a transition, which would mean that
     * by definition the proposition is a base proposition.
     *
     * @return An index over the BasePropositions in the PropNet.
     */
    private Map<GdlSentence, Proposition> recordBasePropositions()
    {
        Map<GdlSentence, Proposition> basePropositions = new HashMap<GdlSentence, Proposition>();
        for (Proposition proposition : propositions) {
            // Skip all propositions without exactly one input.
            if (proposition.getInputs().size() != 1)
                continue;

            Component component = proposition.getSingleInput();
            if (component instanceof Transition) {
            	proposition.setType("base");
                basePropositions.put(proposition.getName(), proposition);
            }
        }

        return basePropositions;
    }

    /**
     * Builds an index over the GoalPropositions in the PropNet.
     *
     * This is done by going over every function proposition in the network
     * where the name of the function is "goal", and extracting the name of the
     * role associated with that goal proposition, and then using those role
     * names as keys that map to the goal propositions in the index.
     *
     * @return An index over the GoalPropositions in the PropNet.
     */
    private Map<Role, Set<Proposition>> recordGoalPropositions()
    {
        Map<Role, Set<Proposition>> goalPropositions = new HashMap<Role, Set<Proposition>>();
        for (Proposition proposition : propositions)
        {
            // Skip all propositions that aren't GdlRelations.
            if (!(proposition.getName() instanceof GdlRelation))
                continue;

            GdlRelation relation = (GdlRelation) proposition.getName();
            if (!relation.getName().getValue().equals("goal"))
                continue;

            proposition.setType("goal");

            Role theRole = new Role((GdlConstant) relation.get(0));
            if (!goalPropositions.containsKey(theRole)) {
                goalPropositions.put(theRole, new HashSet<Proposition>());
            }
            goalPropositions.get(theRole).add(proposition);
        }

        return goalPropositions;
    }

    /**
     * Returns a reference to the single, unique, InitProposition.
     *
     * @return A reference to the single, unique, InitProposition.
     */
    private Proposition recordInitProposition()
    {
        for (Proposition proposition : propositions)
        {
            // Skip all propositions that aren't GdlPropositions.
            if (!(proposition.getName() instanceof GdlProposition))
                continue;

            GdlConstant constant = ((GdlProposition) proposition.getName()).getName();
            if (constant.getValue().toUpperCase().equals("INIT")) {
            	proposition.setType("init");
                return proposition;
            }
        }
        return null;
    }

    /**
     * Builds an index over the InputPropositions in the PropNet.
     *
     * @return An index over the InputPropositions in the PropNet.
     */
    private Map<GdlSentence, Proposition> recordInputPropositions()
    {
        Map<GdlSentence, Proposition> inputPropositions = new HashMap<GdlSentence, Proposition>();
        for (Proposition proposition : propositions)
        {
            // Skip all propositions that aren't GdlFunctions.
            if (!(proposition.getName() instanceof GdlRelation))
                continue;

            GdlRelation relation = (GdlRelation) proposition.getName();
            if (relation.getName().getValue().equals("does")) {
            	proposition.setType("does");
                inputPropositions.put(proposition.getName(), proposition);

                List<GdlTerm> body = proposition.getName().getBody();
                String key = body.get(0).toString();

                for (int i = 1; i < body.size(); i++) {
                	key += "|" + body.get(i).toString();
                }

                moveToProp.put(key, proposition);
            }
        }

        return inputPropositions;
    }

    /**
     * Builds an index over the LegalPropositions in the PropNet.
     *
     * @return An index over the LegalPropositions in the PropNet.
     */
    private Map<Role, Set<Proposition>> recordLegalPropositions()
    {
        Map<Role, Set<Proposition>> legalPropositions = new HashMap<Role, Set<Proposition>>();
        for (Proposition proposition : propositions)
        {
            // Skip all propositions that aren't GdlRelations.
            if (!(proposition.getName() instanceof GdlRelation))
                continue;

            GdlRelation relation = (GdlRelation) proposition.getName();
            if (relation.getName().getValue().equals("legal")) {
            	proposition.setType("legal");

                GdlConstant name = (GdlConstant) relation.get(0);
                Role r = new Role(name);
                if (!legalPropositions.containsKey(r)) {
                    legalPropositions.put(r, new HashSet<Proposition>());
                }
                legalPropositions.get(r).add(proposition);
            }
        }

        return legalPropositions;
    }

    /**
     * Builds an index over the Propositions in the PropNet.
     *
     * @return An index over Propositions in the PropNet.
     */
    private List<Proposition> recordPropositions()
    {
        List<Proposition> propositions = new ArrayList<Proposition>();
        for (Component component : components)
        {
            if (component instanceof Proposition) {
                propositions.add((Proposition) component);
            }
        }
        return propositions;
    }

    /**
     * Records a reference to the single, unique, TerminalProposition.
     *
     * @return A reference to the single, unqiue, TerminalProposition.
     */
    private Proposition recordTerminalProposition()
    {
        for ( Proposition proposition : propositions )
        {
            if ( proposition.getName() instanceof GdlProposition )
            {
                GdlConstant constant = ((GdlProposition) proposition.getName()).getName();
                if ( constant.getValue().equals("terminal") )
                {
                    proposition.setType("terminal");
                	return proposition;
                }
            }
        }

        return null;
    }

    public int getSize() {
        return components.size();
    }

    public int getNumAnds() {
        int andCount = 0;
        for(Component c : components) {
            if(c instanceof And)
                andCount++;
        }
        return andCount;
    }

    public int getNumOrs() {
        int orCount = 0;
        for(Component c : components) {
            if(c instanceof Or)
                orCount++;
        }
        return orCount;
    }

    public int getNumNots() {
        int notCount = 0;
        for(Component c : components) {
            if(c instanceof Not)
                notCount++;
        }
        return notCount;
    }

    public int getNumLinks() {
        int linkCount = 0;
        for(Component c : components) {
            linkCount += c.getOutputs().size();
        }
        return linkCount;
    }

    /**
     * Removes a component from the propnet. Be very careful when using
     * this method, as it is not thread-safe. It is highly recommended
     * that this method only be used in an optimization period between
     * the propnet's creation and its initial use, during which it
     * should only be accessed by a single thread.
     *
     * The INIT and terminal components cannot be removed.
     */
    public void removeComponent(Component c) {


        //Go through all the collections it could appear in
        if(c instanceof Proposition) {
            Proposition p = (Proposition) c;
            GdlSentence name = p.getName();
            if(basePropositions.containsKey(name)) {
                basePropositions.remove(name);
            } else if(inputPropositions.containsKey(name)) {
                inputPropositions.remove(name);
                //The map goes both ways...
                Proposition partner = legalInputMap.get(p);
                if(partner != null) {
                    legalInputMap.remove(partner);
                    legalInputMap.remove(p);
                }
            } else if(name == GdlPool.getProposition(GdlPool.getConstant("INIT"))) {
                throw new RuntimeException("The INIT component cannot be removed. Consider leaving it and ignoring it.");
            } else if(name == GdlPool.getProposition(GdlPool.getConstant("terminal"))) {
                throw new RuntimeException("The terminal component cannot be removed.");
            } else {
                for(Set<Proposition> propositions : legalPropositions.values()) {
                    if(propositions.contains(p)) {
                        propositions.remove(p);
                        Proposition partner = legalInputMap.get(p);
                        if(partner != null) {
                            legalInputMap.remove(partner);
                            legalInputMap.remove(p);
                        }
                    }
                }
                for(Set<Proposition> propositions : goalPropositions.values()) {
                    propositions.remove(p);
                }
            }
            propositions.remove(p);
        }
        components.remove(c);

        //Remove all the local links to the component
        for(Component parent : c.getInputs())
            parent.removeOutput(c);
        for(Component child : c.getOutputs())
            child.removeInput(c);
        //These are actually unnecessary...
        //c.removeAllInputs();
        //c.removeAllOutputs();
    }
}