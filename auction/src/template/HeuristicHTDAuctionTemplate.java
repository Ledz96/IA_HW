package template;

import logist.LogistPlatform;
import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.simulation.VehicleImpl;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import template.Centralized.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class HeuristicHTDAuctionTemplate implements AuctionBehavior
{
	// History&Topology&Distribution-based Heuristic
	
	private enum WeightFlag
	{
		None, Pos, Neg
	}
	
	// Multiplicative advantage factors
	private static final Double MUL_ADV_POS_F = 0.2;
	private static final Double MUL_ADV_NEG_F = 0.2;
	
	// Multiplicative disadvantage factors
	private static final Double MUL_DIS_POS_F = 0.2;
	private static final Double MUL_DIS_NEG_F = 0.1;
	
	// Factor for the adaptation of the multiplicative factors
	private static final Double MUL_F = 1.5;
	
	private enum LossFlag
	{
		None, ZeroMarginalCost, NoNewCities
	}
	
	// Percentages of loss the agent accepts for assuring a task
	private static final Double ZMC_LOSS_MARGIN_P = 0.1; // Zero Marginal Cost
	private static final Double NNC_LOSS_MARGIN_P = 0.05; // No New Cities
	
	// Factor for the adaptation of the loss factors
	private static final Double LOSS_F = 1.5;
	
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
	
	private double medianAvgDist;
	
	private WeightFlag weightFlag;
	private Double multiplicativePositiveAttenuatingFactor;
	private Double multiplicativeNegativeAttenuatingFactor;
	
	private LossFlag lossFlag;
	private Double zeroMarginalCostLossMarginProb;
	private Double noNewCitiesLossMarginProb;
	
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
			multiplicativePositiveAttenuatingFactor = MUL_ADV_POS_F;
			multiplicativeNegativeAttenuatingFactor = MUL_ADV_NEG_F;
		}
		else // disadvantage
		{
			multiplicativePositiveAttenuatingFactor = MUL_DIS_POS_F;
			multiplicativeNegativeAttenuatingFactor = MUL_DIS_NEG_F;
		}
		
		zeroMarginalCostLossMarginProb = ZMC_LOSS_MARGIN_P;
		noNewCitiesLossMarginProb = NNC_LOSS_MARGIN_P;
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
		
		medianAvgDist = Helper.median(avgDist.values());
		
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
		System.out.printf("minBidHistoryWindow: %s%n", minBidHistoryWindow);
	}
	
	@Override
	public void auctionResult(Task task, int winner, Long[] bids) // timeout-bid
	{
		System.out.printf("task.id == %s%n", task.id);
		System.out.printf("winner == %s%n", winner);
		
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
				multiplicativePositiveAttenuatingFactor *= MUL_F;
			else if (weightFlag == WeightFlag.Neg)
				multiplicativeNegativeAttenuatingFactor /= MUL_F;
			
			if (lossFlag == LossFlag.ZeroMarginalCost)
				zeroMarginalCostLossMarginProb /= LOSS_F;
			else if (lossFlag == LossFlag.NoNewCities)
				noNewCitiesLossMarginProb /= LOSS_F;
		}
		else
		{
			if (weightFlag == WeightFlag.Pos)
				multiplicativePositiveAttenuatingFactor /= MUL_F;
			else if(weightFlag == WeightFlag.Neg)
				multiplicativeNegativeAttenuatingFactor *= MUL_F;
			
			if (lossFlag == LossFlag.ZeroMarginalCost)
				zeroMarginalCostLossMarginProb *= LOSS_F;
			else if (lossFlag == LossFlag.NoNewCities)
				noNewCitiesLossMarginProb *= LOSS_F;
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

	private long computeBid(long marginalCost)
	{
		long targetBid;
		
		System.out.printf("[HTD] marginal cost == %s%n", marginalCost);

		if (marginalCost <= 0)
		{
			// Our marginal cost is 0, try by all means to take the task -> bid at the historical min in a window minus
			// a loss which increases/decreases according to our past ability to take the task in this branch
			
			lossFlag = LossFlag.ZeroMarginalCost;
			targetBid = minBidHistoryWindow.stream().mapToLong(Long::longValue).min().getAsLong();
			
			System.out.printf("targetBid: %s%n", targetBid);
			
			long loss = (long) (targetBid * zeroMarginalCostLossMarginProb);
			System.out.printf("[HTD] bid == %s%n", Math.max(1, targetBid - loss));
			return Math.max(1, targetBid - loss);
		}
		
		Set<Topology.City> newSolutionVisitedCities = computeVisitedCities(tempNewSolution);
		Set<Topology.City> newVisitedCities = new HashSet<>(newSolutionVisitedCities);
		newVisitedCities.removeAll(computeVisitedCities(currentSolution));
		
		if (newVisitedCities.size() == 0)
		{
			// No new city: safe behavior
			
			lossFlag = LossFlag.NoNewCities;
			targetBid = minBidHistoryWindow.stream().mapToLong(Long::longValue).min().getAsLong();
			
			// TODO use percentage (e.g. loss = 0.05 * (targetBid - (marginalCost + 1)))
			long loss = (long) (noNewCitiesLossMarginProb * targetBid);
			System.out.printf("[HTD] bid == %s%n", Math.max(marginalCost + 1, targetBid - loss));
			return Math.max(marginalCost + 1, targetBid - loss);
		}
		else
		{
			// We have at least a new unseen city in the plan
			
			// Use those cities weights (normalized difference between the median of the average distances from any city
			// to all others and the single city average distance to other cities), as well as their probabilities to
			// have a task from themselves to a city with a negative (good) weight
			
			double avgCityWeight = newVisitedCities.stream()
					.map(city -> cityWeightMap.get(city)).mapToDouble(Double::doubleValue).average().getAsDouble();
			double normalizedAvgCityWeight = newVisitedCities.stream()
					.map(city -> normalizedCityWeightMap.get(city)).mapToDouble(Double::doubleValue).average().getAsDouble();
			
			// Probability that we get a task to a visited city from new visited cities
			// 1 - (1-p1) * (1-p2) * ...
			double backInPlanProb = 1 - newVisitedCities.stream()
				.map(srcCity -> newSolutionVisitedCities.stream()
					.filter(dstCity -> srcCity != dstCity && srcCity.distanceTo(dstCity) <= medianAvgDist)
					.map(dstCity -> distribution.probability(srcCity, dstCity))
					.reduce(0., Double::sum))
				.map(p -> 1 - p)
				.reduce(0., (invP1, invP2) -> invP1 * invP2);
			
			double backInPlanAttenuation = 0.05;

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

			// First case or not
			targetBid = (minBidHistoryWindow.size() < 1) ? marginalCost * 2 : marginalCost + 1;
			
			targetBid += (long) (multiplicativeFactor * targetBid);
			
			targetBid -= (backInPlanProb * backInPlanAttenuation) * targetBid;
			
			System.out.printf("[HTD] bid == %s%n", targetBid);
			return targetBid;
		}
	}
	
	@Override
	public Long askPrice(Task task) // timeout-bid
	{
		long startTime = System.currentTimeMillis();
		weightFlag = WeightFlag.None;
		lossFlag = LossFlag.None;
		
		Set<Task> taskSet = new HashSet<>(currentTaskSet);
		taskSet.add(task);
		
		Solution tempNewSolutionSLS = CentralizedSolver.slsSearch(vehicles, taskSet, bidTimeout, startTime, random);
		Solution tempNewSolutionNeighbors = CentralizedSolver.addTaskAndSearch(currentSolution, task);
		
		tempNewSolution = List.of(tempNewSolutionSLS, tempNewSolutionNeighbors).stream().min(Comparator.comparingLong(Solution::computeCost)).get();
		lastMarginalCost = (tempNewSolution.computeCost() - currentSolution.computeCost());

		return computeBid(lastMarginalCost);
	}
	
	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) // timeout-plan
	{
		long startTime = System.currentTimeMillis();
		
		if (currentSolution.computeCost() == 0) // Solution is empty
		{
			System.out.printf("[HTD] empty solution%n");
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
		
		System.out.printf("[HTD] adaptedFinalSolution cost: %d%n", adaptedFinalSolution.computeCost());
		System.out.printf("[HTD] totalRevenue: %d%n", totalRevenue);
		System.out.printf("[HTD] gain: %d%n", totalRevenue - adaptedFinalSolution.computeCost());
		
		return adaptedFinalSolution.getPlanList();
	}
}
