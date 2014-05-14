import java.io.*;
import java.net.*;
import java.util.*;

//Test class for multicast server/client interaction
public class AuthClientTest{

	public static void main(String[] args) throws IOException{
		String multicastAddress = "228.5.6.7";
		int port = 22699;
		DatagramSocket socket = new DatagramSocket();
		InetAddress address = InetAddress.getByName(multicastAddress);

		Scanner scan = new Scanner(System.in);

		System.out.print("Input String to send: ");

		String input = scan.nextLine();

		Request r = new Request("a","b",ReqType.ADD);

		int c = 0;
		while(!input.equals("exit")){
			c++;	
			byte[] stringBytes = input.getBytes();

			byte[] bufBytes = new byte[5];

			bufBytes[0] = (byte)6;

			byte[] ib = WlfServer.intToByteArray(c);

			System.arraycopy(ib,0,bufBytes,1,ib.length);

			DatagramPacket packet = new DatagramPacket(bufBytes,bufBytes.length,address,port);
			
			System.out.println("Sending packet to: " +packet.getAddress());

			socket.send(packet);

			if(input.equals("send"))
				startConnection();

			System.out.print("Input String to send: ");

			input = scan.nextLine();


		}
	}

	public static void startConnection() throws IOException{
		System.out.println("Connection");

		ServerSocket socket = new ServerSocket(2699);

		Socket client = socket.accept();

		System.out.println("WTF");

		//Get input and output streams
		ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
		ObjectInputStream in = new ObjectInputStream(client.getInputStream());

		System.out.println("WTFFF");
		String msg = "";
		
		try{
			System.out.println("Reading in object");
			msg = (String)in.readObject();
		} catch(ClassNotFoundException cnf){
			cnf.printStackTrace();
		}

		System.out.println(msg);
	}
}
