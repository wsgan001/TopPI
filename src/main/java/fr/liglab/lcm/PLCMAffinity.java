package fr.liglab.lcm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import fr.liglab.lcm.internals.nomaps.ExplorationStep;
import fr.liglab.lcm.internals.nomaps.FrequentsIteratorRenamer;
import fr.liglab.lcm.io.FileCollector;
import fr.liglab.lcm.io.NullCollector;
import fr.liglab.lcm.io.PatternSortCollector;
import fr.liglab.lcm.io.PatternsCollector;
import fr.liglab.lcm.io.PerItemTopKCollector;
import fr.liglab.lcm.io.StdOutCollector;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

/**
 * LCM implementation, based on UnoAUA04 :
 * "An Efficient Algorithm for Enumerating Closed Patterns in Transaction Databases"
 * by Takeaki Uno el. al.
 */
public class PLCMAffinity {
	private final List<PLCMThread> threads;

	private final PatternsCollector collector;

	private final long[] globalCounters;

	public PLCMAffinity(PatternsCollector patternsCollector) {
		this(patternsCollector, Runtime.getRuntime().availableProcessors());
	}

	public PLCMAffinity(PatternsCollector patternsCollector, int nbThreads) {
		if (nbThreads < 1) {
			throw new IllegalArgumentException("nbThreads has to be > 0, given " + nbThreads);
		}
		this.collector = patternsCollector;
		this.threads = new ArrayList<PLCMThread>(nbThreads);
		for (int i = 0; i < nbThreads; i++) {
			TIntList l = new TIntArrayList(1);
			l.add(i);
			this.threads.add(new PLCMThread(l));
		}

		this.globalCounters = new long[PLCMCounters.values().length];
	}

	public final void collect(int support, int[] pattern) {
		this.collector.collect(support, pattern);
	}

	/**
	 * Initial invocation
	 */
	public void lcm(final ExplorationStep initState) {
		if (initState.pattern.length > 0) {
			collector.collect(initState.counters.transactionsCount, initState.pattern);
		}

		this.threads.get(0).init(initState);
		for (PLCMThread t : this.threads) {
			t.start();
		}

		for (PLCMThread t : this.threads) {
			try {
				t.join();

				synchronized (this.globalCounters) {
					for (int i = 0; i < t.counters.length; i++) {
						this.globalCounters[i] += t.counters[i].get();
					}

				}
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public String toString() {
		StringBuilder builder = new StringBuilder();

		builder.append("PLCM exploration (");
		builder.append(this.threads.size());
		builder.append(" threads)");

		PLCMCounters[] counters = PLCMCounters.values();

		for (int i = 0; i < this.globalCounters.length; i++) {
			PLCMCounters counter = counters[i];

			builder.append(", ");
			builder.append(counter.toString());
			builder.append(':');
			builder.append(this.globalCounters[i]);
		}

		return builder.toString();
	}

	public ExplorationStep stealJob(final PLCMThread thief) {
		// here we need to readlock because the owner thread can write
		for (int level = 0; level < thief.id.size(); level++) {
			for (PLCMThread t : this.threads) {
				if (t == thief) {
					break;
				}
				for (int higherLevel = level + 1; higherLevel < thief.id.size(); higherLevel++) {
					if (thief.id.get(higherLevel) != t.id.get(higherLevel)) {
						break;
					}
				}
				for (int stealPos = 0; stealPos < t.stackedJobs.size(); stealPos++) {
					t.lock.readLock().lock();
					if (!t.stackedJobs.isEmpty()) {
						ExplorationStep sj = t.stackedJobs.get(0);
						t.lock.readLock().unlock();

						ExplorationStep next = sj.next();

						if (next != null) {
							return next;
						}
					} else {
						t.lock.readLock().unlock();
					}
				}
			}
		}
		return null;
	}

	/**
	 * Some classes in EnumerationStep may declare counters here. see references
	 * to PLCMThread.counters
	 */
	public enum PLCMCounters {
		ExplorationStepInstances, ExplorationStepCatchedWrongFirstParents, FirstParentTestRejections, TopKRejections,
	}

	public class PLCMThread extends Thread {
		public final AtomicLong[] counters;
		private final ReadWriteLock lock;
		private final List<ExplorationStep> stackedJobs;
		private final TIntList id;

		public PLCMThread(final TIntList id) {
			super("PLCMThread" + id);
			this.stackedJobs = new ArrayList<ExplorationStep>();
			this.id = id;
			this.lock = new ReentrantReadWriteLock();

			this.counters = new AtomicLong[PLCMCounters.values().length];
			for (int i = 0; i < this.counters.length; i++) {
				this.counters[i] = new AtomicLong();
			}
		}

		private void init(ExplorationStep initState) {
			this.lock.writeLock().lock();
			this.stackedJobs.add(initState);
			this.lock.writeLock().unlock();
		}

		@Override
		public long getId() {
			long id = 0;
			TIntIterator iter = this.id.iterator();
			while (iter.hasNext()) {
				id *= 10;
				id += iter.next();
			}
			return id;
		}

		@Override
		public void run() {
			// no need to readlock, this thread is the only one that can do
			// writes
			boolean exit = false;
			while (!exit) {
				ExplorationStep sj = null;
				if (!this.stackedJobs.isEmpty()) {
					sj = this.stackedJobs.get(this.stackedJobs.size() - 1);

					ExplorationStep extended = sj.next();
					// iterator is finished, remove it from the stack
					if (extended == null) {
						this.lock.writeLock().lock();

						this.stackedJobs.remove(this.stackedJobs.size() - 1);
						this.counters[PLCMCounters.ExplorationStepInstances.ordinal()].incrementAndGet();
						this.counters[PLCMCounters.ExplorationStepCatchedWrongFirstParents.ordinal()].addAndGet(sj
								.getCatchedWrongFirstParentCount());

						this.lock.writeLock().unlock();
					} else {
						this.lcm(extended);
					}

				} else { // our list was empty, we should steal from another
							// thread
					ExplorationStep stolj = stealJob(this);
					if (stolj == null) {
						exit = true;
					} else {
						lcm(stolj);
					}
				}
			}
		}

		private void lcm(ExplorationStep state) {
			collect(state.counters.transactionsCount, state.pattern);

			this.lock.writeLock().lock();
			this.stackedJobs.add(state);
			this.lock.writeLock().unlock();
		}
	}

	public static void main(String[] args) throws InterruptedException {
		Options options = new Options();
		CommandLineParser parser = new PosixParser();

		options.addOption("h", false, "Show help");
		options.addOption(
				"b",
				false,
				"(only for standalone) Benchmark mode : show mining time and drop patterns to oblivion (in which case OUTPUT_PATH is ignored)");
		options.addOption("k", true, "Run in top-k-per-item mode");
		options.addOption("t", true, "How many threads will be launched (defaults to your machine's processors count)");

		try {
			CommandLine cmd = parser.parse(options, args);

			if (cmd.getArgs().length < 2 || cmd.getArgs().length > 3) {
				printMan(options);
			} else {
				standalone(cmd);
			}
		} catch (ParseException e) {
			printMan(options);
		}
	}

	public static void printMan(Options options) {
		String syntax = "java fr.liglab.LCM [OPTIONS] INPUT_PATH MINSUP [OUTPUT_PATH]";
		String header = "\nIf OUTPUT_PATH is missing, patterns are printed to standard output.\nOptions are :";
		String footer = "\nBy martin.kirchgessner@imag.fr";

		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(80, syntax, header, options, footer);
	}

	public static void standalone(CommandLine cmd) {
		String[] args = cmd.getArgs();
		int minsup = Integer.parseInt(args[1]);

		long time = System.currentTimeMillis();
		ExplorationStep initState = new ExplorationStep(minsup, args[0]);
		time = System.currentTimeMillis() - time;
		System.err.println("Dataset loaded in " + time + "ms");

		String outputPath = null;
		if (args.length >= 3) {
			outputPath = args[2];
		}

		PatternsCollector collector = instanciateCollector(cmd, outputPath, initState);

		PLCMAffinity miner;
		if (cmd.hasOption('t')) {
			int nbThreads = Integer.parseInt(cmd.getOptionValue('t'));
			miner = new PLCMAffinity(collector, nbThreads);
		} else {
			miner = new PLCMAffinity(collector);
		}

		time = System.currentTimeMillis();
		miner.lcm(initState);
		time = System.currentTimeMillis() - time;

		long outputted = collector.close();

		System.err.println(miner.toString() + " // mined in " + time + "ms // outputted " + outputted + " patterns");
	}

	/**
	 * Parse command-line arguments to instanciate the right collector
	 */
	private static PatternsCollector instanciateCollector(CommandLine cmd, String outputPath, ExplorationStep initState) {

		PatternsCollector collector = null;

		if (cmd.hasOption('b')) { // BENCHMARK MODE !
			collector = new NullCollector();
		} else {
			if (outputPath != null) {
				try {
					collector = new FileCollector(outputPath);
				} catch (IOException e) {
					e.printStackTrace(System.err);
					System.err.println("Aborting mining.");
					System.exit(1);
				}
			} else {
				collector = new StdOutCollector();
			}
			collector = new PatternSortCollector(collector);
		}

		if (cmd.hasOption('k')) {
			int k = Integer.parseInt(cmd.getOptionValue('k'));

			FrequentsIteratorRenamer extensions = new FrequentsIteratorRenamer(
					initState.counters.getExtensionsIterator(), initState.counters.getReverseRenaming());

			PerItemTopKCollector topKcoll = new PerItemTopKCollector(collector, k, initState.counters.nbFrequents,
					extensions);

			initState.appendSelector(topKcoll.asSelector());
			collector = topKcoll;
		}

		return collector;
	}
}