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
 * TODO: think of a termination condition once all expansions of current repair recommendation do not lead to alignment improvement
 */
public class GreedyRepairRecommendationSearch extends RepairRecommendationSearch {
	
		public GreedyRepairRecommendationSearch(PetrinetGraph	net, 
				Marking			initMarking, 
				Marking[]		finalMarkings, 
				XLog 			log, 
				Map<Transition,Integer>		costMOS, 
				Map<XEventClass,Integer>	costMOT, 
				TransEvClassMapping			mapping, 
				XEventClassifier 			eventClassifier,
				boolean 					debug) throws Exception {
		super(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
	}

	@Override
	public Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint) {
		this.alignmentCostComputations	= 0;
		this.optimalRepairRecommendations.clear();
				
		// cost of alignment to start with
		this.optimalCost = this.computeCost(this.costFuncMOS, this.costFuncMOT);
		
		/* OLD:
		 * Set<String> labels = CostFunction.getLabels(this.log,this.eventClassifier);
		labels.addAll(CostFunction.getLabels(this.net));*/
		
		Set<String> labelsI = CostFunction.getLabels(this.log,this.eventClassifier);
		Set<String> labelsS = CostFunction.getLabels(this.net);
		
		// empty recommendation to start with
		Set<RepairRecommendation> recs = new HashSet<RepairRecommendation>();
		RepairRecommendation recommendation	= new RepairRecommendation();
		recs.add(recommendation);
		
		do {
			if (debug) System.out.println("DEBUG> Alignment computations: "+this.alignmentCostComputations);
			this.optimalRepairRecommendations.clear();
			this.optimalRepairRecommendations.addAll(recs);
			recs.clear();
			
			for (RepairRecommendation r : this.optimalRepairRecommendations) {
				// handle insert labels
				Set<String> ls = new HashSet<String>(labelsI);
				ls.removeAll(r.insertLabels);
				
				for (String label : ls) {
					RepairRecommendation rec = r.clone();
					rec.insertLabels.add(label);
					if (!CostFunction.isUnderBudget(constraint, rec)) continue;
					
					// compute alignment cost
					Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
					Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
					this.adjustCostFuncMOS(tempMOS,rec.getSkipLabels());
					this.adjustCostFuncMOT(tempMOT,rec.getInsertLabels());
					int cost = this.computeCost(tempMOS, tempMOT);
					
					if (cost<=this.optimalCost) {
						if (cost<this.optimalCost) recs.clear();
						recs.add(rec);
						this.optimalCost = cost;
					}
				}
				
				// handle skip labels
				ls = new HashSet<String>(labelsS);
				ls.removeAll(r.skipLabels);
				
				for (String label : ls) {
					RepairRecommendation rec = r.clone();
					rec.skipLabels.add(label);
					if (!CostFunction.isUnderBudget(constraint, rec)) continue;
					
					// compute alignment cost
					Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
					Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
					this.adjustCostFuncMOS(tempMOS,rec.getSkipLabels());
					this.adjustCostFuncMOT(tempMOT,rec.getInsertLabels());
					int cost = this.computeCost(tempMOS, tempMOT);
					
					if (cost<=this.optimalCost) {
						if (cost<this.optimalCost) recs.clear();
						recs.add(rec);
						this.optimalCost = cost;
					}
				}
			}
			
		} while (!recs.isEmpty());
		
		this.backtrackOptimalRepairRecommendations();
		
		return this.optimalRepairRecommendations;
	}

}