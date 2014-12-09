package org.jbpt.mining.repair.main;

import java.util.ArrayList;
import java.util.HashMap;
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
public class RepairRecommendationSearchExpCSVDebug {
	
	private enum RR_SEARCH_ALGORITHM {BF,BF2,GREEDY_ALL,GREEDY_ONE,GOLDRATT_ALL,GOLDRATT_ONE,KNAPSACK_ALL,KNAPSACK_ONE};

	@SuppressWarnings("fallthrough")
	public static void main(String[] args) throws Exception {
		// configuration
		Map<String,XEventClassifier> log2classifier = new HashMap<String,XEventClassifier>();
		Map<String,List<String>> log2nets = new HashMap<String,List<String>>();
		List<RR_SEARCH_ALGORITHM> algs = new ArrayList<RR_SEARCH_ALGORITHM>();
		
		// AO net - DONE
		List<String> aoNet = new ArrayList<String>();
		aoNet.add("AO");
		
		// AO nets - DONE
		/*List<String> aoNets = new ArrayList<String>();
		aoNets.add("AO_induct_1_0");
		aoNets.add("AO_induct_0_9");
		aoNets.add("AO_induct_0_8");
		aoNets.add("AO_induct_0_7");
		aoNets.add("AO_induct_0_6");
		aoNets.add("AO_induct_0_5");
		aoNets.add("AO_induct_0_4");
		aoNets.add("AO_induct_0_3");
		aoNets.add("AO_induct_0_2");
		aoNets.add("AO_induct_0_1");
		aoNets.add("AO_induct_0_0");
		
		// BPI2013all100 nets - DONE
		List<String> BPI2013all100Nets = new ArrayList<String>();
		BPI2013all100Nets.add("BPI2013all100_induct_1_0");
		BPI2013all100Nets.add("BPI2013all100_induct_0_9");
		BPI2013all100Nets.add("BPI2013all100_induct_0_8");
		BPI2013all100Nets.add("BPI2013all100_induct_0_7");
		BPI2013all100Nets.add("BPI2013all100_induct_0_6");
		BPI2013all100Nets.add("BPI2013all100_induct_0_5");
		BPI2013all100Nets.add("BPI2013all100_induct_0_4");
		BPI2013all100Nets.add("BPI2013all100_induct_0_3");
		BPI2013all100Nets.add("BPI2013all100_induct_0_2");
		BPI2013all100Nets.add("BPI2013all100_induct_0_1");
		BPI2013all100Nets.add("BPI2013all100_induct_0_0");
		
		// BPI2013all90 nets - DONE - test start/end
		List<String> BPI2013all90Nets = new ArrayList<String>();
		BPI2013all90Nets.add("BPI2013all90_induct_1_0");
		BPI2013all90Nets.add("BPI2013all90_induct_0_9");
		BPI2013all90Nets.add("BPI2013all90_induct_0_8");
		BPI2013all90Nets.add("BPI2013all90_induct_0_7");
		BPI2013all90Nets.add("BPI2013all90_induct_0_6");
		BPI2013all90Nets.add("BPI2013all90_induct_0_5");
		BPI2013all90Nets.add("BPI2013all90_induct_0_4");
		BPI2013all90Nets.add("BPI2013all90_induct_0_3");
		BPI2013all90Nets.add("BPI2013all90_induct_0_2");
		BPI2013all90Nets.add("BPI2013all90_induct_0_1");
		BPI2013all90Nets.add("BPI2013all90_induct_0_0");*/
		
		// BPI2013all80 nets - DONE
		List<String> BPI2013all80Nets = new ArrayList<String>();
		/*BPI2013all80Nets.add("BPI2013all80_induct_1_0");
		BPI2013all80Nets.add("BPI2013all80_induct_0_9");
		BPI2013all80Nets.add("BPI2013all80_induct_0_8");
		BPI2013all80Nets.add("BPI2013all80_induct_0_7");
		BPI2013all80Nets.add("BPI2013all80_induct_0_6");
		BPI2013all80Nets.add("BPI2013all80_induct_0_5");*/
		BPI2013all80Nets.add("BPI2013all80_induct_0_4");
		/*BPI2013all80Nets.add("BPI2013all80_induct_0_3");
		BPI2013all80Nets.add("BPI2013all80_induct_0_2");
		BPI2013all80Nets.add("BPI2013all80_induct_0_1");
		BPI2013all80Nets.add("BPI2013all80_induct_0_0");*/
		
		// BPI2012all100 nets - DONE
		/*List<String> BPI2012all100Nets = new ArrayList<String>();
		BPI2012all100Nets.add("BPI2012all100_induct_1_0");
		BPI2012all100Nets.add("BPI2012all100_induct_0_9");
		BPI2012all100Nets.add("BPI2012all100_induct_0_8");
		BPI2012all100Nets.add("BPI2012all100_induct_0_7");
		BPI2012all100Nets.add("BPI2012all100_induct_0_6");
		BPI2012all100Nets.add("BPI2012all100_induct_0_5");
		BPI2012all100Nets.add("BPI2012all100_induct_0_4");
		BPI2012all100Nets.add("BPI2012all100_induct_0_3");
		BPI2012all100Nets.add("BPI2012all100_induct_0_2");
		BPI2012all100Nets.add("BPI2012all100_induct_0_1");
		BPI2012all100Nets.add("BPI2012all100_induct_0_0");
		
		// BPI2012all90 nets - DONE
		List<String> BPI2012all90Nets = new ArrayList<String>();
		BPI2012all90Nets.add("BPI2012all90_induct_1_0");
		BPI2012all90Nets.add("BPI2012all90_induct_0_9");
		BPI2012all90Nets.add("BPI2012all90_induct_0_8");
		BPI2012all90Nets.add("BPI2012all90_induct_0_7");
		BPI2012all90Nets.add("BPI2012all90_induct_0_6");
		BPI2012all90Nets.add("BPI2012all90_induct_0_5");
		BPI2012all90Nets.add("BPI2012all90_induct_0_4");
		BPI2012all90Nets.add("BPI2012all90_induct_0_3");
		BPI2012all90Nets.add("BPI2012all90_induct_0_2");
		BPI2012all90Nets.add("BPI2012all90_induct_0_1");
		BPI2012all90Nets.add("BPI2012all90_induct_0_0");
		
		// BPI2012all80 nets - DONE
		List<String> BPI2012all80Nets = new ArrayList<String>();
		BPI2012all80Nets.add("BPI2012all80_induct_1_0");
		BPI2012all80Nets.add("BPI2012all80_induct_0_9");
		BPI2012all80Nets.add("BPI2012all80_induct_0_8");
		BPI2012all80Nets.add("BPI2012all80_induct_0_7");
		BPI2012all80Nets.add("BPI2012all80_induct_0_6");
		BPI2012all80Nets.add("BPI2012all80_induct_0_5");
		BPI2012all80Nets.add("BPI2012all80_induct_0_4");
		BPI2012all80Nets.add("BPI2012all80_induct_0_3");
		BPI2012all80Nets.add("BPI2012all80_induct_0_2");
		BPI2012all80Nets.add("BPI2012all80_induct_0_1");
		BPI2012all80Nets.add("BPI2012all80_induct_0_0");
		
		// BPI2012comp100 nets - DONE
		List<String> BPI2012comp100Nets = new ArrayList<String>();
		BPI2012comp100Nets.add("BPI2012comp100_induct_1_0");
		BPI2012comp100Nets.add("BPI2012comp100_induct_0_9");
		BPI2012comp100Nets.add("BPI2012comp100_induct_0_8");
		BPI2012comp100Nets.add("BPI2012comp100_induct_0_7");
		BPI2012comp100Nets.add("BPI2012comp100_induct_0_6");
		BPI2012comp100Nets.add("BPI2012comp100_induct_0_5");
		BPI2012comp100Nets.add("BPI2012comp100_induct_0_4");
		BPI2012comp100Nets.add("BPI2012comp100_induct_0_3");
		BPI2012comp100Nets.add("BPI2012comp100_induct_0_2");
		BPI2012comp100Nets.add("BPI2012comp100_induct_0_1");
		BPI2012comp100Nets.add("BPI2012comp100_induct_0_0");
		
		// BPI2012comp90 nets - DONE
		List<String> BPI2012comp90Nets = new ArrayList<String>();
		BPI2012comp90Nets.add("BPI2012comp90_induct_1_0");
		BPI2012comp90Nets.add("BPI2012comp90_induct_0_9");
		BPI2012comp90Nets.add("BPI2012comp90_induct_0_8");
		BPI2012comp90Nets.add("BPI2012comp90_induct_0_7");
		BPI2012comp90Nets.add("BPI2012comp90_induct_0_6");
		BPI2012comp90Nets.add("BPI2012comp90_induct_0_5");
		BPI2012comp90Nets.add("BPI2012comp90_induct_0_4");
		BPI2012comp90Nets.add("BPI2012comp90_induct_0_3");
		BPI2012comp90Nets.add("BPI2012comp90_induct_0_2");
		BPI2012comp90Nets.add("BPI2012comp90_induct_0_1");
		BPI2012comp90Nets.add("BPI2012comp90_induct_0_0");
		
		// BPI2012comp80 nets - DONE
		List<String> BPI2012comp80Nets = new ArrayList<String>();
		BPI2012comp80Nets.add("BPI2012comp80_induct_1_0");
		BPI2012comp80Nets.add("BPI2012comp80_induct_0_9");
		BPI2012comp80Nets.add("BPI2012comp80_induct_0_8");
		BPI2012comp80Nets.add("BPI2012comp80_induct_0_7");
		BPI2012comp80Nets.add("BPI2012comp80_induct_0_6");
		BPI2012comp80Nets.add("BPI2012comp80_induct_0_5");
		BPI2012comp80Nets.add("BPI2012comp80_induct_0_4");
		BPI2012comp80Nets.add("BPI2012comp80_induct_0_3");
		BPI2012comp80Nets.add("BPI2012comp80_induct_0_2");
		BPI2012comp80Nets.add("BPI2012comp80_induct_0_1");
		BPI2012comp80Nets.add("BPI2012comp80_induct_0_0");
		
		// BPI2011all100 nets - DONE
		List<String> BPI2011all100Nets = new ArrayList<String>();
		BPI2011all100Nets.add("BPI2011all100_induct_1_0");
		BPI2011all100Nets.add("BPI2011all100_induct_0_9");
		BPI2011all100Nets.add("BPI2011all100_induct_0_8");
		BPI2011all100Nets.add("BPI2011all100_induct_0_7");
		BPI2011all100Nets.add("BPI2011all100_induct_0_6");
		BPI2011all100Nets.add("BPI2011all100_induct_0_5");
		BPI2011all100Nets.add("BPI2011all100_induct_0_4");
		BPI2011all100Nets.add("BPI2011all100_induct_0_3");
		BPI2011all100Nets.add("BPI2011all100_induct_0_2");
		BPI2011all100Nets.add("BPI2011all100_induct_0_1");
		BPI2011all100Nets.add("BPI2011all100_induct_0_0");
		
		// BPI2011all90 nets - DONE
		List<String> BPI2011all90Nets = new ArrayList<String>();
		BPI2011all90Nets.add("BPI2011all90_induct_1_0");
		BPI2011all90Nets.add("BPI2011all90_induct_0_9");
		BPI2011all90Nets.add("BPI2011all90_induct_0_8");
		BPI2011all90Nets.add("BPI2011all90_induct_0_7");
		BPI2011all90Nets.add("BPI2011all90_induct_0_6");
		BPI2011all90Nets.add("BPI2011all90_induct_0_5");
		BPI2011all90Nets.add("BPI2011all90_induct_0_4");
		BPI2011all90Nets.add("BPI2011all90_induct_0_3");
		BPI2011all90Nets.add("BPI2011all90_induct_0_2");
		BPI2011all90Nets.add("BPI2011all90_induct_0_1");
		BPI2011all90Nets.add("BPI2011all90_induct_0_0");
		
		// BPI2011all80 nets - DONE
		List<String> BPI2011all80Nets = new ArrayList<String>();
		BPI2011all80Nets.add("BPI2011all80_induct_1_0");
		BPI2011all80Nets.add("BPI2011all80_induct_0_9");
		BPI2011all80Nets.add("BPI2011all80_induct_0_8");
		BPI2011all80Nets.add("BPI2011all80_induct_0_7");
		BPI2011all80Nets.add("BPI2011all80_induct_0_6");
		BPI2011all80Nets.add("BPI2011all80_induct_0_5");
		BPI2011all80Nets.add("BPI2011all80_induct_0_4");
		BPI2011all80Nets.add("BPI2011all80_induct_0_3");
		BPI2011all80Nets.add("BPI2011all80_induct_0_2");
		BPI2011all80Nets.add("BPI2011all80_induct_0_1");
		BPI2011all80Nets.add("BPI2011all80_induct_0_0");
		
		// BPI2014all100 nets - DONE
		List<String> BPI2014all100Nets = new ArrayList<String>();
		BPI2014all100Nets.add("BPI2014all100_induct_1_0");
		BPI2014all100Nets.add("BPI2014all100_induct_0_9");
		BPI2014all100Nets.add("BPI2014all100_induct_0_8");
		BPI2014all100Nets.add("BPI2014all100_induct_0_7");
		BPI2014all100Nets.add("BPI2014all100_induct_0_6");
		BPI2014all100Nets.add("BPI2014all100_induct_0_5");
		BPI2014all100Nets.add("BPI2014all100_induct_0_4");
		BPI2014all100Nets.add("BPI2014all100_induct_0_3");
		BPI2014all100Nets.add("BPI2014all100_induct_0_2");
		BPI2014all100Nets.add("BPI2014all100_induct_0_1");
		BPI2014all100Nets.add("BPI2014all100_induct_0_0");
		
		// BPI2014all90 nets - DONE
		List<String> BPI2014all90Nets = new ArrayList<String>();
		BPI2014all90Nets.add("BPI2014all90_induct_1_0");
		BPI2014all90Nets.add("BPI2014all90_induct_0_9");
		BPI2014all90Nets.add("BPI2014all90_induct_0_8");
		BPI2014all90Nets.add("BPI2014all90_induct_0_7");
		BPI2014all90Nets.add("BPI2014all90_induct_0_6");
		BPI2014all90Nets.add("BPI2014all90_induct_0_5");
		BPI2014all90Nets.add("BPI2014all90_induct_0_4");
		BPI2014all90Nets.add("BPI2014all90_induct_0_3");
		BPI2014all90Nets.add("BPI2014all90_induct_0_2");
		BPI2014all90Nets.add("BPI2014all90_induct_0_1");
		BPI2014all90Nets.add("BPI2014all90_induct_0_0");
		
		// BPI2014all80 nets - DONE
		List<String> BPI2014all80Nets = new ArrayList<String>();
		BPI2014all80Nets.add("BPI2014all80_induct_1_0");
		BPI2014all80Nets.add("BPI2014all80_induct_0_9");
		BPI2014all80Nets.add("BPI2014all80_induct_0_8");
		BPI2014all80Nets.add("BPI2014all80_induct_0_7");
		BPI2014all80Nets.add("BPI2014all80_induct_0_6");
		BPI2014all80Nets.add("BPI2014all80_induct_0_5");
		BPI2014all80Nets.add("BPI2014all80_induct_0_4");
		BPI2014all80Nets.add("BPI2014all80_induct_0_3");
		BPI2014all80Nets.add("BPI2014all80_induct_0_2");
		BPI2014all80Nets.add("BPI2014all80_induct_0_1");
		BPI2014all80Nets.add("BPI2014all80_induct_0_0");*/
		
		
		// log2nets
		log2nets.put("AO",aoNet); // DONE
		/*log2nets.put("AO2",aoNets); // DONE
		log2nets.put("BPI2013all100",BPI2013all100Nets); // DONE
		log2nets.put("BPI2013all90",BPI2013all90Nets);*/ // DONE
		//log2nets.put("BPI2013all80",BPI2013all80Nets); // DONE
		/*log2nets.put("BPI2012all100",BPI2012all100Nets); // DONE
		log2nets.put("BPI2012all90",BPI2012all90Nets); // DONE
		log2nets.put("BPI2012all80",BPI2012all80Nets); // DONE
		log2nets.put("BPI2012comp100",BPI2012comp100Nets); // DONE
		log2nets.put("BPI2012comp90",BPI2012comp90Nets); // DONE
		log2nets.put("BPI2012comp80",BPI2012comp80Nets); // DONE
		log2nets.put("BPI2011all100",BPI2011all100Nets); // DONE
		log2nets.put("BPI2011all90",BPI2011all90Nets); // DONE
		log2nets.put("BPI2011all80",BPI2011all80Nets); // DONE
		log2nets.put("BPI2014all80",BPI2014all80Nets); // DONE
		log2nets.put("BPI2014all90",BPI2014all90Nets); // DONE
		log2nets.put("BPI2014all100",BPI2014all100Nets); // DONE
*/		
		// log2classifier
		log2classifier.put("AO",XLogInfoImpl.NAME_CLASSIFIER); // DONE
		log2classifier.put("AO2",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2013all100",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2013all90",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2013all80",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2012all100",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2012all90",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2012all80",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2012comp100",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2012comp90",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2012comp80",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2011all100",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2011all90",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2011all80",XLogInfoImpl.STANDARD_CLASSIFIER); // DONE
		log2classifier.put("BPI2014all100",XLogInfoImpl.NAME_CLASSIFIER); // DONE
		log2classifier.put("BPI2014all90",XLogInfoImpl.NAME_CLASSIFIER); // DONE
		log2classifier.put("BPI2014all80",XLogInfoImpl.NAME_CLASSIFIER); // DONE
		
		// algorithms
		//algs.add(RR_SEARCH_ALGORITHM.KNAPSACK_ONE);
		algs.add(RR_SEARCH_ALGORITHM.GOLDRATT_ALL);
		//algs.add(RR_SEARCH_ALGORITHM.GOLDRATT_ONE);
		//algs.add(RR_SEARCH_ALGORITHM.GREEDY_ALL);
		//algs.add(RR_SEARCH_ALGORITHM.GREEDY_ONE);
		
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
		int maxRepairResources = 5;
		
		List<String> Logs = new ArrayList<>();
		Logs.add("AO");
		/*Logs.add("AO2");
		Logs.add("BPI2013all100");
		Logs.add("BPI2013all90");*/
		//Logs.add("BPI2013all80");
		/*Logs.add("BPI2012all100");
		Logs.add("BPI2012all90");
		Logs.add("BPI2012all80");
		Logs.add("BPI2012comp100");
		Logs.add("BPI2012comp90");
		Logs.add("BPI2012comp80");
		Logs.add("BPI2011all100");
		Logs.add("BPI2011all90");
		Logs.add("BPI2011all80");
		Logs.add("BPI2014all100");
		Logs.add("BPI2014all90");
		Logs.add("BPI2014all80");*/
	
		
		
		// EXPERIMENT
		System.out.println("Alg.,Log,Net,Res,Time,Recs,Recs#,Comp#,Cost,Correct");

		Map<RR_SEARCH_ALGORITHM,Integer> alg2lastCost = new HashMap<>();
		for (String logFile : Logs) {
			log = XLogReader.openLog("./exp/"+logFile+".xes.gz");
			
			List<String> netFiles = log2nets.get(logFile);
			for (String netFile : netFiles) {
				
				alg2lastCost.put(RR_SEARCH_ALGORITHM.BF, Integer.MAX_VALUE);
				alg2lastCost.put(RR_SEARCH_ALGORITHM.BF2, Integer.MAX_VALUE);
				alg2lastCost.put(RR_SEARCH_ALGORITHM.GOLDRATT_ALL, Integer.MAX_VALUE);
				alg2lastCost.put(RR_SEARCH_ALGORITHM.GOLDRATT_ONE, Integer.MAX_VALUE);
				alg2lastCost.put(RR_SEARCH_ALGORITHM.GREEDY_ALL, Integer.MAX_VALUE);
				alg2lastCost.put(RR_SEARCH_ALGORITHM.GREEDY_ONE, Integer.MAX_VALUE);
				alg2lastCost.put(RR_SEARCH_ALGORITHM.KNAPSACK_ALL, Integer.MAX_VALUE);
				alg2lastCost.put(RR_SEARCH_ALGORITHM.KNAPSACK_ONE, Integer.MAX_VALUE);
				
				for (int res=0; res<=maxRepairResources; res++) {
					for (RR_SEARCH_ALGORITHM alg : algs) {
						if (alg2lastCost.get(alg)==0) continue;
						
						System.out.print(alg+",");
						System.out.print(logFile+",");
						System.out.print(netFile+",");
						
						try {
							net = constructNet("./exp/"+netFile+".pnml");
							initialMarking = getInitialMarking(net);
							finalMarkings = getFinalMarkings(net);
							costMOS = constructMOSCostFunction(net);
							XEventClass dummyEvClass = new XEventClass("DUMMY",99999);
							XEventClassifier eventClassifier = log2classifier.get(logFile);
							costMOT = constructMOTCostFunction(net,log,eventClassifier,dummyEvClass);
							mapping = constructMapping(net,log,dummyEvClass, eventClassifier);
							
							boolean considerAll = true;
							switch (alg) {
								case BF : 
									rrSearch = new BruteForceRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
									break;
								case BF2:
									rrSearch = new BruteForceRepairRecommendationSearchWithOptimization(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
									break;
								case GREEDY_ONE:
									considerAll = false;
								case GREEDY_ALL:
									rrSearch = new GreedyRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
									break;
								case GOLDRATT_ONE:
									considerAll = false;
								case GOLDRATT_ALL:
									rrSearch = new GoldrattRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
									break;
								case KNAPSACK_ONE:
									considerAll = false;
									rrSearch = new KnapsackRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
									break;
								default:
									break;
							}
							
							Set<String> netLabels = CostFunction.getLabels(net);
							Set<String> logLabels = CostFunction.getLabels(log,eventClassifier);
							logLabels.add("DUMMY");
							Map<String,Integer> insertCosts = CostFunction.getStdCostFunctionOnLabels(logLabels);
							Map<String,Integer> skipCosts = CostFunction.getStdCostFunctionOnLabels(netLabels);
							RepairConstraint constraint = new RepairConstraint(insertCosts,skipCosts,res);
							
							System.out.print(res+",");
							
							long start = System.nanoTime();
							Set<RepairRecommendation> recs = rrSearch.computeOptimalRepairRecommendations(constraint,considerAll);
							long end = System.nanoTime();
							
							System.out.print((end-start)+",");
							System.out.print(recs.toString().replace(",", ";")+",");
							System.out.print(recs.size()+",");
							System.out.print(rrSearch.getNumberOfAlignmentCostComputations()+",");
							int optCost=rrSearch.getOptimalCost();
							System.out.print(optCost+",");
							
							alg2lastCost.put(alg,optCost);
							
							boolean good = true;
							int count = 0;
							for (RepairRecommendation rec : recs) {
								String repairedName = String.format("./exp/REPAIRED.%s.%s.%s.%s.%s.%s.pnml",alg,netFile,logFile,res,count,rrSearch.getOptimalCost());
								
								PetrinetGraph repaired = rrSearch.repair(rec);
								rrSearch.serializeNet(repaired,repairedName);
								
								count++;
								
								// check cost
								PetrinetGraph repairedNet = constructNet(repairedName);
								Marking rInitialMarking = getInitialMarking(repairedNet);
								Marking[] rFinalMarkings = getFinalMarkings(repairedNet);
								Map<Transition,Integer> rCostMOS = constructMOSCostFunction(repairedNet);
								Map<XEventClass,Integer> rCostMOT = constructMOTCostFunction(repairedNet,log,eventClassifier,dummyEvClass);
								TransEvClassMapping rMapping = constructMapping(repairedNet,log,dummyEvClass,eventClassifier);
								
								RepairRecommendationSearch rrrSearch = new KnapsackRepairRecommendationSearch(repairedNet, rInitialMarking, rFinalMarkings, log, rCostMOS, rCostMOT, rMapping, eventClassifier, debug);
								
								Set<String> rNetLabels = CostFunction.getLabels(repairedNet);
								Set<String> rLogLabels = CostFunction.getLabels(log,eventClassifier);
								rLogLabels.add("DUMMY");
								Map<String,Integer> rInsertCosts = CostFunction.getStdCostFunctionOnLabels(rLogLabels);
								Map<String,Integer> rSkipCosts = CostFunction.getStdCostFunctionOnLabels(rNetLabels);
								RepairConstraint rConstraint = new RepairConstraint(rInsertCosts,rSkipCosts,0);
								
								rrrSearch.computeOptimalRepairRecommendations(rConstraint,false);
								
								int optCost2 = rrrSearch.getOptimalCost();
								
								boolean good2 = (optCost2==optCost); 
								good &= good2;
								
								if (!good2) System.out.print(repairedName + "," + optCost2 + ",");
							}
							
							System.out.print(good ? "YES" : "NO");
							System.out.println();
						}
						catch (Exception e) {
							System.out.print("EXCEPTION");
							System.out.println();
						}
					}
				}
			}
		}
		
		System.out.println("DONE!");
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

	private static Map<XEventClass, Integer> constructMOTCostFunction(PetrinetGraph net, XLog log, XEventClassifier eventClassifier, XEventClass dummyEvClass) {
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
			if (p.getLabel().contains("_END"))
				finalMarking.add(p);
		}
			
		Marking[] finalMarkings = new Marking[1];
		finalMarkings[0] = finalMarking;
		
		return finalMarkings;
	}

	private static Marking getInitialMarking(PetrinetGraph net) {
		Marking initMarking = new Marking();
		
		for (Place p : net.getPlaces()) {
			if (p.getLabel().contains("_START"))
				initMarking.add(p);
		}
		
		return initMarking;
	}

	private static PetrinetGraph constructNet(String netFile) {
		PNMLSerializer PNML = new PNMLSerializer();
		NetSystem sys = PNML.parse(netFile);
		
		int pi,ti;
		pi = ti = 1;
		
		for (org.jbpt.petri.Place p : sys.getPlaces()) {
			if (p.getName().contains("_START")) 
				p.setName("p"+(pi++)+"_START");
			else if (p.getName().contains("_END")) 
				p.setName("p"+(pi++)+"_END");
			else  
				p.setName("p"+pi++);
		}
		
		for (org.jbpt.petri.Transition t : sys.getTransitions()) 
			t.setName("t"+ti++);
		
		PetrinetGraph net = PetrinetFactory.newPetrinet(netFile);
		
		// places
		Map<org.jbpt.petri.Place,Place> p2p = new HashMap<>();
		for (org.jbpt.petri.Place p : sys.getPlaces()) {
			Place pp = null;
			
			if (sys.isMarked(p) || p.getName().contains("_START")) {
				pp = net.addPlace(p.getName()+"_START");
			}
			else {
				if (sys.getPostset(p).isEmpty() || p.getName().contains("_END"))
					pp = net.addPlace(p.getName()+"_END");
				else
					pp = net.addPlace(p.getName());
					
			}
			
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
