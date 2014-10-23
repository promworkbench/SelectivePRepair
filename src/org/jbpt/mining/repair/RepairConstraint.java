package org.jbpt.mining.repair;

import java.util.Map;

public class RepairConstraint {
	private Map<String,Double> skipCosts = null;
	private Map<String,Double> insertCosts = null;
	private double resources = 0.0;
	
	public RepairConstraint(Map<String,Double> insertCosts, Map<String,Double> skipCosts, double resources) {
		this.insertCosts = insertCosts;
		this.skipCosts = skipCosts;
		this.resources = resources;
	}
	
	public Map<String, Double> getSkipCosts() {
		return skipCosts;
	}

	public Map<String, Double> getInsertCosts() {
		return insertCosts;
	}

	public double getAvailableResources() {
		return resources;
	}
}
