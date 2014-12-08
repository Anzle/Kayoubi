/**
 * Richard Gerdes
 * Robert Williams
 * Rahul Purwah
 * **/

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

import GivenTools.TorrentInfo;


public class TorrentHandler {
	
	TorrentInfo torrentInfo;			//torrent info
	private HashMap<Integer,Piece> pieces;		//list of file pieces
	private boolean[] recieved;					//stored if piece was received
	private int completedPieceCount = 0;
	private String file; //Renamed since it hopefully will load files
	private boolean done = false;
	private PeerManager peerManager;
	private int uploadedTotal;
	
	
/* Have this check if the output file already exists, maybe via another method*/
	public TorrentHandler(TorrentInfo torrentInfo, String outputFile){
		
		this.torrentInfo = torrentInfo;
		this.file = outputFile;
		this.pieces = new HashMap<Integer, Piece>();
		this.recieved = new boolean[this.torrentInfo.piece_hashes.length];
		
		for(int i = 0; i < this.torrentInfo.piece_hashes.length; i++){
			int length = this.torrentInfo.piece_length;
			if(i == this.torrentInfo.piece_hashes.length - 1){
				length = this.torrentInfo.file_length % this.torrentInfo.piece_length;
				if(length == 0){
					length = this.torrentInfo.piece_length;
				}
			}
			Piece p = new Piece(i, length, this.torrentInfo.piece_hashes[i].array());
			this.pieces.put(i, p);
		}
		
		//Check if the file exists, if it does, load the info
		Path path = Paths.get(file);
		if(Files.exists(path)){
			System.out.println("Loading file");
			loadFile();
		}
		
	}
	
/*Change to save every piece on contact*/
	/** 
	 * called by peer. the torrent tracker uses this to save the data of the file
	 * 
	 * @param b new block received
	 * @param p Peer who received the block
	 */
	public void saveBlock(Block b, Peer p){
		Piece piece = this.pieces.get(b.getIndex());
		if(piece!=null)
			piece.saveBlock(b);
		if(piece.complete()){
			this.recieved[b.getIndex()] = true;
			completedPieceCount++;
			if(completedPieceCount == this.torrentInfo.piece_hashes.length){
				saveFile();
				this.done  = true;
			}
			this.peerManager.notifyPeersPieceCompleted(piece.getIndex());
			
		}
	}
	
	/**
	 * request new block for a given peer
	 * 
	 * @param p peer to request from
	 * @deprecated
	 */
	public void requestNewBlockSequential(Peer peer){
		if(this.done)
			return;
		boolean[] peerlist = peer.getBitfield();
		
		for(int i = 0; i < this.recieved.length; i++){
			if(!this.recieved[i] && peerlist[i]){
				Piece p = this.pieces.get(i);
				if(p == null || p.complete() || p.blockWaiting())
					continue;
				int offset = p.getNextBlockOffset();
				if(offset < 0)
					continue;
				int length = p.getBlockLength(offset);
				if(length <= 0)
					continue;
				//System.out.println("Requesting " + i + "-" + offset + "-" + length);
				peer.sendMessage(Message.blockRequestBuilder(i, offset, length));
				return;
			}
		}
		//System.out.println("Oops");
	}

	/**
	 * request new block for a given peer. this function is optimized to request the piece which the least peers have
	 * 
	 * @param p peer to request from
	 */
	public void requestNewBlock(Peer peer){
		if(this.done)
			return;
		
		boolean[] peerlist = peer.getBitfield();
		int[] priorityBV = new int[peerlist.length];
		ArrayList<Peer> peers = this.peerManager.getPeers();
		
		for(Peer p : peers){
			boolean[] pl = p.getBitfield();
			for(int i = 0; i < priorityBV.length && i < pl.length; i++){
				if(pl[i])
					priorityBV[i]++;
			}
		}
		for(int min = 1; min <= peers.size(); min++){
			//System.out.println("priority: " + min);
			for(int i = 0; i < this.recieved.length; i++){
				if(priorityBV[i] > min)
					continue;
				if(!this.recieved[i] && peerlist[i]){
					Piece p = this.pieces.get(i);
					if(p == null || p.complete() || p.blockWaiting())
						continue;
					int offset = p.getNextBlockOffset();
					if(offset < 0)
						continue;
					int length = p.getBlockLength(offset);
					if(length <= 0)
						continue;
					//System.out.println("Requesting " + i + "-" + offset + "-" + length);
					peer.sendMessage(Message.blockRequestBuilder(i, offset, length));
					return;
				}
			}
		}
		//System.out.println("Oops");
	}
	
	public byte[] getBlockData(int index, int offset, int length){
		Piece p = this.pieces.get(index);
		if(p == null)
			return null;
		byte[] data = p.getBlock(offset, length);

		this.uploadedTotal+=data.length;
		return data;
	}
	
/*change to not print out stuff*/
	private void saveFile() {
		byte[] data = new byte[this.torrentInfo.file_length];
		System.out.println("building file...");
		for(int i : this.pieces.keySet()){
			Piece p = pieces.get(i);
			int pind = p.getIndex();
			byte[] pdata = p.getData();
			int dataOffset = pind * this.torrentInfo.piece_length;

			if(pind < 0)
				continue;
			for(int j = 0; j < pdata.length && dataOffset + j < data.length; j++){
				data[dataOffset + j] = pdata[j];
			}
		}
		BufferedOutputStream bos;
		try {
			System.out.println("saving file...");
			bos = new BufferedOutputStream(new FileOutputStream(file));
			bos.write(data);
			bos.flush();
			bos.close();
			System.out.println("File Saved.");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/* This method reads the file and loads
	 *  the downloaded pieces into the Piece Hash
	 * The method goes piece by piece and checks 
	 * its length. It then reads that many bytes
	 * into a byte array before dividing them into
	 * blocks. Each block is then checked against the
	 * hash and thrown out if it is bad
	 * */
	private void loadFile(){
		BufferedInputStream bis;
		
		try {
			bis = new BufferedInputStream(new FileInputStream(file));
			byte[] data;
			
			for(int i : this.pieces.keySet()){
				Piece p = pieces.get(i);
				int offset = 0 - Piece.MAX_BLOCK_LENGTH;
				data = new byte[p.getLength()];
				bis.read(data);
				//System.out.println("loading piece: " + p.getIndex());
				
				for(int j = 0; j < p.getNumBlocks(); j++){
					offset += Piece.MAX_BLOCK_LENGTH; //on first iteration it should become 0
					byte[] blockData = new byte[p.getBlockLength(offset)];
					
					
					for(int k = 0; k<blockData.length;k++)
						blockData[k] = data[offset+k];
						
					Block b = new Block(p.getIndex(), offset, blockData);
					//System.out.println(" -> block: " + offset);
					p.saveBlock(b);
				}
				
				if(p.complete()){
					this.recieved[p.getIndex()] = true;
					completedPieceCount++;
				}
				
				
			}
		//saveFile();
		
		
		
		} catch (FileNotFoundException e) {
			System.err.println("This is akward. The file doesn't exist.");
			//e.printStackTrace();
		} catch (IOException e) {
			System.err.println("The Load has failed with message: "+e.getMessage());
			//e.printStackTrace();
		}
		
		
	}
	
	public int getBytesDownloaded(){
		int total = 0;
		
		for(int i : pieces.keySet()){
			Piece p = pieces.get(i);
			if(p==null)
				continue;
			total += p.bytesCompleted();
		}
		
		return total;
	}
	
	public int getBytesUploaded(){
		return uploadedTotal;
	}
	
	public boolean isDonw(){
		return this.done;
	}

	public void setPeerManager(PeerManager peerManager) {
		this.peerManager = peerManager;
	}
	
	/** Return the array containing a listing of all gotten pieces**/
	public boolean[] getRecieved(){
		return recieved;
	}

}
