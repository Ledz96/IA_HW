package template;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Solution
{
	private final List<Vehicle> vehicleList;
	private final List<CentralizedPlan> centralizedPlanList;
	private final List<Plan> planList;
	
	private Map<PartialState, Double> stateCostMap;
	
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
		
		this.stateCostMap = new HashMap<>();
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
		
//		System.out.printf("centralizedPlanList: %s%n", centralizedPlanList);
		
		List<Integer> nonEmptyIndexes = Helper.enumerate(centralizedPlanList)
			.filter(pair -> !pair._2.isEmpty())
			.map(Pair::_1)
			.collect(Collectors.toList());
		
//		System.out.printf("nonEmptyIndexes: %s%n", nonEmptyIndexes);
		
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

//				Task task = newCentralizedPlanList.get(randomIndex).popTask();
				Task task = newCentralizedPlanList.get(randomIndex).popTask(random);
				newCentralizedPlanList.get(j).pushTask(task);
				neighbors.add(new Solution(vehicleList, newCentralizedPlanList));
			});
		
		// Add shuffled
		// Idea: first shuffle pickups, for each permutation loop on all interleaving positions and get all
		//       possible delivers according to the capacity of the vehicle
		
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
			
			IntStream.range(1, 2 * pickupActionList.size()).forEachOrdered(pos -> {
				
				ListIterator<LinkedList<CentralizedAction>> it = partialPlanList.listIterator();
				while (it.hasNext())
				{
					LinkedList<CentralizedAction> partialPlan = it.next();
					
					// Get list of carried tasks (tasks we have picked up but haven't delivered yet)
					
					Set<Task> carriedTasks = new HashSet<>();
					// Add picked up tasks
					carriedTasks.addAll(partialPlan.stream()
						                    .limit(pos)
						                    .filter(CentralizedAction::isPickup)
						                    .map(CentralizedAction::getTask)
						                    .collect(Collectors.toSet()));
					
					Set<Task> deliveredTasks = partialPlan.stream()
						.limit(pos)
						.filter(CentralizedAction::isDeliver)
						.map(CentralizedAction::getTask)
						.collect(Collectors.toSet());
					
					// Remove delivered tasks
					carriedTasks.removeAll(deliveredTasks);
					
					// Discard plan if impossible according to vehicle's capacity and avoid adding derived plans
					if (carriedTasks.stream().map(task -> task.weight).reduce(0, Integer::sum) >
						vehicleList.get(randomIndex).capacity())
					{
						it.remove();
						continue;
					}
					
//					CentralizedAction lastAction = partialPlan.get(pos - 1);
//					PartialState partialState = new PartialState(
//						lastAction.isDeliver() ? lastAction.getTask().deliveryCity : lastAction.getTask().pickupCity,
//						carriedTasks, deliveredTasks);
//
//					double partialCost = vehicleList.get(randomIndex).costPerKm() *
//						new CentralizedPlan(centralizedPlanList.get(randomIndex).getInitialCity(),
//					                                         partialPlan)
//							.toPlan().totalDistance();
//
//					if (stateCostMap.containsKey(partialState))
//					{
//						if (stateCostMap.get(partialState) < partialCost)
//						{
//							it.remove();
//							continue;
//						}
//					}
//					stateCostMap.put(partialState, partialCost);
					
					// Discard plan if incomplete
					if (pos >= partialPlan.size())
					{
						it.remove();
					}
					
					for (Task task: carriedTasks)
					{
						LinkedList<CentralizedAction> newPartialPlan = new LinkedList<>(partialPlan);
						newPartialPlan.add(pos, new CentralizedAction(CentralizedAction.ActionType.Deliver, task));
						it.add(newPartialPlan);
					}
				}
			});
			
			neighbors.addAll(partialPlanList.stream().map(finalPlan -> {
				List<CentralizedPlan> newCentralizedPlanList = new ArrayList<>(centralizedPlanList);
				centralizedPlanList.set(randomIndex,
				                        new CentralizedPlan(centralizedPlanList.get(randomIndex).getInitialCity(), finalPlan));
				return new Solution(vehicleList, newCentralizedPlanList);
			}).collect(Collectors.toSet()));
		}
		
//		System.out.printf("Neighbors: %s%n", neighbors);
		
		neighbors.remove(this);
		return neighbors;
	}
	
	@Override
	public String toString()
	{
		return "Solution{" +
			", centralizedPlanList=" + centralizedPlanList +
			'}';
	}
}
