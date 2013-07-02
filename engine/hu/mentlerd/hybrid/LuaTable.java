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
			
			if ( slot < arrayCapacity ){
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
		
		if ( value == null && hashKeys[slot] != null ){
			clearHashSlot(slot);
		} else {
			fillHashSlot(slot, key, value);
		}
	}
	
	public int size(){
		return hashEntries;
	}
	
	public Object nextKey( Object key ){
		int slot = 0;
		
		if ( key != null ){ //Check if the key is in there
			slot = getHashSlot(key);
			
			if ( hashKeys[slot] == null )
				throw new LuaException("invalid key to 'next'");
			
			slot++;
		}
		
		for ( int index = slot; index < hashCapacity; index++ ){ //Find the next filled slot
			if ( hashKeys[index] != null )
				return hashKeys[index];
		}
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
	
	protected void clearHashSlot( int slot ){
		if ( hashKeys[slot] != null ){
			int hole = slot;
			int curr = ++slot % hashCapacity;
			
			Object key = hashKeys[curr];
			while( key != null ){
				int rawSlot = hashOf(key) % hashCapacity;
				
				if (( curr > slot && (rawSlot <= slot || rawSlot > curr )) ||
					( curr < slot && (rawSlot <= slot && rawSlot > curr )) ){
					
					hashKeys[hole]   = hashKeys[curr];
					hashValues[hole] = hashValues[curr];
					
					hole = curr;
				}
				
				key = hashKeys[ ++curr % hashCapacity ];
			}
			
			hashKeys[hole]   = null;
			hashValues[hole] = null;
			
			hashEntries--;			
		}
	}
	
}
