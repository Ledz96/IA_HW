package template;

public class Pair<F, S> {
    private F first; //first member of pair
    private S second; //second member of pair

    public Pair(F first, S second)
    {
        this.first = first;
        this.second = second;
    }

    public void setFirst(F first)
    {
        this.first = first;
    }

    public void setSecond(S second)
    {
        this.second = second;
    }

    public F getFirst()
    {
        return first;
    }

    public S getSecond()
    {
        return second;
    }

    @Override
    public int hashCode()
    {
        return 31*31*first.hashCode() + 31*second.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Pair))
            return false;

        Pair p = (Pair) o;
        return p.getFirst() == first && p.getSecond() == second;
    }
}