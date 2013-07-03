package hu.mentlerd.hybrid;

public abstract class Platform {
	
	//Abstract layer
	public abstract LuaTable register( Class<?> clazz, LuaTable meta );

	public abstract LuaTable getClassMetatable( Class<?> clazz );
	
	/**
	 * This method is called by the engine as a shortcut to getClassMetatable().rawget
	 * to allow a way to globally override specific metatable values for various purposes.
	 * 
	 * (Example: Implementing inheritance, or specifying class names by implemented interfaces)
	 * 
	 * @param clazz The class of the target value
	 * @param index Metavalue index ( __type, __index, ... see {@link LuaOpcodes}.metaOpNames for more)
	 */
	public Object getClassMetavalue( Class<?> clazz, String index ){
		LuaTable meta = getClassMetatable(clazz);
		
		if ( meta == null )
			return null;
		
		return meta.rawget(index);
	}
	
}
