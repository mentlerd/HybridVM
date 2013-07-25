package hu.mentlerd.hybrid;

import static hu.mentlerd.hybrid.LuaOpcodes.*;

public class LuaThread {
	public static final int MAX_INDEX_RECURSION	= 100;

	private final Platform platform;
	private final Coroutine root;
	
	public Coroutine coroutine;
	public DebugHook hook;
	
	public LuaThread( Platform platform, LuaTable rootEnv ){
		this.platform = platform;
		
		root 		= new Coroutine( platform, rootEnv );
		root.thread	= this;
		
		coroutine	= root;
	}
	
	/*
	 * VM Helper
	 */
	public int call( int argCount ){
		int top		= coroutine.getTop();
		int base	= top - argCount -1;
		
		Object func = coroutine.stack[base];
		
		if ( func == null )
			throw new LuaException("attempt to call nil");
		
		if ( func instanceof Callable )
			return callJava((Callable) func, base +1, base, argCount);
		
		if ( !(func instanceof LuaClosure) )
			throw new LuaException("attempt to call non function");
		
		CallFrame frame = coroutine.pushCallFrame((LuaClosure) func, base +1, base, argCount);
			frame.fromLua	= false;
			frame.canYield	= false;
		
		frame.init();
	
		luaMainloop();
	
		return coroutine.getTop() - base;
	}
	
	public int pcall( int argCount ){
		CallFrame frame = coroutine.getCurrentFrame();
		
		int base = coroutine.getTop() - argCount -1;
		
		try{
			int rets	= call( argCount );
			int newTop	= base + rets +1;
			
			coroutine.setTop(newTop);
			coroutine.stackCopy(base, base +1, rets);
			
			coroutine.stack[base] = Boolean.TRUE;
			return rets +1;
		} catch ( Throwable err ){
			
			if ( frame != null )
				frame.closeUpvalues(0);
			
			while( true ){ //Pop all extra frames on top
				CallFrame pop = coroutine.getCurrentFrame();
				
				if ( pop == frame )
					break;
				
				coroutine.addStackTrace(pop);
				coroutine.popCallFrame();
			}
			
			//Report back to caller
			coroutine.setTop(base +4);
			
			coroutine.stack[base]		= Boolean.FALSE;
			coroutine.stack[base +1]	= LuaUtil.getExceptionCause(err);
			coroutine.stack[base +2]	= coroutine.getStackTrace();
			coroutine.stack[base +3]	= err;
			
			coroutine.resetStackTrace();
			return 4;
		}
	}
	
	public Object call( Object func, Object ... args ){
		int top 		= coroutine.getTop();
		int argCount	= args.length;
		
		coroutine.setTop( top + argCount +1 );
		coroutine.stack[top] = func;
		
		System.arraycopy(args, 0, coroutine.stack, top +1, argCount);
		
		Object result = ( call(argCount) >= 1 ? coroutine.stack[top] : null );
		
		coroutine.setTop(top);
		return result;
	}
	
	public Object[] callMultret( Object func, int limit, Object ... args ){
		int top			= coroutine.getTop();
		int argCount	= args.length;
		
		coroutine.setTop( top + argCount +1 );
		coroutine.stack[top] = func;
		
		System.arraycopy(args, 0, coroutine.stack, top +1, argCount);
		
		int rets = call(argCount);
		
		if ( limit > 0 )
			rets = Math.min(rets, limit);

		Object[] values = new Object[rets];
		System.arraycopy(coroutine.stack, top, values, 0, rets);
		
		coroutine.setTop(top);
		return values;
	}
	
	public Object[] resume( Coroutine thread, Object ... args ){
		if ( thread.isDead() )
			throw new IllegalStateException("Cannot resume a dead coroutine!");
		
		int top = coroutine.getTop();
		
		//Push a frame to return yield arguments to
		coroutine.pushJavaFrame(null, top, top, 0);
		
		thread.thread = this;
		thread.parent = this.coroutine;
		
		CallFrame nextFrame = thread.getCurrentFrame();
		int argCount 		= args.length;
		
		if ( nextFrame.argCount == -1 ) //First time resuming, setup stack!
			nextFrame.setTop(argCount);
		
		for ( int index = 0; index < argCount; index++ ) //Push arguments
			nextFrame.push( args[index] );
		
		if ( nextFrame.argCount == -1 ){ //First time resuming, initialize the frame
			nextFrame.argCount = argCount;
			nextFrame.init();
		}
		
		if ( nextFrame.restoreTop )
			nextFrame.setTop( nextFrame.closure.proto.maxStacksize );
		
		this.coroutine = thread;
				
		luaMainloop();
		
		//Retrieve yield returns
		CallFrame frame = coroutine.getCurrentFrame();
		int retCount 	= frame.getTop();
		
		Object[] returns  = new Object[retCount];
		for ( int index = 0; index < retCount; index++ )
			returns[index] = frame.get(index);
		
		//Earse values, and the frame
		coroutine.setTop(top);
		coroutine.popCallFrame();
		
		return returns;
	}
	
	/*
	 * VM Core
	 */
	public static boolean isCallable( Object func ){
		return (func instanceof LuaClosure || func instanceof Callable);
	}
	
	public Object getMetaValue( Object value, String key ){
		return platform.getMetaValue(value, key);
	}
	private Object getSharedMetaValue( Object o1, Object o2, String key ){
		Object meta1 = getMetaValue(o1, key);
		Object meta2 = getMetaValue(o2, key);
		
		if ( meta1 != meta2 || meta1 == null )
			return null;
		
		return meta1;
	}
	
	private int callJava( Callable func, int localBase, int returnBase, int argCount ){
		Coroutine caller	= coroutine; //Handle coroutine changes after a java call
		CallFrame frame 	= caller.pushJavaFrame(func, localBase, returnBase, argCount);
		
		int retCount = func.call(frame, argCount);
		
		int top = frame.getTop();
		int actualReturnBase = top - retCount;

		int diff = returnBase - localBase;
		frame.stackCopy(actualReturnBase, diff, retCount);
		frame.setTop(retCount + diff);

		caller.popCallFrame();
	
		return retCount;
	}
	
	private Object getRK( CallFrame frame, int index, Prototype proto ){
		int cindex = index - 256;
		
		if ( cindex < 0 )
			return frame.get(index);
		else
			return proto.constants[cindex];
	}
	
	private void luaMainloop(){
		CallFrame frame	= coroutine.getCurrentFrame();
		
		LuaClosure closure	= frame.closure;
		Prototype proto		= closure.proto;
		
		int[] opcodes	= proto.code;
		int returnBase	= frame.returnBase;
		
		while(true){
			try{
				int A, B, C;
				
				int code	= opcodes[ frame.pc++ ];
				int inst	= getOp(code);
				
				if ( hook != null ) //Notify the debug hook
					hook.passOpcode(coroutine);
				
				switch( inst ){
					case OP_MOVE: //A B		R(A):= R(B)
						A = getA8(code);
						B = getB9(code);
						
						frame.set(A, frame.get(B));
						break;
					
					case OP_LOADK: //A Bx	R(A):= Kst(Bx)
						A = getA8(code);
						B = getBx(code);
						
						frame.set(A, proto.constants[B]);
						break;
					
					case OP_LOADBOOL: //A B C	R(A):= (Bool)B: if (C) pc++
						A = getA8(code);
						B = getB9(code);
						C = getC9(code);
						
						frame.set(A, Boolean.valueOf( B != 0 ));
						if ( C != 0 )
							frame.pc++;
						
						break;
					
					case OP_LOADNIL: //A B		R(A):= ...:= R(B):= nil
						A = getA8(code);
						B = getB9(code);
						
						frame.stackClear(A, B);
						break;
					
					case OP_GETUPVAL: //A B		R(A):= UpValue[B]
						A = getA8(code);
						B = getB9(code);
						
						frame.set(A, closure.upvalues[B].getValue());
						break;
					
					case OP_GETGLOBAL: //A Bx	R(A):= Gbl[Kst(Bx)]
						A = getA8(code);
						B = getBx(code);
						
						frame.set(A, tableGet(closure.env, proto.constants[B]));
						break;
					
					case OP_GETTABLE: {//A B C	R(A):= R(B)[RK(C)]
						A = getA8(code);
						B = getB9(code);
						C = getC9(code);
						
						Object table 	= frame.get(B);
						Object key 		= getRK(frame, C, proto);

						//Check if it is valid to index the value
						boolean isTable = table instanceof LuaTable;
						
						if ( !isTable && getMetaValue(table, "__index") == null )
							throw LuaUtil.slotError(frame, B, "attempt to index");
						
						frame.set(A, tableGet(table, key));
						break;
					}
					
					case OP_SETGLOBAL: //A Bx	Gbl[Kst(Bx)]:= R(A)
						A = getA8(code);
						B = getBx(code);

						tableSet(closure.env, proto.constants[B], frame.get(A));
						break;
						
					case OP_SETUPVAL: //A B		UpValue[B]:= R(A)
						A = getA8(code);
						B = getB9(code);

						closure.upvalues[B].setValue(frame.get(A));
						break;
						
					case OP_SETTABLE: { //A B C		R(A)[RK(B)]:= RK(C)
						A = getA8(code);
						B = getB9(code);
						C = getC9(code);

						Object table 	= frame.get(A);

						Object key 		= getRK(frame, B, proto);
						Object value 	= getRK(frame, C, proto);

						//Check if it is valid to index the value
						boolean isTable = table instanceof LuaTable;
						
						if ( !isTable && getMetaValue(table, "__index") == null )
							throw LuaUtil.slotError(frame, A, "attempt to index");
						
						tableSet(table, key, value);
						break;
					}
					
					case OP_NEWTABLE: //A B C	R(A):= {} (size = B,C)	
						A = getA8(code);
						B = getB9(code);
						C = getC9(code);

						frame.set(A, new LuaTable(B, C));
						break;
					
					case OP_SELF: { //A B C		R(A+1):= R(B): R(A):= R(B)[RK(C)]
						A = getA8(code);
						B = getB9(code);
						C = getC9(code);

						Object table 	= frame.get(B);
						Object key 		= getRK(frame, C, proto);

						frame.set(A, 	tableGet(table, key));
						frame.set(A +1, table);
						break;
					}
					
					case OP_ADD: //A B C	R(A):= RK(B) + RK(C)
					case OP_SUB: //A B C	R(A):= RK(B) - RK(C)
					case OP_MUL: //A B C	R(A):= RK(B) * RK(C)
					case OP_DIV: //A B C	R(A):= RK(B) / RK(C)
					case OP_MOD: //A B C	R(A):= RK(B) % RK(C)
					case OP_POW: { //A B C	R(A):= RK(B) ^ RK(C)
						A = getA8(code);
						B = getB9(code);
						C = getC9(code);

						Object o1 = getRK(frame, B, proto);
						Object o2 = getRK(frame, C, proto);
						
						Double d1 = (o1 instanceof Double ? (Double) o1 : null);
						Double d2 = (o2 instanceof Double ? (Double) o2 : null);
						
						if ( d1 != null && d2 != null ){
							//Primitive math
							double v1  = d1.doubleValue();
							double v2  = d2.doubleValue();
							double ret = 0;
							
							switch( inst ){
								case OP_ADD:	ret = v1 + v2;	break;
								case OP_SUB:	ret = v1 - v2;	break;
								case OP_MUL:	ret = v1 * v2;	break;
								case OP_DIV:	ret = v1 / v2;	break;
								
								case OP_MOD:	ret = v1 % v2;			break;
								case OP_POW:	ret = Math.pow(v1, v2);	break;
							}
							
							frame.set(A, Double.valueOf(ret));
						} else {
							//Meta math
							String metaKey = getMetaOp(inst);
							Object meta = null;
							
							if ( d1 == null && meta == null ){
								meta = getMetaValue(o1, metaKey);
								
								if ( meta == null )
									throw LuaUtil.slotError(frame, B, "attempt to perform attrimetric on");
							}
							if ( d2 == null && meta == null ){
								meta = getMetaValue(o2, metaKey);
								
								if ( meta == null )
									throw LuaUtil.slotError(frame, C, "attempt to perform attrimetric on");
							}
								
							frame.set(A, call(meta, o1, o2));
						}
						
						break;
					}
					
					case OP_UNM: { //A B	R(A):= -R(B)
						A = getA8(code);
						B = getB9(code);
						
						Object value = frame.get(B);

						if ( value instanceof Double ){
							frame.set(A, Double.valueOf( -((Double) value)) );
						} else {
							Object meta = getMetaValue(value, "__unm");
							
							if ( meta == null )
								throw LuaUtil.slotError(frame, B, "attempt to perform attrimetric on");
							
							frame.set(A, call(meta, value));
						}
						break;
					}
					
					case OP_NOT: //A B	R(A):= not R(B)
						A = getA8(code);
						B = getB9(code);
						
						frame.set(A, !LuaUtil.toBoolean( frame.get(B) ));
						break;
					
					case OP_LEN: { //A B	R(A):= length of R(B)
						A = getA8(code);
						B = getB9(code);

						Object value = frame.get(B);
						
						if ( value instanceof LuaTable ){
							frame.set(A, Double.valueOf( ((LuaTable) value).size() ) );
						} else {
							Object meta = getMetaValue(value, "__len");
							
							if ( meta == null )
								throw LuaUtil.slotError(frame, B, "attempt to get length of");
							
							frame.set(A, call(meta, value));
						}
						
						break;
					}
					
					case OP_CONCAT: { //A B C		R(A):= R(B).. ... ..R(C)
						A = getA8(code);
						B = getB9(code);
						C = getC9(code);

						Object result = "";
						String string;
						
						for ( int index = B; index <= C; index++ ){ //Optimize for multi string concat
							Object concat = frame.get(index); 
							
							string = LuaUtil.rawToString(concat);
							
							if ( result instanceof String && string != null ){ 
								StringBuilder sb = new StringBuilder( (String) result );
								
								while( string != null ){
									sb.append( string );
									
									if ( ++index > C ){
										concat = null;
										break;
									}
									
									concat 	= frame.get(index);
									string	= LuaUtil.rawToString(concat);
								}
								
								result = sb.toString();
							}
							
							if ( concat != null ){
								Object meta = getMetaValue(concat, "__concat");
								
								if ( !isCallable(meta) )
									throw LuaUtil.slotError(frame, index, "attempt to concenate");
							
								result = call( meta, result, concat );
							}
						}
						
						frame.set(A, result);
						break;
					}
					
					case OP_JMP: //sBx		pc+=sBx
						frame.pc += getSBx(code);
						break;
					
					case OP_EQ: //A B C		if ((RK(B) == RK(C)) ~= A) then pc++
					case OP_LE: //A B C		if ((RK(B) <= RK(C)) ~= A) then pc++
					case OP_LT: //A B C		if ((RK(B) <  RK(C)) ~= A) then pc++
						A = getA8(code);
						B = getB9(code);
						C = getC9(code);

						Object o1 = getRK(frame, B, proto);
						Object o2 = getRK(frame, C, proto);
						
						if ( compare(o1, o2, inst) != (A == 1) )
							frame.pc++;
					
						break;
					
					case OP_TEST: //A C		if not (R(A) <=> C) then pc++
						A = getA8(code);
						C = getC9(code);
						
						if ( LuaUtil.toBoolean( frame.get(A) ) == (C == 0) )
							frame.pc++;
						break;
											
					case OP_TESTSET: { //A B C	if (R(B) <=> C) then R(A):= R(B) else pc++
						A = getA8(code);
						B = getB9(code);
						C = getC9(code);

						Object value = frame.get(B);
						
						if ( LuaUtil.toBoolean(value) != (C == 0) ){
							frame.set(A, value);
						} else {
							frame.pc++;
						}
						break;
					}
					
					case OP_CALL: { //A B C		R(A), ... ,R(A+C-2):= R(A)(R(A+1), ... ,R(A+B-1))
						A = getA8(code);
						B = getB9(code);
						C = getC9(code);
						
						if ( hook != null ) //Notify the debug hook
							hook.passEvent(coroutine, DebugHook.MASK_CALL);
						
						int cArgCount = B -1;
						
						if ( cArgCount != -1 ){
							frame.setTop(A + cArgCount +1);
						} else {
							cArgCount = frame.getTop() - A -1;
						}
						frame.restoreTop = ( C != 0 );
						
						//Calculate stack offsets
						int base = frame.localBase;
						
						int cLocalBase	= base + A +1;
						int cReturnBase	= base + A;
						
						Object func = frame.get(A);
					
						if ( !isCallable(func) ){ //Allow __call override
							Object meta = getMetaValue(func, "__call");
						
							if ( func != meta ){
								func = meta;
								
								cLocalBase = cReturnBase;
								cArgCount++;
							}
						}
						
						if ( func instanceof LuaClosure ){
							CallFrame callFrame = coroutine.pushCallFrame((LuaClosure) func, cLocalBase, cReturnBase, cArgCount);
								callFrame.fromLua	= true;
								callFrame.canYield	= frame.canYield;
							
							callFrame.init();
						
							frame	= callFrame;
							closure	= callFrame.closure;
							
							proto	= closure.proto;
							opcodes	= proto.code;
							
							returnBase = callFrame.returnBase;
						} else if ( func instanceof Callable ){
							callJava((Callable) func, cLocalBase, cReturnBase, cArgCount);
							
							frame = coroutine.getCurrentFrame();

							if ( frame == null || !frame.isLua() )
								return; //Got back from a yield to java
							
							closure	= frame.closure;
							
							proto	= closure.proto;
							opcodes	= proto.code;
							
							returnBase	= frame.returnBase;
							
							if ( frame.restoreTop )
								frame.setTop( proto.maxStacksize );
						} else {
							throw LuaUtil.slotError(frame, A, "attempt to call");
						}

						break;
					}
					
					case OP_TAILCALL: { //A B C		return R(A)(R(A+1), ... ,R(A+B-1))
						A = getA8(code);
						B = getB9(code);
						
						if ( hook != null ) //Notify the debug hook
							hook.passEvent(coroutine, DebugHook.MASK_CALL);
						
						int cArgCount = B -1;
						
						if ( cArgCount == -1 )
							cArgCount = frame.getTop() - A -1;

						frame.restoreTop = false;
						
						//Calculate stack offsets
						int base 		= frame.localBase;
						int cLocalBase	= returnBase +1;
						
						coroutine.closeUpvalues(base);

						Object func = frame.get(A);
						
						if ( !isCallable(func) ){ //Allow __call override
							Object meta = getMetaValue(func, "__call");
							
							if ( func != meta ){
								func = meta;
								
								cLocalBase = returnBase;
								cArgCount++;
							}
						}
						
						coroutine.stackCopy(base + A, returnBase, cArgCount +1);
						coroutine.setTop(returnBase + cArgCount + 1);

						if ( func instanceof LuaClosure ){
							frame.localBase	= cLocalBase;
							frame.argCount	= cArgCount;
							
							frame.closure	= (LuaClosure) func;
							frame.init();
						} else if ( func instanceof Callable ){
							Coroutine caller = coroutine;
							
							callJava((Callable) func, cLocalBase, returnBase, cArgCount);
							
							frame = coroutine.getCurrentFrame();
							caller.popCallFrame();
							
							if ( caller != coroutine ) {
								if ( caller.isDead() ){ //Handle implicit yields
									if ( caller == root ){
										//Umm, yielding the root?
										throw new IllegalStateException("Implicit yield in root");
									} else if ( coroutine.getParent() == caller ) { //Returning back to parent
										throw new LuaException("Unimplemented implicit yield in OP_TAILCALL");
									}	
								}
								
								frame = coroutine.getCurrentFrame();
								
								if ( !frame.isLua() )
									return;		
							} else {
								if ( !frame.fromLua )
									return;
									
								frame = coroutine.getCurrentFrame();
							}
						} else {
							throw LuaUtil.slotError(frame, A, "attempt to call");
						}

						closure	= frame.closure;
						proto	= closure.proto;
						
						opcodes 	= proto.code;
						returnBase	= frame.returnBase;
						
						if ( frame.restoreTop )
							frame.setTop( proto.maxStacksize );
						
						break;
					}
					
					case OP_RETURN: { //A B		return R(A), ... ,R(A+B-2)
						A = getA8(code);
						B = getB9(code) - 1;

						if ( hook != null ) //Notify the debug hook
							hook.passEvent(coroutine, DebugHook.MASK_RETURN);
						
						int base = frame.localBase;
						coroutine.closeUpvalues(base);

						if (B == -1)
							B = frame.getTop() - A;

						coroutine.stackCopy(frame.localBase + A, returnBase, B);
						coroutine.setTop(returnBase + B);
						
						if ( frame.fromLua ){
							if ( frame.canYield && coroutine.isAtBottom() ){
								frame.localBase	= frame.returnBase;
								
								Coroutine caller = coroutine;
								Coroutine.yield(frame, frame, B);
								
								caller.popCallFrame();

								frame = coroutine.getCurrentFrame();
								
								if ( frame == null || !frame.isLua() ) //Return if called from java
									return;
							} else {
								coroutine.popCallFrame();
							}
							
							frame 	= coroutine.getCurrentFrame();
							closure	= frame.closure;
							
							proto	= closure.proto;
							opcodes	= proto.code;
							
							returnBase	= frame.returnBase;
						
							if ( frame.restoreTop )
								frame.setTop( proto.maxStacksize );
						
							break;
						} else {
							coroutine.popCallFrame();
							return;
						}
					}
					

					case OP_FORLOOP: { //A sBx		R(A)+=R(A+2): if R(A) <?= R(A+1) then { pc+=sBx: R(A+3)=R(A) }
						A = getA8(code);

						Double index	= (Double) frame.get(A);
						Double limit	= (Double) frame.get(A +1);
						Double step		= (Double) frame.get(A +2);
							index += step;
						
						if ( step > 0 ? index <= limit : index >= limit ){
							frame.pc += getSBx(code);
							
							frame.set(A, index);
							frame.set(A +3, index);
						} else {
							frame.clearFromIndex(A);
						}
						break;
					}
					case OP_FORPREP: { //A sBx		R(A)-=R(A+2): pc+=sBx
						A = getA8(code);
						B = getSBx(code);

						Object index 	= frame.get(A);
						Object step		= frame.get(A +2);
						
						if ( !(index instanceof Double && step instanceof Double) )
							throw new LuaException("invalid for preparation");
						
						frame.set(A, ((Double) index) - ((Double) step));
						frame.pc += B;
						break;
					}
					case OP_TFORLOOP: {		
						/*	
							R(A+3), ... ,R(A+2+C):= R(A)(R(A+1),R(A+2)): 
							
							if R(A+3) ~= nil then R(A+2)=R(A+3)
							else pc++
						*/
						A = getA8(code);
						C = getC9(code);

						frame.setTop(A +6);
						frame.stackCopy(A, A +3, 3);
						call(2);
						frame.clearFromIndex(A + C +3);
						frame.setPrototypeStacksize();

						Object value = frame.get(A + 3);
						if ( value != null ) {
							frame.set(A +2, value);
						} else {
							frame.pc++;
						}
						break;
					}
					

					case OP_SETLIST: { //A B C		R(A)[(C-1)*FPF+i]:= R(A+i), 1 <= i <= B
						A = getA8(code);
						B = getB9(code);
						C = getC9(code);

						if ( B == 0 )
							B = frame.getTop() - A -1;
						
						if ( C == 0 )
							C = opcodes[frame.pc++];
						
						int offset = (C - 1) * FIELDS_PER_FLUSH;

						LuaTable table = (LuaTable) frame.get(A);
						for ( int index = 1; index <= B; index++ ){
							Object key 		= Double.valueOf(offset + index);
							Object value	= frame.get(A + index);
							
							table.rawset(key, value);
						}
						
						frame.setTop( proto.maxStacksize ); //Restore top. (In case of last multret fucked it up)
						break;
					}
					
					case OP_CLOSE: //A		close upvalues up to A
						frame.closeUpvalues( getA8(code) );
						break;
					
					case OP_CLOSURE: { //A Bx	R(A):= closure(KPROTO[Bx], R(A), ... ,R(A+n))
						A = getA8(code);
						B = getBx(code);
						
						Prototype newProto		= proto.prototypes[B];
						LuaClosure newClosure	= new LuaClosure(newProto, closure.env);
						
						frame.set(A, newClosure);
						
						for ( int index = 0; index < newProto.numUpvalues; index++ ){
							code	= opcodes[frame.pc++];
							inst	= getOp(code);
							
							B = getB9(code);
							
							switch( inst ){
								case OP_MOVE:		newClosure.upvalues[index] = frame.findUpvalue(B);	break;
								case OP_GETUPVAL:	newClosure.upvalues[index] = closure.upvalues[B];	break;
							}
						}
						break;
					}
					
					case OP_VARARG: { //A B		R(A), R(A+1), ..., R(A+B-1) = vararg
						A = getA8(code);
						B = getB9(code);

						frame.pushVarargs(A, B -1);
						break;
					}
					
					default:
						throw new LuaException("broken bytecode (unknown inst:" + inst + ")");
				}
			}catch( RuntimeException err ){			
				coroutine.beginStackTrace(frame, err);
					
				while( true ){ //Pop all java frames on top
					frame = coroutine.getCurrentFrame();
					
					if ( frame == null || frame.isLua() )
						break;
					
					coroutine.addStackTrace(frame);
					coroutine.popCallFrame();
				}
				
				boolean doThrow = true;
				
				while( true ){
					
					if ( coroutine.isDead() ){ //Reached the bottom of a coroutine, nothing to pop off
						Coroutine parent = coroutine.getParent();
						
						if ( parent != null ){ //Return to parent coroutine (if it has)
							CallFrame nextFrame = parent.getCurrentFrame();
							
							nextFrame.push( Boolean.FALSE );
							nextFrame.push( LuaUtil.getExceptionCause(err) );
							nextFrame.push( coroutine.getStackTrace() );

							coroutine.resetStackTrace();
							coroutine.detach();
							coroutine = parent;
							
							frame 	= coroutine.getCurrentFrame();
							
							if ( frame == null || !frame.isLua() )
								return; //Return if called from java
							
							closure	= frame.closure;
							
							proto	= closure.proto;
							opcodes	= proto.code;
							
							returnBase	= frame.returnBase;
							
							if ( frame.restoreTop )
								frame.setTop( proto.maxStacksize );
							
							doThrow = false;
						}
						break;
					} else {
						frame = coroutine.getCurrentFrame();
						
						coroutine.addStackTrace(frame);
						coroutine.popCallFrame();
					}
					
					if ( !frame.fromLua )
						break;
				}
				
				if ( frame != null ) //Close upvalues before resuming
					frame.closeUpvalues(0);
				
				if ( doThrow )
					throw err;
			}
		}
	}
		
	/*
	 * General
	 */
	public Object tableGet( Object table, Object key ){
		Object cTable = table;
		
		for ( int depth = 0; depth < MAX_INDEX_RECURSION; depth++ ){
			boolean isTable = ( cTable instanceof LuaTable );
			
			if ( isTable ){
				Object value = ((LuaTable) cTable).rawget(key);
				
				if ( value != null )
					return value;
			}
			
			Object meta = getMetaValue(cTable, "__index");
			if ( meta == null ){
				if ( isTable ) return null;
				
				throw new LuaException("attempt to index a "+ platform.getTypename(cTable) +" value");
			}
			
			if ( isCallable(meta) ){
				return call(meta, table, key);
			} else {
				cTable = meta;
			}
		}
		
		throw new LuaException("loop in gettable");
	}
	
	public void tableSet( Object table, Object key, Object value ){
		Object cTable = table;
		
		for ( int depth = 0; depth < MAX_INDEX_RECURSION; depth++ ){
			Object meta = null;
			
			if ( cTable instanceof LuaTable ){
				LuaTable tbl = (LuaTable) cTable;
				
				//If set or __newindex == null
				if ( tbl.rawget(key) != null || (meta = getMetaValue(cTable, "__newindex")) == null ){
					tbl.rawset(key, value);
					return;
				}
			} else {
				meta = getMetaValue(cTable, "__newindex");
				
				if ( meta == null )
					throw new LuaException("attempt to index a "+ platform.getTypename(cTable) +" value");
			}
			
			if ( isCallable(meta) ){
				call(meta, table, key, value);
				return;
			} else {
				cTable = meta;
			}
		}
		
		throw new LuaException("loop in settable");
	}

	public boolean compare( Object a, Object b, int opcode ){
		switch( opcode ){
			case OP_EQ:			
				
				if ( a == null || b == null ) //Speed up nil comparison
					return a == b;
				
				if ( a instanceof Double && b instanceof Double ){
					return a.equals(b);
				} else if ( a instanceof String && b instanceof String ){
					return a.equals(b);
				} else {
					Object meta = getSharedMetaValue(a, b, "__eq");
				
					if ( meta == null )
						return a.equals( b ); //OP_EQ shall be handled at all times
				
					return LuaUtil.toBoolean( call(meta, a, b) );
				}
				
			case OP_LE:
			case OP_LT:
				
				//Primitive comparison
				boolean isPrimitive = false;
				int compare = 0;
				
				if ( a instanceof Double && b instanceof Double ){
					isPrimitive = true;
					
					compare = ((Double) a).compareTo( (Double) b );
				} else if ( a instanceof String && b instanceof String ){
					isPrimitive = true;
					
					compare = ((String) a).compareTo( (String) b );
				}
				
				if ( isPrimitive )
					return ( opcode == OP_LT ? compare < 0 : compare <= 0 );
				
				//Meta comparison
				boolean isInverted = false;
				
				Object meta = getSharedMetaValue(a, b, getMetaOp(opcode));
				
				if ( meta == null && opcode == OP_LE ){ // OP_LE(A,B) becomes NOT OP_LT(B,A)
					meta = getSharedMetaValue(a, b, "__lt");
					
					Object tmp = a; a = b; b = tmp;
					isInverted = true;
				}
				
				if ( meta == null )
					throw new LuaException( "attempt to compare a "+ platform.getTypename(a) +" with a "+ platform.getTypename(b) +" value");
				
				return LuaUtil.toBoolean( call(meta, a, b) ) == !isInverted;
		
			default:
				throw new IllegalArgumentException("bad comparison opcode");
		}
		
	}
	
	public Object tostring( Object value ){
		if ( value == null )
			return "nil";
			
		if ( value instanceof Boolean || value instanceof Double )
			return value.toString();
		
		if ( value instanceof String )
			return (String) value;
		
		if ( isCallable(value) )
			return "function: " + System.identityHashCode(value);
		
		Object meta = getMetaValue(value, "__tostring");
		if ( meta != null )
			return call( meta, value );
		
		return value.toString();
	}

}
