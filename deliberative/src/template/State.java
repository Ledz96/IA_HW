package template;

import logist.task.Task;
import logist.topology.Topology;

import java.util.AbstractMap;
import java.util.Objects;
import java.util.Set;

public class State {
    private final Topology.City currentCity;
    private final int residualCapacity;
    private final Set<Task> pickedUpTasks;
    private final Set<Task> availableTasks;
    
    private final AbstractMap.SimpleEntry<State, ActionDeliberative> previousChainLink;
    private final int chainDepth;
    
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
    
    public int getChainDepth()
    {
        return chainDepth;
    }

    public State(State previousState, ActionDeliberative actionFrom, Topology.City currentCity, int capacity, Set<Task> pickedUpTasks, Set<Task> availableTasks) {
        this.previousChainLink = new AbstractMap.SimpleEntry<>(previousState, actionFrom);
        this.chainDepth = previousState == null ? 0 : previousState.getChainDepth() + 1;
        
        this.currentCity = currentCity;
        this.residualCapacity = capacity;
        this.pickedUpTasks = pickedUpTasks;
        this.availableTasks = availableTasks;
    }

    public boolean isFinalState()
    {
        return availableTasks.isEmpty() && pickedUpTasks.isEmpty();
    }
    
    public double getChainCost()
    {
        State previousState = previousChainLink.getKey();
        return previousState == null ? 0 : (previousState.getCurrentCity().distanceTo(currentCity) + previousState.getChainCost());
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
}
