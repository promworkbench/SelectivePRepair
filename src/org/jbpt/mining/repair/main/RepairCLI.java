package org.jbpt.mining.repair.main;

import java.util.HashMap;
import java.util.Map;
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
import org.jbpt.mining.repair.CostFunction;
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
public final class RepairCLI {
	final private static String	version	= "1.0";
		
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
	    	
	    	// create group 
	    	
	    	OptionGroup cmdGroup = new OptionGroup();
	    	
	    	cmdGroup.addOption(helpOption);
	    	cmdGroup.addOption(versionOption);
	    	cmdGroup.addOption(pnmlOption);
	    	cmdGroup.setRequired(true);
	    	
	    	options.addOptionGroup(cmdGroup);
	    	
	    	options.addOption(xesOption);
	    	options.addOption(resOption);
	    	options.addOption(stdOption);
	    	
	        // parse the command line arguments
	        CommandLine cmd = parser.parse(options, args);
	        
	        // handle help
	        if(cmd.hasOption("h") || cmd.getOptions().length==0) {
	        	showHelp(options);
	        	return;
	        }
	        
	        // handle version
	        if(cmd.hasOption("v")) {
	        	System.out.println(RepairCLI.version);
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
			Map<Transition,Integer>		costMOS = null; // movements on system
			Map<XEventClass,Integer>	costMOT = null; // movements on trace
			TransEvClassMapping			mapping = null;
			RepairRecommendationSearch	rrSearch = null;
			RepairConstraint			constraint = null;
	        
	        if(cmd.hasOption("p")) {
	        	if(cmd.hasOption("x")) {
	        		// parse pnml
	        		xesPath = cmd.getOptionValue("p");
	        		net = constructNet(xesPath);
	        		
	        		System.out.println("PNML file parsed successfuly.");
	        		
	        		// parse xes
	        		xesPath = cmd.getOptionValue("x");
	        		try {
						log = XLogReader.openLog(xesPath);
					} catch (Exception e) {
						System.out.println(String.format("Error when reading the proposed event log: %s", e.getMessage()));
						return;
					}	        		
	        		System.out.println("XES file parsed successfuly.");
	        		
	        		// identify repair resources
	        		if(cmd.hasOption("r")) {
	        			try {
	        				res = Integer.parseInt(cmd.getOptionValue("r"));
	        			} catch (NumberFormatException e) {
	        				System.out.println(String.format("The specified amount of repair resources is not an integer (option -r). The default value of %s will be used.", res));
	        			}
	        			
	        			System.out.println(String.format("The amount of repair resources is set to %s.", res));
	        		}
	        		else
	        			System.out.println(String.format("The amount of repair resources is not specified (option -r). The default value of %s will be used.", res));
	        		
	        		// TODO: identify cost functions
	        		XEventClass dummyEvClass = new XEventClass("DUMMY",99999);
	        		
	        		// TODO: configure event classifier
	        		XEventClassifier eventClassifier = XLogInfoImpl.STANDARD_CLASSIFIER;
	        		
	        		if(cmd.hasOption("s") || !cmd.hasOption("s")) { // currently we always use standard costs
	        			costMOS = constructMOSCostFunction(net);
						costMOT = constructMOTCostFunction(net,log,eventClassifier,dummyEvClass);
						
						Set<String> netLabels = CostFunction.getLabels(net);
						Set<String> logLabels = CostFunction.getLabels(log,eventClassifier);
						logLabels.add("DUMMY");
						Map<String,Integer> insertCosts = CostFunction.getStdCostFunctionOnLabels(logLabels);
						Map<String,Integer> skipCosts = CostFunction.getStdCostFunctionOnLabels(netLabels);
						constraint = new RepairConstraint(insertCosts,skipCosts,res);
	        		}
	        		else {
	        			// TODO: construct complex cost functions
	        		}
	        		mapping = constructMapping(net,log,dummyEvClass, eventClassifier);
	        		
	        		System.out.println(costMOS); // TODO: remove
	        		System.out.println(costMOT); // TODO: remove
	        		System.out.println(mapping); // TODO: remove
	        		
	        		// TODO: get initial marking
	        		initialMarking = constuctDummyInitialMarkings(net);
	        		
	        		System.out.println(initialMarking);
	        		
	        		// TODO: get final marking
	        		finalMarkings = constuctDummyFinalMarkings(net);
	        		System.out.println(finalMarkings[0]);
	        		
	        		// TODO: choose heuristics 
	        		
	        		// TODO: discover repair recommendation
	        		boolean singleton = true;
	        		rrSearch = new KnapsackRepairRecommendationSearch(net, initialMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, false);
	        		long start = System.nanoTime();
					Set<RepairRecommendation> recs = rrSearch.computeOptimalRepairRecommendations(constraint,singleton);
					long end = System.nanoTime();
					System.out.println(String.format("Repair recommendations discovered in %s nano seconds.", (end-start)));
					System.out.println(String.format("Discovered repair recommendations: %s.", recs));
					System.out.println(String.format("Number of performed optimal alignment computations: %s.", rrSearch.getNumberOfAlignmentComputations()));
					System.out.println(String.format("Sum of costs of optimal alignments between traces in the event log and the repaired model: %s.", rrSearch.getOptimalAlignmentCost()));
					
	        		// TODO: construct repaired model
					int count = 0;
					for (RepairRecommendation rec : recs) {
						String repairedName = String.format("./REPAIRED.%s.pnml",count++);
						PetrinetGraph repaired = rrSearch.repair(rec);
						rrSearch.serializeNet(repaired,repairedName);
						System.out.println(String.format("Repaired model serialized in %s.", repairedName));
					}
	        	}
	        	else
	        		System.out.println("Event log is not specified. Use option -x to specify event log to be used for repair.");
	        	
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

	private static void showHelp(Options options) {
		HelpFormatter formatter = new HelpFormatter();
    	formatter.printHelp(80, "java -jar ImpactDrivenPMRepair.jar <options>", 
    							String.format(
    							"================================================================================\n"+
    							" Tool for (impact-based) process model repair ver. %s\n"+
    							"================================================================================\n", RepairCLI.version), 
    							options, 
    							"================================================================================\n");
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
	
	private static Marking constuctDummyInitialMarkings(PetrinetGraph net) {			
		Marking initialMarking = new Marking();
		
		for (Place p : net.getPlaces()) {
			if (net.getInEdges(p).isEmpty())
				initialMarking.add(p);
		}
		
		return initialMarking;
	}
	
	private static Marking[] constuctDummyFinalMarkings(PetrinetGraph net) {			
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
