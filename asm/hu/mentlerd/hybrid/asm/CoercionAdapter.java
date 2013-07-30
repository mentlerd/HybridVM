package hu.mentlerd.hybrid.asm;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.LuaTable;
import hu.mentlerd.hybrid.asm.OverloadResolver.OverloadRule;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

public class CoercionAdapter extends GeneratorAdapter{
	
	protected static final String TABLE		= AsmHelper.getAsmName( LuaTable.class );	
	protected static final String FRAME		= AsmHelper.getAsmName( CallFrame.class );

	protected static final Type OBJ_BOOLEAN		= Type.getType( Boolean.class );
	protected static final Type OBJ_DOUBLE		= Type.getType( Double.class );
	
	protected static final Type OBJ_TABLE		= Type.getType( LuaTable.class );
	protected static final Type OBJ_OBJECT		= Type.getType( Object.class );
	
	protected static final Type EXCEPTION		= Type.getType( IllegalArgumentException.class );

	protected static Map<String, Type> numberCoercionMap = new HashMap<String, Type>();
	
	static{
		numberCoercionMap.put("java/lang/Byte",		BYTE_TYPE);
		numberCoercionMap.put("java/lang/Short",	SHORT_TYPE);
		numberCoercionMap.put("java/lang/Integer",	INT_TYPE);
		numberCoercionMap.put("java/lang/Float",	FLOAT_TYPE);
		numberCoercionMap.put("java/lang/Long", 	LONG_TYPE);
	}
	
	//This method is here for nested tables: [[I -> [I
	private static Type getEntryType( Type array ){
		return Type.getType( array.getInternalName().substring(1) );
	}
	
	public static boolean isStatic( Member member ){
		return Modifier.isStatic( member.getModifiers() );
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
	 * Returns the type required to be on the stack to coerce it into the target type.
	 * 
	 * @param type The target type
	 * @return The type required on the stack
	 */
	public static Type getCoercedType( Type type ){	
		switch( type.getSort() ){
			
			//Primitives
			case Type.BOOLEAN:	
				return OBJ_BOOLEAN;
			
			//Java numbers are coerced from Double objects
			case Type.BYTE:		
			case Type.CHAR:
			case Type.SHORT:
			case Type.INT:
			
			case Type.FLOAT:
			case Type.LONG:
			
			case Type.DOUBLE:
				return OBJ_DOUBLE;
			
			case Type.OBJECT:
				String clazzName = type.getInternalName();

				//Non primitive numbers object are coerced from Double objects
				if ( numberCoercionMap.containsKey(clazzName) )
					return OBJ_DOUBLE;
				
				return type;
			
			//Arrays are coerced from LuaTables
			case Type.ARRAY:
				return OBJ_TABLE;
				
			default:
				throw new IllegalArgumentException();
		}
	}

	/**
	 * Tells whether the method passes is a valid @LuaMethod
	 * 
	 * (Its return type is int, and only the last parameter is a CallFrame)
	 * @param method
	 * @return
	 */
	public static final boolean isMethodSpecial( Method method ){
		if ( !method.isAnnotationPresent( LuaMethod.class ) || method.isVarArgs() )
			return false;
		
		Type[] rTypes	= Type.getArgumentTypes(method);
		Type rType		= Type.getReturnType(method);
		
		//Check for integer return type
		if ( rType != Type.INT_TYPE )	
			return false;
		
		//Check for CallFrame parameter
		int params = rTypes.length;
		
		if ( params == 0 )
			return false;
		
		Type last = rTypes[params -1];
		
		if ( last.getSort() != Type.OBJECT || !last.getInternalName().equals(FRAME) )
			return false;
		
		return true;
	}

	
	//Instance
	protected int frameArgIndex = -1;
	
	public CoercionAdapter(MethodVisitor mv, int access, String name, String desc) {
		super(mv, access, name, desc);
	}
	public CoercionAdapter(ClassVisitor cw, int access, String name, String desc){	
		super(cw.visitMethod(access, name, desc, null, null), access, name, desc);
	}
	
	//Utility
	public void push0(){ visitInsn(ICONST_0); }
	public void push1(){ visitInsn(ICONST_1); }
	
	private void visitIfValid(Label label) {
		if ( label != null )
			visitLabel(label);
	}

	//Java calls
	public void callJava( Type clazz, Method method ){
		boolean isStatic	= isStatic(method);
		
		boolean isSpecial	= isMethodSpecial(method);
		boolean isVararg	= method.isVarArgs();
		
		int callType		= INVOKESTATIC;

		String owner	= clazz.getInternalName();
		Type pTypes[]	= Type.getArgumentTypes(method);
		
		//Non static calls require an instance, and INVOKEVIRTUAL
		if ( !isStatic ){
			pushSelfArg(clazz);
			callType = INVOKEVIRTUAL;
		}
		
		//Extract parameters to the stack
		int params 	= pTypes.length;
		int off		= 1;
		
		if ( isVararg || isSpecial ) //Last parameter is handled differently
			params -= 1;
		
		if ( isStatic ) //No instance required for static methods 
			off -= 1;
		
		//Extract parameters
		for ( int index = 0; index < params; index++ )
			coerceFrameArg(index + off, pTypes[index]);
	
		//Extract varargs
		if ( isVararg )
			coerceFrameVarargs(params, pTypes[params]);
		
		//Push the callframe
		if ( isSpecial )
			loadArg(frameArgIndex);
		
		//Call
		visitMethodInsn(callType, owner, method.getName(), Type.getMethodDescriptor(method));
		
		if ( isSpecial ) //Special methods manage returning themselves
			return;
		
		//Check if there are arguments to return
		Type rType = Type.getReturnType(method);
		
		if ( rType != Type.VOID_TYPE ) {
			varToLua(rType); //Coerce return, and push on CallFrame
			popToFrame();
			
			push1();
		} else {
			push0();
		}	
	}

	public void callOverload( Type clazz, List<Method> methods ){
		List<OverloadRule> rules = OverloadResolver.resolve(methods);
		
		OverloadRule lastRule = rules.get( rules.size() -1 );
		
		boolean isStatic	= isStatic( lastRule.method );
		
		//Start processing rules, and storing variables
		int lastParamCount 		= -1;
		
		Label nextArgBranch		= null;
		Label nextValueBranch	= null;
		
		Label finish			= new Label();
		
		//Extract parameter count
		int argCount = newLocal(Type.INT_TYPE);
		
		pushFrameArgCount();
		storeLocal(argCount);
		
		//Create branches based off rules
		for ( OverloadRule rule : rules ){
			int paramCount = rule.paramCount;
			
			if ( lastParamCount != paramCount ){ //Argument count mismatch, new branch
				visitIfValid(nextArgBranch);
				nextArgBranch = new Label();
				
				//Check argument count, on failure jump to next count check
				push(paramCount);
				loadLocal(argCount);
				
				ifICmp(LT, nextArgBranch);
			}
			
			if ( rule.paramType != null ){ //Check argument class
				visitIfValid(nextValueBranch);
				nextValueBranch = new Label();
					
				pushFrameArg(rule.paramIndex + (isStatic ? 0 : 1), OBJ_OBJECT, true);
				
				//Check instance, if invalid jump to next type check
				instanceOf(getCoercedType(rule.paramType));
				
				ifZCmp(IFEQ, nextValueBranch);
			}
			
			//Everything passed. Extract parameters from locals, and the frame
			callJava(clazz, rule.method);
			
			goTo(finish);
			
			//Prepare next rule
			lastParamCount = paramCount;
		}
		
		//Fail branch
		visitIfValid(nextArgBranch);
		visitIfValid(nextValueBranch);
		
		throwException(EXCEPTION, "Unable to resolve overloaded call");
		
		//Finish
		visitLabel(finish);
	}
		
	//Frame access
	public void pushSelfArg( Type clazz ){
		loadArg(frameArgIndex);
		
		push0(); //Fancy error messages
		push(clazz);
		push("self");
		
		visitMethodInsn(INVOKEVIRTUAL, FRAME, "getNamedArg", "(ILjava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;");			
		checkCast(clazz);
	}

	public void pushFrameArg( int index, Type type, boolean allowNull ){
		loadArg(frameArgIndex);
		push(index);
		
		pushFrameArg(type, allowNull);
	}
	
	public void pushFrameArgCount(){
		loadArg(frameArgIndex);
		visitMethodInsn(INVOKEVIRTUAL, FRAME, "getArgCount", "()I");
	}
	
	public void popToFrame(){
		loadArg(frameArgIndex);
		swap();
		
		visitMethodInsn(INVOKEVIRTUAL, FRAME, "push", "(Ljava/lang/Object;)V");
	}
	
	//Internals
	private void pushFrameArg( Type type, boolean allowNull ){
		push(type);
		
		String method = allowNull ? "getArgNull" : "getArg";
		visitMethodInsn(INVOKEVIRTUAL, FRAME, method, "(ILjava/lang/Class;)Ljava/lang/Object;");
	}

	//Frame utils
	public void coerceFrameArg( int index, Type type ){
		Type coerced = getCoercedType(type);	
		
		//Pull the parameter on the stack
		pushFrameArg(index, coerced, canCoerceNull(type));
		luaToVar(type);
	}
	
	public void coerceFrameVarargs( int from, Type arrayType ){
		Type entry	= getEntryType(arrayType);
		
		int array	= newLocal(arrayType);
		
		int limit	= newLocal(Type.INT_TYPE);
		int counter	= newLocal(Type.INT_TYPE);
		
		/*
		 * in frame
		 * 
		 * limit = frame.getArgCount() - params
		 * array = new array[limit]
		 * 
		 * for ( int i = 0; i < limit; i++ )
		 *    array[i] = coerceFrameArg( i + params, clazz )
		 *    
		 * push array
		 */
		
		Label loopBody	= new Label();
		Label loopEnd	= new Label();
		
		//Loop init
		pushFrameArgCount();
		
		push(from);
		math(SUB, INT_TYPE);
		
		dup(); //frame.getArgCount() - params
		storeLocal(limit);
		
		newArray(entry); //new array[limit]
		storeLocal(array);
		
		push0();
		storeLocal(counter);
		
		goTo(loopEnd);
		
		//Loop body
		visitLabel(loopBody);
		
		//Prepare array
		loadLocal(array);
		loadLocal(counter);
		
		//Pull argument from the stack
		loadArg(frameArgIndex);
		loadLocal(counter);
		
		push(from);
		math(ADD, INT_TYPE);
		
		pushFrameArg(getCoercedType(entry), canCoerceNull(entry));
		
		//Store
		arrayStore(entry);
		
		iinc(counter, 1);
		
		//Loop end
		visitLabel(loopEnd);
		
		loadLocal(counter);
		loadLocal(limit);

		ifICmp(LT, loopBody);
		
		//'Return'
		loadLocal(array);
	}

	//Coercion
	public void luaToVar( Type type ){
		switch( type.getSort() ){
			
			case Type.BOOLEAN:
			case Type.DOUBLE:
				unbox(type);
				break;
			
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT:
				unbox(INT_TYPE);
				cast(INT_TYPE, type);
				break;
				
			case Type.FLOAT:
			case Type.LONG:
				unbox(type);
				break;
			
			case Type.CHAR:
				unbox(INT_TYPE);			//Double.intValue
				cast(INT_TYPE, CHAR_TYPE);	//int -> char
				break;
				
			case Type.OBJECT:
				String clazz = type.getInternalName();
				
				Type primitive = numberCoercionMap.get(clazz);
				
				if ( primitive != null ){
					unbox(primitive);	//Number.[int|double|float]Value
					valueOf(primitive);	//[Int|Double|Float|...].valueOf
				} else if ( clazz.equals(CHAR) ){
					unbox(INT_TYPE);
					cast(INT_TYPE, CHAR_TYPE);
					
					valueOf(CHAR_TYPE);
				} else {
					checkCast(type);
				}
				
				break;
				
			case Type.ARRAY:
				tableToArray(type);
				break;
		}
	}
	
	public void varToLua( Type type ){	
		switch( type.getSort() ){
		
			case Type.BOOLEAN:
			case Type.DOUBLE:
				valueOf(type);
				break;
			
			case Type.BYTE:
			case Type.CHAR:
			case Type.SHORT:
			case Type.INT:
				
			case Type.FLOAT:
			case Type.LONG:
				cast(type, DOUBLE_TYPE);
				valueOf(DOUBLE_TYPE);
				break;
				
			case Type.OBJECT:
				String clazz = type.getInternalName();
				
				if ( numberCoercionMap.containsKey(clazz) ){
					unbox(DOUBLE_TYPE);		//Number.doubleValue
					valueOf(DOUBLE_TYPE);	//Double.valueOf
				}
				
				if ( clazz.equals(CHAR) ){
					unbox(CHAR_TYPE);		//Character -> char
					varToLua(CHAR_TYPE);	//char -> Double
				}
				break;
				
			case Type.ARRAY:
				arrayToTable(type);
				break;

		}
	}
	
	public void tableToArray( Type type ){
		checkCast(OBJ_TABLE);
		
		int array	= newLocal(type);
		int table 	= newLocal(OBJ_TABLE);
		
		int limit	= newLocal(INT_TYPE);
		int counter	= newLocal(INT_TYPE);
		
		Type entry	= getEntryType(type);
		Type cast	= getCoercedType(entry);
		
		if ( type.getDimensions() > 1 )
			cast = OBJ_TABLE; //Nested arrays require a LuaTable to get coerced
		
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
		dup();
		storeLocal(table);
		
		visitMethodInsn(INVOKEVIRTUAL, TABLE, "maxN", "()I");
		dup();
		storeLocal(limit);
		
		newArray(entry); // new array[maxN()]
		storeLocal(array);
		
		push0();
		storeLocal(counter);
		
		goTo(loopEnd);
		
		//Loop body
		visitLabel(loopBody);
		
		// array[i] = luaToVar( table.rawget( i +1 ) )
		loadLocal(array);
		loadLocal(counter);
		
		loadLocal(table);
		loadLocal(counter);
		
		push(1);
		math(ADD, INT_TYPE);
		visitMethodInsn(INVOKEVIRTUAL, TABLE, "rawget", "(I)Ljava/lang/Object;");
		
		//Check validity
		dup();
		instanceOf(cast);
		
		ifZCmp(IFNE, valid);
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

		ifICmp(LT, loopBody);
		
		//'Return'
		loadLocal(array);
	}

	public void arrayToTable( Type type ){
		int array   = newLocal(type);
		int table	= newLocal(OBJ_TABLE);
		
		int limit	= newLocal(INT_TYPE);
		int counter	= newLocal(INT_TYPE);
		
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
		dup();
		storeLocal(array);
		
		arrayLength();
		storeLocal(limit);
		
		push0();
		storeLocal(counter);
		
		newInstance(OBJ_TABLE);
		dup();
		visitMethodInsn(INVOKESPECIAL, TABLE, "<init>", "()V");
		
		storeLocal(table);
		
		goTo(loopEnd);
		
		//Loop body
		visitLabel(loopBody);
		
		// table.rawset( counter +1, varToLua( array[counter] ) )
		loadLocal(table);
		
		loadLocal(counter);
		push1();
		math(ADD, INT_TYPE);
		
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
		
		ifICmp(LT, loopBody);
		
		//'Return'
		loadLocal(table);
	}
	
}
