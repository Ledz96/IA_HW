package template;

import logist.task.Task;
import logist.topology.Topology;

import java.util.Set;
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

    public Set<Task> getPickedUpTask() {
        return pickedUpTasks;
    }
    
    public State execute(State state)
    {
        Set<Task> newPickedUpTasks = Stream.concat(state.getAvailableTasks().stream(), pickedUpTasks.stream())
                .filter(task -> task.deliveryCity != destination)
                .collect(Collectors.toSet());
        
        return new State(destination,
                         newPickedUpTasks.stream().map(task -> task.weight).reduce(0, Integer::sum),
                         newPickedUpTasks,
                         state.getAvailableTasks().stream().filter(task -> !pickedUpTasks.contains(task)).collect(Collectors.toSet()));
    }
}
