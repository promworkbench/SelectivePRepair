package org.jbpt.mining.repair;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import nl.tue.astar.AStarException;

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
import org.processmining.plugins.petrinet.replayer.matchinstances.algorithms.express.AllOptAlignmentsGraphAlg;
import org.processmining.plugins.petrinet.replayresult.PNMatchInstancesRepResult;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.petrinet.replayresult.StepTypes;
import org.processmining.plugins.replayer.replayresult.AllSyncReplayResult;
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
		int count = 0;
		for (SyncReplayResult res : result) {
			cost += res.getInfo().get("Raw Fitness Cost");
			count++;
		}
		
		System.out.println(count);
		
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
		
		Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
		Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
		this.adjustCostFuncMOS(tempMOS,rec.getSkipLabels());
		this.adjustCostFuncMOT(tempMOT,rec.getInsertLabels());
		
		IPNReplayParameter parameters = new CostBasedCompleteManifestParam(tempMOT, tempMOS,
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
		
		// TODO this is debug code
		for (Transition t: this.net.getTransitions()) {
			if (rec.getSkipLabels().contains(t.getLabel()))
				toSkip.add(t);
		}
		System.out.println("SKIP: "+toSkip);
		
		// REPAIR MOVES ON TRACE (insert labels)
		Map<String,Set<Set<org.jbpt.petri.Place>>> labels2markings = new HashMap<String,Set<Set<org.jbpt.petri.Place>>>();
		Map<PetrinetNode,org.jbpt.petri.Node> map = new HashMap<PetrinetNode,org.jbpt.petri.Node>();
		Map<org.jbpt.petri.Node,PetrinetNode> map2 = new HashMap<org.jbpt.petri.Node,PetrinetNode>();
		NetSystem sys = this.constructNetSystem(map,map2);
		
		org.jbpt.petri.Place start = sys.getSourcePlaces().iterator().next();
		org.jbpt.petri.Place end = sys.getSinkPlaces().iterator().next();
		
		for (SyncReplayResult res : result) {
			this.loadInitialMarking(sys, map);
			
			for (int i=0; i<res.getStepTypes().size(); i++) {
				StepTypes type = res.getStepTypes().get(i);
				
				if (type==StepTypes.L) {
					XEventClass e = (XEventClass) res.getNodeInstance().get(i);
					if (rec.getInsertLabels().contains(e.getId())) {
						Set<org.jbpt.petri.Place> ps = new HashSet<org.jbpt.petri.Place>(sys.getMarkedPlaces());
						
						if (labels2markings.containsKey(e.getId()))
							labels2markings.get(e.getId()).add(ps);
						else {
							Set<Set<org.jbpt.petri.Place>> markings = new HashSet<Set<org.jbpt.petri.Place>>();
							labels2markings.put(e.getId(), markings);
						}
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
		
		// TODO this can be optimized
		System.out.println(labels2markings);
		Map<String,Set<org.jbpt.petri.Place>> labels2places = new HashMap<String,Set<org.jbpt.petri.Place>>();
		
		for (Map.Entry<String,Set<Set<org.jbpt.petri.Place>>> entry : labels2markings.entrySet()) {
			Set<org.jbpt.petri.Place> places = new HashSet<org.jbpt.petri.Place>();
			Set<org.jbpt.petri.Place> ps = new HashSet<org.jbpt.petri.Place>();
			for (Set<org.jbpt.petri.Place> m : entry.getValue()) places.addAll(m);
			Set<org.jbpt.petri.Place> places2 = new HashSet<org.jbpt.petri.Place>(places);
			
			Set<Set<org.jbpt.petri.Place>> ms = new HashSet<Set<org.jbpt.petri.Place>>(entry.getValue());
			while (!ms.isEmpty()) {
				int max = Integer.MIN_VALUE;
				org.jbpt.petri.Place c = null;
				
				for (org.jbpt.petri.Place cc : places) {
					int count = 0;
					for (Set<org.jbpt.petri.Place> m : ms) {
						if (m.contains(cc)) count++;
					}
					
					if (count>max) {
						max = count;
						c=cc;
					}
				}
				
				ps.add(c);
				places.remove(c);
				Set<Set<org.jbpt.petri.Place>> toRemove = new HashSet<Set<org.jbpt.petri.Place>>();
				for (Set<org.jbpt.petri.Place> m : ms) {
					if (m.contains(c))
						toRemove.add(m);
				}
				
				ms.removeAll(toRemove);
			}
			
			labels2places.put(entry.getKey(),ps); // TODO should be ps instead of places2
		}
		
		System.out.println(labels2places);
		
		boolean newStart = true;
		boolean newEnd = true;
		
		// add inserts
		for (Map.Entry<String,Set<org.jbpt.petri.Place>> entry : labels2places.entrySet()) {
			for (org.jbpt.petri.Place p : entry.getValue()) {
				Transition tt = this.net.addTransition(entry.getKey());
				Place pp = (Place) map2.get(p);
				this.net.addArc(pp, tt);
				this.net.addArc(tt, pp);
				
				if (p.equals(start) && newStart) {
					Transition ttt = this.net.addTransition("invisible");
					Place ppp = this.net.addPlace("");
					this.net.addArc(ppp, ttt);
					this.net.addArc(ttt, pp);
					newStart = false;
				}
				
				if (p.equals(end) && newEnd) {
					Transition ttt = this.net.addTransition("invisible");
					Place ppp = this.net.addPlace("");
					this.net.addArc(pp, ttt);
					this.net.addArc(ttt, ppp);
					newEnd = false;
				}
				
			}
		}
		
		// add skips
		for (Object o : toSkip) {
			Transition t = (Transition) o;
			
			Transition tt = this.net.addTransition("invisible");
			tt.setInvisible(true);
			
			for (PetrinetEdge<?,?> edge : this.net.getInEdges(t)) {
				this.net.addArc((Place)edge.getSource(), tt);
			}
			
			for (PetrinetEdge<?,?> edge : this.net.getOutEdges(t)) {
				this.net.addArc(tt,(Place)edge.getTarget());
			}
		}
		
		// TODO overhead!
		/*for (Place p : this.net.getPlaces()) {
			if (this.net.getInEdges(p).isEmpty() || this.net.getOutEdges(p).isEmpty()) continue;
			
			for (String label : rec.getInsertLabels()) {
				Transition tt = this.net.addTransition(label);
				this.net.addArc(p, tt);
				this.net.addArc(tt, p);
			}
		}*/
		
		System.out.println("----------------------------");
	}
	
	private void loadInitialMarking(NetSystem sys, Map<PetrinetNode, Node> map) {
		sys.getMarking().clear();
		Set<Place> places = new HashSet<Place>(this.initMarking);
		for (Place p : places)
			sys.putTokens((org.jbpt.petri.Place) map.get(p), Collections.frequency(this.initMarking, p));
	}

	private NetSystem constructNetSystem(Map<PetrinetNode,org.jbpt.petri.Node> map, Map<Node, PetrinetNode> map2) {
		NetSystem n = new NetSystem();
		
		for (Place p : net.getPlaces()) {
			org.jbpt.petri.Place pp = new org.jbpt.petri.Place(p.getLabel());
			map.put(p,pp);
			map2.put(pp,p);
		}
		
		for (Place p : net.getPlaces()) {
			org.jbpt.petri.Place pp = new org.jbpt.petri.Place(p.getLabel());
			map.put(p,pp);
			map2.put(pp,p);
		}
		
		for (Transition t : net.getTransitions()) {
			org.jbpt.petri.Transition tt = new org.jbpt.petri.Transition(t.getLabel());
			map.put(t,tt);
			map2.put(tt,t);
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
		NetSystem n = this.constructNetSystem(new HashMap<PetrinetNode,org.jbpt.petri.Node>(), new HashMap<org.jbpt.petri.Node,PetrinetNode>());
		
		org.jbpt.utils.IOUtils.toFile(name+".pnml", PNMLSerializer.serializePetriNet(n));
	}
	
	public Map<AlignmentStep,Integer> computeFrequencies(Map<Transition,Integer> t2c, Map<XEventClass,Integer> e2c) {
		//IPNReplayParameter parameters = new CostBasedCompleteManifestParam(e2c, t2c, this.initMarking, this.finalMarkings, this.maxNumOfStates, this.restrictedTrans);
		//rameters.setGUIMode(false);
		
		AllOptAlignmentsGraphAlg replayEngine = new AllOptAlignmentsGraphAlg();
		//AllOptAlignmentsGraphILPAlg replayEngine = new AllOptAlignmentsGraphILPAlg();
		//AllOptAlignmentsTreeAlg replayEngine = new AllOptAlignmentsTreeAlg();
		//CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		this.alignmentCostComputations += 1;
		
		Object[] parameters = new Object[3];
		parameters[0] = t2c;
		parameters[2] = e2c;
		parameters[1] = Integer.MAX_VALUE;
		
		PNMatchInstancesRepResult result = null;
		try {
			result = replayEngine.replayLog(this.context, this.net, this.initMarking, this.finalMarkings[0], this.log, this.mapping, parameters);
		} catch (AStarException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//PNRepResult result = replayEngine.replayLog(this.context, this.net, this.log, this.mapping, parameters);
		
		Map<AlignmentStep,Integer> map = new HashMap<AlignmentStep, Integer>();
		for (AllSyncReplayResult res : result) {
			/*System.out.println("==============================="); 
			for (XEvent e: log.get(res.getTraceIndex().first()))
				System.out.print(e.getAttributes().get("concept:name")+", ");
			System.out.println();
			System.out.println(res.getInfo());
			System.out.println(res.getNodeInstance());
			System.out.println(res.getStepTypes());
			
			System.out.println("===============================");*/
			
			List<Object> nodeInstance = res.getNodeInstanceLst().iterator().next();
			List<StepTypes> stepTypes = res.getStepTypesLst().iterator().next();
			for (int i=0; i<nodeInstance.size(); i++) {
				StepTypes type = stepTypes.get(i);
				if (type==StepTypes.LMGOOD) continue;
				
				AlignmentStep step = new AlignmentStep();
				step.name = nodeInstance.get(i);
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
	
	/*public Map<AlignmentStep,Integer> computeFrequencies(Map<Transition,Integer> t2c, Map<XEventClass,Integer> e2c) {		
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
	}*/
	
	/*public Map<AlignmentStep,Integer> computeFrequencies(Map<Transition,Integer> t2c, Map<XEventClass,Integer> e2c) {
		IPNReplayParameter parameters = new CostBasedCompleteManifestParam(e2c, t2c, 
										this.initMarking, this.finalMarkings, this.maxNumOfStates, this.restrictedTrans);
		parameters.setGUIMode(false);
		
		CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		
		this.alignmentCostComputations += 1;
		
		PNRepResult result = replayEngine.replayLog(this.context, this.net, this.log, this.mapping, parameters);
		
		Map<AlignmentStep,Integer> map = new HashMap<AlignmentStep, Integer>();
		for (SyncReplayResult res : result) {
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
	}*/
	
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
}
