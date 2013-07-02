package hu.mentlerd.hybrid;

public class LuaException extends RuntimeException{
	private static final long serialVersionUID = -8214311557059032574L;

	private Object luaCause;
	
	public LuaException( String message ) {
		super( message );
	}

	public LuaException( String message, Object cause ){
		this( message );
		this.luaCause = cause;
	}

	public Object getLuaCause(){
		if ( luaCause == null )
			return getMessage();
		
		return luaCause;
	}
}
