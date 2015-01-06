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

	public Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint, boolean singleton) {
		this.optimalRepairRecommendations.clear();
		this.optimalAlignmentCost = this.computeAlignmentCost(this.costFuncMOS, this.costFuncMOT);
		int cCost = this.optimalAlignmentCost;
		
		Set<String> labelsI = CostFunction.getLabels(this.log,this.eventClassifier);
		Set<String> labelsS = CostFunction.getLabels(this.net);
		
		// empty recommendation to start with
		Set<RepairRecommendation> recs = new HashSet<RepairRecommendation>();
		RepairRecommendation recommendation	= new RepairRecommendation();
		recs.add(recommendation);
		
		do {
			//if (debug) System.out.println("DEBUG> Alignment computations: "+this.alignmentComputations);
			
			this.optimalRepairRecommendations.clear();
			this.optimalRepairRecommendations.addAll(recs);
			
			if (this.optimalAlignmentCost<=0)
				return this.optimalRepairRecommendations;
			
			recs.clear();
			int mci = 0;
			int mrc = 0;
			boolean free = false;
			
			Set<RepairRecommendation> visited = new HashSet<RepairRecommendation>();
				
			for (RepairRecommendation r : this.optimalRepairRecommendations) {
				Set<AlignmentLabel> labels = new HashSet<AlignmentLabel>();
				
				Set<String> ls = new HashSet<String>(labelsI);
				ls.removeAll(r.insertLabels);
				for (String label : ls) labels.add(new AlignmentLabel(label, true));
				ls = new HashSet<String>(labelsS);
				ls.removeAll(r.skipLabels);
				for (String label : ls) labels.add(new AlignmentLabel(label, false));
				
				for (AlignmentLabel lb : labels) {
					RepairRecommendation rec = r.clone();
					
					if (lb.isTransition()) rec.insertLabels.add(lb.getLabel());
					else rec.skipLabels.add(lb.getLabel());
					
					if (visited.contains(rec)) continue; else visited.add(rec);
					if (!CostFunction.isUnderBudget(constraint, rec)) continue;
					
					int aCost = this.optimalAlignmentCost;
					
					int crc = 0;
					if (lb.isTransition()) crc = constraint.getInsertCosts().get(lb.getLabel());
					else crc = constraint.getSkipCosts().get(lb.getLabel()); 
					
					if (crc==0 || (!free && (((double) this.optimalAlignmentCost / crc) >= mci))) {
						Map<Transition,Integer>  tempMOS = this.getAdjustedCostFuncMOS(rec.getSkipLabels());
						Map<XEventClass,Integer> tempMOT = this.getAdjustedCostFuncMOT(rec.getInsertLabels());
						aCost = this.computeAlignmentCost(tempMOS,tempMOT);
					}
					
					int cci = this.optimalAlignmentCost - aCost;
					if (crc>0) cci = cci/crc;
					
					if (crc>0 && cci>mci && !free) {
						mci = cci;
						mrc = crc;
						recs.clear();
						recs.add(rec);
						cCost = aCost;
					}
					else if (crc>mrc && cci>0 && cci==mci && !free) {
						mrc = crc;
						recs.clear();
						recs.add(rec);
						cCost = aCost;
					}
					else if (crc>0 && crc==mrc && cci>0 && cci==mci && !free && !singleton) {
						recs.add(rec);
					}
					else if (crc==0 && cci>mci && free) {
						mci = cci; 
						recs.clear();
						recs.add(rec);
						cCost = aCost;
					}
					else if (crc==0 && cci==mci && free && !singleton) {
						recs.add(rec);
					}
					else if (crc==0 && cci>0 && !free) {
						free = true;
						mci = cci;
						recs.clear();
						recs.add(rec);
						cCost = aCost;
					}
				}
			}
			
			this.optimalAlignmentCost = cCost;
			
		} while (!recs.isEmpty());
		
		return this.optimalRepairRecommendations;
	}

}
