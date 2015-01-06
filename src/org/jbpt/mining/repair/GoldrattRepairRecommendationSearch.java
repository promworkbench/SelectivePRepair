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
 * 
 * TODO: consider all optimal alignments of a trace with a model
 */
public class GoldrattRepairRecommendationSearch extends RepairRecommendationSearch {
	
	public GoldrattRepairRecommendationSearch(PetrinetGraph net, 
			Marking			initMarking, 
			Marking[]		finalMarkings, 
			XLog 			log, 
			Map<Transition,Integer>		costMOS, 
			Map<XEventClass,Integer>	costMOT, 
			TransEvClassMapping			mapping,
			XEventClassifier		 	eventClassifier,
			boolean 					debug) throws Exception {			
		super(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
	}	
	
	@Override
	public Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint, boolean singleton) {
		// reset
		this.alignmentComputations = 0;
		
		// empty recommendation to start with
		Set<RepairRecommendation> recs = new HashSet<RepairRecommendation>();
		RepairRecommendation recommendation	= new RepairRecommendation();
		recs.add(recommendation);
		
		// collection of optimal alignment costs
		Map<RepairRecommendation,Integer> costs = new HashMap<RepairRecommendation,Integer>();
		
		do {
			this.optimalRepairRecommendations.clear();
			this.optimalRepairRecommendations.addAll(recs);
			recs.clear();
			
			int		mrc = 0; 
			double	mci	= 0;
			
			Set<RepairRecommendation> visited = new HashSet<RepairRecommendation>();
			
			for (RepairRecommendation r : this.optimalRepairRecommendations) {
				// if (debug) System.out.println("DEBUG> Current repair recomendation: " + r);
				
				Map<Transition,Integer>	 tempMOS = this.getAdjustedCostFuncMOS(r.getSkipLabels());
				Map<XEventClass,Integer> tempMOT = this.getAdjustedCostFuncMOT(r.getInsertLabels());
				
				// get movement frequencies and alignment cost 
				Map<AlignmentStep,Integer> frequencies = this.computeMovementFrequenciesAndAlignmentCost(tempMOS,tempMOT);
				//if (this.debug) System.out.println("DEBUG> Movement frequencies:" + frequencies);
				
				// remember alignment cost
				costs.put(r, this.optimalAlignmentCost);
				
				// compute impact of labels on optimal alignment cost (ignore labels in current repair recommendation)
				Map<AlignmentLabel,Double> l2i = new HashMap<AlignmentLabel,Double>();
				boolean free = this.computeImpactOfLabelsOnOptimalAlignmentCost(l2i, frequencies, r, constraint, this.costFuncMOS, this.costFuncMOT);
				//if (this.debug) System.out.println("DEBUG> Label weights:" + l2w);
				
				// compute impact of labels on optimal alignment cost (per repair resource)
				if (!free) l2i = this.computeImpactPerRepairResource(l2i,constraint);
				
				for (Map.Entry<AlignmentLabel,Double> entry : l2i.entrySet()) {
					AlignmentLabel lb = entry.getKey();
					double cci = entry.getValue();
					
					RepairRecommendation rec = r.clone();
					int crc = 0;
					
					if (lb.isTransition()) {
						rec.skipLabels.add(lb.getLabel());
						crc = constraint.getSkipCosts().get(lb.getLabel());
					}
					
					if (!lb.isTransition()) {
						rec.insertLabels.add(lb.getLabel());
						crc = constraint.getInsertCosts().get(lb.getLabel());
					}
					
					if (visited.contains(rec)) continue; else visited.add(rec);
					if (!CostFunction.isUnderBudget(constraint,rec)) continue;
						
					if (free && crc==0 && cci>mci) {
						recs.clear();
						recs.add(rec);
						mci = cci;
					}
					else if (free && crc==0 && cci>0 && cci==mci && !singleton) {
						recs.add(rec);
					}
					else if (!free && cci>mci) { // crc>0!
						recs.clear();
						recs.add(rec);
						mci = cci;
						mrc = crc;
					}
					else if (!free && cci > 0 && cci == mci && crc > mrc) {
						recs.clear();
						recs.add(rec);
						mrc = crc;
					}
					else if (!free && cci == mci && crc > 0 && crc == mrc && !singleton) {
						recs.add(rec);
					}
				}
			}
			
			//if (debug) System.out.println("DEBUG> Repair recommendations: " + recs);
			
		} while (!recs.isEmpty());
		
		int maxCost = Integer.MAX_VALUE;
		for (RepairRecommendation r : this.optimalRepairRecommendations) {
			int cost = costs.get(r);
			if (cost < maxCost) {
				maxCost = cost;
				recs.clear();
				recs.add(r);
			}
			else if (cost==maxCost) {
				recs.add(r);
			}
		}
		
		this.optimalRepairRecommendations.clear();
		this.optimalRepairRecommendations.addAll(recs);
		this.optimalAlignmentCost = maxCost; 
		
		return this.optimalRepairRecommendations;
	}
}
