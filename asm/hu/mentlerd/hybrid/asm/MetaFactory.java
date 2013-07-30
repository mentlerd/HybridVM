package hu.mentlerd.hybrid.asm;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;
import hu.mentlerd.hybrid.LuaTable;
import hu.mentlerd.hybrid.asm.ClassAccess.JavaMethod;

public class MetaFactory {

	public static class MetaNewIndex implements Callable{
		protected Map<Object, Integer> fieldMap;
		protected ClassAccess access;
		
		public MetaNewIndex( ClassAccess access ){
			List<Field> fields = access.getFields();
			
			this.access		= access;
			this.fieldMap	= new HashMap<Object, Integer>();
			
			for ( int index = 0; index < fields.size(); index++ ){
				Field field = fields.get(index);
				
				if ( AsmHelper.isStatic(field) )
					continue;
					
				fieldMap.put( field.getName(), index );
			}
		}
		
		public int call(CallFrame frame, int argCount) {
			Integer fieldID = fieldMap.get( frame.getArg(1) );

			if ( fieldID != null )
				access.set(fieldID, frame);
				
			return 0;
		}
	}

	public static class MetaIndex extends MetaNewIndex{
		protected LuaTable meta;
		
		public MetaIndex(ClassAccess access, LuaTable meta) {
			super(access);
		
			this.meta = meta;
		}
		
		public int call(CallFrame frame, int argCount) {
			Object index	= frame.getArg(1);
			
			Integer fieldID = fieldMap.get(index);
			
			if ( fieldID != null ){
				return access.get(fieldID, frame);
			} else {
				frame.push( meta.rawget(index) ); //Push meta methods
				return 1;
			}
		}
	}
	
	public static LuaTable create( ClassAccess access, String typename ){
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

	public static LuaTable wrapMethods( ClassAccess access, LuaTable target, boolean isStatic ){	
		
		//Load method references
		List<JavaMethod> methods = access.getMethods();
		
		for ( JavaMethod method : methods ){
			String key = method.name;
			
			if (  method.isStatic != isStatic ) continue;
			if ( !method.isOverload && target.rawget(key) != null ) continue;
			
			target.rawset(key, method);
		}
		
		return target;
	}

	public static void wrapEnum( Class<? extends Enum<?>> clazz, String prefix, LuaTable table ){
		for (Enum<?> entry : clazz.getEnumConstants())
			table.rawset( prefix + entry.name(), entry );
	}
	
}
