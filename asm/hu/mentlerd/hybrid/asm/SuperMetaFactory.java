package hu.mentlerd.hybrid.asm;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;
import hu.mentlerd.hybrid.LuaException;
import hu.mentlerd.hybrid.LuaTable;
import hu.mentlerd.hybrid.asm.MetaFactory.MethodReference;

public class SuperMetaFactory {
	
	private static class OverloadComparator implements Comparator<Method>{

		protected OverloadComparator(){
		}
		
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
				
				if ( aParam.isAssignableFrom(bParam) ) return -1;
				if ( bParam.isAssignableFrom(aParam) ) return 1;
				
				return aParam.hashCode() - bParam.hashCode();
			}
					
			throw new RuntimeException("Methods with same signature. This should not happen!");
		}

	}
	
	private static class OverloadRule{
		public int argCount;
		
		public int argIndex;
		public Class<?> argClass;
		
		protected OverloadRule( int argCount, int argIndex, Class<?> argClass ){
			this.argCount	= argCount;
			
			this.argIndex 	= argIndex;
			
			if ( argClass != null )
				this.argClass = CoercionAdapter.getCoercedClass(argClass);
		}
		
		public boolean argWillPass( CallFrame frame ){	
			Object item = frame.get(argIndex +1); //Because of instance calls... TODO: Better please, also BYTECODE
				
			if ( item == null || argClass == null )
				return true;
			
			return argClass.isAssignableFrom( item.getClass() );
		}
	}
	
	private static class OverloadMethodReference implements Callable{
		protected ClassAccessor access;
		
		protected OverloadRule[] rules;
		protected int[] methodID;
		
		protected String source;
		
		protected OverloadMethodReference( ClassAccessor access, int[] methodID, OverloadRule[] rules, String source ){
			this.access		= access;
			
			this.methodID 	= methodID;
			this.rules		= rules;
			
			this.source		= source;
		}

		public int call(CallFrame frame, int argCount) {
			int validIndex = -1;
		
			for( int index = 0; index < methodID.length; index++){
				OverloadRule rule = rules[index];
				
				if ( rule.argCount != argCount -1  ) //Because of instance calls. TODO
					continue;
					
				if ( rule.argWillPass(frame) )
					validIndex = index;
			}
			
			if ( validIndex == -1 )
				throw new LuaException("No correct overloaded method found");
			
			return access.call(methodID[validIndex], frame);
		}
		
		public String toString(){
			return source;
		}
	}
	
	protected static Comparator<Method> COMPARATOR = new OverloadComparator();
	
	/*
	 * To fellow programmers out there who read this. Please don't kill me.
	 * I am really disappointed that I have to do this, but I can't be bothered
	 * right now to do this inside the ASM code, as I am convinced that method
	 * overloading is something that I should not really care about.
	 * 
	 * If you do want it so bad, contact me.
	 */
	protected static Callable wrapOverloaded( ClassAccessor access, List<Method> list, Map<Method, Integer> indexes ){	
		String source = access.getAccessedClass().getSimpleName() + "." + list.get(0).getName();
			
		//Filter duplicate methods, by any ways!
		for ( int aIndex = 0; aIndex < list.size(); aIndex++ ){
			Method aMethod = list.get(aIndex);
		
			Class<?>[] aParams 	= aMethod.getParameterTypes();
			Class<?> aReturn	= aMethod.getReturnType();
			
			for ( int bIndex = aIndex +1; bIndex < list.size(); bIndex++ ){
				Method bMethod = list.get(bIndex);
				
				//Check if names, and arguments don't match
				if ( !AsmHelper.doParamsMatch(aParams, bMethod) )
					continue;
				
				Class<?> bReturn = bMethod.getReturnType();
				
				int aIsSuperior = 0;
				
				//If returns do not match, check if one is a subclass of the other
				if ( !aReturn.equals(bReturn) ){
					if ( aReturn.isAssignableFrom(bReturn) ) aIsSuperior = -1;
					if ( bReturn.isAssignableFrom(aReturn) ) aIsSuperior =  1;
				}
				
				//Not decided yet, declaring classes will settle this
				if ( aIsSuperior == 0 ){
					Class<?> aOwner = aMethod.getDeclaringClass();
					Class<?> bOwner = bMethod.getDeclaringClass();
					
					if ( bOwner.isAssignableFrom(aOwner) ) aIsSuperior =  1;
					if ( aOwner.isAssignableFrom(bOwner) ) aIsSuperior = -1;
				}
				
				if ( aIsSuperior == 0 )
					throw new RuntimeException( "Unable to resolve duplicate method. This should not happen!" );
				
				if ( aIsSuperior == -1 ){
					list.remove(aIndex--);

					//Forcably exit the loop
					bIndex = list.size() +1;
				} else {
					list.remove(bIndex--);
				}
			}
		}
		
		//We might have removed competing methods
		if ( list.size() == 1 ){
			int methodID = indexes.get( list.get(0) );
			
			return new MethodReference(access, methodID, source);
		}
		
		Collections.sort(list, COMPARATOR);
		
		//Here comes the horrible hack
		OverloadRule[] rules = new OverloadRule[list.size()];
		int[] methodID		 = new int[list.size()];
		
		//Insert rules
		Class<?>[] lParams = new Class<?>[0];
		
		for ( int index = 0; index < rules.length; index++ ){
			Method method 		= list.get(index);
			Class<?>[] params 	= method.getParameterTypes();
			
			int pIndex = 0;
			for(; pIndex < lParams.length; pIndex++)
				if ( !lParams[pIndex].equals( params[pIndex] ) )
					break;
			
			Class<?> clazz = ( params.length == 0 ? null : params[pIndex] );
			
			rules[index] 	= new OverloadRule(params.length, pIndex, clazz);
			methodID[index]	= indexes.get(method);
			
			lParams	= params;
		}

		return new OverloadMethodReference(access, methodID, rules, source);
	}
	
	public static Callable wrapOverloaded( ClassAccessor access, String methodName ){
		List<Method> methods = access.getMethods();
		
		//Generate a list of methods with the same name, and indexes
		Map<Method, Integer> indexes = new HashMap<Method, Integer>();
		List<Method> list = new ArrayList<Method>();
		
		for ( int index = 0; index < methods.size(); index++ ){
			Method method = list.get(index);
			
			if ( !method.getName().equals(methodName) )
				continue;
			
			methods.add(method);
			indexes.put(method, index);
		}
	
		return wrapOverloaded(access, methodName);
	}
		
	public static LuaTable wrapMethods( ClassAccessor access, LuaTable table, boolean isStatic ){
		List<Method> methods = access.getMethods();
		
		Map<String, List<Method>> overloads	= new HashMap<String, List<Method>>();
		Map<Method, Integer> indexes		= new HashMap<Method, Integer>();
		
		//Process method list, check for overloaded ones
		for ( int index = 0; index < methods.size(); index++ ){
			Method method = methods.get(index);
			
			if ( AsmHelper.isStatic(method) != isStatic )
				continue;
			
			String name 		= method.getName();
			List<Method> list 	= overloads.get(name);
			
			if ( list == null )
				overloads.put(name, list = new ArrayList<Method>(2));
			
			list.add(method);
			indexes.put(method, index);
		}
		
		//Push methods into the metatable
		String clazz = access.getAccessedClass().getSimpleName() + ".";
		
		for ( Entry<String, List<Method>> entry : overloads.entrySet() ){
			String name			= entry.getKey();
			List<Method> list	= entry.getValue();
			
			if ( list.size() == 1 ){ //Single, not overloaded
				int methodID = indexes.get( list.get(0) );
				
				table.rawset(name, new MethodReference(access, methodID, clazz + name));
			} else { //Overloaded method
				table.rawset(name, wrapOverloaded(access, list, indexes));
			}
		}
		
		return table;
	}

}
