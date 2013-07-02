package hu.mentlerd.hybrid.asm;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.LuaTable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

public class AsmHelper {
	
	//Cached class names
	protected static final String BOOLEAN	= "java/lang/Boolean";
	protected static final String BYTE		= "java/lang/Byte";
	protected static final String CHAR		= "java/lang/Character";
	protected static final String SHORT		= "java/lang/Short";
	protected static final String INT		= "java/lang/Integer";
	protected static final String FLOAT		= "java/lang/Float";
	protected static final String LONG		= "java/lang/Long";
	protected static final String DOUBLE	= "java/lang/Double";
	
	protected static final String STRING	= "java/lang/String";
	protected static final String FRAME		= getAsmName( CallFrame.class );
	
	//Method signatures
	protected static final String GET_ARG_C	= "(ILjava/lang/Class;)Ljava/lang/Object;";
	protected static final String PUSH		= "(Ljava/lang/Object;)V";
	
	//Cached type values
	protected static final Type TYPE_BOOLEAN	= Type.getType(Boolean.class);
	protected static final Type TYPE_STRING		= Type.getType(String.class);
	
	protected static final Type TYPE_DOUBLE		= Type.getType(Double.class);
	protected static final Type TYPE_TABLE		= Type.getType(LuaTable.class);

	
	//Class name to Type.getSort conversion	
	protected static Map<String, Integer> numberConvertMap = new HashMap<String, Integer>();
	
	static{
		numberConvertMap.put(BYTE,	Type.BYTE);
		numberConvertMap.put(SHORT,	Type.SHORT);
		numberConvertMap.put(INT,	Type.INT);
		numberConvertMap.put(FLOAT,	Type.FLOAT);
		numberConvertMap.put(LONG, 	Type.LONG);
	}
	
	protected static String getAsmName( Class<?> clazz ){
		return clazz.getName().replace(".", "/");
	}
	
	public static Label[] createLabels( int count ){
		Label[] labels = new Label[count];
		
		for ( int index = 0; index < count; index++ )
			labels[index] = new Label();
		
		return labels;
	}
	
	public static boolean doParamsMatch( Class<?>[] params, Method method ){
		Class<?>[] pTypes = method.getParameterTypes();
		
		if ( params.length != pTypes.length )
			return false;
		
		for ( int index = 0; index < pTypes.length; index++ ){
			if ( !params[index].equals( pTypes[index] ) )
				return false;
		}
		
		return true;
	}
	
	public static void throwException( MethodVisitor mv, Class<? extends Exception> exception, String message ){
		String clazzName = getAsmName(exception);
		
		mv.visitTypeInsn(NEW, clazzName); //Throw exception
		mv.visitInsn(DUP);
		
		mv.visitLdcInsn(message);
		mv.visitMethodInsn(INVOKESPECIAL, clazzName, "<init>", "(Ljava/lang/String;)V");
		mv.visitInsn(ATHROW);
	}

	//Helpers
	public static boolean isStatic( Method method ){
		return Modifier.isStatic( method.getModifiers() );
	}
	
	protected static final boolean isMethodSpecial( Method method ){
		if ( !method.isAnnotationPresent( LuaMethod.class ) )
			return false;
		
		Type[] rTypes	= Type.getArgumentTypes(method);
		Type rType		= Type.getReturnType(method);
		
		//Check for integer return type
		if ( rType != Type.INT_TYPE ){
			System.out.println( "Invalid @LuaMethod: " + method + "!");
			System.out.println( "Return type is not INT" );
			
			return false;
		}
		
		//Check for too many parameters
		if ( rTypes.length > 1 ){
			System.out.println( "Invalid @LuaMethod: " + method + "!");
			System.out.println( "Too many parameters" );
			
			return false;
		}
		
		//Check for CallFrame parameter
		Type param = rTypes[0];
		
		if ( param.getSort() != Type.OBJECT || !param.getInternalName().equals(FRAME) ){
			System.out.println( "Invalid @LuaMethod: " + method + "!");
			System.out.println( "Parameter is not a LuaFrame" );
			
			return false;
		}
		
		return true;
	}
	
	//Coercion validators
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
	
	//Lua coercions
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
				if ( numberConvertMap.containsKey(clazzName) )
					return Double.class;
				
				return clazz;
			
			//Arrays are coerced from LuaTables
			case Type.ARRAY:
				return LuaTable.class;
		}
	
		return null;
	}

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
				if ( numberConvertMap.containsKey(clazz) ){
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
				Integer numClass = numberConvertMap.get(clazz);
				
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
	 * Basically, everything can be null what is not being processed after,
	 *  so everything aside primitive types, and post processed number objects
	 */
	public static boolean canBeNull( Type param ){
		if ( param.getSort() == Type.OBJECT ){
			String clazz = param.getInternalName();
			
			if ( numberConvertMap.containsKey(clazz) )
				return false;
			
			if ( clazz.equals(BOOLEAN) || clazz.equals(CHAR) )
				return false;
			
			return true;
		}
		
		return false; //Primitives
	}
	
	//Note this function expects to find a CallFrame on the stack
	public static void getParamToVar( MethodVisitor mv, int index, Class<?> param ){
		Type type    = Type.getType(param);
		Type coerced = Type.getType(getCoercedClass(param));	
		
		//Pull the parameter on the stack
		mv.visitIntInsn(SIPUSH, index); //CallFrame.getArg( 0, clazz )
		mv.visitLdcInsn(coerced);
		
		if ( canBeNull(type) )
			mv.visitMethodInsn(INVOKEVIRTUAL, FRAME, "getArgNull", GET_ARG_C);
		else
			mv.visitMethodInsn(INVOKEVIRTUAL, FRAME, "getArg", GET_ARG_C);
		
		luaToVar(mv, type);
	}

	
	//Lua calls
	
	public static void framePush( MethodVisitor mv ){
		mv.visitMethodInsn(INVOKEVIRTUAL, FRAME, "push", PUSH);
	}
	
	// Helper to call to java from Lua.
	public static void callToJava( MethodVisitor mv, Type clazz, Method method ){
		boolean isStatic	= isStatic( method );
		int pCallType		= INVOKESTATIC;
		
		String name			= clazz.getInternalName();
		Class<?>[] pClasses	= method.getParameterTypes();
		
		mv.visitVarInsn(ALOAD, 2); //CallFrame
		
		//Non static calls require an instance, and INVOKEVIRTUAL
		if ( !isStatic ){
			mv.visitInsn(DUP);

			mv.visitInsn(ICONST_0); //CallFrame.getArg( 0, clazz )
			mv.visitLdcInsn(clazz);
			mv.visitMethodInsn(INVOKEVIRTUAL, FRAME, "getArg", GET_ARG_C);
			
			mv.visitTypeInsn(CHECKCAST, clazz.getInternalName());
				
			pCallType = INVOKEVIRTUAL;
		}
		
		//Check LuaMethod annotation for details
		if ( isMethodSpecial( method ) ){
			
			//The only variable is the CallFrame itself
			mv.visitVarInsn(ALOAD, 2);
			
			//Call
			mv.visitMethodInsn(pCallType, name, method.getName(), Type.getMethodDescriptor(method));
		
		} else {
			
			//Put variables to the stack as usual
			for ( int pIndex = 0; pIndex < pClasses.length; pIndex++ ){ //Setup arguments
				
				//CallFrame.getArg( int, coercedClass )
				//In case of non static calls, offset arguments by 1
				mv.visitVarInsn(ALOAD, 2);
				
				getParamToVar(mv, isStatic ? pIndex : pIndex +1, pClasses[pIndex]);	
			}
			
			//Call
			mv.visitMethodInsn(pCallType, name, method.getName(), Type.getMethodDescriptor(method));

			//Check if there are arguments to return
			Type rType		= Type.getReturnType(method);
			
			if ( rType != Type.VOID_TYPE ) {
				varToLua(mv, rType); //Coerce return, and push on CallFrame
				framePush(mv);
				
				mv.visitInsn(ICONST_1);
			} else {
				mv.visitInsn(ICONST_0);
			}
			
		}
	}
	
}
