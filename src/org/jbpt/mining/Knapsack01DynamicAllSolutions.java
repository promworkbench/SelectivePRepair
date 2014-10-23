package org.jbpt.mining;

import java.util.Arrays;
import java.util.Scanner;

// TODO: This code is wrong 
public class Knapsack01DynamicAllSolutions {    
	private static int[] weight;
    private static int[] benefit;
    
    private static int[][] table;
    private static int capacityKnapsack;
    private static int nItems;

    Scanner scan = new Scanner(System.in);

    static int B(int items, int maxCapacity) {

        if (maxCapacity < 0 || items < 0) {
            return -2;
        }

        if (table[items][maxCapacity] != -1) {
            return table[items][maxCapacity];
        }

        int arg1 = B(items - 1, maxCapacity);
        int arg2 = B(items - 1, (maxCapacity - weight[items - 1]));
        if (arg2 == -2) {
            arg2 = 0;
        } else {
            arg2 += benefit[items - 1];
        }
        table[items][maxCapacity] = Math.max(arg1, arg2);
        return table[items][maxCapacity];
    }

    void printAllItems(int row, int col) {
        if (row == 0) { return; }
        
        while (table[row][col] == table[row - 1][col]) {
            int number = col - weight[row - 1];
            int ben = benefit[row - 1];

            if(number==0 && benefit[row-1] == table[row-1][col]){System.out.print("[ "+row+" ] ");}
            if (number <= 0) {
                ben = 0;
                number = 0;
            }
            if (table[row][col] == table[row - 1][number] + ben) {
                //include this item  
                System.out.print("[ ");
                System.out.print(row + " ");
                printAllItems(row - 1, col - weight[row - 1]);
                System.out.print("] ");
            }
            row--;
            if (row == 1) {
                break;
            }
        }

        if (table[row][col] != table[row - 1][col]) {
            System.out.print(row + " "); 

            if (col - weight[row - 1] > 0) {
                printAllItems(row - 1, col - weight[row - 1]); 
                
            }
        }
    }
    
    static String printAllItems2(int row, int col, String s) {
        if (row == 0) { return s; }
        
        while (table[row][col] == table[row - 1][col]) {
            int number = col - weight[row - 1];
            int ben = benefit[row - 1];

            if(number==0 && benefit[row-1] == table[row-1][col]){s+="[ "+row+" ] ";}
            if (number <= 0) {
                ben = 0;
                number = 0;
            }
            if (table[row][col] == table[row - 1][number] + ben) {
                //include this item  
                System.out.print("[ ");
                System.out.print(row + " ");
                printAllItems2(row - 1, col - weight[row - 1],s);
                System.out.print("] ");
            }
            row--;
            if (row == 1) {
                break;
            }
        }

        if (table[row][col] != table[row - 1][col]) {
            System.out.print(row + " "); 

            if (col - weight[row - 1] > 0) {
                printAllItems2(row - 1, col - weight[row - 1],s); 
                
            }
        }
        
        return s;
    }

    void input() {
        System.out.println("Enter the number of items: ");
        nItems = scan.nextInt();

        System.out.println("Enter the (weight and benefit)s of the n items: ");

        weight = new int[nItems];
        benefit = new int[nItems];

        for (int i = 0; i < nItems; ++i) {
            weight[i] = scan.nextInt();
            benefit[i] = scan.nextInt();
        }

        System.out.println("Max capacity of the knapsack: ");
        capacityKnapsack = scan.nextInt();
        table = new int[nItems + 1][capacityKnapsack + 1];

        for (int i = 1; i < nItems + 1; ++i) {
            Arrays.fill(table[i], -1);
        }
        for (int i = 0; i < nItems + 1; ++i) {
            table[i][0] = 0;
        }
    }

    void printTable() {
        System.out.println();
        System.out.print("   ");
        int count = 0;
        for (int i = 0; i < capacityKnapsack + 1; ++i) {
            System.out.printf("%3d ", i);
        }
        System.out.println("\n");
        for (int i = 0; i < nItems + 1; ++i) {
            System.out.printf("%-3d", count);
            for (int j = 0; j < capacityKnapsack + 1; ++j) {
                System.out.printf("%1$3d ", table[i][j]);
            }
            count++;
            System.out.println();
        }
        System.out.println();
    }
    
    public static String solve(int[] profits, int[] weights, int W) {
    	if (profits.length!=weights.length) return "";
    	
    	nItems = profits.length;
    	capacityKnapsack = W;
    	
    	table = new int[nItems + 1][capacityKnapsack + 1];

        for (int i = 1; i < nItems + 1; ++i) {
            Arrays.fill(table[i], -1);
        }
        for (int i = 0; i < nItems + 1; ++i) {
            table[i][0] = 0;
        }
        
        weight = weights;
        benefit = profits;
        
        B(nItems,capacityKnapsack);
        
        // construct results
        return printAllItems2(nItems, capacityKnapsack, "");
    	
    }

    public static void main(String[] args) {
    	int[] weights = new int[6];
    	int[] profits = new int[6];
    	weights[0] = 1;
    	weights[1] = 1;
    	weights[2] = 1;
    	weights[3] = 1;
    	weights[4] = 1;
    	weights[5] = 1;
    	
    	profits[0] = 1;
    	profits[1] = 1;
    	profits[2] = 1;
    	profits[3] = 1;
    	profits[4] = 1;
    	profits[5] = 1;
    	
    	System.out.println(Knapsack01DynamicAllSolutions.solve(profits, weights,2));
    	
        /*Knapsack01DynamicAllSolutions kd = new Knapsack01DynamicAllSolutions();
        kd.input();
        B(nItems, capacityKnapsack);
        kd.printTable(); 
        kd.printAllItems(nItems, capacityKnapsack);*/
    }
}