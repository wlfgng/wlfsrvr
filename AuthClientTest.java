import java.io.*;
import java.net.*;
import java.util.*;

public class AuthClientTest{

	public static void main(String[] args) throws IOException{

		int port = 8080;
		DatagramSocket socket = new DatagramSocket();
		InetAddress address = InetAddress.getByName("230.0.0.1");

		Scanner scan = new Scanner(System.in);

		System.out.print("Input String to send: ");

		String input = scan.nextLine();

		while(!input.equals("exit")){
			byte[] stringBytes = input.getBytes();

			DatagramPacket packet = new DatagramPacket(stringBytes,stringBytes.length,address,port);
			
			socket.send(packet);

			System.out.print("Input String to send: ");

			input = scan.nextLine();


		}
	}
}