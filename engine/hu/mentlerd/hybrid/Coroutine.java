package hu.mentlerd.hybrid;

import java.util.Vector;

public class Coroutine {
	public static final int INITIAL_STACK_SIZE	= 32;
	public static final int INITIAL_FRAME_SIZE	= 10;
	
	public static final int MAX_STACK_SIZE 	= 1024;
	public static final int MAX_FRAME_SIZE	= 100;

	public static void yield( CallFrame frame, CallFrame argFrame, int argCount ){
		if ( !frame.canYield )
			throw new LuaException( "Cannot yield outside of a coroutine" );
		
		Coroutine coroutine	= frame.coroutine;
		Coroutine parent	= coroutine.parent;
		LuaThread thread	= coroutine.thread;

		if ( parent == null )
			throw new LuaException( "Yielded a root thread. Something went horribly wrong!" );
		
		coroutine.detach(); //Kill the current coroutine, and return values

		CallFrame nextFrame = parent.getCurrentFrame();
        
        if ( nextFrame == null ){ //Parent is dead, push to the stack directly
        	parent.setTop( argCount +1 );
        	
        	parent.stack[0] = Boolean.TRUE;
        	for ( int index = 0; index < argCount; index++ )
        		parent.stack[index +1] = argFrame.get(index);
        } else {
        	nextFrame.push( Boolean.TRUE );
        	
        	for ( int index = 0; index < argCount; index++ )
        		nextFrame.push( argFrame.get(index) );
        }

        thread.coroutine = parent;
	}
	
	private final Vector<UpValue> upvalues = new Vector<UpValue>();
	protected final Platform platform;
	
	protected LuaThread thread;
	protected Coroutine parent;
	
	protected LuaTable env;
	
	protected Object[] stack;
	protected int top;
	
	private CallFrame[] frameStack;
	private int frameStackTop;

	private StringBuilder stackTrace = new StringBuilder();
	private int stackTraceLevel = 0;
	
	public Coroutine( Platform platform, LuaTable env ){
		this.stack 		= new Object[INITIAL_STACK_SIZE];
		this.frameStack	= new CallFrame[INITIAL_FRAME_SIZE];
	
		this.platform 	= platform;
		this.env		= env;
	}
	
	//Used to setup root function
	public Coroutine( Platform platform, LuaTable env, LuaClosure root ){
		this(platform, env);
		
		CallFrame frame = pushCallFrame(root, 0, 0, -1);
			frame.fromLua	= true;
			frame.canYield	= true;	
	}
	
	/*
	 * Stack management
	 */
	public final void setTop( int newTop ){
		if ( top < newTop ){
			//Ensure stack size
			int size = stack.length;
			
			if ( size < newTop ){ //Realloc
				if ( newTop > MAX_STACK_SIZE )
					throw new LuaException("Stack overflow");
				
				while( size < newTop )
					size <<= 1;
				
				Object[] realloc = new Object[size];
				System.arraycopy(stack, 0, realloc, 0, stack.length);	
				stack = realloc;
			}
		} else {
			stackClear(newTop, top -1);
		}
		
		top = newTop;
	}
	
	public final int getTop(){
		return top;
	}
	
	public final void stackClear( int index, int end ){
		for(; index <= end; index++)
			stack[index] = null;
	}
	public final void stackCopy( int index, int dest, int len ){
		if (len > 0 && index != dest)
			System.arraycopy(stack, index, stack, dest, len);
	}
	
	/*
	 * Callframe Stack
	 */
	public CallFrame getCurrentFrame(){
		if ( isDead() )	return null;
		
		CallFrame frame = frameStack[frameStackTop -1];
	
		if ( frame == null )	
			frameStack[frameStackTop -1] = (frame = new CallFrame(this));
		
		return frame;
	}

	public CallFrame pushCallFrame( LuaClosure closure, int localBase, int returnBase, int argCount ){
		pullNewFrame();
		
		CallFrame frame = getCurrentFrame();
			frame.setup(closure, localBase, returnBase, argCount);
			
		return frame;
	}
	public CallFrame pushJavaFrame( Callable func, int localBase, int returnBase, int argCount ){
		pullNewFrame();
		
		CallFrame frame = getCurrentFrame();
			frame.setup(null, localBase, returnBase, argCount);
			
			frame.function	= func;
			frame.canYield	= false;
			
		return frame;
	}
	
	public void popCallFrame(){
		if ( frameStackTop == 0 )
			throw new LuaException("Frame stack undeflow");
		
		CallFrame popped = getCurrentFrame();
			popped.closure	= null;
			popped.function	= null;
			
		frameStackTop--;
	}
	
	protected void pullNewFrame(){
		int newTop 	= frameStackTop +1;
		int size	= frameStack.length;
		
		if ( newTop > MAX_FRAME_SIZE )
			throw new LuaException("Frame stack overflow");
		
		if ( size < newTop ){ //Realloc
			size <<= 1;
			
			CallFrame[] realloc = new CallFrame[size];
			System.arraycopy(frameStack, 0, realloc, 0, frameStack.length);
			frameStack = realloc;
		}
		
		frameStackTop = newTop;
	}
	
	public int getFrameTop(){
		return frameStackTop;
	}
	
	public CallFrame getCallFrame(int index) {
		if (index < 0)
			index += frameStackTop;
		
		return frameStack[index];
	}
	
	public boolean isAtBottom(){
		return frameStackTop == 1;
	}
	public boolean isDead(){
		return frameStackTop == 0;
	}
	
	
	/*
	 * Upvalues
	 */
	public void closeUpvalues( int index ){
		int loopIndex = upvalues.size();
		
		while( --loopIndex >= 0 ){
			UpValue upvalue = upvalues.elementAt(loopIndex);
			
			if ( upvalue.getIndex() < index )
				return;
			
			upvalue.close();
			upvalues.removeElementAt(loopIndex);
		}
	}
	
	public UpValue findUpvalue( int index ){
		int loopIndex = upvalues.size();
		
		while( --loopIndex >= 0 ){
			UpValue upvalue = upvalues.elementAt(loopIndex);
			int currIndex = upvalue.getIndex();
			
			if ( currIndex == index )
				return upvalue;
			
			if ( currIndex < index )
				break; //Not found, create!
		}
		
		UpValue upvalue = new UpValue(this, index);
		
		upvalues.insertElementAt(upvalue, loopIndex +1);
		return upvalue;
	}
	
	/*
	 * Misc
	 */
	protected void beginStackTrace( CallFrame frame, Throwable err ){
		stackTrace.append(frame.getSourceLocation());	
		stackTrace.append(": ");
		
		if ( err instanceof LuaException ){
			LuaException error = (LuaException) err;
			
			stackTrace.append( error.getLuaCause() );
		} else {
			stackTrace.append( err.toString() );
			
			err.printStackTrace();
		}
		
		stackTrace.append('\n');
		
		stackTraceLevel = -1;
	}
	protected void addStackTrace( CallFrame frame ){
		 //Skip the first frame, as it was already added in beginStackTrace
		if ( ++stackTraceLevel == 0 ) return;
		
		//Build the prefix
		for ( int index = 0; index < stackTraceLevel; index++ )
			stackTrace.append( ' ' );
		
		stackTrace.append(stackTraceLevel);
		stackTrace.append(". ");
		
		//Trace back the source of the function on the stack
		if ( frame.isLua() ){
			int lastOp = frame.closure.proto.code[ frame.pc -1 ];
			
			String origin = LuaUtil.findSlotOrigin(frame, LuaOpcodes.getA8(lastOp));
				
			if ( origin == null )
				origin = "unknown";
				
			stackTrace.append(origin);
		} else {
			stackTrace.append("java call");
		}

		//Add location
		stackTrace.append(" - ");
		
		stackTrace.append(frame.getSourceLocation());
		stackTrace.append('\n');
	}
	
	public String getStackTrace(){
		return stackTrace.toString();
	}
	public void resetStackTrace(){
		stackTrace = new StringBuilder();
		stackTraceLevel = 0;
	}
	
	
	public Coroutine getParent(){
		return parent;
	}
	public LuaTable getEnv(){
		return env;
	}

	public String getStatus(){
		if ( parent == null ){
			return isDead() ? "dead" : "suspended";
		}
		
		return "normal";
	}
	
	public void resume( Coroutine parent ){
		this.parent = parent;
		this.thread = parent.thread;
	}
	public void detach() {
		this.parent	= null;
		this.thread	= null;
	}

}
