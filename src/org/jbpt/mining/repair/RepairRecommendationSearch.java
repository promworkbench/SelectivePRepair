package org.jbpt.mining.repair;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.DummyGlobalContext;
import org.processmining.contexts.uitopia.DummyUIPluginContext;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.astar.petrinet.manifestreplay.CostBasedCompleteManifestParam;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.replayer.algorithms.IPNReplayParameter;
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedcomplete.CostBasedCompletePruneAlg;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;


/**
 * @author Artem Polyvyanyy
 */
public abstract class RepairRecommendationSearch {
	protected XLog 			log				= null; // log to use
	protected PetrinetGraph	net				= null; // net to use
	protected Marking		initMarking		= null; // initial marking
	protected Marking[]		finalMarkings	= null; // set of final markings
	
	protected DummyUIPluginContext 		context			= null;
	protected TransEvClassMapping		mapping			= null;
	
	protected int 						maxNumOfStates	= Integer.MAX_VALUE;
	protected Set<Transition> 			restrictedTrans = null;
	
	protected Map<Transition,Integer> 	costFuncMOS		= null;
	protected Map<XEventClass,Integer>	costFuncMOT		= null;
	
	protected int alignmentCostComputations = 0;
	protected int optimalCost = Integer.MAX_VALUE;
	protected Set<RepairRecommendation> optimalRepairRecommendations = null;
	
	protected boolean outputFlag = false;
	
	protected RepairRecommendationSearch(
			PetrinetGraph	net, 
			Marking			initMarking, 
			Marking[]		finalMarkings, 
			XLog 			log, 
			Map<Transition,Integer>		costMOS, 
			Map<XEventClass,Integer>	costMOT, 
			TransEvClassMapping			mapping, 
			boolean 					outputFlag) throws Exception {
		
		if (net==null) return;
		
		this.context 			= new DummyUIPluginContext(new DummyGlobalContext(), "label");
		
		this.optimalRepairRecommendations = new HashSet<RepairRecommendation>();
		
		this.net				= net;
		this.initMarking 		= initMarking;
		this.finalMarkings		= finalMarkings;
		this.log				= log;
		
		this.costFuncMOS		= costMOS;
		this.costFuncMOT		= costMOT;
		this.mapping			= mapping;
		this.outputFlag			= outputFlag;
		
		this.restrictedTrans = new HashSet<Transition>();
		
		for (Transition t : this.net.getTransitions()) {
			if (t.isInvisible()) continue;
			this.restrictedTrans.add(t);
		}
	}
	
	public int computeCost() {
		return this.computeCost(this.costFuncMOS,this.costFuncMOT);
	}
	
	public int computeCost(Map<Transition,Integer> costMOS, Map<XEventClass,Integer> costMOT) {
		IPNReplayParameter parameters = new CostBasedCompleteManifestParam(costMOT, costMOS, 
										this.initMarking, this.finalMarkings, this.maxNumOfStates, this.restrictedTrans);
		parameters.setGUIMode(false);
		
		CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		PNRepResult result = replayEngine.replayLog(this.context, this.net, this.log, this.mapping, parameters);
		
		int cost = 0;
		for (SyncReplayResult res : result) {
			cost += res.getInfo().get("Raw Fitness Cost");
		}
		
		return cost;
	}
	
	public int getNumberOfAlignmentCostComputations() {
		return this.alignmentCostComputations;
	}
	
	public abstract Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint);
	
	public int getOptimalCost() {
		return optimalCost;
	}
	
	protected void preserveMinimalOptimalRepairRecommendations() {
		Set<RepairRecommendation> toRemove = new HashSet<RepairRecommendation>();
		
		for (RepairRecommendation r : this.optimalRepairRecommendations) {
			for (RepairRecommendation x : this.optimalRepairRecommendations) {
				if (r.equals(x)) continue;
				
				if (r.contains(x)) {
					toRemove.add(r);
					break;
				}
			}
		}
		
		this.optimalRepairRecommendations.removeAll(toRemove);
	}
	
	protected void backtrackOptimalRepairRecommendations() {
		Set<RepairRecommendation> visited = new HashSet<RepairRecommendation>();
		Queue<RepairRecommendation> toVisit = new ConcurrentLinkedQueue<RepairRecommendation>(this.optimalRepairRecommendations); 
		Set<RepairRecommendation> optimal = new HashSet<RepairRecommendation>();
		
		while (!toVisit.isEmpty()) {
			RepairRecommendation r = toVisit.poll();
			visited.add(r);
			
			boolean isOptimal = true;
			
			// skip labels
			for (String label : r.skipLabels) {
				RepairRecommendation rec = r.clone();
				rec.skipLabels.remove(label);
				
				Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
				Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
				this.adjustCostFuncMOS(tempMOS,rec.getSkipLabels());
				this.adjustCostFuncMOT(tempMOT,rec.getInsertLabels());
				
				int cost = this.computeCost(tempMOS, tempMOT);
				this.alignmentCostComputations++;
				
				if (cost==this.getOptimalCost()) {
					if (!visited.contains(rec) && !toVisit.contains(rec)) toVisit.add(rec);
					isOptimal = false;
				}
			}
			
			// insert labels
			for (String label : r.insertLabels) {
				RepairRecommendation rec = r.clone();
				rec.insertLabels.remove(label);
				
				Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
				Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
				this.adjustCostFuncMOS(tempMOS,rec.getSkipLabels());
				this.adjustCostFuncMOT(tempMOT,rec.getInsertLabels());
				
				int cost = this.computeCost(tempMOS, tempMOT);
				this.alignmentCostComputations++;
				
				if (cost==this.getOptimalCost()) {
					if (!visited.contains(rec) && !toVisit.contains(rec)) toVisit.add(rec);
					isOptimal = false;
				}
			}
			
			if (isOptimal)
				optimal.add(r);
			
		}
		
		this.optimalRepairRecommendations.clear();
		this.optimalRepairRecommendations.addAll(optimal);
	}
	
	protected void adjustCostFuncMOS(Map<Transition,Integer> costFunc, Set<String> labels) {
		Set<Transition> ts = new HashSet<Transition>();
		
		for (Map.Entry<Transition,Integer> entry : costFunc.entrySet()) {
			if (labels.contains(entry.getKey().getLabel())) {
				ts.add(entry.getKey());
			}
		}
		
		for (Transition t : ts) {
			costFunc.put(t,0);
		}
	}
	
	protected void adjustCostFuncMOT(Map<XEventClass, Integer> costFunc, Set<String> labels) { 
		Set<XEventClass> es = new HashSet<XEventClass>();
		
		for (Map.Entry<XEventClass,Integer> entry : costFunc.entrySet()) {
			if (labels.contains(entry.getKey().getId())) {
				es.add(entry.getKey());
			}
		}
		
		for (XEventClass e : es) {
			costFunc.put(e,0);
		}
	}
	
	protected Set<String> getLabels() {
		Set<String> result = CostFunction.getLabels(this.net);
		
		for (XEventClass event : this.costFuncMOT.keySet()) {
			result.add(event.getId());
		}
		
		return result;
	}
	
}
