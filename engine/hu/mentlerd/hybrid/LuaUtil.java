package hu.mentlerd.hybrid;

import static hu.mentlerd.hybrid.LuaOpcodes.*;

public class LuaUtil {
	
	/*
	 * General
	 */
	public static LuaTable getRawMetatable( Object value, Platform platform ){
		LuaTable meta = null;
		
		if ( value instanceof LuaTable )
			meta = ((LuaTable) value).getMetatable();
		
		if ( meta == null && value != null )
			meta = platform.getClassMetatable( value.getClass() );
		
		return meta;
	}
	
	public static Object getMetatable( Object value, Platform platform ){
		LuaTable meta = getRawMetatable(value, platform);
		
		if ( meta != null ){
			Object override = meta.rawget("__metatable");
			
			if ( override != null )
				return override;
		}
		
		return meta;
	}
	
	public static String getTypename( Object value, Platform platform ){
		if ( value == null )
			return "nil";
		
		return getTypename(value.getClass(), platform);
	}
	public static String getTypename( Class<?> clazz, Platform platform ){
		Object name = platform.getClassMetavalue(clazz, "__type");
		
		if ( name == null )
			return clazz.getSimpleName();
		
		return name.toString();
	}
		
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
		return new LuaException("bad argument to #" + arg + " (expected " + getTypename(expected, platform) +", got no value)");
	}
	
	public static LuaException argError( int arg, Class<?> expected, Object got, Platform platform ){
		return new LuaException("bad argument to #" + arg + " (expected " + getTypename(expected, platform) +", got "+ getTypename(got, platform) + ")");
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
		err.append( getTypename(frame.get(slot), frame.getPlatform()) );
		err.append(" value)");
		
		return new LuaException(err.toString());
	}
}
