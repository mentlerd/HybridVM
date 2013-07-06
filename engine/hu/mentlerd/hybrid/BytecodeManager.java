package hu.mentlerd.hybrid;

import hu.mentlerd.hybrid.Prototype.LocalVar;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BytecodeManager {

	public static final byte[] SIGNATURE = new byte[]{ 27, 'L', 'u', 'a' };
	
	public static final int VERSION = 0x51;
	public static final int FORMAT	= 0x00;
	
	protected static final int TYPE_NIL		= 0;
	protected static final int TYPE_BOOLEAN	= 1;
	protected static final int TYPE_NUMBER	= 3;
	protected static final int TYPE_STRING	= 4;

	public static Prototype read( InputStream stream ) throws IOException{
		return read(stream.read(), stream);
	}
	public static Prototype read( int first, InputStream stream ) throws IOException{
		DataInputStream input = new DataInputStream(stream);
		
		//Check signature
		if ( SIGNATURE[0] != first )
			throw new LuaException("Lua signature mismatch");
		
		for ( int index = 1; index < SIGNATURE.length; index++ ){
			if ( SIGNATURE[index] != input.read() )
				throw new LuaException("Lua signature mismatch");
		}
		
		//Check version
		int version = input.read();
		if ( VERSION != version )
			throw new LuaException("Invalid bytecode version! Expected "+VERSION+", got"+version);
		
		if ( FORMAT != input.read() )
			throw new LuaException("Unexpected format type!");
		
		return new BytecodeManager(input).load();
	}
	
	public static void write( OutputStream stream, Prototype proto ) throws IOException{
		DataOutputStream output = new DataOutputStream(stream);
		
		//Signature
		output.write(SIGNATURE);
		
		output.write(VERSION);
		output.write(FORMAT);
		
		//Header
		output.write(0);	//Big endian
		
		output.write(4);	//Int size
		output.write(4);	//SizeT
		output.write(4);	//Instr size
		output.write(8);	//Number size
		output.write(0);	//Integral
		
		//Proto
		dump(output, proto);
	}

	private static void dump( DataOutputStream stream, Prototype proto ) throws IOException{
		dump(stream, proto.source);
		
		stream.writeInt(0);	//linedefined
		stream.writeInt(0);	//lastlinedefined
		
		//Header
		stream.write(proto.numUpvalues);
		stream.write(proto.numParams);
		stream.write(proto.isVararg ? 2 : 0);
		stream.write(proto.maxStacksize);
		
		int length = 0;
		
		//Code
		length = proto.code.length;
		
		stream.writeInt(length);
		for( int index = 0; index < length; index++ )
			stream.writeInt( proto.code[index] );
		
		//Constants
		length = proto.constants.length;
		
		stream.writeInt(length);
		for( int index = 0; index < length; index++ ){
			Object value = proto.constants[index];
			
			if ( value == null ){
				stream.write(TYPE_NIL);
			} else if ( value instanceof Boolean ){
				stream.write(TYPE_BOOLEAN);
				stream.write( ((Boolean) value) ? 1 : 0 );
			} else if ( value instanceof Double ){
				stream.write(TYPE_NUMBER);
				stream.writeLong( Double.doubleToLongBits((Double) value) );
			} else if ( value instanceof String ){
				stream.write(TYPE_STRING);
				dump(stream, (String) value);
			} else {
				throw new RuntimeException("Bad constant in constant pool");
			}
		}
		
		//Write protos
		length = proto.prototypes.length;
		
		stream.writeInt(length);
		for ( int index = 0; index < length; index++ )
			dump(stream, proto.prototypes[index]);
		
		//Line info
		length	= proto.lines.length;
		
		stream.writeInt(length);
		for ( int index = 0; index < length; index++ )
			stream.writeInt( proto.lines[index] );
		
		//Local values
		length = proto.locals.length;
		
		stream.writeInt(length);
		for ( int index = 0; index < length; index++ ){
			LocalVar local = proto.locals[index];
			
			dump(stream, local.name);
			
			stream.writeInt(local.start);
			stream.writeInt(local.end);
		}
		
		//Upvalues
		length = proto.upvalues.length;
		
		stream.writeInt(length);
		for ( int index = 0; index < length; index++ )
			dump(stream, proto.upvalues[index]);
	}
	private static void dump( DataOutputStream stream, String string ) throws IOException{
		if ( string == null ){
			stream.writeShort(0);
			return;
		}
		byte[] buffer = string.getBytes();
		
		stream.writeInt( buffer.length +1 );
		stream.write( buffer );
		stream.write( 0 );
	}
	
	//Instance/Loader
	protected static void loaderAssert( boolean isOK, String type ){
		if ( !isOK )
			throw new LuaException("Loader assert failed: " + type );
	}
	
	protected DataInputStream stream;
	
	protected boolean isLittleEndian;
	
	protected BytecodeManager( DataInputStream stream ) throws IOException{
		this.stream = stream;
	
		//Load header data
		isLittleEndian	= read() != 0;
		
		loaderAssert( read() == 4 , "Int size" );
		loaderAssert( read() == 4 , "SizeT" );
		loaderAssert( read() == 4 , "Instr size" );
		loaderAssert( read() == 8 , "Number size" );
		loaderAssert( read() == 0 , "Integral" );
	}

	protected int read() throws IOException{
		return stream.read();
	}
	
	protected int readInt() throws IOException{
		int value = stream.readInt();
		
		return isLittleEndian ? Integer.reverseBytes(value) : value;
	}
	protected long readLong() throws IOException{
		long value = stream.readLong();
		
		return isLittleEndian ? Long.reverseBytes(value) : value;
	}
	
	protected String readLuaString() throws IOException{
		int len = readInt();
		
		byte[] buffer = new byte[len -1];
		
		stream.read(buffer);
		stream.read();	
		
		return new String(buffer);
	}
		
	public Prototype load() throws IOException{
		Prototype proto = new Prototype();
		
		//General proto info
		proto.source = readLuaString();
	
		stream.skipBytes(8); //linedefined, lastlinedefined
		
		proto.numUpvalues	= read();
		proto.numParams		= read();
		proto.isVararg		= (read() & 2) != 0;
		proto.maxStacksize	= read();
		
		int length = 0;
		
		//Read Opcodes
		length 		= readInt();
		int[] code	= new int[length];
		
		for ( int index = 0; index < length; index++ )
			code[index] = readInt();
		
		//Read constants
		length				= readInt();
		Object[] constants	= new Object[length];
		
		for ( int index = 0; index < length; index++ ){
			Object value = null;
			int type = read();
			
			switch( type ){
				case TYPE_NIL:
					break;
					
				case TYPE_BOOLEAN:
					value = read() != 0 ? Boolean.TRUE : Boolean.FALSE;
					break;
				
				case TYPE_NUMBER:
					value = Double.longBitsToDouble( readLong() );
					break;
				
				case TYPE_STRING:
					value = readLuaString();
					break;
					
				default:
					throw new LuaException("Unknown constant type: " + type);
			}
			
			constants[index] = value;
		}
		
		//Enclosed prototypes
		length = readInt();
		
		Prototype[] protos = new Prototype[length];
		
		for ( int index = 0; index < length; index++ )
			protos[index] = load();
			
		
		//Line info
		length		= readInt();
		int[] lines	= new int[length];
		
		for ( int index = 0; index < length; index++ )
			lines[index] = readInt();
	
		//Local info
		length				= readInt();
		LocalVar[] locals	= new LocalVar[length];
		
		for ( int index = 0; index < length; index++ )
			locals[index] = new LocalVar(readLuaString(), readInt(), readInt());

		//Upvalues
		length				= readInt();
		String[] upvalues	= new String[length];
		
		for ( int index = 0; index < length; index++ )
			upvalues[index] = readLuaString();
		
		//Assign the loaded values
		proto.code		= code;
		proto.constants	= constants;
		
		proto.prototypes = protos;
		
		proto.lines		= lines;
		proto.locals	= locals;
		proto.upvalues	= upvalues;
		
		return proto;
	}
	
}
