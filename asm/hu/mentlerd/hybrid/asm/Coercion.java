package hu.mentlerd.hybrid.asm;

import hu.mentlerd.hybrid.LuaTable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

public class Coercion {

	protected static final String BOOLEAN	= "java/lang/Boolean";
	protected static final String BYTE		= "java/lang/Byte";
	protected static final String CHAR		= "java/lang/Character";
	protected static final String SHORT		= "java/lang/Short";
	protected static final String INT		= "java/lang/Integer";
	protected static final String FLOAT		= "java/lang/Float";
	protected static final String LONG		= "java/lang/Long";
	protected static final String DOUBLE	= "java/lang/Double";
	
	protected static final String STRING	= "java/lang/String";
	
	protected static Map<String, Integer> numberCoercionMap = new HashMap<String, Integer>();
	
	static{
		numberCoercionMap.put(BYTE,		Type.BYTE);
		numberCoercionMap.put(SHORT,	Type.SHORT);
		numberCoercionMap.put(INT,		Type.INT);
		numberCoercionMap.put(FLOAT,	Type.FLOAT);
		numberCoercionMap.put(LONG, 	Type.LONG);
	}
	
	/**
	 * Tells if a null value can be coerced into the target class
	 * 
	 * (Only returns true on non boxed objects, apart from Boolean,
	 * that is evaluated like the Lua standard)
	 * 
	 * @param into The target class
	 * @return Whether it is possible to coerce a null value to the target class
	 */
	public static boolean canCoerceNull( Type into ){
		if ( into.getSort() == Type.OBJECT ){
			String clazz = into.getInternalName();
			
			if ( numberCoercionMap.containsKey(clazz) )
				return false;
			
			if ( clazz.equals(CHAR) )
				return false;
			
			return true;
		}
		
		return false;
	}
	
	
	/**
	 * Returns the class required to be on the stack to coerce it into the target class.
	 * 
	 * @param clazz The target class
	 * @return The class required on the stack
	 */
	public static Class<?> getCoercedClass( Class<?> clazz ){
		Type type = Type.getType(clazz);
		
		switch( type.getSort() ){
			
			//Primitives
			case Type.BOOLEAN:	return Boolean.class;
			case Type.CHAR:		return String.class;
			
			//Java numbers are coerced from Double objects
			case Type.BYTE:		
			case Type.SHORT:
			case Type.INT:
			case Type.FLOAT:
			case Type.LONG:
			case Type.DOUBLE:
				return Double.class;
			
			case Type.OBJECT:
				String clazzName = type.getInternalName();

				//Non primitive numbers object are coerced from Double objects
				if ( numberCoercionMap.containsKey(clazzName) )
					return Double.class;
				
				return clazz;
			
			//Arrays are coerced from LuaTables
			case Type.ARRAY:
				return LuaTable.class;
		}
	
		return null;
	}

	
	/**
	 * Coerces an object on the stack to a Lua value
	 * 
	 * (This method mainly deals with numbers, as they are represented as Double objects in Hybrid)
	 * 
	 * @param type Type of the value on the stack
	 */
	public static void varToLua( MethodVisitor mv, Type type ){	
		switch( type.getSort() ){
		
			//Primitives
			case Type.VOID:
				mv.visitInsn(ACONST_NULL);
				break;
			
			case Type.BOOLEAN: // boolean -> Boolean.valueOf
				mv.visitMethodInsn(INVOKESTATIC, BOOLEAN, 	"valueOf", "(Z)Ljava/lang/Boolean;");
				break;
			
			case Type.BYTE: // byte -> Byte -> double -> Double.valueOf
				mv.visitMethodInsn(INVOKESTATIC, BYTE, "valueOf", "(B)Ljava/lang/Byte;");
				mv.visitMethodInsn(INVOKEVIRTUAL, BYTE, "doubleValue", "()D");
				
				mv.visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
			
			case Type.CHAR: // char -> Char -> .toString
				mv.visitMethodInsn(INVOKESTATIC, CHAR, "valueOf", "(C)Ljava/lang/Character;");
				mv.visitMethodInsn(INVOKEVIRTUAL, CHAR, "toString", "()Ljava/lang/String;");
				break;
				
			case Type.SHORT: // short -> Short -> double -> Double.valueOf
				mv.visitMethodInsn(INVOKESTATIC, SHORT, "valueOf", "(S)Ljava/lang/Short;");
				mv.visitMethodInsn(INVOKEVIRTUAL, SHORT, "doubleValue", "()D");
				
				mv.visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
				
			case Type.INT: // int -> double -> Double.valueOf
				mv.visitInsn(I2D);
				mv.visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
				
			case Type.FLOAT: // float -> double -> Double.valueOf
				mv.visitInsn(F2D);
				mv.visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
				
			case Type.LONG: // long -> double -> Double.valueOf
				mv.visitInsn(L2D);
				mv.visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
				
			case Type.DOUBLE: // double -> Double.valueOf
				mv.visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
				
			//Casting objects is tricky. Check for number objects
			case Type.OBJECT:
				String clazz = type.getInternalName();
				
				//Check number classes (Unlikely to happen, but just in case)
				if ( numberCoercionMap.containsKey(clazz) ){
					mv.visitMethodInsn(INVOKEVIRTUAL, clazz, "doubleValue", "()D");
					mv.visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				}
				
				//Don't let Characters sneak trough, toString them
				if ( clazz.equals(CHAR) )
					mv.visitMethodInsn(INVOKEVIRTUAL, CHAR, "toString", "()Ljava/lang/String;");
				
				break;
				
			//Casting arrays is... terrible		TODO: Actually cast them you lazy fuck
			case Type.ARRAY:
				throw new RuntimeException("java -> lua array coercion not implemented");
		}
	}
	
	
	/**
	 * Coerces a Lua object on the stack to the desired class (Including primitives)
	 * 
	 * @param type Desired class
	 */
	public static void luaToVar( MethodVisitor mv, Type type ){
		switch( type.getSort() ){
			
			//Primitives
			case Type.BOOLEAN: // Boolean.booleanValue
				mv.visitTypeInsn(CHECKCAST, BOOLEAN);
				mv.visitMethodInsn(INVOKEVIRTUAL, BOOLEAN, "booleanValue", "()Z");
				break;
				
			case Type.BYTE: // Double.byteValue
				mv.visitTypeInsn(CHECKCAST, DOUBLE);
				mv.visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "byteValue", "()B");
				break;
			
			case Type.CHAR: // String.charAt(0)
				mv.visitTypeInsn(CHECKCAST, STRING);
				
				mv.visitInsn(ICONST_0);
				mv.visitMethodInsn(INVOKEVIRTUAL, STRING, "charAt", "(I)C");
				break;
				
			case Type.SHORT: // Double.shortValue
				mv.visitTypeInsn(CHECKCAST, DOUBLE);
				mv.visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "shortValue", "()S");
				break;
				
			case Type.INT: // Double.intValue
				mv.visitTypeInsn(CHECKCAST, DOUBLE);
				mv.visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "intValue", "()I");
				break;
				
			case Type.FLOAT: // Double.floatValue
				mv.visitTypeInsn(CHECKCAST, DOUBLE);
				mv.visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "floatValue", "()F");
				break;
				
			case Type.LONG: // Double.longValue
				mv.visitTypeInsn(CHECKCAST, DOUBLE);
				mv.visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "longValue", "()J");
				break;
				
			case Type.DOUBLE: // Double.doubleValue
				mv.visitTypeInsn(CHECKCAST, DOUBLE);
				mv.visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "doubleValue", "()D");
				break;
			
			//Casting objects is tricky. Check for number objects
			case Type.OBJECT:
				String clazz = type.getInternalName();
				
				//Check number classes (Extremely unlikely to happen)
				Integer numClass = numberCoercionMap.get(clazz);
				
				if ( numClass != null ){
					mv.visitTypeInsn(CHECKCAST, DOUBLE); // Object -> Double
					
					switch( numClass ){
						case Type.BYTE: // Double -> byte -> Byte
							mv.visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "byteValue", "()B");
							mv.visitMethodInsn(INVOKESTATIC, BYTE, "valueOf", "(B)Ljava/lang/Byte;");
							break;
							
						case Type.SHORT: // Double -> short -> Short
							mv.visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "shortValue", "()S");
							mv.visitMethodInsn(INVOKESTATIC, SHORT, "valueOf", "(S)Ljava/lang/Short;");
							break;
							
						case Type.INT: // Double -> int -> Integer
							mv.visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "intValue", "()I");
							mv.visitMethodInsn(INVOKESTATIC, INT, "valueOf", "(I)Ljava/lang/Integer;");
							break;
							
						case Type.FLOAT: // Double -> float -> Float
							mv.visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "floatValue", "()F");
							mv.visitMethodInsn(INVOKESTATIC, FLOAT, "valueOf", "(F)Ljava/lang/Float;");
							break;
							
						case Type.LONG: // Double -> long -> Long
							mv.visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "longValue", "()J");
							mv.visitMethodInsn(INVOKESTATIC, LONG, "valueOf", "(J)Ljava/lang/Long;");
							break;
					}
				} else if ( clazz.equals(CHAR) ){ //Chars are strings in lua, use charAt(0)
					mv.visitTypeInsn(CHECKCAST, STRING);
					
					mv.visitInsn(ICONST_0);
					mv.visitMethodInsn(INVOKEVIRTUAL, STRING, "charAt", "(I)C");
				
					mv.visitMethodInsn(INVOKESTATIC, CHAR, "valueOf", "(C)Ljava/lang/Character;");
				} else {
					mv.visitTypeInsn(CHECKCAST, clazz);
				}

				break;
				
			case Type.ARRAY: // TODO: Coerce arrays...
				throw new RuntimeException( "lua -> array coercion not implemented" );

		}
	}

	
	/*
	 * These are temporary
	 */
	public static boolean canCoerceType( Type type ){
		return type.getSort() != Type.ARRAY;
	}
	
	public static boolean canCoerceField( Field field ){
		Type type = Type.getType(field.getType());
		
		return canCoerceType(type);
	}
	public static boolean canCoerceMethod( Method method ){
		Type[] pTypes	= Type.getArgumentTypes(method);
		Type rType		= Type.getReturnType(method);
		
		for ( Type param : pTypes ){
			if ( !canCoerceType(param) )
				return false;
		}
		
		return canCoerceType(rType);
	}
	
}
