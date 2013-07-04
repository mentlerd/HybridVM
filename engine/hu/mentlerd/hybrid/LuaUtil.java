package hu.mentlerd.hybrid;

import static hu.mentlerd.hybrid.LuaOpcodes.*;

import java.util.Arrays;
import java.util.Comparator;

public class LuaUtil {
	
	/*
	 * General
	 */

		
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
		protected Object func;
			
		public int compare( Object A, Object B ) {
			if ( A == null ) return  1;
			if ( B == null ) return -1;
	
			boolean isLess;
			
			if ( func != null ){
				isLess = LuaUtil.toBoolean( thread.call(func, A, B) );
			} else {							
				isLess = thread.compare(A, B, LuaOpcodes.OP_LT);
			}
		
			return isLess ? -1 : 1;
		}
	}
	
	public static void sort( LuaTable table, LuaThread thread, Object func ){
		if ( func != null && !(func instanceof LuaClosure || func instanceof Callable) )
			throw new IllegalArgumentException("Illegal comparator function");
		
		Object[] values = table.array;
		
		LuaComparator comparator = new LuaComparator();
			comparator.thread	= thread;
			comparator.func		= func;
		
		Arrays.sort(values, comparator);
	}
	
	/*
	 * Errors
	 */
	public static Object getExceptionCause( Throwable err ){
		if ( err instanceof LuaException )
			return ((LuaException) err).getLuaCause();
		
		return err.getMessage();
	}
	
	public static LuaException argError(int arg, String desc) {
		return new LuaException("bad argument to #" + arg + "(" + desc + ")");
	}
	
	public static LuaException argError( int arg, Class<?> expected, Platform platform ){
		return new LuaException("bad argument to #" + arg + " (expected " + platform.getTypename(expected) +", got no value)");
	}
	
	public static LuaException argError( int arg, Class<?> expected, Object got, Platform platform ){
		return new LuaException("bad argument to #" + arg + " (expected " + platform.getTypename(expected) +", got "+ platform.getTypename(got) + ")");
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
