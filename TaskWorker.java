import java.io.*;
import java.net.*;

public class TaskWorker implements Runnable{
	private final int PORT;
	private final int TIME_TO_WAIT;

	public TaskWorker(int p, int t){
		this.PORT = p;
		this.TIME_TO_WAIT = t;
	}

	public void run(){
		//Sockets 
		ServerSocket serverSocket = null;
		Socket client = null;

		try{
			//Create the socket
			serverSocket = new ServerSocket(PORT);
			//Set the socket timeout
			serverSocket.setSoTimeout(TIME_TO_WAIT);
			//Wait for the client to connect
			System.out.println("Listening on port "+ PORT);
			client = serverSocket.accept();
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
