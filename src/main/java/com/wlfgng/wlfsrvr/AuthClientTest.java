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
		String[] split = type.split(",");
		String ty = split[0];
		String tag = "";
		String un = "";
		String pw = "";
		if(split.length > 1)
			tag = split[1];
		if(split.length > 2)
			un = split[2];
		if(split.length > 3)
			pw = split[3];

		if("add".equalsIgnoreCase(ty))
			req = new Request(tag,"testPckName",un,pw,ReqType.ADD);
		else if("remove".equalsIgnoreCase(ty))
			req = new Request(tag,"testPckName",un,pw,ReqType.REMOVE);
		else if("update".equalsIgnoreCase(ty))
			req = new Request(tag,"testPckName",un,pw,ReqType.UPDATE);
		else if("get".equalsIgnoreCase(ty))
			req = new Request(tag,"testPckName",un,pw,ReqType.GET);
		else
			req = new Request(tag,"testPckName",un,pw,ReqType.GETALL);

		try{
			System.out.println("Reading in object");
			msg = (String)in.readObject();
			out.writeObject(req);

			System.out.println("DONE WRITING");

			System.out.println(msg);

			Response response = (Response)in.readObject();

			if(response.getType() == RespType.FAILURE)
				System.out.println("FAIL");
			else if(response.getType() == RespType.SUCCESS)
				System.out.println("SUCC");
			else if(response.getType() == RespType.RESULTS){
				System.out.println("RESULTS");
				for(Map.Entry entry : response.getResults().entrySet())
					System.out.println(entry.getKey()+","+entry.getValue());
			}

		} catch(ClassNotFoundException cnf){
			cnf.printStackTrace();
		}
	
		socket.close();
	}
}
