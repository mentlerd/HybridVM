package hu.mentlerd.hybrid.lib;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;
import hu.mentlerd.hybrid.LuaTable;
import hu.mentlerd.hybrid.LuaUtil;

public enum TableLib implements Callable{

	CONCAT {
		public int call(CallFrame frame, int argCount) {
			LuaTable table	= frame.getArg(0, LuaTable.class);
			String sep		= frame.getArgNull(1, String.class);
			
			int start		= frame.getIntArg(2, 1);
			int limit		= frame.getIntArg(3, table.size());
			
			if ( limit == 0 || limit < start ){
				frame.push("");
				return 1;
			}

			StringBuilder result = new StringBuilder();
			
			for ( int index = start; index <= limit; index++ ){
				result.append( LuaUtil.rawToString(table.rawget(index)) );
				
				if ( sep != null && index != limit )
					result.append(sep);
			}
			
			frame.push(result.toString());
			return 1;
		}
	},
	
	INSERT {
		public int call(CallFrame frame, int argCount) {
			LuaTable table	= frame.getArg(0, LuaTable.class);
			
			Object value	= null;
			int target		= 0;
			
			if ( argCount > 2 ){ //table.insert( table, where, what )
				value  = frame.getArg(2);
				target = frame.getIntArg(1);
			} else { //table.insert( table, what )
				value  = frame.getArg(1);
				target = table.maxN() +1;
			}
		
			table.insert(value, target);
			return 0;
		}
	},
	REMOVE {
		public int call(CallFrame frame, int argCount) {
			LuaTable table	= frame.getArg(0, LuaTable.class);
			int index		= 0;
			
			if ( argCount > 1 ){ //maxN is expensive, try to avoid it!
				index = frame.getIntArg(1);
			} else {
				index = table.maxN();
			}
			
			table.remove(index);
			return 0;
		}
	},
	
	MAXN {
		public int call(CallFrame frame, int argCount) {
			LuaTable table	= frame.getArg(0, LuaTable.class);
			
			frame.push(table.maxN());
			return 1;
		}
	},
	
	SORT {
		public int call(CallFrame frame, int argCount) {
			LuaTable table	= frame.getArg(0, LuaTable.class);
			Object comp		= frame.getArgNull(1);
			
			LuaUtil.sort(table, frame.getThread(), comp);
			return 0;
		}
	};
	
	public static LuaTable bind(){
		return bind( new LuaTable() );
	}
	public static LuaTable bind( LuaTable into ){
		for ( TableLib entry : values() )
			into.rawset(entry.name().toLowerCase(), entry);
		
		return into;
	}
}
