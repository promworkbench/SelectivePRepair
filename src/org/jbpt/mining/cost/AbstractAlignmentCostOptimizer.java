package org.jbpt.mining.cost;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.DummyGlobalContext;
import org.processmining.contexts.uitopia.DummyUIPluginContext;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
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
public abstract class AbstractAlignmentCostOptimizer {
	protected XLog 			log				= null; // log to use
	protected PetrinetGraph	net				= null; // net to use
	protected Marking			initMarking		= null; // initial marking
	protected Marking[]		finalMarkings	= null; // set of final markings
	
	protected DummyUIPluginContext 		context			= null;
	protected Map<Transition,Integer> 	mapTrans2Cost	= null;
	protected Map<XEventClass,Integer>	mapEvClass2Cost	= null;
	protected TransEvClassMapping		mapping			= null;
	public	Map<Transition,Integer> 	optTrans2Cost	= null;
	public	Map<XEventClass,Integer>	optEvClass2Cost	= null;
	
	protected int 						maxNumOfStates	= Integer.MAX_VALUE;
	protected Set<Transition> 			restrictedTrans = null;
	
	protected int iterations = 0;
	
	protected boolean outputFlag = false; 
	
	AbstractAlignmentCostOptimizer(PetrinetGraph net, Marking initMarking, Marking[] finalMarkings, XLog log, 
			Map<Transition,Integer> mapTrans2Cost, Map<XEventClass,Integer> mapEvClass2Cost, TransEvClassMapping mapping, boolean outputFlag) throws Exception {
		if (net==null) return;
		
		this.context 			= new DummyUIPluginContext(new DummyGlobalContext(), "label");
		
		this.net				= net;
		this.initMarking 		= initMarking;
		this.finalMarkings		= finalMarkings;
		this.log				= log;
		this.mapTrans2Cost		= mapTrans2Cost;
		this.mapEvClass2Cost	= mapEvClass2Cost;
		this.mapping			= mapping;
		this.outputFlag			= outputFlag;
		
		this.restrictedTrans = new HashSet<Transition>();
		for (Transition t : this.net.getTransitions()) {
			if (t.isInvisible()) continue;
			
			this.restrictedTrans.add(t);
		}
	}
	
	public int computeCost() {
		return this.computeCost(this.mapTrans2Cost,this.mapEvClass2Cost);
	}
	
	public int computeCost(Map<Transition,Integer> t2c, Map<XEventClass,Integer> e2c) {
		IPNReplayParameter parameters = new CostBasedCompleteManifestParam(e2c, t2c, 
										this.initMarking, this.finalMarkings, this.maxNumOfStates, this.restrictedTrans);
		parameters.setGUIMode(false);
		
		CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		PNRepResult result = replayEngine.replayLog(this.context, this.net, this.log, this.mapping, parameters);
		
		int cost = 0;
		for (SyncReplayResult res : result) {
			cost+=res.getInfo().get("Raw Fitness Cost");
		}
		
		return cost;
	}
	
	public int getNumberOfIterations() {
		return this.iterations;
	}
	
	public abstract int findOptimalCost(int resources);
	
	public void improveNetSystem() {
		this.skipTransitions();
	}

	private void skipTransitions() {
		for (Transition t : this.optTrans2Cost.keySet()) {
			if (this.optTrans2Cost.get(t)<this.mapTrans2Cost.get(t)) {
				Transition tt = net.addTransition("");
				tt.setInvisible(true);
				
				for (PetrinetEdge<?,?> edge : net.getInEdges(t)) {
					net.addArc((Place)edge.getSource(), tt);
				}
				
				for (PetrinetEdge<?,?> edge : net.getOutEdges(t)) {
					net.addArc(tt,(Place)edge.getTarget());
				}
			}
		}
	}
	
	public PetrinetGraph getNet() {
		return this.net;
	}
	
}
