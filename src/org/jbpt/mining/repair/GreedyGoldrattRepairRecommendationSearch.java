package org.jbpt.mining.repair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.model.XLog;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.replayresult.StepTypes;


/**
 * @author Artem Polyvyanyy
 */
public class GreedyGoldrattRepairRecommendationSearch extends RepairRecommendationSearch {
	
		public GreedyGoldrattRepairRecommendationSearch(PetrinetGraph	net, 
				Marking			initMarking, 
				Marking[]		finalMarkings, 
				XLog 			log, 
				Map<Transition,Integer>		costMOS, 
				Map<XEventClass,Integer>	costMOT, 
				TransEvClassMapping			mapping,
				XEventClassifier		 	eventClassifier,
				boolean 					debug) throws Exception {
		super(net, initMarking, finalMarkings, log, costMOS, costMOT, mapping, eventClassifier, debug);
	}
		
	Map<String,Integer> costFuncMOSwL = new HashMap<String,Integer>();
	Map<String,Integer> costFuncMOTwL = new HashMap<String,Integer>();
	private Map<String,Double> costFuncMOSwLperR = new HashMap<String,Double>();
	private Map<String,Double> costFuncMOTwLperR = new HashMap<String,Double>();

	@Override
	public Set<RepairRecommendation> computeOptimalRepairRecommendations(RepairConstraint constraint) {
		this.alignmentCostComputations	= 0;
		
		// empty recommendation to start with
		Set<RepairRecommendation> recs = new HashSet<RepairRecommendation>();
		RepairRecommendation recommendation	= new RepairRecommendation();
		recs.add(recommendation);
		
		do {
			this.optimalRepairRecommendations.clear();
			this.optimalRepairRecommendations.addAll(recs);
			recs.clear();
			
			for (RepairRecommendation r : this.optimalRepairRecommendations) {
				
				System.out.println("REC: " + r);
				
				Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
				Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
				this.adjustCostFuncMOS(tempMOS,r.getSkipLabels());
				this.adjustCostFuncMOT(tempMOT,r.getInsertLabels());
				
				Map<AlignmentStep,Integer> frequencies = this.computeFrequencies(tempMOS,tempMOT);
				this.updateFrequencies(frequencies,r);
				
				Map<Transition,Integer>	 costFuncMOSw = new HashMap<Transition, Integer>();
				Map<XEventClass,Integer> costFuncMOTw = new HashMap<XEventClass, Integer>();
				
				// weight transition costs
				for (Map.Entry<Transition,Integer> entryA : this.costFuncMOS.entrySet()) {
					int coef = 0;
					for (Map.Entry<AlignmentStep,Integer> entryB : frequencies.entrySet()) {
						AlignmentStep step = entryB.getKey();
						if (step.type!=StepTypes.MREAL) continue;
						Transition tt = (Transition) step.name;
						Transition t = entryA.getKey();
						if (!tt.equals(t)) continue;
						
						coef = entryB.getValue();
						break;
					}
					
					costFuncMOSw.put(entryA.getKey(), entryA.getValue()*coef);
				}
				
				// weight evClass costs
				for (Map.Entry<XEventClass,Integer> entryA : this.costFuncMOT.entrySet()) {
					int coef = 0;
					for (Map.Entry<AlignmentStep,Integer> entryB : frequencies.entrySet()) {
						AlignmentStep step = entryB.getKey();
						if (step.type!=StepTypes.L) continue;
						XEventClass ecc = (XEventClass) step.name;
						XEventClass ec = entryA.getKey();
						if (!ecc.equals(ec)) continue;
						
						coef = entryB.getValue();
						break;
					}
					
					costFuncMOTw.put(entryA.getKey(), entryA.getValue()*coef);
				}
				
				System.out.println("T:" + costFuncMOSw);		// weighted costs for MOS
				System.out.println("E:" + costFuncMOTw);		// weighted costs for MOT
				
				// get label weights
				costFuncMOSwL.clear();
				costFuncMOTwL.clear();
				List<Label> labels = new ArrayList<GreedyGoldrattRepairRecommendationSearch.Label>();
				
				for (Map.Entry<Transition,Integer> entry : costFuncMOSw.entrySet()) {
					String label = entry.getKey().getLabel();
					int weight = entry.getValue();
					
					if (weight <= 0) continue;
					
					Integer val = costFuncMOSwL.get(label);
					if (val==null) {
						costFuncMOSwL.put(label, weight);
						labels.add(new Label(label, true));
					}
					else {
						costFuncMOSwL.put(label, val+weight);
					}
				}
				
				for (Map.Entry<XEventClass,Integer> entry : costFuncMOTw.entrySet()) {
					String label = entry.getKey().getId();
					int weight = entry.getValue();
					
					if (weight <= 0) continue;
					
					Integer val = costFuncMOTwL.get(label);
					if (val==null) {
						costFuncMOTwL.put(label, weight);
						labels.add(new Label(label, false));
					}
					else {
						costFuncMOTwL.put(label, val+weight);
					}
				}
				
				System.out.println("WLT:" + costFuncMOSwL);		// weighted costs for MOS label
				System.out.println("WLE:" + costFuncMOTwL);		// weighted costs for MOT label
				
				// get per unit costs
				
				costFuncMOSwLperR.clear();
				costFuncMOTwLperR.clear();
				
				for (Map.Entry<String,Integer> entry : costFuncMOSwL.entrySet())
					costFuncMOSwLperR.put(entry.getKey(), (double) entry.getValue() / constraint.getSkipCosts().get(entry.getKey()));
				
				for (Map.Entry<String,Integer> entry : costFuncMOTwL.entrySet())
					costFuncMOTwLperR.put(entry.getKey(), (double) entry.getValue() / constraint.getInsertCosts().get(entry.getKey()));
				
				System.out.println("PUT:" + costFuncMOSwLperR);		// weighted costs for MOS label per resource
				System.out.println("PUE:" + costFuncMOTwLperR);		// weighted costs for MOT label per resource*/
				
				
				// sort labels
				System.out.println("not sorted: " + labels);
				Collections.sort(labels);
				Collections.reverse(labels);
				System.out.println("sorted: " + labels);
				
				
				Iterator<Label> i = labels.iterator();
				int w = 0;
				while (i.hasNext()) {
					Label cl = i.next();
					
					double cost = 0.0;
					if (cl.isTransition)
						cost = constraint.getSkipCosts().get(cl.label);
					else
						cost = constraint.getInsertCosts().get(cl.label);
					
					double price = CostFunction.getRequiredResources(constraint, r);
					
					// TODO add all extensions of r to recs!
					if (price+cost <= constraint.getAvailableResources()) {
						RepairRecommendation rec = r.clone();
						
						if (cl.isTransition)
							rec.skipLabels.add(cl.label);
						else
							rec.insertLabels.add(cl.label);
						
						recs.add(rec);
								
						break;
					}
				}
			}
			System.out.println("RECS: "+recs);
			System.out.println("==============================================================================");
			
		} while (!recs.isEmpty());
		
		RepairRecommendation rec = this.optimalRepairRecommendations.iterator().next();
		
		Map<Transition,Integer>  tempMOS	= new HashMap<Transition,Integer>(this.costFuncMOS);
		Map<XEventClass,Integer> tempMOT	= new HashMap<XEventClass,Integer>(this.costFuncMOT);
		this.adjustCostFuncMOS(tempMOS,rec.getSkipLabels());
		this.adjustCostFuncMOT(tempMOT,rec.getInsertLabels());
		
		this.optimalCost = this.computeCost(tempMOS, tempMOT);
		this.alignmentCostComputations++;
		
		return this.optimalRepairRecommendations;
	}
	
	private void updateFrequencies(Map<AlignmentStep,Integer> frequencies, RepairRecommendation r) {
		
		Set<AlignmentStep> toRemove = new HashSet<AlignmentStep>();
		
		for (Map.Entry<AlignmentStep,Integer> entry : frequencies.entrySet()) {
			AlignmentStep step = entry.getKey();
			
			if (step.type==StepTypes.MREAL && r.getSkipLabels().contains(step.name.toString()))
				toRemove.add(step);

			if (step.type==StepTypes.L && r.getInsertLabels().contains(step.name.toString()))
				toRemove.add(step);
		}
		
		for (AlignmentStep step : toRemove)
			frequencies.remove(step);
	}

	public class Label implements Comparable<Label> {
		public boolean isTransition = false;
		public String label = "";
		
		public Label(String label, boolean isT) {
			this.label = label;
			this.isTransition = isT;
		}
		
		@Override
		public String toString() {
			return String.format("[%s,%s]", this.label, this.isTransition ? "+" : "-");
		}

		@Override
		public int compareTo(Label o) {
			double wThis = 0;
			double wO = 0;
			
			if (this.isTransition)
				wThis = costFuncMOSwLperR.get(this.label);
			else
				wThis = costFuncMOTwLperR.get(this.label);
			
			if (o.isTransition)
				wO = costFuncMOSwLperR.get(o.label);
			else
				wO = costFuncMOTwLperR.get(o.label);
				
			if (wThis<wO) return -1;
			if (wThis>wO) return 1;
			
			return 0;
		}
	}
	
	/*public class AlignmentStep {
		public Object	 name = null;
		public StepTypes type = null;
		
		public int hashCode() {
			int result = name.hashCode()+11*type.hashCode(); 
			return result;
		}
		
		@Override
		public String toString() {
			return String.format("(%s,%s)",this.name.toString(),this.type);
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof AlignmentStep)) return false;
			AlignmentStep step = (AlignmentStep) obj;
			if (step.name.equals(this.name) && step.type==this.type) 
				return true;
			
			return false;
		}
	}*/
}
