package org.jbpt.mining.cost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.model.XLog;
import org.jbpt.algo.ListCombinationGenerator;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;


/**
 * @author Artem Polyvyanyy
 */
public class AlignmentCostOptimizerExhaustive extends AbstractAlignmentCostOptimizer {
	
		public AlignmentCostOptimizerExhaustive(PetrinetGraph net,
			Marking initMarking, Marking[] finalMarkings, XLog log,
			Map<Transition, Integer> mapTrans2Cost,
			Map<XEventClass, Integer> mapEvClass2Cost,
			TransEvClassMapping mapping, boolean outputFlag) throws Exception {
		super(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost,mapping, outputFlag);
	}

	@Override
	public int findOptimalCost(int resources) {
		List<List<Integer>> lists = new ArrayList<List<Integer>>();
		List<Object> list = new ArrayList<Object>();
		
		// prepare transitions
		for (Map.Entry<Transition,Integer> entry : this.mapTrans2Cost.entrySet()) {
			list.add(entry.getKey());
			
			List<Integer> li = new ArrayList<Integer>();
			for (int i=0; i<=entry.getValue() && i<=resources; i++) {
				li.add(new Integer(i));
			}
			
			lists.add(li);
		}
		
		// prepare events
		for (Map.Entry<XEventClass,Integer> entry : this.mapEvClass2Cost.entrySet()) {
			list.add(entry.getKey());
			
			List<Integer> li = new ArrayList<Integer>();
			for (int i=0; i<=entry.getValue() && i<=resources; i++) {
				li.add(new Integer(i));
			}
			
			lists.add(li);
		}
		
		this.iterations=0;
		this.optTrans2Cost		= new HashMap<Transition, Integer>(this.mapTrans2Cost);
		this.optEvClass2Cost	= new HashMap<XEventClass, Integer>(this.mapEvClass2Cost);
		
		int optCost = this.computeCost(this.mapTrans2Cost, this.mapEvClass2Cost);
		
		Map<Transition,Integer>	 t2c = null;
		Map<XEventClass,Integer> e2c = null;
		
		//System.err.println(lists);
		
		ListCombinationGenerator<Integer> lcg = new ListCombinationGenerator<>(lists);
		while (lcg.hasMoreCombinations()) {
			List<Integer> comb = lcg.getNextCombination();
			
			int sum = 0;
			for (Integer i : comb) sum+=i;
			
			if (sum!=resources) continue;
			//System.out.println(comb);
			
			this.iterations++;
			
			t2c = new HashMap<Transition, Integer>(this.mapTrans2Cost);
			e2c = new HashMap<XEventClass, Integer>(this.mapEvClass2Cost);
			
			for (int i=0; i<list.size(); i++) {
				Object obj = list.get(i);
				
				if (obj instanceof Transition) {
					t2c.put((Transition) obj,t2c.get((Transition) obj)-comb.get(i));
				}
				
				if (obj instanceof XEventClass) {
					e2c.put((XEventClass) obj,e2c.get((XEventClass) obj)-comb.get(i));
				}
			}
			
			int cost = this.computeCost(t2c,e2c);
			
			if (cost<optCost) {
				optCost = cost;
				
				this.optTrans2Cost		= new HashMap<Transition, Integer>(t2c);
				this.optEvClass2Cost	= new HashMap<XEventClass, Integer>(e2c);
			}
		}
			
		if (this.outputFlag) System.out.println("Transition costs: "+this.optTrans2Cost);
		if (this.outputFlag) System.out.println("Event costs: "+this.optEvClass2Cost);

		return optCost;
	}

	/*public int findOptimalCostFunctions(int resources) {
		List<Object> objs = new ArrayList<Object>();
		
		for (Transition t : this.mapTrans2Cost.keySet()) {
			for (int i=0; i<this.mapTrans2Cost.get(t);i++)
				objs.add(t);
		}
		
		for (XEventClass evClass : this.mapEvClass2Cost.keySet()) {
			for (int i=0; i<this.mapEvClass2Cost.get(evClass);i++)
				objs.add(evClass);
		}
		
		this.optTrans2Cost		= new HashMap<Transition, Integer>(this.mapTrans2Cost);
		this.optEvClass2Cost	= new HashMap<XEventClass, Integer>(this.mapEvClass2Cost);
		
		int optCost = this.computeCost(this.mapTrans2Cost, this.mapEvClass2Cost); 
		
		Map<Transition,Integer>	 t2c = null;
		Map<XEventClass,Integer> e2c = null;
		CombinationGenerator<Object> cg = new CombinationGenerator<Object>(objs,resources);
		
		this.iterations=0;
		while (cg.hasMore()) {
			this.iterations++;
			Collection<Object> res = cg.getNextCombination();
			t2c = new HashMap<Transition, Integer>(this.mapTrans2Cost);
			e2c = new HashMap<XEventClass, Integer>(this.mapEvClass2Cost);
			
			for (Object obj : res) {
				if (obj instanceof Transition) {
					t2c.put((Transition) obj,t2c.get((Transition) obj)-1);
				}
				
				if (obj instanceof XEventClass) {
					e2c.put((XEventClass) obj,e2c.get((XEventClass) obj)-1);
				}
			}
			
			int cost = this.computeCost(t2c,e2c);
			
			if (cost<optCost) {
				optCost = cost;
				
				this.optTrans2Cost		= new HashMap<Transition, Integer>(t2c);
				this.optEvClass2Cost	= new HashMap<XEventClass, Integer>(e2c);
			}
		}
				
		System.out.println("Transition costs: "+this.optTrans2Cost);
		System.out.println("Event costs: "+this.optEvClass2Cost);

		return optCost;
	}*/
}
