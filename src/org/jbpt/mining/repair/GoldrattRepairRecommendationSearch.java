package org.jbpt.mining.repair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.jbpt.mining.Knapsack01DynamicOneSolution;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.astar.petrinet.manifestreplay.CostBasedCompleteManifestParam;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.replayer.algorithms.IPNReplayParameter;
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedcomplete.CostBasedCompletePruneAlg;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.petrinet.replayresult.StepTypes;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;


/**
 * @author Artem Polyvyanyy
 */
public class GoldrattRepairRecommendationSearch extends RepairRecommendationSearch {
	
		public GoldrattRepairRecommendationSearch(PetrinetGraph	net, 
				Marking			initMarking, 
				Marking[]		finalMarkings, 
				XLog 			log, 
				Map<Transition,Integer>		costMOS, 
				Map<XEventClass,Integer>	costMOT, 
				TransEvClassMapping			mapping, 
				boolean 					outputFlag) throws Exception {
		super(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, outputFlag);
	}
		
	private Map<String,Integer> costFuncMOSwL = new HashMap<String,Integer>();
	private Map<String,Integer> costFuncMOTwL = new HashMap<String,Integer>();
	private Map<String,Double> costFuncMOSwLperR = new HashMap<String,Double>();
	private Map<String,Double> costFuncMOTwLperR = new HashMap<String,Double>();

	@Override
	public Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint) {
		this.alignmentCostComputations	= 0;
		this.optimalRepairRecommendations.clear();
		
		// get misalignment frequencies
		// TODO: check if all optimal alignments are considered
		// TODO: weighted frequencies, based on the number of optimal alignments 
		Map<AlignmentStep,Integer> frequencies = this.computeFrequencies(this.costFuncMOS, this.costFuncMOT);
		System.out.println("FREQUENCIES:" + frequencies);
		
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
		
		System.out.println("T:" + costFuncMOSw);		// weighted costs for MOS
		System.out.println("E:" + costFuncMOTw);		// weighted costs for MOT
		
		// get label weights
		costFuncMOSwL.clear();
		costFuncMOTwL.clear();
		
		List<Label> labels = new ArrayList<GoldrattRepairRecommendationSearch.Label>();
		
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
		
		System.out.println("WLT:" + costFuncMOSwL);		// weighted costs for MOS label
		System.out.println("WLE:" + costFuncMOTwL);		// weighted costs for MOT label
		
		// get per unit costs
		
		/*costFuncMOSwLperR.clear();
		costFuncMOTwLperR.clear();
		
		for (Map.Entry<String,Integer> entry : costFuncMOSwL.entrySet())
			costFuncMOSwLperR.put(entry.getKey(), (double) entry.getValue() / constraint.getSkipCosts().get(entry.getKey()));
		
		for (Map.Entry<String,Integer> entry : costFuncMOTwL.entrySet())
			costFuncMOTwLperR.put(entry.getKey(), (double) entry.getValue() / constraint.getInsertCosts().get(entry.getKey()));
		
		System.out.println("T:" + costFuncMOSwLperR);		// weighted costs for MOS label per resource
		System.out.println("E:" + costFuncMOTwLperR);		// weighted costs for MOT label per resource*/
		
		int N = labels.size();
		int[] profit = new int[N+1];
        int[] weight = new int[N+1];
        
        for (int n=1; n<=N; n++) {
        	Label lb = labels.get(n-1);
        	if (lb.isTransition) {
        		profit[n] = costFuncMOSwL.get(lb.label);
        		double d = ((double) constraint.getSkipCosts().get(lb.label));
                weight[n] = (int) d;															// TODO	
        	}
        	else {
        		profit[n] = costFuncMOTwL.get(lb.label);
                weight[n] = (int)((double) constraint.getInsertCosts().get(lb.label));			// TODO
        	}
        }
        
        System.out.println("LABELS: "+labels);
        System.out.print("PROFIT: ");
        for (int n=1; n<=N; n++)
        	System.out.print(profit[n]+" ");
        System.out.println();
        System.out.print("WEIGHT: ");
        for (int n=1; n<=N; n++)
        	System.out.print(weight[n]+" ");
        System.out.println();
        
        // TODO: get all solutions to the Knapsack problem 
        boolean[] take = Knapsack01DynamicOneSolution.solve(profit, weight, (int) constraint.getAvailableResources());
        
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
		
		rec = this.optimalRepairRecommendations.iterator().next();
		
		Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
		Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
		this.adjustCostFuncMOS(tempMOS,rec.getSkipLabels());
		this.adjustCostFuncMOT(tempMOT,rec.getInsertLabels());
		
		this.optimalCost = this.computeCost(tempMOS, tempMOT);
		this.alignmentCostComputations++;
		
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
	
	public class AlignmentStep {
		public Object	 name = null;
		public StepTypes type = null;
		
		public int hashCode() {
			int result = name.hashCode()+11*type.hashCode(); 
			return result;
		}
		
		@Override
		public String toString() {
			return String.format("(%s,%s)",this.name.toString(),this.type);
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof AlignmentStep)) return false;
			AlignmentStep step = (AlignmentStep) obj;
			if (step.name.equals(this.name) && step.type==this.type) 
				return true;
			
			return false;
		}
	}
	
	public Map<AlignmentStep,Integer> computeFrequencies(Map<Transition,Integer> t2c, Map<XEventClass,Integer> e2c) {
		IPNReplayParameter parameters = new CostBasedCompleteManifestParam(e2c, t2c, 
										this.initMarking, this.finalMarkings, this.maxNumOfStates, this.restrictedTrans);
		parameters.setGUIMode(false);
		
		CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		
		this.alignmentCostComputations += 1;
		
		PNRepResult result = replayEngine.replayLog(this.context, this.net, this.log, this.mapping, parameters);
		
		Map<AlignmentStep,Integer> map = new HashMap<AlignmentStep, Integer>();
		for (SyncReplayResult res : result) {
			
			System.out.println("==============================="); 
			for (XEvent e: log.get(res.getTraceIndex().first()))
				System.out.print(e.getAttributes().get("concept:name")+", ");
			System.out.println();
			System.out.println(res.getInfo());
			System.out.println(res.getNodeInstance());
			System.out.println(res.getStepTypes());
			
			System.out.println("===============================");
			
			for (int i=0; i<res.getNodeInstance().size(); i++) {
				StepTypes type = res.getStepTypes().get(i);
				if (type==StepTypes.LMGOOD) continue;
				
				AlignmentStep step = new AlignmentStep();
				step.name = res.getNodeInstance().get(i);
				step.type = type;
				
				Integer c = map.get(step);
				if (c==null)
					map.put(step,1);
				else
					map.put(step, map.get(step)+1);
			}
		}
		
		return map;
	}
}
