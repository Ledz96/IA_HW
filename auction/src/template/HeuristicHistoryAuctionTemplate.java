package template;

import logist.LogistPlatform;
import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import template.Centralized.*;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class HeuristicHistoryAuctionTemplate implements AuctionBehavior
{
	// additive advantage factors
	private static final Double ADD_ADV_POS_F = 0.6;
	private static final Double ADD_ADV_NEG_F = 0.2;
	
	// additive disadvantage factors
	private static final Double ADD_DIS_POS_F = 0.6;
	private static final Double ADD_DIS_NEG_F = 0.1;
	
	// multiplicative advantage factors
	private static final Double MUL_ADV_POS_F = 0.2;
	private static final Double MUL_ADV_NEG_F = 0.2;
	
	// multiplicative disadvantage factors
	private static final Double MUL_DIS_POS_F = 0.2;
	private static final Double MUL_DIS_NEG_F = 0.1;
	
	
	private static final Double MUL_F = 1.5;
	
	private enum WeightFlag
	{
		None, Pos, Neg
	}
	
	private static final Long LOSS_MARGIN = 3L;
	
	/////
	
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
	
	private Long lastMarginalCost;
	private Long totalRevenue = 0L;
	
	private Double additivePositiveAttenuatingFactor;
	private Double additiveNegativeAttenuatingFactor;
	private WeightFlag weightFlag;
	
	private Double multiplicativePositiveAttenuatingFactor;
	private Double multiplicativeNegativeAttenuatingFactor;
	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) // timeout-setup
	{
		LogistSettings ls = LogistPlatform.getSettings();
		this.setupTimeout = ls.get(LogistSettings.TimeoutKey.SETUP);
		this.planTimeout = ls.get(LogistSettings.TimeoutKey.PLAN);
		this.bidTimeout = ls.get(LogistSettings.TimeoutKey.BID);
		
		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicles = agent.vehicles();
		this.random = new Random(agent.id());
		
		currentTaskSet = new HashSet<>();
		
		List<CentralizedPlan> centralizedPlanList = new ArrayList<>();
		vehicles.forEach(vehicle -> centralizedPlanList.add(new CentralizedPlan(vehicle, new ArrayList<>())));

		currentSolution = new Solution(centralizedPlanList);
		computeHeuristics();
		
		if (agent.id() == 0) // advantage
		{
			additivePositiveAttenuatingFactor = ADD_ADV_POS_F;
			additiveNegativeAttenuatingFactor = ADD_ADV_NEG_F;
			
			multiplicativePositiveAttenuatingFactor = MUL_ADV_POS_F;
			multiplicativeNegativeAttenuatingFactor = MUL_ADV_NEG_F;
		}
		else // disadvantage
		{
			additivePositiveAttenuatingFactor = ADD_DIS_POS_F;
			additiveNegativeAttenuatingFactor = ADD_DIS_NEG_F;
			
			multiplicativePositiveAttenuatingFactor = MUL_DIS_POS_F;
			multiplicativeNegativeAttenuatingFactor = MUL_DIS_NEG_F;
		}
	}
	
	Map<Topology.City, Double> cityWeightMap = new HashMap<>();
	Map<Topology.City, Double> normalizedCityWeightMap = new HashMap<>(); // "normalized" without considering outliers
	
	private void computeHeuristics()
	{
		// Compute city weights
		
		Map<Topology.City, Double> avgDist = new HashMap<>();
		
		for (Topology.City src : topology.cities())
		{
			Double distSum = 0.;
			
			for (Topology.City dst : topology.cities())
			{
				if (src != dst)
				{
					distSum += src.distanceTo(dst);
				}
			}
			
			avgDist.put(src, distSum / topology.size() - 1);
		}
		
		double medianAvgDist = Helper.median(avgDist.values());
		
		for (Topology.City city : topology.cities())
		{
			double cityWeight = avgDist.get(city) - medianAvgDist;
			cityWeightMap.put(city, cityWeight);
		}
		
		double weightAvg = cityWeightMap.values().stream().mapToDouble(Double::doubleValue).average().getAsDouble();
		double weightSTD = Helper.std(new ArrayList<>(cityWeightMap.values()));
		
		double maxAbsWeight = 0.;
		
		for (Map.Entry<Topology.City, Double> entry : cityWeightMap.entrySet())
		{
			// keep out outliers (> 2 std away from mean)
			if (Math.abs(entry.getValue() - weightAvg) < 2 * weightSTD)
				maxAbsWeight = Math.max(maxAbsWeight, Math.abs(entry.getValue()));
		}
		
		for (Map.Entry<Topology.City, Double> entry : cityWeightMap.entrySet())
		{
			normalizedCityWeightMap.put(entry.getKey(), entry.getValue() / maxAbsWeight);
		}
	}
	
	private final int minBidHistoryWindowSize = 5;
	private final LinkedList<Long> minBidHistoryWindow = new LinkedList<>();
	private long minHistoryBid = Long.MAX_VALUE;
	
	private void updateHistory(Long[] bids)
	{
		List<Long> bidList = new ArrayList<>(Arrays.asList(bids));
		bidList.remove(agent.id());
		
		long minBid = bidList.stream().mapToLong(Long::longValue).min().getAsLong();
		
		if (minBidHistoryWindow.size() >= minBidHistoryWindowSize)
			minBidHistoryWindow.removeLast();
		minBidHistoryWindow.addFirst(minBid);
		
		minHistoryBid = Math.min(minHistoryBid, minBid);
	}
	
	@Override
	public void auctionResult(Task task, int winner, Long[] bids) // timeout-bid
	{
		if (bids.length > 1)
		{
			updateHistory(bids);
		}
		
		if (winner == agent.id())
		{
			currentTaskSet.add(task);
			currentSolution = tempNewSolution;
			totalRevenue += bids[agent.id()];
			
			if (weightFlag == WeightFlag.Pos)
			{
				additivePositiveAttenuatingFactor *= MUL_F;
				multiplicativePositiveAttenuatingFactor *= MUL_F;
			}
			else if (weightFlag == WeightFlag.Neg)
			{
				additiveNegativeAttenuatingFactor /= MUL_F;
				multiplicativeNegativeAttenuatingFactor /= MUL_F;
			}
		}
		else
		{
			if (weightFlag == WeightFlag.Pos)
			{
				additivePositiveAttenuatingFactor /= MUL_F;
				multiplicativePositiveAttenuatingFactor /= MUL_F;
			}
			else if(weightFlag == WeightFlag.Neg)
			{
				additiveNegativeAttenuatingFactor *= MUL_F;
				multiplicativeNegativeAttenuatingFactor *= MUL_F;
			}
		}
	}
	
	private Set<Topology.City> computeVisitedCities(Solution solution)
	{
		Set<Topology.City> visitedCities = new HashSet<>();
		
		for (CentralizedPlan plan: solution.getCentralizedPlanList())
		{
			Topology.City currentCity = plan.getVehicle().getCurrentCity();
			visitedCities.add(currentCity);
			
			for (CentralizedAction action: plan.getActionList())
			{
				Topology.City nextCity = action.isPickup() ? action.getTask().pickupCity : action.getTask().deliveryCity;
				visitedCities.addAll(currentCity.pathTo(nextCity));
				currentCity = nextCity;
			}
		}
		
		return visitedCities;
	}
	
	@Override
	public Long askPrice(Task task) // timeout-bid
	{
		long startTime = System.currentTimeMillis();
		weightFlag = WeightFlag.None;
		
		Set<Task> taskSet = new HashSet<>(currentTaskSet);
		taskSet.add(task);
		
		Solution tempNewSolutionSLS = CentralizedSolver.slsSearch(vehicles, taskSet, bidTimeout, startTime, random);
		Solution tempNewSolutionNeighbors = CentralizedSolver.addTaskAndSearch(currentSolution, task);
		
		tempNewSolution = List.of(tempNewSolutionSLS, tempNewSolutionNeighbors).stream().min(Comparator.comparingLong(Solution::computeCost)).get();
		lastMarginalCost = (tempNewSolution.computeCost() - currentSolution.computeCost());
		
		if (minBidHistoryWindow.size() < 1)
		{
			return lastMarginalCost * 2;
			// TODO change based on destination city connectivity
		}
		
		// Try to gain, but stay safe
		long targetBid;
		
		if (lastMarginalCost <= 0)
		{
			// 2
			targetBid = minBidHistoryWindow.stream().mapToLong(Long::longValue).min().getAsLong();
			
			// 3
//			targetBid = (long) minBidHistoryWindow.stream().mapToLong(Long::longValue).average().getAsDouble();
			
			long loss = (long) Math.max(0, LOSS_MARGIN + random.nextGaussian());
			targetBid = Math.max(1, targetBid - loss);
		}
		else
		{
			Set<Topology.City> newVisitedCities = computeVisitedCities(tempNewSolution);
			newVisitedCities.removeAll(computeVisitedCities(currentSolution));
			
			if (newVisitedCities.size() > 0)
			{
				// We have at least a new unseen city in the plan
				
				targetBid = lastMarginalCost + 1;
				
				double avgCityWeight = newVisitedCities.stream()
					.map(city -> cityWeightMap.get(city)).mapToDouble(Double::doubleValue).average().getAsDouble();
				double normalizedAvgCityWeight = newVisitedCities.stream()
					.map(city -> normalizedCityWeightMap.get(city)).mapToDouble(Double::doubleValue).average().getAsDouble();
				
				// additive
				
				double additiveFactor;
				if (avgCityWeight > 0)
				{
					weightFlag = WeightFlag.Pos;
					additiveFactor = additivePositiveAttenuatingFactor * avgCityWeight;
				}
				else
				{
					weightFlag = WeightFlag.Neg;
					additiveFactor = additiveNegativeAttenuatingFactor * avgCityWeight;
				}

//				targetBid += additiveFactor;
				
				// multiplicative
				
				double multiplicativeFactor;
				if (avgCityWeight > 0)
				{
					weightFlag = WeightFlag.Pos;
					multiplicativeFactor = multiplicativePositiveAttenuatingFactor * normalizedAvgCityWeight;
				}
				else
				{
					weightFlag = WeightFlag.Neg;
					multiplicativeFactor = multiplicativeNegativeAttenuatingFactor * normalizedAvgCityWeight;
				}
				
				targetBid += multiplicativeFactor * targetBid;
			}
			else
			{
				// 2
				targetBid = minBidHistoryWindow.stream().mapToLong(Long::longValue).min().getAsLong();
				
				long loss = (long) Math.max(0, LOSS_MARGIN + random.nextGaussian());
				targetBid = Math.max(lastMarginalCost + 1, targetBid - loss);
			}
		}
		
		return targetBid;
	}
	
	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) // timeout-plan
	{
		long startTime = System.currentTimeMillis();
		
		if (currentSolution.computeCost() == 0) // Solution is empty
		{
			System.out.printf("[Safe] empty solution%n");
			return currentSolution.getPlanList();
		}
		
		Solution finalSolution = CentralizedSolver.slsSearch(currentSolution, planTimeout, startTime, random);
		
		List<CentralizedPlan> finalCentralizedPlanList = finalSolution.getCentralizedPlanList().stream().map(centralizedPlan -> {
			List<CentralizedAction> newCentralizedActionList = new LinkedList<>();
			for (CentralizedAction centralizedAction : centralizedPlan.getActionList())
			{
				newCentralizedActionList.add(
					new CentralizedAction(centralizedAction.getActionType(),
					                      tasks.stream().filter(task -> task.id == centralizedAction.getTask().id)
						                      .findFirst().get()));
			}
			return new CentralizedPlan(centralizedPlan.getVehicle(), newCentralizedActionList);
		}).collect(Collectors.toList());
		
		Solution adaptedFinalSolution = new Solution(finalCentralizedPlanList);
		
		System.out.printf("[Heuristic] adaptedFinalSolution cost: %d%n", adaptedFinalSolution.computeCost());
		System.out.printf("[Heuristic] totalRevenue: %d%n", totalRevenue);
		System.out.printf("[Heuristic] gain: %d%n", totalRevenue - adaptedFinalSolution.computeCost());
		
		List<Plan> planList = adaptedFinalSolution.getPlanList();
//		for (Plan plan: planList)
//		{
//			System.out.printf("plan: %s%n", plan);
//		}
		return planList;
	}
}
