package template;

import logist.task.Task;
import logist.topology.Topology;

import java.util.Objects;
import java.util.Set;

public class State {
    private final Topology.City currentCity;
    private final int currentWeight;
    private final Set<Task> pickedUpTasks;
    private final Set<Task> availableTasks;

    public State(Topology.City currentCity, int currentWeight, Set<Task> pickedUpTasks, Set<Task> availableTasks) {
        this.currentCity = currentCity;
        this.currentWeight = currentWeight;
        this.pickedUpTasks = pickedUpTasks;
        this.availableTasks = availableTasks;
    }

    public Topology.City getCurrentCity() {
        return currentCity;
    }

    public int getCurrentWeight() {
        return currentWeight;
    }

    public Set<Task> getPickedUpTasks() {
        return pickedUpTasks;
    }

    public Set<Task> getAvailableTasks() {
        return availableTasks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        State state = (State) o;
        return currentWeight == state.currentWeight &&
                currentCity.equals(state.currentCity) &&
                pickedUpTasks.equals(state.pickedUpTasks) &&
                availableTasks.equals(state.availableTasks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentCity, currentWeight, pickedUpTasks, availableTasks);
    }
}
