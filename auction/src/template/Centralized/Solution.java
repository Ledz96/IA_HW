package template.Centralized;

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
	
	public long computeCost()
	{
		return centralizedPlanList.stream()
			.map(centralizedPlan -> centralizedPlan.getVehicle().costPerKm() * centralizedPlan.toPlan().totalDistance())
			.reduce(Double::sum).get().longValue();
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
	
	// Reminder: do not use visitedNeighbors here
	public Set<Solution> computeSwapNeighbors(int planIdx)
	{
		Set<Solution> neighbors = new HashSet<>();
		CentralizedPlan centralizedPlan = centralizedPlanList.get(planIdx);
		
		for (int pos = 0; pos < centralizedPlan.getLength(); pos++)
		{
			Stream.concat(moveLeft(centralizedPlan.getVehicle(), centralizedPlan.getActionList(), pos).stream(),
			              moveRight(centralizedPlan.getVehicle(), centralizedPlan.getActionList(), pos).stream())
				.forEach(plan -> {
					List<CentralizedPlan> newCentralizedPlanList = new ArrayList<>(centralizedPlanList);
					newCentralizedPlanList.set(planIdx, plan);
					
					neighbors.add(new Solution(newCentralizedPlanList));
				});
		}
		
		return neighbors;
	}
	
	private int fillWithChangeVehicleNeighbors(Set<Solution> neighbors, Random random)
//	private Optional<Integer> fillWithChangeVehicleNeighbors(Set<Solution> neighbors, Random random)
	{
		List<Integer> nonEmptyIndexes = Helper.enumerate(centralizedPlanList)
			.filter(pair -> !pair._2.isEmpty())
			.map(Pair::_1)
			.collect(Collectors.toList());
		
		int randomIndex = nonEmptyIndexes.stream()
//		Optional<Integer> randomIndex = nonEmptyIndexes.stream()
			.skip(new Double(random.nextDouble() * nonEmptyIndexes.size()).longValue())
			.findFirst().get();
//			.findFirst();

//		if (randomIndex.isEmpty())
//			return Optional.empty();
		
		IntStream.range(0, centralizedPlanList.size())
			.filter(j -> j != randomIndex)
//			.filter(j -> j != randomIndex.get())
			.forEach(j -> {
				List<CentralizedPlan> newCentralizedPlanList = centralizedPlanList.stream()
					.map(CentralizedPlan::new)
					.collect(Collectors.toList());
				
				// TOEVAL popRandom best
//				Task task = newCentralizedPlanList.get(randomIndex).popFirstTask();
				Task task = newCentralizedPlanList.get(randomIndex).popRandomTask(random);
//				Task task = newCentralizedPlanList.get(randomIndex.get()).popRandomTask(random);
				
				// TOEVAL pushTask best
				boolean taskWasPushed = newCentralizedPlanList.get(j).pushTask(task);
//				newCentralizedPlanList.get(j).pushTaskInRandomPosition(task, random);
				
				// task weight may be greater than vehicle capacity, so the push may not be successful
				if (taskWasPushed)
				{
					// Solution may be empty if one vehicle cannot take any tasks, let's avoid adding it
					Solution newSolution = new Solution(newCentralizedPlanList);
					
					Set<Task> solutionSeenTasks = getCentralizedPlanList().stream()
						.map(CentralizedPlan::getActionList)
						.flatMap(actionList -> actionList.stream().map(CentralizedAction::getTask))
						.collect(Collectors.toSet());
					Set<Task> newSolutionSeenTasks = newSolution.getCentralizedPlanList().stream()
						.map(CentralizedPlan::getActionList)
						.flatMap(actionList -> actionList.stream().map(CentralizedAction::getTask))
						.collect(Collectors.toSet());
					assert newSolutionSeenTasks.equals(solutionSeenTasks);
					
					neighbors.add(newSolution);
				}
			});
		
		return randomIndex;
	}
	
	public Set<Solution> chooseSwapNeighbors(Random random, Set<CentralizedPlan> visitedNeighbors)
	{
		Set<Solution> neighbors = new HashSet<>();
		int randomIndex = fillWithChangeVehicleNeighbors(neighbors, random);
		if (neighbors.isEmpty())
			return neighbors;
		
		for (Solution neighbor : neighbors)
		{
			Set<Task> solutionSeenTasks = getCentralizedPlanList().stream()
				.map(CentralizedPlan::getActionList)
				.flatMap(actionList -> actionList.stream().map(CentralizedAction::getTask))
				.collect(Collectors.toSet());
			Set<Task> neighborSolutionSeenTasks = neighbor.getCentralizedPlanList().stream()
				.map(CentralizedPlan::getActionList)
				.flatMap(actionList -> actionList.stream().map(CentralizedAction::getTask))
				.collect(Collectors.toSet());
			assert neighborSolutionSeenTasks.equals(solutionSeenTasks);
		}
		
		CentralizedPlan randomCentralizedPlan = centralizedPlanList.get(randomIndex);
		// TOEVAL visitedNeighbors best
		if (!visitedNeighbors.contains(randomCentralizedPlan))
//		if (true)
		{
			Set<Solution> swapNeighbors = computeSwapNeighbors(randomIndex);
			
			for (Solution neighbor : swapNeighbors)
			{
				Set<Task> solutionSeenTasks = getCentralizedPlanList().stream()
					.map(CentralizedPlan::getActionList)
					.flatMap(actionList -> actionList.stream().map(CentralizedAction::getTask))
					.collect(Collectors.toSet());
				Set<Task> neighborSolutionSeenTasks = neighbor.getCentralizedPlanList().stream()
					.map(CentralizedPlan::getActionList)
					.flatMap(actionList -> actionList.stream().map(CentralizedAction::getTask))
					.collect(Collectors.toSet());
				assert neighborSolutionSeenTasks.equals(solutionSeenTasks);
			}
			
			neighbors.addAll(swapNeighbors);
			visitedNeighbors.add(randomCentralizedPlan);
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
//		return "Solution{" +
//			"centralizedPlanList=" + centralizedPlanList +
//			'}';
		
		String ret = String.format("Solution {%n");
		for (CentralizedPlan centralizedPlan : centralizedPlanList)
		{
			ret += String.format("%s%n", centralizedPlan.toString());
		}
		ret += "}";
		
		return ret;
	}
}
