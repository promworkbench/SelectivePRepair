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
public class WeightedGreedyRepairRecommendationOptimizationOLD extends RepairRecommendationSearch {
	
		public WeightedGreedyRepairRecommendationOptimizationOLD(PetrinetGraph	net, 
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
		
		HashMap<RepairRecommendation,Integer> rec2ac  = new HashMap<RepairRecommendation,Integer>(); 
		
		this.optimalRepairRecommendations.clear();
		
		RepairRecommendation recommendation	= new RepairRecommendation();
		this.optimalRepairRecommendations.add(recommendation);
		
		this.optimalCost = this.computeCost(this.costFuncMOS, this.costFuncMOT);
		this.alignmentCostComputations++;
		
		rec2ac.put(recommendation, this.optimalCost);
		
		Set<String> labels = CostFunction.getLabels(this.net);
		
		boolean flag = true;
		while (flag) {
			flag = false;
			
			Set<RepairRecommendation> newRecs = new HashSet<RepairRecommendation>(this.optimalRepairRecommendations);
			
			double minWeight = Double.MAX_VALUE;
			double maxResources = 0.0;
			
			for (RepairRecommendation rec : this.optimalRepairRecommendations) {
				int recAC = rec2ac.get(rec);
				
				// handle insert labels
				Set<String> ls = new HashSet<String>(labels);
				ls.removeAll(rec.insertLabels);
				for (String label : ls) {
					RepairRecommendation r = rec.clone();
					r.insertLabels.add(label);
					if (!CostFunction.isUnderBudget(constraint, r)) continue;
					
					Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
					Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
					this.adjustCostFuncMOS(tempMOS, r.getSkipLabels());
					this.adjustCostFuncMOT(tempMOT, r.getInsertLabels());
					
					int cost = this.computeCost(tempMOS, tempMOT);
					this.alignmentCostComputations++;
					
					if (cost>recAC) continue; // this condition should never hold!
					
					double weight = constraint.getInsertCosts().get(label) / ((double)(recAC - cost));
					
					if (weight < minWeight) {
						this.optimalCost = cost;
						minWeight = weight;
						maxResources = constraint.getInsertCosts().get(label);
						newRecs.clear();
						newRecs.add(r);
						flag = true;
						
						rec2ac.put(r,cost);
					}
					else if (weight == minWeight && !newRecs.contains(r)) {
						if (constraint.getInsertCosts().get(label) > maxResources) {
							this.optimalCost = cost;
							maxResources = constraint.getInsertCosts().get(label);
							newRecs.clear();
							newRecs.add(r);
							flag = true;
							
							rec2ac.put(r,cost);
						}
						else if (constraint.getInsertCosts().get(label) == maxResources) {
							newRecs.add(r);
							rec2ac.put(r,cost);
							flag = true;	
						}
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
					this.adjustCostFuncMOS(tempMOS, r.getSkipLabels());
					this.adjustCostFuncMOT(tempMOT, r.getInsertLabels());
					
					int cost = this.computeCost(tempMOS, tempMOT);
					this.alignmentCostComputations++;
					
					if (cost>recAC) continue; // this condition should never hold!
					
					double weight = constraint.getSkipCosts().get(label) / ((double)(recAC - cost));
					
					if (weight < minWeight) {
						this.optimalCost = cost;
						minWeight = weight;
						maxResources = constraint.getSkipCosts().get(label);
						newRecs.clear();
						newRecs.add(r);
						flag = true;
						
						rec2ac.put(r,cost);
					}
					else if (weight == minWeight && !newRecs.contains(r)) {
						if (constraint.getSkipCosts().get(label) > maxResources) {
							this.optimalCost = cost;
							maxResources = constraint.getSkipCosts().get(label);
							newRecs.clear();
							newRecs.add(r);
							flag = true;
							
							rec2ac.put(r,cost);
						}
						else if (constraint.getSkipCosts().get(label) == maxResources) {
							newRecs.add(r);
							rec2ac.put(r,cost);
							flag = true;
						}
					}
				}
				
			}
			
			this.optimalRepairRecommendations.clear();
			this.optimalRepairRecommendations.addAll(newRecs);
		}
		
		
		
		return this.optimalRepairRecommendations;
	}

}
