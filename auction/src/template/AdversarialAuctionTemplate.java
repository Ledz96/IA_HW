package template;

import logist.LogistPlatform;
import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.simulation.VehicleImpl;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import template.Centralized.*;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class AdversarialAuctionTemplate implements AuctionBehavior
{
	private Long setupTimeout;
	private Long planTimeout;
	private Long bidTimeout;

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private List<Vehicle> myVehicles;
	private final Map<Integer, List<Vehicle>> vehiclesMap = new HashMap<>();
	private Random random;
	
	private final Map<Integer, Set<Task>> currentTaskSetMap = new HashMap<>();
	private final Map<Integer, Solution> currentSolutionMap = new HashMap<>();
	private final Map<Integer, Solution> tempNewSolutionMap = new HashMap<>();

	private Long lastMarginalCost;
	private Long totalRevenue = 0L;

	// TODO magic number = 0.71

	private void initAgentMaps(int id, List<Vehicle> vehicleList)
	{
		vehiclesMap.put(id, vehicleList);

		currentTaskSetMap.put(id, new HashSet<>());

		List<CentralizedPlan> centralizedPlanList = new ArrayList<>();
		vehiclesMap.get(id).forEach(vehicle -> centralizedPlanList.add(new CentralizedPlan(vehicle, new ArrayList<>())));
		currentSolutionMap.put(id, new Solution(centralizedPlanList));

		tempNewSolutionMap.put(id, new Solution(centralizedPlanList));
	}

	private void initAgentMaps(int id)
	{
		int maxCapacity = myVehicles.stream().map(Vehicle::capacity).max(Integer::compareTo).get();
		int minCostPerKm = myVehicles.stream().map(Vehicle::costPerKm).min(Integer::compareTo).get();
		Set<Topology.City> cities = new HashSet<>(topology.cities());
		
		List<Vehicle> newVehicleList = new ArrayList<>();
		for (Vehicle vehicle : myVehicles)
		{
//			Topology.City city = cities.stream().skip((int) (random.nextDouble() * cities.size())).findFirst().get();
//			cities.remove(city);
//
//			newVehicleList.add(new VehicleImpl(vehicle.id(), vehicle.name(), maxCapacity, minCostPerKm, city, (long) vehicle.speed(), vehicle.color()).getInfo());
			
			newVehicleList.add(new VehicleImpl(vehicle.id(), vehicle.name(), vehicle.capacity(), vehicle.costPerKm(), vehicle.homeCity(), (long) vehicle.speed(), vehicle.color()).getInfo());
		}

		initAgentMaps(id, newVehicleList);
	}

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) // timeout-setup
	{
		LogistSettings ls = LogistPlatform.getSettings();
		this.setupTimeout = ls.get(LogistSettings.TimeoutKey.SETUP);
		this.planTimeout = ls.get(LogistSettings.TimeoutKey.PLAN);
		this.bidTimeout = ls.get(LogistSettings.TimeoutKey.BID);

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.myVehicles = agent.vehicles();
		this.random = new Random(agent.id());

		initAgentMaps(agent.id(), agent.vehicles());
	}

	private Solution findBestNewSolution(int id, Solution currentSolution, Task task, long timeout, long startTime)
	{
		// TODO use deterministic only if there are too many tasks or adversaries
		
		Set<Task> newTaskSet = new HashSet<>(currentTaskSetMap.get(id));
		newTaskSet.add(task);
		
		Solution tempNewDeterministicSolution = CentralizedSolver.addTaskAndSearch(currentSolutionMap.get(id), task);
		Solution tempNewSLSSolution = CentralizedSolver.slsSearch(vehiclesMap.get(id), newTaskSet, bidTimeout, System.currentTimeMillis(), random);
		return List.of(tempNewSLSSolution, tempNewDeterministicSolution).stream()
			.min(Comparator.comparingLong(Solution::computeCost)).get();
	}
	
	@Override
	public void auctionResult(Task task, int winner, Long[] bids) // timeout-bid
	{
		long startTime = System.currentTimeMillis();
		
		System.out.printf("task.id == %s%n", task.id);
		System.out.printf("winner == %s%n", winner);
		
		if (!currentTaskSetMap.containsKey(winner))
		{
			Solution bestAdvSolution = null;
			long bestAdvSolutionDist = Long.MAX_VALUE;
			
			long dio = System.currentTimeMillis();
			
			for (int it = 0; it < 50; it++)
			{
				// Init so that the mapping of cities to the rival's vehicles is randomized
				initAgentMaps(winner);
				
				Solution advSolution = findBestNewSolution(winner, currentSolutionMap.get(winner), task, bidTimeout, startTime);
				long advSolutionDist = Math.abs(advSolution.computeCost() - bids[winner]);
				
				if (advSolutionDist < bestAdvSolutionDist)
				{
					bestAdvSolution = advSolution;
					bestAdvSolutionDist = advSolutionDist;
				}
			}
			
			System.out.printf("dio: %s%n", System.currentTimeMillis() - dio);
			
			tempNewSolutionMap.put(winner, bestAdvSolution);
		}
		
		currentTaskSetMap.get(winner).add(task);
		
		currentSolutionMap.put(winner, tempNewSolutionMap.get(winner));
		
		if (winner == agent.id())
			totalRevenue += bids[agent.id()];
	}

	@Override
	public Long askPrice(Task task) // timeout-bid
	{
		long startTime = System.currentTimeMillis();
		long searchTime = bidTimeout / (2 * currentTaskSetMap.size());

		// Compute temp solution for everyone

		for (Map.Entry<Integer, Set<Task>> entry : currentTaskSetMap.entrySet())
		{
			tempNewSolutionMap.put(entry.getKey(),
			                       findBestNewSolution(entry.getKey(), currentSolutionMap.get(entry.getKey()), task, bidTimeout, startTime));
		}

		Map<Integer, Long> marginalCostMap = new HashMap<>();
		for (Map.Entry<Integer, Solution> entry : tempNewSolutionMap.entrySet())
			marginalCostMap.put(entry.getKey(), entry.getValue().computeCost() - currentSolutionMap.get(entry.getKey()).computeCost());

		lastMarginalCost = marginalCostMap.get(agent.id());
		
		Optional<Long> bestAdvMarginalCost = marginalCostMap.entrySet().stream()
			.filter(entry -> entry.getKey() != agent.id())
			.map(Map.Entry::getValue)
			.min(Comparator.comparingLong(Long::longValue));
		
		if (bestAdvMarginalCost.isPresent())
		{
			System.out.printf("[Adv] lastMarginalCost == %s%n", lastMarginalCost);
			System.out.printf("[Adv] bestAdvMarginalCost == %s%n", bestAdvMarginalCost.get());
			System.out.printf("[Adv] bid == %s%n", Math.max(lastMarginalCost + 1, bestAdvMarginalCost.get() - 10));
			
			return Math.max(lastMarginalCost + 1, bestAdvMarginalCost.get() - 10);
		}
		else
		{
			System.out.printf("[Adv] lastMarginalCost == %s%n", lastMarginalCost);
			System.out.printf("[Adv] bid == %s%n", lastMarginalCost * 2);
			
			return lastMarginalCost * 2;
		}
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) // timeout-plan
	{
		long startTime = System.currentTimeMillis();

		if (currentSolutionMap.get(agent.id()).computeCost() == 0) // Solution is empty
		{
			System.out.printf("[Adv] empty solution%n");
			return currentSolutionMap.get(agent.id()).getPlanList();
		}

		Solution finalSolution = CentralizedSolver.slsSearch(currentSolutionMap.get(agent.id()), planTimeout, startTime, random);

		List<CentralizedPlan> finalCentralizedPlanList = finalSolution.getCentralizedPlanList().stream().map(centralizedPlan -> {
			List<CentralizedAction> newCentralizedActionList = new LinkedList<>();
			for (CentralizedAction centralizedAction : centralizedPlan.getActionList())
			{
				newCentralizedActionList.add(
					new CentralizedAction(centralizedAction.getActionType(),
					                      tasks.stream().filter(task -> task.id == centralizedAction.getTask().id)
						                      .findFirst().get()));
			}
			return new CentralizedPlan(centralizedPlan.getVehicle(), newCentralizedActionList);
		}).collect(Collectors.toList());

		Solution adaptedFinalSolution = new Solution(finalCentralizedPlanList);

		System.out.printf("[Adv] adaptedFinalSolution cost: %d%n", adaptedFinalSolution.computeCost());
		System.out.printf("[Adv] totalRevenue: %d%n", totalRevenue);
		System.out.printf("[Adv] gain: %d%n", totalRevenue - adaptedFinalSolution.computeCost());

		return adaptedFinalSolution.getPlanList();
	}
}
