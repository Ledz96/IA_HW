package template.Centralized;

import logist.simulation.Vehicle;
import logist.task.Task;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CentralizedSolver
{
	// TODO pick definitive values
//	private static final int N_ITER = 10000;
	private static final int N_ITER = 20000;
//	private static final int STUCK_LIMIT = 1000;
	private static final int STUCK_LIMIT = 2000;
	private static final double exploreProb = 0.5;
	
	private static Solution selectOptimizedInitialSolution(List<Vehicle> vehicleList, Set<Task> tasks, Random random)
	{
		// For each task to be picked up, get the nearest vehicle. If the vehicle can carry the task assign the latter
		// to the former, otherwise add a Deliver action to the vehicle for the task with the delivery City which is
		// closest to the task. Iterate getting the nearest vehicle.
		
		Map<Vehicle, CentralizedPlan> vehiclePlanMap = new HashMap<>();
		vehicleList.forEach(vehicle -> vehiclePlanMap.put(vehicle, new CentralizedPlan(vehicle, new ArrayList<>())));
		
		List<Task> taskList = new ArrayList<>(tasks);
		Collections.shuffle(taskList, random);
		
		for (Task randomTask : taskList)
		{
			Comparator<Pair<Vehicle, Double>> comparator = Comparator.comparingDouble(Pair::_2);
			PriorityQueue<Pair<Vehicle, Double>> vehicleDistanceQueue = new PriorityQueue<>(comparator);
			vehicleDistanceQueue
				.addAll(vehiclePlanMap.entrySet().stream()
							.filter(entry -> entry.getKey().capacity() >= randomTask.weight)
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
//					Task deliverTask = vehiclePlanMap.get(vehicleDistancePair._1).getCarriedTasks().stream()
//						.min(Comparator.comparingDouble(task -> task.deliveryCity.distanceTo(randomTask.pickupCity)))
//						.get();
					
					Optional<Task> deliverTask = vehiclePlanMap.get(vehicleDistancePair._1).getCarriedTasks().stream()
						.min(Comparator.comparingDouble(task -> task.deliveryCity.distanceTo(randomTask.pickupCity)));
					
					if (deliverTask.isPresent())
					{
						vehiclePlanMap.get(vehicleDistancePair._1)
							.addAction(new CentralizedAction(CentralizedAction.ActionType.Deliver, deliverTask.get()));
						
						vehicleDistanceQueue
							.add(new Pair<>(vehicleDistancePair._1,
							                vehiclePlanMap.get(vehicleDistancePair._1)
								                .getCurrentCity().distanceTo(randomTask.pickupCity)));
					}
				}
			}
		}
		
		// Fill incomplete plans
		
		vehiclePlanMap.values().stream()
			.filter(Predicate.not(CentralizedPlan::isComplete))
			.forEach(plan -> plan.getCarriedTasks()
				.forEach(task -> plan.addAction(new CentralizedAction(CentralizedAction.ActionType.Deliver, task))));
		
		Solution solution = new Solution(new ArrayList<>(vehicleList.stream()
			                                                 .map(vehiclePlanMap::get)
			                                                 .collect(Collectors.toList())));
		
		// Must keep plans sorted according to their vehicle
		return solution;
	}
	
	private static Solution localChoice(Set<Solution> solutionSet, Solution defaultSolution)
	{
		if (solutionSet.isEmpty())
			return defaultSolution;
		
		assert solutionSet.stream()
			.map(Solution::getCentralizedPlanList)
			.anyMatch(planList -> planList.stream()
				.anyMatch(Predicate.not(CentralizedPlan::isEmpty)));
		
		return solutionSet.stream()
			.min(Comparator.comparingDouble(Solution::computeCost))
			.get();
	}
	
	public static Solution slsSearch(Solution initialSolution,
	                                 long timeout,
	                                 long startTime,
	                                 Random random)
	{
		Solution solution = initialSolution;
		Solution localMinimum = solution;
		
		Set<CentralizedPlan> visitedNeighbors = new HashSet<>();
		
		long maxIterationTime = 0; // higher bound on next iteration time span
		int iter = 0;
		int stuck = 0;
		
		long beforeIterationTime = System.currentTimeMillis();
		while (iter < N_ITER)
		{
			if (System.currentTimeMillis() - startTime + 1.1 * maxIterationTime > timeout)
			{
				System.out.println("Reached timeout, returning best solution found");
				break;
			}
			
			if (random.nextDouble() < exploreProb)
			{
				solution = localChoice(solution.chooseSwapNeighbors(random, visitedNeighbors), solution);
			}
			
			if (solution.computeCost() < localMinimum.computeCost())
			{
				localMinimum = solution;
				stuck = 0;
			}
			else
			{
				stuck++;
			}
			
			if (stuck >= STUCK_LIMIT)
			{
				solution = initialSolution;
				stuck = 0;
				
				visitedNeighbors = new HashSet<>();
			}
			
			maxIterationTime = Math.max(maxIterationTime, System.currentTimeMillis() - beforeIterationTime);
			iter++;
		}
		
		return solution.computeCost() < localMinimum.computeCost() ? solution : localMinimum;
	}
	
	public static Solution slsSearch(List<Vehicle> vehicleList,
	                                 Set<Task> tasks,
	                                 long timeout,
	                                 long startTime,
	                                 Random random)
	{
		Solution optimizedInitialSolution = selectOptimizedInitialSolution(vehicleList, tasks, random);
		return slsSearch(optimizedInitialSolution, timeout, startTime, random);
	}
	
	public static Solution addTaskAndSearch(Solution solution, Task task, long timeout)
	{
		long startTime = System.currentTimeMillis();
		boolean expiredTime = false;
		
		Set<Solution> neighbors = new HashSet<>();
		
		for (int planIdx = 0; planIdx < solution.getCentralizedPlanList().size(); planIdx++)
		{
			CentralizedPlan plan = solution.getCentralizedPlanList().get(planIdx);
			
			for (int insertionIndex: plan.getInsertPositions(task))
			{
				if ((
						(planIdx == 0 && insertionIndex > 0) ||
						(planIdx > 0))
					&& System.currentTimeMillis() > startTime + timeout)
				{
					expiredTime = true;
					break;
				}
				
				CentralizedPlan newPlan = new CentralizedPlan(plan);
				newPlan.insertTask(task, insertionIndex);
				
				List<CentralizedPlan> newCentralizedPlanList = new ArrayList<>(solution.getCentralizedPlanList());
				newCentralizedPlanList.set(planIdx, newPlan);
				
				Solution newSolution = new Solution(newCentralizedPlanList);
				neighbors.add(newSolution);
				
				neighbors.addAll(newSolution.computeSwapNeighbors(planIdx));
			}
			
			if (expiredTime)
				break;
		}
		
		Optional<Solution> ret = neighbors.stream().min(Comparator.comparingLong(Solution::computeCost));
		if (ret.isEmpty())
		{
			try
			{
				String exMsg = String.format("%s%n%s%n",
				                             "Insufficient time for calculating any solution!",
				                             "Minimum tested timeout: 500ms");
				throw new Exception(exMsg);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				System.exit(1);
			}
		}
		
		return ret.get();
	}
}
