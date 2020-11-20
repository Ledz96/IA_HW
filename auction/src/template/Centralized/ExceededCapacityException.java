package template.Centralized;

public class ExceededCapacityException extends RuntimeException
{
	private static final String str = "Capacity of vehicle was exceeded";
	
	public ExceededCapacityException()
	{
		super(str);
	}
	
	public ExceededCapacityException(String message)
	{
		super(String.format("%s: %s%n", str, message));
	}
}
