package org.jbpt.mining.repair;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.model.XLog;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;


/**
 * @author Artem Polyvyanyy
 */
public class GreedyRepairRecommendationSearchOLD extends RepairRecommendationSearch {
	
		public GreedyRepairRecommendationSearchOLD(PetrinetGraph	net, 
				Marking			initMarking, 
				Marking[]		finalMarkings, 
				XLog 			log, 
				Map<Transition,Integer>		costMOS, 
				Map<XEventClass,Integer>	costMOT, 
				TransEvClassMapping			mapping, 
				boolean 					outputFlag) throws Exception {
		super(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, outputFlag);
	}

	@Override
	public Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint) {
		this.alignmentCostComputations	= 0;
		
		this.optimalRepairRecommendations.clear();
		RepairRecommendation recommendation	= new RepairRecommendation();
		this.optimalRepairRecommendations.add(recommendation);
		
		this.optimalCost = this.computeCost(this.costFuncMOS, this.costFuncMOT);
		this.alignmentCostComputations++;
		
		Set<String> labels = CostFunction.getLabels(this.net);
		
		boolean flag = true;
		while (flag) {
			flag = false;
			Set<RepairRecommendation> newRecs = new HashSet<RepairRecommendation>(this.optimalRepairRecommendations);
			System.out.println(newRecs);
			
			for (RepairRecommendation rec : this.optimalRepairRecommendations) {
				
				// handle insert labels
				Set<String> ls = new HashSet<String>(labels);
				ls.removeAll(rec.insertLabels);
				for (String label : ls) {
					RepairRecommendation r = rec.clone();
					r.insertLabels.add(label);
					if (!CostFunction.isUnderBudget(constraint, r)) continue;
					
					Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
					Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
					this.adjustCostFuncMOS(tempMOS,r.getSkipLabels());
					this.adjustCostFuncMOT(tempMOT,r.getInsertLabels());
					
					int cost = this.computeCost(tempMOS, tempMOT);
					this.alignmentCostComputations++;
					
					if (cost < this.optimalCost) {
						this.optimalCost = cost;
						newRecs.clear();
						newRecs.add(r);
						flag = true;
					}
					else if (cost == this.optimalCost && !newRecs.contains(r)) {
						newRecs.add(r);
						flag = true;
					}
				}
				
				// handle skip labels
				ls = new HashSet<String>(labels);
				ls.removeAll(rec.skipLabels);
				for (String label : ls) {
					RepairRecommendation r = rec.clone();
					r.skipLabels.add(label);
					if (!CostFunction.isUnderBudget(constraint, r)) continue;
					
					Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
					Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
					this.adjustCostFuncMOS(tempMOS,r.getSkipLabels());
					this.adjustCostFuncMOT(tempMOT,r.getInsertLabels());
					
					int cost = this.computeCost(tempMOS, tempMOT);
					this.alignmentCostComputations++;
					
					if (cost < this.optimalCost) {
						this.optimalCost = cost;
						newRecs.clear();
						newRecs.add(r);
						flag = true;
					}
					else if (cost == this.optimalCost && !newRecs.contains(r)) {
						newRecs.add(r);
						flag = true;
					}
				}
			}
			
			this.optimalRepairRecommendations.clear();
			this.optimalRepairRecommendations.addAll(newRecs);
		}
		
		this.backtrackOptimalRepairRecommendations();
		
		return this.optimalRepairRecommendations;
	}

}
