package template;

import logist.topology.Topology.City;

public class State
{
	private City currentCity;
	private City taskDestination;

	public City getTaskDestination()
	{
		return taskDestination;
	}

	public City getCurrentCity()
	{
		return currentCity;
	}

	public State(City current, City destination)
	{
		if (current == null)
			throw new NullPointerException("State constructor parameter 'current' shall not be null.");

		currentCity = current;
		taskDestination = destination;
	}

	public boolean isTaskState()
	{
		return taskDestination != null;
	}

	// overrides for hashing

	@Override
	public int hashCode()
	{
		// Szudzik

		int x = currentCity.hashCode();
		int y = taskDestination == null ? 0 : taskDestination.hashCode();

		int xx = x >= 0 ? x * 2 : x * -2 - 1;
		int yy = y >= 0 ? y * 2 : y * -2 - 1;

		return (xx >= yy) ? (xx * xx + xx + yy) : (yy * yy + xx);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!(obj instanceof State))
			return false;

		State state = (State) obj;
		return currentCity == state.getCurrentCity() && taskDestination == state.getTaskDestination();
	}
	
	@Override
	public String toString()
	{
		return String.format("State(%s, %s)", currentCity, taskDestination);
	}
}
