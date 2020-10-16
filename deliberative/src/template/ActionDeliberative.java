package template;

import logist.task.Task;
import logist.topology.Topology;

public class ActionDeliberative {
    private final Topology.City destination;
    private final Task pickedUpTask;            // Might be a list of tasks

    public ActionDeliberative(Topology.City destination, Task pickedUpTask) {
        this.destination = destination;
        this.pickedUpTask = pickedUpTask;
    }

    public Topology.City getDestination() {
        return destination;
    }

    public Task getPickedUpTask() {
        return pickedUpTask;
    }
}
