package template;

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
	
	private int residualCapacity;
	
	public Vehicle getVehicle()
	{
		return vehicle;
	}
	
	public List<CentralizedAction> getActionList()
	{
		return actionList;
	}
	
	public int getResidualCapacity()
	{
		return residualCapacity;
	}
	
	public CentralizedPlan(Vehicle vehicle, List<CentralizedAction> actionList) throws RuntimeException
	{
		this.vehicle = vehicle;
		this.actionList = new ArrayList<>();
		this.residualCapacity = vehicle.capacity();
		
		actionList.forEach(action -> {
			if (!addAction(action))
				throw new RuntimeException(String.format("Vehicle capacity exceeded with action: %s", action));
		});
	}
	
	public CentralizedPlan(CentralizedPlan plan) throws RuntimeException
	{
		this(plan.getVehicle(), plan.getActionList());
	}
	
	public boolean addAction(CentralizedAction centralizedAction)
	{
		Task task = centralizedAction.getTask();
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
		if (task.weight > vehicle.capacity())
			return false;
		
		actionList.add(0, new CentralizedAction(CentralizedAction.ActionType.Deliver, task));
		actionList.add(0, new CentralizedAction(CentralizedAction.ActionType.PickUp, task));
		// No change needed to residualCapacity
		return true;
	}
	
//	public Task popTask()
//	{
//		CentralizedAction pickupAction = actionList.removeFirst();
//		// Assert first action is always a PickUp
//		assert pickupAction.isPickup();
//
//		Task task = pickupAction.getTask();
//		// Remove Deliver action for same task (and assert something is removed!)
//		assert actionList.removeFirstOccurrence(new CentralizedAction(CentralizedAction.ActionType.Deliver, task));
//
//		return pickupAction.getTask();
//	}
	
	public Task popRandomTask(Random random)
	{
		List<CentralizedAction> pickupList = actionList.stream().filter(CentralizedAction::isPickup).collect(Collectors.toList());
		Task randomPickupTask = pickupList.stream().skip(random.nextInt(pickupList.size())).findFirst().get().getTask();

		assert actionList.remove(new CentralizedAction(CentralizedAction.ActionType.PickUp, randomPickupTask));
		boolean hadDeliver =  actionList.remove(new CentralizedAction(CentralizedAction.ActionType.Deliver, randomPickupTask));
		
		if (!hadDeliver)
			residualCapacity += randomPickupTask.weight;

		return randomPickupTask;
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
}
