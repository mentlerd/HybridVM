package hu.mentlerd.hybrid.platform;

import java.util.HashMap;
import java.util.Map;

import hu.mentlerd.hybrid.Callable;
import hu.mentlerd.hybrid.Coroutine;
import hu.mentlerd.hybrid.LuaClosure;
import hu.mentlerd.hybrid.LuaTable;
import hu.mentlerd.hybrid.Platform;
import hu.mentlerd.hybrid.lib.BaseLib;
import hu.mentlerd.hybrid.lib.CoroutineLib;
import hu.mentlerd.hybrid.lib.StringLib;
import hu.mentlerd.hybrid.lib.TableLib;

public class JmePlatform extends Platform{
	protected Map<Class<?>, LuaTable> metatables = new HashMap<Class<?>, LuaTable>();
	protected LuaTable env = new LuaTable();
	
	private LuaTable register( Class<?> clazz, String type ){
		LuaTable meta = new LuaTable();
			meta.rawset("__type", type);
			
		metatables.put(clazz, meta);
		return meta;
	}
	
	private void loadLib( LuaTable env, Class<? extends Enum<? extends Callable>> lib ){
		for (Enum<? extends Callable> entry : lib.getEnumConstants())
			env.rawset( entry.name().toLowerCase(), entry );
	}
	private LuaTable loadLib( LuaTable env, String name, Class<? extends Enum<? extends Callable>> lib ){
		LuaTable table = new LuaTable();
			env.rawset(name, table);
		
		loadLib(table, lib);
		return table;
	}
	
	public JmePlatform(){
		//Typenames
		register(Boolean.class,	"boolean");
		register(Double.class,	"number");
		
		register(LuaTable.class,	"table");
		
		register(LuaClosure.class,	"function");
		register(Callable.class,	"function");
		
		//Standard libs
		loadLib(env, BaseLib.class);
		
		loadLib(env, "table", TableLib.class);
		
		LuaTable string		= loadLib(env, "string", 	StringLib.class);
		LuaTable coroutine 	= loadLib(env, "coroutine", CoroutineLib.class);
	
		//Special meta
		LuaTable meta;
		
		meta = register(String.class, "string");
			meta.rawset("__index", string);
			meta.rawset("__len", StringLib.LEN);
		
		meta = register(Coroutine.class, "thread");
			meta.rawset("__index", coroutine);
			
		//Env globals
		env.rawset("_G", env);
		
		env.rawset("VM", "Hybrid");
		env.rawset("VERSION", 1.0);
	}
	
	public LuaTable register( Class<?> clazz, LuaTable meta ){
		metatables.put(clazz, meta);
		return meta;
	}
	
	
	public LuaTable getClassMetatable(Class<?> clazz) {	
		if ( Callable.class.isAssignableFrom(clazz) )
			return metatables.get(Callable.class);
		
		return metatables.get(clazz);
	}

	public LuaTable getEnv() {
		return env;
	}

}
