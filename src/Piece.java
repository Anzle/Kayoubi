import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;


public class Piece {

	public static final int MAX_BLOCK_LENGTH = 16384;
	
	private int index;
	private int length;
	private byte[] hash;
	private byte[] data = null;

	private int[] blockOffsets;
	private int[] blockLengths;
	private long[] blockLastRequested;
	private HashMap<Integer, Block> blocks;
	private boolean completed = false;
	
	public Piece(int index, int length, byte[] hash){
		this.index = index;
		this.length = length;
		this.hash = hash;
		blocks = new HashMap<Integer,Block>();
		
		int count = this.length / MAX_BLOCK_LENGTH;
		if(this.length % MAX_BLOCK_LENGTH > 0)
			count++;
		
		this.blockOffsets = new int[count];
		this.blockLengths = new int[count];
		this.blockLastRequested = new long[count];
		
		for(int i = 0; i < count;i++){
			this.blockOffsets[i] = (i * MAX_BLOCK_LENGTH);
			this.blockLengths[i] = MAX_BLOCK_LENGTH;
			if(i == count - 1 && this.length % MAX_BLOCK_LENGTH > 0)
				this.blockLengths[i] = (this.length % MAX_BLOCK_LENGTH);
		}
	}
	
	public int getIndex(){
		return this.index;
	}
	
	public byte[] getData(){
		byte[] data = new byte[length];
		for(Integer offset : this.blocks.keySet()){
			Block b = this.blocks.get(offset);
			if(b == null)
				continue;
			byte[] bdata = b.getData();
			for(int i = 0; i < b.getLength() && offset + i < this.length; i++){
				data[offset+i] = bdata[i];
			}
		}
		return data;
	}
	
	public int bytesCompleted(){
		int num = 0;
		for(Integer k : this.blocks.keySet()){
			Block b = this.blocks.get(k);
			if(b != null)
				num += b.getLength();
		}
		
		return num;
		
	}
	
	public byte[] getBlock(int offset, int length){
		if(!this.completed)
			return null;
		if(offset + length > this.length)
			return null;
		
		byte[] block = new byte[length];
		
		for(int i = 0; i < length; i++){
			block[i] = this.data[offset + length];
		}
		
		return block;
	}
	
	public int getLength(){
		return this.length;
	}

	public boolean complete() {
		return completed;
	}
	
	public boolean isValid(){
		try {
			MessageDigest hasher = MessageDigest.getInstance("SHA");
			byte[] result = hasher.digest(this.getData());
			hasher.reset();
			if(result.length != hash.length){
				System.err.println("Hash check for piece " + this.index + " failed with hash length mismatch.");
				return false;
			}
			//System.out.println("hash-"+ (new String(hash)));
			//System.out.println("newh-"+ (new String(result)));
			for(int i = 0; i < result.length; i++){
				if(result[i] != hash[i]){
					System.err.println("Hash check for piece " + this.index + " failed at byte " + i + ".");
					return false;
				}
			}
		} catch (NoSuchAlgorithmException e) {
			System.err.println("Unable to check hash of piece. Invalid Algorithm");
		} 
		return true;
	}
	
	public void saveBlock(Block b) {
		if(b.getIndex() != this.index)
			return;
		if(completed)
			return;
		this.blocks.put(b.getOffset(), b);
		this.checkCompleted();
	}
	
	private void checkCompleted(){
		for(int i = 0; i < this.blockOffsets.length; i++){
			if(!blocks.containsKey(this.blockOffsets[i])){
				return;
			}
		}
		if(this.isValid()){
			this.completed = true;
			this.data = new byte[this.length];
			for(int key : this.blocks.keySet()){
				Block b = this.blocks.get(key);
				int offset = b.getOffset();
				byte[] bdata = b.getData();
				for(int j = 0; j < bdata.length && offset + j < this.length; j++ ){
					this.data[offset + j] = bdata[j];
				}
			}
			
		}else
			this.blocks = new HashMap<Integer, Block>();
	}

	public boolean blockWaiting() {
		for(int i = 0; i < this.blockOffsets.length; i++){
			if(this.blocks.containsKey(blockOffsets[i]))
				continue;
			if(this.blockLastRequested[i] - System.currentTimeMillis() < 500)
				continue;
			return true;
		}
		return false;
	}

	public int getNextBlockOffset() {
		for(int i = 0; i < this.blockOffsets.length; i++){
			if(this.blocks.containsKey(blockOffsets[i]))
				continue;
			if(System.currentTimeMillis() - this.blockLastRequested[i] < 500)
				continue;
			this.blockLastRequested[i] = System.currentTimeMillis();
			return blockOffsets[i];
		}
		
		return -1;
	}

	public int getBlockLength(int offset) {
		for(int i = 0; i < this.blockOffsets.length; i++){
			if(offset == this.blockOffsets[i])
				return this.blockLengths[i];
		}
		return -1;
	}

}
