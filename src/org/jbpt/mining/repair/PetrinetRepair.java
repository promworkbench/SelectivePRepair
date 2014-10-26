package org.jbpt.mining.repair;

import java.util.HashSet;
import java.util.Set;

import org.deckfour.xes.model.XLog;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;

public class PetrinetRepair {
	
	public static void repair(PetrinetGraph net, XLog log, RepairRecommendation recommendation) {
		Set<Transition> skip = new HashSet<Transition>();
		for (Transition t : net.getTransitions()) {
			if (recommendation.getSkipLabels().contains(t.getLabel())) {
				skip.add(t);
			}
		}
		
		for (Transition t : skip) {
			Transition tt = net.addTransition("");
			tt.setInvisible(true);
			
			for (PetrinetEdge<?,?> edge : net.getInEdges(t)) {
				net.addArc((Place)edge.getSource(), tt);
			}
			
			for (PetrinetEdge<?,?> edge : net.getOutEdges(t)) {
				net.addArc(tt,(Place)edge.getTarget());
			}
		}
		
	}

}
