package template;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
	private HashMap<Pair<State,ActionReactive>, Double> stateActionRewards = new HashMap<>();
	private HashMap<State, Double> stateProbabilities = new HashMap<>();
	private HashMap<State, ActionReactive> stateActionBest = new HashMap<>();

	private Double discountFactor;
	private int numActions;
	private Agent myAgent;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent)
	{
		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
        discountFactor = agent.readProperty("discount-factor", Double.class, 0.95);
		numActions = 0;
		myAgent = agent;

		topology.cities().forEach(city -> {
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
			
			states.forEach(s -> {
				List<ActionReactive> actionList = new ArrayList<>();
				
				if (s.isTaskState()) {
					ActionReactive a = new ActionReactive(s.getTaskDestination(), true);
					actionList.add(a);
					stateActionRewards.put(new Pair<>(s, a), (double) (td.reward(s.getCurrentCity(), s.getTaskDestination()) - s.getCurrentCity().distanceTo(s.getTaskDestination()) * agent.vehicles().get(0).costPerKm()));
				}

				s.getCurrentCity().neighbors().forEach(c -> {
					ActionReactive a = new ActionReactive(c, false);
					actionList.add(a);
					stateActionRewards.put(new Pair<>(s, a), (double) - s.getCurrentCity().distanceTo(c) * agent.vehicles().get(0).costPerKm());
				});
				
				stateActionSpace.put(s, actionList);
			});

			// initialize v value
			
			states.forEach(state -> vValue.put(state, - (double) (int) Double.MAX_VALUE));
		});

		train();
	}

	private void train()
    {
        AtomicBoolean hasConverged = new AtomicBoolean();
        int iterations = 0;
        
        do {
            hasConverged.set(true);
            iterations++;
            cityStates.values().stream().flatMap(List::stream).forEach(s -> {
                stateActionSpace.get(s).forEach(a -> {
                    double qValue = stateActionRewards.get(new Pair<>(s, a)) +
                            discountFactor * stateProbabilities.entrySet().stream()
                                .filter(e -> e.getKey().getCurrentCity() == a.getDestination())
                                .map(e -> e.getValue() * vValue.get(e.getKey()))
                                .reduce(0.0, Double::sum);

                    if (qValue > vValue.get(s))
                    {
                        hasConverged.set(false);
                        vValue.put(s, qValue);
                        stateActionBest.put(s, a);
                    }
                });
            });
        }
        while (!hasConverged.get());
        
        System.out.printf("Iterations: %d", iterations);
    }

	@Override
	public Action act(Vehicle vehicle, Task availableTask)
	{
		State state = new State(vehicle.getCurrentCity(), availableTask == null ? null : availableTask.deliveryCity);
		ActionReactive action = stateActionBest.get(state);
		
		if (numActions >= 1)
		{
			System.out.printf("[Reactive %.2f] avg profit = %f (%d actions)%n",
			                  discountFactor,
			                  myAgent.getTotalProfit() / (double) numActions,
			                  myAgent.getTotalProfit());
		}
		numActions++;
		
		return action.isDeliveringTask() ? new Action.Pickup(availableTask) : new Action.Move(action.getDestination());
	}
}
