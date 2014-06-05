package org.jbpt.mining;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.jbpt.mining.cost.AlignmentCostOptimizerGoldrattFrequencies;
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

public class MainAlignmentCost {

	public static void main(String[] args) throws Exception {
		
		// net
		PetrinetGraph net = PetrinetFactory.newPetrinet("My net");
		Place i = net.addPlace("i");
		Place p1 = net.addPlace("p1");
		Place p2 = net.addPlace("p2");
		Place p3 = net.addPlace("p3");
		Place p4 = net.addPlace("p4");
		Place o = net.addPlace("o");
		
		Transition tA = net.addTransition("A");
		tA.setInvisible(false);
		Transition tB = net.addTransition("B");
		tA.setInvisible(false);
		Transition tC = net.addTransition("C");
		tC.setInvisible(false);
		Transition tD = net.addTransition("D");
		tD.setInvisible(false);
		Transition tE = net.addTransition("E");
		tE.setInvisible(false);
		
		net.addArc(i,tA);
		net.addArc(tA,p1);
		net.addArc(tA,p2);
		net.addArc(p1,tC);
		net.addArc(p1,tD);
		net.addArc(p2,tD);
		net.addArc(p2,tB);
		net.addArc(tC,p3);
		net.addArc(tD,p3);
		net.addArc(tD,p4);
		net.addArc(tB,p4);
		net.addArc(p3,tE);
		net.addArc(p4,tE);
		net.addArc(tE,o);
		
		// markings
		Marking initMarking = new Marking();
		initMarking.add(i);
		
		Marking finalMarking = new Marking();
		finalMarking.add(o);
		
		Marking[] finalMarkings = new Marking[1];
		finalMarkings[0] = finalMarking;
		
		// log
		XLog log = XLogReader.openLog("logs/exercise1.xes");

		performExperiment(net,log,initMarking, finalMarkings, "1.dot");
	}
		

	private static void performExperiment(PetrinetGraph net, XLog log, Marking initMarking, Marking[] finalMarkings, String fileName) throws Exception {
		// transition costs
		Map<Transition,Integer> mapTrans2Cost = new HashMap<Transition,Integer>();
		Map<String,Set<Transition>> s2ts = new HashMap<String,Set<Transition>>();
		for (Transition  t : net.getTransitions()) {
			if (t.isInvisible()) continue;
			
			mapTrans2Cost.put(t,2);
			
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
		XEventClassifier eventClassifier = new XEventNameClassifier();
		Map<XEventClass,Integer> mapEvClass2Cost = new HashMap<XEventClass,Integer>();
		TransEvClassMapping mapping = new TransEvClassMapping(eventClassifier,new XEventClass("DUMMY",99999));
		XLogInfo summary = XLogInfoFactory.createLogInfo(log,eventClassifier);
		for (XEventClass evClass : summary.getEventClasses().getClasses()) {
			mapEvClass2Cost.put(evClass, 1);
			for (Transition t : s2ts.get(evClass.getId())) {
				mapping.put(t, evClass);	
			}
		}
		
		Set<Transition> restrictedTrans = new HashSet<Transition>();
		for (Transition t : net.getTransitions()) {
			if (t.isInvisible()) continue;
			
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
			
			System.out.println("===============================");
			
			for (XEvent e: log.get(res.getTraceIndex().first()))
				System.out.print(e.getAttributes().get("concept:name")+", ");
			System.out.println();
			System.out.println(res.getInfo());
			System.out.println(res.getNodeInstance());
			System.out.println(res.getStepTypes());
			
			System.out.println("===============================");
		}
		
		serializeNet(net,fileName);
		
		// optimize
		//AlignmentCostOptimizerExhaustive acOptimizer = new AlignmentCostOptimizerExhaustive(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost, mapping);
		//AlignmentCostOptimizerGoldratt acOptimizer = new AlignmentCostOptimizerGoldratt(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost, mapping);
		//AlignmentCostOptimizerGoldrattStepwise acOptimizer = new AlignmentCostOptimizerGoldrattStepwise(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost, mapping);
		AlignmentCostOptimizerGoldrattFrequencies acOptimizer = new AlignmentCostOptimizerGoldrattFrequencies(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost, mapping);
		System.out.println("Initial cost: "+ acOptimizer.computeCost());
		System.out.println("Optimal cost: "+ acOptimizer.findOptimalCost(4));
		
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
		//visualiseNet(acOptimizer.getNet(),"improved.dot");
		System.out.println("===END============================================================================");
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
