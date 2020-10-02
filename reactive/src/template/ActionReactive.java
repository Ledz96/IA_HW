package template;

import logist.topology.Topology;

import javax.swing.*;

public class ActionReactive {

    private Topology.City destination;
    private boolean isDeliveringTask;

    public ActionReactive(Topology.City destination, boolean isDeliveringTask)
    {
        this.destination = destination;
        this.isDeliveringTask = isDeliveringTask;
    }

    public Topology.City getDestination()
    {
        return destination;
    }

    public boolean isDeliveringTask() {
        return isDeliveringTask;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof ActionReactive))
        {
            return false;
        }

        ActionReactive a = (ActionReactive) o;

        return ((a.getDestination() == destination) && (a.isDeliveringTask() == isDeliveringTask));
    }

    @Override
    public int hashCode()
    {
        return 31*31*destination.hashCode() + Boolean.hashCode(isDeliveringTask);
    }

}
