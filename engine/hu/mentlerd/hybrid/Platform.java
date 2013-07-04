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
	
	
	public final Object getMetaValue( Object obj, String index ){
		if ( obj == null ) return null;
		
		if ( obj instanceof LuaTable ){
			LuaTable meta = ((LuaTable) obj).getMetatable();
		
			if ( meta == null )
				return getClassMetavalue(LuaTable.class, index);
			
			return meta.rawget(index);
		}
		
		return getClassMetavalue(obj.getClass(), index);
	}
	
	public final String getTypename( Object obj ){
		if ( obj == null ) 
			return "nil";
		
		return getTypename(obj.getClass());
	}
	public final String getTypename( Class<?> clazz ){
		Object name		= getClassMetavalue(clazz, "__type");
		
		if ( name == null )
			return clazz.getSimpleName();
		
		return name.toString();
	}
}
