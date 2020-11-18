package template.Centralized;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.topology.Topology;

import java.util.*;
import java.util.stream.Collectors;

public class CentralizedPlan
{
	private final Vehicle vehicle;
	private final List<CentralizedAction> actionList;
	private List<Integer> residuals;
	
	public Vehicle getVehicle()
	{
		return vehicle;
	}
	
	public List<CentralizedAction> getActionList()
	{
		return actionList;
	}
	
	public CentralizedPlan(Vehicle vehicle, List<CentralizedAction> actionList) throws ExceededCapacityException
	{
		this.vehicle = vehicle;
		this.actionList = new ArrayList<>();
		
		this.residuals = new ArrayList<>();
		this.residuals.add(vehicle.capacity());
		
		actionList.forEach(action -> {
			if (!addAction(action))
				throw new ExceededCapacityException(String.format("Action: %s", action));
		});
	}
	
	public CentralizedPlan(CentralizedPlan plan) throws RuntimeException
	{
		this(plan.getVehicle(), plan.getActionList());
		this.residuals = new ArrayList<>(plan.residuals);
	}
	
	public boolean addAction(CentralizedAction centralizedAction)
	{
		Task task = centralizedAction.getTask();
		int residualCapacity = residuals.get(residuals.size() - 1);
		
		if (centralizedAction.isPickup())
		{
			if (task.weight > residualCapacity)
				return false;
			
			residualCapacity -= task.weight;
		}
		else // Deliver
		{
			if (!getCarriedTasks().contains(centralizedAction.getTask()))
				throw new RuntimeException("Cannot add Deliver action for not picked up task");
			
			residualCapacity += task.weight;
		}
		
		actionList.add(centralizedAction);
		residuals.add(residualCapacity);
		return true;
	}
	
	public Set<Task> getCarriedTasks()
	{
		Set<Task> carriedTaskSet = new HashSet<>();
		
		actionList.forEach(action -> {
			if (action.isPickup())
				carriedTaskSet.add(action.getTask());
			else // Deliver
				carriedTaskSet.remove(action.getTask());
		});
		
		return carriedTaskSet;
	}
	
	public Topology.City getCurrentCity()
	{
		if (isEmpty())
			return vehicle.homeCity();
		
		CentralizedAction lastAction = actionList.get(actionList.size() - 1);
		Task lastTask = lastAction.getTask();
		return lastAction.isPickup() ? lastTask.pickupCity : lastTask.deliveryCity;
	}
	
	public int getLength()
	{
		return actionList.size();
	}
	
	// TODO remove
//	public List<Integer> computeResiduals()
//	{
//		List<Integer> residuals = new ArrayList<>(actionList.size() + 1);
//		int residual = vehicle.capacity();
//
//		residuals.add(residual);
//		for (CentralizedAction action: actionList)
//		{
//			residual += (action.isPickup() ? -1 : 1) * action.getTask().weight;
//			residuals.add(residual);
//		}
//
//		return residuals;
//	}
	
	public List<Integer> getInsertPositions(Task task)
	{
		return Helper.enumerate(residuals)
			.filter(pair -> task.weight <= pair._2)
			.map(Pair::_1)
			.collect(Collectors.toList());
	}
	
	public boolean insertTask(Task task, int index)
	{
		if (task.weight > residuals.get(index))
			return false;
		
		actionList.add(index, new CentralizedAction(CentralizedAction.ActionType.Deliver, task));
		residuals.add(index + 1, residuals.get(index));
		
		actionList.add(index, new CentralizedAction(CentralizedAction.ActionType.PickUp, task));
		residuals.add(index + 1, residuals.get(index) - task.weight);
		
		return true;
	}
	
	public boolean isComplete()
	{
		return getCarriedTasks().isEmpty();
	}
	
	public boolean isEmpty()
	{
		return actionList.isEmpty();
	}
	
	public boolean pushTask(Task task)
	{
		return insertTask(task, actionList.size());
	}
	
	public boolean pushTaskInRandomPosition(Task task, Random random)
	{
		List<Pair<Integer, Integer>> filteredIndexedResiduals = Helper.enumerate(residuals).filter(pair -> task.weight <= pair._2).collect(Collectors.toList());
		int index = filteredIndexedResiduals.get((int) random.nextDouble() * filteredIndexedResiduals.size())._1;
		
		return insertTask(task, index);
	}
	
	private Task popTask(Task task)
	{
		CentralizedAction pickupAction = new CentralizedAction(CentralizedAction.ActionType.PickUp, task);
		CentralizedAction deliverAction = new CentralizedAction(CentralizedAction.ActionType.Deliver, task);
		
		int pickupActionResidualIdx = actionList.indexOf(pickupAction) + 1;
		int deliverActionResidualIdx = actionList.indexOf(deliverAction) + 1;
		
		assert actionList.remove(pickupAction);
		assert actionList.remove(deliverAction);
		
		for (int i = pickupActionResidualIdx; i < deliverActionResidualIdx; i++)
		{
			residuals.set(i, residuals.get(i) + pickupAction.getTask().weight);
		}
		
		residuals.remove(deliverActionResidualIdx);
		residuals.remove(pickupActionResidualIdx);
		
		return task;
	}
	
	public Task popFirstTask()
	{
		assert actionList.get(0).isPickup();
		Task firstTask = actionList.get(0).getTask();
		
		return popTask(firstTask);
	}
	
	public Task popRandomTask(Random random)
	{
		List<CentralizedAction> pickupList = actionList.stream().filter(CentralizedAction::isPickup).collect(Collectors.toList());
		int randomIdx = random.nextInt(pickupList.size());
		CentralizedAction randomPickupAction = pickupList.stream().skip(randomIdx).findFirst().get();
		
		return popTask(randomPickupAction.getTask());
	}
	
	public Plan toPlan() throws RuntimeException
	{
		if (!isComplete())
			throw new RuntimeException("Centralized plan is not complete");
		
		Topology.City initialCity = vehicle.homeCity();
		Plan plan = new Plan(initialCity);
		Topology.City currentCity = initialCity;
		
		for (CentralizedAction action: actionList)
		{
			Task task = action.getTask();
			if (action.isPickup())
			{
				currentCity.pathTo(task.pickupCity).forEach(plan::appendMove);
				plan.appendPickup(task);
				currentCity = task.pickupCity;
			}
			else // Deliver
			{
				currentCity.pathTo(task.deliveryCity).forEach(plan::appendMove);
				plan.appendDelivery(task);
				currentCity = task.deliveryCity;
			}
		}
		return plan;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		CentralizedPlan that = (CentralizedPlan) o;
		return Objects.equals(vehicle, that.vehicle) &&
			Objects.equals(actionList, that.actionList);
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(vehicle, actionList);
	}
	
	@Override
	public String toString()
	{
		return "CentralizedPlan{" +
			"vehicle=" + vehicle +
			", actionList=" + actionList +
			'}';
	}
}
