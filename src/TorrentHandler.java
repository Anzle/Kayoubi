import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import GivenTools.TorrentInfo;


public class TorrentHandler {
	
	TorrentInfo torrentInfo;			//torrent info
	private HashMap<Integer,Piece> pieces;		//list of file pieces
	private boolean[] recieved;					//stored if piece was received
	private int completedPieceCount = 0;
	private String outputFile;
	private boolean done = false;
	private PeerManager peerManager;
	private int uploadedTotal;
	public TorrentHandler(TorrentInfo torrentInfo, String outputFile){
		
		this.torrentInfo = torrentInfo;
		this.outputFile = outputFile;
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
		
	}
	
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
			for(int i = 0; i < priorityBV.length; i++){
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
			bos = new BufferedOutputStream(new FileOutputStream(outputFile));
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

}
