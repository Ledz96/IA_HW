package template;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.topology.Topology;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ActionDeliberative {
    private final Topology.City destination;
    private final Set<Task> pickedUpTasks;

    public ActionDeliberative(Topology.City destination, Set<Task> pickedUpTask) {
        this.destination = destination;
        this.pickedUpTasks = pickedUpTask;
    }

    public Topology.City getDestination() {
        return destination;
    }

    public Set<Task> getPickedUpTasks() {
        return pickedUpTasks;
    }
    
    public State execute(State state, Vehicle vehicle)
    {
        Set<Task> newPickedUpTasks = Stream.concat(state.getPickedUpTasks().stream(), pickedUpTasks.stream())
                .filter(task -> task.deliveryCity != destination)
                .collect(Collectors.toSet());
        
        return new State(state,
                         this,
                         vehicle.costPerKm(),
                         destination,
                         vehicle.capacity() - newPickedUpTasks.stream().map(task -> task.weight).reduce(0, Integer::sum),
                         newPickedUpTasks,
                         state.getAvailableTasks().stream().filter(Predicate.not(pickedUpTasks::contains)).collect(Collectors.toSet()));
    }
    
    public static boolean checkEnoughCapacity(State state, Set<Task> pickupTasks)
    {
        return pickupTasks.stream().map(task -> task.weight).reduce(0, Integer::sum) <= state.getResidualCapacity();
    }
    
    @Override
    public String toString()
    {
        return "ActionDeliberative{" +
            "destination=" + destination +
            ", pickedUpTasks=" + pickedUpTasks +
            '}';
    }
}
