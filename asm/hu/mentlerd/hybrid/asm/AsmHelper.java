package hu.mentlerd.hybrid.asm;

import hu.mentlerd.hybrid.CallFrame;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

public class AsmHelper {
	
	protected static final String FRAME		= getAsmName( CallFrame.class );
	
	protected static final String GET_ARG_C	= "(ILjava/lang/Class;)Ljava/lang/Object;";
	protected static final String PUSH		= "(Ljava/lang/Object;)V";


	protected static String getAsmName( Class<?> clazz ){
		return clazz.getName().replace(".", "/");
	}
	
	public static Label[] createLabels( int count ){
		Label[] labels = new Label[count];
		
		for ( int index = 0; index < count; index++ )
			labels[index] = new Label();
		
		return labels;
	}
	
	public static void push( MethodVisitor mv, int value ){
		if ( value >= -1 && value <= 5 ){
			mv.visitInsn(ICONST_0 + value);
		} else if ( value >= Byte.MIN_VALUE  && value <= Byte.MAX_VALUE ){
			mv.visitIntInsn(BIPUSH, value);
		} else if ( value >= Short.MIN_VALUE && value <= Short.MAX_VALUE ){
			mv.visitIntInsn(SIPUSH, value);
		} else {
			mv.visitLdcInsn(Integer.valueOf(value));
		}
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
	
	public static final boolean isMethodSpecial( Method method ){
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
		
	//Note this function expects to find a CallFrame on the stack
	public static void getParamToVar( MethodVisitor mv, int index, Class<?> param ){
		Type type    = Type.getType(param);
		Type coerced = Type.getType(Coercion.getCoercedClass(param));	
		
		//Pull the parameter on the stack
		push( mv, index );
		mv.visitLdcInsn(coerced);
		
		if ( Coercion.canCoerceNull(type) )
			mv.visitMethodInsn(INVOKEVIRTUAL, FRAME, "getArgNull", GET_ARG_C);
		else
			mv.visitMethodInsn(INVOKEVIRTUAL, FRAME, "getArg", GET_ARG_C);
		
		Coercion.luaToVar(mv, type);
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
				Coercion.varToLua(mv, rType); //Coerce return, and push on CallFrame
				framePush(mv);
				
				mv.visitInsn(ICONST_1);
			} else {
				mv.visitInsn(ICONST_0);
			}
			
		}
	}
	
}
