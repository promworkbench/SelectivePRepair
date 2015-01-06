package org.jbpt.mining.repair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.model.XLog;
import org.jbpt.mining.Knapsack01DynamicOneSolution;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;


/**
 * @author Artem Polyvyanyy
 * 
 * TODO: consider all optimal alignments of a trace with a model
 * TODO: get all solutions to the Knapsack problem
 */
public class KnapsackRepairRecommendationSearch extends RepairRecommendationSearch {
	
	public KnapsackRepairRecommendationSearch(
			PetrinetGraph				net, 
			Marking						initMarking, 
			Marking[]					finalMarkings, 
			XLog 						log, 
			Map<Transition,Integer>		costMOS, 
			Map<XEventClass,Integer>	costMOT, 
			TransEvClassMapping			mapping, 
			XEventClassifier 			eventClassifier,
			boolean 					debug) throws Exception {
		super(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
	}
	
	public Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint, boolean singleton) {
		// reset
		this.alignmentComputations	= 0;
		this.optimalRepairRecommendations.clear();
		
		// get movement frequencies and alignment cost 
		Map<AlignmentStep,Integer> frequencies = this.computeMovementFrequenciesAndAlignmentCost(this.costFuncMOS, this.costFuncMOT);
		//if (this.debug) System.out.println("DEBUG> Movement frequencies:" + frequencies);
		
		// compute impact of labels on optimal alignment cost
		Map<AlignmentLabel,Integer> l2w = this.computeImpactOfLabelsOnOptimalAlignmentCost(frequencies, this.costFuncMOS, this.costFuncMOT);
		//if (this.debug) System.out.println("DEBUG> Label weights:" + l2w);
		
		boolean stop = true;
		
		// formulate Knapsack problem
		int N = l2w.size();
		int[] profit = new int[N+1];
        int[] weight = new int[N+1];
        
        List<AlignmentLabel> labels = new ArrayList<AlignmentLabel>(l2w.keySet());
        
        for (int n=1; n<=N; n++) {
        	AlignmentLabel lb = labels.get(n-1);
        	profit[n] = l2w.get(lb);
        	
        	if (lb.isTransition())
                weight[n] = constraint.getSkipCosts().get(lb.getLabel());	
        	else
                weight[n] = constraint.getInsertCosts().get(lb.getLabel());
        	
        	if (weight[n]<=constraint.getAvailableResources()) stop = false;
        }
        
        if (stop) {
        	this.optimalRepairRecommendations.add(new RepairRecommendation());
			return this.optimalRepairRecommendations;
        }
        
        /*if (this.debug) System.out.println("DEBUG> Labels: "+labels);
        if (this.debug) System.out.print("DEBUG> Profits: ");
        for (int n=1; n<=N; n++)
        	if (this.debug) System.out.print(profit[n]+" ");
        if (this.debug) System.out.println();
        if (this.debug) System.out.print("DEBUG> Weights: ");
        for (int n=1; n<=N; n++)
        	if (this.debug) System.out.print(weight[n]+" ");
        if (this.debug) System.out.println();*/

        Set<boolean[]> solutions = new HashSet<boolean[]>();
        if (singleton) {
        	boolean[] solution = Knapsack01DynamicOneSolution.solve(profit, weight, constraint.getAvailableResources());
        	solutions.add(solution);
        }
        else {
        	// TODO replace with an implementation for all solutions!
        	boolean[] solution = Knapsack01DynamicOneSolution.solve(profit, weight, constraint.getAvailableResources());
        	solutions.add(solution);
        }
        
        this.optimalRepairRecommendations.clear();
        for (boolean[] solution : solutions) {
        	RepairRecommendation rec = new RepairRecommendation();
        	
        	for (int n=1; n<=N; n++) {
            	if (!solution[n]) continue;
            	
            	AlignmentLabel lb = labels.get(n-1);
    			if (lb.isTransition())
    				rec.skipLabels.add(lb.getLabel());
    			else
    				rec.insertLabels.add(lb.getLabel());
    		}
        	
        	int aCost = this.computeAlignmentCost(rec);
        	
        	if (aCost<this.optimalAlignmentCost) {
        		this.optimalAlignmentCost = aCost;
        		this.optimalRepairRecommendations.clear();
        	}
        	
        	if (aCost<=this.optimalAlignmentCost) {
        		this.optimalRepairRecommendations.add(rec);
        	}
        	
        }
		
        // TODO ideally, one needs to minimise these repairs
		return this.optimalRepairRecommendations;
	}
}
