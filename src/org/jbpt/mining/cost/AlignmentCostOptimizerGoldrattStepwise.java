package org.jbpt.mining.cost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.model.XLog;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;


/**
 * @author Artem Polyvyanyy
 */
public class AlignmentCostOptimizerGoldrattStepwise extends AbstractAlignmentCostOptimizer {
	
	public AlignmentCostOptimizerGoldrattStepwise(PetrinetGraph net,
			Marking initMarking, Marking[] finalMarkings, XLog log,
			Map<Transition, Integer> mapTrans2Cost,
			Map<XEventClass, Integer> mapEvClass2Cost,
			TransEvClassMapping mapping) throws Exception {
		super(net, initMarking, finalMarkings, log, mapTrans2Cost, mapEvClass2Cost,mapping);
	}

	public int findOptimalCost(int resources) {
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
		
		int res = resources;
		while (res>0) {
			int minCostT = Integer.MAX_VALUE;
			HashMap<Transition,Integer> t2iMin = null;
			for (Map.Entry<Transition,Integer> entry : this.optTrans2Cost.entrySet()) {
				if (entry.getValue()>0) {
					HashMap<Transition,Integer> t2i = new HashMap<Transition, Integer>(this.optTrans2Cost);
					t2i.put(entry.getKey(), entry.getValue()-1);
					int cost = this.computeCost(t2i,this.optEvClass2Cost);
					this.iterations++;
					if (cost<minCostT) {
						minCostT = cost;
						t2iMin = new HashMap<Transition, Integer>(t2i);
					}
				}
			}
			
			int minCostE = Integer.MAX_VALUE;
			HashMap<XEventClass,Integer> e2iMin = null;
			for (Map.Entry<XEventClass,Integer> entry : this.optEvClass2Cost.entrySet()) {
				if (entry.getValue()>0) {
					HashMap<XEventClass,Integer> e2i = new HashMap<XEventClass, Integer>(this.optEvClass2Cost);
					e2i.put(entry.getKey(), entry.getValue()-1);
					int cost = this.computeCost(this.optTrans2Cost,e2i);
					this.iterations++;
					if (cost<minCostE) {
						minCostE = cost;
						e2iMin = new HashMap<XEventClass,Integer>(e2i);
					}
				}
			}
			
			if (minCostT<minCostE) {
				this.optTrans2Cost = new HashMap<Transition,Integer>(t2iMin);
			}
			else {
				this.optEvClass2Cost = new HashMap<XEventClass,Integer>(e2iMin);
			}
			
			res--;
		}
		
		System.out.println("Transition costs: "+this.optTrans2Cost);
		System.out.println("Event costs: "+this.optEvClass2Cost);
		
		return this.computeCost(this.optTrans2Cost,this.optEvClass2Cost);
	}
}
