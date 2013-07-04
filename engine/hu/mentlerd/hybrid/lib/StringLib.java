package hu.mentlerd.hybrid.lib;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;
import hu.mentlerd.hybrid.LuaClosure;
import hu.mentlerd.hybrid.LuaException;
import hu.mentlerd.hybrid.LuaTable;
import hu.mentlerd.hybrid.LuaThread;
import hu.mentlerd.hybrid.LuaUtil;

public enum StringLib implements Callable{
	
	SUB {
		public int call(CallFrame frame, int argCount) {
			String string = frame.getArg(0, String.class);
			
			int start = frame.getIntArg(1);
			int limit = frame.getIntArg(2, string.length());
			
			//Magic negative start/limit
			int len = string.length();
			
			if ( start < 0 )
				start = Math.max(len + start +1, 1);
			else if ( start == 0 )
				start = 1;
			
			if ( limit < 0 )
				limit = Math.max(limit + len +1, 0);
			else if ( limit > len )
				limit = len;
			
			//substring
			if ( start > limit )
				frame.push("");
			else
				frame.push( string.substring(start -1, limit) );
			
			return 1;
		}
	},
	
	CHAR {
		public int call(CallFrame frame, int argCount) {
			StringBuilder sb = new StringBuilder();
			
			for ( int index = 0; index < argCount; index++ )
				sb.append((char) frame.getIntArg(index));
			
			frame.push( sb.toString() );
			return 1;
		}
	},
	BYTE {
		public int call(CallFrame frame, int argCount) {
			String string = frame.getArg(0, String.class);
			
			int start = frame.getIntArg(1, 1);
			int limit = frame.getIntArg(2, 1);
			
			//Magic negative start/limit
			int len = string.length();
			
			if ( start < 0 )
				start = start + len +1;
			if ( start <= 0 )
				start = 1;
			
			if ( limit < 0 )
				limit = limit + len +1;
			else if ( limit > len )
				limit = len;
			
			//chars
			int chars = limit - start +1;
			
			if ( chars <= 0 )
				return 0;
			
			frame.setTop(chars);
			for ( int index = 0; index < chars; index++ )
				frame.set(index, Double.valueOf( string.charAt(index + start -1) ));
			
			return chars;
		}
	},
	
	LOWER {
		public int call(CallFrame frame, int argCount) {
			String string = frame.getArg(0, String.class);
			
			frame.push( string.toLowerCase() );
			return 1;
		}
	},
	UPPER {
		public int call(CallFrame frame, int argCount) {
			String string = frame.getArg(0, String.class);
			
			frame.push( string.toUpperCase() );
			return 1;
		}
	},
	
	REVERSE {
		public int call(CallFrame frame, int argCount) {
			String string = frame.getArg(0, String.class);
				string = new StringBuilder(string).reverse().toString();
			
			frame.push(string);
			return 1;
		}
	},
	
	LEN {
		public int call(CallFrame frame, int argCount) {
			String string = frame.getArg(0, String.class);
			
			frame.push( string.length() );
			return 1;
		}
	},
	REP {
		public int call(CallFrame frame, int argCount) {
			String rep	= frame.getArg(0, String.class);
			int count	= frame.getIntArg(1);
			
			StringBuilder sb = new StringBuilder(rep);	
			for ( int index = 1; index < count; index++ )
				sb.append(rep);
			
			frame.push( sb.toString() );
			return 1;
		}
	},
	
	FORMAT {
		public int call(CallFrame frame, int argCount) {
			throw new LuaException("unimplemented string.format");
		}
	},
	
	FIND {
		public int call(CallFrame frame, int argCount) {
			return find(frame, argCount, true);
		}
	},
	MATCH {
		public int call(CallFrame frame, int argCount) {
			return find(frame, argCount, false);
		}
	},
	
	GMATCH {
		public int call(CallFrame frame, int argCount) {
			String string	= frame.getArg(0, String.class);
			String pattern	= frame.getArg(1, String.class);
			
			frame.push( new MatchIterator(string, pattern) );
			return 1;
		}
	},
	
	GSUB {
		public int call(CallFrame frame, int argCount) {
			String string	= frame.getArg(0, String.class);
			String pattern	= frame.getArg(1, String.class);
			
			Object object	= frame.getArg(2);
			int limit		= frame.getIntArg(3, Integer.MAX_VALUE);
			
			if ( !( object instanceof String || object instanceof LuaClosure || 
					object instanceof Callable || object instanceof LuaTable ) ){
				
				throw LuaUtil.argError(2, "string/function/table expected");
			}
			
			LuaThread thread = frame.getThread();
			
			Pattern finder	= Pattern.compile(pattern);
			Matcher matcher	= finder.matcher(string);
			
			StringBuilder sb = new StringBuilder();
			
			int index = 0;
			int last = 0;
			
			while( index < limit ){ //Iterate over
				if ( !matcher.find() )
					break;
				
				//Append inner parts
				int start = matcher.start();
			
				if ( last < start )
					sb.append( string.substring(last, start) );
				
				last = matcher.end();
				
				//Replace
				String replace = LuaUtil.rawToString(object);
				
				if ( replace == null ){
					String match = matcher.group();
					Object value = null;
					
					if ( object instanceof LuaTable )
						value = ((LuaTable) object).rawget(match);
					else
						value = thread.call(object, match);
					
					if ( value == null )
						replace = match;
					else
						replace = LuaUtil.rawToString(value);
				}
				
				sb.append(replace);
			}
			
			//Append the ending
			sb.append( string.substring(last) );
			
			frame.push( sb.toString() );
			return 1;
		}
	};
	
	protected static final String SPECIALS = "^$*+?.([%-"; 
	protected static boolean hasSpecials( String pattern ){
		for ( int index = 0; index < pattern.length(); index++ ){
			if ( SPECIALS.indexOf( pattern.charAt(index) ) != -1 )
				return true;
		}
		
		return false;
	}
	
	protected static int find(CallFrame frame, int argCount, boolean isFind){
		String string	= frame.getArg(0, String.class);
		String pattern	= frame.getArg(1, String.class);
		
		int init = frame.getIntArg(2, 1) -1;
		int len = string.length();
		
		boolean plain = LuaUtil.toBoolean( frame.getArg(3, Boolean.FALSE) );
		
		//Magic negative limit
		if ( init < 0 )
			init = Math.max( init + len, 0 );
		else if ( init > len )
			init = len;
		
		//Do plain search on request, or no specials
		if ( isFind && ( plain || !hasSpecials(pattern) ) ){
			int pos = string.indexOf(pattern, init);
			
			if ( pos > -1 ){
				frame.push(pos);
				frame.push(pos + string.length());
				return 2;
			}
		} else {
			if ( init != 0 )
				string = string.substring(init);
			
			Pattern finder	= Pattern.compile(pattern.replace('%', '\\'));
			Matcher matcher	= finder.matcher(string);
			
			if ( matcher.find() ){
				int rets = matcher.groupCount();
				
				if ( isFind ){ //Push region in before matches
					frame.push( matcher.start() );
					frame.push( matcher.end() );
					rets += 2;
				} else {
					frame.push( matcher.group() );
					rets += 1;
				}
				
				for ( int index = 0; index < matcher.groupCount(); index++ ) //Push groups
					frame.push( matcher.group(index +1) );
				
				return rets;
			}
		}
		
		frame.push( null );
		return 1;
	}

	protected static class MatchIterator implements Callable{
		protected Matcher matcher;
		
		public MatchIterator( String string, String pattern ){
			Pattern finder	= Pattern.compile(pattern.replace('%', '\\'));
			this.matcher	= finder.matcher(string);
		}
		
		public int call(CallFrame frame, int argCount) {
			if ( matcher.find() ){
				int groups = matcher.groupCount();
				
				if ( groups == 0 ){
					frame.push( matcher.group() ); //Push the matched region
					
					return 1;
				} else {
					for ( int index = 0; index < groups; index++ ) //Push groups
						frame.push( matcher.group(index +1) );
					
					return groups;
				}
			} else {
				frame.push(null);
				return 1;
			}
		}
	}
	
}
