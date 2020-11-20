package template.Centralized;

import logist.task.Task;

import java.util.Objects;

public final class CentralizedAction
{
	public enum ActionType
	{
		PickUp, Deliver
	}
	
	private final ActionType actionType;
	private final Task task;
	
	public ActionType getActionType()
	{
		return actionType;
	}
	
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
	
	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		CentralizedAction that = (CentralizedAction) o;
		return actionType == that.actionType &&
			Objects.equals(task, that.task);
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(actionType, task);
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
