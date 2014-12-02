import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Scanner;

import GivenTools.BencodingException;
import GivenTools.TorrentInfo;

public class RUBTClient {

	/**THe port that our server will connect over*/
	final int SERVER_PORT = 5000;
	
	Scanner input;
	
	File file;
	String tfile;
	String sfile;
	String flag;
	long fsize;
	byte[] tbytes;
	
	InputStream fstream;
	
	TorrentInfo torInfo;
	TorrentHandler torrentHandler;
	Tracker tracker;
	PeerManager peerManager;
	
	public RUBTClient(String[] args){
		// the following is a check to make sure the command line arguments were
		// stored correctly
		input = new Scanner(System.in);
		
		tfile = args[0];
		sfile = args[1];
		
		flag = "";
		if(args.length == 3){
			if(args[2].equals("-i")){
				System.out.print("Input IP: ");
				flag = input.next();
			}
		}
		file = new File(tfile);
		fsize = -1;
		
		tbytes = null;
		
		try
		{
			fstream = new FileInputStream(file);
			fsize = file.length();

			// Initialize the byte array for the file's data
			tbytes = new byte[(int) fsize];

			int point = 0;
			int done = 0;

			// Read from the file
			while (point < tbytes.length
					&& (done = fstream.read(tbytes, point,
							tbytes.length - point)) >= 0)
			{
				point += done;
			}

			fstream.close();

			torInfo = new TorrentInfo(tbytes);
			System.out.println("Init tracker...");
			
			torrentHandler = new TorrentHandler(torInfo,sfile);
			tracker = new Tracker(torInfo, torrentHandler, SERVER_PORT);
			
			peerManager = new PeerManager(SERVER_PORT, tracker, flag);
		
			Thread hajime = new Thread(new UserInput());
			hajime.start();
			
		} catch (FileNotFoundException e)
		{
			System.err.println(e.getMessage());
			return;
		} catch (IOException e)
		{
			System.err.println(e.getMessage());
			return;
		} catch (BencodingException e) {
			System.err.println(e.getMessage());
			return;
		}
	}
	
	//This function begins the download
	private void download() {
		int count = 0;
			while(!peerManager.downloading && count < 50){
				peerManager.download();
				count++;
			}
		if(count>=50)
			System.out.println("Coundn't start download");
	}
	
	//This function makes us Exit
	private void gracefulExit() {
			System.out.println("EXITING");
			peerManager.disconnect();
			System.exit(0);
			//disconnect from the peers
			//save stuff
			//pieceout like a boss
	}
	

	/*UserInput is used to control the user control throughout the program*/
	class UserInput implements Runnable{
		Scanner input;		
		public UserInput(){
			input = new Scanner(System.in);
		}

		public void run() {
			while(true){
				System.out.println("input q to quit, input d to display text");
				String c = input.next();
				c = c.toLowerCase();
				switch(c){
				//case "d": download();break;
				case "q": gracefulExit();break;
				case "t": System.out.println("I like Scarves");break;
				}
				
				
			}
			
		}
	}
	
	
	
	
	
	
	
	
	
	public static void main(String[] args) {

		if (!(args.length == 2 || args.length == 3)) {
			System.out.println("THERE WAS AN ERROR WITH THE INPUTS");
			return;
		}
		new RUBTClient(args);
	}

}