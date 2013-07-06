package hu.mentlerd.hybrid.lib;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;
import hu.mentlerd.hybrid.Coroutine;
import hu.mentlerd.hybrid.LuaClosure;
import hu.mentlerd.hybrid.LuaException;
import hu.mentlerd.hybrid.LuaTable;
import hu.mentlerd.hybrid.LuaUtil;

public enum CoroutineLib implements Callable{

	CREATE {
		public int call(CallFrame frame, int argCount) {
			LuaClosure closure = getFunction(frame);
			
			Coroutine coroutine = new Coroutine(frame.getPlatform(), frame.getEnv(), closure);
			frame.push( coroutine );
			return 1;
		}
	},
	WRAP {
		public int call(CallFrame frame, int argCount) {
			LuaClosure closure = getFunction(frame);
			
			Coroutine coroutine = new Coroutine(frame.getPlatform(), frame.getEnv(), closure);
			frame.push( new WrappedCoroutine(coroutine) );
			return 1;
		}
	},
	RESUME {
		public int call(CallFrame frame, int argCount) {
			Coroutine coroutine = frame.getArg(0, Coroutine.class);
			
			if ( coroutine.isDead() )
				throw new LuaException("unable to resume a dead coroutine");
			
			coroutine.resume( frame.coroutine );
			
			CallFrame nextFrame = coroutine.getCurrentFrame();
			boolean isFirst = ( nextFrame.argCount == -1 );
			
			if ( isFirst ) //First time resuming, setup stack!
				nextFrame.setTop(0);
			
			for ( int index = 1; index < argCount; index++ ) //Push arguments
				nextFrame.push( frame.get(index) );
			
			if ( isFirst ){ //First time resuming, initialize the frame
				nextFrame.argCount = argCount -1;
				nextFrame.init();
			}
			
			//Set the coroutine here, the thread will handle from there
			frame.getThread().coroutine = coroutine;
			return 0;
		}
	},
	STATUS {
		public int call(CallFrame frame, int argCount) {
			Coroutine coroutine = frame.getArg(0, Coroutine.class);
			
			frame.push( coroutine.getStatus() );
			return 1;
		}
	},
	YIELD {
		public int call(CallFrame frame, int argCount) {
			Coroutine coroutine = frame.coroutine;
			Coroutine parent	= coroutine.getParent();
			
			if ( parent == null )
				throw new LuaException("Cannot yield outside a coroutine");
			
			CallFrame parentFrame = coroutine.getCallFrame(-2);
			Coroutine.yield(parentFrame, frame, argCount);
			return 0;
		}
	};
	
	protected static class WrappedCoroutine implements Callable{
		protected final Coroutine coroutine;
		
		public WrappedCoroutine( Coroutine coroutine ){
			this.coroutine = coroutine;
		}
		
		public int call(CallFrame frame, int argCount) {
			Object[] args = new Object[argCount];
			Object[] rets = null;
			
			for ( int index = 0; index < argCount; index++ )
				args[index] = frame.get(index);
			
			rets = frame.getThread().resume(coroutine, args);
			
			if ( LuaUtil.toBoolean(rets[0]) ){
				int retCount = rets.length -1;
				
				for ( int index = 0; index < retCount; index++ )
					frame.push( rets[index +1] );
				
				return retCount;
			} else {
				throw new LuaException( rets[1].toString() );
			}
		}
	}
	
	protected LuaClosure getFunction( CallFrame frame ){
		Object obj = frame.getArg(0);
		
		if ( !(obj instanceof LuaClosure) )
			throw new LuaException("argument must be a lua function");
		
		return (LuaClosure) obj;
	}
	
	public static LuaTable bind(){
		return bind( new LuaTable() );
	}
	public static LuaTable bind( LuaTable into ){
		for ( CoroutineLib entry : values() )
			into.rawset(entry.name().toLowerCase(), entry);
		
		return into;
	}
}
