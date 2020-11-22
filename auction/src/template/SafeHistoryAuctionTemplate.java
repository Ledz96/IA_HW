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
//import template.Centralized.*;
//
//import java.io.File;
//import java.util.*;
//import java.util.stream.Collectors;
//
//@SuppressWarnings("unused")
//public class SafeHistoryAuctionTemplate implements AuctionBehavior
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
//	private Long lastMarginalCost;
//	private Long totalRevenue = 0L;
//
//	private Long lossMargin = 3L;
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
//	private final int minBidHistoryWindowSize = 5;
//	private final LinkedList<Long> minBidHistoryWindow = new LinkedList<>();
//	private long minHistoryBid = Long.MAX_VALUE;
//
//	private void updateHistory(Long[] bids)
//	{
//		List<Long> bidList = new ArrayList<>(Arrays.asList(bids));
//		bidList.remove(agent.id());
//		long minBid = bidList.stream().mapToLong(Long::longValue).min().getAsLong();
//
//		if (minBidHistoryWindow.size() >= minBidHistoryWindowSize)
//			minBidHistoryWindow.removeLast();
//		minBidHistoryWindow.addFirst(minBid);
//
//		minHistoryBid = Math.min(minHistoryBid, minBid);
//	}
//
//	@Override
//	public void auctionResult(Task task, int winner, Long[] bids) // timeout-bid
//	{
//		updateHistory(bids);
//
//		if (winner == agent.id())
//		{
//			currentTaskSet.add(task);
//			currentSolution = tempNewSolution;
//			totalRevenue += bids[agent.id()];
//		}
//	}
//
//	@Override
//	public Long askPrice(Task task) // timeout-bid
//	{
//		long startTime = System.currentTimeMillis();
//
//		Set<Task> taskSet = new HashSet<>(currentTaskSet);
//		taskSet.add(task);
//
//		Solution tempNewSolutionSLS = CentralizedSolver.slsSearch(vehicles, taskSet, bidTimeout, startTime, random);
//		Solution tempNewSolutionNeighbors = CentralizedSolver.addTaskAndSearch(currentSolution, task);
//
//		tempNewSolution = List.of(tempNewSolutionSLS, tempNewSolutionNeighbors).stream().min(Comparator.comparingLong(Solution::computeCost)).get();
////		System.out.printf("[Safe] tempNewSolution: %s%n", tempNewSolution);
//
//		lastMarginalCost = (tempNewSolution.computeCost() - currentSolution.computeCost());
//
//		if (minBidHistoryWindow.size() < 1)
//		{
//			return lastMarginalCost * 2;
//			// TODO change based on destination city connectivity
//		}
//
//		// Try to gain, but stay safe
//		long targetBid;
//
//		if (lastMarginalCost <= 0)
//		{
//			// 1
////			targetBid = minHistoryBid;
//
//			// 2
//			targetBid = minBidHistoryWindow.stream().mapToLong(Long::longValue).min().getAsLong();
//
//			// 3
////			targetBid = (long) minBidHistoryWindow.stream().mapToLong(Long::longValue).average().getAsDouble();
//		}
//		else
//		{
//			// 2
//			targetBid = minBidHistoryWindow.stream().mapToLong(Long::longValue).min().getAsLong();
//
//			// 3
////			targetBid = (long) minBidHistoryWindow.stream().mapToLong(Long::longValue).average().getAsDouble();
//
//			// TODO ...
//		}
//
//		System.out.printf("[Safe] lastMarginalCost == %s%n", lastMarginalCost);
//
//		long loss = (long) Math.max(0, lossMargin + random.nextGaussian());
//
//		System.out.printf("[Safe] bid == %s%n", Math.max(lastMarginalCost + 1, targetBid - loss));
//		return Math.max(lastMarginalCost + 1, targetBid - loss);
//	}
//
//	@Override
//	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) // timeout-plan
//	{
//		long startTime = System.currentTimeMillis();
//
//		if (currentSolution.computeCost() == 0) // Solution is empty
//		{
//			System.out.printf("[Safe] empty solution%n");
//			return currentSolution.getPlanList();
//		}
//
//		Solution finalSolution = CentralizedSolver.slsSearch(currentSolution, planTimeout, startTime, random);
//
//		List<CentralizedPlan> finalCentralizedPlanList = finalSolution.getCentralizedPlanList().stream().map(centralizedPlan -> {
//			List<CentralizedAction> newCentralizedActionList = new LinkedList<>();
//			for (CentralizedAction centralizedAction : centralizedPlan.getActionList())
//			{
//				newCentralizedActionList.add(
//					new CentralizedAction(centralizedAction.getActionType(),
//					                      tasks.stream().filter(task -> task.id == centralizedAction.getTask().id)
//						                      .findFirst().get()));
//			}
//			return new CentralizedPlan(centralizedPlan.getVehicle(), newCentralizedActionList);
//		}).collect(Collectors.toList());
//
//		Solution adaptedFinalSolution = new Solution(finalCentralizedPlanList);
//
//		System.out.printf("[Safe] adaptedFinalSolution cost: %d%n", adaptedFinalSolution.computeCost());
//		System.out.printf("[Safe] totalRevenue: %d%n", totalRevenue);
//		System.out.printf("[Safe] gain: %d%n", totalRevenue - adaptedFinalSolution.computeCost());
//
//		List<Plan> planList = adaptedFinalSolution.getPlanList();
////		for (Plan plan: planList)
////		{
////			System.out.printf("plan: %s%n", plan);
////		}
//		return planList;
//	}
//}
