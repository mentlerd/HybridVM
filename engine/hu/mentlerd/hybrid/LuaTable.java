package hu.mentlerd.hybrid;

public class LuaTable {
	
	protected static int findPowerOfTwo( int n ){
		int res = 1;
		
		while (res < n)
			res <<= 1;
		
		return res;
	}
	
	protected static Object[] realloc( Object[] array, int size ){
		Object[] res = new Object[size];
			System.arraycopy(array, 0, res, 0, Math.min(size, array.length));
		
		return res;
	}
	
	public static final double HASH_LOAD_FACTOR		= 0.8;
	public static final double ARRAY_LOAD_FACTOR	= 0.8;
	
	public static final int INITIAL_HASH_SIZE 	= 16;
	public static final int INITIAL_ARRAY_SIZE 	= 8;
	
	//Hash map
	protected Object[] hashKeys;
	protected Object[] hashValues;
	
	protected int hashCapacity;
	protected int hashEntries;
	
	//Array
	protected Object[] array;
	
	protected int arrayCapacity;
	protected int arrayEntries;
	
	//Meta
	protected LuaTable metatable;
	
	public LuaTable(){
		this( INITIAL_ARRAY_SIZE, INITIAL_HASH_SIZE );
	}
	
	public LuaTable( int arraySize, int hashSize ){
		arrayCapacity	= findPowerOfTwo(arraySize);
		hashCapacity 	= findPowerOfTwo(hashSize);
		
		array		= new Object[arrayCapacity];
		
		hashKeys 	= new Object[hashCapacity];
		hashValues	= new Object[hashCapacity];
	}
	
	/*
	 * Lua methods
	 */
	public Object rawget( Object key ){
		if ( key == null )
			throw new LuaException("table index is nil");
		
		//In case of integer indexes, check the table first, but allow a hash search too
		if ( key instanceof Double ){
			Double index	= (Double) key;
			int slot		= index.intValue();
			
			if ( index == slot && 0 <= slot && slot < arrayCapacity ){
				Object value = array[slot];
				
				if ( value != null )
					return value;
			}
		}
		
		return hashValues[ getHashSlot(key) ];
	}
	
	public void rawset( Object key, Object value ){
		if ( key == null )
			throw new LuaException("table index is nil");
		
		//In case of integer indexes, try to put into the array instead
		if ( key instanceof Double ){
			Double index 	= (Double) key;
			int slot		= index.intValue();
			
			if ( index == slot && fillArraySlot(slot, value) )
				return; //The array accepted the value
		}
		
		int slot = getHashSlot(key);
		
		if ( value == null ){
			if ( hashKeys[slot] != null )
				clearHashSlot(slot);
		} else {
			fillHashSlot(slot, key, value);
		}
	}
	
	public int size(){
		return hashEntries;
	}
	
	public Object nextKey( Object key ){
		int arrayIndex	= -1; //Disallow search
		int hashSlot	=  0;
	
		if ( key == null ){
			arrayIndex	= 0; //Allow searching for values
		} else {
			
			//Check for starting index
			if ( key instanceof Double ){
				Double slot	= (Double) key;
				int index	= slot.intValue();
				
				if ( index == slot && 0 <= index && index < arrayCapacity ){
					arrayIndex	= index;
					hashSlot	= -1;
					
					if ( array[arrayIndex] == null )
						throw new LuaException("invalid key to 'next'");
					
					arrayIndex++;
				}
			}
			
			//Check hash slot (if allowed)
			if ( hashSlot != -1 ){
				hashSlot = getHashSlot(key);
			
				if ( hashKeys[hashSlot] == null )
					throw new LuaException("invalid key to 'next'");
				
				hashSlot++;
			}
		}
		
		//Find the next array value (If allowed)
		if ( arrayIndex != -1 ){
			for( int index = arrayIndex; index < arrayCapacity; index++ ){
				if ( array[index] != null )
					return Double.valueOf(index);
			}
			
			//No more keys in the array, allow hash search
			hashSlot = 0;
		}
		
		//Find the next hash slot
		for ( int index = hashSlot; index < hashCapacity; index++ ){ //Find the next filled slot
			if ( hashKeys[index] != null )
				return hashKeys[index];
		}

		//Absolute nothing found
		return null;
	}
	
	/*
	 * Generic
	 */
	public void setMetatable( LuaTable meta ){
		this.metatable = meta;
	}
	public LuaTable getMetatable(){
		return metatable;
	}
	
	/*
	 * Array
	 */
	private boolean fillArraySlot( int slot, Object value ){
		if ( slot < 0 ) return false;
		
		if ( slot < arrayCapacity ){
			boolean slotIsTaken = ( array[slot] != null );
			
			if ( value != null && !slotIsTaken ) arrayEntries++;
			if ( value == null &&  slotIsTaken ) arrayEntries--;
			
			array[slot] = value;
			return true;
		} else if ( slot < arrayCapacity *2 ){
			
			//Consider reallocating
			if ( arrayEntries > arrayCapacity * ARRAY_LOAD_FACTOR ){
				arrayCapacity <<= 1;
				array = realloc( array, arrayCapacity );
			} else {
				return false;
			}
			
			return fillArraySlot(slot, value);
		}
		
		return false;
	}

	
	/*
	 * HashMap
	 */
	protected int hashOf( Object obj ){
		int code = obj.hashCode();
		
		code ^= ( code >>> 20 ) ^ ( code >>> 12 );
		code ^= ( code >>> 7 )  ^ ( code >>> 4 );
		
		return code;
	}
	
	protected int getHashSlot( Object key ){
		int hashSlot = hashOf( key ) & ( hashCapacity -1 );
		
		Object hashKey = null;
		while( (hashKey = hashKeys[hashSlot]) != null && !key.equals( hashKey ) )
			hashSlot = ++hashSlot % hashCapacity;
		
		return hashSlot;
	}
	
	private void fillHashSlot( int slot, Object key, Object value ){
		boolean isNew = ( hashKeys[slot] == null );
		
		hashKeys[slot]		= key;
		hashValues[slot] 	= value;
		
		if ( isNew ){
			hashEntries++;
			
			if ( hashEntries > hashCapacity * HASH_LOAD_FACTOR )
				expandHash();
		}
	}

	private void expandHash(){
		int oldSize = hashCapacity;
		hashCapacity = oldSize << 1;
			
		Object[] oldKeys   = hashKeys;
		Object[] oldValues = hashValues;
		
		hashKeys   = new Object[hashCapacity];
		hashValues = new Object[hashCapacity];
		
		for ( int i = 0; i < oldSize; i++ ){
			Object oldKey = oldKeys[i];
			
			if ( oldKey != null ){
				int slot = getHashSlot(oldKey);
				
				hashKeys[slot]   = oldKey;
				hashValues[slot] = oldValues[i];
			}
		}
	}
	
	protected void clearHashSlot( int removed ){
		if ( hashKeys[removed] == null ) return;
		
		int space = removed;
		int check = (removed +1) % hashCapacity;
		
		Object key = hashKeys[check];
		while( key != null ){
			int desired = hashOf( key ) & ( hashCapacity -1 );
	
		//	if ((check > removed && desired < check) ||	--Before overflow and target
		//		(check < removed && desired > check) ){ --After overflow and target
			
			if ( check > removed == desired < check ){
				hashKeys[space]		= hashKeys[check];
				hashValues[space]	= hashValues[check];
				
				space = check;
			}
			
			key	= hashKeys[ ++check % hashCapacity ];
		}
		
		hashKeys[space]		= null;
		hashValues[space]	= null;
		
		hashEntries--;
	}
	
}
