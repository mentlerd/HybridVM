package hu.mentlerd.hybrid.asm;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;
import hu.mentlerd.hybrid.LuaException;
import hu.mentlerd.hybrid.LuaTable;
import hu.mentlerd.hybrid.platform.JmePlatform;

public class AsmPlatform extends JmePlatform{
	
	protected static class AsmIndex implements Callable{
		protected final AsmPlatform platform;
				
		protected AsmIndex( AsmPlatform platform ){
			this.platform = platform;
		}
		
		public int call( CallFrame frame, int argCount ) {
			Object target = frame.getArg(0);
			Object index  = frame.getArg(1);
			
			if ( target == null ){
				throw new LuaException("attempt to index a nil value");
			} else {
				frame.push(platform.indexObject(target.getClass(), index));
				return 1;
			}
		}
	}
	
	protected AsmIndex __index = new AsmIndex(this);
	
	public LuaTable loadAsLib( String name, Class<?> clazz ){
		ClassAccessor access = ClassAccessorFactory.create(clazz);
		LuaTable lib = MetaFactory.wrapMethods(access, new LuaTable(), true);
		
		env.rawset(name, lib);
		return lib;
	}
	
	public Object indexObject( Class<?> clazz, Object index ){
		while ( clazz != Object.class ){
			LuaTable meta = metatables.get(clazz);
			
			if ( meta != null ){
				Object value = meta.rawget(index);
				
				if ( value != null )
					return value;
			}
			
			clazz = clazz.getSuperclass();
		}
		
		return null;
	}
	
	public Object getClassMetavalue(Class<?> clazz, String index) {
		if ( index.equals("__index") )
			return __index;
		
		return super.getClassMetavalue(clazz, index);
	}
	
}
