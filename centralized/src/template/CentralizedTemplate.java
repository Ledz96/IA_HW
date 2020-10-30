package template;

//the list of imports

import java.io.File;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.source.tree.Tree;
import logist.LogistSettings;

import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 */
@SuppressWarnings("unused")
public class CentralizedTemplate implements CentralizedBehavior
{
	enum Algorithm { naive, SLS };
	
	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private long timeout_setup;
	private long timeout_plan;
	
	private Algorithm algorithm;
	private double exploreProb;
	
	private int N_ITER;
	private int STUCK_LIMIT;
	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent)
	{
		// this code is used to get the timeouts
		LogistSettings ls = null;
		try
		{
			ls = Parsers.parseSettings("config" + File.separator + "settings_default.xml");
		}
		catch (Exception exc)
		{
			System.out.println("There was a problem loading the configuration file.");
		}
		
		// the setup method cannot last more than timeout_setup milliseconds
		timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
		// the plan method cannot execute more than timeout_plan milliseconds
		timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
		
		// Get algorithm to use
		String algorithmName = agent.readProperty("algorithm", String.class, "naive");
		algorithm = Algorithm.valueOf(algorithmName);
		
		exploreProb = agent.readProperty("explore-prob", Double.class, 0.5);
		N_ITER = agent.readProperty("iterations", Integer.class, 10000);
		STUCK_LIMIT = agent.readProperty("stuck-limit", Integer.class, 1000);
		
		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
	}
	
	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks)
	{
		long time_start = System.currentTimeMillis();
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
		
		List<Plan> plans = new ArrayList<>();
		
		if (algorithm == Algorithm.naive)
		{
			plans.add(naivePlan(vehicles.get(0), tasks));
			while (plans.size() < vehicles.size())
				plans.add(Plan.EMPTY);
		}
		else // SLS
		{
			plans = slsPlan(vehicles, tasks, time_start).getPlanList();
		}
		
		long time_end = System.currentTimeMillis();
		long duration = time_end - time_start;
		System.out.printf("[%s] The plan was generated in %d milliseconds.%n", algorithm, duration);
		
		System.out.printf("Total cost: %f%n", Helper.zip(vehicles, plans)
			.map(pair -> pair._1.costPerKm() * pair._2.totalDistance())
			.reduce(0., Double::sum));
		
		return plans;
	}
	
	private Plan naivePlan(Vehicle vehicle, TaskSet tasks)
	{
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);
		
		for (Task task : tasks)
		{
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
			{
				plan.appendMove(city);
			}
			
			plan.appendPickup(task);
			
			// move: pickup location => delivery location
			for (City city : task.path())
			{
				plan.appendMove(city);
			}
			
			plan.appendDelivery(task);
			
			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}
	
	private Solution selectNaiveInitialSolution(List<Vehicle> vehicleList, TaskSet tasks)
	{
		// Give all tasks to the first vehicle, to be picked up and delivered sequentially
		// TOCHECK here we assume that all vehicle can pickup any task provided the vehicle's initial capacity
		
		List<CentralizedPlan> centralizedPlanList= new ArrayList<>();
		
		centralizedPlanList.add(new CentralizedPlan(
			vehicleList.get(0),
			tasks.stream()
				.flatMap(task -> Stream.of(
					new CentralizedAction(CentralizedAction.ActionType.PickUp, task),
					new CentralizedAction(CentralizedAction.ActionType.Deliver, task)))
				.collect(Collectors.toCollection(LinkedList::new))));
		
		vehicleList.stream().skip(1)
			.forEach(vehicle -> centralizedPlanList.add(new CentralizedPlan(vehicle, new LinkedList<>())));
		
		return new Solution(centralizedPlanList);
	}
	
	private Solution selectOptimizedInitialSolution(List<Vehicle> vehicleList, TaskSet tasks, Random random)
	{
//		List<Task> orderedTasks = new ArrayList<>(tasks);
//		orderedTasks.sort(Collections.reverseOrder(Comparator.comparingInt(o -> o.weight)));
		
		// For each task to be picked up, get the nearest vehicle. If the vehicle can carry the task assign the latter
		// to the former, otherwise add a Deliver action to the vehicle for the task with the delivery City which is
		// closest to the task. Iterate getting the nearest vehicle.
		
		// FIXME probably the bug is due to associating the wrong plan to a vehicle
		
		Map<Vehicle, CentralizedPlan> vehiclePlanMap = new HashMap<>();
		vehicleList.forEach(vehicle -> vehiclePlanMap.put(vehicle, new CentralizedPlan(vehicle, new ArrayList<>())));
		
		List<Task> taskList = new ArrayList<>(tasks);
		Collections.shuffle(taskList, random);
		
		for (Task randomTask : taskList)
		{
			Comparator<Pair<Vehicle, Double>> comparator = Comparator.comparingDouble(Pair::_2);
			PriorityQueue<Pair<Vehicle, Double>> vehicleDistanceQueue = new PriorityQueue<>(comparator);
			vehicleDistanceQueue.addAll(vehiclePlanMap.entrySet().stream()
				                            .map(entry -> new Pair<>(entry.getKey(),
				                                                    randomTask.pickupCity.distanceTo(entry.getValue().getCurrentCity())))
				                            .collect(Collectors.toList()));
			
			while (true)
			{
				Pair<Vehicle, Double> vehicleDistancePair = vehicleDistanceQueue.poll();
				assert vehicleDistancePair != null;
				
				if (vehiclePlanMap
						.get(vehicleDistancePair._1)
						.addAction(new CentralizedAction(CentralizedAction.ActionType.PickUp, randomTask)))
				{
					break;
				}
				else
				{
					// Get carried task with closest delivery city
					Task deliverTask = vehiclePlanMap.get(vehicleDistancePair._1).getCarriedTasks().stream()
						.min(Comparator.comparingDouble(task -> task.deliveryCity.distanceTo(randomTask.pickupCity)))
						.get();
					
					vehiclePlanMap.get(vehicleDistancePair._1)
						.addAction(new CentralizedAction(CentralizedAction.ActionType.Deliver, deliverTask));
					
					vehicleDistanceQueue
						.add(new Pair<>(vehicleDistancePair._1,
						                vehiclePlanMap.get(vehicleDistancePair._1)
							                .getCurrentCity().distanceTo(randomTask.pickupCity)));
				}
			}
		}
		
		// Fill incomplete plans
		
		vehiclePlanMap.values().stream()
			.filter(Predicate.not(CentralizedPlan::isComplete))
			.forEach(plan -> plan.getCarriedTasks()
				.forEach(task -> plan.addAction(new CentralizedAction(CentralizedAction.ActionType.Deliver, task))));
		
		return new Solution(new ArrayList<>(vehiclePlanMap.values()));
	}
	
//		BiFunction<Vehicle, List<CentralizedAction>, Boolean> feasible =
//			(Vehicle vehicle, List<CentralizedAction> centralizedActionList) -> {
//			int partial = 0;
//			for (CentralizedAction action: centralizedActionList)
//			{
//				partial += (action.isPickup() ? +1 : -1) * action.getTask().weight;
//				if (partial > vehicle.capacity())
//					return false;
//			}
//			return true;
//		};
	
	private Solution localChoice(Set<Solution> solutionSet)
	{
		assert solutionSet.stream()
			.map(Solution::getCentralizedPlanList)
			.anyMatch(planList -> planList.stream()
				.skip(1)
				.anyMatch(Predicate.not(CentralizedPlan::isEmpty)));
		
		return solutionSet.stream()
			.min(Comparator.comparingDouble(Solution::computeCost))
			.get();
	}
	
	private Solution slsPlan(List<Vehicle> vehicleList, TaskSet tasks, long startTime)
	{
		Random random = new Random(0);
//		Solution solution = selectNaiveInitialSolution(vehicleList, tasks);
		Solution solution = selectOptimizedInitialSolution(vehicleList, tasks, random);
		Solution localMinimum = solution;
		
		long maxIterationTime = 0;
		int iter = 0;
		int stuck = 0;
		
		long currentTime = System.currentTimeMillis();
		while (iter < N_ITER && currentTime - startTime + maxIterationTime < timeout_plan)
		{
			
			
			
			if (random.nextDouble() < exploreProb)
			{
//				System.out.printf("Solution cost: %f%n", solution.computeCost());
//				System.out.printf("stuck: %d%n", stuck);

				solution = localChoice(solution.chooseRandomNeighbors(1000, random));

				if (solution.computeCost() < localMinimum.computeCost())
				{
					localMinimum = solution;
					stuck = 0;
				}
				else
				{
					stuck++;
				}
			}
			
//			Set<Solution> neighbors = solution.chooseRandomNeighbors(1000, random);
//			if (random.nextDouble() < exploreProb)
//			{
//				solution = localChoice(neighbors);
//			}
//			else
//			{
//				solution = neighbors.stream().skip((long) (random.nextDouble() * neighbors.size())).findFirst().get();
//			}
//
//			if (solution.computeCost() < localMinimum.computeCost())
//			{
//				localMinimum = solution;
//				stuck = 0;
//			}
//			else
//			{
//				stuck++;
//			}
			
			
			
			if (stuck >= STUCK_LIMIT)
			{
				System.out.println("reset");
//				solution = selectNaiveInitialSolution(vehicleList, tasks);
				solution = selectOptimizedInitialSolution(vehicleList, tasks, random);
				stuck = 0;
			}
			
			maxIterationTime = Math.max(maxIterationTime, System.currentTimeMillis() - currentTime);
			iter++;
		}
		
		return solution.computeCost() < localMinimum.computeCost() ? solution : localMinimum;
	}
}
