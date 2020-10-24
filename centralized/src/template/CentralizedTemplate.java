package template;

//the list of imports

import java.io.File;
import java.util.*;
import java.util.stream.IntStream;

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
			plans = slsPlan(vehicles, tasks);
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
	
	private List<Plan> selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks)
	{
		// TODO ...
	}
	
	private Set<List<Plan>> chooseNeighbors()
	{
		// TODO ...
	}
	
	private double computePlanListCost(List<Plan> planList, List<Vehicle> vehicles)
	{
		return IntStream.range(0, planList.size())
			.mapToObj(i -> new Pair<Plan, Vehicle>(planList.get(i), vehicles.get(i)))
			.map(pair -> pair._2().costPerKm() * pair._1().totalDistance())
			.reduce(Double::sum).get();
	}
	
	private List<Plan> localChoice(Set<List<Plan>> planListSet, List<Vehicle> vehicles)
	{
		return planListSet.stream()
			.map(planList -> new Pair<List<Plan>, Double>(planList, computePlanListCost(planList, vehicles)))
			.min(Comparator.comparingDouble(Pair::_2))
			.get()._1();
	}
	
	private List<Plan> slsPlan(List<Vehicle> vehicles, TaskSet tasks)
	{
		Random random = new Random(0);
		List<Plan> solution = selectInitialSolution(vehicles, tasks);
		
		boolean condition = false;
		while(!condition)
		{
			if (random.nextFloat() < epsilon)
				solution = localChoice(chooseNeighbors(), vehicles);
			// TODO ...
		}
	}
}
