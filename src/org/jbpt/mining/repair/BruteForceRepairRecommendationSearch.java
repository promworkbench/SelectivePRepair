package org.jbpt.mining.repair;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.model.XLog;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;


/**
 * @author Artem Polyvyanyy
 */
public class BruteForceRepairRecommendationSearch extends RepairRecommendationSearch {
	
		public BruteForceRepairRecommendationSearch(
				PetrinetGraph net, 
				Marking			initMarking, 
				Marking[]		finalMarkings, 
				XLog 			log, 
				Map<Transition,Integer>		costMOS, 
				Map<XEventClass,Integer>	costMOT, 
				TransEvClassMapping			mapping, 
				XEventClassifier eventClassifier, 
				boolean debug) throws Exception {
		super(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
	}
		
	private Set<RepairRecommendation> visited = new HashSet<RepairRecommendation>(); 
		
	private void computeOptimalRepairRecommendations(RepairConstraint constraint, RepairRecommendation recommendation, 
														Set<String> labelsI, Set<String> labelsS) {
		if (this.optimalAlignmentCost<=0) return;
		// recommendation was already visited!
		if (visited.contains(recommendation)) return;
		
		// recommendation is over my budget!
		if (!CostFunction.isUnderBudget(constraint, recommendation)) return;
		
		// prepare cost functions
		Map<Transition,Integer>  tempMOS = this.getAdjustedCostFuncMOS(recommendation.getSkipLabels());
		Map<XEventClass,Integer> tempMOT = this.getAdjustedCostFuncMOT(recommendation.getInsertLabels());
		
		// compute cost
		int cost = this.computeAlignmentCost(tempMOS,tempMOT);
		
		if (this.debug) System.out.println(String.format("DEBUG> %s : %s", recommendation, cost));
		
		// update optimal cost
		if (cost < this.optimalAlignmentCost) {
			this.optimalAlignmentCost = cost;
			this.optimalRepairRecommendations.clear();
			this.optimalRepairRecommendations.add(recommendation);
		}
		else if (cost == this.optimalAlignmentCost) {
			this.optimalRepairRecommendations.add(recommendation);
		}
		
		// remember visited recommendations
		visited.add(recommendation);
		
		// make recursive calls
		for (String label : labelsI) {
			Set<String> labelsIC = new HashSet<String>(labelsI);
			Set<String> labelsSC = new HashSet<String>(labelsS);
			labelsIC.remove(label);
			RepairRecommendation rec = recommendation.clone();
			rec.insertLabels.add(label);
			
			this.computeOptimalRepairRecommendations(constraint,rec,labelsIC,labelsSC);
		}

		for (String label : labelsS) {
			Set<String> labelsIC = new HashSet<String>(labelsI);
			Set<String> labelsSC = new HashSet<String>(labelsS);
			labelsSC.remove(label);
			RepairRecommendation rec = recommendation.clone();
			rec.skipLabels.add(label);
			
			this.computeOptimalRepairRecommendations(constraint,rec,labelsIC,labelsSC);
		}
	}

	@Override
	public Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint, boolean considerAll) {
		this.alignmentComputations	= 0;
		this.optimalRepairRecommendations.clear();
		this.visited.clear();
		this.optimalAlignmentCost = Integer.MAX_VALUE;
		
		RepairRecommendation recommendation	= new RepairRecommendation();
		
		Set<String> labelsI = CostFunction.getLabels(this.log,this.eventClassifier);
		Set<String> labelsS = CostFunction.getLabels(this.net);
		
		this.computeOptimalRepairRecommendations(constraint, recommendation, labelsI, labelsS);
		
		this.preserveMinimalOptimalRepairRecommendations();
		
		return this.optimalRepairRecommendations;
	}
}
