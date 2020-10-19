package template;

/* import table */
import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeTemplate implements DeliberativeBehavior {

	enum Algorithm { BFS, ASTAR }
	
	/* Environment */
	Topology topology;
	TaskDistribution td;
	
	/* the properties of the agent */
	Agent agent;
	int capacity;

	/* the planning class */
	Algorithm algorithm;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		
		// initialize the planner
		int capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
		
		// ...
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
			// ...
			plan = naivePlan(vehicle, tasks);
			break;
		case BFS:
			// ...
			plan = BFS(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
		return plan;
	}
	
	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}
	
	Set<Task> previouslyCarriedTasks = new HashSet<>();
	
	private void fillPlan(Plan plan, State finalState)
	{
		State previousState = finalState.getPreviousChainLink().getKey();
		if (previousState == null)
			return;
		
		fillPlan(plan, previousState);
		
		ActionDeliberative action = finalState.getPreviousChainLink().getValue();
		action.getPickedUpTasks().forEach(plan::appendPickup);
		
		assert finalState.getCurrentCity() == action.getDestination();
		previousState.getCurrentCity().pathTo(action.getDestination()).forEach(plan::appendMove);
		
		Stream.concat(previousState.getPickedUpTasks().stream(), action.getPickedUpTasks().stream())
			.filter(task -> task.deliveryCity == finalState.getCurrentCity())
			.forEach(plan::appendDelivery);
	}

	private Plan BFS(Vehicle vehicle, TaskSet tasks)
	{
		City currentCity = vehicle.getCurrentCity();
		Plan plan = new Plan(currentCity);
		
		State initialState = !previouslyCarriedTasks.isEmpty() ?
			new State(null, null, currentCity, 0,
			          new HashSet<>(previouslyCarriedTasks),
		              tasks.stream().filter(Predicate.not(previouslyCarriedTasks::contains)).collect(Collectors.toSet()))
			:
			new State(null, null, currentCity, 0, new HashSet<>(), tasks);
		previouslyCarriedTasks = new HashSet<>();
		
		Queue<State> stateQueue = new LinkedList<>();
		stateQueue.add(initialState);
		
		Set<State> visitedStates = new HashSet<>();
		visitedStates.add(initialState);
		
		// temp set for children states before being added to the queue
		Map<State, Double> tempChildrenStates = new HashMap<>();
		
		boolean foundFinal = false;
		Set<State> finalStates = new HashSet<>();
		
		// TODO EDIT check
		while (!(stateQueue.isEmpty() && foundFinal))
		{
			// TODO EDIT check
			if (stateQueue.isEmpty())
			{
				stateQueue.addAll(tempChildrenStates.keySet());
				tempChildrenStates = new HashMap<>();
				continue;
			}
			
			State state = stateQueue.poll();
			if (state.isFinalState())
			{
				// TODO EDIT check
				foundFinal = true;
				
				// we only keep all finals with minimum depth
				if (finalStates.isEmpty() || state.getChainDepth() == finalStates.iterator().next().getChainDepth())
				{
					finalStates.add(state);
					continue;
				}
				else break; // TODO never triggers after new changes?
			}
			
			// Generate possible actions
			
			Set<Set<Task>> pickupSets = Helper
				.combinations(state.getAvailableTasks()
					              .stream()
					              .filter(task -> task.pickupCity == state.getCurrentCity())
					              .collect(Collectors.toSet()))
				.stream()
				.filter(taskSet -> ActionDeliberative.checkEnoughCapacity(state, taskSet))
				.collect(Collectors.toSet());
			
			Set<ActionDeliberative> possibleActions = new HashSet<>(
				pickupSets.stream()
					.flatMap(taskSet -> topology.cities().stream()
						.filter(destCity ->
					        destCity != state.getCurrentCity() &&
						        (
							        // dest city has tasks or is target of at least one picked up task
							        state.getAvailableTasks().stream()
								        .filter(Predicate.not(taskSet::contains))
								        .anyMatch(task -> task.pickupCity == destCity)
							        ||
							        Stream.concat(state.getPickedUpTasks().stream(), taskSet.stream())
								        .anyMatch(task -> task.deliveryCity == destCity)
						        )
						)
						.map(destCity -> new ActionDeliberative(destCity, taskSet)))
					.collect(Collectors.toSet()));
			
			// Generate new states
			Set<State> newStates = possibleActions.stream()
				.map(action -> action.execute(state, vehicle.capacity()))
				.filter(Predicate.not(visitedStates::contains))
				.collect(Collectors.toSet());
			
			// Mark new states as visited and add them to the queue
			
			visitedStates.addAll(newStates);
			
			// don't immediately add children states to the queue, as we may found a better parent on the same level
			// TODO EDIT check
			for (State newState: newStates)
			{
				double newStateCost = newState.getChainCost(vehicle.costPerKm());
				if (!tempChildrenStates.containsKey(newState)
					||
					(tempChildrenStates.containsKey(newState) &&
						newStateCost < tempChildrenStates.get(newState)))
				{
					tempChildrenStates.put(newState, newStateCost);
				}
			}
		}
		
		fillPlan(plan,
		         finalStates.stream()
			         .min(Comparator.comparing(state -> state.getChainCost(vehicle.costPerKm()))).get());
		return plan;
	}

	@Override
	public void planCancelled(TaskSet carriedTasks)
	{
		if (!carriedTasks.isEmpty())
		{
			// This cannot happen for this simple agent, but typically
			// you will need to consider the carriedTasks when the next
			// plan is computed.
			
			previouslyCarriedTasks = carriedTasks;
		}
	}
}
