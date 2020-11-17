//package template;
//
////the list of imports
//
//import logist.Measures;
//import logist.agent.Agent;
//import logist.behavior.AuctionBehavior;
//import logist.plan.Plan;
//import logist.simulation.Vehicle;
//import logist.task.Task;
//import logist.task.TaskDistribution;
//import logist.task.TaskSet;
//import logist.topology.Topology;
//import logist.topology.Topology.City;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Random;
//
///**
// * A very simple auction agent that assigns all tasks to its first vehicle and
// * handles them sequentially.
// *
// */
//@SuppressWarnings("unused")
//public class AuctionTemplate implements AuctionBehavior
//{
//	private Topology topology;
//	private TaskDistribution distribution;
//	private Agent agent;
//	private List<Vehicle> vehicles;
//	private Random random;
//
//	@Override
//	public void setup(Topology topology, TaskDistribution distribution, Agent agent)
//	{
//		this.topology = topology;
//		this.distribution = distribution;
//		this.agent = agent;
//		this.vehicles = agent.vehicles();
//		this.random = new Random(agent.id());
//	}
//
//	@Override
//	public void auctionResult(Task previous, int winner, Long[] bids)
//	{
//		// TODO ...
//	}
//
//	@Override
//	public Long askPrice(Task task)
//	{
//		// TODO ...
//	}
//
//	@Override
//	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks)
//	{
//		// TODO ...
//	}
//}
