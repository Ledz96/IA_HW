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
		currentCity = current;
		taskDestination = destination;
	}
	
	// overrides for hashing
	
	@Override
	public int hashCode()
	{
		// Szudzik
		
		int x = currentCity.hashCode();
		int y = taskDestination.hashCode();
		
		int xx = x >= 0 ? x * 2 : x * -2 - 1;
		int yy = y >= 0 ? y * 2 : y * -2 - 1;
		
		return (xx >= yy) ? (xx * xx + xx + yy) : (yy * yy + xx);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		
		State state = (State) obj;
		return currentCity.equals(state.getCurrentCity()) && taskDestination.equals(state.getTaskDestination());
	}
}
