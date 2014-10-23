package org.jbpt.mining;

import java.util.ArrayList;
import java.util.List;

public final class Permutation {
    
    List<Integer> numbers = null;
    List<List<Integer>> permutations = null;
    
    public Permutation(List<Integer> numbers) {
    	this.numbers = numbers;
    	this.permutations = permutation(this.numbers);
    }

    public static List<List<Integer>> permutation(List<Integer> numbers) {
        final List<List<Integer>> numPermutations = new ArrayList<List<Integer>>();
        permute(numbers, 0, numPermutations);
        return numPermutations;
    }

    private static void permute(List<Integer> numbers, int currIndex, List<List<Integer>> numPermutations) {
        if (currIndex == numbers.size() - 1) {
            numPermutations.add(numbers);
            return;
        }

        // prints the string without permuting characters from currIndex onwards.
        permute(numbers, currIndex + 1, numPermutations);

        // prints the strings on permuting the characters from currIndex onwards.
        for (int i = currIndex + 1; i < numbers.size(); i++) {
            if (numbers.get(currIndex) == numbers.get(i)) continue;
            numbers = swap(numbers, currIndex, i);
            permute(numbers, currIndex + 1, numPermutations);
        }
    }

    private static List<Integer> swap(List<Integer> numbers, int i, int j) {
    	List<Integer> result = new ArrayList<Integer>(numbers);
    	
    	Integer tmp = result.get(i);
    	result.set(i,result.get(j));
    	result.set(j,tmp);
    	
    	return result;
    }
    
    public List<List<Integer>> getPermutations() {
    	return this.permutations;
    }

    /*public static void main(String[] args) {
    	List<Integer> list = new ArrayList<Integer>();
    	list.add(1);
    	list.add(2);
    	list.add(3);
    	
        for (List<Integer> str : permutation(list)) {
            System.out.println(str);
        }

        System.out.println("------------");
        
        list.clear();
    	list.add(1);
    	list.add(1);
    	list.add(2);
    	list.add(2);
    	
    	for (List<Integer> str : permutation(list)) {
            System.out.println(str);
        }
    }*/
}