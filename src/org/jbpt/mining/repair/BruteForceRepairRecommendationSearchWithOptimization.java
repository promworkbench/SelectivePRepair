package org.jbpt.mining.repair;
import java.util.HashMap;
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
public class BruteForceRepairRecommendationSearchWithOptimization extends RepairRecommendationSearch {
	
		public BruteForceRepairRecommendationSearchWithOptimization(
				PetrinetGraph	net, 
				Marking			initMarking, 
				Marking[]		finalMarkings, 
				XLog 			log, 
				Map<Transition,Integer>		costMOS, 
				Map<XEventClass,Integer>	costMOT, 
				TransEvClassMapping			mapping, 
				XEventClassifier			eventClassifier, 
				boolean 					debug) throws Exception {
		super(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
	}
		
	private Set<RepairRecommendation> visited = new HashSet<RepairRecommendation>();
	private Set<RepairRecommendation> computed = new HashSet<RepairRecommendation>();
		
	private void computeOptimalRepairRecommendations(RepairConstraint constraint, RepairRecommendation recommendation, 
							RepairRecommendation previousRecommendation, Set<String> labelsI, Set<String> labelsS, boolean first) {
		
		if (visited.contains(recommendation)) return;
		
		// recommendation is over my budget!
		if (!CostFunction.isUnderBudget(constraint,recommendation)) {
			if (computed.contains(previousRecommendation))
				return;
			
			// prepare cost functions
			Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
			Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
			this.adjustCostFuncMOS(tempMOS,previousRecommendation.getSkipLabels());
			this.adjustCostFuncMOT(tempMOT,previousRecommendation.getInsertLabels());
			
			// compute cost
			int cost = this.computeCost(tempMOS, tempMOT);
			
			if (this.debug) System.out.println(String.format("DEBUG> %s : %s", recommendation, cost));
			
			// update optimal cost
			if (cost < this.optimalCost) {
				this.optimalCost = cost;
				this.optimalRepairRecommendations.clear();
				this.optimalRepairRecommendations.add(previousRecommendation);
			}
			else if (cost == this.optimalCost) {
				this.optimalRepairRecommendations.add(previousRecommendation);
			}
			
			computed.add(previousRecommendation);
			
			return;
		}
		
		visited.add(recommendation);
		
		// make recursive calls
		for (String label : labelsI) {
			Set<String> labelsIC = new HashSet<String>(labelsI);
			Set<String> labelsSC = new HashSet<String>(labelsS);
			labelsIC.remove(label);
			RepairRecommendation rec = recommendation.clone();
			rec.insertLabels.add(label);
			
			this.computeOptimalRepairRecommendations(constraint,rec,recommendation,labelsIC,labelsSC,false);
		}

		for (String label : labelsS) {
			Set<String> labelsIC = new HashSet<String>(labelsI);
			Set<String> labelsSC = new HashSet<String>(labelsS);
			labelsSC.remove(label);
			RepairRecommendation rec = recommendation.clone();
			rec.skipLabels.add(label);
			
			this.computeOptimalRepairRecommendations(constraint,rec,recommendation,labelsIC,labelsSC,false);
		}
	}

	@Override
	public Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint) {
		this.alignmentCostComputations	= 0;
		this.optimalRepairRecommendations.clear();
		this.visited.clear();
		this.computed.clear();
		this.optimalCost = Integer.MAX_VALUE;
		
		RepairRecommendation recommendation	= new RepairRecommendation();
		
		Set<String> labelsI = CostFunction.getLabels(this.log,this.eventClassifier);
		Set<String> labelsS = CostFunction.getLabels(this.net);
		
		this.computeOptimalRepairRecommendations(constraint, recommendation, recommendation, labelsI, labelsS, true);
		
		this.backtrackOptimalRepairRecommendations();
		
		return this.optimalRepairRecommendations;
	}
}
