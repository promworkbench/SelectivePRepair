package org.jbpt.mining.repair;

import java.util.HashSet;
import java.util.Set;

public class RepairRecommendation {
	protected Set<String> skipLabels = null;
	protected Set<String> insertLabels = null;
	
	public RepairRecommendation() {
		this.skipLabels = new HashSet<String>();
		this.insertLabels = new HashSet<String>();
	}
	
	public RepairRecommendation(Set<String> insertLabels, Set<String> skipLabels) {
		this.skipLabels = skipLabels;
		this.insertLabels = insertLabels;
	}
	
	public Set<String> getInsertLabels() {
		return insertLabels;
	}
	
	public Set<String> getSkipLabels() {
		return skipLabels;
	}
	
	@Override
	public RepairRecommendation clone() {
		return new RepairRecommendation(new HashSet<String>(this.insertLabels),new HashSet<String>(this.skipLabels));
	}
	
	@Override
	public String toString() {
		return String.format("[%s, %s]", this.insertLabels, this.skipLabels);
	}
	
	@Override
	public int hashCode() {
		return 11 * this.skipLabels.hashCode() + 17 * this.insertLabels.hashCode();
	}
	
	public boolean contains(RepairRecommendation r) {
		return this.insertLabels.containsAll(r.insertLabels) && this.skipLabels.containsAll(r.skipLabels) ? true : false;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof RepairRecommendation)) return false;
		RepairRecommendation rec = (RepairRecommendation) obj;
		if (!rec.insertLabels.equals(this.insertLabels)) return false;
		if (!rec.skipLabels.equals(this.skipLabels)) return false;
		
		return true;
	}
}
