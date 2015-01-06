package org.jbpt.mining.repair;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

import nl.tue.astar.AStarException;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.model.XLog;
import org.jbpt.mining.HittingSets;
import org.jbpt.petri.NetSystem;
import org.jbpt.petri.Node;
import org.jbpt.petri.io.PNMLSerializer;
import org.jbpt.throwable.SerializationException;
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
	
	protected int alignmentComputations = 0;
	protected int optimalAlignmentCost = Integer.MAX_VALUE;
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
		
		this.optimalRepairRecommendations = new HashSet<RepairRecommendation>();
		
		this.net				= net;
		this.log				= log;
		this.initMarking 		= initMarking;
		this.finalMarkings		= finalMarkings;
		this.eventClassifier	= eventClassifier;
		//this.context 			= new DummyUIPluginContext(new DummyGlobalContext(), "label");
		
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
	
	public Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint) {
		return this.computeOptimalRepairRecommendations(constraint,true);
	}
	
	public abstract Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint, boolean singleton);
	
	public int getNumberOfAlignmentComputations() {
		return this.alignmentComputations;
	}
	
	public int getOptimalAlignmentCost() {
		return this.optimalAlignmentCost;
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
	
	protected void minimizeOptimalRepairRecommendations() {
		Set<RepairRecommendation>	visited = new HashSet<RepairRecommendation>();
		Queue<RepairRecommendation> toVisit = new ConcurrentLinkedQueue<RepairRecommendation>(this.optimalRepairRecommendations); 
		Set<RepairRecommendation>	optimal = new HashSet<RepairRecommendation>();
		
		while (!toVisit.isEmpty()) {
			RepairRecommendation r = toVisit.poll();
			visited.add(r);
			boolean isMinimal = true;
			
			// skip labels
			for (String label : r.skipLabels) {
				RepairRecommendation rec = r.clone();
				rec.skipLabels.remove(label);
				
				Map<Transition,Integer>  tempMOS = this.getAdjustedCostFuncMOS(rec.getSkipLabels());
				Map<XEventClass,Integer> tempMOT = this.getAdjustedCostFuncMOT(rec.getInsertLabels());
				int cost = this.computeAlignmentCost(tempMOS, tempMOT);
				
				if (cost==this.optimalAlignmentCost) {
					if (!visited.contains(rec)) toVisit.add(rec); // && !toVisit.contains(rec)
					isMinimal = false;
				}
			}
			
			// insert labels
			for (String label : r.insertLabels) {
				RepairRecommendation rec = r.clone();
				rec.insertLabels.remove(label);
				
				Map<Transition,Integer>  tempMOS = this.getAdjustedCostFuncMOS(rec.getSkipLabels());
				Map<XEventClass,Integer> tempMOT = this.getAdjustedCostFuncMOT(rec.getInsertLabels());
				int cost = this.computeAlignmentCost(tempMOS, tempMOT);
				
				if (cost==this.optimalAlignmentCost) {
					if (!visited.contains(rec)) toVisit.add(rec); //  && !toVisit.contains(rec)
					isMinimal = false;
				}
			}
			
			if (isMinimal) optimal.add(r);
			
		}
		
		this.optimalRepairRecommendations.clear();
		this.optimalRepairRecommendations.addAll(optimal);
	}
	
	protected Map<Transition,Integer> getAdjustedCostFuncMOS(Set<String> labels) {
		Map<Transition,Integer> result = new HashMap<Transition,Integer>(this.costFuncMOS);
		
		Set<Transition> ts = new HashSet<Transition>();
		
		for (Map.Entry<Transition,Integer> entry : result.entrySet()) {
			if (labels.contains(entry.getKey().getLabel())) {
				ts.add(entry.getKey());
			}
		}
		
		for (Transition t : ts)	result.put(t,0);
		
		return result;
	}
	
	protected Map<XEventClass,Integer> getAdjustedCostFuncMOT(Set<String> labels) {
		Map<XEventClass,Integer> result	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
		
		Set<XEventClass> es = new HashSet<XEventClass>();
		
		for (Map.Entry<XEventClass,Integer> entry : result.entrySet()) {
			if (labels.contains(entry.getKey().getId())) {
				es.add(entry.getKey());
			}
		}
		
		for (XEventClass e : es) {
			result.put(e,0);
		}
		
		return result;
	}
	
	public PetrinetGraph repair(RepairRecommendation rec) {		
		Map<Transition,Integer>  tempMOS = this.getAdjustedCostFuncMOS(rec.getSkipLabels());
		Map<XEventClass,Integer> tempMOT = this.getAdjustedCostFuncMOT(rec.getInsertLabels());
		
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
			
			Vector<Vector<org.jbpt.petri.Place>> v = new Vector<Vector<org.jbpt.petri.Place>>();
			for (Set<org.jbpt.petri.Place> m : entry.getValue()) {
				Vector<org.jbpt.petri.Place> a = new Vector<org.jbpt.petri.Place>(m);
				v.add(a);
			}
			
			HittingSets hs = new HittingSets(v); // selects random minimal hitting set 
			
			int max = Integer.MAX_VALUE;
			Vector<org.jbpt.petri.Place> ps = null;
			@SuppressWarnings("unchecked")
			Vector<Vector<org.jbpt.petri.Place>> s = hs.getSets();
			for (int i=0; i<s.size(); i++) {
				Vector<org.jbpt.petri.Place> k = s.get(i);
				if (k.size()<max) {
					max = k.size();
					ps = k;
				}
			}
						
			labels2places.put(entry.getKey(),new HashSet<org.jbpt.petri.Place>(ps));
		}
		
		// PERFORM REPAIRS
		
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
	
	public int computeAlignmentCost(Map<Transition,Integer> transitions2costs, Map<XEventClass,Integer> events2costs) {
		int cost = 0;
		PNRepResult result = this.getPNReplayResults(transitions2costs, events2costs);
			
		for (SyncReplayResult res : result) {
			cost += ((int) res.getInfo().get("Raw Fitness Cost").doubleValue()) * res.getTraceIndex().size();
		}
		
		return cost;
	}
	
	public int computeAlignmentCost() {
		return this.computeAlignmentCost(this.costFuncMOS,this.costFuncMOT);
	}
	
	public int computeAlignmentCost(RepairRecommendation rec) {
		Map<Transition,Integer>  tempMOS	= this.getAdjustedCostFuncMOS(rec.getSkipLabels());
		Map<XEventClass,Integer> tempMOT	= this.getAdjustedCostFuncMOT(rec.getInsertLabels());
		
		return this.computeAlignmentCost(tempMOS,tempMOT);
	}
	
	public Map<AlignmentStep,Integer> computeMovementFrequencies(Map<Transition,Integer> transitions2costs, Map<XEventClass,Integer> events2costs) {
		Map<AlignmentStep,Integer> map = new HashMap<AlignmentStep, Integer>();
		PNRepResult result = this.getPNReplayResults(transitions2costs, events2costs);
		
		for (SyncReplayResult res : result) {
			List<Object> nodeInstance = res.getNodeInstance();
			List<StepTypes> stepTypes = res.getStepTypes();
			
			for (int i=0; i<nodeInstance.size(); i++) {
				StepTypes type = stepTypes.get(i);
				if (type==StepTypes.LMGOOD) continue;
				
				AlignmentStep step = new AlignmentStep(nodeInstance.get(i),type);
				
				Integer c = map.get(step);
				if (c==null)
					map.put(step,res.getTraceIndex().size());
				else
					map.put(step,c+res.getTraceIndex().size());
			}
		}	
		
		return map;
	}
	
	private PNRepResult getPNReplayResults(Map<Transition,Integer> transitions2costs, Map<XEventClass,Integer> events2costs) {
		PetrinetReplayerWithoutILP replayEngine = new PetrinetReplayerWithoutILP();		
		
		IPNReplayParameter parameters = new CostBasedCompleteParam(events2costs,transitions2costs);
		parameters.setInitialMarking(this.initMarking);
		parameters.setFinalMarkings(this.finalMarkings[0]);
		parameters.setGUIMode(false);
		parameters.setCreateConn(false);
		
		PNRepResult result = null;
		try {
			result = replayEngine.replayLog(context, net, log, mapping, parameters);
			this.alignmentComputations++;
		} catch (AStarException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public Map<AlignmentStep,Integer> computeMovementFrequenciesAndAlignmentCost(Map<Transition,Integer> transitions2costs, Map<XEventClass,Integer> events2costs) {		
		Map<AlignmentStep,Integer> map = new HashMap<AlignmentStep, Integer>();
		PNRepResult result = this.getPNReplayResults(transitions2costs,events2costs);
		
		this.optimalAlignmentCost = 0;
		
		for (SyncReplayResult res : result) {
			List<Object> nodeInstance = res.getNodeInstance();
			List<StepTypes> stepTypes = res.getStepTypes();
			
			for (int i=0; i<nodeInstance.size(); i++) {
				StepTypes type = stepTypes.get(i);
				if (type==StepTypes.LMGOOD) continue;
				
				AlignmentStep step = new AlignmentStep(nodeInstance.get(i),type);

				Integer c = map.get(step);
				if (c==null)
					map.put(step,res.getTraceIndex().size());
				else
					map.put(step,c+res.getTraceIndex().size());
			}
			
			this.optimalAlignmentCost += ((int) res.getInfo().get("Raw Fitness Cost").doubleValue()) * res.getTraceIndex().size();
		}	
		
		return map;
	}
	
	protected Map<AlignmentLabel,Integer> computeImpactOfLabelsOnOptimalAlignmentCost(Map<AlignmentStep,Integer> frequencies, Map<Transition,Integer> transitions2costs, Map<XEventClass,Integer> events2costs) {
		Map<AlignmentLabel,Integer>	result = new HashMap<AlignmentLabel,Integer>();
		
		for (Map.Entry<AlignmentStep,Integer> entry : frequencies.entrySet()) {
			AlignmentStep step = entry.getKey();
			
			if (step.type==StepTypes.MREAL) {
				Transition t = (Transition) step.name;
				AlignmentLabel label = new AlignmentLabel(t.getLabel(),true);
				int freq = entry.getValue();
				
				if (result.get(label)==null)
					result.put(label, transitions2costs.get(t)*freq);
				else
					result.put(label, result.get(label) + transitions2costs.get(t)*freq);	
			}
			else if (step.type==StepTypes.L) {
				XEventClass e = (XEventClass) step.name;
				AlignmentLabel label = new AlignmentLabel(e.getId(),false);
				int freq = entry.getValue();
				
				if (result.get(label)==null)
					result.put(label, events2costs.get(e)*freq);
				else
					result.put(label, result.get(label) + events2costs.get(e)*freq);
			}
		}

		return result;
	}
	
	protected boolean computeImpactOfLabelsOnOptimalAlignmentCost(Map<AlignmentLabel,Double> labels2impacts, Map<AlignmentStep,Integer> frequencies, RepairRecommendation r, RepairConstraint constraint, Map<Transition,Integer> transitions2costs, Map<XEventClass,Integer> events2costs) {
		boolean result = false;
		
		for (Map.Entry<AlignmentStep,Integer> entry : frequencies.entrySet()) {
			AlignmentStep step = entry.getKey();
			
			if (step.type==StepTypes.MREAL) {
				if (r.getSkipLabels().contains(((Transition) step.name).getLabel())) continue;
				
				Transition t = (Transition) step.name;
				AlignmentLabel lb = new AlignmentLabel(t.getLabel(),true);
				
				int freq = entry.getValue();
				
				if (labels2impacts.get(lb)==null) {
					double impact = transitions2costs.get(t)*freq;
					
					if (impact>0 && constraint.getSkipCosts().get(t.getLabel())==0)
						result = true;
					
					labels2impacts.put(lb, impact);
				}
				else {
					double impact = labels2impacts.get(lb) + transitions2costs.get(t)*freq;
					
					if (impact>0 && constraint.getSkipCosts().get(t.getLabel())==0)
						result = true;
					
					labels2impacts.put(lb, impact);
				}
			}
			else if (step.type==StepTypes.L) {
				if (r.getInsertLabels().contains(((XEventClass) step.name).getId())) continue;
				
				XEventClass e = (XEventClass) step.name;
				AlignmentLabel lb = new AlignmentLabel(e.getId(),false);
				int freq = entry.getValue();
				
				if (labels2impacts.get(lb)==null) {
					double impact = events2costs.get(e)*freq;
					
					if (impact>0 && constraint.getInsertCosts().get(e.getId())==0)
						result = true;
					
					labels2impacts.put(lb, impact);
				}
				else {
					double impact = labels2impacts.get(lb) + events2costs.get(e)*freq;
					
					if (impact>0 && constraint.getInsertCosts().get(e.getId())==0)
						result = true;
					
					labels2impacts.put(lb, impact);
				}
			}
		}
		
		return result;
	}
	
	protected Map<AlignmentLabel,Double> computeImpactPerRepairResource(Map<AlignmentLabel, Double> labels2impacts, RepairConstraint constraint) {
		Map<AlignmentLabel,Double> result = new HashMap<AlignmentLabel,Double>();
		
		for (Map.Entry<AlignmentLabel,Double> entry : labels2impacts.entrySet()) {
			AlignmentLabel lb = entry.getKey();
			double impact = entry.getValue();
			int cost = 0;
			
			if (lb.isTransition) cost = constraint.getSkipCosts().get(lb.label);
			else cost = constraint.getInsertCosts().get(lb.label);
			
			result.put(lb, impact / cost);
		}
		
		return result;
	}
	
	protected void filterImpacts(Map<AlignmentLabel,Double> labels2impacts, RepairRecommendation r) {
		Set<AlignmentLabel> toRemove = new HashSet<AlignmentLabel>();
		
		for (Map.Entry<AlignmentLabel,Double> entry : labels2impacts.entrySet()) {
			AlignmentLabel lb = entry.getKey();
			
			if (lb.isTransition && r.getSkipLabels().contains(lb.label))
				toRemove.add(lb);
			else if (!lb.isTransition && r.getInsertLabels().contains(lb.label))
				toRemove.add(lb);
		}
		
		for (AlignmentLabel lb : toRemove)
			labels2impacts.remove(lb);
	}

	
	public class AlignmentStep {
		public Object	 name = null;
		public StepTypes type = null;
		
		public AlignmentStep(Object name, StepTypes type) {
			this.name = name;
			this.type = type;
		}
		
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
	
	public class AlignmentLabel {
		private String label = "";
		private boolean isTransition = false;
		
		public AlignmentLabel(String label, boolean isT) {
			this.label = label;
			this.isTransition = isT;
		}
		
		public String getLabel() {
			return this.label;
		}
		
		public boolean isTransition() {
			return this.isTransition;
		}
		
		@Override
		public String toString() {
			return String.format("[%s,%s]", this.label, this.isTransition ? "+" : "-");
		}
		
		public int hashCode() {
			return label.hashCode() * (isTransition ? 7 : 13);
		}

		public boolean equals(Object obj) {
			if (!(obj instanceof AlignmentLabel)) return false;
			AlignmentLabel that = (AlignmentLabel) obj;
			
			if (this.label.equals(that.label) && this.isTransition==that.isTransition)
				return true;
				
			return false;
		}
	}
}
