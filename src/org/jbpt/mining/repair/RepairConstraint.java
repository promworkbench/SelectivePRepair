package org.jbpt.mining.repair;

import java.util.Map;

public class RepairConstraint {
	private Map<String,Integer> skipCosts = null;
	private Map<String,Integer> insertCosts = null;
	private int resources = 0;
	
	public RepairConstraint(Map<String,Integer> insertCosts, Map<String,Integer> skipCosts, int resources) {
		this.insertCosts = insertCosts;
		this.skipCosts = skipCosts;
		this.resources = resources;
	}
	
	public Map<String,Integer> getSkipCosts() {
		return skipCosts;
	}

	public Map<String,Integer> getInsertCosts() {
		return insertCosts;
	}

	public int getAvailableResources() {
		return resources;
	}
}
