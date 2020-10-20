package template;

import logist.task.Task;
import logist.topology.Topology;

import java.util.AbstractMap;
import java.util.Objects;
import java.util.Set;

public class State {
    // === hashed ===
    private final Topology.City currentCity;
    private final int residualCapacity;
    private final Set<Task> pickedUpTasks;
    private final Set<Task> availableTasks;
    // ==============
    
    private final AbstractMap.SimpleEntry<State, ActionDeliberative> previousChainLink;
    private final double chainCost;
    
    public Topology.City getCurrentCity() {
        return currentCity;
    }
    
    public int getResidualCapacity()
    {
        return residualCapacity;
    }
    
    public Set<Task> getPickedUpTasks() {
        return pickedUpTasks;
    }
    
    public Set<Task> getAvailableTasks() {
        return availableTasks;
    }
    
    public AbstractMap.SimpleEntry<State, ActionDeliberative> getPreviousChainLink()
    {
        return previousChainLink;
    }
    
    public double getChainCost()
    {
        return chainCost;
    }

    public State(State previousState, ActionDeliberative actionFrom, int costPerKm, Topology.City currentCity, int capacity, Set<Task> pickedUpTasks, Set<Task> availableTasks) {
        this.previousChainLink = new AbstractMap.SimpleEntry<>(previousState, actionFrom);
        this.chainCost = previousState == null ? 0 :
            previousState.getChainCost() + costPerKm * previousState.getCurrentCity().distanceTo(currentCity);
        
        this.currentCity = currentCity;
        this.residualCapacity = capacity;
        this.pickedUpTasks = pickedUpTasks;
        this.availableTasks = availableTasks;
    }

    public boolean isFinalState()
    {
        return availableTasks.isEmpty() && pickedUpTasks.isEmpty();
    }
    
    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        State state = (State) o;
        return residualCapacity == state.residualCapacity &&
            Objects.equals(currentCity, state.currentCity) &&
            Objects.equals(pickedUpTasks, state.pickedUpTasks) &&
            Objects.equals(availableTasks, state.availableTasks);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(currentCity, residualCapacity, pickedUpTasks, availableTasks);
    }
    
    @Override
    public String toString()
    {
        return "State{" +
            "currentCity=" + currentCity +
            ", residualCapacity=" + residualCapacity +
            ", pickedUpTasks=" + pickedUpTasks +
            ", availableTasks=" + availableTasks +
            ", previousChainLink=" + previousChainLink +
            ", chainDepth=" + chainDepth +
            '}';
    }
}
