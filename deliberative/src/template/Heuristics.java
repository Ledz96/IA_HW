package template;

import logist.topology.Topology;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Heuristics
{
	public static double H1(State state, int costPerKm)
	{
		Set<Topology.City> destCitiesSet = Stream.concat(state.getPickedUpTasks().stream(), state.getAvailableTasks().stream())
			.map(task -> task.deliveryCity)
			.collect(Collectors.toSet());
		
		return costPerKm * destCitiesSet.stream()
			.map(destCity -> state.getCurrentCity().distanceTo(destCity))
			.max(Double::compareTo).get();
	}
	
	public static double H2(State state, int costPerKm)
	{
		double maxAvailable = state.getAvailableTasks().size() < 1 ? 0 :
			state.getAvailableTasks().stream()
				.map(availTask ->
					     state.getCurrentCity().distanceTo(availTask.pickupCity) + availTask.pickupCity.distanceTo(availTask.deliveryCity))
				.max(Double::compareTo).get();
		
		double maxPickedUp = state.getPickedUpTasks().size() < 1 ? 0 :
			state.getPickedUpTasks().stream()
				.map(pickedUpTask -> state.getCurrentCity().distanceTo(pickedUpTask.deliveryCity))
				.max(Double::compareTo).get();
		
		return costPerKm * Double.max(maxAvailable, maxPickedUp);
	}
}
