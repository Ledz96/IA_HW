package template.Centralized;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Helper
{
	public static <T1,T2> Stream<Pair<T1,T2>> zip(Stream<T1> leftStream, Stream<T2> rightStream)
	{
		Iterator<T2> rightIterator = rightStream.iterator();
		return leftStream.map(left -> new Pair<>(left, rightIterator.next()));
	}
	
	public static <T> Stream<Pair<Integer, T>> enumerate(Collection<T> collection)
	{
		return zip(IntStream.range(0, collection.size()).boxed(), collection.stream());
	}
}
