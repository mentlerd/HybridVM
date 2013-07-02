package hu.mentlerd.hybrid;

public abstract class DebugHook {
	
	public static boolean isListening( int event, int mask ){
		return ( event & mask ) != 0;
	}
	
	public static final int MASK_CALL	= 1;
	public static final int MASK_RETURN	= 2;
//	public static final int MASK_LINE	= 4;	Note: Not implemented yet.
	public static final int MASK_COUNT	= 8;
	
	public DebugHook( int mask ){
		this.mask	= mask;
	}
	public DebugHook( int mask, int limit ){
		this.mask	= mask;
		this.limit	= limit;
	}
	
	public int counter;
	public int limit;
	
	public int mask;
	
	public final void passOpcode( Coroutine thread ){
		if ( !isListening(MASK_COUNT, mask) )
			return;
		
		if ( counter++ > limit ){
			counter = 0;
			
			call(thread, MASK_COUNT);
		}
	}
	
	public final void passEvent( Coroutine thread, int event ){
		if ( !isListening(event, mask) ) 
			return;
		
		call( thread, event );
	}
	
	public abstract void call( Coroutine thread, int event );

}
