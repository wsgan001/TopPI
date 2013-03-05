package fr.liglab.lcm;

import fr.liglab.lcm.internals.Dataset;
import fr.liglab.lcm.internals.ExtensionsIterator;
import fr.liglab.lcm.io.PatternsCollector;
import fr.liglab.lcm.util.ItemsetsFactory;
import gnu.trove.map.TIntIntMap;

/**
 * LCM implementation, based on UnoAUA04 :
 * "An Efficient Algorithm for Enumerating Closed Patterns in Transaction Databases"
 * by Takeaki Uno el. al.
 */
public class LCM {
	private final PatternsCollector collector;

	private int explored = 0;
	private int cut = 0;

	public LCM(PatternsCollector patternsCollector) {
		collector = patternsCollector;
	}

	/**
	 * Initial invocation
	 */
	public void lcm(final Dataset dataset) {
		// usually, it's empty
		int[] pattern = dataset.getDiscoveredClosureItems();

		if (pattern.length > 0) {
			collector.collect(dataset.getTransactionsCount(), pattern);
		}

		ExtensionsIterator iterator = dataset.getCandidatesIterator();
		int candidate;
		while ((candidate = iterator.getExtension()) != -1) {
			explored++;
			lcm(pattern, dataset, candidate);
		}
	}

	/**
	 * This function will output clo(pattern U {extension}), and (in recursive
	 * calls) all closed frequent closed itemsets prefixed by
	 * "pattern U {extension}"
	 * 
	 * HEAVY ASSUMPTIONS :
	 * 
	 * @param pattern
	 *            is a closed frequent itemset, freshly found out of :
	 * @param parent_dataset
	 *            - pattern's support
	 * @param extension
	 *            is an item known to yield a prefix-preserving extension of P
	 */
	public void lcm(final int[] pattern, final Dataset parent_dataset,
			int extension) {

		final Dataset dataset = parent_dataset.getProjection(extension);
		int[] Q = ItemsetsFactory.extend(pattern, extension,
				dataset.getDiscoveredClosureItems());

		collector.collect(dataset.getTransactionsCount(), Q);

		ExtensionsIterator iterator = dataset.getCandidatesIterator();
		int[] sortedFreqs = iterator.getSortedFrequents();
		TIntIntMap supportCounts = dataset.getSupportCounts();

		int candidate;
		int previousExplore = -1;
		int previousCandidate = -1;
		while ((candidate = iterator.getExtension()) != -1) {
			int explore = collector.explore(Q, candidate, sortedFreqs,
					supportCounts, previousCandidate, previousExplore);
			previousExplore = explore;
			previousCandidate = candidate;
			if (explore < 0) {
				explored++;
				lcm(Q, dataset, candidate);
			} else {
				cut++;
			}
		}
	}

	public String toString() {
		return "LCM exploration : " + explored + " patterns explored / " + cut
				+ " aborted ";
	}
}
