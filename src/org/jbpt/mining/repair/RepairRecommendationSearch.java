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
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.model.XLog;
import org.jbpt.petri.NetSystem;
import org.jbpt.petri.Node;
import org.jbpt.petri.io.PNMLSerializer;
import org.jbpt.throwable.SerializationException;
import org.processmining.contexts.uitopia.DummyGlobalContext;
import org.processmining.contexts.uitopia.DummyUIPluginContext;
import org.processmining.models.graphbased.directed.DirectedGraphElement;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.astar.petrinet.PetrinetReplayerWithoutILP;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.replayer.algorithms.IPNReplayParameter;
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedcomplete.CostBasedCompleteParam;
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
	
	protected XEventClassifier eventClassifier = null;
	
	protected DummyUIPluginContext 		context			= null;
	protected TransEvClassMapping		mapping			= null;
	
	protected int 						maxNumOfStates	= Integer.MAX_VALUE;
	protected Set<Transition> 			restrictedTrans = null;
	
	protected Map<Transition,Integer> 	costFuncMOS		= null;
	protected Map<XEventClass,Integer>	costFuncMOT		= null;
	
	protected int alignmentCostComputations = 0;
	protected int optimalCost = Integer.MAX_VALUE;
	protected Set<RepairRecommendation> optimalRepairRecommendations = null;
	
	protected boolean debug = true;
	
	protected RepairRecommendationSearch(
			PetrinetGraph	net, 
			Marking			initMarking, 
			Marking[]		finalMarkings, 
			XLog 			log, 
			Map<Transition,Integer>		costMOS, 
			Map<XEventClass,Integer>	costMOT, 
			TransEvClassMapping			mapping, 
			XEventClassifier eventClassifier, 
			boolean debug) throws Exception {
		
		if (net==null) return;
		
		this.context 			= new DummyUIPluginContext(new DummyGlobalContext(), "label");
		
		this.eventClassifier	= eventClassifier;
		
		this.optimalRepairRecommendations = new HashSet<RepairRecommendation>();
		
		this.net				= net;
		this.initMarking 		= initMarking;
		this.finalMarkings		= finalMarkings;
		this.log				= log;
		
		this.costFuncMOS		= costMOS;
		this.costFuncMOT		= costMOT;
		this.mapping			= mapping;
		this.debug				= debug;
		
		this.restrictedTrans = new HashSet<Transition>();
		
		for (Transition t : this.net.getTransitions()) {
			if (t.isInvisible()) continue;
			this.restrictedTrans.add(t);
		}
	}
	
	public int computeCost() {
		return this.computeCost(this.costFuncMOS,this.costFuncMOT);
	}
	
	public int getNumberOfAlignmentCostComputations() {
		return this.alignmentCostComputations;
	}
	
	public abstract Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint, boolean considerAll);
	
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
	
	public PetrinetGraph repair(RepairRecommendation rec) {		
		Map<Transition,Integer>  tempMOS = new HashMap<Transition,Integer>(this.costFuncMOS);
		Map<XEventClass,Integer> tempMOT = new HashMap<XEventClass,Integer>(this.costFuncMOT);
		this.adjustCostFuncMOS(tempMOS,rec.getSkipLabels());
		this.adjustCostFuncMOT(tempMOT,rec.getInsertLabels());
		
		PetrinetReplayerWithoutILP replayEngine = new PetrinetReplayerWithoutILP();		
		
		IPNReplayParameter parameters = new CostBasedCompleteParam(tempMOT,tempMOS);
		parameters.setInitialMarking(this.initMarking);
		parameters.setFinalMarkings(this.finalMarkings[0]);
		parameters.setGUIMode(false);
		parameters.setCreateConn(false);

		PNRepResult result = null;
		try {
			result = replayEngine.replayLog(this.context, this.net, this.log, this.mapping, parameters);
		} catch (AStarException e1) {
			e1.printStackTrace();
		}
	
		// COLLECT TRANSITIONS TO SKIP
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
		Map<String,Set<Set<org.jbpt.petri.Place>>> labels2markings = new HashMap<String,Set<Set<org.jbpt.petri.Place>>>();
		Map<PetrinetNode,org.jbpt.petri.Node> mapX = new HashMap<PetrinetNode,org.jbpt.petri.Node>();
		Map<org.jbpt.petri.Node,PetrinetNode> mapY = new HashMap<org.jbpt.petri.Node,PetrinetNode>();
		
		NetSystem sys = this.constructNetSystem(this.net, mapX, mapY);
		
		for (SyncReplayResult res : result) {
			this.loadInitialMarking(sys, mapX);
			
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
							markings.add(ps);
							labels2markings.put(e.getId(), markings);
						}
					}
				}
				else {
					Transition t = (Transition) res.getNodeInstance().get(i);
					org.jbpt.petri.Transition tt = (org.jbpt.petri.Transition) mapX.get(t);
					sys.fire(tt);
				}				
			}
		}
		
		Map<String,Set<org.jbpt.petri.Place>> labels2places = new HashMap<String,Set<org.jbpt.petri.Place>>();
		
		for (Map.Entry<String,Set<Set<org.jbpt.petri.Place>>> entry : labels2markings.entrySet()) {
			Set<Set<org.jbpt.petri.Place>> ms = new HashSet<Set<org.jbpt.petri.Place>>(entry.getValue());
			Set<org.jbpt.petri.Place> places = new HashSet<org.jbpt.petri.Place>();
			for (Set<org.jbpt.petri.Place> m : entry.getValue()) places.addAll(m);
			
			Set<org.jbpt.petri.Place> ps	 = new HashSet<org.jbpt.petri.Place>();
			
			// TODO
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
			
			labels2places.put(entry.getKey(),ps);
		}
		
		Map<DirectedGraphElement,DirectedGraphElement> map = new HashMap<DirectedGraphElement,DirectedGraphElement>();
		PetrinetGraph netCopy = PetrinetFactory.clonePetrinet((Petrinet) net, map);
		
		// INSERT LABELS
		for (Map.Entry<String,Set<org.jbpt.petri.Place>> entry : labels2places.entrySet()) {
			for (org.jbpt.petri.Place p : entry.getValue()) {
				Transition tt = netCopy.addTransition(entry.getKey());
				Place pp = (Place) map.get(mapY.get(p));
				netCopy.addArc(pp, tt);
				netCopy.addArc(tt, pp);
			}
		}
		
		// SKIP LABELS
		for (Object o : toSkip) {
			Transition t = (Transition) o;
			
			Transition tt = netCopy.addTransition("");
			tt.setInvisible(true);
			
			for (PetrinetEdge<?,?> edge : netCopy.getInEdges((Transition)map.get(t))) {
				netCopy.addArc((Place)edge.getSource(), tt);
			}
			
			for (PetrinetEdge<?,?> edge : netCopy.getOutEdges((Transition)map.get(t))) {
				netCopy.addArc(tt,(Place)edge.getTarget());
			}
		}
		
		return netCopy;
	}
	
	public PetrinetGraph repairOLD(RepairRecommendation rec) {		
		Map<Transition,Integer>  tempMOS = new HashMap<Transition,Integer>(this.costFuncMOS);
		Map<XEventClass,Integer> tempMOT = new HashMap<XEventClass,Integer>(this.costFuncMOT);
		this.adjustCostFuncMOS(tempMOS,rec.getSkipLabels());
		this.adjustCostFuncMOT(tempMOT,rec.getInsertLabels());
		
		PetrinetReplayerWithoutILP replayEngine = new PetrinetReplayerWithoutILP();		
		
		IPNReplayParameter parameters = new CostBasedCompleteParam(tempMOT,tempMOS);
		parameters.setInitialMarking(this.initMarking);
		parameters.setFinalMarkings(this.finalMarkings[0]);
		parameters.setGUIMode(false);
		parameters.setCreateConn(false);

		PNRepResult result = null;
		try {
			result = replayEngine.replayLog(this.context, this.net, this.log, this.mapping, parameters);
		} catch (AStarException e1) {
			e1.printStackTrace();
		}
	
		// COLLECT TRANSITIONS TO SKIP
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
		Map<String,Set<Set<org.jbpt.petri.Place>>> labels2markings = new HashMap<String,Set<Set<org.jbpt.petri.Place>>>();
		Map<PetrinetNode,org.jbpt.petri.Node> mapX = new HashMap<PetrinetNode,org.jbpt.petri.Node>();
		Map<org.jbpt.petri.Node,PetrinetNode> mapY = new HashMap<org.jbpt.petri.Node,PetrinetNode>();
		
		NetSystem sys = this.constructNetSystem(this.net, mapX, mapY);
		
		org.jbpt.petri.Place start = sys.getSourcePlaces().iterator().next();
		org.jbpt.petri.Place end   = sys.getSinkPlaces().iterator().next();
		
		for (SyncReplayResult res : result) {
			this.loadInitialMarking(sys, mapX);
			
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
							markings.add(ps);
							labels2markings.put(e.getId(), markings);
						}
					}
				}
				else {
					Transition t = (Transition) res.getNodeInstance().get(i);
					org.jbpt.petri.Transition tt = (org.jbpt.petri.Transition) mapX.get(t);
					sys.fire(tt);
				}				
			}
		}
		
		Map<String,Set<org.jbpt.petri.Place>> labels2places = new HashMap<String,Set<org.jbpt.petri.Place>>();
		
		for (Map.Entry<String,Set<Set<org.jbpt.petri.Place>>> entry : labels2markings.entrySet()) {
			Set<Set<org.jbpt.petri.Place>> ms = new HashSet<Set<org.jbpt.petri.Place>>(entry.getValue());
			Set<org.jbpt.petri.Place> places = new HashSet<org.jbpt.petri.Place>();
			for (Set<org.jbpt.petri.Place> m : entry.getValue()) places.addAll(m);
			
			Set<org.jbpt.petri.Place> ps	 = new HashSet<org.jbpt.petri.Place>();
			
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
			
			labels2places.put(entry.getKey(),ps);
		}
		
		// PERFORM REPAIRS
		boolean newStart = true;
		boolean newEnd = true;
		
		Map<DirectedGraphElement,DirectedGraphElement> map = new HashMap<DirectedGraphElement,DirectedGraphElement>();
		PetrinetGraph netCopy = PetrinetFactory.clonePetrinet((Petrinet) net, map);
		
		// INSERT LABELS
		for (Map.Entry<String,Set<org.jbpt.petri.Place>> entry : labels2places.entrySet()) {
			for (org.jbpt.petri.Place p : entry.getValue()) {
				Transition tt = netCopy.addTransition(entry.getKey());
				Place pp = (Place) map.get(mapY.get(p));
				netCopy.addArc(pp, tt);
				netCopy.addArc(tt, pp);
				
				if (p.equals(start) && newStart) {
					Transition ttt = netCopy.addTransition("");
					Place ppp = netCopy.addPlace("");
					netCopy.addArc(ppp, ttt);
					netCopy.addArc(ttt, pp);
					newStart = false;
				}
				
				if (p.equals(end) && newEnd) {
					Transition ttt = netCopy.addTransition("");
					Place ppp = netCopy.addPlace("");
					netCopy.addArc(pp, ttt);
					netCopy.addArc(ttt, ppp);
					newEnd = false;
				}	
			}
		}
		
		// SKIP LABELS
		for (Object o : toSkip) {
			Transition t = (Transition) o;
			
			Transition tt = netCopy.addTransition("");
			tt.setInvisible(true);
			
			for (PetrinetEdge<?,?> edge : netCopy.getInEdges((Transition)map.get(t))) {
				netCopy.addArc((Place)edge.getSource(), tt);
			}
			
			for (PetrinetEdge<?,?> edge : netCopy.getOutEdges((Transition)map.get(t))) {
				netCopy.addArc(tt,(Place)edge.getTarget());
			}
		}
		
		return netCopy;
	}
	
	private void loadInitialMarking(NetSystem sys, Map<PetrinetNode, Node> map) {
		sys.getMarking().clear();
		Set<Place> places = new HashSet<Place>(this.initMarking);
		for (Place p : places)
			sys.putTokens((org.jbpt.petri.Place) map.get(p), Collections.frequency(this.initMarking, p));
	}

	private NetSystem constructNetSystem(PetrinetGraph pn, Map<PetrinetNode,org.jbpt.petri.Node> mapX, Map<Node, PetrinetNode> mapY) 
	{
		NetSystem n = new NetSystem();
		
		for (Place p : pn.getPlaces()) {
			org.jbpt.petri.Place pp = new org.jbpt.petri.Place(p.getLabel());
			mapX.put(p,pp);
			mapY.put(pp,p);
		}
		
		for (Place p : pn.getPlaces()) {
			org.jbpt.petri.Place pp = new org.jbpt.petri.Place(p.getLabel());
			mapX.put(p,pp);
			mapY.put(pp,p);
		}
		
		for (Transition t : pn.getTransitions()) {
			org.jbpt.petri.Transition tt = new org.jbpt.petri.Transition(t.getLabel());
			mapX.put(t,tt);
			mapY.put(tt,t);
		}
		
		for (PetrinetEdge<?,?> edge : pn.getEdges()) {
			if (edge.getSource() instanceof Place)
				n.addFlow((org.jbpt.petri.Place) mapX.get(edge.getSource()),(org.jbpt.petri.Transition)mapX.get(edge.getTarget()));
			else
				n.addFlow((org.jbpt.petri.Transition) mapX.get(edge.getSource()),(org.jbpt.petri.Place)mapX.get(edge.getTarget()));
		}
		
		for (org.jbpt.petri.Place p : n.getPlaces()) {
			if (p.getName().contains("_START"))
				n.putTokens(p,1);
		}
		
		return n;
	}
	
	public void serializeNet(PetrinetGraph pn, String name) throws SerializationException {
		NetSystem n = this.constructNetSystem(pn, new HashMap<PetrinetNode,org.jbpt.petri.Node>(), new HashMap<org.jbpt.petri.Node,PetrinetNode>());
		
		org.jbpt.utils.IOUtils.toFile(name, PNMLSerializer.serializePetriNet(n));
	}
	
	/*public int computeCost(Map<Transition,Integer> costMOS, Map<XEventClass,Integer> costMOT) {
		IPNReplayParameter parameters = new CostBasedCompleteManifestParam(costMOT, costMOS, 
										this.initMarking, this.finalMarkings, this.maxNumOfStates, this.restrictedTrans);
		parameters.setGUIMode(false);
		
		CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		PNRepResult result = replayEngine.replayLog(this.context, this.net, this.log, this.mapping, parameters);
		
		int cost = 0;
		for (SyncReplayResult res : result) {
			cost += res.getInfo().get("Raw Fitness Cost");
		}
		
		this.alignmentCostComputations++;
		
		return cost;
	}*/
	
	public int computeCost(Map<Transition,Integer> costMOS, Map<XEventClass,Integer> costMOT) {
		PetrinetReplayerWithoutILP replayEngine = new PetrinetReplayerWithoutILP();		
		//AllOptAlignmentsGraphAlg replayEngine = new AllOptAlignmentsGraphAlg();
		//AllOptAlignmentsGraphILPAlg replayEngine = new AllOptAlignmentsGraphILPAlg();
		//AllOptAlignmentsTreeAlg replayEngine = new AllOptAlignmentsTreeAlg();
		//CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		
		IPNReplayParameter parameters = new CostBasedCompleteParam(costMOT,costMOS);
		parameters.setInitialMarking(this.initMarking);
		parameters.setFinalMarkings(this.finalMarkings[0]);
		parameters.setGUIMode(false);
		parameters.setCreateConn(false);
		
		/*Object[] parameters = new Object[3];
		parameters[0] = costMOS;
		parameters[2] = costMOT;
		parameters[1] = Integer.MAX_VALUE;*/
				
		int cost = 0;
		try {
			PNRepResult result = replayEngine.replayLog(context, net, log, mapping, parameters);
			
			for (SyncReplayResult res : result) {
				cost += ((int) res.getInfo().get("Raw Fitness Cost").doubleValue()) * res.getTraceIndex().size();
			}
		} catch (AStarException e) {
			e.printStackTrace();
		}
		
		this.alignmentCostComputations++;
		
		return cost;
	}
	
	public Map<AlignmentStep,Integer> computeFrequencies(Map<Transition,Integer> t2c, Map<XEventClass,Integer> e2c) {
		PetrinetReplayerWithoutILP replayEngine = new PetrinetReplayerWithoutILP();		
		//AllOptAlignmentsGraphAlg replayEngine = new AllOptAlignmentsGraphAlg();
		//AllOptAlignmentsGraphILPAlg replayEngine = new AllOptAlignmentsGraphILPAlg();
		//AllOptAlignmentsTreeAlg replayEngine = new AllOptAlignmentsTreeAlg();
		//CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		
		IPNReplayParameter parameters = new CostBasedCompleteParam(e2c,t2c);
		parameters.setInitialMarking(this.initMarking);
		parameters.setFinalMarkings(this.finalMarkings[0]);
		parameters.setGUIMode(false);
		parameters.setCreateConn(false);
		
		/*Object[] parameters = new Object[3];
		parameters[0] = costMOS;
		parameters[2] = costMOT;
		parameters[1] = Integer.MAX_VALUE;*/
				
		Map<AlignmentStep,Integer> map = new HashMap<AlignmentStep, Integer>();
		try {
			PNRepResult result = replayEngine.replayLog(context, net, log, mapping, parameters);
			
			for (SyncReplayResult res : result) {
				List<Object> nodeInstance = res.getNodeInstance();
				List<StepTypes> stepTypes = res.getStepTypes();
				
				for (int i=0; i<nodeInstance.size(); i++) {
					StepTypes type = stepTypes.get(i);
					if (type==StepTypes.LMGOOD) continue;
					
					AlignmentStep step = new AlignmentStep();
					step.name = nodeInstance.get(i);
					step.type = type;
					
					Integer c = map.get(step);
					if (c==null)
						map.put(step,res.getTraceIndex().size());
					else
						map.put(step,c+res.getTraceIndex().size());
				}
			}	
		} catch (AStarException e) {
			e.printStackTrace();
		}
		
		this.alignmentCostComputations++;
		
		return map;
	}
	
	public Map<AlignmentStep,Integer> computeFrequenciesAndCost(Map<Transition,Integer> t2c, Map<XEventClass,Integer> e2c) {
		PetrinetReplayerWithoutILP replayEngine = new PetrinetReplayerWithoutILP();		
		//AllOptAlignmentsGraphAlg replayEngine = new AllOptAlignmentsGraphAlg();
		//AllOptAlignmentsGraphILPAlg replayEngine = new AllOptAlignmentsGraphILPAlg();
		//AllOptAlignmentsTreeAlg replayEngine = new AllOptAlignmentsTreeAlg();
		//CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		
		IPNReplayParameter parameters = new CostBasedCompleteParam(e2c,t2c);
		parameters.setInitialMarking(this.initMarking);
		parameters.setFinalMarkings(this.finalMarkings[0]);
		parameters.setGUIMode(false);
		parameters.setCreateConn(false);
		
		Map<AlignmentStep,Integer> map = new HashMap<AlignmentStep, Integer>();
		try {
			PNRepResult result = replayEngine.replayLog(context, net, log, mapping, parameters);
		
			this.optimalCost = 0;
			for (SyncReplayResult res : result) {
				List<Object> nodeInstance = res.getNodeInstance();
				List<StepTypes> stepTypes = res.getStepTypes();
				
				for (int i=0; i<nodeInstance.size(); i++) {
					StepTypes type = stepTypes.get(i);
					if (type==StepTypes.LMGOOD) continue;
					
					AlignmentStep step = new AlignmentStep();
					step.name = nodeInstance.get(i);
					step.type = type;
					
					Integer c = map.get(step);
					if (c==null)
						map.put(step,res.getTraceIndex().size());
					else
						map.put(step,c+res.getTraceIndex().size());
				}
				
				this.optimalCost += ((int) res.getInfo().get("Raw Fitness Cost").doubleValue()) * res.getTraceIndex().size();
			}	
		} catch (AStarException e) {
			e.printStackTrace();
		}
		
		/*
		 * int cost = 0;
		try {
			PNRepResult result = replayEngine.replayLog(context, net, log, mapping, parameters);
			
			for (SyncReplayResult res : result) {
				cost += res.getInfo().get("Raw Fitness Cost") * res.getTraceIndex().size();
			}
		} catch (AStarException e) {
			e.printStackTrace();
		}
		 */
		
		this.alignmentCostComputations++;
		
		return map;
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
}
