package hu.mentlerd.hybrid;

public class LuaOpcodes {

	public static final int FIELDS_PER_FLUSH = 50;
	
	public static final int OP_MOVE 		= 0;
	
	public static final int OP_LOADK 		= 1;
	public static final int OP_LOADBOOL 	= 2;
	public static final int OP_LOADNIL 		= 3;
	
	public static final int OP_GETUPVAL 	= 4;
	public static final int OP_GETGLOBAL 	= 5;
	public static final int OP_GETTABLE 	= 6;
	public static final int OP_SETGLOBAL 	= 7;
	public static final int OP_SETUPVAL 	= 8;
	public static final int OP_SETTABLE 	= 9;
	
	public static final int OP_NEWTABLE 	= 10;
	
	public static final int OP_SELF = 11;
	
	public static final int OP_ADD = 12;
	public static final int OP_SUB = 13;
	public static final int OP_MUL = 14;
	public static final int OP_DIV = 15;
	public static final int OP_MOD = 16;
	public static final int OP_POW = 17;
	public static final int OP_UNM = 18;
	public static final int OP_NOT = 19;
	public static final int OP_LEN = 20;
	
	public static final int OP_CONCAT = 21;
	
	public static final int OP_JMP = 22;
	
	public static final int OP_EQ = 23;
	public static final int OP_LT = 24;
	public static final int OP_LE = 25;
	
	public static final int OP_TEST 	= 26;
	public static final int OP_TESTSET 	= 27;
	
	public static final int OP_CALL 	= 28;
	public static final int OP_TAILCALL = 29;
	
	public static final int OP_RETURN 	= 30;
	
	public static final int OP_FORLOOP 	= 31;
	public static final int OP_FORPREP 	= 32;
	public static final int OP_TFORLOOP = 33;
	
	public static final int OP_SETLIST 	= 34;
	public static final int OP_CLOSE 	= 35;
	public static final int OP_CLOSURE 	= 36;
	public static final int OP_VARARG 	= 37;

	public static int getOp(int code) {
		return code & 63;
	}

	public static final int getA8(int op) {
		return (op >>> 6) & 255;
	}

	public static final int getC9(int op) {
		return (op >>> 14) & 511;
	}

	public static final int getB9(int op) {
		return (op >>> 23) & 511;
	}

	public static final int getBx(int op) {
		return (op >>> 14);
	}

	public static final int getSBx(int op) {
		return (op >>> 14) - 131071;
	}

	//Meta operator names
	protected static final int META_OP_OFFSET	= OP_ADD;
	protected static final int META_OP_COUNT	= OP_LE - OP_ADD +1;
	
	private static final String opMetaNames[] = new String[META_OP_COUNT];
	
	static {
		setMetaOp(OP_ADD, "__add");
		setMetaOp(OP_SUB, "__sub");
		setMetaOp(OP_MUL, "__mul");
		setMetaOp(OP_DIV, "__div");
		setMetaOp(OP_MOD, "__mod");
		setMetaOp(OP_POW, "__POW");
		
		setMetaOp(OP_EQ, "__eq");
		setMetaOp(OP_LT, "__lt");
		setMetaOp(OP_LE, "__le");
	}
	
	private static void setMetaOp( int op, String meta ){
		opMetaNames[op -META_OP_OFFSET] = meta;
	}
	public static String getMetaOp( int op ){
		return opMetaNames[op -META_OP_OFFSET];
	}
	
}
