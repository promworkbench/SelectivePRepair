package org.jbpt.mining.repair.exp;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Experiment {
	
	private static String outFile = "repairOut.txt";
	private static String errFile = "repairErr.txt";

	public static void main(String[] args) throws FileNotFoundException, ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		System.setOut(outputFile(Experiment.outFile));
		System.setErr(outputFile(Experiment.errFile));
		Class app = Class.forName("org.jbpt.mining.repair.main.RepairRecommendationSearchExpCSVNew");
		
		Method main = app.getDeclaredMethod("main", new Class[] { (new String[1]).getClass()});
		String[] appArgs = new String[1];
		//System.arraycopy(args, 0, appArgs, 0, args.length);
		main.invoke(null, appArgs);

	}
	
	protected static PrintStream outputFile(String name) throws FileNotFoundException {
		return new PrintStream(new BufferedOutputStream(new FileOutputStream(name)),true);
	}

}
