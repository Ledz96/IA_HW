package template;

import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.Random;

public class DummyReactiveTemplate implements ReactiveBehavior
{
	private int numActions;
	private Agent myAgent;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent)
	{
		this.numActions = 0;
		this.myAgent = agent;
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask)
	{
		Action action;

		if (availableTask == null)
		{
			City currentCity = vehicle.getCurrentCity();
			action = new Move(currentCity.randomNeighbor(new Random()));
		}
		else
		{
			action = new Pickup(availableTask);
		}
		
		if (numActions >= 1)
		{
			System.out.printf("[Dummy] avg profit = %f (%d actions)%n",
			                  myAgent.getTotalProfit() / (double) numActions,
			                  myAgent.getTotalProfit());
		}
		numActions++;
		
		return action;
	}
}
