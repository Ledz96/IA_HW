package template;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

public class Permutations<T>
{
	private Set<LinkedList<T>> permRet = new HashSet<>();
	private LinkedList<T> permTemp = new LinkedList<>();
	
	private void _get(Set<T> set, int level)
	{
		if (set.isEmpty())
		{
			permRet.add(new LinkedList<>(permTemp));
			return;
		}
		
		Iterator<T> it = set.iterator();
		while (it.hasNext())
		{
			T el = it.next();
			permTemp.add(el);
			
			Set<T> passingSet = new HashSet<>(set);
			passingSet.remove(el);
			
			_get(passingSet, level + 1);
			permTemp.removeLast();
		}
	}
	
	public Set<LinkedList<T>> get(Set<T> set)
	{
		_get(set, 0);
		return permRet;
	}
}