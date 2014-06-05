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
public class AlignmentCostOptimizerGoldratt extends AbstractAlignmentCostOptimizer {
	
	
	AlignmentCostOptimizerGoldratt(PetrinetGraph net,
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
		
		this.iterations=1;
		
		int res = resources;
		while (res>0) {
			int maxCost = 0;
			Object max = null;
			
			for (Map.Entry<Transition, Integer> entry : this.optTrans2Cost.entrySet()) {
				if (entry.getValue()>maxCost) {
					max = entry.getKey();
					maxCost = entry.getValue(); 
				}
			}
			
			for (Map.Entry<XEventClass, Integer> entry : this.optEvClass2Cost.entrySet()) {
				if (entry.getValue()>maxCost) {
					max = entry.getKey();
					maxCost = entry.getValue(); 
				}
			}
			
			if (res>=maxCost) {
				if (max instanceof Transition)
					this.optTrans2Cost.put((Transition) max, 0);
				else if (max instanceof XEventClass)
					this.optEvClass2Cost.put((XEventClass) max, 0);
			}
			else
			{
				if (max instanceof Transition)
					this.optTrans2Cost.put((Transition) max, maxCost-res);
				else if (max instanceof XEventClass)
					this.optEvClass2Cost.put((XEventClass) max, maxCost-res);
			}
			
			res-=maxCost;
			
		}
				
		System.out.println("Transition costs: "+this.optTrans2Cost);
		System.out.println("Event costs: "+this.optEvClass2Cost);
		
		
		return this.computeCost(this.optTrans2Cost,this.optEvClass2Cost);
	}
}
