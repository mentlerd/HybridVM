package hu.mentlerd.hybrid;

public class CallFrame{
	
	public final Coroutine coroutine;
	
	public LuaClosure closure;
	public Callable function;
	
	public int pc;

	public int localBase;
	public int argCount;

	protected int returnBase;
	
	protected boolean fromLua;
	protected boolean restoreTop;
	
	public boolean canYield;
	
	protected CallFrame( Coroutine coroutine ){
		this.coroutine	= coroutine;
	}

	public void setup( LuaClosure closure, int localBase, int returnBase, int argCount ){
		this.closure	= closure;
		this.function	= null;
		
		this.localBase	= localBase;
		this.returnBase = returnBase;
		this.argCount	= argCount;
	}
	
	public void init(){
		this.pc = 0;
		
		if ( closure != null ){
			Prototype proto = closure.proto;
			
			if ( proto.isVararg ){
				localBase += argCount;
			
				setTop(proto.maxStacksize);
				stackCopy(-argCount, 0, Math.min(argCount, proto.numParams));
			} else {
				setTop(proto.maxStacksize);
				stackClear(proto.numParams, argCount);
			}
		}
	}
	
	/*
	 * Stack
	 */
	public void set( int index, Object value ){
		if ( getTop() <= index )
			throw new RuntimeException("Script ignored top!");
		
		coroutine.stack[localBase + index] = value;
	}
	public Object get( int index ){
		return coroutine.stack[localBase + index];
	}
	
	public void setTop( int top ){
		coroutine.setTop(localBase + top);
	}	
	public int getTop(){	
		return coroutine.getTop() - localBase;
	}

	protected void clearFromIndex( int index ){
		if ( getTop() < index )
			setTop(index);
		
		stackClear(index, getTop() -1);
	}	

	protected void stackClear( int index, int end ){
		coroutine.stackClear(localBase + index, localBase + end);
	}
	protected void stackCopy( int index, int dest, int len ){
		coroutine.stackCopy(localBase + index, localBase + dest, len);
	}
	
	/*
	 * Push
	 */
	public void push( Object value ) {
		int top = getTop();
		
		setTop(top +1);
		set(top, value);
	}
	public void push( int value ){
		push( Double.valueOf(value) );
	}
	
	
	protected void pushVarargs(int index, int n) {
		int nParams		= closure.proto.numParams;
		int nVarargs	= argCount - nParams;
		
		if ( nVarargs < 0 ) 
			nVarargs = 0;
		
		if (n == -1) {
			n = nVarargs;
			setTop(index + n);
		}
		
		if (nVarargs > n) 
			nVarargs = n;
		
		stackCopy(-argCount + nParams, index, nVarargs);
		
		int nilCount = n - nVarargs;
		if (nilCount > 0)
			stackClear(index + nVarargs, index + n -1);
	}
	
	/*
	 * Misc
	 */
	public void closeUpvalues(int limit) {
		coroutine.closeUpvalues(localBase + limit);
	}
	public UpValue findUpvalue(int index) {
		return coroutine.findUpvalue(localBase + index);
	}
	
	public void setPrototypeStacksize() {
		if ( closure != null )
			setTop(closure.proto.maxStacksize);
	}

	public String getSourceLocation(){
		if ( closure != null ){
			int lines[] = closure.proto.lines;
			
			if ( lines != null ){
				int index = pc -1;
				
				if ( index >= 0 && index < lines.length )
					return closure.proto.source + ":" + lines[index];
			
			} else {
				return closure.proto.source + ":-1";
			}
		}
		
		return "[J]:" + function;
	}

	public CallFrame getParent( int level ){
		int index = coroutine.getFrameTop() - level -1;
		
		if ( level <= 0 || index <= 0 )
			throw new IllegalArgumentException("Illegal level index");
		
		return coroutine.getCallFrame(index);
	}
	
	public String findLocalVar( int slot ){
		if ( isLua() )
			return closure.proto.findLocalName(slot, pc);
		
		return null;
	}
	
	public boolean isLua() {
		return closure != null;
	}

	/*
	 * CallInfo implementation
	 */
	public Platform getPlatform() {
		return coroutine.platform;
	}
	public LuaTable getEnv(){
		if ( isLua() )
			return closure.env;
		
		return coroutine.getEnv();
	}
	public LuaThread getThread() {
		return coroutine.thread;
	}
	
	public int getArgCount() {
		return argCount;
	}

	public Object getArg(int n) {
		if ( argCount <= n )
			throw new LuaException("bad argument to #" + n + " (value expected)");
		
		return get(n);
	}
	
	public Object getArgNull(int n){
		if ( argCount <= n )
			return null;
		
		return get(n);
	}
	
	public Object getArg(int n, Object fallback) {
		if ( argCount <= n )
			return fallback;
		
		Object arg = get(n);
		if ( arg == null )
			arg = fallback;
		
		return arg;
	}

	public <T> T getArg(int n, Class<T> clazz) {
		if ( argCount <= n )
			throw LuaUtil.argError(n, clazz, coroutine.platform);
		
		Object arg = get(n);
		if ( arg == null || !clazz.isAssignableFrom( arg.getClass() ) )
			throw LuaUtil.argError(n, clazz, arg, coroutine.platform);
			
		return clazz.cast(arg);
	}
	
	public <T> T getArgNull(int n, Class<T> clazz) {
		if ( argCount <= n )
			return null;
		
		Object arg = get(n);
		if ( arg != null && !clazz.isAssignableFrom( arg.getClass() ) )
			throw LuaUtil.argError(n, clazz, arg, coroutine.platform);
		
		return clazz.cast(arg);
	}
	
	public <T> T getArg(int n, Class<T> clazz, T fallback) {
		if ( argCount <= n )
			throw LuaUtil.argError(n, clazz, coroutine.platform);
		
		Object arg = get(n);
		
		if ( arg == null )
			return fallback;
		
		if ( !clazz.isAssignableFrom( arg.getClass() ) )
			throw LuaUtil.argError(n, clazz, arg, coroutine.platform);
		
		return clazz.cast(arg);
	}
	
	public int getIntArg(int n){
		if ( argCount <= n )
			throw new LuaException("bad argument to #" + n + " (value expected)");
		
		Double number = getArg(n, Double.class);
		
		if ( number != number.intValue() )
			throw LuaUtil.argError(n, "expected int value");
	
		return number.intValue();
	}
	public int getIntArg(int n, int fallback){
		if ( argCount <= n )
			return fallback;
	
		Double number = getArg(n, Double.class);
		
		if ( number != number.intValue() )
			throw LuaUtil.argError(n, "expected int value");
	
		return number.intValue();
	}
	
	public <T> T getNamedArg(int n, Class<T> clazz, String slot){
		if ( argCount <= n )
			throw LuaUtil.argError(slot, clazz, coroutine.platform);
		
		Object arg = get(n);
		if ( arg == null || !clazz.isAssignableFrom( arg.getClass() ) )
			throw LuaUtil.argError(slot, clazz, arg, coroutine.platform);
			
		return clazz.cast(arg);
	}
	
}
