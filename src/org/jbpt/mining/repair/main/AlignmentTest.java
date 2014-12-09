package org.jbpt.mining.repair.main;

import java.util.HashMap;
import java.util.Map;

import nl.tue.astar.AStarException;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.info.impl.XLogInfoImpl;
import org.deckfour.xes.model.XLog;
import org.jbpt.petri.Flow;
import org.jbpt.petri.NetSystem;
import org.jbpt.petri.io.PNMLSerializer;
import org.processmining.contexts.uitopia.DummyGlobalContext;
import org.processmining.contexts.uitopia.DummyUIPluginContext;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.astar.petrinet.PetrinetReplayerWithoutILP;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.replayer.algorithms.IPNReplayParameter;
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedcomplete.CostBasedCompleteParam;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;

import ee.ut.prom.XLogReader;

public class AlignmentTest {

	public static void main(String[] args) throws Exception {
		DummyUIPluginContext context = new DummyUIPluginContext(new DummyGlobalContext(), "label");
		
		for (int i=0; i<100; i++) {
			PetrinetGraph net = null;
			Marking initialMarking = null;
			Marking[] finalMarkings = null; // only one marking is used so far
			XLog log = null;
			Map<Transition,Integer>  costMOS = null; // movements on system
			Map<XEventClass,Integer> costMOT = null; // movements on trace
			TransEvClassMapping mapping = null;
			
			net = constructNet("./exp/REPAIRED.GREEDY_ALL.BPI2012all100_induct_1_0.BPI2012all100.11.0.41764.pnml");
			initialMarking = getInitialMarking(net);
			finalMarkings = getFinalMarkings(net);
			log = XLogReader.openLog("./exp/BPI2012all100.xes.gz");
			costMOS = constructMOSCostFunction(net);
			XEventClass dummyEvClass = new XEventClass("DUMMY",99999);
			XEventClassifier eventClassifier = XLogInfoImpl.STANDARD_CLASSIFIER;
			costMOT = constructMOTCostFunction(net,log,eventClassifier,dummyEvClass);
			mapping = constructMapping(net,log,dummyEvClass, eventClassifier);
			
			int cost = AlignmentTest.computeCost(costMOS, costMOT, initialMarking, finalMarkings, context, net, log, mapping);
			
			System.out.println(i + ":\t" + cost);
		}

	}
	
	public static int computeCost(Map<Transition,Integer> costMOS, Map<XEventClass,Integer> costMOT, Marking initialMarking, Marking[] finalMarkings, DummyUIPluginContext context, PetrinetGraph net, XLog log, TransEvClassMapping mapping) {
		PetrinetReplayerWithoutILP replayEngine = new PetrinetReplayerWithoutILP();		
		
		IPNReplayParameter parameters = new CostBasedCompleteParam(costMOT,costMOS);
		parameters.setInitialMarking(initialMarking);
		parameters.setFinalMarkings(finalMarkings[0]);
		parameters.setGUIMode(false);
		parameters.setCreateConn(false);

				
		int cost = 0;
		try {
			PNRepResult result = replayEngine.replayLog(context, net, log, mapping, parameters);
			
			for (SyncReplayResult res : result) {
				cost += res.getInfo().get("Raw Fitness Cost") * res.getTraceIndex().size();
			}
		} catch (AStarException e) {
			e.printStackTrace();
		}
		
		return cost;
	}
	
	private static PetrinetGraph constructNet(String netFile) {
		PNMLSerializer PNML = new PNMLSerializer();
		NetSystem sys = PNML.parse(netFile);
		
		//System.err.println(sys.getMarkedPlaces());
		
		int pi,ti;
		pi = ti = 1;
		for (org.jbpt.petri.Place p : sys.getPlaces()) 
			p.setName("p"+pi++);
		for (org.jbpt.petri.Transition t : sys.getTransitions()) 
			t.setName("t"+ti++);
		
		PetrinetGraph net = PetrinetFactory.newPetrinet(netFile);
		
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
		
		// add unique start node
		if (sys.getSourceNodes().isEmpty()) {
			Place i = net.addPlace("START_P");
			Transition t = net.addTransition("");
			t.setInvisible(true);
			net.addArc(i,t);
			
			for (org.jbpt.petri.Place p : sys.getMarkedPlaces()) {
				net.addArc(t,p2p.get(p));	
			}
			
		}
		
		return net;
	}
	
	private static Marking[] getFinalMarkings(PetrinetGraph net) {			
		Marking finalMarking = new Marking();
		
		for (Place p : net.getPlaces()) {
			if (net.getOutEdges(p).isEmpty())
				finalMarking.add(p);
		}
			
		Marking[] finalMarkings = new Marking[1];
		finalMarkings[0] = finalMarking;
		
		return finalMarkings;
	}

	private static Marking getInitialMarking(PetrinetGraph net) {
		Marking initMarking = new Marking();
		
		for (Place p : net.getPlaces()) {
			if (net.getInEdges(p).isEmpty())
				initMarking.add(p);
		}
		
		return initMarking;
	}

	private static Map<Transition, Integer> constructMOSCostFunction(PetrinetGraph net) {
		Map<Transition,Integer> costMOS = new HashMap<Transition,Integer>();
		
		for (Transition  t : net.getTransitions())
			if (t.isInvisible() || t.getLabel().equals(""))
				costMOS.put(t,0);
			else
				costMOS.put(t,1);	
		
		return costMOS;
	}
	
	private static Map<XEventClass, Integer> constructMOTCostFunction(PetrinetGraph net, XLog log, XEventClassifier eventClassifier, XEventClass dummyEvClass) {
		Map<XEventClass,Integer> costMOT = new HashMap<XEventClass,Integer>();		
		XLogInfo summary = XLogInfoFactory.createLogInfo(log,eventClassifier);
		
		for (XEventClass evClass : summary.getEventClasses().getClasses()) {
			costMOT.put(evClass,1);
		}
		
		costMOT.put(dummyEvClass,1);
		
		return costMOT;
	}
	
	private static TransEvClassMapping constructMapping(PetrinetGraph net, XLog log, XEventClass dummyEvClass, XEventClassifier eventClassifier) {
		TransEvClassMapping mapping = new TransEvClassMapping(eventClassifier, dummyEvClass);
		
		XLogInfo summary = XLogInfoFactory.createLogInfo(log,eventClassifier);
		
		for (Transition t : net.getTransitions()) {
			boolean mapped = false;
			
			for (XEventClass evClass : summary.getEventClasses().getClasses()) {
				String id = evClass.getId();
				
				if (t.getLabel().equals(id)) {
					mapping.put(t, evClass);
					mapped = true;
					break;
				}
			}
			
			if (!mapped && !t.isInvisible()) {
				mapping.put(t, dummyEvClass);
			}
				
		}
		
		return mapping;
	}
}
