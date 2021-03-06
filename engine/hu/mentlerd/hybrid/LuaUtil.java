package hu.mentlerd.hybrid;

import static hu.mentlerd.hybrid.LuaOpcodes.*;

import java.util.Arrays;
import java.util.Comparator;

public class LuaUtil {

	/*
	 * Conversion
	 */
	public static boolean toBoolean( Object value ){
		return value != null && value != Boolean.FALSE;
	}
	
	public static Double rawToNumber( Object value ){
		if ( value instanceof Double )
			return (Double) value;
		
		//String tonumber
		return null;
	}
	
	public static String rawToString( Object value ){
		if ( value == null )
			return "nil";
	
		if ( value instanceof String )
			return (String) value;
	
		if ( value instanceof Double )
			return value.toString();
		
		return null;
	}
	
	/*
	 * Sorting
	 */
	protected static class LuaComparator implements Comparator<Object>{
		protected LuaThread thread;
		
		protected Object comparator;
		protected boolean order;
		
		public LuaComparator( LuaThread thread, Object func, boolean desc ){
			this.thread	= thread;
			
			this.comparator	= func;
			this.order		= desc;
		}
		
		public int compare( Object A, Object B ) {
			if ( A == null ) return  1;
			if ( B == null ) return -1;
	
			boolean isLess;
			
			if ( comparator != null ){
				isLess = LuaUtil.toBoolean( thread.call(comparator, A, B) );
			} else {							
				isLess = thread.compare(A, B, LuaOpcodes.OP_LT);
			}
		
			return (isLess != order) ? -1 : 1;
		}
	}
	
	public static void sort( LuaTable table, LuaThread thread, Object func ){
		sort(table, thread, func, false);
	}
	public static void sort( LuaTable table, LuaThread thread, Object func, boolean desc ){
		if ( func != null && !LuaThread.isCallable(func) )
			throw new IllegalArgumentException("Illegal comparator function");
		
		Arrays.sort(table.array, new LuaComparator(thread, func, desc));
	}
	
	/*
	 * Errors
	 */
	public static Object getExceptionCause( Throwable err ){
		if ( err instanceof LuaException )
			return ((LuaException) err).getLuaCause();
		
		return err.getMessage();
	}

	public static LuaException argError( String slot, String desc ){
		return new LuaException("bad argument to " + slot + " (" + desc + ")" );
	}
	public static LuaException argError( String slot, Class<?> expected, Platform platform ){
		return argError(slot, "expected " + platform.getTypename(expected) +", got no value");
	}
	public static LuaException argError( String slot, Class<?> expected, Object got, Platform platform ){
		return argError(slot, "expected " + platform.getTypename(expected) +", got "+ platform.getTypename(got));
	}


	public static LuaException argError( int index, String desc ){
		return argError("#" + index, desc);
	}
	public static LuaException argError( int index, Class<?> expected, Platform platform ){
		return argError("#" + index, expected, platform);
	}
	public static LuaException argError( int index, Class<?> expected, Object got, Platform platform ){
		return argError("#" + index, expected, got, platform);
	}
	
	
	/*
	 * Thread errors
	 */
	public static String findSlotOrigin( CallFrame frame, int slot ){
		Prototype proto = frame.closure.proto;
		
		int[] opcodes 	= proto.code;
		int pc			= frame.pc -1;
		
		while( pc-- > 0 ){
			int code = opcodes[pc];
			
			if ( getA8(code) == slot ){ //Only process opcodes pointing to the slot
				switch( getOp(code) ){
					
					case OP_MOVE: //Moved, most likely from the argument section
						return frame.findLocalVar( getB9(code) );
						
					case OP_GETGLOBAL: //Got from global index
						return "global "+proto.constants[ getBx(code) ];
					
					case OP_GETUPVAL: //Upvalue
						return proto.upvalues[ getB9(code) ];
						
					case OP_SELF: //Self call, it is a 'method'
						return "method "+proto.constants[ getC9(code) -256 ];
						
					default: //Got from elsewhere. No idea from where
						return null;
	
				}	
			}
		}
		
		//Was not on stack
		return null;
	}
	
	public static LuaException slotError( CallFrame frame, int slot, String error ){
		StringBuilder err = new StringBuilder(error);
			err.append(' ');
			
		Object origin = findSlotOrigin(frame, slot);
		if ( origin == null ){
			err.append("?");
		} else {
			err.append(origin);
		}
		
		err.append(" (a ");
		err.append( frame.getPlatform().getTypename(frame.get(slot)) );
		err.append(" value)");
		
		return new LuaException(err.toString());
	}
}
