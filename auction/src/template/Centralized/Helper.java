package template.Centralized;

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
	
	public static <T> Stream<Pair<Integer, T>> enumerate(Collection<T> collection)
	{
		return zip(IntStream.range(0, collection.size()).boxed(), collection.stream());
	}
	
	public static Double median(Collection<Double> collection)
	{
		List<Double> list = new ArrayList<>(collection);
		Collections.sort(list);
		
		int mid = list.size() / 2;
		if (list.size() % 2 == 1)
			return list.get(mid);
		else
			return (list.get(mid - 1) + list.get(mid)) / 2;
	}
	
	/** https://stackoverflow.com/a/37931179 */
	public static double std(List<Double> table)
	{
		double mean = table.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
		double temp = 0;
		
		for (int i = 0; i < table.size(); i++)
		{
			double sqrDiffToMean = Math.pow(table.get(i) - mean, 2);
			temp += sqrDiffToMean;
		}
		
		double meanOfDiffs = temp / table.size();
		return Math.sqrt(meanOfDiffs);
	}
}
