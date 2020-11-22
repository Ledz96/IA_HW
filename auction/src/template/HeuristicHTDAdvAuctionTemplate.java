//package template;
//
//import logist.LogistPlatform;
//import logist.LogistSettings;
//import logist.agent.Agent;
//import logist.behavior.AuctionBehavior;
//import logist.plan.Plan;
//import logist.simulation.Vehicle;
//import logist.simulation.VehicleImpl;
//import logist.task.Task;
//import logist.task.TaskDistribution;
//import logist.task.TaskSet;
//import logist.topology.Topology;
//import template.Centralized.*;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
//@SuppressWarnings("unused")
//public class HeuristicHTDAdvAuctionTemplate implements AuctionBehavior
//{
//	// History&Topology&Distribution-based Heuristic
//
//	private enum WeightFlag
//	{
//		None, Pos, Neg
//	}
//
//	// Multiplicative advantage factors
//	private static final Double MUL_ADV_POS_F = 0.2;
//	private static final Double MUL_ADV_NEG_F = 0.2;
//
//	// Multiplicative disadvantage factors
//	private static final Double MUL_DIS_POS_F = 0.2;
//	private static final Double MUL_DIS_NEG_F = 0.1;
//
//	// Factor for the adaptation of the multiplicative factors
//	private static final Double MUL_F = 1.5;
//
//	private enum LossFlag
//	{
//		None, ZeroMarginalCost, NoNewCities
//	}
//
//	// Percentages of loss the agent accepts for assuring a task
//	private static final Double ZMC_LOSS_MARGIN_P = 0.1; // Zero Marginal Cost
//	private static final Double NNC_LOSS_MARGIN_P = 0.05; // No New Cities
//
//	// Factor for the adaptation of the loss factors
//	private static final Double LOSS_F = 1.5;
//
//	private static final Double ADV_RETAIN = 0.85;
//
//	/////
//
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
//	private double medianAvgDist;
//
//	private WeightFlag weightFlag;
//	private Double multiplicativePositiveAttenuatingFactor;
//	private Double multiplicativeNegativeAttenuatingFactor;
//
//	private LossFlag lossFlag;
//	private Double zeroMarginalCostLossMarginProb;
//	private Double noNewCitiesLossMarginProb;
//
//	// Adversarial
//
//	private final Map<Integer, List<Vehicle>> vehiclesMap = new HashMap<>();
//	private final Map<Integer, Set<Task>> advCurrentTaskSetMap = new HashMap<>();
//	private final Map<Integer, Solution> advCurrentSolutionMap = new HashMap<>();
//	private final Map<Integer, Solution> advTempNewSolutionMap = new HashMap<>();
//
//	private long predictionCorrection = 0L;
//
//	// === ADV ===
//
//	private void initAgentMaps(int id, List<Vehicle> vehicleList)
//	{
//		vehiclesMap.put(id, vehicleList);
//
//		advCurrentTaskSetMap.put(id, new HashSet<>());
//
//		List<CentralizedPlan> centralizedPlanList = new ArrayList<>();
//		vehiclesMap.get(id).forEach(vehicle -> centralizedPlanList.add(new CentralizedPlan(vehicle, new ArrayList<>())));
//		advCurrentSolutionMap.put(id, new Solution(centralizedPlanList));
//
//		advTempNewSolutionMap.put(id, new Solution(centralizedPlanList));
//	}
//
//	private void initAgentMaps(int id)
//	{
//		int maxCapacity = vehicles.stream().map(Vehicle::capacity).max(Integer::compareTo).get();
//		int minCostPerKm = vehicles.stream().map(Vehicle::costPerKm).min(Integer::compareTo).get();
//		Set<Topology.City> cities = new HashSet<>(topology.cities());
//		cities.removeAll(vehicles.stream().map(Vehicle::homeCity).collect(Collectors.toSet()));
//
//		List<Vehicle> newVehicleList = new ArrayList<>();
//		for (Vehicle vehicle : vehicles)
//		{
//			Topology.City city = cities.stream().skip((int) (random.nextDouble() * cities.size())).findFirst().get();
//			cities.remove(city);
//
//			newVehicleList.add(new VehicleImpl(vehicle.id(), vehicle.name(), maxCapacity, minCostPerKm, city, (long) vehicle.speed(), vehicle.color()).getInfo());
//		}
//
//		initAgentMaps(id, newVehicleList);
//	}
//
//	// ===========
//
//	@Override
//	public void setup(Topology topology, TaskDistribution distribution, Agent agent) // timeout-setup
//	{
//		LogistSettings ls = LogistPlatform.getSettings();
//		this.setupTimeout = ls.get(LogistSettings.TimeoutKey.SETUP);
//		this.planTimeout = ls.get(LogistSettings.TimeoutKey.PLAN);
//		this.bidTimeout = ls.get(LogistSettings.TimeoutKey.BID);
//
//		this.topology = topology;
//		this.distribution = distribution;
//		this.agent = agent;
//		this.vehicles = agent.vehicles();
////		this.random = new Random(agent.id());
//		this.random = new Random();
//
//		currentTaskSet = new HashSet<>();
//
//		List<CentralizedPlan> centralizedPlanList = new ArrayList<>();
//		vehicles.forEach(vehicle -> centralizedPlanList.add(new CentralizedPlan(vehicle, new ArrayList<>())));
//
//		currentSolution = new Solution(centralizedPlanList);
//		computeHeuristics();
//
//		if (agent.id() == 0) // advantage
//		{
//			multiplicativePositiveAttenuatingFactor = MUL_ADV_POS_F;
//			multiplicativeNegativeAttenuatingFactor = MUL_ADV_NEG_F;
//		}
//		else // disadvantage
//		{
//			multiplicativePositiveAttenuatingFactor = MUL_DIS_POS_F;
//			multiplicativeNegativeAttenuatingFactor = MUL_DIS_NEG_F;
//		}
//
//		zeroMarginalCostLossMarginProb = ZMC_LOSS_MARGIN_P;
//		noNewCitiesLossMarginProb = NNC_LOSS_MARGIN_P;
//
//		// === ADV ===
////		initAgentMaps(agent.id(), agent.vehicles());
//		// ===========
//	}
//
//	Map<Topology.City, Double> cityWeightMap = new HashMap<>();
//	Map<Topology.City, Double> normalizedCityWeightMap = new HashMap<>(); // "normalized" without considering outliers
//
//	private void computeHeuristics()
//	{
//		// Compute city weights
//
//		Map<Topology.City, Double> avgDist = new HashMap<>();
//
//		for (Topology.City src : topology.cities())
//		{
//			Double distSum = 0.;
//
//			for (Topology.City dst : topology.cities())
//			{
//				if (src != dst)
//				{
//					distSum += src.distanceTo(dst);
//				}
//			}
//
//			avgDist.put(src, distSum / topology.size() - 1);
//		}
//
//		medianAvgDist = Helper.median(avgDist.values());
//
//		for (Topology.City city : topology.cities())
//		{
//			double cityWeight = avgDist.get(city) - medianAvgDist;
//			cityWeightMap.put(city, cityWeight);
//		}
//
//		double weightAvg = cityWeightMap.values().stream().mapToDouble(Double::doubleValue).average().getAsDouble();
//		double weightSTD = Helper.std(new ArrayList<>(cityWeightMap.values()));
//
//		double maxAbsWeight = 0.;
//
//		for (Map.Entry<Topology.City, Double> entry : cityWeightMap.entrySet())
//		{
//			// keep out outliers (> 2 std away from mean)
//			if (Math.abs(entry.getValue() - weightAvg) < 2 * weightSTD)
//				maxAbsWeight = Math.max(maxAbsWeight, Math.abs(entry.getValue()));
//		}
//
//		for (Map.Entry<Topology.City, Double> entry : cityWeightMap.entrySet())
//		{
//			normalizedCityWeightMap.put(entry.getKey(), entry.getValue() / maxAbsWeight);
//		}
//	}
//
//	// TODO search best size
//	private final int minBidHistoryWindowSize = 5;
//	private final LinkedList<Long> minBidHistoryWindow = new LinkedList<>();
//	private long minHistoryBid = Long.MAX_VALUE;
//
//	private void updateHistory(Long[] bids)
//	{
//		List<Long> bidList = new ArrayList<>(Arrays.asList(bids));
//		bidList.remove(agent.id());
//
//		long minBid = bidList.stream().mapToLong(Long::longValue).min().getAsLong();
//
//		if (minBidHistoryWindow.size() >= minBidHistoryWindowSize)
//			minBidHistoryWindow.removeLast();
//		minBidHistoryWindow.addFirst(minBid);
//
//		minHistoryBid = Math.min(minHistoryBid, minBid);
////		System.out.printf("minBidHistoryWindow: %s%n", minBidHistoryWindow);
//	}
//
//	// === ADV ===
//
//	private Solution findBestNewSolution(int id, Solution currentSolution, Task task, long timeout, long startTime)
//	{
//		// TODO use deterministic only if there are too many tasks or adversaries
//
//		Set<Task> newTaskSet = new HashSet<>(advCurrentTaskSetMap.get(id));
//		newTaskSet.add(task);
//
//		Solution tempNewDeterministicSolution = CentralizedSolver.addTaskAndSearch(advCurrentSolutionMap.get(id), task);
//		Solution tempNewSLSSolution = CentralizedSolver.slsSearch(vehiclesMap.get(id), newTaskSet, timeout, startTime, random);
//		return List.of(tempNewSLSSolution, tempNewDeterministicSolution).stream()
//			.min(Comparator.comparingLong(Solution::computeCost)).get();
//	}
//
//	// ===========
//
//	// TODO if another agent bids a negative value, is it a problem for us? Would it be better to clamp bids[] to 0?
//
//	@Override
//	public void auctionResult(Task task, int winner, Long[] bids) // timeout-bid
//	{
//		long startTime = System.currentTimeMillis();
//		System.out.printf("winner == %s%n", winner);
//
//		// Safe
//		if (bids.length > 1)
//		{
//			updateHistory(bids);
//		}
//
//		// === ADV ===
//		if (!advCurrentTaskSetMap.containsKey(winner) && winner != agent.id())
//		{
//			Solution bestAdvSolution = null;
//			long bestAdvSolutionDist = Long.MAX_VALUE;
//
//			long dio = System.currentTimeMillis();
//
//			for (int it = 0; it < 50; it++)
//			{
//				// Init so that the mapping of cities to the rival's vehicles is randomized
//				initAgentMaps(winner);
//
//				Solution advSolution = findBestNewSolution(winner, advCurrentSolutionMap.get(winner), task, bidTimeout, startTime);
//				long advSolutionDist = Math.abs(advSolution.computeCost() - bids[winner]);
//
//				if (advSolutionDist < bestAdvSolutionDist)
//				{
//					bestAdvSolution = advSolution;
//					bestAdvSolutionDist = advSolutionDist;
//				}
//			}
//
//			System.out.printf("dio: %s%n", System.currentTimeMillis() - dio);
//
//			advTempNewSolutionMap.put(winner, bestAdvSolution);
//		}
//
//		// Decay prediction correction
//		predictionCorrection = (long) (predictionCorrection * 0.5);
//
//		// If  our prediction is too far from the adversarial bid, update the correction coefficient for adversarial marginal
//
//		long advMarginal;
//		if (winner == agent.id())
//			advMarginal = tempNewSolution.computeCost() - currentSolution.computeCost();
//		else
//			advMarginal = advTempNewSolutionMap.get(winner).computeCost() - advCurrentSolutionMap.get(winner).computeCost();
//
//		advMarginal = Math.max(0, advMarginal);
//
//		if (bids[winner] > 2 * advMarginal)
//		{
//			predictionCorrection += (bids[winner] - advMarginal) / 2;
//		}
//		else if (bids[winner] < 0.5 * advMarginal)
//		{
//			predictionCorrection -= (advMarginal - bids[winner]) / 2;
//		}
//
//		System.out.printf("[HTDAdv] predictionCorrection: %s%n", predictionCorrection);
//
//		// TODO reset predictionCorrection if task is lost to a bid over marginal cost?
//
//		//
//
//		if (winner != agent.id())
//		{
//			advCurrentTaskSetMap.get(winner).add(task);
//			advCurrentSolutionMap.put(winner, advTempNewSolutionMap.get(winner));
//		}
//
//		// ===========
//
//		if (winner == agent.id())
//		{
//			currentTaskSet.add(task);
//			currentSolution = tempNewSolution;
//			totalRevenue += bids[agent.id()];
//
//			if (weightFlag == WeightFlag.Pos)
//				multiplicativePositiveAttenuatingFactor *= MUL_F;
//			else if (weightFlag == WeightFlag.Neg)
//				multiplicativeNegativeAttenuatingFactor /= MUL_F;
//
//			if (lossFlag == LossFlag.ZeroMarginalCost)
//				zeroMarginalCostLossMarginProb /= LOSS_F;
//			else if (lossFlag == LossFlag.NoNewCities)
//				noNewCitiesLossMarginProb /= LOSS_F;
//		}
//		else
//		{
//			if (weightFlag == WeightFlag.Pos)
//				multiplicativePositiveAttenuatingFactor /= MUL_F;
//			else if(weightFlag == WeightFlag.Neg)
//				multiplicativeNegativeAttenuatingFactor *= MUL_F;
//
//			if (lossFlag == LossFlag.ZeroMarginalCost)
//				zeroMarginalCostLossMarginProb *= LOSS_F;
//			else if (lossFlag == LossFlag.NoNewCities)
//				noNewCitiesLossMarginProb *= LOSS_F;
//		}
//	}
//
//	private Set<Topology.City> computeVisitedCities(Solution solution)
//	{
//		Set<Topology.City> visitedCities = new HashSet<>();
//
//		for (CentralizedPlan plan: solution.getCentralizedPlanList())
//		{
//			Topology.City currentCity = plan.getVehicle().getCurrentCity();
//			visitedCities.add(currentCity);
//
//			for (CentralizedAction action: plan.getActionList())
//			{
//				Topology.City nextCity = action.isPickup() ? action.getTask().pickupCity : action.getTask().deliveryCity;
//				visitedCities.addAll(currentCity.pathTo(nextCity));
//				currentCity = nextCity;
//			}
//		}
//
//		return visitedCities;
//	}
//
//	private Optional<Long> computeAdversarialMarginal(Task task, long timeout, long startTime)
//	{
//		// Compute temp solution for everyone
//
//		if (advCurrentTaskSetMap.entrySet().isEmpty())
//			return Optional.empty();
//
//		long timeSpan = timeout / advCurrentTaskSetMap.entrySet().size();
//		for (Map.Entry<Integer, Set<Task>> entry : advCurrentTaskSetMap.entrySet())
//		{
//			advTempNewSolutionMap.put(entry.getKey(),
//			                          findBestNewSolution(entry.getKey(), advCurrentSolutionMap.get(entry.getKey()), task, timeSpan, startTime));
//		}
//
//		Map<Integer, Long> marginalCostMap = new HashMap<>();
//		for (Map.Entry<Integer, Solution> entry : advTempNewSolutionMap.entrySet())
//		{
//			marginalCostMap.put(entry.getKey(), entry.getValue().computeCost() - advCurrentSolutionMap.get(entry.getKey()).computeCost());
//		}
//
//		return marginalCostMap.entrySet().stream()
//			.map(Map.Entry::getValue)
//			.min(Comparator.comparingLong(Long::longValue));
//	}
//
//	private long computeBid(Optional<Long> adversarialMarginal)
//	{
//		long targetBid;
//
//		System.out.printf("[HTDAdv] marginal cost == %s%n", lastMarginalCost);
//
//		if (lastMarginalCost <= 0)
//		{
//			// Our marginal cost is 0, try by all means to take the task -> bid at the historical min in a window minus
//			// a loss which increases/decreases according to our past ability to take the task in this branch
//
//			lastMarginalCost = 0L;
//
//			lossFlag = LossFlag.ZeroMarginalCost;
//			targetBid = minBidHistoryWindow.stream().mapToLong(Long::longValue).min().getAsLong();
//
//			long loss = (long) (targetBid * zeroMarginalCostLossMarginProb);
//
//			// TOEVAL verify this is right
//			if (adversarialMarginal.isPresent())
//			{
//				targetBid = Math.min(targetBid - loss, (long) (ADV_RETAIN * adversarialMarginal.get()));
//				long avgMarginals = (lastMarginalCost + adversarialMarginal.get()) / 2;
//				targetBid = Math.max(Math.max(1, avgMarginals), targetBid);
//			}
//			else
//			{
//				targetBid = Math.max(1, targetBid - loss);
//			}
//
//			System.out.printf("[HTDAdv] bid == %s%n", targetBid);
//			return targetBid;
//		}
//
//		Set<Topology.City> newSolutionVisitedCities = computeVisitedCities(tempNewSolution);
//		Set<Topology.City> newVisitedCities = new HashSet<>(newSolutionVisitedCities);
//		newVisitedCities.removeAll(computeVisitedCities(currentSolution));
//
//		if (newVisitedCities.size() == 0)
//		{
//			// No new city: safe behavior
//			System.out.printf("[HTDAdv] no new city%n");
//
//			lossFlag = LossFlag.NoNewCities;
//
//			// Min of history mins
//			targetBid = minBidHistoryWindow.stream().mapToLong(Long::longValue).min().getAsLong();
//
//			long loss = (long) (noNewCitiesLossMarginProb * targetBid);
//
//			if (adversarialMarginal.isPresent())
//			{
//				// Min between (Min of history mins) and adversarial marginal
//				targetBid = Math.min(targetBid - loss, (long) (ADV_RETAIN * adversarialMarginal.get()));
//
//				long avgMarginals = (lastMarginalCost + adversarialMarginal.get()) / 2;
//				// If the adversarial marginal is higher than the safe min and of our marginal, take a value in between the marginals
//				targetBid = Math.max(Math.max(lastMarginalCost + 1, avgMarginals), targetBid);
//			}
//			else
//			{
//				targetBid = Math.max(lastMarginalCost + 1, targetBid - loss);
//			}
//
//			System.out.printf("[HTDAdv] bid == %s%n", targetBid);
//			return targetBid;
//		}
//		else
//		{
//			// We have at least a new unseen city in the plan
//
//			// Use those cities weights (normalized difference between the median of the average distances from any city
//			// to all others and the single city average distance to other cities), as well as their probabilities to
//			// have a task from themselves to a city with a negative (good) weight
//
//			double avgCityWeight = newVisitedCities.stream()
//					.map(city -> cityWeightMap.get(city)).mapToDouble(Double::doubleValue).average().getAsDouble();
//			double normalizedAvgCityWeight = newVisitedCities.stream()
//					.map(city -> normalizedCityWeightMap.get(city)).mapToDouble(Double::doubleValue).average().getAsDouble();
//
//			// Probability that we get a task to a visited city from new visited cities
//			// 1 - (1-p1) * (1-p2) * ...
//			double backInPlanProb = 1 - newVisitedCities.stream()
//				.map(srcCity -> newSolutionVisitedCities.stream()
//					.filter(dstCity -> srcCity != dstCity && srcCity.distanceTo(dstCity) <= medianAvgDist)
//					.map(dstCity -> distribution.probability(srcCity, dstCity))
//					.reduce(0., Double::sum))
//				.map(p -> 1 - p)
//				.reduce(0., (invP1, invP2) -> invP1 * invP2);
//
//			double backInPlanAttenuation = 0.05;
//
//			double multiplicativeFactor;
//			if (avgCityWeight > 0)
//			{
//				System.out.printf("[HTDAdv] new cities, but bad!%n");
//				weightFlag = WeightFlag.Pos;
//				multiplicativeFactor = multiplicativePositiveAttenuatingFactor * normalizedAvgCityWeight;
//			}
//			else
//			{
//				System.out.printf("[HTDAdv] new cities, good cities! Yummy!%n");
//				weightFlag = WeightFlag.Neg;
//				multiplicativeFactor = multiplicativeNegativeAttenuatingFactor * normalizedAvgCityWeight;
//			}
//
//			if (adversarialMarginal.isPresent())
//			{
//				long avgMarginals = (lastMarginalCost + adversarialMarginal.get()) / 2;
//				lastMarginalCost = Math.max(lastMarginalCost, avgMarginals);
//			}
//			else
//			{
//				long avgMarginals = lastMarginalCost;
//			}
//
//			// TODO use adversarial margin to steal the city if it is very advantageous?
//
//			targetBid = lastMarginalCost + 1;
//
//			targetBid += (long) (multiplicativeFactor * targetBid);
//
//			targetBid -= (backInPlanProb * backInPlanAttenuation) * targetBid;
//
//			System.out.printf("[HTDAdv] bid == %s%n", targetBid);
//			return targetBid;
//		}
//	}
//
//	@Override
//	public Long askPrice(Task task) // timeout-bid
//	{
//		long startTime = System.currentTimeMillis();
//		weightFlag = WeightFlag.None;
//		lossFlag = LossFlag.None;
//
//		System.out.printf("=== Task: %d ===%n", task.id);
//
//		Set<Task> taskSet = new HashSet<>(currentTaskSet);
//		taskSet.add(task);
//
//		Solution tempNewSolutionSLS = CentralizedSolver.slsSearch(vehicles, taskSet, bidTimeout, startTime, random);
//		Solution tempNewSolutionNeighbors = CentralizedSolver.addTaskAndSearch(currentSolution, task);
//
//		tempNewSolution = List.of(tempNewSolutionSLS, tempNewSolutionNeighbors).stream().min(Comparator.comparingLong(Solution::computeCost)).get();
//		lastMarginalCost = (tempNewSolution.computeCost() - currentSolution.computeCost());
//
//		Optional<Long> adversarialMarginal = computeAdversarialMarginal(task,
//		                                                                bidTimeout - (System.currentTimeMillis() - startTime),
//		                                                                startTime);
//
//		if (adversarialMarginal.isPresent())
//		{
//			System.out.printf("[HTDAdv] adversarialMarginal: %s%n", adversarialMarginal);
////			adversarialMarginal = Optional.of(adversarialMarginal.get() + predictionCorrection);
//			adversarialMarginal = Optional.of(adversarialMarginal.get());
//		}
//
////		return computeBid(adversarialMarginal);
//		return 0L;
//	}
//
//	@Override
//	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) // timeout-plan
//	{
//		long startTime = System.currentTimeMillis();
//
//		if (currentSolution.computeCost() == 0) // Solution is empty
//		{
//			System.out.printf("[HTDAdv] empty solution%n");
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
//		System.out.printf("[HTDAdv] adaptedFinalSolution cost: %d%n", adaptedFinalSolution.computeCost());
//		System.out.printf("[HTDAdv] totalRevenue: %d%n", totalRevenue);
//		System.out.printf("[HTDAdv] gain: %d%n", totalRevenue - adaptedFinalSolution.computeCost());
//
//		return adaptedFinalSolution.getPlanList();
//	}
//}
