package hu.mentlerd.hybrid.asm;

import hu.mentlerd.hybrid.CallFrame;

import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.TraceClassVisitor;

import static org.objectweb.asm.Opcodes.*;
import static java.lang.reflect.Modifier.*;

public class ClassAccessorFactory {
	
	protected static final String ACCESSOR	= AsmHelper.getAsmName( ClassAccessor.class );
	protected static final String OBJECT	= AsmHelper.getAsmName( Object.class );

	protected static final String FRAME		= AsmHelper.getAsmName( CallFrame.class );
	
	// Generated method signatures
	protected static final String FIELD_GET = "(L"+OBJECT+";I)L"+OBJECT+";";
	protected static final String FIELD_SET = "(L"+OBJECT+";IL"+OBJECT+";)V";

	protected static final String CALL		= "(IL"+FRAME+";)I";
	protected static final String CREATE	= "(IL"+FRAME+";)V";
	
	protected static Type EXCEPTION	= Type.getType( IllegalArgumentException.class );
	
	//Cached ClassAccessors
	protected static Map<Class<?>, ClassAccessor> instances = new HashMap<Class<?>, ClassAccessor>();
	
	public static ClassAccessor create( Class<?> clazz ){
		ClassAccessor result = instances.get(clazz);
		
		if ( result == null )
			instances.put(clazz, result = generate(clazz));
		
		return result;
	}
	
	private static ClassAccessor generate( Class<?> clazz ){
		Constructor<?>[] constructors = clazz.getConstructors();
		
		List<Field> fields   	= getValidFields(clazz);
		List<Method> methods 	= getValidMethods(clazz);
		List<String> overloads	= new ArrayList<String>();
		
		Map<String, List<Method>> overloadMap = OverloadResolver.mapOverloaded(methods);
		
		//ASM pass
		String clazzName	= clazz.getName();
		String accessName	= clazzName + "Accessor";
		
		//Bypass prohibited name check
		if ( accessName.startsWith("java.") )
			accessName = "accessor." + accessName;
		
		//Generator internals
		Type clazzType 			= Type.getType(clazz);
	
		String clazzNameAsm		= clazzName.replace(".", "/");
		String accessNameAsm	= accessName.replace(".", "/");
		
		//Class generation start
		ClassWriter cw = new ClassWriter( ClassWriter.COMPUTE_MAXS );
		CoercionAdapter mv;
		
		cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER, accessNameAsm, null, ACCESSOR, null);
		
		//ClassAccessor()
		{
			mv = new CoercionAdapter(cw, ACC_PUBLIC, "<init>", "()V");
			
			mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitMethodInsn(INVOKESPECIAL, ACCESSOR, "<init>", "()V");
				mv.visitInsn(RETURN);
				mv.visitMaxs(1, 1);
			mv.visitEnd();
		}
		
		//Object getField( Object, int )
		{
			mv = new CoercionAdapter(cw, ACC_PUBLIC, "getField", FIELD_GET);
			
			mv.visitCode();
			
				if ( !fields.isEmpty() ){
					mv.visitVarInsn(ILOAD, 2); //Load index
					
					Label sDefault 	= new Label(); //Switch labels
					Label[] sLabels = AsmHelper.createLabels(fields.size());
					
					mv.visitTableSwitchInsn(0, sLabels.length -1, sDefault, sLabels); //Switch init
					
					for ( int index = 0; index < fields.size(); index++ ){ //Switch branches for each field
						Field field 	= fields.get(index);
						Type fieldType 	= Type.getType(field.getType());
						
						mv.visitLabel(sLabels[index]);
					
						mv.visitVarInsn(ALOAD, 1);
						mv.visitTypeInsn(CHECKCAST, clazzNameAsm);
						mv.visitFieldInsn(GETFIELD, clazzNameAsm, field.getName(), fieldType.getDescriptor());
					
						mv.varToLua(fieldType); //Cast primitives to objects
						
						mv.visitInsn(ARETURN);
					}
					
					mv.visitLabel(sDefault); //Create default branch
				}
				
				mv.throwException(EXCEPTION, "Illegal field index");
				
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		//void setField( Object, int, Object )
		{	
			mv = new CoercionAdapter(cw, ACC_PUBLIC, "setField", FIELD_SET);
			
			mv.visitCode();
				
				if ( !fields.isEmpty() ){
					mv.visitVarInsn(ILOAD, 2); //Load index
					
					Label sDefault 	= new Label(); //Switch labels
					Label[] sLabels = AsmHelper.createLabels(fields.size());
					
					mv.visitTableSwitchInsn(0, sLabels.length -1, sDefault, sLabels); //Switch init
					
					for ( int index = 0; index < fields.size(); index++ ){ //Switch branches for each field
						Field field 	= fields.get(index);
						Type fieldType 	= Type.getType(field.getType());
						
						mv.visitLabel(sLabels[index]);
					
						mv.visitVarInsn(ALOAD, 1);
						mv.visitTypeInsn(CHECKCAST, clazzNameAsm);
						mv.visitVarInsn(ALOAD, 3);
						
						mv.luaToVar(fieldType);
						
						mv.visitFieldInsn(PUTFIELD, clazzNameAsm, field.getName(), fieldType.getDescriptor());
						mv.visitInsn(RETURN);
					}
					
					mv.visitLabel(sDefault); //Create default branch
				}
			
				mv.throwException(EXCEPTION, "Illegal field index");
					
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		//void call( int, CallFrame )
		{		
			mv = new CoercionAdapter(cw, ACC_PUBLIC, "call", CALL);
			mv.frameArgIndex = 1;
			
			mv.visitCode();
				
				if ( !methods.isEmpty() ){
					mv.visitVarInsn(ILOAD, 1); //Load index
					
					Label sDefault 	= new Label(); //Switch labels
					Label[] sLabels = AsmHelper.createLabels(methods.size());
					
					mv.visitTableSwitchInsn(0, sLabels.length -1, sDefault, sLabels); //Switch init
					
					int index = 0;
					for ( Method method : methods ){ //Switch branches for each method
						mv.visitLabel(sLabels[index++]);
						
						mv.callJava(clazzType, method);
						mv.visitInsn(IRETURN);
					}
						
					mv.visitLabel(sDefault); //Create default branch
				}
			
				mv.throwException(EXCEPTION, "Illegal method index");
				
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		//int resolve( int, CallFrame )
		{
			mv = new CoercionAdapter(cw, ACC_PUBLIC, "resolve", CALL);
			mv.frameArgIndex = 1;
			
			mv.visitCode();
			
				if ( !overloadMap.isEmpty() ){
					mv.visitVarInsn(ILOAD, 1);
					
					Label sDefault	= new Label();
					Label[] sLabels	= AsmHelper.createLabels(overloadMap.size());
					
					mv.visitTableSwitchInsn(0, sLabels.length -1, sDefault, sLabels); //Switch init
					
					int index = 0;
					for ( Entry<String, List<Method>> entry : overloadMap.entrySet() ){
						mv.visitLabel(sLabels[index++]);
						
						overloads.add(entry.getKey());
						
						mv.callJava(clazzType, entry.getValue());
						mv.visitInsn(IRETURN);
					}
					
					mv.visitLabel(sDefault);
				}
			
				mv.throwException(EXCEPTION, "Invalid overloaded method index");
				
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		//void create( int, CallFrame )
		{
			mv = new CoercionAdapter(cw, ACC_PUBLIC, "create", CREATE);
			mv.frameArgIndex = 1;
			
			mv.visitCode();
				
				if ( constructors.length > 0 ){
					mv.visitVarInsn(ILOAD, 1); //Load index
					
					Label sDefault 	= new Label(); //Switch labels
					Label[] sLabels = AsmHelper.createLabels(constructors.length);
					
					mv.visitTableSwitchInsn(0, sLabels.length -1, sDefault, sLabels); //Switch init
					
					for ( int index = 0; index < constructors.length; index++ ){ //Switch branches for each field
						Constructor<?> constructor 	= constructors[index];
						Class<?>[] pClasses	= constructor.getParameterTypes();
						
						mv.visitLabel(sLabels[index]);
						
						//Construct object
						mv.visitTypeInsn(NEW, clazzNameAsm);
						mv.visitInsn(DUP);
						
						//Unpack, and coerce arguments
						for ( int pIndex = 0; pIndex < pClasses.length; pIndex++ )
							mv.coerceFrameArg(pIndex, Type.getType(pClasses[pIndex]));
						
						//Initialize the object, and push back
						mv.visitMethodInsn(INVOKESPECIAL, clazzNameAsm, "<init>", Type.getConstructorDescriptor(constructor));
						mv.varToLua(clazzType);
						mv.popToFrame();
						
						mv.visitInsn(RETURN);
					}
					
					mv.visitLabel(sDefault); //Create default branch
				}
			
				mv.throwException(EXCEPTION, "Illegal constructor index");
				
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		cw.visitEnd();
		
		//Find the class loader to use
		ClassLoader loader = clazz.getClassLoader();
		
		if ( loader == null )
			loader = ClassLoader.getSystemClassLoader();
		
		ClassReader reader 		= new ClassReader(cw.toByteArray());
		TraceClassVisitor trace	= new TraceClassVisitor(new PrintWriter(System.out));
			reader.accept(trace, 0);
		
		//Create instance
		ClassAccessor access = Loader.createInstance(loader, cw.toByteArray(), accessName, ClassAccessor.class);
			access.clazz	= clazz;
			
			access.fields		= fields;
			access.methods		= methods;
			access.overloads	= overloads;
			
			access.constructors	= constructors;
		
		return access;
	}
	
	protected static List<Field> getValidFields( Class<?> clazz ){
		ArrayList<Field> fields = new ArrayList<Field>();
        
		if ( clazz.isInterface() ) //Interfaces have no fields
			return fields;
		
		while( clazz != Object.class ){	
			for ( Field field : clazz.getDeclaredFields() ){
				int mods = field.getModifiers();
				
				if ( isStatic(mods)  ) continue;
				if ( isPrivate(mods) ) continue;
				
				fields.add(field);
			}
			
			clazz = clazz.getSuperclass();
		}
		
		return fields;
	}
		
	protected static List<Method> getValidMethods( Class<?> clazz ){
		ArrayList<Method> methods = new ArrayList<Method>();
		
		for ( Method method : clazz.getMethods() ){
			if ( method.getDeclaringClass() == Object.class )
				continue;
			
			methods.add(method);
		}

		return methods;
	}
	
}
