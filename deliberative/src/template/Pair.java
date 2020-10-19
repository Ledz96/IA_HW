package template;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Pair<T>
{
	private final T first;
	private final T second;
	
	public T getFirst()
	{
		return first;
	}
	
	public T getSecond()
	{
		return second;
	}
	
	public Pair(T first, T second)
	{
		this.first = first;
		this.second = second;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Pair<?> pair = (Pair<?>) o;
		return (
			Objects.equals(first, pair.first) &&
				Objects.equals(second, pair.second)
		)
			||
			(
				Objects.equals(first, pair.second) &&
					Objects.equals(second, pair.first)
				);
	}
	
	@Override
	public int hashCode()
	{
		Set<T> set = new HashSet<>();
		set.add(first);
		set.add(second);
		return set.hashCode();
	}
	
	@Override
	public String toString()
	{
		return "Pair{" +
			"first=" + first +
			", second=" + second +
			'}';
	}
}
