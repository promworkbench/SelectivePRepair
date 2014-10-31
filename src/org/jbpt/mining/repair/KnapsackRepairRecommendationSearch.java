package org.jbpt.mining.repair;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.processmining.plugins.petrinet.replayresult.StepTypes;


/**
 * @author Artem Polyvyanyy
 * 
 * TODO: consider all optimal alignments of a trace with a model
 * TODO: get all solutions to the Knapsack problem
 */
public class KnapsackRepairRecommendationSearch extends RepairRecommendationSearch {
	
		public KnapsackRepairRecommendationSearch(
				PetrinetGraph	net, 
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
		
	private Map<String,Integer> costFuncMOSwL = new HashMap<String,Integer>();
	private Map<String,Integer> costFuncMOTwL = new HashMap<String,Integer>();
	private Map<String,Double> costFuncMOSwLperR = new HashMap<String,Double>();
	private Map<String,Double> costFuncMOTwLperR = new HashMap<String,Double>();

	@Override
	public Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint) {
		this.alignmentCostComputations	= 0;
		this.optimalRepairRecommendations.clear();
		
		// get movement frequencies 
		Map<AlignmentStep,Integer> frequencies = this.computeFrequencies(this.costFuncMOS, this.costFuncMOT);
		if (debug) System.out.println("DEBUG> Movement frequencies:" + frequencies);
		
		Map<Transition,Integer>	 costFuncMOSw = new HashMap<Transition, Integer>();
		Map<XEventClass,Integer> costFuncMOTw = new HashMap<XEventClass, Integer>();
		
		// weight transition costs
		for (Map.Entry<Transition,Integer> entryA : this.costFuncMOS.entrySet()) {
			int coef = 0;
			for (Map.Entry<AlignmentStep,Integer> entryB : frequencies.entrySet()) {
				AlignmentStep step = entryB.getKey();
				if (step.type!=StepTypes.MREAL) continue;
				Transition tt = (Transition) step.name;
				Transition t = entryA.getKey();
				if (!tt.equals(t)) continue;
				
				coef = entryB.getValue();
				break;
			}
			
			costFuncMOSw.put(entryA.getKey(), entryA.getValue()*coef);
		}
		
		// weight evClass costs
		for (Map.Entry<XEventClass,Integer> entryA : this.costFuncMOT.entrySet()) {
			int coef = 0;
			for (Map.Entry<AlignmentStep,Integer> entryB : frequencies.entrySet()) {
				AlignmentStep step = entryB.getKey();
				if (step.type!=StepTypes.L) continue;
				XEventClass ecc = (XEventClass) step.name;
				XEventClass ec = entryA.getKey();
				if (!ecc.equals(ec)) continue;
				
				coef = entryB.getValue();
				break;
			}
			
			costFuncMOTw.put(entryA.getKey(), entryA.getValue()*coef);
		}
		
		// get label weights
		costFuncMOSwL.clear();
		costFuncMOTwL.clear();
		
		List<Label> labels = new ArrayList<KnapsackRepairRecommendationSearch.Label>();
		
		for (Map.Entry<Transition,Integer> entry : costFuncMOSw.entrySet()) {
			String label = entry.getKey().getLabel();
			int weight = entry.getValue();
			
			if (weight <= 0) continue;
			
			Integer val = costFuncMOSwL.get(label);
			if (val==null) {
				costFuncMOSwL.put(label, weight);
				labels.add(new Label(label, true));
			}
			else {
				costFuncMOSwL.put(label, val+weight);
			}
		}
		
		for (Map.Entry<XEventClass,Integer> entry : costFuncMOTw.entrySet()) {
			String label = entry.getKey().getId();
			int weight = entry.getValue();
			
			if (weight <= 0) continue;
			
			Integer val = costFuncMOTwL.get(label);
			if (val==null) {
				costFuncMOTwL.put(label, weight);
				labels.add(new Label(label, false));
			}
			else {
				costFuncMOTwL.put(label, val+weight);
			}
		}
		
		
		int N = labels.size();
		int[] profit = new int[N+1];
        int[] weight = new int[N+1];
        
        for (int n=1; n<=N; n++) {
        	Label lb = labels.get(n-1);
        	if (lb.isTransition) {
        		profit[n] = costFuncMOSwL.get(lb.label);
                weight[n] = constraint.getSkipCosts().get(lb.label);	
        	}
        	else {
        		profit[n] = costFuncMOTwL.get(lb.label);
                weight[n] = constraint.getInsertCosts().get(lb.label);
        	}
        }
        
        if (this.debug) System.out.println("DEBUG> Labels: "+labels);
        if (this.debug) System.out.print("DEBUG> Profits: ");
        for (int n=1; n<=N; n++)
        	if (this.debug) System.out.print(profit[n]+" ");
        if (this.debug) System.out.println();
        if (this.debug) System.out.print("DEBUG> Weights: ");
        for (int n=1; n<=N; n++)
        	if (this.debug) System.out.print(weight[n]+" ");
        if (this.debug) System.out.println();

        boolean[] take = Knapsack01DynamicOneSolution.solve(profit, weight, constraint.getAvailableResources());
        
        RepairRecommendation rec = new RepairRecommendation();
        for (int n=1; n<=N; n++) {
        	if (!take[n]) continue;
        	
        	Label lb = labels.get(n-1);
			if (lb.isTransition)
				rec.skipLabels.add(lb.label);
			else
				rec.insertLabels.add(lb.label);
		}
        
        this.optimalRepairRecommendations.add(rec);
		
		Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
		Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
		this.adjustCostFuncMOS(tempMOS,rec.getSkipLabels());
		this.adjustCostFuncMOT(tempMOT,rec.getInsertLabels());
		
		this.optimalCost = this.computeCost(tempMOS, tempMOT);
		
		return this.optimalRepairRecommendations;
	}
	
	public class Label implements Comparable<Label> {
		public boolean isTransition = false;
		public String label = "";
		
		public Label(String label, boolean isT) {
			this.label = label;
			this.isTransition = isT;
		}
		
		@Override
		public String toString() {
			return String.format("[%s,%s]", this.label, this.isTransition ? "+" : "-");
		}

		@Override
		public int compareTo(Label o) {
			double wThis = 0;
			double wO = 0;
			
			if (this.isTransition)
				wThis = costFuncMOSwLperR.get(this.label);
			else
				wThis = costFuncMOTwLperR.get(this.label);
			
			if (o.isTransition)
				wO = costFuncMOSwLperR.get(o.label);
			else
				wO = costFuncMOTwLperR.get(o.label);
				
			if (wThis<wO) 
				return -1;
			if (wThis>wO) 
				return 1;
			
			return 0;
		}
	}
	
	
	
	
}
