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

import fr.liglab.mining.CountersHandler.TopPICounters;
import fr.liglab.mining.internals.ExplorationStep;
import fr.liglab.mining.internals.FrequentsIterator;
import fr.liglab.mining.internals.Selector;

public class Grouper {
	
	public final int nbGroups;
	public final int maxItemID;
	
	public Grouper(int groupsCount, int maxItem) {
		this.nbGroups = groupsCount;
		this.maxItemID = maxItem;
	}
	
	/**
	 * @param itemId
	 * @return item's groupID or -1 if we should dump the corresponding group (see SingleGroup)
	 */
	public int getGroupId(int itemId) {
		return itemId % this.nbGroups;
	}
	
	public static final class SingleGroup extends Grouper {
		private final int groupId;
		
		public SingleGroup(int groupsCount, int maxItem, int singleGroupID) {
			super(groupsCount, maxItem);
			this.groupId = singleGroupID;
		}
		
		@Override
		public int getGroupId(int itemId) {
			return (super.getGroupId(itemId) == this.groupId) ? this.groupId : -1;
		}
	}
	
	public FrequentsIterator getGroupItems(int groupId) {
		return new ItemsInGroupIterator(groupId, this.maxItemID);
	}
	
	private final class ItemsInGroupIterator implements FrequentsIterator {
		
		private int current;
		private final int max;
		
		public ItemsInGroupIterator(int start, int last) {
			this.current = start - nbGroups;
			this.max = last;
		}
		
		@Override
		public int next() {
			if (this.current == Integer.MIN_VALUE) {
				return -1;
			} else {
				this.current += nbGroups;
				
				if (this.current > this.max) {
					this.current = Integer.MIN_VALUE;
					return -1;
				} else {
					return this.current;
				}
			}
		}

		@Override
		public int peek() {
			return this.current;
		}

		@Override
		public int last() {
			return this.max;
		}
	}
	
	public Selector getStartersSelector(Selector next, int groupId, int[] renamingToGlobal) {
		return new StartersSelector(next, groupId, renamingToGlobal);
	}
	
	/**
	 * This selector does not copy itself !!
	 * This means that it must be the selector chain's head, use it to restrict the exploration 
	 * of an initial ExplorationState to group's items
	 */
	private final class StartersSelector extends Selector {
		
		private final int gid;
		private final int[] renaming;
		
		public StartersSelector(Selector follower, int groupId, int[] renamingToGlobal) {
			super(follower);
			this.gid = groupId;
			this.renaming = renamingToGlobal;
		}
		
		@Override
		protected boolean allowExploration(int extension, ExplorationStep state)
				throws WrongFirstParentException {
			
			return (this.renaming[extension] % nbGroups) == this.gid;
		}

		@Override
		protected Selector copy(Selector newNext) {
			return newNext;
		}

		@Override
		protected TopPICounters getCountersKey() {
			return null;
		}
	}
	

	public FrequentsIterator getNonGroupItems(int groupId) {
		if (this.nbGroups == 1) {
			throw new IllegalArgumentException("There's no item outside a unique group !");
		}
		
		return new ItemsNotInGroupIterator(groupId, this.maxItemID, groupId);
	}
	
	private final class ItemsNotInGroupIterator implements FrequentsIterator {
		
		private int current;
		private final int max;
		private final int groupId;
		
		public ItemsNotInGroupIterator(int start, int last, int gid) {
			this.current = -1;
			this.max = last;
			this.groupId = gid;
		}
		
		@Override
		public int next() {
			if (this.current == Integer.MIN_VALUE) {
				return -1;
			} else {
				this.current++;
				
				if ((this.current % nbGroups) == this.groupId) {
					this.current++;
				}
				
				if (this.current > this.max) {
					this.current = Integer.MIN_VALUE;
					return -1;
				} else {
					return this.current;
				}
			}
		}

		@Override
		public int peek() {
			return this.current;
		}

		@Override
		public int last() {
			return this.max;
		}
	}
}
