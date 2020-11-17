package template.CentralizedStuff;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Centralized
{
	private static final long timeout_plan = 60 * 1000;
//	private static final int N_ITER = 10000;
	private static final int N_ITER = 1000;
	private static final int STUCK_LIMIT = 1000;
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
		
		// Must keep plans sorted according to their vehicle
		return new Solution(new ArrayList<>(vehicleList.stream()
			                                    .map(vehiclePlanMap::get).collect(Collectors.toList())));
	}
	
	private static Solution localChoice(Set<Solution> solutionSet)
	{
		assert solutionSet.stream()
			.map(Solution::getCentralizedPlanList)
			.anyMatch(planList -> planList.stream()
				.anyMatch(Predicate.not(CentralizedPlan::isEmpty)));
		
		return solutionSet.stream()
			.min(Comparator.comparingDouble(Solution::computeCost))
			.get();
	}
	
	public static Solution slsPlan(List<Vehicle> vehicleList, Set<Task> tasks, long startTime)
	{
		Random random = new Random();
		
		Solution solution = selectOptimizedInitialSolution(vehicleList, tasks, random);
		
		Solution localMinimum = solution;
		
		long maxIterationTime = 0; // higher bound on next iteration time span
		int iter = 0;
		int stuck = 0;
		
		long beforeIterationTime = System.currentTimeMillis();
		while (iter < N_ITER)
		{
			if (System.currentTimeMillis() - startTime + maxIterationTime > timeout_plan)
			{
				System.out.println("Reached timeout, returning best solution found");
				break;
			}
			
			if (random.nextDouble() < exploreProb)
			{
				solution = localChoice(solution.chooseSwapNeighbors(random));
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
//				System.out.println("reset");
				solution = selectOptimizedInitialSolution(vehicleList, tasks, random);
				
				stuck = 0;
			}
			
			maxIterationTime = Math.max(maxIterationTime, System.currentTimeMillis() - beforeIterationTime);
			iter++;
		}
		
		return solution.computeCost() < localMinimum.computeCost() ? solution : localMinimum;
	}
}
