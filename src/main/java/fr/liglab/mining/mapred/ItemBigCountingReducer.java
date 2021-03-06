/*
	This file is part of TopPI - see https://github.com/slide-lig/TopPI/
	
	Copyright 2016 Martin Kirchgessner, Vincent Leroy, Alexandre Termier, Sihem Amer-Yahia, Marie-Christine Rousset, Université Grenoble Alpes, LIG, CNRS
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	 http://www.apache.org/licenses/LICENSE-2.0
	 
	or see the LICENSE.txt file joined with this program.
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/
package fr.liglab.mining.mapred;

import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Reducer;

public class ItemBigCountingReducer extends
		Reducer<IntWritable, IntWritable, IntWritable, IntWritable> {
	
	public static final String COUNTERS_GROUP = "ItemCounters";
	public static final String COUNTER_REBASING_MAX_ID = "rebasing-maxId";
	
	private int minSupport = 10;
	
	private final IntWritable valueW = new IntWritable();
	
	@Override
	protected void setup(Context context)
			throws IOException, InterruptedException {
		
		this.minSupport = context.getConfiguration().getInt(TopPIoverHadoop.KEY_MINSUP, 10);
	}
	
	@Override
	protected void reduce(IntWritable key, 
			Iterable<IntWritable> values, Context context)
			throws java.io.IOException, InterruptedException {
		
		Iterator<IntWritable> it = values.iterator();
		int count = 0;
		
		while (it.hasNext()) {
			count += it.next().get();
		}
		
		if (count >= this.minSupport) {
			valueW.set(count);
			
			context.write(key, valueW);
		}
	}
}
