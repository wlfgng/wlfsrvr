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
			//Get address of socket
			InetAddress address = InetAddress.getByName(HOST);
			//Initialize socket
			System.out.println("Connecting to "+address+"...");
			socket = new Socket(address,2699);

			//Get input and output streams
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

			out.flush();
			//TODO Change this to sending your key
			String msg = "Hello Message";
			out.writeObject(msg);
			out.flush();

		} catch(SocketTimeoutException timeout){
			System.out.println("Client connection not made...");
		} catch(SocketException se){
			se.printStackTrace();
		} catch(IOException ioe){
			ioe.printStackTrace();
		}	finally {
			try{
				if(socket != null)
					socket.close();
			} catch(IOException ioe){
				System.exit(-1);
			}
		}
	}

}
