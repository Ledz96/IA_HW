package template;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Solution
{
	private final List<Vehicle> vehicleList;
	private final List<CentralizedPlan> centralizedPlanList;
	private final List<Plan> planList;
	
	public List<Vehicle> getVehicleList()
	{
		return vehicleList;
	}
	
	public List<CentralizedPlan> getCentralizedPlanList()
	{
		return centralizedPlanList;
	}
	
	public List<Plan> getPlanList()
	{
		return planList;
	}
	
	public Solution(List<Vehicle> vehicleList, List<CentralizedPlan> centralizedPlanList)
	{
		this.vehicleList = vehicleList;
		this.centralizedPlanList = centralizedPlanList;
		this.planList = centralizedPlanList.stream().map(CentralizedPlan::toPlan).collect(Collectors.toList());
	}
	
	public double computeCost()
	{
		return Helper.zip(planList, vehicleList)
			.map(pair -> pair._2().costPerKm() * pair._1().totalDistance())
			.reduce(Double::sum).get();
	}
	
	public Set<Solution> chooseNeighbors(Random random)
	{
		Set<Solution> neighbors = new HashSet<>();
		
		List<Integer> nonEmptyIndexes = Helper.enumerate(centralizedPlanList)
			.filter(pair -> !pair._2.isEmpty())
			.map(Pair::_1)
			.collect(Collectors.toList());
		
		int randomIndex = nonEmptyIndexes.stream()
			.skip(new Double(random.nextDouble() * nonEmptyIndexes.size()).longValue())
			.findFirst().get();
		
		// TOCHECK here we assume that all vehicle can pickup any task provided the vehicle's initial capacity
		IntStream.range(0, centralizedPlanList.size())
			.filter(j -> j != randomIndex)
			.forEach(j -> {
				List<CentralizedPlan> newCentralizedPlanList = centralizedPlanList.stream()
					.map(CentralizedPlan::new)
					.collect(Collectors.toList());

				Task task = newCentralizedPlanList.get(randomIndex).popTask();
				newCentralizedPlanList.get(j).pushTask(task);

				neighbors.add(new Solution(vehicleList, newCentralizedPlanList));
			});
		
		// TODO add shuffled
		// TODO idea: first shuffle pickups, for each permutation loop on all interleaving positions and get all
		//            possible delivers according to the capacity of the vehicle
		
		CentralizedPlan plan = centralizedPlanList.get(randomIndex);
//		List<CentralizedAction> pickupActionList = plan.getActionList().stream()
//			.filter(CentralizedAction::isPickup)
//			.collect(Collectors.toList());
		Set<CentralizedAction> pickupActionList = plan.getActionList().stream()
			.filter(CentralizedAction::isPickup)
			.collect(Collectors.toSet());
		
//		for (LinkedList<CentralizedAction> permutedPickupActionList: Helper.permutations(pickupActionList))

		for (LinkedList<CentralizedAction> permutedPickupActionList: new Permutations<CentralizedAction>().get(pickupActionList))
		{
			List<LinkedList<CentralizedAction>> partialPlanList = new LinkedList<>();
			partialPlanList.add(permutedPickupActionList);

			for (int pos = 1; pos < 2*pickupActionList.size(); pos++)
			{
				List<LinkedList<CentralizedAction>> newPlanList = new LinkedList<>();

				for (LinkedList<CentralizedAction> partialPlan : partialPlanList)
				{
					// Get list of carried tasks (tasks we have picked up but haven't delivered yet)
					
					Set<Task> carriedTasks = new HashSet<>();
					// Add picked up tasks
					carriedTasks.addAll(partialPlan.stream()
						                    .limit(pos)
						                    .filter(CentralizedAction::isPickup)
						                    .map(CentralizedAction::getTask)
						                    .collect(Collectors.toSet()));
					// Remove delivered tasks
					carriedTasks.removeAll(partialPlan.stream()
						                       .limit(pos)
						                       .filter(CentralizedAction::isDeliver)
						                       .map(CentralizedAction::getTask)
						                       .collect(Collectors.toSet()));

					// Discard plan if incomplete or impossible according to vehicle's capacity
					if (carriedTasks.stream().map(task -> task.weight).reduce(0, Integer::sum) >
						vehicleList.get(randomIndex).capacity())
					{
						continue;
					}

					if (!(pos >= partialPlan.size())) {
						newPlanList.add(partialPlan);
					}
					
					for (Task task: carriedTasks)
					{
						LinkedList<CentralizedAction> newPartialPlan = new LinkedList<>(partialPlan);
						newPartialPlan.add(pos, new CentralizedAction(CentralizedAction.ActionType.Deliver, task));
						newPlanList.add(newPartialPlan);
					}
				}

				partialPlanList = newPlanList;
			}

			neighbors.addAll(partialPlanList.stream().map(finalPlan -> {
				List<CentralizedPlan> newCentralizedPlanList = new ArrayList<>(centralizedPlanList);
				centralizedPlanList.set(randomIndex,
				                        new CentralizedPlan(centralizedPlanList.get(randomIndex).getInitialCity(), finalPlan));
				return new Solution(vehicleList, newCentralizedPlanList);
			}).collect(Collectors.toSet()));
		}

		neighbors.stream().forEach(sol -> {
				sol.getCentralizedPlanList().stream().forEach(centPlan -> {
					System.out.println(centPlan.getActionList());
				});
		});

		return neighbors;
	}
}
