package org.jbpt.mining.repair.main;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
 * Command line interface to techniques reported in the "Impact-driven Process Model Repair" paper.
 * 
 * @version 1.0
 * 
 * @author Artem Polyvyanyy 
 */ 
public final class ImpactDrivenPMRepairCLI {
	final private static String	version	= "1.0";
	
	private static Map<Transition,Integer>	costMOS = null; // movements on system
	private static Map<XEventClass,Integer>	costMOT = null; // movements on trace
	private static TransEvClassMapping		mapping = null;
	private static Map<String,Integer>		insertCosts = null;
	private static Map<String,Integer>		skipCosts = null;
		
	public static void main(String[] args) throws Exception {
		// read parameters from the CLI
		CommandLineParser parser = new DefaultParser();
		
		Options options = null;
	    try {
	    	// create Options object
	    	options = new Options();
	    	
	    	// create options
	    	Option helpOption		= Option.builder("h").longOpt("help").numberOfArgs(0).required(false).desc("print help message").hasArg(false).build();
	    	Option versionOption	= Option.builder("v").longOpt("version").numberOfArgs(0).required(false).desc("get version of this tool").hasArg(false).build();
	    	Option pnmlOption		= Option.builder("p").longOpt("pnml").hasArg(true).optionalArg(false).valueSeparator().argName("PNML path").required(false).desc("PNML path").build();
	    	
	    	Option xesOption		= Option.builder("x").longOpt("xes").hasArg(true).optionalArg(false).valueSeparator().argName("XES path").required(false).desc("XES path").build();	    	
	    	Option resOption		= Option.builder("r").longOpt("res").hasArg(true).optionalArg(false).valueSeparator().argName("resources").required(false).desc("amount of available repair resources").build();
	    	Option stdOption		= Option.builder("s").longOpt("std").numberOfArgs(0).required(false).desc("request to use the standard cost function on legal moves (alignment) and the standard repair constraint)").hasArg(false).build();
	    	Option finOption		= Option.builder("f").longOpt("fin").numberOfArgs(0).required(false).desc("request to use the standard final marking, i.e., the marking that puts one token at every sink place and no tokens elsewhere").hasArg(false).build();
	    	Option clsOption		= Option.builder("c").longOpt("cls").hasArg(true).optionalArg(false).valueSeparator().argName("classifier").required(false).desc("event classifier (\"standard\" or \"name\", use \"standard\" by default)").build();
	    	
	    	Option mOption			= Option.builder("m").longOpt("multiple").numberOfArgs(0).required(false).desc("discover multiple repair recommendations").hasArg(false).build();
	    	
	    	Option kOption			= Option.builder("k").longOpt("knapsack").numberOfArgs(0).required(false).desc("use knapsack optimization").hasArg(false).build();
	    	Option gOption			= Option.builder("g").longOpt("goldratt").numberOfArgs(0).required(false).desc("use Goldratt optimization").hasArg(false).build();
	    	Option rOption			= Option.builder("e").longOpt("greedy").numberOfArgs(0).required(false).desc("use greedy optimization").hasArg(false).build();
	    	Option oOption			= Option.builder("o").longOpt("obf").numberOfArgs(0).required(false).desc("use optimized brute-force").hasArg(false).build();
	    	
	    	// create groups
	    	OptionGroup cmdGroup = new OptionGroup();
	    	cmdGroup.addOption(helpOption);
	    	cmdGroup.addOption(versionOption);
	    	cmdGroup.addOption(pnmlOption);
	    	cmdGroup.setRequired(true);
	    	
	    	OptionGroup hGroup = new OptionGroup();
	    	hGroup.addOption(kOption);
	    	hGroup.addOption(gOption);
	    	hGroup.addOption(rOption);
	    	hGroup.addOption(oOption);
	    	hGroup.setRequired(false);
	    	
	    	options.addOptionGroup(cmdGroup);
	    	options.addOptionGroup(hGroup);
	    	
	    	options.addOption(xesOption);
	    	options.addOption(resOption);
	    	options.addOption(stdOption);
	    	options.addOption(finOption);
	    	options.addOption(clsOption);
	    	options.addOption(mOption);
	    	
	        // parse the command line arguments
	        CommandLine cmd = parser.parse(options, args);
	        
	        // handle help
	        if(cmd.hasOption("h") || cmd.getOptions().length==0) {
	        	showHelp(options);
	        	return;
	        }
	        
	        // handle version
	        if(cmd.hasOption("v")) {
	        	System.out.println(ImpactDrivenPMRepairCLI.version);
	        	return;
	        }
	        
	        // handle repair
	        XLog						log = null;
	        int							res = 1000;
	        String 						xesPath = null;
	        String 						pnmlPath = null;
	        PetrinetGraph 				net = null;
			Marking 					initialMarking = null;
			Marking[] 					finalMarkings = null; // only one marking is used so far
			RepairRecommendationSearch	rrSearch = null;
			RepairConstraint			constraint = null;
	        
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			
			showHeader();

	        if(cmd.hasOption("p")) {
	        	if(cmd.hasOption("x")) {
	        		// DONE: Parse PNML
	        		pnmlPath = cmd.getOptionValue("p");
	        		PNMLSerializer PNML = new PNMLSerializer();
	        		NetSystem sys = PNML.parse(pnmlPath);
	        		Map<org.jbpt.petri.Place,Place> p2p = new HashMap<>();
	        		net = constructNet(sys,p2p);
	        		System.out.println(String.format("%s: PNML file %s loaded.", sdfDate.format(new Date()),pnmlPath));
	        		
	        		// DONE: get initial marking
	        		initialMarking = constuctInitialMarkings(sys,p2p,net);
	        		System.out.println(String.format("%s: In the initial marking the net contains %s.", sdfDate.format(new Date()), constructMarkingString(initialMarking)));
	        		
	        		// DONE: get final marking
	        		if(cmd.hasOption("r")) {
	        			finalMarkings = constuctStandardFinalMarking(net);
	        			System.out.println(String.format("%s: The final marking of the net is set to %s.", sdfDate.format(new Date()), constructMarkingString(finalMarkings[0])));
	        		}
	        		else {
	        			// TODO
	        			finalMarkings = constuctStandardFinalMarking(net);
	        			System.out.println(String.format("%s: The final marking of the net is set to %s.", sdfDate.format(new Date()), constructMarkingString(finalMarkings[0])));
	        		}
	        		
	        		// DONE: Parse XES
	        		xesPath = cmd.getOptionValue("x");
	        		try {
						log = XLogReader.openLog(xesPath);
					} catch (Exception e) {
						System.out.println(String.format("%s: ERROR - Cannot parse XES file %s. Message: %s",sdfDate.format(new Date()),xesPath,e.getMessage()));
						return;
					}	        		
	        		System.out.println(String.format("%s: XES file %s loaded.",sdfDate.format(new Date()),xesPath));
	        		
	        		
	        		// DONE: Identify repair resources
	        		if(cmd.hasOption("r")) {
	        			try {
	        				res = Integer.parseInt(cmd.getOptionValue("r"));
	        			} catch (NumberFormatException e) {
	        				System.out.println(String.format("%s: The specified amount of repair resources is not an integer (option -r). The default value of %s will be used.",sdfDate.format(new Date()),res));
	        			}
	        			
	        			System.out.println(String.format("%s: The amount of repair resources is set to %s.",sdfDate.format(new Date()),res));
	        		}
	        		else
	        			System.out.println(String.format("%s: The amount of repair resources is not specified (option -r). The default value of %s will be used.",sdfDate.format(new Date()),res));
	        		
	        		// DONE: Configure event classifier
	        		XEventClassifier eventClassifier = cmd.hasOption("c") && cmd.getOptionValue("c").trim().toLowerCase().equals("name") ?   
	        					XLogInfoImpl.NAME_CLASSIFIER : XLogInfoImpl.STANDARD_CLASSIFIER;
	        		System.out.println(String.format("%s: The tool is configured to use %s event classifier.",sdfDate.format(new Date()), eventClassifier==XLogInfoImpl.STANDARD_CLASSIFIER ? "STANDARD" : "NAME"));
	        		
	        		// DONE: identify cost functions
	        		XEventClass dummyEvClass = new XEventClass("DUMMY",99999);
	        		costMOS = constructMOSCostFunction(net);
					costMOT = constructMOTCostFunction(net,log,eventClassifier,dummyEvClass);
					Set<String> netLabels = CostFunction.getLabels(net);
					Set<String> logLabels = CostFunction.getLabels(log,eventClassifier);
					//logLabels.add("DUMMY");
					insertCosts = CostFunction.getStdCostFunctionOnLabels(logLabels);
					skipCosts 	= CostFunction.getStdCostFunctionOnLabels(netLabels);
					constraint = new RepairConstraint(insertCosts,skipCosts,res);
					mapping = constructMapping(net,log,dummyEvClass, eventClassifier);
	        		
	        		printCostsAndMapping(sdfDate);
					
	        		if(!cmd.hasOption("s")) {
	        			handleCostChanges(sdfDate);
	        		}
	        		
	        		System.out.println("--------------------------------------------------------------------------------");
	        		
	        		// DONE: choose heuristics
	        		String heuristics = "BRUTE-FORCE";
	        		if (cmd.hasOption("k")) {
	        			rrSearch = new KnapsackRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, false);
	        			System.out.println(String.format("%s: The tool is configured to use KNAPSACK optimization.",sdfDate.format(new Date())));
	        			heuristics = "KNAPSACK";
	        		}
	        		else if (cmd.hasOption("g")) {
	        			rrSearch = new GoldrattRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, false);
	        			System.out.println(String.format("%s: The tool is configured to use GOLDRATT optimization.",sdfDate.format(new Date())));
	        			heuristics = "GOLDRATT";
	        		}
	        		else if (cmd.hasOption("e")) {
	        			rrSearch = new GreedyRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, false);
	        			System.out.println(String.format("%s: The tool is configured to use GREEDY optimization.",sdfDate.format(new Date())));
	        			heuristics = "GREEDY";
	        		}
	        		else if (cmd.hasOption("o")) {
	        			rrSearch = new BruteForceRepairRecommendationSearchWithOptimization(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, false);
	        			System.out.println(String.format("%s: The tool is configured to use OPTIMIZED BRUTE-FORCE.",sdfDate.format(new Date())));
	        			heuristics = "OPTIMIZED-BRUTE-FORCE";
	        		}
	        		else {
	        			rrSearch = new BruteForceRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, false);
	        			System.out.println(String.format("%s: The tool is configured to use BRUTE-FORCE.",sdfDate.format(new Date())));
	        		}
	        		
	        		// DONE: discover single or multiple repair recommendations
	        		boolean singleton = true;
	        		if (cmd.hasOption("m")) {
	        			singleton = false;
	        			System.out.println(String.format("%s: The tool is configured to discover multiple repair recommendations.",sdfDate.format(new Date())));
	        		}
	        		else 
	        			System.out.println(String.format("%s: The tool is configured to discover single repair recommendation.",sdfDate.format(new Date())));
	        		
	        		System.out.println("--------------------------------------------------------------------------------");
	        		
	        		// DONE: discover repair recommendation(s)
	        		long start = System.nanoTime();
					Set<RepairRecommendation> recs = rrSearch.computeOptimalRepairRecommendations(constraint,singleton);
					long end = System.nanoTime();
					System.out.println(String.format("%s: Repair recommendation(s) discovered in %s nano seconds.",sdfDate.format(new Date()),(end-start)));
					for (RepairRecommendation r : recs)
						System.out.println(String.format("%s: Discovered repair recommendation: insert %s and skip %s.",sdfDate.format(new Date()),r.getInsertLabels(),r.getSkipLabels()));
					System.out.println(String.format("%s: Number of performed optimal alignment computations: %s.",sdfDate.format(new Date()),rrSearch.getNumberOfAlignmentComputations()));
					System.out.println(String.format("%s: Sum of costs of optimal alignments between traces in the event log and the repaired model: %s.",sdfDate.format(new Date()),rrSearch.getOptimalAlignmentCost()));
					
	        		// DONE: construct repaired model
					int count = 0;
					for (RepairRecommendation rec : recs) {
						String repairedName = String.format("./REPAIRED.%s.%s.%s.%s.pnml",heuristics,constraint.getAvailableResources(),rrSearch.getOptimalAlignmentCost(),count++);
						PetrinetGraph repaired = rrSearch.repair(rec);
						rrSearch.serializeNet(repaired,repairedName);
						System.out.println(String.format("%s: Repaired model serialized in %s.",sdfDate.format(new Date()),repairedName));
					}
	        	}
	        	else
	        		System.out.println(String.format("%s: Event log is not specified. Use option -x to specify event log to be used for repair.",sdfDate.format(new Date())));
	        	
	        	return;
	        }
	    }
	    catch (ParseException exp) {
	        // oops, something went wrong
	        System.err.println("CLI parsing failed. Reason: " + exp.getMessage() + "\n");
	        showHelp(options);
	        return;
	    }
	}
	
	private static String constructMarkingString(Marking m) {
		if (m == null || m.isEmpty()) return "no tokens";
		
		String result = "";
		Iterator<Place> i = m.baseSet().iterator();
		
		while (i.hasNext()) {
			Place p = i.next();
			
			if (m.occurrences(p)==1)
				result += String.format("%s token at place '%s', and ", m.occurrences(p), p);
			else
				result += String.format("%s tokens at place '%s', and ", m.occurrences(p), p);
		}
		result += "no tokens elsewhere"; 
				
		return result;
	}

	private static void handleCostChanges(SimpleDateFormat sdfDate) {
		System.out.print(String.format("%s: Do you want to change costs (Y/N): ",sdfDate.format(new Date())));
		
		Scanner scanner = new Scanner(System.in);
		String input = scanner.next().trim().toLowerCase();
		
		while (input.equals("y")) {
        	System.out.println(String.format("%s: 1. Change cost of a move on system",sdfDate.format(new Date())));
        	System.out.println(String.format("%s: 2. Change cost of a move on trace",sdfDate.format(new Date())));
        	System.out.println(String.format("%s: 3. Change cost of inserting a label",sdfDate.format(new Date())));
        	System.out.println(String.format("%s: 4. Change cost of skippig a label",sdfDate.format(new Date())));
        	System.out.println(String.format("%s: 5. None of the above",sdfDate.format(new Date())));
        	
        	input = "";
        	do {
        		System.out.print(String.format("%s: Make your choice: ",sdfDate.format(new Date())));
        		input = scanner.next().trim().toLowerCase();
        	}
        	while (!input.equals("1") && !input.equals("2") && !input.equals("3") && !input.equals("4") && !input.equals("5"));
        	
        	if (input.equals("1") || input.equals("2") || input.equals("3") || input.equals("4")) {
        		String label = "";
            	System.out.print(String.format("%s: Enter label: ",sdfDate.format(new Date())));
        		label = scanner.next().trim();
        		
        		String cost = "";
            	System.out.print(String.format("%s: Enter cost: ",sdfDate.format(new Date())));
        		cost = scanner.next().trim().toLowerCase();
            	
            	boolean costb = true;
            	try {
            		int c = Integer.parseInt(cost);
            		
            		if (costb && c>=0) {
                		switch (input) {
                    		case "1":
                    			augmentMoveOnSystemCost(label,c);
                    			break;
                    		case "2":
                    			augmentMoveOnTraceCost(label,c);
                    			break;
                    		case "3":
                    			if (insertCosts.entrySet().contains(label)) insertCosts.put(label,c);            			
                    			break;
                    		case "4":
                    			if (skipCosts.entrySet().contains(label)) skipCosts.put(label,c);
                    			break;
                    		case "5":
                    			break;
                    	}	
                	}
                	else {
                		System.out.println(String.format("%s: Error - cost must be a natural number.",sdfDate.format(new Date())));
                	}
            	}
            	catch (NumberFormatException e) {
            		System.out.println(String.format("%s: Error - entered string is not a number.",sdfDate.format(new Date())));
            	}	
        	}
        	
        	printCostsAndMapping(sdfDate);
        	
			System.out.print(String.format("%s: Do you want to change costs (Y/N): ",sdfDate.format(new Date())));
			input = scanner.next().trim().toLowerCase();
        }
		
		scanner.close();
	}
	
	private static void augmentMoveOnTraceCost(String label, int c) {
		XEventClass x = null;
		
		for (XEventClass e : costMOT.keySet()) {
			if (e.toString().trim().equals(label)) {
				x = e;
				break;
			}
		}
		
		if (x!=null) costMOT.put(x,c);
	}

	private static void augmentMoveOnSystemCost(String label, int c) {
		Transition x = null;
		
		for (Transition e : costMOS.keySet()) {
			if (e.getLabel().trim().equals(label)) {
				x = e;
				break;
			}
		}
		
		if (x!=null) costMOS.put(x,c);
	}

	private static void printCostsAndMapping(SimpleDateFormat sdfDate) {
		// DONE: print costs of moves on system 
		for (Map.Entry<Transition,Integer> e : costMOS.entrySet()) {
			System.out.println(String.format("%s: Cost of move '%s' on system is set to %s.",sdfDate.format(new Date()),e.getKey(),e.getValue()));	
		}
		
		// DONE: print costs of moves on system 
		for (Map.Entry<XEventClass,Integer> e : costMOT.entrySet()) {
			System.out.println(String.format("%s: Cost of move '%s' on trace is set to %s.",sdfDate.format(new Date()),e.getKey(),e.getValue()));	
		}
		
		// DONE: print transition-event mapping
		for (Map.Entry<Transition,XEventClass> e : mapping.entrySet()) {
			System.out.println(String.format("%s: The tool is configured to use transition-event mapping '%s-%s'.",sdfDate.format(new Date()),e.getKey(),e.getValue()));
		}
		
		// DONE: print insert costs
		for (Map.Entry<String,Integer> e : insertCosts.entrySet()) {
			System.out.println(String.format("%s: Cost of inserting label '%s' is set to %s.",sdfDate.format(new Date()),e.getKey(),e.getValue()));
		}
		
		// DONE: print skip costs
		for (Map.Entry<String,Integer> e : skipCosts.entrySet()) {
			System.out.println(String.format("%s: Cost of skipping label '%s' is set to %s.",sdfDate.format(new Date()),e.getKey(),e.getValue()));
		}
	}

	private static void showHelp(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		
		showHeader();
    	formatter.printHelp(80, "java -jar ImpactDrivenPMRepair.jar <options>", 
    							String.format(
    							"", ImpactDrivenPMRepairCLI.version), options, 
    							"================================================================================\n");
	}
	
	private static void showHeader() {
		System.out.println(String.format("================================================================================\n"+
		    	" Tool for (impact-based) process model repair ver. %s\n"+
				"================================================================================",ImpactDrivenPMRepairCLI.version)); 
	}
	
	private static PetrinetGraph constructNet(NetSystem sys, Map<org.jbpt.petri.Place,Place> p2p) {
			
		PetrinetGraph net = PetrinetFactory.newPetrinet(null);
		
		// places
		p2p.clear();
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
	
	private static Map<XEventClass, Integer> constructMOTCostFunction(PetrinetGraph net, XLog log, XEventClassifier eventClassifier, XEventClass dummyEvClass) {
		Map<XEventClass,Integer> costMOT = new HashMap<XEventClass,Integer>();		
		XLogInfo summary = XLogInfoFactory.createLogInfo(log,eventClassifier);
		
		for (XEventClass evClass : summary.getEventClasses().getClasses())
			costMOT.put(evClass,1);
		
		costMOT.put(dummyEvClass,1);
		
		return costMOT;
	}
	
	private static Map<Transition, Integer> constructMOSCostFunction(PetrinetGraph net) {
		Map<Transition,Integer> costMOS = new HashMap<Transition,Integer>();
		
		for (Transition  t : net.getTransitions())
			if (t.isInvisible()) costMOS.put(t,0);
			else costMOS.put(t,1);	
		
		return costMOS;
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
				mapping.put(t,dummyEvClass);
			}
				
		}
		
		return mapping;
	}
	
	private static Marking constuctInitialMarkings(NetSystem sys, Map<org.jbpt.petri.Place, Place> p2p, PetrinetGraph net) {			
		Marking initialMarking = new Marking();
		
		for (Map.Entry<org.jbpt.petri.Place,Integer> pm : sys.getMarking().entrySet()) {
			initialMarking.add(p2p.get(pm.getKey()), pm.getValue());
		}
		
		return initialMarking;
	}
	
	private static Marking[] constuctStandardFinalMarking(PetrinetGraph net) {			
		Marking finalMarking = new Marking();
		
		for (Place p : net.getPlaces()) {
			if (net.getOutEdges(p).isEmpty())
				finalMarking.add(p);
		}
			
		Marking[] finalMarkings = new Marking[1];
		finalMarkings[0] = finalMarking;
		
		return finalMarkings;
	}
}
