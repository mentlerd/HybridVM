package hu.mentlerd.hybrid;

import hu.mentlerd.hybrid.compiler.LexState;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class LuaClosure {

	public static Prototype compile( String code, String source ) throws IOException{
		return compile( new ByteArrayInputStream(code.getBytes()), source);
	}
	
	public static Prototype compile( InputStream stream, String source ) throws IOException{	
		int first = stream.read();
		
		if ( first == BytecodeManager.SIGNATURE[0] ){
			return BytecodeManager.read(first, stream);
		} else {
			InputStreamReader reader = new InputStreamReader(stream);
			
			return LexState.compile(first, reader, source);
		}
	}
	
	public Prototype proto;
	public LuaTable env;
	
	public UpValue[] upvalues;
	
	public LuaClosure( Prototype proto, LuaTable env ){
		this.proto	= proto;
		this.env	= env;
		
		this.upvalues = new UpValue[proto.numUpvalues];
	}
	
}
