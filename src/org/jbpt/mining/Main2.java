package org.jbpt.mining;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.DummyGlobalContext;
import org.processmining.contexts.uitopia.DummyUIPluginContext;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
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


public class Main2 {

	public static void main(String[] args) throws Exception {
		DummyUIPluginContext context = new DummyUIPluginContext(new DummyGlobalContext(), "label");
		
		CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		XLog log = XLogReader.openLog("logs/exercise1.xes");
		
		// create net
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
		net.addArc(p1,tE);
		net.addArc(p2,tE);
		net.addArc(p2,tB);
		net.addArc(tC,p3);
		net.addArc(tE,p3);
		net.addArc(tE,p4);
		net.addArc(tB,p4);
		net.addArc(p3,tD);
		net.addArc(p4,tD);
		net.addArc(tD,o);
		
		//XEventClassifier eventClassifier = new XEventNameClassifier();
		XEventClassifier eventClassifier = new XEventNameClassifier();
		TransEvClassMapping mapping = new TransEvClassMapping(eventClassifier,new XEventClass("DUMMY",99999));
		
		// move on log costs
		XLogInfo summary = XLogInfoFactory.createLogInfo(log,eventClassifier);
		Map<XEventClass,Integer> mapEvClass2Cost = new HashMap<XEventClass,Integer>();
		for (XEventClass evClass : summary.getEventClasses().getClasses()) {
			mapEvClass2Cost.put(evClass, 1);
			//System.out.println(evClass.getId());
			
			if (evClass.getId().contains("A")) {
				mapping.put(tA, evClass);
				System.err.println(evClass);
			}
			
			if (evClass.getId().contains("B")) {
				mapping.put(tB, evClass);
				System.err.println(evClass);
			}
			
			if (evClass.getId().contains("C")) {
				mapping.put(tC, evClass);
				System.err.println(evClass);
			}
			
			if (evClass.getId().contains("D")) {
				mapping.put(tD, evClass);
				System.err.println(evClass);
			}
			
			if (evClass.getId().contains("E")) {
				mapping.put(tE, evClass);
				System.err.println(evClass);
			}
		}
		
		// move of net cost
		Map<Transition,Integer> mapTrans2Cost = new HashMap<Transition,Integer>();
		mapTrans2Cost.put(tA, 1);
		mapTrans2Cost.put(tB, 1);
		mapTrans2Cost.put(tC, 1);
		mapTrans2Cost.put(tD, 1);
		mapTrans2Cost.put(tE, 1);
		
		// markings
		Marking initMarking = new Marking();
		initMarking.add(i);
		
		Marking finalMarking = new Marking();
		finalMarking.add(o);
		
		Marking[] finalMarkings = new Marking[1];
		finalMarkings[0] = finalMarking;
		
		int maxNumOfStates = Integer.MAX_VALUE;
		
		
		Set<Transition> restrictedTrans = new HashSet<Transition>();
		restrictedTrans.add(tA);
		restrictedTrans.add(tB);
		restrictedTrans.add(tC);
		restrictedTrans.add(tD);
		restrictedTrans.add(tE);
		
		IPNReplayParameter parameters = new CostBasedCompleteManifestParam(mapEvClass2Cost, mapTrans2Cost, initMarking, finalMarkings, maxNumOfStates, restrictedTrans);
		parameters.setGUIMode(false);
		
		PNRepResult result = replayEngine.replayLog(context, net, log, mapping, parameters);
		
		for (SyncReplayResult res : result) {
			
			System.out.println("===============================");
			
			System.out.println(res.getInfo());
			System.out.println(res.getNodeInstance());
			System.out.println(res.getStepTypes());
			System.out.println(res.getTraceIndex());
			
			System.out.println("===============================");
		}
	}

}
