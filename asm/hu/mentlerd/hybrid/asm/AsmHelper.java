package hu.mentlerd.hybrid.asm;

import java.lang.reflect.Method;
import org.objectweb.asm.Label;

public class AsmHelper {
	
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
		
}
