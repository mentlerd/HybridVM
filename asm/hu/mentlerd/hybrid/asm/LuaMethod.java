package hu.mentlerd.hybrid.asm;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)

/*
 * A method is considered special if it returns multiple values, or requires
 *  direct access to the call frame and argument count. In this case the @LuaMethod
 *  annotation is placed to alert the compiler about this behavior.
 *  
 * Apart from the annotation, the following rules apply:
 *  - The only parameter is CallFrame
 *  - The method returns an integer value
 *  
 * If any of these rules don't apply, the compiler will ignore the special
 *  behavior request, and will warn the developer
 */
public @interface LuaMethod {
	
}
