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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeTemplate implements DeliberativeBehavior
{
	enum Algorithm { naive, BFS, ASTAR }
	enum Heuristic { H1, H2 }
	private final Map<Heuristic, BiFunction<State, Integer, Double>> heuristicFunctionMap = new HashMap<>();
	
	/* Environment */
	Topology topology;
	TaskDistribution td;
	
	/* the properties of the agent */
	Agent agent;
	int capacity;

	/* the planning class */
	Algorithm algorithm;
	Heuristic heuristic;
	
	private static AtomicInteger sharedId = new AtomicInteger(0);
	private int id;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent)
	{
		id = sharedId.incrementAndGet();
		
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		
		// initialize the planner
		int capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
		
		heuristicFunctionMap.put(Heuristic.H1, Heuristics::H1);
		heuristicFunctionMap.put(Heuristic.H2, Heuristics::H2);
		
		if (algorithm == Algorithm.ASTAR)
		{
			String heuristicName = agent.readProperty("heuristic", String.class, "H2");
			heuristic = Heuristic.valueOf(heuristicName.toUpperCase());
			System.out.printf("Heuristic: %s%n", heuristic);
		}
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks)
	{
		Plan plan = null;
		
		long startTime = System.currentTimeMillis();
		
		// Compute the plan with the selected algorithm.
		switch (algorithm)
		{
			case naive:
				plan = naivePlan(vehicle, tasks);
				break;
			case BFS:
				try {
					plan = BFSPlan(vehicle, tasks);
				} catch (TimeoutException e) {
					e.printStackTrace();
				}
				break;
			case ASTAR:
				try {
					plan = ASTARPlan(vehicle, tasks);
				} catch (TimeoutException e) {
					e.printStackTrace();
				}
				break;
			default:
				throw new AssertionError("Should not happen.");
		}
		
		if (plan == null)
			System.exit(0);
		
		long elapsedTime = System.currentTimeMillis() - startTime;
		System.out.printf("[%s] planning time: %dms%n", algorithm, elapsedTime);
		
		return plan;
	}
	
	private Plan naivePlan(Vehicle vehicle, TaskSet tasks)
	{
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
	
	private State getInitialState(Vehicle vehicle, TaskSet tasks)
	{
		return previouslyCarriedTasks.isEmpty() ?
			new State(null,
			          null,
			          vehicle.costPerKm(),
			          vehicle.getCurrentCity(),
			          vehicle.capacity(),
			          new HashSet<>(),
			          tasks)
			:
			new State(null,
			          null,
			          vehicle.costPerKm(),
			          vehicle.getCurrentCity(),
			          vehicle.capacity() - previouslyCarriedTasks.stream().map(task -> task.weight).reduce(0, Integer::sum),
			          new HashSet<>(previouslyCarriedTasks),
			          tasks.stream().filter(Predicate.not(previouslyCarriedTasks::contains)).collect(Collectors.toSet()));
	}
	
	private void checkElapsedTime(long startTime) throws TimeoutException
	{
		long elapsedTime = System.currentTimeMillis() - startTime;
		if (elapsedTime > 60 * 1000)
			throw new TimeoutException("Exceeded 60 seconds");
	}
	
	private Set<State> computeDerivedStates(State state, Vehicle vehicle)
	{
		// Generate al possible subsets of available tasks from the current city which we have enough capacity for
		Set<Set<Task>> pickupSets = Helper
			.combinations(state.getAvailableTasks()
				              .stream()
				              .filter(task -> task.pickupCity == state.getCurrentCity())
				              .collect(Collectors.toSet()))
			.stream()
			.filter(taskSet -> ActionDeliberative.checkEnoughCapacity(state, taskSet))
			.collect(Collectors.toSet());
		
		// Generate all possible actions (for each set of task picked up and destination city)
		// Only useful actions are considered (no useless movements to cities without actions to be picked up or that
		// are not target of any picked up task)
		Set<ActionDeliberative> possibleActions = pickupSets.stream()
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
			.collect(Collectors.toSet());
		
		// Generate new states from the possible actions
		return possibleActions.stream()
			.map(action -> action.execute(state, vehicle))
			.collect(Collectors.toSet());
	}
	
		private void fillPlan(Plan plan, State finalState)
	{
		// Recursively climb up the chain of states and explore the chain back down while filling out the plan
		
		State previousState = finalState.getPreviousChainLink().getKey();
		if (previousState == null)
			return;
		
		fillPlan(plan, previousState);
		
		ActionDeliberative action = finalState.getPreviousChainLink().getValue();
		action.getPickedUpTasks().forEach(plan::appendPickup);
		
		System.out.printf("[%s] State: %s%n", id, previousState);
		System.out.printf("[%s] Action: %s%n", id, action);
		action.getPickedUpTasks().forEach(pickedUpTask -> System.out.printf("[%s] pickup: %s%n", id, pickedUpTask));
		
		assert finalState.getCurrentCity() == action.getDestination();
		previousState.getCurrentCity().pathTo(action.getDestination()).forEach(plan::appendMove);
		
		Stream.concat(previousState.getPickedUpTasks().stream(), action.getPickedUpTasks().stream())
			.filter(task -> task.deliveryCity == finalState.getCurrentCity())
			.forEach(plan::appendDelivery);
	}
	
	private Plan BFSPlan(Vehicle vehicle, TaskSet tasks) throws TimeoutException
	{
		long startTime = System.currentTimeMillis();
		int iterations = 0;

		City currentCity = vehicle.getCurrentCity();
		Plan plan = new Plan(currentCity);

		State initialState = getInitialState(vehicle, tasks);

		Queue<State> stateQueue = new LinkedList<>();
		stateQueue.add(initialState);

		Map<State, Double> visitedStates = new HashMap<>();
		visitedStates.put(initialState, initialState.getChainCost());

		State bestState = null;

		while (!stateQueue.isEmpty())
		{
			checkElapsedTime(startTime);
			iterations++;

			State state = stateQueue.poll();

			if (state.isFinalState() && (bestState == null || state.getChainCost() < bestState.getChainCost()))
			{
				bestState = state;
			}

			for (State newState: computeDerivedStates(state, vehicle))
			{
				if (!visitedStates.containsKey(newState)
					||
					(visitedStates.containsKey(newState) && newState.getChainCost() < visitedStates.get(newState)))
				{
					stateQueue.add(newState);
					visitedStates.put(newState, newState.getChainCost());
				}
			}
		}

		System.out.printf("[BFS] iterations: %d%n", iterations);

		fillPlan(plan, bestState);
		System.out.printf("[BFS] min cost: %f%n", vehicle.costPerKm() * plan.totalDistance());
		return plan;
	}
	
	private Plan ASTARPlan(Vehicle vehicle, TaskSet tasks) throws TimeoutException
	{
		long startTime = System.currentTimeMillis();
		int iterations = 0;
		
		City currentCity = vehicle.getCurrentCity();
		Plan plan = new Plan(currentCity);
		
		State initialState = getInitialState(vehicle, tasks);
		BiFunction<State, Integer, Double> heuristicFunction = heuristicFunctionMap.get(heuristic);
		
		Comparator<State> comparator = Comparator.comparingDouble(state ->
			state.getChainCost() + heuristicFunction.apply(state, vehicle.costPerKm()));
		PriorityQueue<State> Q = new PriorityQueue<>(comparator);
		Q.add(initialState);
		
		Map<State, Double> C = new HashMap<>();
		
		while (!Q.isEmpty())
		{
			checkElapsedTime(startTime);
			iterations++;
			
			State n = Q.poll();
			double nCost = n.getChainCost();
			
			if (!C.containsKey(n) || (C.containsKey(n) && nCost < C.get(n)))
			{
				C.put(n, nCost);
				
				Set<State> derivedStates = computeDerivedStates(n, vehicle);
				
				for (State derivedState: derivedStates)
				{
					if (derivedState.isFinalState())
					{
						System.out.printf("[ASTAR] iterations: %d%n", iterations);
						System.out.printf("[ASTAR] min cost: %f%n", derivedState.getChainCost());
						
						fillPlan(plan, derivedState);
						return plan;
					}
				}
				
				Q.addAll(derivedStates);
			}
		}
		
		throw new AssertionError("should not reach here");
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
