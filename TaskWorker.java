import java.io.*;
import java.net.*;

public class TaskWorker implements Runnable{
	private final int PORT;
	private final int TIME_TO_WAIT;
	private final String HOST;

	public TaskWorker(int p, int t, String h){
		this.PORT = p;
		this.TIME_TO_WAIT = t;
		this.HOST = h;
	}

	public void run(){
		Socket socket  = null;
		try{
			//Get address of client
			InetAddress address = InetAddress.getByName(HOST);
			//Initialize socket
			socket = new Socket(address,PORT);

			//Get input and output streams
			

		} catch(SocketTimeoutException timeout){
			System.out.println("Client connection not made...");
		} catch(SocketException se){
			se.printStackTrace();
		} catch(IOException ioe){
			ioe.printStackTrace();
		}	finally {
			try{
				if(client != null)
					client.close();
				if(serverSocket != null)
					serverSocket.close();
			} catch(IOException ioe){
				System.exit(-1);
			}
		}
	}


}
