package template;

import logist.plan.Plan;
import logist.task.Task;
import logist.topology.Topology;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class CentralizedPlan
{
	private final Topology.City initialCity;
	private final List<CentralizedAction> actionList;
	
	public Topology.City getInitialCity()
	{
		return initialCity;
	}
	
	public List<CentralizedAction> getActionList()
	{
		return actionList;
	}
	
	public CentralizedPlan(Topology.City initialCity, List<CentralizedAction> actionList)
	{
		this.initialCity = initialCity;
		// TODO check actionList validity? (deliver after pickup, capacity, ...)
		this.actionList = actionList;
	}
	
	public CentralizedPlan(CentralizedPlan plan)
	{
		this.initialCity = plan.getInitialCity();
		this.actionList = new ArrayList<>(plan.getActionList());
	}
	
	public boolean isEmpty()
	{
		return actionList.isEmpty();
	}
	
	public void pushTask(Task task)
	{
//		actionList.addFirst(new CentralizedAction(CentralizedAction.ActionType.Deliver, task));
//		actionList.addFirst(new CentralizedAction(CentralizedAction.ActionType.PickUp, task));
		actionList.add(0, new CentralizedAction(CentralizedAction.ActionType.Deliver, task));
		actionList.add(0, new CentralizedAction(CentralizedAction.ActionType.PickUp, task));
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
	
	public Task popTask(Random random)
	{
		List<CentralizedAction> pickupStream = actionList.stream().filter(CentralizedAction::isPickup).collect(Collectors.toList());
		Task randomPickupTask = pickupStream.stream().skip(random.nextInt(pickupStream.size())).findFirst().get().getTask();

		assert actionList.remove(new CentralizedAction(CentralizedAction.ActionType.PickUp, randomPickupTask));
		assert actionList.remove(new CentralizedAction(CentralizedAction.ActionType.Deliver, randomPickupTask));

		return randomPickupTask;
	}
	
	public Plan toPlan()
	{
		Plan plan = new Plan(initialCity);
		Topology.City city = initialCity;
		for (CentralizedAction action: actionList)
			city = action.addToPlan(plan, city);
		return plan;
	}
	
	@Override
	public String toString()
	{
		return "CentralizedPlan{" +
			"initialCity=" + initialCity +
			", actionList=" + actionList +
			'}';
	}
}
