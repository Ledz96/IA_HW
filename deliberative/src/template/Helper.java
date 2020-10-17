package template;

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
}
