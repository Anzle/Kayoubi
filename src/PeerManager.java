/**
 * Robert Williams
  * Rahul Purwah
  * Richard Gerdes
 * **/
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * PeerManager is a class designed to connect to Peers.
 * 	It runs two Phases:
 * 		 one that listens for new incoming connections to upload to
 * 		 one that checks for new available connections to download from
 * 
 * 	
 * */
public class PeerManager {

	ServerSocket server;
	int port;
	Peer aPeer;
	ArrayList<Peer> peerList;
	List<Peer>  unchokedList= new ArrayList<Peer>(); //List of unchoked peers
	List<Peer>  chokedList= new ArrayList<Peer>(); //List of choked peers
	Tracker tracker;
	Thread serverCheck;
	Thread peerCheck;
	Thread peerPulse;
	Thread chokeCheckThread; //this thread is going to check every 30 seconds which peers are chocked and then those peers will be added to the ArrayList ChokedPeerList
	boolean downloading;
	/**The wait interval: set to 2 minutes*/
	final int INTERVAL = 10000;//120000;
	final int wait30 = 30000;//30 seconds for checking peers
	String flag;
	TorrentHandler th;
	final int MAX_PEERS = 10;
	
	
	public PeerManager(int portNumber, Tracker tracker, String flag){
		port = portNumber;
		this.tracker = tracker;
		this.flag = flag;
		downloading = false;
		peerList = new ArrayList<Peer>(MAX_PEERS);
		th = this.tracker.getTorrentHandler();
		th.setPeerManager(this);
		
		if(flag.equals("")){
			peerCheck = new Thread(new PeerListener());
			peerCheck.start();
			
			try {
					server = new ServerSocket(port);
					serverCheck = new Thread(new ServerListener());
					serverCheck.start();
				} catch (IOException e) {
					System.err.println("PeerManager's server has run into an issue and failed to initilize");
				}
			
			//peerPulse checks if a peer is still active (see documentation
					peerPulse = new Thread(new PeerPulse());
					peerPulse.start();
					
			//this thread is used to check to see which threads are choked and which are un choked. it runs every 30 minutes.
					
					chokeCheckThread= new Thread(new ChokeCheck());
					chokeCheckThread.start();
					
				
			
		}
		//They did input a flag
		else{
			Socket s;
			try {
				s = new Socket(flag, port);
			
				Peer p = new Peer(s, th, tracker.getPeerId());
					if(p.connect()){
						add(p);
						System.out.println("Downloading Connection from: " + p.ip);
						p.download();
						
					}	
				} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				System.err.println("There was a bad flag input. You should Exit");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.err.println("There was a bad flag input. You should Exit");
			}
		}
	}
	
	/**@return whether there are any peers downloading*/
	public boolean getDownloading(){return downloading;}
	
	/**@return port number*/
	public int getPort(){return port;}
	
	/**
	*
	 * */
	public void download() {
		if(peerList.isEmpty()){
			//System.err.println("There are no peers to download from.");
			downloading = false;
		}
		else{
			//Extend
			for( Peer p: peerList){
				System.out.println("Downloading from: " + p.ip);
				p.download();
			}
			
			/*{
				Peer p = peerList.get(0);
				System.out.println("Downloading from: " + p.ip);
				downloading = true;
				p.start();
			}*/
			
			downloading = true;
		}
	}
	
	/**
	 * Disconnect all peers from the list
	 */
	public void disconnect(){
		for(Peer p: peerList){
			p.disconnect();
			System.out.println("Disconnected from peer:" + p.ip);
			//remove(p);
		}
		peerList = null;
	}
	
	private void disconnect(Peer p){
		p.disconnect();
		remove(p);
		System.out.println("Disconnected from peer:" + p.ip);
	}
	
	/**
	 * Add our peer to our peer list*/
	private synchronized void add(Peer p){
		//if(p instanceof Peer)
			peerList.add((Peer)p);
			p.start();
			System.out.println("Established connection to peer: " + p.ip);
	}
	
	/**
	 * Check that the peer is contained within our peer list
	 * @param p
	 * @return
	 */
	private synchronized boolean contains(Object p){
		if(p instanceof Peer)
			return peerList.contains((Peer)p);
		return false;
	}
	
	/**
	 * Check to see if the peer is still active/alive
	 * @param p
	 * @return
	 */
	private synchronized boolean checkPulse(Peer p){
		return p.hasPulse()? true:false;
	}
	
	/**
	 * Check the pulse of all peers
	 */
	private synchronized void pulse(){
		for(int i = 0; i < peerList.size();i++){
			Peer p = peerList.get(i);
			if(!checkPulse(p)){//if it has no pulse, it's dead
				disconnect(p);
			}
		}
	}
	
	/**
	 * remove a peer from our Peer List
	 * @param p
	 */
	private synchronized void remove(Peer p){
		peerList.remove(p);
	}
	
	/**
	 * retrieve the list of current peers
	 */
	public synchronized ArrayList<Peer> getPeers(){
		return this.peerList;
	}
	
	/***
	 * 
	 * Private classes that act as threads
	 *
	 */
	
	/*
	 * This thread checks for peers that we want to connect to!
	 */
	private class PeerListener implements Runnable{
		
		//This constructor calls super... just like an implicit one... so it does nothing
		public PeerListener(){super();}
		
		/**
		 * Check the tracker for peers that we can download the file from
		 * 	if a peer is already connected to us, skip that peer */
		public void run(){
			//Loop Forever
			while(true){
				System.out.println("Checking for new peers.");
				ArrayList<Peer> peers = tracker.getPeers();
				
				if(peers == null){
					System.err.println("There are no new peers.");
					try {
						Thread.sleep(INTERVAL);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return;
				}
				
				//System.out.println("num peers is " + peers.size());
				
				for(Peer p : peers){
					//check that we don't add a peer who has been added already
					if(peerList.contains(p)){
						//System.out.println("Peer List contains:" + p);
						continue;
					}

					//for Phase 2, we only connect to these peers
					//else if(p.ip.equals("128.6.171.130") || p.ip.equals("128.6.171.131")){
//made for blacklisting IPs
					
					//else if(p.ip.equals("172.31.116.211")) continue;
//****************************************//						
					
					else{
						if(p.connect()){
							System.out.println("Downloading Connection from: " + p.ip);
							add(p); //This is a synchronized method
							//p.download();
						}
						//else
							//System.out.println("Could not connect to: "+p.ip);
					}
				}
				
				try {
					Thread.sleep(INTERVAL);
				} catch (InterruptedException e) {
					System.err.println("peerListener: Sleep was interupted..."+
							" shouldn't actually matter that this happened");
				}
			}
		}
	}

/*
 * This Peer checks for incomming connections, ie, people trying to download from us	
 */
	private class ServerListener implements Runnable{
		
		public ServerListener(){super();}
		
		/**
		 * This method constantly checks if there is a peer
		 * 	attempting to connect to the server
		 */
		public void run(){
			while(true){
				try {

					System.out.println("Checking for inbound Connections");
					aPeer = new Peer(server.accept(), th, tracker.getPeerId());

					//aPeer.new connect for incoming connects
					if(peerList.contains(aPeer))
						continue;
					if(aPeer.connect('i')){
						System.out.println("Uploading Connection made with:" + aPeer.ip);
						add(aPeer);
						//aPeer.start();
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		/*To prevent memory leakage*/
		private synchronized void add(Object p){
			if(p instanceof Peer)
				peerList.add((Peer)p);
		}
	}


	public void notifyPeersPieceCompleted(int index) {
		byte[] m = Message.haveBuilder(index);
		for(Peer p : this.peerList){
			p.sendMessage(m);
		}
	}
	
	
	/*
	 * This thread checks if our peers are still connected
	 * If not, it tosses them from the list
	 * This was implemented because after a bad peer disconnect
	 * 	the peer remains in the peer list, making us unable to reconnect to
	 *  that peer. 
	 *  
	 *  This will check if a peer is still connected and if not remove it from our peer list (trying to close
	 *  connections to the sockets just incase somethign strange happened)
	 */
	class PeerPulse implements Runnable{
		public void run(){
			while(true){
				pulse();		
								
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					System.err.println("Class PeerPulse sleep failure.");
					//e.printStackTrace();
				}
			}
			
		}
	}

	//this is the class to run the thread for choking and unchoking the specified peers
	
	private class ChokeCheck implements Runnable{                       
		
	        public void run(){
	        	while(true){
	        
		        		if(peerList.size()>1){
		        		
		        		for(int i=0;i<peerList.size();i++){
		        			if(unchokedList.contains(peerList.get(i))==false && peerList.get(i).amChoking()==false){ //this condition is to check for unchoked peer which doesnt already exist in the unchokedlist
		        				unchokedList.add(peerList.get(i));
		        			}
		        			
		        			if(chokedList.contains(peerList.get(i))==false && peerList.get(i).amChoking()==true){ //this condition is to check for unchoked peer which doesnt already exist in the unchokedlist
		        				chokedList.add(peerList.get(i));
		        			}
		        			
		        		}
		        		
		        		Collections.sort(unchokedList, new Comparator<Peer>() { //this sorts the unchokedlist based on download speeds
		        	        public int compare(Peer  p1, Peer p2)
		        	        {
		        	        	if(p1.getDownloadSpeed()==p2.getDownloadSpeed()){
		        	        		return 0;
		        	        	}else if(p1.getDownloadSpeed()>p2.getDownloadSpeed()){
		        	        		return 1;
		        	        	}else{
		        	        		return -1;
		        	        	}
		        	        		
		        	        }
		        	    });
		        		
		        		//this picks the worst unchoked peer and then chokes it
		        		
		        		int tochoke=0;
		        		tochoke=peerList.indexOf(unchokedList.get(0));
		        		peerList.get(tochoke).choke();
		        		unchokedList.remove(0);
		        		
		        		//this randomly picks a choked peer and unchokes it
		        		int randomNum = 0 + (int)(Math.random()*chokedList.size()); 
		        		int tounchoke=peerList.indexOf(chokedList.get(randomNum));
		        		peerList.get(tounchoke).unchoke();
		        		chokedList.remove(randomNum);
		        		
		        		//this makes sure that at any given time there are only 6 unchoked peers
		        		while(unchokedList.size()>6){
			        		tochoke=peerList.indexOf(unchokedList.get(0));
			        		peerList.get(tochoke).choke();
			        		unchokedList.remove(0);
		        		}
		        	
		        	}
	        		
	        		
					try {
						Thread.sleep(wait30);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

	        	}
	        	
	        }

	}
	
}