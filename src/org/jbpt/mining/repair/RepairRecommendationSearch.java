package org.jbpt.mining.repair;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.model.XLog;
import org.jbpt.petri.NetSystem;
import org.jbpt.petri.Node;
import org.jbpt.petri.io.PNMLSerializer;
import org.jbpt.throwable.SerializationException;
import org.processmining.contexts.uitopia.DummyGlobalContext;
import org.processmining.contexts.uitopia.DummyUIPluginContext;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
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
	
	public void repair(RepairRecommendation rec) {
		System.out.println("----------------------------");
		System.out.println(" START PROCESS MODEL REPAIR ");
		System.out.println("----------------------------");
		
		IPNReplayParameter parameters = new CostBasedCompleteManifestParam(this.costFuncMOT, this.costFuncMOS,
					this.initMarking, this.finalMarkings, this.maxNumOfStates, this.restrictedTrans);
		
		parameters.setGUIMode(false);

		CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();

		PNRepResult result = replayEngine.replayLog(this.context, this.net, this.log, this.mapping, parameters);

		// REPAIR MOVES ON MODEL (skip labels)
		
		Set<Object> toSkip = new HashSet<Object>();
		if (!rec.getSkipLabels().isEmpty()) { 
			for (SyncReplayResult res : result) {
				for (int i=0; i<res.getStepTypes().size(); i++) {
					if (res.getStepTypes().get(i)==StepTypes.MREAL) {
						Object obj = res.getNodeInstance().get(i);
						if (rec.getSkipLabels().contains(obj.toString()))
							toSkip.add(obj);
					}
				}
			}	
		}
		
		// REPAIR MOVES ON TRACE (insert labels)
		Set<Set<org.jbpt.petri.Place>> markings = new HashSet<Set<org.jbpt.petri.Place>>();
		Map<PetrinetNode,org.jbpt.petri.Node> map = new HashMap<PetrinetNode,org.jbpt.petri.Node>();
		NetSystem sys = this.constructNetSystem(map);
		
		for (SyncReplayResult res : result) {
			this.loadInitialMarking(sys, map);
			
			for (int i=0; i<res.getStepTypes().size(); i++) {
				StepTypes type = res.getStepTypes().get(i);
				
				if (type==StepTypes.L) {
					XEventClass e = (XEventClass) res.getNodeInstance().get(i);
					if (rec.getInsertLabels().contains(e.getId())) {
						markings.add(sys.getMarkedPlaces());
					}
				}
				else {
					Transition t = (Transition) res.getNodeInstance().get(i);
					org.jbpt.petri.Transition tt = (org.jbpt.petri.Transition) map.get(t);
					sys.fire(tt);
				}				
			}
			
			/*System.out.println(res.getNodeInstance());
			System.out.println(res.getNodeInstance().size());
			System.out.println(res.getStepTypes());
			System.out.println(res.getStepTypes().size());
			System.out.println("----");*/
		}
		
		// TODO finish insert labels repairs
		System.out.println(markings);
		
		
		
		// add skips
		for (Object o : toSkip) {
			Transition t = (Transition) o;
			
			Transition tt = this.net.addTransition("");
			tt.setInvisible(true);
			
			for (PetrinetEdge<?,?> edge : net.getInEdges(t)) {
				net.addArc((Place)edge.getSource(), tt);
			}
			
			for (PetrinetEdge<?,?> edge : net.getOutEdges(t)) {
				net.addArc(tt,(Place)edge.getTarget());
			}
		}
	}
	
	private void loadInitialMarking(NetSystem sys, Map<PetrinetNode, Node> map) {
		sys.getMarking().clear();
		Set<Place> places = new HashSet<Place>(this.initMarking);
		for (Place p : places)
			sys.putTokens((org.jbpt.petri.Place) map.get(p), Collections.frequency(this.initMarking, p));
	}

	private NetSystem constructNetSystem(Map<PetrinetNode,org.jbpt.petri.Node> map) {
		NetSystem n = new NetSystem();
		
		for (Place p : net.getPlaces()) {
			org.jbpt.petri.Place pp = new org.jbpt.petri.Place(p.getLabel());
			map.put(p,pp);
		}
		
		for (Place p : net.getPlaces()) {
			org.jbpt.petri.Place pp = new org.jbpt.petri.Place(p.getLabel());
			map.put(p,pp);
		}
		
		for (Transition t : net.getTransitions()) {
			org.jbpt.petri.Transition tt = new org.jbpt.petri.Transition(t.getLabel());
			map.put(t,tt);
		}
		
		for (PetrinetEdge<?,?> edge : net.getEdges()) {
			if (edge.getSource() instanceof Place)
				n.addFlow((org.jbpt.petri.Place) map.get(edge.getSource()),(org.jbpt.petri.Transition)map.get(edge.getTarget()));
			else
				n.addFlow((org.jbpt.petri.Transition) map.get(edge.getSource()),(org.jbpt.petri.Place)map.get(edge.getTarget()));
		}
		
		return n;
	}
	
	public void serializeNet(String name) throws SerializationException {
		NetSystem n = this.constructNetSystem(new HashMap<PetrinetNode,org.jbpt.petri.Node>());
		
		org.jbpt.utils.IOUtils.toFile(name+".pnml", PNMLSerializer.serializePetriNet(n));
	}
	
}
