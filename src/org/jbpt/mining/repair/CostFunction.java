package org.jbpt.mining.repair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

	public static Map<String,Double> getStdCostFunctionOnLabels(Set<String> labels) {
		Map<String, Double> result = new HashMap<String, Double>();
		
		for (String label : labels)
			result.put(label, 1.0);
		
		return result;
	}
	
	public static double getRequiredResources(RepairConstraint constraint, RepairRecommendation recommendation) {
		double result = 0.0;
		
		for (String label : recommendation.getInsertLabels()) {
			Double ci = constraint.getInsertCosts().get(label);
			result += ci == null ? 0 : ci;
		}
		
		for (String label : recommendation.getSkipLabels()) {
			Double cs = constraint.getInsertCosts().get(label);
			result += cs == null ? 0 : cs;
		}
		
		return result;
	}
	
	public static boolean isUnderBudget(RepairConstraint constraint, RepairRecommendation recommendation) {
		return CostFunction.getRequiredResources(constraint, recommendation) <= constraint.getAvailableResources();
	}

}
