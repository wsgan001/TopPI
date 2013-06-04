package fr.liglab.lcm.internals.tidlist;

import java.util.Arrays;

import fr.liglab.lcm.internals.Counters;

public class UByteConsecutiveItemsConcatenatedTidList extends ConsecutiveItemsConcatenatedTidList {

	@SuppressWarnings("cast")
	public static boolean compatible(int maxTid) {
		return maxTid <= ((int) Byte.MAX_VALUE) - ((int) Byte.MIN_VALUE);
	}

	private byte[] array;

	@Override
	public TidList clone() {
		UByteConsecutiveItemsConcatenatedTidList o = (UByteConsecutiveItemsConcatenatedTidList) super.clone();
		o.array = Arrays.copyOf(this.array, this.array.length);
		return o;
	}

	@Override
	void allocateArray(int size) {
		this.array = new byte[size];
	}

	@Override
	void write(int position, int transaction) {
		if (transaction > Byte.MAX_VALUE) {
			transaction = -transaction + Byte.MAX_VALUE;
			if (transaction < Byte.MIN_VALUE) {
				throw new IllegalArgumentException(transaction + " too big for a byte");
			}
		}
		this.array[position] = (byte) transaction;
	}

	@SuppressWarnings("cast")
	@Override
	int read(int position) {
		if (this.array[position] >= 0) {
			return this.array[position];
		} else {
			return ((int) -this.array[position]) + ((int) Byte.MAX_VALUE);
		}
	}

	public UByteConsecutiveItemsConcatenatedTidList(Counters c) {
		super(c);
	}

	public UByteConsecutiveItemsConcatenatedTidList(int[] lengths) {
		super(lengths);
	}

}