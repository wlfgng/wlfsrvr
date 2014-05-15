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

			startConnection(input);

			System.out.print("Input String to send: ");

			input = scan.nextLine();


		}
	}

	public static void startConnection(String type) throws IOException{

		ServerSocket socket = new ServerSocket(22710);

		Socket client = socket.accept();

		System.out.println("Connection");

		//Get input and output streams
		ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
		ObjectInputStream in = new ObjectInputStream(client.getInputStream());

		String msg = "";
		Request req;

		if("add".equalsIgnoreCase(type))
			req = new Request("asdf","clt",ReqType.ADD);
		else if("remove".equalsIgnoreCase(type))
			req = new Request("asdf","clt",ReqType.REMOVE);
		else if("update".equalsIgnoreCase(type))
			req = new Request("asdf","clt",ReqType.UPDATE);
		else if("get".equalsIgnoreCase(type))
			req = new Request("asdf","clt",ReqType.GET);
		else
			req = new Request("asdf","clt",ReqType.GETALL);

		
		try{
			System.out.println("Reading in object");
			msg = (String)in.readObject();
		} catch(ClassNotFoundException cnf){
			cnf.printStackTrace();
		}

		out.writeObject(req);

		System.out.println("DONE WRITING");

		System.out.println(msg);
	}
}
