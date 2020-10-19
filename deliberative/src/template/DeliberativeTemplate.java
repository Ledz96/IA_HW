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
import java.util.concurrent.atomic.AtomicBoolean;
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
	enum Heuristic { H1, H2, H3, H4 }
	private final Map<Heuristic, BiFunction<State, Integer, Double>> heuristicMap = new HashMap<>();
	
	/* Environment */
	Topology topology;
	TaskDistribution td;
	
	/* the properties of the agent */
	Agent agent;
	int capacity;

	/* the planning class */
	Algorithm algorithm;
	Heuristic heuristic;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent)
	{
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		
		// initialize the planner
		int capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
		
		heuristicMap.put(Heuristic.H1, Heuristics::H1c);
		heuristicMap.put(Heuristic.H2, Heuristics::H2);
		heuristicMap.put(Heuristic.H3, Heuristics::H3);
		heuristicMap.put(Heuristic.H4, Heuristics::H4);
		
		if (algorithm == Algorithm.ASTAR)
		{
			String heuristicName = agent.readProperty("heuristic", String.class, "H1");
			heuristic = Heuristic.valueOf(heuristicName.toUpperCase());
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
				try
				{
					plan = BFSPlan(vehicle, tasks);
				} catch (TimeoutException e)
				{
					e.printStackTrace();
				}
				break;
			case ASTAR:
				try
				{
					plan = ASTARPlan(vehicle, tasks);
				} catch (TimeoutException e)
				{
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
	
	private Set<State> computeDerivedStates(State state, Vehicle vehicle)
	{
		// Generate possible actions
		
		Set<Set<Task>> pickupSets = Helper
			.combinations(state.getAvailableTasks()
				              .stream()
				              .filter(task -> task.pickupCity == state.getCurrentCity())
				              .collect(Collectors.toSet()))
			.stream()
			.filter(taskSet -> ActionDeliberative.checkEnoughCapacity(state, taskSet))
			.collect(Collectors.toSet());
		
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
		
		// Generate new states
		return possibleActions.stream()
			.map(action -> action.execute(state, vehicle.capacity()))
			.collect(Collectors.toSet());
	}
	
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

	private Plan BFSPlan(Vehicle vehicle, TaskSet tasks) throws TimeoutException
	{
		long startTime = System.currentTimeMillis();
		
		City currentCity = vehicle.getCurrentCity();
		Plan plan = new Plan(currentCity);
		
		State initialState = !previouslyCarriedTasks.isEmpty() ?
			new State(null, null, currentCity, 0,
			          new HashSet<>(previouslyCarriedTasks),
		              tasks.stream().filter(Predicate.not(previouslyCarriedTasks::contains)).collect(Collectors.toSet()))
			:
			new State(null, null, currentCity, 0, new HashSet<>(), tasks);
		
		Queue<State> stateQueue = new LinkedList<>();
		stateQueue.add(initialState);
		
		Set<State> visitedStates = new HashSet<>();
		visitedStates.add(initialState);
		
		// temp set for children states before being added to the queue
		Map<State, Double> tempChildrenStates = new HashMap<>();
		
		boolean foundFinal = false;
		Set<State> finalStates = new HashSet<>();
		
		while (!(stateQueue.isEmpty() && foundFinal))
		{
			long elapsedTime = System.currentTimeMillis() - startTime;
			if (elapsedTime > 60 * 1000)
				throw new TimeoutException("Exceeded 60 seconds");
			
			if (stateQueue.isEmpty())
			{
				stateQueue.addAll(tempChildrenStates.keySet());
				tempChildrenStates = new HashMap<>();
				
				// Mark new states as visited and add them to the queue
				visitedStates.addAll(tempChildrenStates.keySet());
				
				continue;
			}
			
			State state = stateQueue.poll();
			if (state.isFinalState())
			{
				foundFinal = true;
				
				// we only keep all finals with minimum depth
				finalStates.add(state);
				continue;
			}
			
			// Generate new states
			Set<State> newStates = computeDerivedStates(state, vehicle).stream()
				.filter(Predicate.not(visitedStates::contains))
				.collect(Collectors.toSet());
			
			// don't immediately add children states to the queue, as we may found a better parent on the same level
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
	
	private Plan ASTARPlan(Vehicle vehicle, TaskSet tasks) throws TimeoutException
	{
		long startTime = System.currentTimeMillis();
		
		City currentCity = vehicle.getCurrentCity();
		Plan plan = new Plan(currentCity);
		
		State initialState = !previouslyCarriedTasks.isEmpty() ?
			new State(null, null, currentCity, 0,
			          new HashSet<>(previouslyCarriedTasks),
			          tasks.stream().filter(Predicate.not(previouslyCarriedTasks::contains)).collect(Collectors.toSet()))
			:
			new State(null, null, currentCity, 0, new HashSet<>(), tasks);
		previouslyCarriedTasks = new HashSet<>();
		
		BiFunction<State, Integer, Double> heuristicFunction = heuristicMap.get(heuristic);
		
		Comparator<State> comparator = Comparator.comparingDouble(state ->
			state.getChainCost(vehicle.costPerKm()) + heuristicFunction.apply(state, vehicle.costPerKm()));
		PriorityQueue<State> Q = new PriorityQueue<>(comparator);
		Q.add(initialState);
		
		Map<State, Double> C = new HashMap<>();
		
		while (!Q.isEmpty())
		{
			long elapsedTime = System.currentTimeMillis() - startTime;
			if (elapsedTime > 60 * 1000)
				throw new TimeoutException("Exceeded 60 seconds");
			
			State n = Q.poll();
			double nCost = n.getChainCost(vehicle.costPerKm());
			
			if (!C.containsKey(n)
				||
				(C.containsKey(n) &&
					nCost < C.get(n)))
			{
				C.put(n, nCost);
				
				Set<State> derivedStates = computeDerivedStates(n, vehicle);
				
				for (State derivedState: derivedStates)
				{
					if (derivedState.isFinalState())
					{
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
