package hu.mentlerd.hybrid.asm;

import hu.mentlerd.hybrid.CallFrame;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public abstract class ClassAccessor{
	protected Class<?> clazz;
	
	protected Constructor<?>[] constructors;
	protected List<Field> fields;
	
	protected List<Method> methods;
	protected List<String> overloads;
	
	public Class<?> getAccessedClass(){
		return clazz;
	}
	
	public Constructor<?>[] getConstructors(){
		return constructors;
	}
	public List<Field> getFields(){
		return fields;
	}
	
	public List<Method> getMethods(){
		return methods;
	}
	public List<String> getOverloads(){
		return overloads;
	}
	
	//These methods are generated runtime
	public abstract Object getField( Object var, int index );	
	public abstract void setField( Object var, int index, Object value );
	
	public abstract int call( int index, CallFrame frame );
	public abstract int resolve( int index, CallFrame frame );
	
	public abstract void create( int index, CallFrame frame );
}
