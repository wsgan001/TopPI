package fr.liglab.lcm.io;

import java.util.Arrays;

/**
 * a PatternsCollector decorator : it will transmit everything, except that all
 * patterns are re-instanciated and translated according to the RebasedDataset
 * provided at instanciation.
 */
public class PatternSortCollector extends PatternsCollector {

	protected final PatternsCollector decorated;

	public PatternSortCollector(PatternsCollector wrapped) {
		this.decorated = wrapped;
	}

	public void collect(final int support, final int[] pattern) {
		int[] sorted = Arrays.copyOf(pattern, pattern.length);
		Arrays.sort(sorted);
		this.decorated.collect(support, sorted);
	}

	public void close() {
		this.decorated.close();
	}

}
