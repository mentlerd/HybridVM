package hu.mentlerd.hybrid.lib;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;
import hu.mentlerd.hybrid.Coroutine;
import hu.mentlerd.hybrid.LuaClosure;
import hu.mentlerd.hybrid.LuaException;

public enum CoroutineLib implements Callable{

	CREATE {
		public int call(CallFrame frame, int argCount) {
			LuaClosure closure = getFunction(frame);
			
			Coroutine coroutine = new Coroutine(closure, frame.getEnv(), frame.getPlatform());
			frame.push( coroutine );
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
	
	protected LuaClosure getFunction( CallFrame frame ){
		Object obj = frame.getArg(0);
		
		if ( !(obj instanceof LuaClosure) )
			throw new LuaException("argument must be a lua function");
		
		return (LuaClosure) obj;
	}
	
}
