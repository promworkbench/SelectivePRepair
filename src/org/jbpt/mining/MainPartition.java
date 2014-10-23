package org.jbpt.mining;

public class MainPartition {

	public static void main(String[] args) {
		PartitionWithDuplicates part = new PartitionWithDuplicates(4,5);
		
		
		while (part.hasMoreCombinations()) {
			System.out.println(part.getNextCombination());
		}
	}

}
