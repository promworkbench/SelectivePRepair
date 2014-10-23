package org.jbpt.mining.cost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.model.XLog;
import org.jbpt.mining.PartitionWithDuplicates;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;


/**
 * @author Artem Polyvyanyy
 */
public class AlignmentCostOptimizerExhaustiveEfficient extends AbstractAlignmentCostOptimizer {
	
		public AlignmentCostOptimizerExhaustiveEfficient(PetrinetGraph net,
			Marking initMarking, Marking[] finalMarkings, XLog log,
			Map<Transition, Integer> mapTrans2Cost,
			Map<XEventClass, Integer> mapEvClass2Cost,
			TransEvClassMapping mapping, boolean outputFlag) throws Exception {
		super(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost,mapping, outputFlag);
	}

	@Override
	public int findOptimalCost(int resources) {
		this.iterations=0;
		this.optTrans2Cost		= new HashMap<Transition, Integer>(this.mapTrans2Cost);
		this.optEvClass2Cost	= new HashMap<XEventClass, Integer>(this.mapEvClass2Cost);
		
		int optCost = this.computeCost(this.mapTrans2Cost, this.mapEvClass2Cost);
		
		Map<Transition,Integer>	 t2c = null;
		Map<XEventClass,Integer> e2c = null;
		
		
		List<Object> list = new ArrayList<Object>();
		
		// prepare transitions
		for (Map.Entry<Transition,Integer> entry : this.mapTrans2Cost.entrySet()) {
			list.add(entry.getKey());
		}

		// prepare events
		for (Map.Entry<XEventClass,Integer> entry : this.mapEvClass2Cost.entrySet()) {
			list.add(entry.getKey());
		}
		
		PartitionWithDuplicates part = new PartitionWithDuplicates(resources,list.size()); 
		
		this.iterations = 0;
		while (part.hasMoreCombinations()) {
			List<Integer> decrease = part.getNextCombination();
			
			boolean flag = true;
			for (int i=0; i<list.size(); i++) {
				if (i<this.mapTrans2Cost.size()) {
					Transition t = (Transition) list.get(i);
					if (this.mapTrans2Cost.get(t)<decrease.get(i)) {
						flag = false;
						break;
					}
				}
				else {
					XEventClass e = (XEventClass) list.get(i);
					if (this.mapEvClass2Cost.get(e)<decrease.get(i)) {
						flag = false;
						break;
					}
				}
			}
			
			
			if (!flag) continue;
			
			this.iterations++;
			
			t2c = new HashMap<Transition, Integer>(this.mapTrans2Cost);
			e2c = new HashMap<XEventClass, Integer>(this.mapEvClass2Cost);
			
			for (int i=0; i<list.size(); i++) {
				if (i<this.mapTrans2Cost.size()) {
					Transition t = (Transition) list.get(i);
					t2c.put(t,t2c.get(t)-decrease.get(i));
				}
				else {
					XEventClass e = (XEventClass) list.get(i);
					e2c.put(e,e2c.get(e)-decrease.get(i));
				}
			}
			
			int cost = this.computeCost(t2c,e2c);
			
			if (cost<optCost) {
				optCost = cost;
				
				this.optTrans2Cost		= new HashMap<Transition,Integer>(t2c);
				this.optEvClass2Cost	= new HashMap<XEventClass,Integer>(e2c);
			}
		}
			
		if (this.outputFlag) System.out.println("Transition costs: "+this.optTrans2Cost);
		if (this.outputFlag) System.out.println("Event costs: "+this.optEvClass2Cost);

		return optCost;
	}
}
