package hu.mentlerd.hybrid.asm;

import hu.mentlerd.hybrid.CallFrame;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

public class AsmHelper {
	
	protected static final String FRAME		= getAsmName( CallFrame.class );

	public static String getAsmName( Class<?> clazz ){
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
		
}
