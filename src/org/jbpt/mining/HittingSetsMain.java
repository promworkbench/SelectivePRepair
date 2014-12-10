package org.jbpt.mining;

import java.util.Vector;

public class HittingSetsMain {

	public static void main(String[] args) {
		Vector v = new Vector<>();
		Vector a = new Vector<>();
		a.add(1);
		a.add(2);
		Vector b = new Vector<>();
		b.add(2);
		b.add(3);
		Vector c = new Vector<>();
		c.add(3);
		c.add(4);
		//c.add(2);
		
		v.add(a);
		v.add(b);
		v.add(c);
		HittingSets hs = new HittingSets(v);
		System.out.println(hs.getSets());

	}

}
