package org.jbpt.mining;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class PartitionWithDuplicates { 
	private List<List<Integer>>		sums = new ArrayList<List<Integer>>();
	private List<Integer>			sum = null;
	
	private int curPos = 0;
	private int size = 0;
	
	Permutation perm = null;
	
	private boolean hasMore = true;
	Iterator<List<Integer>> iter = null;
	
	public PartitionWithDuplicates(int s, int size) {
		// build sums
		this.partition(s);
		
		this.size = size;
			
		sum = new ArrayList<Integer>(sums.get(curPos));
		while (sum.size() > size) {
			curPos++;
			sum = new ArrayList<Integer>(sums.get(curPos));
		}
		
		while (sum.size()<size)
			sum.add(0);
		
		perm = new Permutation(sum);
		iter = perm.getPermutations().iterator();
		
		//System.out.println(sums);
	}

    public void partition(int n) {
        partition(n, n, new ArrayList<Integer>());
    }
    
    public void partition(int n, int max, List<Integer> prefix) {
        if (n == 0) {
        	sums.add(prefix);
            return;
        }

        for (int i = Math.min(max, n); i >= 1; i--) {
        	List<Integer> pref = new ArrayList<Integer>(prefix);
        	pref.add(i);
            partition(n-i, i, pref);
        }
    }
    
    public List<Integer> getNextCombination() {
    	List<Integer> result = iter.next();
    	
    	if (!iter.hasNext()) {
    		curPos++;
    		if (curPos>=sums.size()) {
    			hasMore = false;
    			return result;
    		}
    		
    		sum = new ArrayList<Integer>(sums.get(curPos));
    		while (sum.size() > this.size) {
    			curPos++;
    			if (curPos>=sums.size()) {
        			hasMore = false;
        			return result;
        		}
    			sum = new ArrayList<Integer>(sums.get(curPos));
    		}
    		
    		while (sum.size()<size)
    			sum.add(0);
    		
    		perm = new Permutation(sum);
    		iter = perm.getPermutations().iterator();
    		hasMore = iter.hasNext();
    	}
    	
    	return result;
    }
    
    public boolean hasMoreCombinations() {
		return hasMore;
	}

}

