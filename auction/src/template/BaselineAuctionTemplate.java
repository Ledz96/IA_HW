//package template;
//
//import logist.LogistSettings;
//import logist.agent.Agent;
//import logist.behavior.AuctionBehavior;
//import logist.config.Parsers;
//import logist.plan.Plan;
//import logist.simulation.Vehicle;
//import logist.task.Task;
//import logist.task.TaskDistribution;
//import logist.task.TaskSet;
//import logist.topology.Topology;
//import template.Centralized.CentralizedSolver;
//import template.Centralized.CentralizedPlan;
//import template.Centralized.Solution;
//
//import java.io.File;
//import java.util.*;
//import java.util.stream.Collectors;
//
//@SuppressWarnings("unused")
//public class BaselineAuctionTemplate implements AuctionBehavior
//{
//	private Long setupTimeout;
//	private Long planTimeout;
//	private Long bidTimeout;
//
//	private Topology topology;
//	private TaskDistribution distribution;
//	private Agent agent;
//	private List<Vehicle> vehicles;
//	private Random random;
//
//	private Set<Task> currentTaskSet;
//	private Solution currentSolution;
//	private Solution tempNewSolution;
//
//	private Long marginalCost;
//	private Long totalRevenue = 0L;
//
//	@Override
//	public void setup(Topology topology, TaskDistribution distribution, Agent agent) // timeout-setup
//	{
//		LogistSettings ls = null;
//		try
//		{
//			ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
//		}
//		catch (Exception exc)
//		{
//			System.out.println("There was a problem loading the configuration file.");
//		}
//
//		this.setupTimeout = ls.get(LogistSettings.TimeoutKey.SETUP);
//		this.planTimeout = ls.get(LogistSettings.TimeoutKey.PLAN);
//		this.bidTimeout = ls.get(LogistSettings.TimeoutKey.BID);
//
//		this.topology = topology;
//		this.distribution = distribution;
//		this.agent = agent;
//		this.vehicles = agent.vehicles();
//		this.random = new Random(agent.id());
////		this.random = new Random();
//
//		currentTaskSet = new HashSet<>();
//
//		List<CentralizedPlan> centralizedPlanList = new ArrayList<>();
//		vehicles.forEach(vehicle -> {
//			centralizedPlanList.add(new CentralizedPlan(vehicle, new ArrayList<>()));
//		});
//
//		currentSolution = new Solution(centralizedPlanList);
//	}
//
//	@Override
//	public void auctionResult(Task task, int winner, Long[] bids) // timeout-bid
//	{
//		if (winner == agent.id())
//		{
//			currentTaskSet.add(task);
//			currentSolution = tempNewSolution;
//			totalRevenue += marginalCost; // TODO true if bid == marginal cost, evaluate for other strategies
//		}
//	}
//
//	int marginalZero = 0;
//
//	@Override
//	public Long askPrice(Task task) // timeout-bid
//	{
//		long startTime = System.currentTimeMillis();
//
//		Set<Task> taskSet = new HashSet<>(currentTaskSet);
//		taskSet.add(task);
//
//		Solution tempNewSLSSolution = CentralizedSolver.slsSearch(vehicles, taskSet, bidTimeout, startTime, random);
//		Solution tempNewDeterministicSolution = CentralizedSolver.addTaskAndSearch(currentSolution, task);
//
//		tempNewSolution = List.of(tempNewSLSSolution, tempNewDeterministicSolution).stream().min(Comparator.comparingLong(Solution::computeCost)).get();
//
//		marginalCost = (tempNewSolution.computeCost() - currentSolution.computeCost());
//		marginalZero += (marginalCost <= 0 ? 1 : 0);
////		System.out.printf("currentSolution: %d%n", currentSolution.computeCost());
////		System.out.printf("tempNewSolution: %d%n", tempNewSolution.computeCost());
////		System.out.printf("marginalZero: %d%n", marginalZero);
//		return marginalCost;
//	}
//
//	@Override
//	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) // timeout-plan
//	{
//		long startTime = System.currentTimeMillis();
//		Solution finalSolution = CentralizedSolver.slsSearch(vehicles, tasks, planTimeout, startTime, random);
//		System.out.printf("[Baseline] finalSolution cost: %s%n", finalSolution.computeCost());
//
//		List<Plan> planList = finalSolution.getPlanList();
////		for (Plan plan: planList)
////		{
////			System.out.printf("plan: %s%n", plan);
////		}
//		return planList;
//	}
//}
