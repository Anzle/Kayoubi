import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class Peer extends Thread {

	byte[] id;
	String ip;
	int port;

	Socket socket;
	DataInputStream from_peer;
	DataOutputStream to_peer;
	private boolean peer_choking = false;
	private boolean peer_interested = false;
	private boolean am_choking = false;
	private boolean am_interested = false;
	
	private boolean pulse;

	boolean[] bitfield;

	private TorrentHandler torrentHandler;
	private byte[] clientID;
	private long lastMessageSent;
	private Thread keepAlive;
	
	/**Connect outwards to a Peer
	 * This is used when looking to a Peer to download from them
	 * */
	public Peer(String id, String ip, int port, TorrentHandler torrentHandler, byte[] clientID){
		// TODO Auto-generated constructor stub
		this.id = id.getBytes();
		this.ip = ip;
		this.port = port;
		this.torrentHandler = torrentHandler;
		this.clientID = clientID;
		this.bitfield = new boolean[this.torrentHandler.torrentInfo.piece_hashes.length];
		pulse = false;
	}

	/**
	 * Connect inwards to a Peer
	 * 	This is used when a Peer wants us to upload to it
	 * 	The main difference between the constructors is that a socket
	 *  has already been created
	 *  The id will be obtained during the handshake*/
	public Peer(Socket connection,TorrentHandler torrentHandler, byte[] clientID){
		socket = connection;
		port = socket.getLocalPort();
		ip = socket.getInetAddress().getHostAddress();
		id = new byte[68];
		this.torrentHandler = torrentHandler;
		this.clientID = clientID;
		bitfield = new boolean[this.torrentHandler.torrentInfo.piece_hashes.length];
		pulse = false;
	}

	public boolean connect() {
		// connect the connection to the peer
		try {
			socket = new Socket(ip, port);
			from_peer = new DataInputStream(socket.getInputStream());
			to_peer = new DataOutputStream(socket.getOutputStream());

		} catch (UnknownHostException e) {
			System.err.println(e.getMessage() + "AKA: Your host is gone");
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err.println(e.getMessage());
			return false;
		}
		// handshake with the peer
		if (!handshake()){
			System.err.println("The handshake with: " + ip + " failed.");
			return false;
		}
		
		//alive keeps track of whether this is still a valid torrent
		pulse = true;
		//Start the clock
		keepAlive = new Thread(new KeepAlive());
		keepAlive.start();
		// if both are good return true
		return true;
	}

	//@SuppressWarnings("deprecation")
	public void download() {
		System.out.println("Starting download with peer " + this.ip + " send intrested");
		System.out.println(this.torrentHandler.torrentInfo.piece_hashes.length);
		byte[] m = Message.interested();
		this.sendMessage(m);
		//run();
	/*	try {
			while (this.socket.isConnected()) {
				int len = this.from_peer.readInt();
				//System.out.println("read init!");
				//System.out.println(len);
				if (len > 0) {
					byte id = this.from_peer.readByte();
					//System.out.println("messageid: " + ((int) (id)));
					switch (id) {
					case (byte) 0: // choke
						this.peer_choking = true;
						System.out.println("choked");
						break;
					case (byte) 1: // unchoke
						this.peer_choking = false;
						System.out.println("un choked");
						this.torrentHandler.requestNewBlock(this);
						break;
					case (byte) 2: // interested
						this.peer_interested = true;
						System.out.println("interested");
						break;
					case (byte) 3: // not interested
						this.peer_interested = false;
						System.out.println("not interested");
						break;
					case (byte) 4: // have
						int piece = this.from_peer.readInt();
						System.out.println("have " + piece);
						if (piece >= 0 && piece < bitfield.length)
							bitfield[piece] = true;
						break;
					case (byte) 5: // bit field
						byte[] payload = new byte[len - 1];
						this.from_peer.readFully(payload);
						boolean[] bitfield = new boolean[payload.length * 8];
						int boolIndex = 0;
						for (int byteIndex = 0; byteIndex < payload.length; ++byteIndex) {
							for (int bitIndex = 7; bitIndex >= 0; --bitIndex) {
								if (boolIndex >= payload.length * 8) {
									// Bad to return within a loop, but it's the easiest way
									continue;
								}

								bitfield[boolIndex++] = (payload[byteIndex] >> bitIndex & 0x01) == 1 ? true: false;
							}
						}
						this.bitfield = bitfield;
						//System.out.println("bitfield recieved");
						//System.out.println("building request");
						this.torrentHandler.requestNewBlock(this);
						break;
					case (byte) 6: // request
						//System.out.println("request");
						int rindex = this.from_peer.readInt();
						int rbegin = this.from_peer.readInt();
						int rlength = this.from_peer.readInt();
						// Not required for phase 1, but we need to clear the
						byte[] rdata = this.torrentHandler.getBlockData(rindex, rbegin, rlength);
						this.sendMessage(Message.pieceBuilder(rindex,rbegin,rdata));
						break;
					case (byte) 7: // piece
						int payloadLen = len - 9;
						//System.out.println("loading ("+payloadLen+")...");
						int bindex = this.from_peer.readInt();
						int boffset = this.from_peer.readInt();
						byte[] data = new byte[payloadLen];
						for(int i = 0; i < payloadLen; i++){
							//System.out.println(i);
							data[i] = this.from_peer.readByte();
						}
						System.out.println("piece " + bindex + "-" + boffset);
						Block b = new Block(bindex, boffset, data);
						this.torrentHandler.saveBlock(b, this);
						this.torrentHandler.requestNewBlock(this);
						break;
					case (byte) 8: // cancel
						System.out.println("cancel");
						int cindex = this.from_peer.readInt();
						int cbegin = this.from_peer.readInt();
						int clength = this.from_peer.readInt();
						// Not required for phase 1, but we need to clear the
						// buffer
						break;
					default:
						System.out.println("message invalid");
						break;
					}
				}else{
					if(len == 0)
						System.out.println("keep alive");
					else
						System.out.println("message length: " + len);
				}
			}
			
			//socket.close();
		} catch (IOException e) {
			System.err.println("Peer " + this.ip + ":" + this.port + " disconnected. " + e.getMessage());
			
		}*/
	}

	/**
	 * Perform the Handshake with the Peer The method creates the handshake to
	 * be sent, then Receives a response from the peer, validates it and
	 * returns.
	 * */
	public boolean handshake() {
		byte[] shake = Message.handshake(this.clientID, this.torrentHandler.torrentInfo.info_hash.array());
		byte[] responce;
		if (sendMessage(shake))
			responce = recieveMessage();
		else
			return false;

		if (responce == null)
			return false;

		return Message.validateHandshake(responce, shake, id);
	}

	/**
	 * Sends a magical Columbidae(a bird) to deliver our message to the connected Peer
	 * If the bird is shot out of flight, print to the error stream
	 * 
	 * @param message
	 *            The message to be sent via pigeon
	 */
	public boolean sendMessage(byte[] message) {
		try {
			to_peer.write(message);
			lastMessageSent = System.nanoTime();
			to_peer.flush();
		} catch (IOException e) {
			System.err.println("Error sending message: " + message.toString() +
					"/nto peer located at: " + ip);
			return false;
		}
		return true;
	}
	/**
	 * Receives a magical Columbidae(a pigeon) who brings messages from the connected Peer
	 * If the bird has a blank message... we don't really care. 
	 * 
	 * @return the complete message brought by the pigeon
	 */
	public byte[] recieveMessage() {
		byte[] responce = new byte[68];
		for(int i=0;i<responce.length;i++)
			responce[i] = 0;
		try {
			this.from_peer.readFully(responce);
		} catch (IOException e) {
			// http://docs.oracle.com/javase/tutorial/essential/io/datastreams.html
			// We will do nothing with this! The EOF is how it knows to stop
		}
		return responce;

	}
	
	//-----------------Incoming connections
	
	/**
	 * This connect method handles sockets that already have a
	 * @param boob -> useless
	 * @return
	 * 	true if the handshake succeeds
	 *  false if the handshake fails
	 */
	boolean connect(char boob){
		try {
			from_peer = new DataInputStream(socket.getInputStream());
			to_peer = new DataOutputStream(socket.getOutputStream());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err.println(e.getMessage());
			return false;
		}
		// handshake with the peer
		if (!handshake(boob)){
			System.err.println("The handshake with: " + ip + " failed.");
			return false;
		}
		//alive keeps track of whether this is still a valid peer
		pulse = true;
		//Start the clock
		keepAlive = new Thread(new KeepAlive());
		keepAlive.start();
		// if both are good return true
		return true;
	}
	
	/**
	 * This version of handshake performs a handshake where a peer is contacting us first
	 * 	It receives a handshake, then validates it aginst ours before returning our handshake
	 * 	to the requester
	 * 
	 * @param boob -> useless
	 * 
	 * @return
	 * 	true if the handshake is valid and successful
	 * 	false if the received handshake is bad
	 * 
	 * The method also extracts the peerID of the requesting peer and saves it
	 */
	boolean handshake(char boob){
		byte[] shake = Message.handshake(clientID, torrentHandler.torrentInfo.info_hash.array());
		byte[] responce = recieveMessage();
		
		//If the response is not of the wrong length
		if(responce.length== 68){
			//Extract the peerID from the response
			for(int i=0;i<20;i++){
				id[i] = responce[48+i];
			}
			//check that the handshake is good, then send our handshake to the peer
			if(Message.validateHandshake(responce, shake, id)){
				sendMessage(shake);
				return true;
			}
		}
		//else, if that stuff fails. . .
		sendMessage(new byte[68]);
		return false;
	}

	public String getAddress() {
		return this.ip + ":" + this.port;
	}

	@Override
	/**
	 * Begin downloading from the Peer
	 */
	public void run() {
		while(pulse)	
			interpertMessage();
		
	}
	

	private void interpertMessage() {
		try {
			//while (this.socket.isConnected()) {
				int len = from_peer.readInt();
				//System.out.println("read init!");
				//System.out.println(len);
				if (len > 0) {
					byte id = this.from_peer.readByte();
					//System.out.println("messageid: " + ((int) (id)));
					switch (id) {
					case (byte) 0: // choke
						this.peer_choking = true;
						System.out.println("choked");
						break;
					case (byte) 1: // unchoke
						this.peer_choking = false;
						System.out.println("un choked");
						this.torrentHandler.requestNewBlock(this);
						break;
					case (byte) 2: // interested
						this.peer_interested = true;
						System.out.println("interested");
						break;
					case (byte) 3: // not interested
						this.peer_interested = false;
						System.out.println("not interested");
						break;
					case (byte) 4: // have
						int piece = this.from_peer.readInt();
						System.out.println("have " + piece);
						if (piece >= 0 && piece < bitfield.length)
							bitfield[piece] = true;
						break;
					case (byte) 5: // bit field
						byte[] payload = new byte[len - 1];
						this.from_peer.readFully(payload);
						boolean[] bitfield = new boolean[payload.length * 8];
						int boolIndex = 0;
						for (int byteIndex = 0; byteIndex < payload.length; ++byteIndex) {
							for (int bitIndex = 7; bitIndex >= 0; --bitIndex) {
								if (boolIndex >= payload.length * 8) {
									// Bad to return within a loop, but it's the easiest way
									continue;
								}

								bitfield[boolIndex++] = (payload[byteIndex] >> bitIndex & 0x01) == 1 ? true: false;
							}
						}
						this.bitfield = bitfield;
						//System.out.println("bitfield recieved");
						//System.out.println("building request");
						this.torrentHandler.requestNewBlock(this);
						break;
					case (byte) 6: // request
						//System.out.println("request");
						int rindex = this.from_peer.readInt();
						int rbegin = this.from_peer.readInt();
						int rlength = this.from_peer.readInt();
						// Not required for phase 1, but we need to clear the
						byte[] rdata = this.torrentHandler.getBlockData(rindex, rbegin, rlength);
						this.sendMessage(Message.pieceBuilder(rindex,rbegin,rdata));
						break;
					case (byte) 7: // piece
						int payloadLen = len - 9;
						//System.out.println("loading ("+payloadLen+")...");
						int bindex = this.from_peer.readInt();
						int boffset = this.from_peer.readInt();
						byte[] data = new byte[payloadLen];
						for(int i = 0; i < payloadLen; i++){
							//System.out.println(i);
							data[i] = this.from_peer.readByte();
						}
						System.out.println("piece " + bindex + "-" + boffset);
						Block b = new Block(bindex, boffset, data);
						this.torrentHandler.saveBlock(b, this);
						this.torrentHandler.requestNewBlock(this);
						break;
					case (byte) 8: // cancel
						System.out.println("cancel");
						int cindex = this.from_peer.readInt();
						int cbegin = this.from_peer.readInt();
						int clength = this.from_peer.readInt();
						// Not required for phase 1, but we need to clear the
						// buffer
						break;
					default:
						System.out.println("message invalid");
						break;
					}
				}else{
					if(len == 0)
						System.out.println("keep alive");
					else
						System.out.println("message length: " + len);
				}
			//}
				//socket.close();
			}catch (IOException e) {
				System.err.println("Peer " + this.ip + ":" + this.port + " disconnected. " + e.getMessage());
				pulse = false;
			}
		
	}

	public boolean[] getBitfield() {
		return this.bitfield;
	}
	/**
	 * Checks to see if the input object is actually the same peer!!!
	 * @param Object o instance of Peer
	 * @return if this object is the same peer or not
	 */
	public boolean equals(Object o){
		if(o instanceof Peer){
			return ip.equals(((Peer) o).ip)?true:false; //Love the Trinary Operations
		}
		return false;
	}
	
	/**
	 * End the communication with the Peer*/
	public void disconnect(){
		//disconnect our connections
		pulse = false;
		try {
			to_peer.flush();
			to_peer.close();
			from_peer.close();
			socket.close();
		} catch (IOException e) {
			System.err.println("Closing peer:" + ip + " has somehow failed."+
								" It was probably already dead :( ");
		}
		
	}
	/**
	 * KeepAlive runs a clock that sends out a keep alive message every 2 minutes*/
	class KeepAlive implements Runnable{

		@Override
		public void run() {
			while(pulse){
				//12 seconds * nanosecond to second -> 10^9
				if((System.nanoTime() - lastMessageSent)  > 120*Math.pow(10, 9)){
					sendMessage(new byte[]{});
					System.out.println("Sending " + ip + " a keep alive message");
				}
			}
			
		}
		
	}
	public boolean hasPulse() {
		// TODO Auto-generated method stub
		return pulse;
	}

}
