package template;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Helper
{
	public static <T1,T2> Stream<Pair<T1,T2>> zip(Stream<T1> leftStream, Stream<T2> rightStream)
	{
		Iterator<T2> rightIterator = rightStream.iterator();
		return leftStream.map(left -> new Pair<>(left, rightIterator.next()));
	}
	
	public static <T1,T2> Stream<Pair<T1,T2>> zip(Collection<T1> leftCollection, Collection<T2> rightCollection)
	{
		return zip(leftCollection.stream(), rightCollection.stream());
	}
	
	public static <T> Stream<Pair<Integer, T>> enumerate(Collection<T> collection)
	{
		return zip(IntStream.range(0, collection.size()).boxed(), collection.stream());
	}
	
//	public static <T> Set<LinkedList<T>> _permutations(LinkedList<T> list, Set<LinkedList<T>> partialSet)
//	{
//		if (list.isEmpty())
//			return partialSet;
//
//		T el = list.removeFirst();
//		Set<LinkedList<T>> newPartialSet = new HashSet<>();
//
//		if (partialSet.isEmpty())
//		{
//			newPartialSet.add(Stream.of(el).collect(Collectors.toCollection(LinkedList::new)));
//		}
//		else
//		{
//			for (List<T> partial: partialSet)
//			{
//				IntStream.rangeClosed(0, partial.size()).forEachOrdered(i -> {
//					LinkedList<T> newPartial = new LinkedList<>(partial);
//					newPartial.add(i, el);
//					newPartialSet.add(newPartial);
//				});
//			}
//		}
//
//		return _permutations(list, newPartialSet);
//	}
//
//	public static <T> Set<LinkedList<T>> permutations(List<T> list)
//	{
//		return _permutations(new LinkedList<>(list), new HashSet<>());
//	}
	
	public static long factorial(int num)
	{
		assert num >= 0;
		
		if (num == 0)
			return 1;
		return num * factorial(num - 1);
	}
}
