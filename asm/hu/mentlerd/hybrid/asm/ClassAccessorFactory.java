package hu.mentlerd.hybrid.asm;

import hu.mentlerd.hybrid.CallFrame;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

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
		
		List<Field> fields   = getValidFields(clazz);
		List<Method> methods = getValidMethods(clazz);
	
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
		MethodVisitor mv;
		
		cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER, accessNameAsm, null, ACCESSOR, null);
		
		//ClassAccessor()
		{
			mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
			
			mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitMethodInsn(INVOKESPECIAL, ACCESSOR, "<init>", "()V");
				mv.visitInsn(RETURN);
				mv.visitMaxs(1, 1);
			mv.visitEnd();
		}
		
		//Object getField( Object, int )
		{
			mv = cw.visitMethod(ACC_PUBLIC, "getField", FIELD_GET, null, null);
			
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
						mv.visitFrame(F_SAME, 0, null, 0, null);
					
						mv.visitVarInsn(ALOAD, 1);
						mv.visitTypeInsn(CHECKCAST, clazzNameAsm);
						mv.visitFieldInsn(GETFIELD, clazzNameAsm, field.getName(), fieldType.getDescriptor());
					
						AsmHelper.varToLua(mv, fieldType); //Cast primitives to objects
						
						mv.visitInsn(ARETURN);
					}
					
					mv.visitLabel(sDefault); //Create default branch
					mv.visitFrame(F_SAME, 0, null, 0, null);
				}
				
				AsmHelper.throwException(mv, IllegalArgumentException.class, "Illegal field index");
				
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		//void setField( Object, int, Object )
		{	
			mv = cw.visitMethod(ACC_PUBLIC, "setField", FIELD_SET, null, null);
			
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
						mv.visitFrame(F_SAME, 0, null, 0, null);
					
						mv.visitVarInsn(ALOAD, 1);
						mv.visitTypeInsn(CHECKCAST, clazzNameAsm);
						mv.visitVarInsn(ALOAD, 3);
						
						AsmHelper.luaToVar(mv, fieldType);
						
						mv.visitFieldInsn(PUTFIELD, clazzNameAsm, field.getName(), fieldType.getDescriptor());
						mv.visitInsn(RETURN);
					}
					
					mv.visitLabel(sDefault); //Create default branch
					mv.visitFrame(F_SAME, 0, null, 0, null);
				}
			
				AsmHelper.throwException(mv, IllegalArgumentException.class, "Illegal field index");
			
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		//void call( int, CallFrame )
		{		
			mv = cw.visitMethod(ACC_PUBLIC, "call", CALL, null, null);
			
			mv.visitCode();
				
				if ( !methods.isEmpty() ){
					mv.visitVarInsn(ILOAD, 1); //Load index
					
					Label sDefault 	= new Label(); //Switch labels
					Label[] sLabels = AsmHelper.createLabels(methods.size());
					
					mv.visitTableSwitchInsn(0, sLabels.length -1, sDefault, sLabels); //Switch init
					
					for ( int index = 0; index < methods.size(); index++ ){ //Switch branches for each field
						mv.visitLabel(sLabels[index]);
						mv.visitFrame(F_SAME, 0, null, 0, null);
						
						//Add method invocation
						AsmHelper.callToJava(mv, clazzType, methods.get(index));
						
						//Return how many values we pushed
						mv.visitInsn(IRETURN);
					}
					
					mv.visitLabel(sDefault); //Create default branch
					mv.visitFrame(F_SAME, 0, null, 0, null);
				}
			
				AsmHelper.throwException(mv, IllegalArgumentException.class, "Illegal method index");
				
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		//void create( int, CallFrame )
		{
			mv = cw.visitMethod(ACC_PUBLIC, "create", CREATE, null, null);
			
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
						mv.visitFrame(F_SAME, 0, null, 0, null);
						
						mv.visitVarInsn(ALOAD, 2); //CallFrame
	
						//Construct object
						mv.visitTypeInsn(NEW, clazzNameAsm);
						mv.visitInsn(DUP);
						
						//Unpack, and coerce arguments
						for ( int pIndex = 0; pIndex < pClasses.length; pIndex++ ){
							Type pType   = Type.getType( pClasses[pIndex] );
							Type coerced = Type.getType( AsmHelper.getCoercedClass(pClasses[pIndex]) );
							
							mv.visitVarInsn(ALOAD, 2);
							AsmHelper.frameGetArg(mv, pIndex, coerced);
							
							AsmHelper.luaToVar(mv, pType);
						}
						
						//Initialize the object, and push back
						mv.visitMethodInsn(INVOKESPECIAL, clazzNameAsm, "<init>", Type.getConstructorDescriptor(constructor));
						AsmHelper.varToLua(mv, clazzType);
						AsmHelper.framePush(mv);
						
						mv.visitInsn(RETURN);
					}
					
					mv.visitLabel(sDefault); //Create default branch
					mv.visitFrame(F_SAME, 0, null, 0, null);
				}
			
				AsmHelper.throwException(mv, IllegalArgumentException.class, "Illegal constructor index");
				
				mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		
		cw.visitEnd();
		
		//Find the class loader to use
		ClassLoader loader = clazz.getClassLoader();
		
		if ( loader == null )
			loader = ClassLoader.getSystemClassLoader();
		
		//Create instance
		ClassAccessor access = Loader.createInstance(loader, cw.toByteArray(), accessName, ClassAccessor.class);
			access.clazz	= clazz;
			
			access.fields	= fields;
			access.methods	= methods;
		
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
				
				if ( !AsmHelper.canCoerceField(field) )
					continue;
				
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
			
			if ( !AsmHelper.canCoerceMethod(method) )
				continue;
			
			methods.add(method);
		}

		return methods;
	}
	
}
