package org.jbpt.mining.repair.cmd;
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
import org.jbpt.mining.repair.CostFunction;
import org.jbpt.mining.repair.GoldrattRepairRecommendationSearch;
import org.jbpt.mining.repair.PetrinetRepair;
import org.jbpt.mining.repair.RepairConstraint;
import org.jbpt.mining.repair.RepairRecommendation;
import org.jbpt.petri.Flow;
import org.jbpt.petri.NetSystem;
import org.jbpt.petri.io.PNMLSerializer;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;

import ee.ut.prom.XLogReader;


public class OptimalRepairRecommendationMain {

	public static void main(String[] args) throws Exception {
		// PREPARE NET
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

		// PREPARE MARKINGS
		Marking initMarking = new Marking();
		initMarking.add(p2p.get(i));
		
		Marking finalMarking = new Marking();
		finalMarking.add(p2p.get(o));
		
		Marking[] finalMarkings = new Marking[1];
		finalMarkings[0] = finalMarking;
		
		// PREPARE LOG
		XLog log = XLogReader.openLog(args[1]);
		
		// MOVEMENT ON SYSTEM COSTS
		Map<Transition,Integer> costMOS = new HashMap<Transition,Integer>();
		Map<String,Set<Transition>> labels2ts = new HashMap<String,Set<Transition>>();
		for (Transition  t : net.getTransitions()) {
			if (t.isInvisible() || t.getLabel().contains("invisible")) {
				costMOS.put(t,0);
				continue;
			}
			
			costMOS.put(t,1);
			
			if (labels2ts.get(t.getLabel())==null) {
				Set<Transition> ts = new HashSet<Transition>();
				ts.add(t);
				
				labels2ts.put(t.getLabel(), ts);
			}
			else {
				labels2ts.get(t.getLabel()).add(t);
			}
		}
		
		// CHOOSE EVENT CLASSIFIER
		//XEventClassifier eventClassifier = XLogInfoImpl.STANDARD_CLASSIFIER;
		XEventClassifier eventClassifier = XLogInfoImpl.NAME_CLASSIFIER;
		
		// MOVEMENT ON TRACE COSTS
		Map<XEventClass,Integer> costMOT = new HashMap<XEventClass,Integer>();
		TransEvClassMapping mapping = new TransEvClassMapping(eventClassifier,new XEventClass("DUMMY",99999));
		XLogInfo summary = XLogInfoFactory.createLogInfo(log,eventClassifier);
		for (XEventClass evClass : summary.getEventClasses().getClasses()) {
			costMOT.put(evClass, 1);
			String id = evClass.getId();
			if (labels2ts.get(id)==null) {
				System.err.println(id);
				continue;
			}
			for (Transition t : labels2ts.get(id)) {
				mapping.put(t, evClass);
				System.out.println(t + " - " + evClass);
			}
		}
		
		// DEFINE RESTRICTED TRANSITIONS
		/*Set<Transition> restrictedTrans = new HashSet<Transition>();
		for (Transition t : net.getTransitions()) {
			if (t.isInvisible() || t.getLabel().contains("invisible")) continue;
			restrictedTrans.add(t);
		}
		
		// SHOW ALIGNMENT
		IPNReplayParameter parameters = new CostBasedCompleteManifestParam(costMOT, costMOS, 
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
		}*/
		
		///////WeightedGreedyRepairRecommendationOptimization opt = new WeightedGreedyRepairRecommendationOptimization(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, true);
		
		// SEARCH FOR OPTIMAL REPAIR RECOMMENDATION
		//BruteForceRepairRecommendationSearch opt = new BruteForceRepairRecommendationSearch(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, true);
		//ImprovedBruteForceRepairRecommendationSearch opt = new ImprovedBruteForceRepairRecommendationSearch(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, true);
		//GreedyRepairRecommendationSearch opt = new GreedyRepairRecommendationSearch(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, true);
		//GreedyGoldrattRepairRecommendationSearch opt = new GreedyGoldrattRepairRecommendationSearch(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, true);
		GoldrattRepairRecommendationSearch opt = new GoldrattRepairRecommendationSearch(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, true);
		
		// SERIALIZE NET
		opt.serializeNet("original");
		
		Set<String> labels = CostFunction.getLabels(net);
		Map<String,Double> costFunc = CostFunction.getStdCostFunctionOnLabels(labels);  
		RepairConstraint constraint = new RepairConstraint(costFunc,costFunc,9);
		
		Set<RepairRecommendation> recs = opt.computeOptimalRepairRecommendations(constraint);
		
		System.out.println("==RESULT=============================");
		System.out.println(CostFunction.getLabels(net));
		System.out.println(CostFunction.getLabels(net).size());
		System.out.println("=====");
		System.out.println(recs);
		System.out.println(recs.size());
		System.out.println("=====");
		System.out.println(opt.getNumberOfAlignmentCostComputations());
		System.out.println(opt.getOptimalCost());
		
		// REPAIR NET
		opt.repair(recs.iterator().next());
		opt.serializeNet("repaired");
		
		PetrinetRepair.repair(net, log, recs.iterator().next());
	}

	

}
