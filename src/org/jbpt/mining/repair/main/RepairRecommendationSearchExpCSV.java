package org.jbpt.mining.repair.main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.info.impl.XLogInfoImpl;
import org.deckfour.xes.model.XLog;
import org.jbpt.mining.repair.BruteForceRepairRecommendationSearch;
import org.jbpt.mining.repair.BruteForceRepairRecommendationSearchWithOptimization;
import org.jbpt.mining.repair.CostFunction;
import org.jbpt.mining.repair.GoldrattRepairRecommendationSearch;
import org.jbpt.mining.repair.GreedyRepairRecommendationSearch;
import org.jbpt.mining.repair.KnapsackRepairRecommendationSearch;
import org.jbpt.mining.repair.RepairConstraint;
import org.jbpt.mining.repair.RepairRecommendation;
import org.jbpt.mining.repair.RepairRecommendationSearch;
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

/**
 * @author Artem Polyvyanyy
 */
public class RepairRecommendationSearchExpCSV {
	
	private enum RR_SEARCH_ALGORITHM {BF,BF2,GREEDY,GOLDRATT,KNAPSACK};
	
	//private static XEventClassifier eventClassifier = XLogInfoImpl.STANDARD_CLASSIFIER;
	private static XEventClassifier eventClassifier = XLogInfoImpl.NAME_CLASSIFIER;

	public static void main(String[] args) throws Exception {
		// configuration
		Map<String,Set<String>> log2nets = new HashMap<String,Set<String>>();
		List<RR_SEARCH_ALGORITHM> algs = new ArrayList<RR_SEARCH_ALGORITHM>();
		
		Set<String> aoNets = new HashSet<String>();
		aoNets.add("AO");
		log2nets.put("AO", aoNets);
		
		algs.add(RR_SEARCH_ALGORITHM.KNAPSACK);
		algs.add(RR_SEARCH_ALGORITHM.GOLDRATT);
		algs.add(RR_SEARCH_ALGORITHM.GREEDY);
		//algs.add(RR_SEARCH_ALGORITHM.BF2);
		//algs.add(RR_SEARCH_ALGORITHM.BF);
		
		// preparation
		RepairRecommendationSearch rrSearch = null;
		PetrinetGraph net = null;
		Marking initialMarking = null;
		Marking[] finalMarkings = null; // only one marking is used so far
		XLog log = null;
		Map<Transition,Integer>  costMOS = null; // movements on system
		Map<XEventClass,Integer> costMOT = null; // movements on trace
		TransEvClassMapping mapping = null;
		boolean debug = false;
		int maxRepairResources = 12;
		
		// experiment
		System.out.println("Alg.,Log,Net,Res,Time,Recs,Recs#,Comp#,Cost");
		for (Map.Entry<String,Set<String>> entry : log2nets.entrySet()) {
			String logFile = entry.getKey();
			for (String netFile : entry.getValue()) {				
				for (int res=0; res<=maxRepairResources; res++) {
					for (RR_SEARCH_ALGORITHM alg : algs) {
						System.out.print(alg+",");
						System.out.print(logFile+",");
						System.out.print(netFile+",");
						
						net = constructNet("./exp/"+netFile+".pnml");
						initialMarking = getInitialMarking(net);
						finalMarkings = getFinalMarkings(net);
						log = XLogReader.openLog("./exp/"+logFile+".xes");
						costMOS = constructMOSCostFunction(net);
						XEventClass dummyEvClass = new XEventClass("DUMMY",99999);
						costMOT = constructMOTCostFunction(net,log,dummyEvClass);
						mapping = constructMapping(net,log,dummyEvClass);
						
						//System.out.println("Initial marking: "+initialMarking);
						//System.out.println("Final markings: "+Arrays.toString(finalMarkings));
						//System.out.println("MOS costs: "+costMOS);
						//System.out.println("MOT costs: "+costMOT);
						
						switch (alg) {
							case BF : 
								rrSearch = new BruteForceRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
								break;
							case BF2:
								rrSearch = new BruteForceRepairRecommendationSearchWithOptimization(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
								break;
							case GREEDY:
								rrSearch = new GreedyRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
								break;
							case GOLDRATT:
								rrSearch = new GoldrattRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
								break;
							case KNAPSACK:
								rrSearch = new KnapsackRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
								break;
						}
						
						
						//String originalName = String.format("exp/%s.BEFORE.pnml",netFile);
						//rrSearch.serializeNet(net,originalName);
						//System.out.println("Original net serialized in: "+originalName);
						
						Set<String> netLabels = CostFunction.getLabels(net);
						Set<String> logLabels = CostFunction.getLabels(log,eventClassifier);
						logLabels.add("DUMMY");
						Map<String,Integer> insertCosts = CostFunction.getStdCostFunctionOnLabels(logLabels);
						Map<String,Integer> skipCosts = CostFunction.getStdCostFunctionOnLabels(netLabels);
						RepairConstraint constraint = new RepairConstraint(insertCosts,skipCosts,res);
						
						//System.out.println("Insert costs: "+insertCosts);
						//System.out.println("Skip costs: "+skipCosts);
						System.out.print(res+",");
						
						long start = System.nanoTime();
						Set<RepairRecommendation> recs = rrSearch.computeOptimalRepairRecommendations(constraint);
						long end = System.nanoTime();
						
						System.out.print((end-start)+",");
						System.out.print(recs.toString().replace(",", ";")+",");
						System.out.print(recs.size()+",");
						System.out.print(rrSearch.getNumberOfAlignmentCostComputations()+",");
						System.out.print(rrSearch.getOptimalCost());
						
						int count = 0;
						for (RepairRecommendation rec : recs) {
							String repairedName = String.format("exp/%s.%s.%s.%s.%s.%s.REPAIRED.pnml",alg,netFile,logFile,res,count,rrSearch.getOptimalCost());
							
							PetrinetGraph repaired = rrSearch.repair(rec);
							rrSearch.serializeNet(repaired,repairedName);
							
							//System.out.println("Repaired net serialized in: "+repairedName+" after applying "+rec);
							count++;
						}
						
						System.out.println();
					}
				}
			}
		}
		
		//System.out.println("----------------------------------------");
		//System.out.println("DONE!");
	}

	private static TransEvClassMapping constructMapping(PetrinetGraph net, XLog log, XEventClass dummyEvClass) {
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

	private static Map<XEventClass, Integer> constructMOTCostFunction(PetrinetGraph net, XLog log, XEventClass dummyEvClass) {
		Map<XEventClass,Integer> costMOT = new HashMap<XEventClass,Integer>();		
		XLogInfo summary = XLogInfoFactory.createLogInfo(log,eventClassifier);
		
		for (XEventClass evClass : summary.getEventClasses().getClasses()) {
			costMOT.put(evClass,1);
		}
		
		costMOT.put(dummyEvClass,1);
		
		return costMOT;
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

	private static PetrinetGraph constructNet(String netFile) {
		PNMLSerializer PNML = new PNMLSerializer();
		NetSystem sys = PNML.parse(netFile);
		
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
		
		return net;
	}

}
