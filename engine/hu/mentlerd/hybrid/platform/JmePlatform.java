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
import hu.mentlerd.hybrid.lib.MathLib;
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
		
	public JmePlatform(){
		//Typenames
		register(Boolean.class,	"boolean");
		register(Double.class,	"number");
		
		register(LuaTable.class,	"table");
		
		register(LuaClosure.class,	"function");
		register(Callable.class,	"function");
		
		//Standard libs
		BaseLib.bind(env);
		
		env.rawset("math", MathLib.bind());
		env.rawset("table", TableLib.bind());
		
		LuaTable string		= new LuaTable();
		LuaTable coroutine	= new LuaTable();
		
		StringLib.bind(string);
		CoroutineLib.bind(coroutine);
		
		env.rawset("string", 	string);
		env.rawset("coroutine", coroutine);
		
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
