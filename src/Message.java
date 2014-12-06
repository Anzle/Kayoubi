import java.nio.ByteBuffer;


public class Message {

	public static byte[] handshake(byte[] peerId, byte[]info_hash){
		//Create the handshake
		//Set the first bits equal to "BitTorrent protocol" as specified by BT protocol
				byte[] handshake = new byte[68];
				handshake[0] = 19;
				handshake[1] = 'B';
				handshake[2] = 'i';
				handshake[3] = 't';
				handshake[4] = 'T';
				handshake[5] = 'o';
				handshake[6] = 'r'; 
				handshake[7] = 'r';
				handshake[8] = 'e';
				handshake[9] = 'n'; 
				handshake[10] = 't';
				handshake[11] = ' ';
				handshake[12] = 'p';
				handshake[13] = 'r';
				handshake[14] = 'o';
				handshake[15] = 't';
				handshake[16] = 'o';
				handshake[17] = 'c';
				handshake[18] = 'o';
				handshake[19] = 'l';    

				//Set the next 8 bytes as '0' byte paddings
				for(int i = 0; i < 8; i++){
					handshake[19 + i + 1] = 0;
				}
				//Set the next bytes equal to the SHA-1 from the torrent file
				for(int i = 0; i < 20; i++){
					handshake[28 + i] = info_hash[i];
				}
				//Set the next bytes equal to the PeerID
				for(int i = 0; i < peerId.length; i++){
					handshake[48 + i] = peerId[i];
				}

				return handshake;
	}
	
	/** Check if the handshake message from a peer was a valid message. 
	 * @param peer_id 
	 * @param 
	 * 		The handshake received from a peer
	 * @return
	 * 		True if it is valid, false otherwise
	 * */
	public static boolean validateHandshake(byte[] recieved_handshake, byte[] sent_handshake, byte[] peer_id){
		//Print out the Sent Handshake
		/* System.out.println("Print out the return handshake\n");
		for(int i=0; i<sent_handshake.length;i++){
			System.out.print(sent_handshake[i] + " ");
			if(i==19||i==27||i==47)
				System.out.println("");
		}
		System.out.println("\n");
		
		//Print out the returned Handshake
		 System.out.println("Print out the return handshake\n");
		for(int i=0; i<recieved_handshake.length;i++){
			System.out.print(recieved_handshake[i] + " ");
			if(i==19||i==27||i==47)
				System.out.println("");
		}
		System.out.println("\n");
		*/
		
		//Check if the message is the correct size
		if(recieved_handshake.length != 68 ){
			System.err.println("validateHandshake, length");
			return false;
		}
		//Check if it has the proper heading
		for(int i=0;i<19;i++){
			if(recieved_handshake[i] != sent_handshake[i]){
				//System.err.println("Invalid Handshake: Heading at " + i);
				return false;
			}
		}
		//Check if it has the correct SHA1 hash
		for(int i=0; i<20;i++){
			if(recieved_handshake[28+i] != sent_handshake[28+i]){
				System.err.println("Invalid Handshake: Info Hash at " + i);
				return false;
			}
		}
		//If those check out... peer_id
		for(int i=0;i<20;i++){
			if(recieved_handshake[48+i] != peer_id[i]){
				System.err.println("Invalid Handshake: Peer Id at " + i);
				return false;
			}
		}
		//All checks out good!
		return true;
	}

	public static byte[] blockRequestBuilder(int index, int offset, int blen) {
		ByteBuffer msg = ByteBuffer.allocate(17);
		
		msg.putInt(13);
		msg.put((byte) 6);
		msg.putInt(index);
		msg.putInt(offset);
		msg.putInt(blen);
		
		return msg.array();
	}

	public static byte[] interested() {
		ByteBuffer msg = ByteBuffer.allocate(17);
		
		msg.putInt(1);
		msg.put((byte) 2);
		
		return msg.array();
	}

	public static byte[] pieceBuilder(int index, int offset, byte[] data) {

		ByteBuffer msg = ByteBuffer.allocate(13 + data.length);
		
		msg.putInt(13);
		msg.put((byte) 7);
		msg.putInt(index);
		msg.putInt(offset);
		for(byte b : data)
			msg.put(b);
		
		return msg.array();
	}
	public static byte[] haveBuilder(int index) {

		ByteBuffer msg = ByteBuffer.allocate(9);
		
		msg.putInt(5);
		msg.put((byte) 4);
		msg.putInt(index);
		
		return msg.array();
	}
	
	public static byte[] choke(){
		
		ByteBuffer msg = ByteBuffer.allocate(5);
		
		msg.putInt(1);
		msg.put((byte) 0);

		return msg.array();
	}
	
	public static byte[] unchoke(){
		
		ByteBuffer msg = ByteBuffer.allocate(5);
		
		msg.putInt(1);
		msg.put((byte) 1);

		return msg.array();
	}
}
