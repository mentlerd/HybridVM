package hu.mentlerd.hybrid.asm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;

public abstract class ClassAccess {
		
	public static class JavaMethod implements Callable{
		protected ClassAccess access;
		protected int index;
		
		public final boolean isStatic;
		public final boolean isOverload;
		
		public final String name;
		public final String source;
		
		public JavaMethod( int index, Method method ){
			this(index, method, false);
		}
		
		public JavaMethod( int index, Method method, boolean isOverload ){
			this.index	= index;
			
			this.isStatic	= AsmHelper.isStatic(method);
			this.isOverload	= isOverload;
			
			this.name	= method.getName();
			this.source	= AsmHelper.getMethodSource(method);
		}
			
		public int call(CallFrame frame, int argCount) {
			return access.call(index, frame);
		}	
	}
	
	//Instance
	public List<Field> fields;
	public List<JavaMethod> methods;
	
	public List<Field> getFields(){
		return fields;
	}
	public List<JavaMethod> getMethods(){
		return methods;
	}
	
	public abstract int get( int index, CallFrame frame );
	public abstract int set( int index, CallFrame frame );
	
	public abstract int call( int index, CallFrame frame );
}
