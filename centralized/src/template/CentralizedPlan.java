package template;

import logist.plan.Plan;
import logist.task.Task;
import logist.topology.Topology;

import java.util.LinkedList;

public class CentralizedPlan
{
	private final Topology.City initialCity;
	private final LinkedList<CentralizedAction> actionList;
	
	public Topology.City getInitialCity()
	{
		return initialCity;
	}
	
	public LinkedList<CentralizedAction> getActionList()
	{
		return actionList;
	}
	
	public CentralizedPlan(Topology.City initialCity, LinkedList<CentralizedAction> actionList)
	{
		this.initialCity = initialCity;
		// TODO check actionList validity? (deliver after pickup, capacity, ...)
		this.actionList = actionList;
	}
	
	public CentralizedPlan(CentralizedPlan plan)
	{
		this.initialCity = plan.getInitialCity();
		this.actionList = new LinkedList<>(plan.getActionList());
	}
	
	public boolean isEmpty()
	{
		return actionList.isEmpty();
	}
	
	public void pushTask(Task task)
	{
		actionList.addFirst(new CentralizedAction(CentralizedAction.ActionType.Deliver, task));
		actionList.addFirst(new CentralizedAction(CentralizedAction.ActionType.PickUp, task));
	}
	
	public Task popTask()
	{
		CentralizedAction pickupAction = actionList.removeFirst();
		// Assert first action is always a PickUp
		assert pickupAction.isPickup();
		
		Task task = pickupAction.getTask();
		// Remove Deliver action for same task (and assert something is removed!)
		assert actionList.removeFirstOccurrence(new CentralizedAction(CentralizedAction.ActionType.Deliver, task));
		
		return pickupAction.getTask();
	}
	
	public Plan toPlan()
	{
		Plan plan = new Plan(initialCity);
		Topology.City city = initialCity;
		for (CentralizedAction action: actionList)
			city = action.addToPlan(plan, city);
		return plan;
	}
}
