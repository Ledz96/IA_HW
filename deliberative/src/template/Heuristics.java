package template;

import logist.topology.Topology;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Heuristics
{
	public static double H1c(State state, int costPerKm)
	{
		double maxDistAnyTask = Stream.concat(state.getPickedUpTasks().stream(), state.getAvailableTasks().stream())
			.map(task -> state.getCurrentCity().distanceTo(task.deliveryCity))
			.max(Double::compareTo).get();
		
		double maxLengthAvailable = state.getAvailableTasks().size() == 0 ? 0 :
			state.getAvailableTasks().stream()
				.map(task -> task.pickupCity.distanceTo(task.deliveryCity))
				.max(Double::compareTo).get();
		
		return costPerKm * Double.max(maxDistAnyTask, maxLengthAvailable);
	}
	
	public static double H2(State state, int costPerKm)
	{
		Set<Topology.City> destCitiesSet = Stream.concat(state.getPickedUpTasks().stream(), state.getAvailableTasks().stream())
			.map(task -> task.deliveryCity)
			.collect(Collectors.toSet());
		
		return costPerKm * (destCitiesSet.stream()
			.map(city -> city.neighbors().stream().map(city::distanceTo).min(Double::compareTo).get())
			.reduce(0., Double::sum));
	}
	
	public static double H3(State state, int costPerKm)
	{
		Set<Topology.City> destCitiesSet = Stream.concat(state.getPickedUpTasks().stream(), state.getAvailableTasks().stream())
			.map(task -> task.deliveryCity)
			.collect(Collectors.toSet());
		
		return costPerKm * (destCitiesSet.size() < 2 ? 0 :
			Helper.pairs(destCitiesSet).stream()
				.map(pair -> pair.getFirst().distanceTo(pair.getSecond()))
				.max(Double::compareTo).get());
	}
	
	public static double H4(State state, int costPerKm)
	{
		return Double.max(H1c(state, costPerKm), H3(state, costPerKm));
	}
}
