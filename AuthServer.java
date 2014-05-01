import java.io.*;
import java.net.*;


public class AuthServer{

	private final int MAX_KEY_SIZE = 1024;
	private final int PACKET_SIZE = 1 + 32 + MAX_KEY_SIZE;

	private final byte SENTINEL = -1;
	private int SERVERS = 7;
	private final byte SERVER_NUM;

	private final int PORT;
	private final String GROUP_NAME;
	private InetAddress GROUP;
	private MulticastSocket socket;


	public AuthServer(int p, String g, byte b){
		PORT = p;
		GROUP_NAME = g;
		SERVER_NUM = b;
	}

	public void runServer(){

		try{

			socket = new MulticastSocket(PORT);
			GROUP = InetAddress.getByName(GROUP_NAME);

			//Join the multicast group
			socket.joinGroup(GROUP);

			for(;;){

				System.out.println("Server " + SERVER_NUM + " listening...");

				byte[] buffer = new byte[PACKET_SIZE];
				DatagramPacket packet = new DatagramPacket(buffer,buffer.length);

				//Listen on socket
				socket.receive(packet);

				System.out.println(new String(packet.getData()));

				System.out.println(packet.getAddress());

				InetAddress a = packet.getAddress();

				int p = packet.getPort();

				buffer[0] = SERVER_NUM;

				packet = new DatagramPacket(buffer,packet.getLength(),GROUP,PORT);

				socket.send(packet);

				buffer = new byte[PACKET_SIZE];

				packet = new DatagramPacket(buffer,buffer.length);

				int numSentinels = 0;

				for(;;){
					//Listen for incoming server numbers
					socket.receive(packet);

					System.out.println(packet.getData());
					
					if(packet.getData()[0] > SERVER_NUM){ //If a higher priority server exists, you are not the leader
						//Send a message of concession
						packet.getData()[0] = -1;
						packet = new DatagramPacket(packet.getData(),packet.getData().length,GROUP,PORT);
						socket.send(packet);

						//Temporary
						System.out.println("Server with higher priority got job");
						System.exit(0); 
					} else if(packet.getData()[0] == SENTINEL){ //Received a message of concession
						System.out.println("Concession msg " + (++numSentinels) + " received");
						if(numSentinels == SERVERS-1){
							System.out.println("Every other server has conceded, I am the leader.");
							break;
						}

					}
					else{ //Message is of lower priority, ignore?
						System.out.println(packet.getData()[0]);
						System.out.println("Lower priority, ignoring for the time being");
					}
				}	
			}

		} catch(IOException ioe){
			ioe.printStackTrace();
		}
	}

	//Main method to test functionality
	public static void main(String[] args){
		byte b = Byte.parseByte(args[0]);
		AuthServer as = new AuthServer(8080,"230.0.0.1",b);
		as.runServer();
	}

}