package hu.mentlerd.hybrid.lib;

import java.util.Random;

import hu.mentlerd.hybrid.CallFrame;
import hu.mentlerd.hybrid.Callable;
import hu.mentlerd.hybrid.LuaException;
import hu.mentlerd.hybrid.LuaTable;

public class MathLib {
	
	public static LuaTable bind(){
		return bind( new LuaTable() );
	}
	public static LuaTable bind( LuaTable into ){
		into.rawset("e",		Math.E);
		into.rawset("pi", 	Math.PI);
			
		into.rawset("huge",	Double.POSITIVE_INFINITY);
		
		OneArgMath.bind(into);
		TwoArgMath.bind(into);
		
		return into;
	}
	
	protected static class OneArgMath implements Callable{
		protected static String[] METHODS = {
			"abs",	"ceil",	"floor",
			"sin",	"cos",	"tan",
			"sinh",	"cosh",	"tanh", 
			"asin", "acos", "atan",
			"deg",	"rad",	"exp",
			"sqrt",	"log",	"log10",
		};
		
		protected static void bind( LuaTable math ){
			for ( int index = 0; index < METHODS.length; index++ )
				math.rawset( METHODS[index], new OneArgMath(index) );
		}
		
		protected final int methodID;
		protected OneArgMath( int methodID ){
			this.methodID = methodID;
		}
		
		public int call(CallFrame frame, int argCount) {
			Double param = frame.getArg(0, Double.class);
			Double ret	 = null;
			
			switch( methodID ){
				case 0:		ret = Math.abs(param);	break;
				
				case 1:		ret = Math.ceil(param);		break;
				case 2:		ret = Math.floor(param);	break;
				
				case 3:		ret = Math.sin(param);		break;
				case 4:		ret = Math.cos(param);		break;
				case 5:		ret = Math.tan(param);		break;
				
				case 6:		ret = Math.sinh(param);		break;
				case 7:		ret = Math.cosh(param);		break;
				case 8:		ret = Math.tanh(param);		break;
				
				case 9:		ret = Math.asin(param);		break;
				case 10:	ret = Math.acos(param);		break;
				case 11:	ret = Math.atan(param);		break;
				
				case 12:	ret = Math.toDegrees(param);	break;
				case 13:	ret = Math.toRadians(param);	break;
				
				case 14:	ret = Math.exp(param);		break;
				case 15:	ret = Math.sqrt(param);		break;
					
				case 16:	ret = Math.log(param);		break;
				case 17:	ret = Math.log10(param);	break;
				
				default:
					throw new LuaException("Not implemented math function");
			}
			
			frame.push(ret);
			return 1;
		}		
	}
	protected static class TwoArgMath implements Callable{
		protected static String[] METHODS = {
			"frexp",	"atan2",	"fmod",
			"ldexp",	"pow",
		};
		
		protected static void bind( LuaTable math ){
			for ( int index = 0; index < METHODS.length; index++ )
				math.rawset( METHODS[index], new TwoArgMath(index) );
		}
		
		protected final int methodID;
		protected TwoArgMath( int methodID ){
			this.methodID = methodID;
		}
		
		public int call(CallFrame frame, int argCount) {
			Double A = frame.getArg(0, Double.class);
			Double B = frame.getArg(1, Double.class);
			
			Double ret	 = null;
			
			switch( methodID ){
				case 1:		ret = Math.atan2(A, B);		break;
				case 4:		ret = Math.pow(A, B);		break;
				
				default:
					throw new LuaException("Not implemented math function");
			}
			
			frame.push(ret);
			return 1;
		}
	}
	
	protected static class SpecialMath implements Callable{
		protected static String[] METHODS = {
			"min",	"max",	"modf",
			"random",	"randomseed"
		};
		
		protected static void bind( LuaTable math ){
			math.rawset("min",  new SpecialMath(0));
			math.rawset("max",  new SpecialMath(1));
			math.rawset("modf", new SpecialMath(2));
			
			Random random = new Random();
			
			math.rawset("randomseed", 	new SpecialMath(3, random));
			math.rawset("random", 		new SpecialMath(4, random));
		}
		
		protected final int methodID;
		protected Random random;
		
		protected SpecialMath( int methodID ){
			this.methodID = methodID;
		}
		protected SpecialMath( int methodID, Random random ){
			this.methodID	= methodID;
			this.random 	= random;
		}
		
		public int call(CallFrame frame, int argCount) {
			switch( methodID ){
				case 0: { //min 
					Double min = Double.MAX_VALUE;
					Double param;
					
					for ( int index = 0; index < argCount; index++ ){
						param = frame.getArg(index, Double.class);
						
						if ( min > param )
							min = param;
					}
					
					frame.push(min);
					return 1;
				}
				case 1: { //max 
					Double max = Double.MIN_VALUE;
					Double param;
					
					for ( int index = 0; index < argCount; index++ ){
						param = frame.getArg(index, Double.class);
						
						if ( param > max )
							max = param;
					}
					
					frame.push(max);
					return 1;
				}
				
				case 2: { //modf
					Double arg = frame.getArg(0, Double.class);
					
					Double intPart	= (arg > 0 ? Math.floor(arg) : Math.ceil(arg));
					Double fracPart	= arg - intPart;
					
					frame.push(intPart);
					frame.push(fracPart);
					return 2;
				}
				
				case 3: { //randomseed
					random.setSeed( frame.getArg(0, Double.class).longValue() );
					return 0;
				}
				case 4: { //random
					if ( argCount == 0 ){
						frame.push( random.nextDouble() );
					} else {
						int min = frame.getIntArg(0, 0);
						int max = frame.getIntArg(1);
						
						if ( max < min )
							throw new LuaException("Empty interval");
						
						frame.push(min + random.nextInt( max - min +1 ));
					}
				
					return 1;
				}
				
				default:
					throw new LuaException("Not implemented math function");
			}
		}
	}
}
