package hu.mentlerd.hybrid.asm;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.asm.ClassAccess.JavaMethod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;
import static java.lang.reflect.Modifier.*;

public class ClassAccessFactory {
	
	protected static final String ACCESS	= AsmHelper.getAsmName( ClassAccess.class );
	protected static final String FRAME		= AsmHelper.getAsmName( CallFrame.class );
	protected static final Type EXCEPTION	= Type.getType( IllegalArgumentException.class );

	protected static final String MDESC		= "(IL"+FRAME+";)I";
	
	
	protected static List<Method> listValidMethods( Class<?> clazz ){
		ArrayList<Method> list = new ArrayList<Method>();
		
		for ( Method method : clazz.getMethods() ){
			if ( method.getDeclaringClass() == Object.class )
				continue;
			
			list.add(method);
		}
		
		return list;
	}
	protected static List<Field> listValidFields( Class<?> clazz ){
		ArrayList<Field> list = new ArrayList<Field>();
		
		for ( Field field : clazz.getFields() ){	
			if ( !isPublic( field.getModifiers() ) )
				continue;
			
			list.add(field);
		}
		
		return list;
	}
	
	protected static int countMethods( Collection<List<Method>> map ){
		int count = 0;
		
		for ( List<Method> list : map ){
			int size = list.size();
			
			if ( size > 1 ){
				count += size +1;
			} else {
				count++;
			}
		}
		
		return count;
	}
	
	//Cached ClassAccessors
	protected static Map<Class<?>, ClassAccess> instances = new HashMap<Class<?>, ClassAccess>();

	public static ClassAccess create( Class<?> clazz ){
		
		/*
		 * Check if the target class utilizes type parameter generics, because ASM generated
		 *  classes in fact CAN BYPASS TYPE SAFETY! causing unexpected ClassCast exceptions 
		 */
		if ( clazz.getTypeParameters().length > 0 )
			throw new IllegalArgumentException("Class has generic type parameters!");
		
		ClassAccess result = instances.get(clazz);

		if ( result == null )
			instances.put(clazz, result = generate(clazz));

		return result;
	}
	
	protected static ClassAccess generate( Class<?> clazz ){

		List<Method> methods	= listValidMethods(clazz);
		List<Field> fields		= listValidFields(clazz);
		
		List<JavaMethod> javaCalls	= new ArrayList<JavaMethod>();
		
		//ASM pass
		String clazzName	= clazz.getName();
		String accessName	= clazzName + "Accessor";

		//Bypass prohibited name check
		if ( accessName.startsWith("java.") )
			accessName = "accessor." + accessName;

		//Generator internals
		Type target 			= Type.getType(clazz);
		String accessNameAsm	= accessName.replace(".", "/");
		
		ClassWriter cw = new ClassWriter( ClassWriter.COMPUTE_MAXS );
		CoercionAdapter mv;
		
		cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER, accessNameAsm, null, ACCESS, null);
		
		//ClassAccess()
		{
			mv = new CoercionAdapter(cw, ACC_PUBLIC, "<init>", "()V");
			
			mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitMethodInsn(INVOKESPECIAL, ACCESS, "<init>", "()V");
				mv.visitInsn(RETURN);
				mv.visitMaxs(1, 1);
			mv.visitEnd();
		}
		
		//set( int, CallFrame )
		{
			int fieldCount = fields.size(); 
			
			mv = new CoercionAdapter(cw, ACC_PUBLIC, "set", MDESC);
			mv.frameArgIndex = 1;
			
			mv.visitCode();
				
				if ( fieldCount > 0 ){
					Label[] sLabels	= AsmHelper.createLabels(fieldCount);
					Label sDefault	= new Label();
					
					mv.loadArg(0);
					mv.visitTableSwitchInsn(0, sLabels.length -1, sDefault, sLabels);
					
					for ( int index = 0; index < fieldCount; index++ ){
						mv.visitLabel(sLabels[index]);
						
						Field field = fields.get(index);
						
						if ( AsmHelper.isFinal(field) ){
							mv.goTo(sDefault);
							continue;
						}
						
						Type type	= Type.getType( field.getType() );
						String name	= field.getName();
						
						boolean isStatic = AsmHelper.isStatic(field);
						
						if ( isStatic ){
							mv.coerceFrameArg(2, type);
							
							mv.putStatic(target, name, type);
						} else {
							mv.pushSelfArg(target);
							mv.coerceFrameArg(2, type);

							mv.putField(target, name, type);
						}
						
						mv.push0();
						mv.returnValue();
					}
					
					//Default branch
					mv.visitLabel(sDefault);
				}
			
				mv.throwException(EXCEPTION, "Illegal field access");
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		//get( int, CallFrame )
		{
			int fieldCount = fields.size(); 
			
			mv = new CoercionAdapter(cw, ACC_PUBLIC, "get", MDESC);
			mv.frameArgIndex = 1;
			
			mv.visitCode();
				
				if ( fieldCount > 0 ){
					Label[] sLabels	= AsmHelper.createLabels(fieldCount);
					Label sDefault	= new Label();
					
					mv.loadArg(0);
					mv.visitTableSwitchInsn(0, sLabels.length -1, sDefault, sLabels);
					
					for ( int index = 0; index < fieldCount; index++ ){
						mv.visitLabel(sLabels[index]);
						
						Field field = fields.get(index);
						
						Type type	= Type.getType( field.getType() );
						String name	= field.getName();
						
						boolean isStatic = AsmHelper.isStatic(field);
						
						if ( isStatic ){
							mv.coerceFrameArg(1, type);
							mv.getStatic(target, name, type);
						} else {
							mv.pushSelfArg(target);
							mv.getField(target, name, type);
						}
						
						mv.varToLua(type);
						mv.popToFrame();
						
						mv.push1();
						mv.returnValue();
					}
					
					//Default branch
					mv.visitLabel(sDefault);
				}
			
				mv.throwException(EXCEPTION, "Illegal field access");
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		//call( int, CallFrame )
		{
			Collection<List<Method>> methodMap = OverloadResolver.mapMethods(methods);
			int methodCount = countMethods(methodMap);
			
			mv = new CoercionAdapter(cw, ACC_PUBLIC, "call", MDESC);
			mv.frameArgIndex = 1;
			
			mv.visitCode();
				
				if ( methodCount > 0 ){
					Label[] sLabels	= AsmHelper.createLabels(methodCount);
					Label sDefault	= new Label();
					
					mv.loadArg(0);
					mv.visitTableSwitchInsn(0, sLabels.length -1, sDefault, sLabels);
					
					int index = 0;
					
					for ( List<Method> list : methodMap ){
						
						//Insert the overloaded method first
						if ( list.size() > 1 ){	
							javaCalls.add( new JavaMethod(index, list.get(0), true) );
							
							mv.visitLabel(sLabels[index++]);
							
							mv.callOverload(target, list);
							mv.returnValue();	
						}
						
						//Insert normal methods
						for ( Method method : list ){
							javaCalls.add( new JavaMethod(index, method) );
							
							mv.visitLabel(sLabels[index++]);
						
							mv.callJava(target, method);
							mv.returnValue();
						}
						
					}
					
					//Default branch
					mv.visitLabel(sDefault);
				}
			
				mv.throwException(EXCEPTION, "Illegal method access");
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		mv.visitEnd();
		
		//Find the class loader to use
		ClassLoader loader = clazz.getClassLoader();

		if ( loader == null )
			loader = ClassLoader.getSystemClassLoader();

		//Create instance
		ClassAccess access = Loader.createInstance(loader, cw.toByteArray(), accessName, ClassAccess.class);
			access.fields	= fields;
			access.methods	= javaCalls;
			
		for ( JavaMethod call : javaCalls )
			call.access = access;
		
		return access;
	}
	
}
