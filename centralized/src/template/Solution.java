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
	private final List<CentralizedPlan> centralizedPlanList;
	
//	private Map<PartialState, Double> stateCostMap;
	
	public List<CentralizedPlan> getCentralizedPlanList()
	{
		return centralizedPlanList;
	}
	
	public List<Plan> getPlanList()
	{
		return centralizedPlanList.stream().map(CentralizedPlan::toPlan).collect(Collectors.toList());
	}
	
	public Solution(List<CentralizedPlan> centralizedPlanList)
	{
		assert centralizedPlanList.stream().allMatch(CentralizedPlan::isComplete);
		
		this.centralizedPlanList = centralizedPlanList;
//		this.vehicleList = centralizedPlanList.stream().map(CentralizedPlan::getVehicle).collect(Collectors.toList());
//		this.stateCostMap = new HashMap<>();
	}
	
	public double computeCost()
	{
		return centralizedPlanList.stream()
			.map(centralizedPlan -> centralizedPlan.getVehicle().costPerKm() * centralizedPlan.toPlan().totalDistance())
			.reduce(Double::sum).get();
	}
	
	public int getChangeVehicleNeighbors(Set<Solution> neighbors, PopStrategy popStrategy, Random random)
	{
		List<Integer> nonEmptyIndexes = Helper.enumerate(centralizedPlanList)
			.filter(pair -> !pair._2.isEmpty())
			.map(Pair::_1)
			.collect(Collectors.toList());
		
		int randomIndex = nonEmptyIndexes.stream()
			.skip(new Double(random.nextDouble() * nonEmptyIndexes.size()).longValue())
			.findFirst().get();
		
		// TOCHECK here we assume that all vehicles can pickup any task provided the vehicle's initial capacity
		IntStream.range(0, centralizedPlanList.size())
			.filter(j -> j != randomIndex)
			.forEach(j -> {
				List<CentralizedPlan> newCentralizedPlanList = centralizedPlanList.stream()
					.map(CentralizedPlan::new)
					.collect(Collectors.toList());
				
				Task task = popStrategy == PopStrategy.first ?
					newCentralizedPlanList.get(randomIndex).popFirstTask()
					:
					newCentralizedPlanList.get(randomIndex).popRandomTask(random);
				
				newCentralizedPlanList.get(j).pushTask(task);
				neighbors.add(new Solution(newCentralizedPlanList));
			});
		
		return randomIndex;
	}
	
	/** Compute all possible permutations, not feasible */
	public Set<Solution> chooseAllNeighbors(int shuffleCount, PopStrategy popStrategy, Random random)
	{
		Set<Solution> neighbors = new HashSet<>();
		int randomIndex = getChangeVehicleNeighbors(neighbors, popStrategy, random);

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
//		Set<LinkedList<CentralizedAction>> shuffledPlanSet = new HashSet<>();
//		for (int i = 0; i < shuffleCount; i++)
		{
//			if (shuffledPlanSet.size() == Helper.factorial(pickupActionList.size()))
//				break;

			LinkedList<CentralizedAction> shuffledPickupActionList = new LinkedList<>(pickupActionList);
			Collections.shuffle(shuffledPickupActionList, random);

//			if (shuffledPlanSet.contains(shuffledPickupActionList))
//				continue;
//			shuffledPlanSet.add(shuffledPickupActionList);

			List<LinkedList<CentralizedAction>> partialPlanList = new LinkedList<>();
			partialPlanList.add(shuffledPickupActionList);

			/////

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
						centralizedPlanList.get(randomIndex).getVehicle().capacity())
					{
						it.remove();
						continue;
					}

//					CentralizedAction lastAction = partialPlan.get(pos - 1);
//					PartialState partialState = new PartialState(
//						lastAction.isDeliver() ? lastAction.getTask().deliveryCity : lastAction.getTask().pickupCity,
//						carriedTasks, deliveredTasks);
//
//					double partialCost = centralizedPlanList.get(randomIndex).getVehicle().costPerKm() *
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
						newPlanList.add(newPartialPlan);
					}
				}
			});

			neighbors.addAll(partialPlanList.stream().map(finalPlan -> {
				List<CentralizedPlan> newCentralizedPlanList = new ArrayList<>(centralizedPlanList);
				centralizedPlanList.set(randomIndex, new CentralizedPlan(centralizedPlanList.get(randomIndex).getVehicle(), finalPlan));
				return new Solution(newCentralizedPlanList);
			}).collect(Collectors.toSet()));
		}

//		System.out.printf("Neighbors: %s%n", neighbors);

		neighbors.remove(this);
		return neighbors;
	}
	
	/** Compute shuffleCount possible random permutations */
	public Set<Solution> chooseRandomNeighbors(int shuffleCount, PopStrategy popStrategy, Random random)
	{
		Set<Solution> neighbors = new HashSet<>();
		int randomIndex = getChangeVehicleNeighbors(neighbors, popStrategy, random);
		
		// Shuffle
		
		CentralizedPlan plan = centralizedPlanList.get(randomIndex);
		
		Set<CentralizedAction> possibleActions = plan.getActionList().stream()
			.filter(CentralizedAction::isPickup)
			.collect(Collectors.toSet());
		
		int shuffleIt = 0;
		while (shuffleIt < shuffleCount)
		{
			List<CentralizedAction> possibleActionSet = new ArrayList<>(possibleActions);
			List<CentralizedAction> newPlan = new ArrayList<>(plan.getLength());
			int newPlanWeight = 0;
			boolean feasible = true;
			
			for (int newPlanIdx = 0; newPlanIdx < plan.getLength(); newPlanIdx++)
			{
				CentralizedAction newAction = possibleActionSet.remove((int) (random.nextDouble() * possibleActionSet.size()));
				newPlan.add(newAction);
				
				if (newAction.isPickup())
				{
					possibleActionSet.add(new CentralizedAction(CentralizedAction.ActionType.Deliver, newAction.getTask()));
					newPlanWeight += newAction.getTask().weight;
				}
				else
				{
					newPlanWeight -= newAction.getTask().weight;
				}
				
				// Check plan feasibility
				if (newPlanWeight > centralizedPlanList.get(randomIndex).getVehicle().capacity())
				{
					feasible = false;
					break;
				}
			}
			
			if (feasible)
			{
				List<CentralizedPlan> newCentralizedPlanList = new ArrayList<>(centralizedPlanList);
				newCentralizedPlanList.set(randomIndex, new CentralizedPlan(centralizedPlanList.get(randomIndex).getVehicle(), newPlan));
				neighbors.add(new Solution(newCentralizedPlanList));
				shuffleIt++;
			}
		}
		
		return neighbors;
	}
	
	private Set<CentralizedPlan> moveLeft(Vehicle vehicle, List<CentralizedAction> actionList, int pos)
	{
		if (pos <= 0)
			return new HashSet<>();
		
		CentralizedAction posAction = actionList.get(pos);
		CentralizedAction prevAction = actionList.get(pos - 1);
		
		List<CentralizedAction> newActionList = new ArrayList<>(actionList);
		newActionList.set(pos, prevAction);
		newActionList.set(pos - 1, posAction);
		
		if (posAction.isPickup())
		{
			try
			{
				new CentralizedPlan(vehicle, newActionList);
			}
			catch (ExceededCapacityException ex)
			{
				return new HashSet<>();
			}
		}
		
		if (posAction.isDeliver() &&
			prevAction.isPickup() &&
			prevAction.getTask() == posAction.getTask())
			return new HashSet<>();
		
		Set<CentralizedPlan> ret = moveLeft(vehicle, newActionList, pos - 1);
		ret.add(new CentralizedPlan(vehicle, newActionList));
		return ret;
	}
	
	private Set<CentralizedPlan> moveRight(Vehicle vehicle, List<CentralizedAction> actionList, int pos)
	{
		if (pos >= actionList.size() - 1)
			return new HashSet<>();
		
		CentralizedAction posAction = actionList.get(pos);
		CentralizedAction nextAction = actionList.get(pos + 1);
		
		if (posAction.isPickup() &&
			nextAction.isDeliver() &&
			nextAction.getTask() == posAction.getTask())
			return new HashSet<>();
		
		List<CentralizedAction> newActionList = new ArrayList<>(actionList);
		newActionList.set(pos, nextAction);
		newActionList.set(pos + 1, posAction);
		
		if (posAction.isDeliver())
		{
			try
			{
				new CentralizedPlan(vehicle, newActionList);
			}
			catch (ExceededCapacityException ex)
			{
				return new HashSet<>();
			}
		}
		
		Set<CentralizedPlan> ret = moveRight(vehicle, newActionList, pos + 1);
		ret.add(new CentralizedPlan(vehicle, newActionList));
		return ret;
	}
	
	public Set<Solution> chooseSwapNeighbors(PopStrategy popStrategy, Random random)
	{
		Set<Solution> neighbors = new HashSet<>();
		int randomIndex = getChangeVehicleNeighbors(neighbors, popStrategy, random);

		for (int pos = 0; pos < centralizedPlanList.get(randomIndex).getLength(); pos++)
		{
			Stream.concat(moveLeft(centralizedPlanList.get(randomIndex).getVehicle(),
			                       centralizedPlanList.get(randomIndex).getActionList(),
			                       pos).stream(),
			              moveRight(centralizedPlanList.get(randomIndex).getVehicle(),
			                        centralizedPlanList.get(randomIndex).getActionList(),
			                        pos).stream())
				.forEach(plan -> {
					List<CentralizedPlan> newCentralizedPlanList = new ArrayList<>(centralizedPlanList);
					newCentralizedPlanList.set(randomIndex, plan);
					neighbors.add(new Solution(newCentralizedPlanList));
				});
		}
		
		return neighbors;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Solution solution = (Solution) o;
		return Objects.equals(centralizedPlanList, solution.centralizedPlanList);
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(centralizedPlanList);
	}
	
	@Override
	public String toString()
	{
		return "Solution{" +
			"centralizedPlanList=" + centralizedPlanList +
			'}';
	}
}
