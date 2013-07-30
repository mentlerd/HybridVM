package hu.mentlerd.hybrid.asm;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;

public abstract class ClassAccess {
	
	protected static String getSource( Member member ){
		return member.getName() + "@" + member.getDeclaringClass().getSimpleName();
	}
	protected static boolean isStatic( Member member ){
		return Modifier.isStatic( member.getModifiers() );
	}
	
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
			
			this.isStatic	= isStatic(method);
			this.isOverload	= isOverload;
			
			this.name	= method.getName();
			this.source	= getSource(method);
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
