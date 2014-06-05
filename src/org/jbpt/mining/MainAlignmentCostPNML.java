package org.jbpt.mining;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.info.impl.XLogInfoImpl;
import org.deckfour.xes.model.XLog;
import org.jbpt.mining.cost.AlignmentCostOptimizerExhaustive;
import org.jbpt.petri.Flow;
import org.jbpt.petri.NetSystem;
import org.jbpt.petri.io.PNMLSerializer;
import org.jbpt.throwable.SerializationException;
import org.processmining.contexts.uitopia.DummyGlobalContext;
import org.processmining.contexts.uitopia.DummyUIPluginContext;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.astar.petrinet.manifestreplay.CostBasedCompleteManifestParam;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.replayer.algorithms.IPNReplayParameter;
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedcomplete.CostBasedCompletePruneAlg;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;

import ee.ut.prom.XLogReader;


public class MainAlignmentCostPNML {

	/**
	 * args[0] - pnml file
	 * args[1] - xes file
	 */
	public static void main(String[] args) throws Exception {
		PNMLSerializer PNML = new PNMLSerializer();
		NetSystem sys = PNML.parse(args[0]);
		
		int pi,ti;
		pi = ti = 1;
		for (org.jbpt.petri.Place p : sys.getPlaces()) 
			p.setName("p"+pi++);
		for (org.jbpt.petri.Transition t : sys.getTransitions()) 
			t.setName("t"+ti++);
		
		org.jbpt.petri.Place i = sys.getSourcePlaces().iterator().next();
		org.jbpt.petri.Place o = sys.getSinkPlaces().iterator().next();
		
		PetrinetGraph net = PetrinetFactory.newPetrinet("My net");
		
		// places
		Map<org.jbpt.petri.Place,Place> p2p = new HashMap<>();
		for (org.jbpt.petri.Place p : sys.getPlaces()) {
			Place pp = net.addPlace(p.getName());
			p2p.put(p,pp);
		}
		
		// transitions
		Map<org.jbpt.petri.Transition,Transition> t2t = new HashMap<>();
		for (org.jbpt.petri.Transition t : sys.getTransitions()) {
			Transition tt = net.addTransition(t.getLabel());
			tt.setInvisible(t.isSilent());
			t2t.put(t,tt);
		}
		
		// flow
		for (Flow f : sys.getFlow()) {
			if (f.getSource() instanceof org.jbpt.petri.Place) {
				net.addArc(p2p.get(f.getSource()),t2t.get(f.getTarget()));
			} else {
				net.addArc(t2t.get(f.getSource()),p2p.get(f.getTarget()));
			}
		}
		
		serializeNet(net, "original");

		// markings
		Marking initMarking = new Marking();
		initMarking.add(p2p.get(i));
		
		Marking finalMarking = new Marking();
		finalMarking.add(p2p.get(o));
		
		Marking[] finalMarkings = new Marking[1];
		finalMarkings[0] = finalMarking;
		
		// log
		XLog log = XLogReader.openLog(args[1]);
		
		// transition costs
		Map<Transition,Integer> mapTrans2Cost = new HashMap<Transition,Integer>();
		
		Map<String,Set<Transition>> s2ts = new HashMap<String,Set<Transition>>();
		for (Transition  t : net.getTransitions()) {
			if (t.isInvisible() || t.getLabel().contains("invisible")) {
				mapTrans2Cost.put(t, 0);
				continue;
			}
			
			mapTrans2Cost.put(t, 2);
			
			if (s2ts.get(t.getLabel())==null) {
				Set<Transition> ts = new HashSet<Transition>();
				ts.add(t);
				
				s2ts.put(t.getLabel(), ts);
			}
			else {
				s2ts.get(t.getLabel()).add(t);
			}
		}
		
		// transition 2 event class
		Map<XEventClass,Integer> mapEvClass2Cost = new HashMap<XEventClass,Integer>();
		
		XEventClassifier eventClassifier = XLogInfoImpl.STANDARD_CLASSIFIER;
		//XEventClassifier eventClassifier = XLogInfoImpl.NAME_CLASSIFIER;
		TransEvClassMapping mapping = new TransEvClassMapping(eventClassifier,new XEventClass("DUMMY",99999));
		XLogInfo summary = XLogInfoFactory.createLogInfo(log,eventClassifier);
		
		for (XEventClass evClass : summary.getEventClasses().getClasses()) {
			mapEvClass2Cost.put(evClass, 1);
			String id = evClass.getId();
			if (s2ts.get(id)==null) continue; // TODO ?!
			for (Transition t : s2ts.get(id)) {
				mapping.put(t, evClass);
				System.out.println(t + " - " + evClass);
			}
		}
		
		Set<Transition> restrictedTrans = new HashSet<Transition>();
		for (Transition t : net.getTransitions()) {
			if (t.isInvisible() || t.getLabel().contains("invisible")) continue;
			
			restrictedTrans.add(t);
		}
		
		// show alignment
		IPNReplayParameter parameters = new CostBasedCompleteManifestParam(mapEvClass2Cost, mapTrans2Cost, 
				initMarking, finalMarkings, Integer.MAX_VALUE, restrictedTrans);
		parameters.setGUIMode(false);

		CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		DummyUIPluginContext context = new DummyUIPluginContext(new DummyGlobalContext(), "label");
		PNRepResult result = replayEngine.replayLog(context, net, log, mapping, parameters);
		
		for (SyncReplayResult res : result) {
			
			/*System.out.println("===============================");
			
			for (XEvent e: log.get(res.getTraceIndex().first()))
				System.out.print(e.getAttributes().get("concept:name")+", ");
			System.out.println();
			System.out.println(res.getInfo());
			System.out.println(res.getNodeInstance());
			System.out.println(res.getStepTypes());
			
			System.out.println("===============================");*/
		}
		
		// optimize
		AlignmentCostOptimizerExhaustive acOptimizer = new AlignmentCostOptimizerExhaustive(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost, mapping);
		//AlignmentCostOptimizerGoldratt acOptimizer = new AlignmentCostOptimizerGoldratt(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost, mapping);
		//AlignmentCostOptimizerGoldrattStepwise acOptimizer = new AlignmentCostOptimizerGoldrattStepwise(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost, mapping);
		//AlignmentCostOptimizerGoldrattFrequencies acOptimizer = new AlignmentCostOptimizerGoldrattFrequencies(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost, mapping);
		System.out.println("Initial cost: " + acOptimizer.computeCost());
		System.out.println("Optimal cost: " + acOptimizer.findOptimalCost(4));
		
		// show alignment
		parameters = new CostBasedCompleteManifestParam(acOptimizer.optEvClass2Cost, acOptimizer.optTrans2Cost, 
				initMarking, finalMarkings, Integer.MAX_VALUE, restrictedTrans);
		parameters.setGUIMode(false);
		
		result = replayEngine.replayLog(context, net, log, mapping, parameters);
		
		/*for (SyncReplayResult res : result) {
			
			System.out.println("===============================");
			
			for (XEvent e: log.get(res.getTraceIndex().first()))
				System.out.print(e.getAttributes().get("concept:name")+", ");
			System.out.println();
			System.out.println(res.getInfo());
			System.out.println(res.getNodeInstance());
			System.out.println(res.getStepTypes());
			
			System.out.println("===============================");
		}*/
		
		System.out.println("Numner of alignment computations: " + acOptimizer.getNumberOfIterations());
		
		acOptimizer.improveNetSystem();
		serializeNet(acOptimizer.getNet(), "improved");	
	}

	private static void serializeNet(PetrinetGraph net, String name) throws IOException, SerializationException {
		NetSystem n = new NetSystem();
		
		Map<PetrinetNode,org.jbpt.petri.Node> map = new HashMap<PetrinetNode,org.jbpt.petri.Node>();
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
				n.addFlow((org.jbpt.petri.Place)map.get(edge.getSource()),(org.jbpt.petri.Transition)map.get(edge.getTarget()));
			else
				n.addFlow((org.jbpt.petri.Transition)map.get(edge.getSource()),(org.jbpt.petri.Place)map.get(edge.getTarget()));
		}
		
		PNMLSerializer pnml = new PNMLSerializer();
		//org.jbpt.utils.IOUtils.toFile(name+".pnml", pnml.serializePetriNet(n));
	}

}
