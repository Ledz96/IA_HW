package template;

import logist.plan.Plan;
import logist.task.Task;
import logist.topology.Topology;

public final class CentralizedAction
{
	public enum ActionType
	{
		PickUp, Deliver
	}
	
	private final ActionType actionType;
	private final Task task;
	
//	public ActionType getActionType()
//	{
//		return actionType;
//	}
	
	public Task getTask()
	{
		return task;
	}
	
	public CentralizedAction(ActionType actionType, Task task)
	{
		this.actionType = actionType;
		this.task = task;
	}
	
	public boolean isPickup()
	{
		return actionType == ActionType.PickUp;
	}
	
	public boolean isDeliver()
	{
		return actionType == ActionType.Deliver;
	}
	
	// TODO move outside?
	/**
	 * Append logist actions needed for the CentralizedAction to be performed to the plan
	 * @param plan
	 * @param currentCity
	 * @return current City after the action
	 */
	public Topology.City addToPlan(Plan plan, Topology.City currentCity)
	{
		if (actionType == ActionType.PickUp)
		{
			currentCity.pathTo(task.pickupCity).forEach(plan::appendMove);
			plan.appendPickup(task);
			return task.pickupCity;
		}
		else // Deliver
		{
			currentCity.pathTo(task.deliveryCity).forEach(plan::appendMove);
			plan.appendDelivery(task);
			return task.deliveryCity;
		}
	}
	
	@Override
	public String toString()
	{
		return "CentralizedAction{" +
			"actionType=" + actionType +
			", task=" + task +
			'}';
	}
}
