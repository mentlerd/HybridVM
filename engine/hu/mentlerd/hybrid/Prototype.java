package hu.mentlerd.hybrid;

public final class Prototype {
	
	public static class LocalVar {
		public String name;
		
		public int start;
		public int end;
		
		public LocalVar(String name) {
			this.name = name;
		}
	}

	public int[] code;

	public Object[] constants;
	public Prototype[] prototypes;
	
	public int numParams;
	public boolean isVararg;

	public int numUpvalues;
	public int maxStacksize;
		
	public String source;
	
	//Debug info
	public int[] lines;
	public LocalVar[] locals;
	public String[] upvalues;
	
	//Debug helper
	public String findLocalName( int slot, int pc ){
		for ( int index = 0; index < locals.length; index++ ){
			LocalVar local = locals[index];
			
			if ( local.start > pc )
				break;
			
			if ( local.end >= pc && slot-- == 0 )
				return local.name;
		}
			
		return null;
	}
	
}
