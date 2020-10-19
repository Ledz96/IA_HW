package template;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Helper
{
	public static <T> Set<Set<T>> combinations(Set<T> set)
	{
		Set<Set<T>> ret = new HashSet<>();
		ret.add(new HashSet<>());
		
		for (int i = 0; i < set.size(); i++)
		{
			ret.addAll(ret.stream().flatMap(retSet -> set.stream().map(el -> {
				Set<T> newSet = new HashSet<>(retSet);
				newSet.add(el);
				return newSet;
			})).collect(Collectors.toSet()));
		}
		
		return ret;
	}
	
	public static <T> Set<Pair<T>> pairs(Set<T> set)
	{
		return set.stream()
			.flatMap(left ->
				         set.stream()
					         .filter(right -> right != left)
					         .map(right -> new Pair<>(left, right)))
			.collect(Collectors.toSet());
	}
}
