package hu.mentlerd.hybrid.asm;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.LuaTable;

import java.lang.reflect.Method;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import static org.objectweb.asm.Opcodes.*;

public class CoercionAdapter extends GeneratorAdapter{

	protected static final String BOOLEAN	= "java/lang/Boolean";
	protected static final String BYTE		= "java/lang/Byte";
	protected static final String CHAR		= "java/lang/Character";
	protected static final String SHORT		= "java/lang/Short";
	protected static final String INT		= "java/lang/Integer";
	protected static final String FLOAT		= "java/lang/Float";
	protected static final String LONG		= "java/lang/Long";
	protected static final String DOUBLE	= "java/lang/Double";
	
	protected static final String STRING	= "java/lang/String";
	protected static final String TABLE		= AsmHelper.getAsmName( LuaTable.class );	
	protected static final String FRAME		= AsmHelper.getAsmName( CallFrame.class );

	protected static final Type TYPE_BOOLEAN	= Type.getType( Boolean.class );
	protected static final Type TYPE_STRING		= Type.getType( String.class );
	protected static final Type TYPE_DOUBLE		= Type.getType( Double.class );
	
	protected static final Type TYPE_TABLE		= Type.getType( LuaTable.class );
	protected static final Type EXCEPTION		= Type.getType( IllegalArgumentException.class );
	
	protected static final String GET_ARG	= "(ILjava/lang/Class;)Ljava/lang/Object;";
	protected static final String PUSH		= "(Ljava/lang/Object;)V";

	
	protected static Map<String, Integer> numberCoercionMap = new HashMap<String, Integer>();
	
	static{
		numberCoercionMap.put(BYTE,		Type.BYTE);
		numberCoercionMap.put(SHORT,	Type.SHORT);
		numberCoercionMap.put(INT,		Type.INT);
		numberCoercionMap.put(FLOAT,	Type.FLOAT);
		numberCoercionMap.put(LONG, 	Type.LONG);
	}
	
	//This method is here for nested tables: [[I -> [I
	private static Type getEntryType( Type array ){
		return Type.getType( array.getInternalName().substring(1) );
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
	
	@Deprecated //Method overload resulving will be moved to bytecode, thats why this is deprecated
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
	
	public static Type getCoercedType( Type type ){	
		switch( type.getSort() ){
			
			//Primitives
			case Type.BOOLEAN:	return TYPE_BOOLEAN;
			case Type.CHAR:		return TYPE_STRING;
			
			//Java numbers are coerced from Double objects
			case Type.BYTE:		
			case Type.SHORT:
			case Type.INT:
			case Type.FLOAT:
			case Type.LONG:
			case Type.DOUBLE:
				return TYPE_DOUBLE;
			
			case Type.OBJECT:
				String clazzName = type.getInternalName();

				//Non primitive numbers object are coerced from Double objects
				if ( numberCoercionMap.containsKey(clazzName) )
					return TYPE_DOUBLE;
				
				return type;
			
			//Arrays are coerced from LuaTables
			case Type.ARRAY:
				return TYPE_TABLE;
				
			default:
				throw new IllegalArgumentException();
		}
	}

	protected int frameArgIndex = -1;
	
	public CoercionAdapter(MethodVisitor mv, int access, String name, String desc) {
		super(mv, access, name, desc);
	}
	public CoercionAdapter(ClassVisitor cw, int access, String name, String desc){	
		super(cw.visitMethod(access, name, desc, null, null), access, name, desc);
	}
	
	public void callToJava( Type clazz, Method method ){
		boolean isStatic	= AsmHelper.isStatic( method );
		int pCallType		= INVOKESTATIC;
		
		String name			= clazz.getInternalName();
		Class<?>[] pClasses	= method.getParameterTypes();
		
		loadArg(frameArgIndex);
		
		//Non static calls require an instance, and INVOKEVIRTUAL
		if ( !isStatic ){
			visitInsn(DUP);
			
			visitInsn(ICONST_0); //CallFrame.getArg( 0, clazz )
			visitLdcInsn(clazz);
			visitMethodInsn(INVOKEVIRTUAL, FRAME, "getArg", GET_ARG);
			
			visitTypeInsn(CHECKCAST, clazz.getInternalName());
				
			pCallType = INVOKEVIRTUAL;
		}
		
		//Check LuaMethod annotation for details
		if ( AsmHelper.isMethodSpecial( method ) ){
			
			//The only variable is the CallFrame itself
			loadArg(frameArgIndex);
			
			//Call
			visitMethodInsn(pCallType, name, method.getName(), Type.getMethodDescriptor(method));
		
		} else {
			
			//Put variables to the stack as usual
			for ( int pIndex = 0; pIndex < pClasses.length; pIndex++ ){ //Setup arguments
				//CallFrame.getArg( int, coercedClass )
				//In case of non static calls, offset arguments by 1
				coerceFrameArg(isStatic ? pIndex : pIndex +1, pClasses[pIndex]);
			}
			
			//Call
			visitMethodInsn(pCallType, name, method.getName(), Type.getMethodDescriptor(method));

			//Check if there are arguments to return
			Type rType = Type.getReturnType(method);
			
			if ( rType != Type.VOID_TYPE ) {
				varToLua(rType); //Coerce return, and push on CallFrame
				visitMethodInsn(INVOKEVIRTUAL, FRAME, "push", CoercionAdapter.PUSH);
				
				visitInsn(ICONST_1);
			} else {
				visitInsn(ICONST_0);
			}
			
		}
	}
	
	
	public void coerceFrameArg( int index, Class<?> clazz ){
		Type type    = Type.getType(clazz);
		Type coerced = Type.getType(CoercionAdapter.getCoercedClass(clazz));	
		
		//Pull the parameter on the stack
		loadArg(frameArgIndex);
		push(index);
		push(coerced);
		
		if ( canCoerceNull(type) )
			visitMethodInsn(INVOKEVIRTUAL, FRAME, "getArgNull", GET_ARG);
		else
			visitMethodInsn(INVOKEVIRTUAL, FRAME, "getArg", GET_ARG);
		
		luaToVar(type);
	}
	
	public void luaToVar( Type type ){
		switch( type.getSort() ){
			
			//Primitives
			case Type.BOOLEAN: // Boolean.booleanValue
				visitTypeInsn(CHECKCAST, BOOLEAN);
				visitMethodInsn(INVOKEVIRTUAL, BOOLEAN, "booleanValue", "()Z");
				break;
				
			case Type.BYTE: // Double.byteValue
				visitTypeInsn(CHECKCAST, DOUBLE);
				visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "byteValue", "()B");
				break;
			
			case Type.CHAR: // String.charAt(0)
				visitTypeInsn(CHECKCAST, STRING);
				
				visitInsn(ICONST_0);
				visitMethodInsn(INVOKEVIRTUAL, STRING, "charAt", "(I)C");
				break;
				
			case Type.SHORT: // Double.shortValue
				visitTypeInsn(CHECKCAST, DOUBLE);
				visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "shortValue", "()S");
				break;
				
			case Type.INT: // Double.intValue
				visitTypeInsn(CHECKCAST, DOUBLE);
				visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "intValue", "()I");
				break;
				
			case Type.FLOAT: // Double.floatValue
				visitTypeInsn(CHECKCAST, DOUBLE);
				visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "floatValue", "()F");
				break;
				
			case Type.LONG: // Double.longValue
				visitTypeInsn(CHECKCAST, DOUBLE);
				visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "longValue", "()J");
				break;
				
			case Type.DOUBLE: // Double.doubleValue
				visitTypeInsn(CHECKCAST, DOUBLE);
				visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "doubleValue", "()D");
				break;
			
			//Casting objects is tricky. Check for number objects
			case Type.OBJECT:
				String clazz = type.getInternalName();
				
				//Check number classes (Extremely unlikely to happen)
				Integer numClass = numberCoercionMap.get(clazz);
				
				if ( numClass != null ){
					visitTypeInsn(CHECKCAST, DOUBLE); // Object -> Double
					
					switch( numClass ){
						case Type.BYTE: // Double -> byte -> Byte
							visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "byteValue", "()B");
							visitMethodInsn(INVOKESTATIC, BYTE, "valueOf", "(B)Ljava/lang/Byte;");
							break;
							
						case Type.SHORT: // Double -> short -> Short
							visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "shortValue", "()S");
							visitMethodInsn(INVOKESTATIC, SHORT, "valueOf", "(S)Ljava/lang/Short;");
							break;
							
						case Type.INT: // Double -> int -> Integer
							visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "intValue", "()I");
							visitMethodInsn(INVOKESTATIC, INT, "valueOf", "(I)Ljava/lang/Integer;");
							break;
							
						case Type.FLOAT: // Double -> float -> Float
							visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "floatValue", "()F");
							visitMethodInsn(INVOKESTATIC, FLOAT, "valueOf", "(F)Ljava/lang/Float;");
							break;
							
						case Type.LONG: // Double -> long -> Long
							visitMethodInsn(INVOKEVIRTUAL, DOUBLE, "longValue", "()J");
							visitMethodInsn(INVOKESTATIC, LONG, "valueOf", "(J)Ljava/lang/Long;");
							break;
					}
				} else if ( clazz.equals(CHAR) ){ //Chars are strings in lua, use charAt(0)
					visitTypeInsn(CHECKCAST, STRING);
					
					visitInsn(ICONST_0);
					visitMethodInsn(INVOKEVIRTUAL, STRING, "charAt", "(I)C");
				
					visitMethodInsn(INVOKESTATIC, CHAR, "valueOf", "(C)Ljava/lang/Character;");
				} else {
					visitTypeInsn(CHECKCAST, clazz);
				}

				break;
				
			case Type.ARRAY:
				visitTypeInsn(CHECKCAST, TABLE);
				
				int table 	= newLocal(TYPE_TABLE);
				int array	= newLocal(type);
				
				int limit	= newLocal(Type.INT_TYPE);
				int counter	= newLocal(Type.INT_TYPE);
				
				Type entry	= getEntryType(type);
				Type cast	= getCoercedType(entry);
				
				if ( type.getDimensions() > 1 )
					cast = TYPE_TABLE; //Nested arrays require a LuaTable to get coerced
				
				/*
				 * in table
				 * 
				 * limit = table.maxN()
				 * array = array[limit]
				 * 
				 * for ( int i = 0; i < limit; i++ )
				 *     array[i] = luaToVar( table.rawget( i +1 ) )
				 *     
				 * return array
				 */
				
				Label loopBody	= new Label();
				Label loopEnd	= new Label();
				
				Label valid		= new Label();
				
				//Loop init
				visitInsn(DUP);
				storeLocal(table);
				
				visitMethodInsn(INVOKEVIRTUAL, TABLE, "maxN", "()I");
				visitInsn(DUP);
				storeLocal(limit);
				
				newArray(entry); // new array[maxN()]
				storeLocal(array);
				
				visitInsn(ICONST_0);
				storeLocal(counter);
				
				visitJumpInsn(GOTO, loopEnd);
				
				//Loop body
				visitLabel(loopBody);
				
				// array[i] = luaToVar( table.rawget( i +1 ) )
				loadLocal(array);
				loadLocal(counter);
				
				loadLocal(table);
				loadLocal(counter);
				visitInsn(ICONST_1);
				visitInsn(IADD);	
				visitMethodInsn(INVOKEVIRTUAL, TABLE, "rawget", "(I)Ljava/lang/Object;");
				
				//Check validity
				visitInsn(DUP);
				visitTypeInsn(INSTANCEOF, cast.getInternalName());
				
				visitJumpInsn(IFNE, valid);
				throwException(EXCEPTION, "Unable to coerce to array. Value could not be coerced to descriptor: " + entry );
				
				visitLabel(valid);
				
				//Coerce, store
				luaToVar(entry);
				arrayStore(entry);
			
				iinc(counter, 1);
				
				//Loop end
				visitLabel(loopEnd);
				
				loadLocal(counter);
				loadLocal(limit);
	
				visitJumpInsn(IF_ICMPLT, loopBody);
				
				//'Return'
				loadLocal(array);
				break;
		}
	}

	
	public void varToLua( Type type ){	
		switch( type.getSort() ){
		
			//Primitives
			case Type.VOID:
				visitInsn(ACONST_NULL);
				break;
			
			case Type.BOOLEAN: // boolean -> Boolean.valueOf
				visitMethodInsn(INVOKESTATIC, BOOLEAN, 	"valueOf", "(Z)Ljava/lang/Boolean;");
				break;
			
			case Type.BYTE: // byte -> Byte -> double -> Double.valueOf
				visitMethodInsn(INVOKESTATIC, BYTE, "valueOf", "(B)Ljava/lang/Byte;");
				visitMethodInsn(INVOKEVIRTUAL, BYTE, "doubleValue", "()D");
				
				visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
			
			case Type.CHAR: // char -> Char -> .toString
				visitMethodInsn(INVOKESTATIC, CHAR, "valueOf", "(C)Ljava/lang/Character;");
				visitMethodInsn(INVOKEVIRTUAL, CHAR, "toString", "()Ljava/lang/String;");
				break;
				
			case Type.SHORT: // short -> Short -> double -> Double.valueOf
				visitMethodInsn(INVOKESTATIC, SHORT, "valueOf", "(S)Ljava/lang/Short;");
				visitMethodInsn(INVOKEVIRTUAL, SHORT, "doubleValue", "()D");
				
				visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
				
			case Type.INT: // int -> double -> Double.valueOf
				visitInsn(I2D);
				visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
				
			case Type.FLOAT: // float -> double -> Double.valueOf
				visitInsn(F2D);
				visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
				
			case Type.LONG: // long -> double -> Double.valueOf
				visitInsn(L2D);
				visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
				
			case Type.DOUBLE: // double -> Double.valueOf
				visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				break;
				
			//Casting objects is tricky. Check for number objects
			case Type.OBJECT:
				String clazz = type.getInternalName();
				
				//Check number classes (Unlikely to happen, but just in case)
				if ( numberCoercionMap.containsKey(clazz) ){
					visitMethodInsn(INVOKEVIRTUAL, clazz, "doubleValue", "()D");
					visitMethodInsn(INVOKESTATIC, DOUBLE, "valueOf", "(D)Ljava/lang/Double;");
				}
				
				//Don't let Characters sneak trough, toString them
				if ( clazz.equals(CHAR) )
					visitMethodInsn(INVOKEVIRTUAL, CHAR, "toString", "()Ljava/lang/String;");
				
				break;
				
			//Casting arrays is done using for loops, and calls to varToLua
			case Type.ARRAY:
				int array   = newLocal(type);
				int counter = newLocal(Type.INT_TYPE);
				int table	= newLocal(TYPE_TABLE);
				
				Type entry	= getEntryType(type);
				
				/*
				 * in array
				 * 
				 * table = new table
				 * for ( int i = 0; i < array.length; i++ )
				 *     table.rawset( i +1, varToLua( array[i] ) )
				 *     
				 * return table
				 */
				
				Label loopBody	= new Label();
				Label loopEnd	= new Label();
				
				//Loop init
				storeLocal(array);
				
				visitInsn(ICONST_0);
				storeLocal(counter);
				
				visitTypeInsn(NEW, TYPE_TABLE.getInternalName());
				visitInsn(DUP);
				visitMethodInsn(INVOKESPECIAL, TABLE, "<init>", "()V");
				
				storeLocal(table);
				
				visitJumpInsn(GOTO, loopEnd);
				
				//Loop body
				visitLabel(loopBody);
				
				// table.rawset( counter +1, varToLua( array[counter] ) )
				loadLocal(table);
				
				loadLocal(counter);
				visitInsn(ICONST_1);
				visitInsn(IADD);
				
				loadLocal(array);
				loadLocal(counter);
				arrayLoad(entry);
				varToLua(entry);
				
				visitMethodInsn(INVOKEVIRTUAL, TABLE, "rawset", "(ILjava/lang/Object;)V");
			
				iinc(counter, 1);
				
				//Loop end
				visitLabel(loopEnd);
				
				loadLocal(counter);
				loadLocal(array);
				arrayLength();
				
				visitJumpInsn(IF_ICMPLT, loopBody);
				
				//'Return'
				loadLocal(table);
				break;
		}
	}

}
