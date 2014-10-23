package org.jbpt.mining;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jbpt.algo.CombinationGenerator;


public class Partition { 
	private List<List<Integer>>		sums = new ArrayList<List<Integer>>();
	private List<Integer>			sum = null;
	private Collection<Integer>		pos = null;
	private List<Integer>			opos = null;
	
	private int curPos = 0;
	private int size = 0;
	private Collection<Integer> numbers = new ArrayList<Integer>();
	
	private CombinationGenerator<Integer> comb = null;
	private Permutations<Integer>	perm = null;
	
	private boolean hasMore = true;
	
	public Partition(int s, int size) {
		// build sums
		this.partition(s);
		
		this.size = size;
		for (int i=0; i<size; i++) numbers.add(i);
		
		
		sum = new ArrayList<Integer>(sums.get(curPos));
		
		comb = new CombinationGenerator<Integer>(numbers,sum.size());
		
		pos = comb.getNextCombination();
		
		perm = new Permutations<Integer>(new ArrayList<Integer>(pos));
		
		opos = perm.next();
		
		System.out.println(sums);
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
    	List<Integer> result = new ArrayList<Integer>();
    	for (int i=0; i<this.size; i++)
    		result.add(0);
    	
    	for (int j=0; j<sum.size(); j++) {
    		result.set(opos.get(j), sum.get(j));
    	}
    	
    	if (perm.hasNext())
    		opos = perm.next();
    	else {
    		if (comb.hasMore()) {
    			pos = comb.getNextCombination();
        		perm = new Permutations<Integer>(new ArrayList<Integer>(pos));
        		opos = perm.next();
    		}
    		else {
    			curPos++;
    			if (curPos>=this.sums.size()) {
    				hasMore = false;
    				return result;
    			}
    			
    			sum = new ArrayList<Integer>(sums.get(curPos));						// sums up to available resources
    			comb = new CombinationGenerator<Integer>(numbers,sum.size());		
    			pos = comb.getNextCombination();									// positions to use to put parts of resources
        		perm = new Permutations<Integer>(new ArrayList<Integer>(pos));		
        		opos = perm.next();
    		}
    	}
		
		return result;
    }
    
    public boolean hasMoreCombinations() {
		return hasMore;
	}

}

