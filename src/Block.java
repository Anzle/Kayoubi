
public class Block {
	
	private final int index;
	private final int offset;
	private final byte[] data;
	
	public Block(int index, int offset, byte[] data){
		this.index = index;
		this.offset = offset;
		this.data = data;
	}
	
	public int getIndex(){
		return index;
	}
	
	public int getOffset(){
		return offset;
	}
	
	public byte[] getData(){
		return data;
	}
	
	public int getLength(){
		return data.length;
	}
}
