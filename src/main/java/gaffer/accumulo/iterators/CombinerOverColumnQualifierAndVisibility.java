/**
 * Copyright 2015 Crown Copyright
 * Copyright 2011-2015 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gaffer.accumulo.iterators;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.accumulo.core.data.ByteSequence;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.PartialKey;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.Combiner;
import org.apache.accumulo.core.iterators.IteratorEnvironment;
import org.apache.accumulo.core.iterators.IteratorUtil;
import org.apache.accumulo.core.iterators.OptionDescriber;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;
import org.apache.accumulo.core.iterators.WrappingIterator;
import org.apache.hadoop.io.Text;

/**
 * A {@link Combiner} that combines values with identical rowkeys and column families.
 * 
 * Users extending this class must specify a reduce() method.
 *
 * This class is a modified version of Accumulo's Combiner class (org.apache.accumulo.core.iterators.Combiner).
 */
public abstract class CombinerOverColumnQualifierAndVisibility extends WrappingIterator implements OptionDescriber {

	/**
	 * A Java Iterator that iterates over the {@link ColumnQualifierColumnVisibilityValueTriple} for a given row Key
	 * and column family from a source {@link SortedKeyValueIterator}.
	 */
	public static class ColumnQualifierColumnVisibilityValueTripleIterator implements Iterator<ColumnQualifierColumnVisibilityValueTriple> {
		Key columnQualifierColumnVisibilityValueTripleIteratorTopKey;
		SortedKeyValueIterator<Key,Value> source;
		boolean hasNext;

		/**
		 * Constructs an iterator over {@link Value}s whose {@link Key}s are versions of the current topKey of the source
		 * {@link SortedKeyValueIterator}.
		 * 
		 * @param source  The {@link SortedKeyValueIterator} of {@link Key}, {@link Value} pairs from which to read data.
		 */
		public ColumnQualifierColumnVisibilityValueTripleIterator(SortedKeyValueIterator<Key,Value> source) {
			this.source = source;
			Key unsafeRef = source.getTopKey();
			columnQualifierColumnVisibilityValueTripleIteratorTopKey = new Key(unsafeRef.getRow().getBytes(),
					unsafeRef.getColumnFamily().getBytes(),
					unsafeRef.getColumnQualifier().getBytes(),
					unsafeRef.getColumnVisibility().getBytes(),
					unsafeRef.getTimestamp(),unsafeRef.isDeleted(),
					true);
			hasNext = _hasNext();
		}

		private boolean _hasNext() {
			return source.hasTop() && !source.getTopKey().isDeleted()
					&& columnQualifierColumnVisibilityValueTripleIteratorTopKey.equals(source.getTopKey(), PartialKey.ROW_COLFAM);
		}

		/**
		 * @return <code>true</code> if there is another Value
		 * 
		 * @see java.util.Iterator#hasNext()
		 */
		@Override
		public boolean hasNext() {
			return hasNext;
		}

		/**
		 * @return the next Value
		 * 
		 * @see java.util.Iterator#next()
		 */
		@Override
		public ColumnQualifierColumnVisibilityValueTriple next() {
			if (!hasNext)
				throw new NoSuchElementException();

			Text topColumnQualifier = new Text(source.getTopKey().getColumnQualifier());
			Text topColumnVisibility = new Text(source.getTopKey().getColumnVisibility());
			Value topValue = new Value(source.getTopValue());

			try {
				source.next();
				hasNext = _hasNext();
			} catch (IOException e) {
				throw new RuntimeException(e); // Looks like a bad idea, but this is what the in-built Combiner iterator does
			}
			ColumnQualifierColumnVisibilityValueTriple topVisValPair = new ColumnQualifierColumnVisibilityValueTriple(topColumnQualifier.toString(),
					topColumnVisibility.toString(), topValue);
			return topVisValPair;
		}

		/**
		 * unsupported
		 * 
		 * @see java.util.Iterator#remove()
		 */
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	Key topKey;
	Value topValue;
	ColumnQualifierColumnVisibilityValueTriple topVisValPair;

	@Override
	public Key getTopKey() {
		if (topKey == null)
			return super.getTopKey();
		return topKey;
	}

	@Override
	public Value getTopValue() {
		if (topKey == null)
			return super.getTopValue();
		return topValue;
	}

	@Override
	public boolean hasTop() {
		return topKey != null || super.hasTop();
	}

	@Override
	public void next() throws IOException {
		if (topKey != null) {
			topKey = null;
			topValue = null;
		} else {
			super.next();
		}

		findTop();
	}

	private Key workKey = new Key();

	/**
	 * Sets the topKey and topValue based on the top key of the source.
	 * 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	private void findTop() {
		// check if aggregation is needed
		if (super.hasTop()) {
			workKey.set(super.getTopKey());
			if (workKey.isDeleted())
				return;
			Iterator<ColumnQualifierColumnVisibilityValueTriple> viter = new ColumnQualifierColumnVisibilityValueTripleIterator(getSource());
			topVisValPair = reduce(workKey, viter);
			topValue = topVisValPair.getValue();
			topKey = new Key(workKey.getRow().toString(), workKey.getColumnFamily().toString(), topVisValPair.getColumnQualifier(),
					topVisValPair.getColumnVisibility(), workKey.getTimestamp());

			while (viter.hasNext())
				viter.next();
		}
	}

	@Override
	public void seek(Range range, Collection<ByteSequence> columnFamilies, boolean inclusive) throws IOException {
		// do not want to seek to the middle of a value that should be combined...

		Range seekRange = IteratorUtil.maximizeStartKeyTimeStamp(range);

		super.seek(seekRange, columnFamilies, inclusive);
		findTop();

		if (range.getStartKey() != null) {
			while (hasTop() && getTopKey().equals(range.getStartKey(), PartialKey.ROW_COLFAM)
					&& getTopKey().getTimestamp() > range.getStartKey().getTimestamp()) {
				// The value has a more recent time stamp, so pass it up
				next();
			}

			while (hasTop() && range.beforeStartKey(getTopKey())) {
				next();
			}
		}
	}

	/**
	 * Reduces a list of triples of (column qualifier, column visibility, Value) into a single
	 * triple.
	 * 
	 * @param key  The most recent version of the Key being reduced.
	 * 
	 * @param iter  An iterator over the Values for different versions of the key.
	 * 
	 * @return The combined Value.
	 */
	public abstract ColumnQualifierColumnVisibilityValueTriple reduce(Key key, Iterator<ColumnQualifierColumnVisibilityValueTriple> iter);

	@Override
	public void init(SortedKeyValueIterator<Key,Value> source, Map<String,String> options, IteratorEnvironment env) throws IOException {
		super.init(source, options, env);
	}

	@Override
	public SortedKeyValueIterator<Key,Value> deepCopy(IteratorEnvironment env) {
		CombinerOverColumnQualifierAndVisibility newInstance;
		try {
			newInstance = this.getClass().newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		newInstance.setSource(getSource().deepCopy(env));
		return newInstance;
	}

	@Override
	public IteratorOptions describeOptions() {
		IteratorOptions io = new IteratorOptions("comb_rk_cf",
				"Applies a reduce function to triples of (column qualifier, column visibility, value) with identical (rowkey, column family)",
				null, null);
		return io;
	}

	@Override
	public boolean validateOptions(Map<String,String> options) {
		return options.keySet().size() == 0;
	}

}

