package template;

import logist.task.Task;
import logist.topology.Topology;

import java.util.Objects;
import java.util.Set;

public class PartialState
{
	Topology.City currentCity;
	Set<Task> carriedTasks;
	Set<Task> deliveredTasks;
	
	public PartialState(Topology.City currentCity, Set<Task> carriedTasks, Set<Task> deliveredTasks)
	{
		this.currentCity = currentCity;
		this.carriedTasks = carriedTasks;
		this.deliveredTasks = deliveredTasks;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		PartialState that = (PartialState) o;
		return Objects.equals(currentCity, that.currentCity) &&
			Objects.equals(carriedTasks, that.carriedTasks) &&
			Objects.equals(deliveredTasks, that.deliveredTasks);
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(currentCity, carriedTasks, deliveredTasks);
	}
	
	@Override
	public String toString()
	{
		return "PartialState{" +
			"currentCity=" + currentCity +
			", pickedUpTasks=" + carriedTasks +
			", deliveredTasks=" + deliveredTasks +
			'}';
	}
}
