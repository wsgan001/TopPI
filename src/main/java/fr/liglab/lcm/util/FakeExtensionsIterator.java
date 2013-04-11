package fr.liglab.lcm.util;

import java.util.Calendar;

import fr.liglab.lcm.internals.ExtensionsIterator;
import gnu.trove.iterator.TIntIterator;

/**
 * FIXME : actually, ExtensionsIterator is useless !
 */
public class FakeExtensionsIterator implements ExtensionsIterator {

	private final int[] sortedFrequents;
	private final TIntIterator wrapped;

	public FakeExtensionsIterator(int[] sortedFrequents, TIntIterator wrapped) {
		this.sortedFrequents = sortedFrequents;
		this.wrapped = wrapped;
	}

	public int[] getSortedFrequents() {
		return this.sortedFrequents;
	}

	public synchronized int getExtension() {
		if (wrapped.hasNext()) {
			int next = wrapped.next();
			System.out.format("%1$tY/%1$tm/%1$td %1$tk:%1$tM:%1$tS - extending with %2$d\n", Calendar.getInstance(), next);
			return next;
		} else {
			return -1;
		}
	}

}
