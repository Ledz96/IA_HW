package template;

//the list of imports

import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import template.CentralizedStuff.Centralized;
import template.CentralizedStuff.CentralizedPlan;
import template.CentralizedStuff.Solution;

import java.util.*;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 *
 */
@SuppressWarnings("unused")
public class BaselineAuctionTemplate implements AuctionBehavior
{
	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private List<Vehicle> vehicles;
	private Random random;
	
	Set<Task> taskSet;
	Solution currentSolution;
	Solution tempNewSolution;
	
	Long marginalCost;
	Long totalRevenue;
	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent)
	{
		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicles = agent.vehicles();
		this.random = new Random(agent.id());
		
		taskSet = new HashSet<>();
		
		List<CentralizedPlan> centralizedPlanList = new ArrayList<>();
		vehicles.forEach(vehicle -> {
			centralizedPlanList.add(new CentralizedPlan(vehicle, new ArrayList<>()));
		});
		
		currentSolution = new Solution(centralizedPlanList);
	}

	@Override
	public void auctionResult(Task task, int winner, Long[] bids)
	{
		if (winner == agent.id())
		{
			taskSet.add(task);
			currentSolution = tempNewSolution;
			totalRevenue += marginalCost; // TODO true if bid == marginal cost, evaluate for other strategies
		}
	}
	
	@Override
	public Long askPrice(Task task)
	{
		System.out.printf("task id: %d%n", task.id);
		
		Set<Task> newTaskSet = new HashSet<>(taskSet);
		newTaskSet.add(task);
		
		// TODO seed initial plan with currentPlan?
		// TODO nope, do both (seeded and not seeded) and compare, take the best
		tempNewSolution = Centralized.slsPlan(vehicles, newTaskSet, System.currentTimeMillis());
		
		marginalCost = (long) (tempNewSolution.computeCost() - currentSolution.computeCost());
		return marginalCost;
	}
	
	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks)
	{
		Solution finalSolution = Centralized.slsPlan(vehicles, tasks, System.currentTimeMillis());
		
		List<Plan> planList = finalSolution.getPlanList();
		for (Plan plan: planList)
		{
			System.out.printf("plan: %s%n", plan);
		}
		return planList;
	}
}
