package template.CentralizedStuff;

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
	}
	
	public double computeCost()
	{
		return centralizedPlanList.stream()
			.map(centralizedPlan -> centralizedPlan.getVehicle().costPerKm() * centralizedPlan.toPlan().totalDistance())
			.reduce(Double::sum).get();
	}
	
	public int getChangeVehicleNeighbors(Set<Solution> neighbors, Random random)
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
				
				Task task = newCentralizedPlanList.get(randomIndex).popFirstTask();
				
				newCentralizedPlanList.get(j).pushTask(task);
				neighbors.add(new Solution(newCentralizedPlanList));
			});
		
		return randomIndex;
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
	
	public Set<Solution> chooseSwapNeighbors(Random random)
	{
		Set<Solution> neighbors = new HashSet<>();
		int randomIndex = getChangeVehicleNeighbors(neighbors, random);

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
