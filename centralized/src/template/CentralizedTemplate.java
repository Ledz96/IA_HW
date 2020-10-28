package template;

//the list of imports

import java.io.File;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import logist.LogistSettings;

import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 */
@SuppressWarnings("unused")
public class CentralizedTemplate implements CentralizedBehavior
{
	enum Algorithm { naive, SLS };
	
	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private long timeout_setup;
	private long timeout_plan;
	
	private Algorithm algorithm;
	private double epsilon;
	
	private final int N_ITER = 10000;
	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent)
	{
		// this code is used to get the timeouts
		LogistSettings ls = null;
		try
		{
			ls = Parsers.parseSettings("config" + File.separator + "settings_default.xml");
		}
		catch (Exception exc)
		{
			System.out.println("There was a problem loading the configuration file.");
		}
		
		// the setup method cannot last more than timeout_setup milliseconds
		timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
		// the plan method cannot execute more than timeout_plan milliseconds
		timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
		
		// Get algorithm to use
		String algorithmName = agent.readProperty("algorithm", String.class, "naive");
		algorithm = Algorithm.valueOf(algorithmName);
		
		// TODO rename abandonProbability?
		epsilon = agent.readProperty("epsilon", Double.class, 0.5);
		
		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
	}
	
	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks)
	{
		long time_start = System.currentTimeMillis();
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
		
		List<Plan> plans = new ArrayList<>();
		
		if (algorithm == Algorithm.naive)
		{
			plans.add(naivePlan(vehicles.get(0), tasks));
			while (plans.size() < vehicles.size())
				plans.add(Plan.EMPTY);
		}
		else // SLS
		{
			plans = slsPlan(vehicles, tasks).getPlanList();
		}
		
		long time_end = System.currentTimeMillis();
		long duration = time_end - time_start;
		System.out.printf("[%s] The plan was generated in %d milliseconds.%n", algorithm, duration);
		
		return plans;
	}
	
	private Plan naivePlan(Vehicle vehicle, TaskSet tasks)
	{
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);
		
		for (Task task : tasks)
		{
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
			{
				plan.appendMove(city);
			}
			
			plan.appendPickup(task);
			
			// move: pickup location => delivery location
			for (City city : task.path())
			{
				plan.appendMove(city);
			}
			
			plan.appendDelivery(task);
			
			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}
	
	private Solution selectInitialSolution(List<Vehicle> vehicleList, TaskSet tasks)
	{
		// Give all tasks to the first vehicle, to be picked up and delivered sequentially
		// TOCHECK here we assume that all vehicle can pickup any task provided the vehicle's initial capacity
		
		List<CentralizedPlan> centralizedPlanList= new ArrayList<>();
		
		centralizedPlanList.add(new CentralizedPlan(
			vehicleList.get(0).homeCity(),
			tasks.stream()
				.flatMap(task -> Stream.of(
					new CentralizedAction(CentralizedAction.ActionType.PickUp, task),
					new CentralizedAction(CentralizedAction.ActionType.Deliver, task)))
				.collect(Collectors.toCollection(LinkedList::new))));
		
		vehicleList.stream().skip(1)
			.forEach(vehicle -> centralizedPlanList.add(new CentralizedPlan(vehicle.homeCity(), new LinkedList<>())));
		
		return new Solution(vehicleList, centralizedPlanList);
	}
	
	private Solution localChoice(Set<Solution> solutionSet)
	{
		assert solutionSet.stream()
			.map(Solution::getCentralizedPlanList)
			.anyMatch(planList -> planList.stream()
				.skip(1)
				.anyMatch(Predicate.not(CentralizedPlan::isEmpty)));
		
//		for (Solution solution: solutionSet)
//		{
//			System.out.println(solution);
//			System.out.println(solution.computeCost());
//		}
		
		return solutionSet.stream()
			.min(Comparator.comparingDouble(Solution::computeCost))
			.get();
	}
	
	private Solution slsPlan(List<Vehicle> vehicleList, TaskSet tasks)
	{
		System.out.println(tasks);
		
		Random random = new Random(0);
		Solution solution = selectInitialSolution(vehicleList, tasks);
		
		for (int i = 0; i < N_ITER; i++)
		{
			if (random.nextFloat() < epsilon)
				solution = localChoice(solution.chooseNeighbors(random));
		}
		
		return solution;
	}
}
