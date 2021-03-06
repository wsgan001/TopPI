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
package fr.liglab.mining.internals;

import fr.liglab.mining.CountersHandler;
import fr.liglab.mining.CountersHandler.TopPICounters;
import fr.liglab.mining.internals.tidlist.TidList;
import gnu.trove.iterator.TIntIterator;

/**
 * A stateless Selector that may throw WrongFirstParentException
 * 
 * Allows to perform first-parent test BEFORE performing item counting for a
 * candidate extension
 */
public final class FirstParentTest extends Selector {

	public static final FirstParentTest tailInstance = new FirstParentTest();

	@Override
	protected TopPICounters getCountersKey() {
		return null; // YEP! this selector throws an exception so this method
						// should never be called
	}

	public FirstParentTest() {
		super();
	}

	FirstParentTest(Selector follower) {
		super(follower);
	}

	static Selector getTailInstance() {
		return tailInstance;
	}

	@Override
	protected Selector copy(Selector newNext) {
		if (newNext == null) {
			return tailInstance;
		} else {
			return new FirstParentTest(newNext);
		}
	}

	private boolean isAincludedInB(final TIntIterator aIt, final TIntIterator bIt) {
		int tidA = 0;
		int tidB = 0;

		while (aIt.hasNext() && bIt.hasNext()) {
			tidA = aIt.next();
			tidB = bIt.next();

			while (tidB < tidA && bIt.hasNext()) {
				tidB = bIt.next();
			}

			if (tidB > tidA) {
				return false;
			}
		}

		return tidA == tidB && !aIt.hasNext();
	}

	/**
	 * returns true or throws a WrongFirstParentException
	 */
	@Override
	protected boolean allowExploration(int extension, ExplorationStep state) throws WrongFirstParentException {

		if (state.dataset instanceof DatasetView) {
			throw new IllegalArgumentException("FPtest can only be done on Dataset");
		}

		final TidList occurrencesLists = state.dataset.tidLists;

		final int candidateSupport = state.counters.getSupportCount(extension);

		for (int i = state.counters.getMaxFrequent(); i > extension; i--) {
			if (state.counters.getSupportCount(i) >= candidateSupport) {
				TIntIterator candidateOccurrences = occurrencesLists.get(extension);
				final TIntIterator iOccurrences = occurrencesLists.get(i);
				if (isAincludedInB(candidateOccurrences, iOccurrences)) {
					CountersHandler.increment(TopPICounters.PreFPTestsRejections);
					throw new WrongFirstParentException(extension, i);
				}
			}
		}

		return true;
	}
}
