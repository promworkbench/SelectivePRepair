package org.jbpt.mining.cost;
import java.util.HashMap;
import java.util.Map;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.model.XLog;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.astar.petrinet.manifestreplay.CostBasedCompleteManifestParam;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.replayer.algorithms.IPNReplayParameter;
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedcomplete.CostBasedCompletePruneAlg;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.petrinet.replayresult.StepTypes;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;


/**
 * @author Artem Polyvyanyy
 */
public class AlignmentCostOptimizerGoldrattFrequencies extends AbstractAlignmentCostOptimizer {

	public AlignmentCostOptimizerGoldrattFrequencies(PetrinetGraph net,
			Marking initMarking, Marking[] finalMarkings, XLog log,
			Map<Transition, Integer> mapTrans2Cost,
			Map<XEventClass, Integer> mapEvClass2Cost,
			TransEvClassMapping mapping) throws Exception {
		super(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost,mapping);
	}

	public int findOptimalCost(int resources) {
		Map<AlignmentStep,Integer> frequencies = this.computeFrequencies(this.mapTrans2Cost, this.mapEvClass2Cost);
		
		System.out.println("Original MOM costs: "+ mapTrans2Cost);
		System.out.println("Original MOL costs: "+ mapEvClass2Cost);
		System.out.println("Misalignment frequencies: " + frequencies);
		
		Map<Transition,Integer>	 tmpTrans2Cost = new HashMap<Transition, Integer>();
		Map<XEventClass,Integer> tmpEvClass2Cost = new HashMap<XEventClass, Integer>();
		
		// weight transition costs
		for (Map.Entry<Transition,Integer> entryA : mapTrans2Cost.entrySet()) {
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
			
			tmpTrans2Cost.put(entryA.getKey(), entryA.getValue()*coef);
		}
		
		System.out.println("Weighted MOM costs: "+tmpTrans2Cost);
		
		// weight evClass costs
		for (Map.Entry<XEventClass,Integer> entryA : mapEvClass2Cost.entrySet()) {
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
			
			tmpEvClass2Cost.put(entryA.getKey(), entryA.getValue()*coef);
		}
		
		System.out.println("Weighted MOL costs: "+tmpEvClass2Cost);
		
		// compute optimal cost
		this.optTrans2Cost		= new HashMap<Transition, Integer>(this.mapTrans2Cost);
		this.optEvClass2Cost	= new HashMap<XEventClass, Integer>(this.mapEvClass2Cost);
		
		int res = resources;
		int maxCost = 1;
		while (res>0) {
			maxCost = 0;
			Object max = null;
			
			for (Map.Entry<Transition, Integer> entry : tmpTrans2Cost.entrySet()) {
				if (entry.getValue()>maxCost) {
					max = entry.getKey();
					maxCost = entry.getValue(); 
				}
			}
			
			for (Map.Entry<XEventClass, Integer> entry : tmpEvClass2Cost.entrySet()) {
				if (entry.getValue()>maxCost) {
					max = entry.getKey();
					maxCost = entry.getValue(); 
				}
			}
			
			if (maxCost==0) break;

			if (max instanceof Transition) {
				int cost = this.optTrans2Cost.get(max);
				
				if (res>=cost)
					this.optTrans2Cost.put((Transition) max, 0);
				else
					this.optTrans2Cost.put((Transition) max, cost-res);
				
				res-=cost;
				tmpTrans2Cost.put((Transition) max,0);
			}
			
			if (max instanceof XEventClass) {
				int cost = this.optEvClass2Cost.get(max);
				
				if (res>=cost)
					this.optEvClass2Cost.put((XEventClass) max, 0);
				else
					this.optEvClass2Cost.put((XEventClass) max, cost-res);
				
				res-=cost;
				tmpEvClass2Cost.put((XEventClass) max,0);
			}
		}
				
		System.out.println("Optimal MOM costs: "+this.optTrans2Cost);
		System.out.println("Optimal MOL costs: "+this.optEvClass2Cost);
		
		return this.computeCost(this.optTrans2Cost,this.optEvClass2Cost);
	}
	
	public class AlignmentStep {
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
	}
	
	public Map<AlignmentStep,Integer> computeFrequencies(Map<Transition,Integer> t2c, Map<XEventClass,Integer> e2c) {
		IPNReplayParameter parameters = new CostBasedCompleteManifestParam(e2c, t2c, 
										this.initMarking, this.finalMarkings, this.maxNumOfStates, this.restrictedTrans);
		parameters.setGUIMode(false);
		
		CostBasedCompletePruneAlg replayEngine = new CostBasedCompletePruneAlg();
		PNRepResult result = replayEngine.replayLog(this.context, this.net, this.log, this.mapping, parameters);
		
		Map<AlignmentStep,Integer> map = new HashMap<AlignmentStep, Integer>();
		for (SyncReplayResult res : result) {
			for (int i=0; i<res.getNodeInstance().size(); i++) {
				StepTypes type = res.getStepTypes().get(i);
				if (type==StepTypes.LMGOOD) continue;
				
				AlignmentStep step = new AlignmentStep();
				step.name = res.getNodeInstance().get(i);
				step.type = type;
				
				Integer c = map.get(step);
				if (c==null)
					map.put(step,1);
				else
					map.put(step, map.get(step)+1);
			}
		}
		
		return map;
	}
	
	@Override
	public int getNumberOfIterations() {
		return 2;
	}
}
