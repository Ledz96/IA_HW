package template;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import template.Centralized.CentralizedSolver;
import template.Centralized.CentralizedPlan;
import template.Centralized.Solution;

import java.io.File;
import java.util.*;

@SuppressWarnings("unused")
public class BaselineAuctionTemplate implements AuctionBehavior
{
	private Long setupTimeout;
	private Long planTimeout;
	private Long bidTimeout;
	
	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private List<Vehicle> vehicles;
	private Random random;
	
	private Set<Task> currentTaskSet;
	private Solution currentSolution;
	private Solution tempNewSolution;
	
	private Long marginalCost;
	private Long totalRevenue = 0L;
	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) // timeout-setup
	{
		LogistSettings ls = null;
		try
		{
			ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
		}
		catch (Exception exc)
		{
			System.out.println("There was a problem loading the configuration file.");
		}
		
		this.setupTimeout = ls.get(LogistSettings.TimeoutKey.SETUP);
		this.planTimeout = ls.get(LogistSettings.TimeoutKey.PLAN);
		this.bidTimeout = ls.get(LogistSettings.TimeoutKey.BID);
		
		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicles = agent.vehicles();
		this.random = new Random(agent.id());
//		this.random = new Random();
		
		currentTaskSet = new HashSet<>();
		
		List<CentralizedPlan> centralizedPlanList = new ArrayList<>();
		vehicles.forEach(vehicle -> {
			centralizedPlanList.add(new CentralizedPlan(vehicle, new ArrayList<>()));
		});

		currentSolution = new Solution(centralizedPlanList);
	}

	@Override
	public void auctionResult(Task task, int winner, Long[] bids) // timeout-bid
	{
		if (winner == agent.id())
		{
			currentTaskSet.add(task);
			currentSolution = tempNewSolution;
			totalRevenue += marginalCost; // TODO true if bid == marginal cost, evaluate for other strategies
		}
	}
	
	int marginalZero = 0;
	
	@Override
	public Long askPrice(Task task) // timeout-bid
	{
		long startTime = System.currentTimeMillis();
		
		Set<Task> taskSet = new HashSet<>(currentTaskSet);
		taskSet.add(task);
		
		Solution tempNewSLSSolution = CentralizedSolver.slsSearch(vehicles, taskSet, bidTimeout, startTime, random);
		Solution tempNewDeterministicSolution = CentralizedSolver.addTaskAndSearch(currentSolution, task);
		
		tempNewSolution = List.of(tempNewSLSSolution, tempNewDeterministicSolution).stream().min(Comparator.comparingLong(Solution::computeCost)).get();

		marginalCost = (tempNewSolution.computeCost() - currentSolution.computeCost());
		marginalZero += (marginalCost <= 0 ? 1 : 0);
//		System.out.printf("currentSolution: %d%n", currentSolution.computeCost());
//		System.out.printf("tempNewSolution: %d%n", tempNewSolution.computeCost());
//		System.out.printf("marginalZero: %d%n", marginalZero);
		return marginalCost;
		
		
		
//		// TODO seed initial plan with currentSolution?
//		// TODO nope, do both (seeded and not seeded) and compare, take the best
//
//		// !!! best solution is always seededSolver, as it does not contain the new task!!!
//
//		bidTimeout = 5 * 1000L;
//
//		if (currentTaskSet.isEmpty())
//		{
//			ExecutorService threadPool = Executors.newFixedThreadPool(1);
//			CentralizedSolver unseededSolver = new CentralizedSolver(vehicles, taskSet, random);
//			Future<Solution> unseededFuture = threadPool.submit(unseededSolver);
//
//			try
//			{
//				threadPool.awaitTermination(bidTimeout - (System.currentTimeMillis() - startTime), TimeUnit.MILLISECONDS);
//			}
//			catch (InterruptedException e)
//			{
//				e.printStackTrace();
//			}
//
//			Solution unseededSolution = null;
//			if (unseededFuture.isDone())
//			{
//				// TODO keep this branch only if CentralizedSolver has a time/iteration bound again
//				try
//				{
//					unseededSolution = unseededFuture.get();
//				}
//				catch (Exception e)
//				{
//					e.printStackTrace();
//				}
//			}
//			else
//			{
//				unseededSolution = unseededSolver.getLastSolution();
//			}
//
//			tempNewSolution = unseededSolution;
//		}
//		else
//		{
//			ExecutorService threadPool = Executors.newFixedThreadPool(2);
//			CentralizedSolver unseededSolver = new CentralizedSolver(vehicles, taskSet, random);
//			CentralizedSolver seededSolver = new CentralizedSolver(currentSolution, random);
//
//			Future<Solution> unseededFuture = threadPool.submit(unseededSolver);
//			Future<Solution> seededFuture = threadPool.submit(seededSolver);
//
//			try
//			{
//				threadPool.awaitTermination(bidTimeout - (System.currentTimeMillis() - startTime), TimeUnit.MILLISECONDS);
//			}
//			catch (InterruptedException e)
//			{
//				e.printStackTrace();
//			}
//
//			Solution unseededSolution = null;
//			Solution seededSolution = null;
//
//			if (unseededFuture.isDone())
//			{
//				// TODO keep this branch only if CentralizedSolver has a time/iteration bound again
//				try
//				{
//					unseededSolution = unseededFuture.get();
//				}
//				catch (Exception e)
//				{
//					e.printStackTrace();
//				}
//			}
//			else
//			{
//				unseededSolution = unseededSolver.getLastSolution();
//			}
//
//			if (seededFuture.isDone())
//			{
//				// TODO keep this branch only if CentralizedSolver has a time/iteration bound again
//				try
//				{
//					seededSolution = seededFuture.get();
//				}
//				catch (Exception e)
//				{
//					e.printStackTrace();
//				}
//			}
//			else
//			{
//				seededSolution = seededSolver.getLastSolution();
//			}
//
//			assert unseededSolution != null;
//			assert seededSolution != null;
//
//			tempNewSolution = Collections.min(List.of(unseededSolution, seededSolution),
//			                                  Comparator.comparingLong(Solution::computeCost));
//			// TODO !!! best solution is always seededSolver, as it does not contain the new task!!!
//		}
//
//		System.out.printf("tempNewSolution: %s%n", tempNewSolution);
//
//		marginalCost = tempNewSolution.computeCost() - currentSolution.computeCost();
//		return marginalCost;
	}
	
	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) // timeout-plan
	{
		long startTime = System.currentTimeMillis();
		Solution finalSolution = CentralizedSolver.slsSearch(vehicles, tasks, planTimeout, startTime, random);
		System.out.printf("finalSolution cost: %s%n", finalSolution.computeCost());
		
		List<Plan> planList = finalSolution.getPlanList();
//		for (Plan plan: planList)
//		{
//			System.out.printf("plan: %s%n", plan);
//		}
		return planList;
	}
}
