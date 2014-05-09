import java.net.*;
import java.io.*;

public class HeartbeatWorker implements Runnable{

	private MulticastSocket socket;
	private InetAddress group;
	private final String groupAddress;
	private final int PULSE;
	private final int PORT;

	private int expectedServers;
	private int offset;

	private final byte PING_BYTE = 6;
	private final byte RESP_BYTE = 7;
	private final int PING_PACKET_SIZE = 2;

	public HeartbeatWorker(String groupIP, int port, int pulse, int exp){
		this.groupAddress = groupIP;
		this.PORT = port;
		this.PULSE = pulse;
		this.expectedServers = exp;
	}

	public void run(){

	}

	private void init() throws UnknownHostException, IOException{
		//Initialize socket
		socket = new MulticastSocket(PORT);
		//Get the group address
		group = InetAddress.getByName(groupAddress);
		//Join the multicast group
		socket.joinGroup(group);
	}

	private void listen() throws IOException{
		System.out.println("Listening for pulses on "+groupAddress+":"+PORT);
		//Get new packet
		DatagramPacket packet = newPingPacket(0,false);
		try{
			//Wait for pulses from other servers
			socket.receive(packet);

		} catch(SocketTimeoutException timeout){
			//Timed out without a pulse, send a pulse
			sendPulses();
		}
	}

	private void sendPulses(){
		boolean[] aliveServers = new boolean[expectedServers+offset];
		//Ping all the servers	
		for(int i = 0; i < expectedServers+offset; i++){
			//Make the new packet
			DatagramPacket pack = newPingPacket(i,true);
			//Send the ping
			socket.send(pack);
		}

		try{
			for(;;){
				//Make the receiving packet
				DatagramPacket pack = newPingPacket(0,false);
				//Wait on packets
				socket.receive();
				//Check that it's a response  packet
				if(pack.getData()[1] != RESPONSE_BYTE) continue;
				//Get server number from ping packet
				int servNum = AuthServer.byteToInt(pack.getData()[1]);
				//Check to make sure it's not a weird server number
				if(servNum >= 0 && servNum <= expectedServers){
					//Register the server as alive
					aliveServers[servNum] = true;
					//
				}
			}
		}
	}

	public DatagramPacket newPingPacket(int serverNum, boolean sending){
		DatagramPacket pack;
		//Construct buffer, same for sending and receiving
		byte[] pingBytes = new byte[PING_PACKET_SIZE];

		//Construct appropriate packet, sending or receiving
		if(sending){ //Sending packet, opCode and serverNumber
			pingBytes[0] = PING_BYTE;
			pingBytes[1] = (byte)serverNum;
			pack = new DatagramPacket(pingBytes,pingBytes.length,group,PORT);
		} else{
			pack = new DatagramPacket(pingBytes,pingBytes.length);
		}
		return pack;
	}


}
