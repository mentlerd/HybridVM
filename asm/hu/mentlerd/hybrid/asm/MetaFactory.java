package hu.mentlerd.hybrid.asm;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;
import hu.mentlerd.hybrid.LuaTable;

public class MetaFactory {
	
	public static class MethodReference implements Callable{
		protected final ClassAccessor access;
		protected final int index;
		
		protected final String source;
		
		public MethodReference( ClassAccessor access, int methodID, String source ){
			this.access	= access;
			this.index	= methodID;
			
			this.source	= source;
		}

		public int call(CallFrame frame, int argCount) {
			return access.call(index, frame);
		}
		
		public String toString(){
			return "function:"+source;
		}
	}
	
	public static class OverloadReference extends MethodReference{

		public OverloadReference(ClassAccessor access, int methodID, String source) {
			super(access, methodID, source);
		}
		
		public int call(CallFrame frame, int argCount){
			return access.resolve(index, frame);
		}
	}
	
	public static class ConstructorReference extends MethodReference{

		public ConstructorReference(ClassAccessor access, int methodID) {
			super(access, methodID, "<init>");
		}
		
		public int call(CallFrame frame, int argCount) {
			access.create(index, frame);
			return 1;
		}
	}
	
	public static class MetaNewIndex implements Callable{
		protected Map<Object, Integer> fieldMap;
		protected ClassAccessor access;
		
		public MetaNewIndex( ClassAccessor access ){
			List<Field> fields = access.getFields();
			
			//Save space by preallocating
			this.access		= access;
			this.fieldMap	= new HashMap<Object, Integer>( fields.size() );
			
			for ( int index = 0; index < fields.size(); index++ )
				fieldMap.put( fields.get(index).getName(), index );
		}
		
		public int call(CallFrame frame, int argCount) {
			Object instance = frame.getArg(0, access.clazz);
			Integer fieldID = fieldMap.get( frame.get(1) );
			
			if ( fieldID != null )
				access.setField(instance, fieldID, frame.getArg(2));

			return 0;
		}
	}

	public static class MetaIndex extends MetaNewIndex{
		protected LuaTable meta;
		
		public MetaIndex(ClassAccessor access, LuaTable meta) {
			super(access);
		
			this.meta = meta;
		}
		
		public int call(CallFrame frame, int argCount) {
			Object instance = frame.getArg(0, access.getAccessedClass());
			Object index	= frame.getArg(1);
			
			Integer fieldID = fieldMap.get(frame.get(1));
			
			if ( fieldID != null ){
				frame.push( access.getField(instance, fieldID) );
			} else {
				frame.push( meta.rawget(index) ); //Push meta methods
			}
			
			return 1;
		}
	}
	
	
	public static LuaTable create( ClassAccessor access, String typename ){
		LuaTable meta = new LuaTable();
			meta.rawset("__type", typename);
		
		//Set indexers
		meta.rawset("__index", new MetaIndex(access, meta));
		meta.rawset("__newindex", new MetaNewIndex(access));
		
		//Create method references
		wrapMethods(access, meta, false);
		
		//Copy standard methods to lua metamethods
		meta.rawset( "__tostring", 	meta.rawget("toString") );
		meta.rawset( "__eq",		meta.rawget("equals") );
		
		return meta;
	}
		
	public static LuaTable wrapMethods( ClassAccessor access, LuaTable target, boolean isStatic ){	
		
		//Create static method references
		String origin		 = access.getAccessedClass().getSimpleName() + ".";
		
		List<Method> methods 	= access.getMethods();
		List<String> overloads	= access.getOverloads();
		
		for ( int methodID = 0; methodID < methods.size(); methodID++ ){
			Method method = methods.get(methodID);
			
			if ( CoercionAdapter.isStatic(method) == isStatic ){
				String name = method.getName();
				
				if ( overloads.contains(name) )
					continue;
				
				target.rawset(name, new MethodReference(access, methodID, origin + name));	
			}
		}
		
		for ( int overloadID = 0; overloadID < overloads.size(); overloadID++ ){
			String name = overloads.get(overloadID);
			
			target.rawset(name, new OverloadReference(access, overloadID, name));
		}
		
		return target;
	}
	
	public static Callable wrapConstructor( ClassAccessor access, Class<?> ... params ){	
		try {
			Constructor<?> target = access.getAccessedClass().getConstructor(params);
			Constructor<?>[] constructors = access.getConstructors();
		
			for ( int index = 0; index < constructors.length; index++ ){
				if ( target.equals( constructors[index] ) )
					return new ConstructorReference(access, index);
			}
			
			throw new IllegalArgumentException("Constructor is not accessable");
		} catch ( Exception e ) {
			throw new IllegalArgumentException("Unable to find the constructor specified");
		}
	}

	public static void wrapEnum( Class<? extends Enum<?>> clazz, String prefix, LuaTable table ){
		for (Enum<?> entry : clazz.getEnumConstants())
			table.rawset( prefix + entry.name(), entry );
	}
	
}
