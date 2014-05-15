/* TODO List:

	 Replace all sending of ping packets to sending of heartbeat packets
	 Replace all receiving of ping packets to receiving of response packets

*/

import java.net.MulticastSocket;
import java.net.InetAddress;
import java.net.DatagramPacket;

import java.net.UnknownHostException;
import java.net.SocketTimeoutException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import java.io.IOException;

public class HeartbeatWorker implements Runnable{

	private MulticastSocket socket;
	private InetAddress group;
	private final String groupAddress;
	private final int PULSE_RATE;
	private final int PORT;
	private final int TTW;

	private int expectedServers;
	private final int OFFSET = 3;

	private AtomicBoolean flag;
	private TimeoutManager timeout;

	private final byte HEART_BYTE = 7;
	private final byte RESP_BYTE = 8;
	private final byte UPDATE_BYTE = 9;


	private final int UPDATE_PACKET_SIZE = 2;
	private final int HEART_PACKET_SIZE = 1;
	private final int RESP_PACKET_SIZE = 2;

	public HeartbeatWorker(String groupIP, int port, int pulse, int exp){
		this.groupAddress = groupIP;
		this.PORT = port;
		this.PULSE_RATE = pulse;
		this.TTW = pulse/2;
		//System.out.println(TTW);
		this.expectedServers = exp;
		this.flag = new AtomicBoolean(false);
		this.timeout = new TimeoutManager(PULSE_RATE,flag);
	}

	public void run(){
		try{
			//Init the socket and group
			init();
			//System.out.println("Heartbeat thread running");
			for(;;){
				//Sleep until pulse time
				Thread.sleep(PULSE_RATE);
				//Send pulses and wait on response
				sendPulses();
			}
		} catch(InterruptedException ie){
			ie.printStackTrace();
		} catch(IOException ioe){
			ioe.printStackTrace();
		}
	}

	private void init() throws UnknownHostException, IOException{
		//Initialize socket
		socket = new MulticastSocket(PORT);
		//Get the group address
		group = InetAddress.getByName(groupAddress);
		//Join the multicast group
		socket.joinGroup(group);
	}

	private void sendPulses() throws IOException{
		socket.setSoTimeout(TTW);
		AtomicBoolean flag = new AtomicBoolean(false);
		TimeoutManager timeout = new TimeoutManager(TTW,flag);
		boolean[] aliveServers = new boolean[expectedServers+OFFSET];
		//Ping all the servers	
		for(int i = 0; i < expectedServers+OFFSET; i++){
			//Make the new packet
			DatagramPacket pack = newHeartbeatPacket();
			//Send the heartbeat
			socket.send(pack);
		}

		try{
			for(;;){
				if(flag.get()){
					//System.out.println("Timed out on thread");
					break;
				}
				//Make the receiving packet
				DatagramPacket pack = newResponsePacket(0,false);
				//Wait on packets
				socket.receive(pack);
				//Check that it's a response  packet
				if(pack.getData()[0] != RESP_BYTE) continue;
				//Get server number from response  packet
				int servNum = WlfServer.byteToInt(pack.getData()[1]);
				//Check to make sure it's not a weird server number
				if(servNum >= 0 && servNum <= expectedServers+OFFSET){
					//System.out.println("Server " + servNum + " reported");
					//Register the server as alive
					aliveServers[servNum] = true;
				}
			}
		} catch(SocketTimeoutException ste){
			//System.out.println("Timed out waiting for heartbeat  responses");
			timeout = null;
		}

		//Get the number of servers registered alive
		int numAlive = 0;
		for(int i = 0; i < aliveServers.length; i++){
			if(aliveServers[i])
				numAlive++;
		}
		
		System.out.println("Servers alive: " + numAlive);

		//Make a new packet with the report
		DatagramPacket reportPacket = newServerUpdatePacket(numAlive);

		//Send
		socket.send(reportPacket);

	}

	public DatagramPacket newHeartbeatPacket(){
		DatagramPacket pack;
		byte[] heartBytes = new byte[HEART_PACKET_SIZE];
		heartBytes[0] = HEART_BYTE;
		pack = new DatagramPacket(heartBytes,heartBytes.length,group,PORT);
		return pack;
	}

	public DatagramPacket newServerUpdatePacket(int alive){
		DatagramPacket pack;
		//Construct buffer
		byte[] updateBytes = new byte[UPDATE_PACKET_SIZE];
		
		//Construct the packet
		updateBytes[0] = UPDATE_BYTE;
		updateBytes[1] = (byte)alive;

		pack = new DatagramPacket(updateBytes,updateBytes.length,group,PORT);

		return pack;
	}

	public DatagramPacket newResponsePacket(int num, boolean send){
		DatagramPacket pack;
		byte[] respBytes = new byte[RESP_PACKET_SIZE];

		if(send){ //Sending packets need info
			respBytes[0] = RESP_BYTE;
			respBytes[1] = (byte)num;
			pack = new DatagramPacket(respBytes,respBytes.length,group,PORT);
		} else{ //Receiving packets don't need info
			pack = new DatagramPacket(respBytes,respBytes.length);
		}

		return pack;
	}
}
