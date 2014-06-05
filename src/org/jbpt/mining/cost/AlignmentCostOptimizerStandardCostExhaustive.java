package org.jbpt.mining.cost;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.model.XLog;
import org.jbpt.algo.CombinationGenerator;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;


/**
 * @author Artem Polyvyanyy
 */
public class AlignmentCostOptimizerStandardCostExhaustive extends AbstractAlignmentCostOptimizer {
	
	
	AlignmentCostOptimizerStandardCostExhaustive(PetrinetGraph net,
			Marking initMarking, Marking[] finalMarkings, XLog log,
			Map<Transition, Integer> mapTrans2Cost,
			Map<XEventClass, Integer> mapEvClass2Cost,
			TransEvClassMapping mapping) throws Exception {
		super(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost,mapping);
	}

	public int findOptimalCost(int resources) {
		List<Object> objs = new ArrayList<Object>();
		for (Transition t : this.mapTrans2Cost.keySet())
			objs.add(t);
		for (XEventClass evClass : this.mapEvClass2Cost.keySet())
			objs.add(evClass);
		
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
					t2c.put((Transition) obj,0);
				}
				
				if (obj instanceof XEventClass) {
					e2c.put((XEventClass) obj,0);
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
	}
}
