package hu.mentlerd.hybrid;

public class UpValue {
	private Coroutine coroutine;
	private final int index;
	
	private Object value;
	
	public UpValue( Coroutine coroutine, int index ){
		this.coroutine	= coroutine;
		this.index		= index;
	}
	
	public int getIndex(){
		return index;
	}
	
	public Object getValue(){
		if ( coroutine == null )
			return value;
		
		return coroutine.stack[index];
	}
	
	public void setValue( Object value ){
		if ( coroutine == null ){
			this.value = value;
		} else {
			coroutine.stack[index] = value;
		}
	}

	public void close(){
		this.value		= coroutine.stack[index];
		this.coroutine	= null;
	}
}
