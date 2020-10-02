package template;

import java.util.*;
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
	private HashMap<State, Double> vValue = new HashMap<>();
	private HashMap<State, List<ActionReactive>> stateActionSpace = new HashMap<>();
	private HashMap<Map.Entry<State,ActionReactive>, Double> stateActionRewards = new HashMap<>();
	private HashMap<State, Double> stateProbabilities = new HashMap<>();

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
			
			states.forEach(state -> stateProbabilities.put(state, td.probability(state.getCurrentCity(), state.getTaskDestination())));
			
			// init action list and action reward table
			
			List<ActionReactive> actionList = new ArrayList<>();
			states.forEach(s -> {
				if (s.isTaskState()) {
					ActionReactive a = new ActionReactive(s.getTaskDestination(), true);
					actionList.add(a);
					stateActionRewards.put(new AbstractMap.SimpleEntry<State, ActionReactive>(s, a), (double) (td.reward(s.getCurrentCity(), s.getTaskDestination()) - agent.vehicles().get(0).costPerKm()));
				}

				s.getCurrentCity().neighbors().forEach(c -> {
					ActionReactive a = new ActionReactive(c, false);
					actionList.add(a);
					stateActionRewards.put(new AbstractMap.SimpleEntry<State, ActionReactive>(s, a), (double) (td.reward(s.getCurrentCity(), s.getTaskDestination()) - agent.vehicles().get(0).costPerKm()));
				});

				stateActionSpace.put(s, actionList);
			});

			// initialize v value

			states.forEach(state -> vValue.put(state, Double.MIN_VALUE));
		}
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask)
	{
		// TODO
		
		return null;
	}
}
