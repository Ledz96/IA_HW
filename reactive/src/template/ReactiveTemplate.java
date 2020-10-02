package template;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

public class ReactiveTemplate implements ReactiveBehavior
{
	private HashMap<City, List<State>> cityStates = new HashMap<>();
	private HashMap<State, Double> stateProb = new HashMap<>();
	
	private HashMap<State, Double> vValue;
	private HashMap<State, Action> bestAction;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent)
	{
		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class, 0.95);
		
		for (City city : topology.cities())
		{
			// init cityStates with all possible states for each city
			
			List<State> states = Stream.concat(
					topology.cities().stream()
							.filter(dest -> dest != city)
							.map(dest -> new State(city, dest)),
					Stream.of(new State(city, null)))
					.collect(Collectors.toList());

			cityStates.put(city, states);
			
			// calc probabilities for each state
			
			states.stream().map()
		}
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask)
	{
		// TODO
		
		return null;
	}

}
