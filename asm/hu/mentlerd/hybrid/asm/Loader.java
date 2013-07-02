package hu.mentlerd.hybrid.asm;

import java.lang.reflect.Method;

public class Loader {
	
	public static Class<?> loadClass( ClassLoader loader, byte[] code, String name ){
		try {
			Class<?> clazz = Class.forName("java.lang.ClassLoader");
			
			Method defineClass = clazz.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class );
				defineClass.setAccessible(true);
				
			Object[] args = new Object[]{ name, code, 0, code.length };
			
			return (Class<?>) defineClass.invoke(loader, args);
		} catch ( Exception err ){
			err.printStackTrace();
		}
	
		return null;
	}
	
	public static <T> T createInstance( ClassLoader loader, byte[] code, String name, Class<T> clazz ){
		Class<?> genClass = loadClass(loader, code, name);
		
		try {	
			return clazz.cast( genClass.newInstance() );
		} catch ( Exception err ) {
			err.printStackTrace();
		}
		
		return null;
	}
	
}