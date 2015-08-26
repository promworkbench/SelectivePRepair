package org.jbpt.mining.repair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.model.XLog;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;

public class CostFunction {
	
	public static Set<String> getLabels(PetrinetGraph net) {
		Set<String> result = new HashSet<String>();
		for (Transition t : net.getTransitions()) {
			if (t.isInvisible() || t.getLabel().toLowerCase().contains("invisible")) continue;
			result.add(t.getLabel());
		}
		
		return result;
	}

	public static Map<String,Integer> getStdCostFunctionOnLabels(Set<String> labels) {
		Map<String,Integer> result = new HashMap<String, Integer>();
		
		for (String label : labels)
			result.put(label, 1);
		
		return result;
	}
	
	public static int getRequiredResources(RepairConstraint constraint, RepairRecommendation recommendation) {
		int result = 0;
		
		for (String label : recommendation.getInsertLabels()) {
			Integer ci = constraint.getInsertCosts().get(label);
			result += ci == null ? 0 : ci;
		}
		
		for (String label : recommendation.getSkipLabels()) {
			Integer cs = constraint.getSkipCosts().get(label);
			result += cs == null ? 0 : cs;
		}
		
		return result;
	}
	
	public static boolean isUnderBudget(RepairConstraint constraint, RepairRecommendation recommendation) {
		return CostFunction.getRequiredResources(constraint, recommendation) <= constraint.getAvailableResources();
	}

	public static Set<String> getLabels(XLog log, XEventClassifier eventClassifier) {
		Set<String> result = new HashSet<String>();
		XLogInfo summary = XLogInfoFactory.createLogInfo(log,eventClassifier);
		
		for (XEventClass evClass : summary.getEventClasses().getClasses()) {
			result.add(evClass.getId());
		}
		
		return result;
	}

}
