package hu.mentlerd.hybrid.asm;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Type;

public class OverloadResolver implements Comparator<Method>{
	
	public static class OverloadRule{
		public int paramCount;
		
		public int paramIndex;
		public Type paramType;
		
		public Method method;
		
		public OverloadRule( int count, int index, Class<?> clazz, Method method ){
			paramCount	= count;
			paramIndex	= index;
			
			if ( clazz != null )
				paramType = Type.getType(clazz);
			
			this.method	= method;
		}
	}
	
	public static Comparator<Method> SORTER = new OverloadResolver();
		
	protected static boolean isSuperior( Method A, Method B ){
		Class<?> aReturn = A.getReturnType();
		Class<?> bReturn = B.getReturnType();
	
		if ( !aReturn.equals(bReturn) ){
			if ( aReturn.isAssignableFrom(bReturn) ) return false;
			if ( bReturn.isAssignableFrom(aReturn) ) return true;
		}
		
		Class<?> aOwner = A.getDeclaringClass();
		Class<?> bOwner = B.getDeclaringClass();
		
		if ( aOwner.isAssignableFrom(bOwner) ) return false;
		if ( bOwner.isAssignableFrom(aOwner) ) return true;
		
		throw new IllegalArgumentException("A, and B are equal!");
	}
	
	protected static boolean doParametersMatch( Method A, Method B ){
		Class<?>[] aParams = A.getParameterTypes();
		Class<?>[] bParams = B.getParameterTypes();
		
		if ( aParams.length != bParams.length )
			return false;
		
		for ( int index = 0; index < aParams.length; index++ ){
			if ( aParams[index] != bParams[index] )
				return false;
		}
		
		return true;
	}
	
	
	public static Map<String, List<Method>> mapMethods( List<Method> methods ){
		HashMap<String, List<Method>> map = new HashMap<String, List<Method>>();
		
		for ( Method method : methods ){
			String name = method.getName();
			List<Method> list = map.get(name);
			
			if ( list == null ) //Lazy init
				map.put(name, list = new ArrayList<Method>());
			
			list.add(method);
		}
		
		return map;
	}
	
	private static void resolveOverrides( List<Method> methods ){
		int size = methods.size();
		
		if ( size < 2 )
			return;
		
		//Check every method against each
		for ( int aIndex = 0; aIndex < size; aIndex++ ){
			Method A = methods.get(aIndex);
			
			for ( int bIndex = aIndex +1; bIndex < size; bIndex++ ){
				Method B = methods.get(bIndex);
				
				if ( !doParametersMatch(A, B) ) //Only check exact matches
					continue;
				
				size--; //We are going to remove an element
				
				if ( isSuperior(A, B) ){ //Remove the non superior method
					methods.remove(bIndex--);
				} else {
					methods.remove(aIndex--);
					break; //'A' was removed, exit the loop 
				}
			}
		}
	}
	
	private static void resolveOrder( List<Method> methods ){
		Collections.sort(methods, SORTER);
	}
	
	public static List<OverloadRule> resolve( List<Method> methods ){
		resolveOverrides(methods); //Remove duplicates, and order methods
		resolveOrder(methods);
		
		List<OverloadRule> rules = new ArrayList<OverloadRule>(methods.size());
		
		//Generate rules, by traversing the sorted methods
		int lastParamCount		= -1;
		Class<?>[] lastParams 	= new Class<?>[0];
		
		for( Method method : methods ){
			Class<?>[] params 	= method.getParameterTypes();
			int count			= params.length;
			
			//Calculate first differing parameter index
			int pIndex = 0;
			for(; pIndex < lastParamCount; pIndex++){
				if ( lastParams[pIndex] != params[pIndex] )
					break;
			}
			
			//Create rule
			Class<?> clazz = ( count == 0 ? null : params[pIndex] );
			rules.add( new OverloadRule(count, pIndex, clazz, method) );
			
			lastParams 		= params;
			lastParamCount	= params.length;
		}
		
		return rules;
	}
	
	private OverloadResolver(){}
		
	public int compare(Method a, Method b) {
		Class<?>[] aParams = a.getParameterTypes();
		Class<?>[] bParams = b.getParameterTypes();
		
		int pCount = aParams.length;
		
		//Order by param count
		if ( pCount != bParams.length )
			return pCount - bParams.length;
		
		//And by matching parameters
		for ( int pIndex = 0; pIndex < pCount; pIndex++ ){
			Class<?> aParam = aParams[pIndex];
			Class<?> bParam = bParams[pIndex];
						
			if ( aParam.equals(bParam) )
				continue;

			boolean aInterface = aParam.isInterface();
			boolean bInterface = bParam.isInterface();
			
			if ( aInterface != bInterface ) //Interfaces override objects by default
				return aInterface ? -1 : 1;
				
			if ( aParam.isAssignableFrom(bParam) ) return -1;
			if ( bParam.isAssignableFrom(aParam) ) return  1;
			
			return aParam.hashCode() - bParam.hashCode();
		}
		
		throw new IllegalArgumentException("Duplicate method: " + Type.getMethodDescriptor(a));
	}
	
}
