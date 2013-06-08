package fr.liglab.lcm.io;

import java.io.IOException;

/**
 * A thread safe PatternsCollector that will write to multiple files, one per mining thread.
 */
public class MultiThreadedFileCollector implements PatternsCollector {
	
	private final FileCollector[] collectors;
	
	/**
	 * @param prefix
	 * 			filename prefix for pattern files, each thread will append [ThreadID].dat
	 * @param maxId
	 * 			higer bound on thread's getId()
	 * @throws IOException
	 */
	public MultiThreadedFileCollector(final String prefix, final int maxId) throws IOException {
		this.collectors = new FileCollector[maxId];
		for (int i = 0; i < maxId; i++) {
			this.collectors[i] = new FileCollector(prefix + i + ".dat");
		}
	}
	
	@Override
	public void collect(int support, int[] pattern) {
		this.collectors[(int) Thread.currentThread().getId()].collect(support, pattern);
	}

	@Override
	public long close() {
		long total = 0;
		
		for (FileCollector collector : this.collectors) {
			total += collector.close();
		}
		
		return total;
	}

	@Override
	public int getAveragePatternLength() {
		long totalLen = 0;
		long nbPatterns = 0;
		
		for (FileCollector collector : this.collectors) {
			totalLen += collector.getCollectedLength();
			nbPatterns += collector.getCollected();
		}
		
		return (int) (totalLen / nbPatterns);
	}

}
