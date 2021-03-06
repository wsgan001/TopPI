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

import java.util.Iterator;

import fr.liglab.mining.CountersHandler;
import fr.liglab.mining.CountersHandler.TopPICounters;
import fr.liglab.mining.internals.tidlist.IntConsecutiveItemsConcatenatedTidList;
import fr.liglab.mining.internals.tidlist.TidList;
import fr.liglab.mining.internals.tidlist.TidList.TIntIterable;
import fr.liglab.mining.internals.tidlist.UShortConsecutiveItemsConcatenatedTidList;
import fr.liglab.mining.internals.transactions.IntIndexedTransactionsList;
import fr.liglab.mining.internals.transactions.ReusableTransactionIterator;
import fr.liglab.mining.internals.transactions.TransactionsList;
import fr.liglab.mining.internals.transactions.TransactionsWriter;
import fr.liglab.mining.internals.transactions.UShortIndexedTransactionsList;
import gnu.trove.iterator.TIntIterator;

/**
 * Stores transactions and does occurrence delivery
 */
public class Dataset implements Cloneable {

	protected final TransactionsList transactions;

	/**
	 * frequent item => array of occurrences indexes in "concatenated"
	 * Transactions are added in the same order in all occurrences-arrays.
	 */
	protected final TidList tidLists;

	private final int minSup;
	
	/**
	 * inclusive higher bound
	 */
	private final int maxItem;

	protected Dataset(TransactionsList transactions, TidList occurrences, int minSup, int maxItem) {
		// DO NOT COUNT NbDatasets here
		this.transactions = transactions;
		this.tidLists = occurrences;
		this.minSup = minSup;
		this.maxItem = maxItem;
	}

	@Override
	protected Dataset clone() {
		return new Dataset(this.transactions.clone(), this.tidLists.clone(), this.minSup, this.maxItem);
	}

	Dataset(Counters counters, final Iterator<TransactionReader> transactions, int minSup, int maxItem) {
		this(counters, transactions, Integer.MAX_VALUE, minSup, maxItem);
	}

	/**
	 * @param counters
	 * @param transactions
	 *            assumed to be filtered according to counters
	 * @param tidListBound
	 *            - highest item (exclusive) which will have a tidList. set to
	 *            MAX_VALUE when using predictive pptest.
	 */
	Dataset(Counters counters, final Iterator<TransactionReader> transactions, int tidListBound, int minSup, int maxItem) {
		CountersHandler.increment(TopPICounters.NbDatasets);
		this.minSup = minSup;
		this.maxItem = maxItem;

		int maxTransId;

		// if (UByteIndexedTransactionsList.compatible(counters)) {
		// this.transactions = new UByteIndexedTransactionsList(counters);
		// maxTransId = UByteIndexedTransactionsList.getMaxTransId(counters);
		// } else
		if (UShortIndexedTransactionsList.compatible(counters)) {
			this.transactions = new UShortIndexedTransactionsList(counters);
			maxTransId = UShortIndexedTransactionsList.getMaxTransId(counters);
		} else {
			this.transactions = new IntIndexedTransactionsList(counters);
			maxTransId = IntIndexedTransactionsList.getMaxTransId(counters);
		}

		// if (UByteConsecutiveItemsConcatenatedTidList.compatible(maxTransId))
		// {
		// this.tidLists = new
		// UByteConsecutiveItemsConcatenatedTidList(counters, tidListBound);
		// } else
		if (UShortConsecutiveItemsConcatenatedTidList.compatible(maxTransId)) {
			this.tidLists = new UShortConsecutiveItemsConcatenatedTidList(counters, tidListBound);
		} else {
			this.tidLists = new IntConsecutiveItemsConcatenatedTidList(counters, tidListBound);
		}

		TransactionsWriter writer = this.transactions.getWriter();
		while (transactions.hasNext()) {
			TransactionReader transaction = transactions.next();
			if (transaction.getTransactionSupport() != 0 && transaction.hasNext()) {
				final int transId = writer.beginTransaction(transaction.getTransactionSupport());

				while (transaction.hasNext()) {
					final int item = transaction.next();
					writer.addItem(item);

					if (item < 0) {
						System.err.println("WTF item " + item + " appearing in transaction " + transId);
					}

					if (item < tidListBound) {
						this.tidLists.addTransaction(item, transId);
					}
				}

				writer.endTransaction();
			}
		}
	}

	public void compress(int coreItem) {
		this.transactions.compress(coreItem);
	}

	/**
	 * In this implementation the inputted transactions are assumed to be
	 * filtered, therefore it returns null. However this is not true for
	 * subclasses.
	 * 
	 * @return items known to have a 100% support in this dataset
	 */
	int[] getIgnoredItems() {
		return null; // we assume this class always receives
	}

	/**
	 * @return how many transactions (ignoring their weight) are stored behind
	 *         this dataset
	 */
	int getStoredTransactionsCount() {
		return this.transactions.size();
	}

	public TransactionsIterable getSupport(int item) {
		return new TransactionsIterable(this.tidLists.getIterable(item));
	}
	
	public Iterator<TransactionReader> getTransactions() {
		return new TransactionsIterator(this.transactions.getIdIterator());
	}

	public final class TransactionsIterable implements Iterable<TransactionReader> {
		final TIntIterable tids;

		public TransactionsIterable(TIntIterable tidList) {
			this.tids = tidList;
		}

		@Override
		public Iterator<TransactionReader> iterator() {
			return new TransactionsIterator(this.tids.iterator());
		}
	}

	protected final class TransactionsIterator implements Iterator<TransactionReader> {

		protected final TIntIterator it;
		private final ReusableTransactionIterator transIter;

		public TransactionsIterator(TIntIterator tids) {
			this.it = tids;
			this.transIter = transactions.getIterator();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		@Override
		public TransactionReader next() {
			this.transIter.setTransaction(this.it.next());
			return this.transIter;
		}

		@Override
		public boolean hasNext() {
			return this.it.hasNext();
		}
	}

	@Override
	public String toString() {
		return this.transactions.toString();
	}

	public int getMinSup() {
		return this.minSup;
	}

	public int getMaxItem() {
		return this.maxItem;
	}
}
