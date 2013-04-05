package fr.liglab.lcm.mapred;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.CounterGroup;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;

import fr.liglab.lcm.mapred.writables.ItemAndSupportWritable;
import fr.liglab.lcm.mapred.writables.TransactionWritable;

/**
 * Driver methods for our 3 map-reduce jobs
 */
public class Driver {
	//////////////////// MANDATORY CONFIGURATION PROPERTIES ////////////////////
	
	public static final String KEY_INPUT    = "lcm.input";
	public static final String KEY_OUTPUT   = "lcm.output";
	public static final String KEY_MINSUP   = "lcm.minsup";
	public static final String KEY_NBGROUPS = "lcm.nbGroups";
	public static final String KEY_DO_TOP_K = "lcm.topK";
	
	//////////////////// OPTIONAL CONFIGURATION PROPERTIES ////////////////////
	
	public static final String KEY_GROUPER_CLASS = "lcm.grouper";
	public static final String KEY_MINING_ALGO = "lcm.mapred.algo";
	public static final String KEY_SINGLE_GROUP_ID = "lcm.single-group";
	public static final String KEY_DUMP_ON_HEAP_EXN = "lcm.dump-path";
	
	
	//////////////////// INTERNAL CONFIGURATION PROPERTIES ////////////////////
	
	/**
	 * property key for item-counter-n-grouper output path
	 */
	static final String KEY_GROUPS_MAP = "lcm.groupsMap";
	
	/**
	 * property key for mining output path
	 */
	static final String KEY_RAW_PATTERNS = "lcm.rawpatterns";
	
	/**
	 * property key for aggregated patterns' output path
	 */
	static final String KEY_AGGREGATED_PATTERNS = "lcm.aggregated";
	
	/**
	 * this property will be filled after item counting
	 */
	public static final String KEY_REBASING_MAX_ID = "lcm.items.maxId";
	
	
	protected final Configuration conf;
	protected final String input;
	protected final String output;
	
	/**
	 * All public KEY_* are expected in the provided configuration
	 * (except KEY_DO_TOP_K : if it's not set, all patterns will be mined)
	 */
	public Driver(Configuration configuration) {
		this.conf = configuration;
		
		this.input = this.conf.get(KEY_INPUT);
		this.output = this.conf.get(KEY_OUTPUT);
		
		this.conf.setStrings(KEY_GROUPS_MAP, this.output + "/" + DistCache.REBASINGMAP_DIRNAME);
		this.conf.setStrings(KEY_RAW_PATTERNS, this.output + "/" + "rawMinedPatterns");
		this.conf.setStrings(KEY_AGGREGATED_PATTERNS, output + "/" + "topPatterns");
	}
	
	@Override
	public String toString() {
		int g = this.conf.getInt(KEY_NBGROUPS, -1);
		int k = this.conf.getInt(KEY_DO_TOP_K, -1);
		int minSupport = this.conf.getInt(KEY_MINSUP, -1);
		
		StringBuilder builder = new StringBuilder();
		
		builder.append("Here is LCM-over-Hadoop driver, finding ");
		
		if (k > 0) {
			builder.append("top-");
			builder.append(k);
			builder.append("-per-item ");
		}
		
		builder.append("itemsets (supported by at least ");
		builder.append(minSupport);
		builder.append(" transactions) from ");
		builder.append(this.input);
		builder.append(" (splitted in ");
		builder.append(g);
		builder.append(" groups), outputting them to ");
		builder.append(this.output);
		
		return builder.toString();
	}
	
	public int run() throws Exception {
		System.out.println(toString());
		
		if (genItemMapToCache()) {
			
			String miningAlgo = this.conf.get(KEY_MINING_ALGO, "");
			
			if ("1".equals(miningAlgo)) {
				if (miningJob(false) && aggregateTopK()) {
					return 0;
				}
			} else { // algo "2"
				if (miningJob(true)) {
					return 0;
				}
			}
		}
		
		return 1;
	}
	
	protected boolean genItemMapToCache() 
			throws IOException, InterruptedException, ClassNotFoundException {
		
		String output = this.conf.getStrings(KEY_GROUPS_MAP)[0];
		
		Job job = new Job(conf, 
				"Computing frequent items mapping to groups, from "+this.input);
		
		job.setJarByClass(this.getClass());
		
		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		job.setOutputKeyClass(IntWritable.class);
		job.setOutputValueClass(IntWritable.class);
		
		FileInputFormat.addInputPath(job, new Path(this.input) );
		FileOutputFormat.setOutputPath(job, new Path(output));
		
		job.setMapperClass(ItemCountingMapper.class);
		job.setMapOutputKeyClass(NullWritable.class);
		job.setMapOutputValueClass(ItemAndSupportWritable.class);
		
		job.setReducerClass(ItemCountingReducer.class);
		job.setNumReduceTasks(1);
		
		boolean success = job.waitForCompletion(true);
		
		if (success) {
			DistCache.copyToCache(this.conf, output);
			CounterGroup counters = job.getCounters().getGroup(ItemCountingReducer.COUNTERS_GROUP);
			Counter rebasingMaxID = counters.findCounter(ItemCountingReducer.COUNTER_REBASING_MAX_ID);
			
			this.conf.setInt(KEY_REBASING_MAX_ID, (int) rebasingMaxID.getValue());
		}
		
		return success;
	}
	
	protected boolean miningJob(boolean useGroupOnly) 
			throws IOException, InterruptedException, ClassNotFoundException {
		
		Job job = new Job(conf, "Mining frequent itemsets from "+this.input);
		
		job.setJarByClass(this.getClass());
		
		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		job.setOutputKeyClass(ItemAndSupportWritable.class);
		job.setOutputValueClass(TransactionWritable.class);
		
		FileInputFormat.addInputPath(job, new Path(this.input) );
		
		job.setMapperClass(MiningMapper.class);
		job.setMapOutputKeyClass(IntWritable.class);
		job.setMapOutputValueClass(TransactionWritable.class);
		
		String outputPath;
		if (useGroupOnly) {
			outputPath = this.conf.getStrings(KEY_AGGREGATED_PATTERNS)[0];
			job.setReducerClass(MiningGroupOnlyReducer.class);
		} else {
			outputPath = this.conf.getStrings(KEY_RAW_PATTERNS)[0];
			job.setReducerClass(MiningReducer.class);
		}
		
		FileOutputFormat.setOutputPath(job, new Path(outputPath));
		
		return job.waitForCompletion(true);
	}
	
	protected boolean aggregateTopK() 
			throws IOException, InterruptedException, ClassNotFoundException {
		
		String input  = this.conf.getStrings(KEY_RAW_PATTERNS)[0];
		String output = this.conf.getStrings(KEY_AGGREGATED_PATTERNS)[0];
		
		Job job = new Job(conf, "Aggregating top-K frequent itemsets from "+this.input);
		
		job.setJarByClass(this.getClass());
		
		job.setInputFormatClass(SequenceFileInputFormat.class);
		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		job.setOutputKeyClass(ItemAndSupportWritable.class);
		job.setOutputValueClass(TransactionWritable.class);
		
		FileInputFormat.addInputPath(job, new Path(input) );
		FileOutputFormat.setOutputPath(job, new Path(output));

		job.setSortComparatorClass(ItemAndSupportWritable.SortComparator.class);
		job.setGroupingComparatorClass(ItemAndSupportWritable.ItemOnlyComparator.class);
		job.setPartitionerClass(AggregationPartitioner.class);
		job.setReducerClass(AggregationReducer.class);
		
		return job.waitForCompletion(true);
	}
}
