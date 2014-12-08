/**
 * Rahul Purwah
 * Richard Gerdes
 * Robert Williams
 * **/

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import GivenTools.Bencoder2;
import GivenTools.BencodingException;
import GivenTools.ToolKit;
import GivenTools.TorrentInfo;

public class Tracker {

	TorrentInfo torrentInfo;
	private final char[] HEXCHARS = "0123456789ABCDEF".toCharArray();
	private String peer_id;
	private byte[] info_hash;
	private TorrentHandler torrentHandler;
	private int serverPort;
	private String ih_str;
	private HashMap peer_info;
	private int minInterval = 120;
	private String event=null;

	public Tracker(TorrentInfo torrentInfo, TorrentHandler torrentHandler, int serverPort) {
		this.torrentInfo = torrentInfo;
		this.torrentHandler = torrentHandler;
		this.serverPort = serverPort;
		
		StringBuilder sb = new StringBuilder();
		Random random = new Random();
		for (int i = 0; i < 20; i++) {
		    char c = this.HEXCHARS[random.nextInt(this.HEXCHARS.length)];
		    sb.append(c);
		}
		this.peer_id = sb.toString();
		/*
		try {
			ByteBuffer bytes = Bencoder2.getInfoBytes(torrentInfo.torrent_file_bytes);
			System.out.println(new String(bytes.array()));
		} catch (BencodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
		
	}
	
	public Tracker(TorrentInfo torrentInfo, TorrentHandler torrentHandler, int serverPort, String event) {
		
		this.torrentInfo = torrentInfo;
		this.torrentHandler = torrentHandler;
		this.serverPort = serverPort;
		
		StringBuilder sb = new StringBuilder();
		Random random = new Random();
		for (int i = 0; i < 20; i++) {
		    char c = this.HEXCHARS[random.nextInt(this.HEXCHARS.length)];
		    sb.append(c);
		}
		this.peer_id = sb.toString();
		
		this.event=event;
	}

	/**
	 * Perform the HTTP Get Request from the Torrent
	 * @return String response
	 * 		The responce from the HTTP Server*/
	private String queryServer() throws IOException {

		ih_str = "";
		byte[] info_hash = this.torrentInfo.info_hash.array();
		for (int i = 0; i < info_hash.length; i++) {
			
				ih_str += "%" + this.HEXCHARS[(info_hash[i] & 0xF0) >>> 4]
						+ this.HEXCHARS[info_hash[i] & 0x0F];
		}

		//System.out.println("info hash: " + ih_str);
		
		/*Save the info hash for later usage as an array of bytes*/
		this.info_hash = this.torrentInfo.info_hash.array();
	

		// String query = "announce?info_hash=" + ih_str + "&peer_id=" + host.getPeerID() + "&port=" + host.getPort() + "&left=" + torinfo.file_length + "&uploaded=0&downloaded=0";

		String query=null;
		
		//this makes sure that event is either started, completed, or stopped
		
		if(this.torrentHandler.getBytesDownloaded()==0){
			this.event="started";
		}else if(this.torrentInfo.file_length==0){
			this.event="completed";
		}else{
			this.event="not done yet";  //this is checked in the next method and eliminated 
		}
		
		
		
		if((this.event.equalsIgnoreCase("started")) || (this.event.equalsIgnoreCase("completed")) || (this.event.equalsIgnoreCase("stopped")) ){
			//does nothing if its one of the strings stated above
		}else{
			this.event=null;
		}
		
		
		if(this.event==null){
			query = "announce?info_hash=" + ih_str + "&peer_id=" + this.peer_id + "&port="+this.serverPort+"&left=" + this.torrentInfo.file_length + "&uploaded="+this.torrentHandler.getBytesUploaded()+ "&downloaded="+this.torrentHandler.getBytesDownloaded();
		}else{
			query = "announce?info_hash=" + ih_str + "&peer_id=" + this.peer_id + "&port="+this.serverPort+"&left=" + this.torrentInfo.file_length + "&uploaded="+this.torrentHandler.getBytesUploaded()+ "&downloaded="+this.torrentHandler.getBytesDownloaded()+ "&event="+this.event;
		}
		
		URL urlobj;
		
		urlobj = new URL(this.torrentInfo.announce_url, query);
		
		//System.out.println(urlobj);
		HttpURLConnection uconnect = (HttpURLConnection) urlobj
				.openConnection();
		uconnect.setRequestMethod("GET");
		
		BufferedReader in = new BufferedReader(new InputStreamReader(
				uconnect.getInputStream()));

		StringBuffer response = new StringBuffer();
		

		String inline = "";
		while ((inline = in.readLine()) != null) {

			response.append(inline);

		}
		//System.out.println(peer_id +"\n" +response.toString());
		return response.toString(); //html response 
	}

	/**
	 * Generate a list of peers that the client can attempt to connect to
	 * @return an Array List of Peers*/
	
	public ArrayList<Peer> getPeers() {
		String response = "";
		try {
			response = queryServer();
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return null;
		}
		HashMap results = null;
		try {
			Object o = Bencoder2.decode(response.getBytes()); //this is done
			
			if (o instanceof HashMap) {
				results = (HashMap) o;
			}
		} catch (BencodingException e) {
			System.err.println(e.getMessage());
			return null;
		}

		if (results == null){
			System.err.println("Results is null");
			return null;
		}

		ByteBuffer i = ByteBuffer.wrap("min interval".getBytes());
		
		Object mini_o = results.get(i);
		if(mini_o != null && mini_o instanceof Integer){
			this.minInterval = (Integer) mini_o;
		}
		
		ByteBuffer b = ByteBuffer.wrap("peers".getBytes());

		ByteBuffer peer_ip_key = ByteBuffer.wrap("ip".getBytes());
		ByteBuffer peer_port_key = ByteBuffer.wrap("port".getBytes());
		ByteBuffer peer_id_key = ByteBuffer.wrap("peer id".getBytes());
		

		Object peer_list_o = results.get(b);

		if (peer_list_o == null){
			System.err.println("peer_list_o is null");
			return null;
		}else if (!(peer_list_o instanceof ArrayList)) {
			System.err.println("No Results");
			return null;
		}

		ArrayList peer_list = (ArrayList) peer_list_o;
		
		ArrayList<Peer> peers = new ArrayList<Peer>();
		for (Object peer_info_o : peer_list) {

			if (!(peer_info_o instanceof HashMap)) {
				continue;
			}

			peer_info = (HashMap) peer_info_o;

			Object port_o = peer_info.get(peer_port_key);
			Object ip_o = peer_info.get(peer_ip_key);
			Object id_o = peer_info.get(peer_id_key);

			if(port_o == null || !(port_o instanceof Integer)){
				System.err.println("Bad port");
				continue;
			}
			if(ip_o == null || !(ip_o instanceof ByteBuffer)){
				System.err.println("Bad ip");
				continue;
			}
			if(id_o == null || !(id_o instanceof ByteBuffer)){
				System.err.println("Bad id");
				continue;
			}
			
			int port = (Integer) port_o;
			String ip = new String(((ByteBuffer) ip_o).array());
			String id = new String(((ByteBuffer) id_o).array());
			
			
			Peer p = new Peer(id, ip, port, torrentHandler, this.peer_id.getBytes());
			peers.add(p);
		}

		return peers;

	}
	
	/** @return the SHA1 Info Hash*/
	public byte[] getInfoHash(){return info_hash;}
	
	/**return a byte[] of the peerId we got*/
	public byte[] getPeerId(){return this.peer_id.getBytes();}
	
	public void client_info(){
		
		
		String one="client info: ";
		String two="port number: "+ this.serverPort;
		String three="downloaded: " + this.torrentHandler.getBytesDownloaded();
		String four="uploaded: " + this.torrentHandler.getBytesUploaded();
		String five= "total: "+this.torrentInfo.file_length;
		
		
		//All of this code must be updated, pulled from above
		//Need to add in the downloaded bytes
		//need to add in the events
		//need the interval, then need to make this into a thread
		//need to make this send out various messages... use switch
		
		
		String query = "announce?info_hash=" + ih_str + "&peer_id=" + this.peer_id + "&port=6881&left=" + this.torrentInfo.file_length + "&uploaded=0&downloaded=0";
		URL urlobj;
		
		try{
		urlobj = new URL(this.torrentInfo.announce_url, query);
		HttpURLConnection uconnect = (HttpURLConnection) urlobj.openConnection();
		uconnect.setDoOutput(true);
		uconnect.setDoInput(true);
		uconnect.setInstanceFollowRedirects(false);
		uconnect.setRequestMethod("GET");
		
		DataOutputStream publish=new DataOutputStream(uconnect.getOutputStream());
		publish.writeBytes(one);
		publish.writeBytes(two);
		publish.writeBytes(three);
		publish.writeBytes(four);
		publish.writeBytes(five);
		
		publish.flush();
		publish.close();
		uconnect.disconnect();
		
		}catch(Exception e){}
		
		// this.torrentInfo.
	    // can implement serverSocket class 69 69
		//implemented somewhere in the code about how much is downloaded 
		//take in an argument for the port number which continues to listen and then implement with a server socket to get the info
	
	
	
	
	}
	
	public TorrentHandler getTorrentHandler(){
		return this.torrentHandler;
	}
	
	/*
	class queryServer_thread implements Runnable{
		
		public void run() {
			
			ByteBuffer peer_interval_key = ByteBuffer.wrap("interval".getBytes());
			
			long interval= peer_info; //need to change this to the interval from URl
			
			while(true){
			
				try {
					queryServer();
					Thread.sleep(interval);
					
				
				
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
		
			}
		}
	
	}
	*/
}
