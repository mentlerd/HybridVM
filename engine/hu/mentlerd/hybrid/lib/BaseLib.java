package hu.mentlerd.hybrid.lib;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;
import hu.mentlerd.hybrid.LuaClosure;
import hu.mentlerd.hybrid.LuaException;
import hu.mentlerd.hybrid.LuaTable;
import hu.mentlerd.hybrid.LuaThread;
import hu.mentlerd.hybrid.LuaUtil;
import hu.mentlerd.hybrid.Platform;

public enum BaseLib implements Callable{

	PCALL {
		public int call(CallFrame frame, int argCount) {
			return frame.getThread().pcall( argCount -1 );
		}
	},
	
	PRINT {
		public int call(CallFrame frame, int argCount){
			LuaThread thread 	= frame.getThread();
			LuaTable env		= frame.getEnv();
				
			Object tostring	= thread.tableGet(env, "tostring");
			
			if ( tostring == null )
				tostring = BaseLib.TOSTRING;
			
			StringBuilder sb = new StringBuilder();
			for ( int index = 0; index < argCount; index++ ){
				if ( index > 0 ) sb.append("\t");
				
				sb.append( thread.call(tostring, frame.get(index)) );
			}
			
			System.out.println( sb.toString() );
			return 0;
		}
	},
	
	SELECT {
		public int call(CallFrame frame, int argCount) {
			Object index = frame.getArg(0);
			
			if ( index instanceof String ){
				String string = (String) index;
				
				if ( string.startsWith("#") ){
					frame.push( argCount -1 );
					return 1;
				}
			}
			
			int limit = frame.getIntArg(0);
			if ( limit >= 1 && limit < argCount ){
				return argCount - limit;
			}
			
			return 0;
		}
	},
	
	TYPE {
		public int call(CallFrame frame, int argCount) {
			Platform platform	= frame.getPlatform();
			Object value		= frame.getArg(0);
			
			frame.push( LuaUtil.getTypename(value, platform) );
			return 1;
		}
	},
	
	NEXT {
		public int call(CallFrame frame, int argCount) {
			LuaTable table	= frame.getArg(0, LuaTable.class);
			Object key		= frame.getArgNull(1);
			
			Object next	= table.nextKey(key);
			frame.push( next );
			
			if ( next == null ) {
				return 1;
			} else {
				frame.push( table.rawget(next) );
				return 2;
			}
		}
	},
	INEXT {
		public int call(CallFrame frame, int argCount) {
			LuaTable table	= frame.getArg(0, LuaTable.class);
			Double index	= frame.getArgNull(1, Double.class);
		
			if ( index == null )
				index = 0D;
			
			if ( index != index.intValue() )
				throw new LuaException("Bad argument to inext! Expected whole number as index");
			
			Object value = table.rawget(++index);
			
			if ( value == null ){
				frame.push(null);
				return 1;
			} else {
				frame.push(index);
				frame.push(value);
				return 2;
			}
		}
	},
	
	PAIRS {
		public int call(CallFrame frame, int argCount) {
			LuaTable table = frame.getArg(0, LuaTable.class);
			
			frame.push( BaseLib.NEXT );
			frame.push( table );
			frame.push( null );
			return 3;
		}
	},
	IPAIRS {
		public int call(CallFrame frame, int argCount) {
			LuaTable table = frame.getArg(0, LuaTable.class);
			
			frame.push( BaseLib.INEXT );
			frame.push( table );
			frame.push( null );
			return 3;
		}
	},
	
	TOSTRING {
		public int call(CallFrame frame, int argCount) {
			LuaThread thread	= frame.getThread();
			Object value		= frame.getArg(0);
			
			frame.push( thread.tostring(value) );
			return 1;
		}
	},
	TONUMBER {
		public int call(CallFrame frame, int argCount) {
			String number	= frame.getArg(0, String.class);
			int radix		= frame.getIntArg(1, 10);
			
			try{
				if ( radix == 10 ) {
					frame.push( Double.parseDouble(number) );
				} else {
					frame.push( Double.valueOf( Integer.parseInt(number, radix) ) );
				}
			} catch ( Exception e ){
				frame.push( null );
			}
			
			return 1;
		}
	},
	
	GETMETATABLE {
		public int call(CallFrame frame, int argCount) {
			LuaTable table	= frame.getArg(0, LuaTable.class);
			
			LuaTable meta	= table.getMetatable();
			Object override = null;
			
			if ( meta != null && (override = meta.rawget("__metatable")) != null )
				frame.push( override );
			else
				frame.push( meta );
			
			return 1;
		}
	},
	SETMETATABLE {
		public int call(CallFrame frame, int argCount) {
			LuaTable table	= frame.getArg(0, LuaTable.class);
			LuaTable nMeta	= frame.getArg(1, LuaTable.class);
			
			LuaTable meta	= table.getMetatable();
			
			if ( meta != null && meta.rawget("__metatable") != null )
				throw new LuaException("cannot change a protected metatable");

			table.setMetatable(nMeta);
			
			frame.push(table);
			return 1;
		}
	},
	
	ASSERT {
		public int call(CallFrame frame, int argCount) {
			if ( !LuaUtil.toBoolean( frame.getArgNull(0) ) ){
				Object cause = frame.getArg(1, "assertation failed");
				
				throw new LuaException( "lua assert failure", cause );
			}
			return argCount;
		}
	},
	
	ERROR {
		public int call(CallFrame frame, int argCount) {	
			throw new LuaException("lua thrown error", frame.getArg(0));
		}
	},
	
	UNPACK {
		public int call(CallFrame frame, int argCount) {
			LuaTable args = frame.getArg(0, LuaTable.class);
			
			int start = frame.getIntArg(1, 1);
			int limit = frame.getIntArg(2, args.size());
			
			if ( limit == 0 )
				return 0;
			
			if ( start < 1 || limit < start )
				throw new LuaException( "invalid unpack bounds" );
			
			int count = limit - start +1;
			
			frame.setTop( count );
			for ( int offset = 0; offset < count; offset++ )
				frame.set(offset, args.rawget( Double.valueOf(start + offset) ) );
			
			return count;
		}
	},

	SETFENV {
		public int call(CallFrame frame, int argCount) {
			LuaClosure func = frame.getArg(0, LuaClosure.class);
			LuaTable env	= frame.getArg(1, LuaTable.class);
			
			func.env = env;
			return 0;
		}
	},
	GETFENV {
		public int call(CallFrame frame, int argCount) {
			Object arg = frame.getArgNull(0);
									
			if ( arg instanceof Callable ){
				frame.push( frame.getEnv() );
			} else if ( arg instanceof LuaClosure ){
				LuaClosure func = (LuaClosure) arg;
				
				frame.push( func.env );
			} else {
				int level = frame.getIntArg(0, 1);
		
				if ( level == 0 )
					frame.push( frame.coroutine.getEnv() );
				else
					frame.push( frame.getParent(level).getEnv() );
			}
			return 1;
		}
	},
	
	RAWEQUAL {
		public int call(CallFrame frame, int argCount) {	
			Object a = frame.getArg(0);
			Object b = frame.getArg(1);
			
			if ( a == null || b == null ){
				frame.push( a == b );
			} else {
				frame.push( a.equals(b) );
			}
			return 1;
		}		
	},
	RAWSET {
		public int call(CallFrame frame, int argCount) {
			LuaTable table	= frame.getArg(0, LuaTable.class);
			
			Object key		= frame.getArg(1);
			Object value	= frame.getArg(2);
			
			table.rawset(key, value);
			return 0;
		}
	},
	RAWGET {
		public int call(CallFrame frame, int argCount) {
			LuaTable table	= frame.getArg(0, LuaTable.class);
			Object key		= frame.getArg(1);
			
			frame.push( table.rawget(key) );
			return 1;
		}
	},
	
}
