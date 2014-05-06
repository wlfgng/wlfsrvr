import java.io.*;
import java.net.*;


public class AuthServer{

	private final int CENSUS_TIMEOUT = 1000; //Census timeout in milliseconds
	private final int PING_TIMEOUT = 500;

	private final int MAX_SERVERS = 255; //Max servers allowed in a group

	//OpCodes for chatter packets
	private final byte INTRO_BYTE = 1;
	private final byte CENSUS_BYTE = 2;
	private final byte DEPART_BYTE = 3;

	private final byte PING_BYTE = 5;


	//Sizes for chatter packets
	private final int CENSUS_PACKET_SIZE = 2; // 1 byte OpCode + 1 byte ServerNum
	private final int PING_PACKET_SIZE = 2; //1 byte OpCode + 1 byte ServerNum
	private final int INTRO_PACKET_SIZE = 1;
	private final int DEPART_PACKET_SIZE = 2;
	private final int MAX_CHATTER_SIZE = 5;


	//REMOVE WHEN DONE TESTING
	private final byte CONCESSION = 2;

	/*
	NOT FINAL VALUES, REMOVE WHEN DONE TESTING
	*/
	private final int MAX_KEY_SIZE = 1024;
	private final int PACKET_SIZE = 1 + 32 + MAX_KEY_SIZE;

	//Server number for chatter
	private byte serverNumber;
	private int serverCount = 3;

	private volatile boolean working;

	private final int PORT;
	private final String GROUP_NAME;
	private InetAddress group;
	private MulticastSocket socket;

	private final int CH_PORT;
	private final String CH_GROUP_NAME;
	private InetAddress chGroup;
	private MulticastSocket chSocket;

	public AuthServer(int p, String g){
		PORT = p;
		GROUP_NAME = g;

		//TEMPORARY, CHANGE SOON
		CH_PORT = 22699;
		CH_GROUP_NAME = "228.5.6.7";

	}

	public void setServerNumber(byte num){
		this.serverNumber=num;
	}

	public void initServer() throws UnknownHostException, IOException{
		//Initialize socket
		socket = new MulticastSocket(PORT);
		//Get group address
		group = InetAddress.getByName(GROUP_NAME);
		//Join the multicast group
		socket.joinGroup(group);

		//Initialize chatter socket
		chSocket = new MulticastSocket(CH_PORT);
		//Get chatter group address
		chGroup = InetAddress.getByName(CH_GROUP_NAME);
		//Join the chatter multicast group
		chSocket.joinGroup(chGroup);
	}

	public void introduce() throws IOException{
		//Construct intro packet
		DatagramPacket intro = newIntroPacket();

		//send introduction
		chSocket.send(intro);
	}

	public void takeCensus() throws SocketException, IOException{
		//Set max time to wait in between greetings
		chSocket.setSoTimeout(CENSUS_TIMEOUT);

		//Array for census of servers that respond
		boolean[] aliveServers = new boolean[MAX_SERVERS];

		//Number of servers reported in
		int numReports = 0;

		//Wait for reports from servers until timeout occurs
		try{
			for(;;){
				//Construct packet
				DatagramPacket cPacket = newCensusPacket(false);
				
				//Receive on chatter socket
				chSocket.receive(cPacket);

				//Verify OpCode
				if(cPacket.getData()[0] != CENSUS_BYTE) continue;

				//Record the server alive
				aliveServers[byteToInt(cPacket.getData()[1])] = true;
				numReports++;
			}
		} catch(SocketTimeoutException t){ //Timeout reached, census gather is over
			System.out.println("Census timeout reached, finished waiting on reports.");
		}

		//Reset socket timeout
		chSocket.setSoTimeout(0);

		//Get next available spot, either end of list 
		int nextSpot = nextAvailableSpot(aliveServers);
		boolean spotAvailable = false;

		//Check bounds, no more than 255 servers allowed
		if(nextSpot == -1){
			sendDepartureMessage();
			throw new IOException("Cluster is at max allowed servers, couldn't register server.");
		}

		//If the spot returned wasnt at the end, a server may be dead
		while(nextSpot <= numReports && !spotAvailable){
			//Ping the potentially dead server
			spotAvailable = !pingServer(nextSpot);

			//If the spot isn't available 
			if(!spotAvailable){
				//Server is actually alive
				aliveServers[nextSpot] = true; 
				//Get next available server
				nextSpot = nextAvailableSpot(aliveServers);
			}
		}

		//Successfully added to sever cluster, register your number
		serverNumber = (byte)nextSpot;

		//Record how many servers exists
		serverCount = numReports;

		if(serverNumber <= serverCount-1)
			sendDepartureMessage();


	}

	public boolean pingServer(int serverNum, int ttw) throws SocketException, IOException{
		//Create packet
		DatagramPacket packet = newPingPacket(serverNum,true);

		//Send ping
		chSocket.send(packet);

		//Create the receive packet
		packet = newPingPacket(serverNum,false);

		//Set socket timeout
		chSocket.setSoTimeout(ttw);

		boolean response;

		try{ //Wait for server to respond
			for(;;){
				chSocket.receive(packet);
				//Check packet header
				if(packet.getData()[0] == PING_BYTE)
					if(packet.getData()[1] == (byte)serverNum)
						response = true;
			}

		} catch(SocketTimeoutException ex){
			response =  false;
		}

		//Reset socket timeout
		chSocket.setSoTimeout(0);

		return response;
	}

	

	public boolean pingServer(int serverNum) throws SocketException, IOException{
		return pingServer(serverNum,PING_TIMEOUT);
	}

	public void sendDepartureMessage() throws IOException{
		//New depart pack
		DatagramPacket departPack = newDeparturePacket();

		//Send depart packet
		chSocket.send(departPack);

	}

	public void run() throws SocketException, UnknownHostException, IOException {

		//Initialize server
		initServer();

		//Introduce to the group
		introduce();

		//Wait for census information
		takeCensus();

		//Print server information
		System.out.print("Server: " + serverNumber + " ");
		System.out.print("listening to chatter on " + CH_GROUP_NAME + " ");
		System.out.print(" on port " + CH_PORT + "\n");

		//Print group information
		System.out.println("Current server count: " + serverCount);

		listen();

	}

	private void listen(){

		try{
			for(;;){
				//Construct new packet
				DatagramPacket packet = newChatterPacket();
				//receive packet
				chSocket.receive(packet);
				//Handle the packet
				handlePacket(packet);
			}
		} catch(IOException ioe){
			ioe.printStackTrace();
			System.exit(-1);
		} 

	}

	private void handlePacket(DatagramPacket pack) throws IOException{
		//Get the data from the packet
		byte[] data = pack.getData();
		//Get the OpCode
		byte opCode = data[0];

		//Handle the packet appropriately 
		switch(opCode){
			case INTRO_BYTE: //Introduction received
				handleIntro();	//Handle the intro packet
				break;
			case DEPART_BYTE:
				handleDepart(pack);
				break;
			case CENSUS_BYTE:
				handleCensus(pack);
				break;
		}

	}

	private void handleIntro() throws IOException{
		//Increment server count
		serverCount++;

		System.out.println("New server introduced, server count: "+serverCount);

		//Send census information
		sendCensusInfo();
	}

	private void handleDepart(DatagramPacket p) throws IOException{
		//Decrement server count
		serverCount--;

		System.out.println("Server at "+p.getAddress()+" departed, server count: "+serverCount);
	}

	private void handleCensus(DatagramPacket p) throws IOException{
		System.out.println("Census from " + p.getData()[1]);
	}

	private void sendCensusInfo() throws IOException{
		//Construct packet to send census info
		DatagramPacket sendPacket = newCensusPacket(true);
		//send the packet
		chSocket.send(sendPacket);

		System.out.println("Census packet sent.");

	}


	public DatagramPacket newPingPacket(int serverNum, boolean sending){
		DatagramPacket pack;
		//Construct buffer, same for sending and receiving
		byte[] pingBytes = new byte[PING_PACKET_SIZE];

		//Construct appropriate packet, sending or receiving
		if(sending){ //Sending packet, opCode and serverNumber
			pingBytes[0] = PING_BYTE;
			pingBytes[1] = (byte)serverNum;
			pack = new DatagramPacket(pingBytes,pingBytes.length,chGroup,CH_PORT);
		} else{
			pack = new DatagramPacket(pingBytes,pingBytes.length);
		}
		return pack;
	}

	public DatagramPacket newIntroPacket(){
		DatagramPacket pack;
		//Construct buffer
		byte[] introBytes = new byte[INTRO_PACKET_SIZE];
		//Fill in OpCode
		introBytes[0] = INTRO_BYTE;
		//Create packet
		pack = new DatagramPacket(introBytes,introBytes.length,chGroup,CH_PORT);

		return pack;
	}

	public DatagramPacket newCensusPacket(boolean sending){
		DatagramPacket pack;
		//Construct buffer
		byte[] cBytes = new byte[CENSUS_PACKET_SIZE];

		if(sending){ //Sending packet, add OpCode and your server number
			cBytes[0] = CENSUS_BYTE;
			cBytes[1] = (byte)serverNumber;
			//Construct packet
			pack = new DatagramPacket(cBytes,cBytes.length,chGroup,CH_PORT);
		} else{ //Receiving packet
			pack = new DatagramPacket(cBytes,cBytes.length);
		}
		return pack;
	}

	public DatagramPacket newDeparturePacket(){
		DatagramPacket pack;
		//Construct buffer
		byte[] dBytes = new byte[DEPART_PACKET_SIZE];
		//OpCode
		dBytes[0] = DEPART_BYTE;
		//Make
		pack = new DatagramPacket(dBytes,dBytes.length,chGroup,CH_PORT);

		return pack;
	}

	public DatagramPacket newChatterPacket(){
		DatagramPacket pack;
		//Buffer allows for any chatter packet
		byte[] chatBytes = new byte[MAX_CHATTER_SIZE];
		//Construct packet
		pack = new DatagramPacket(chatBytes,chatBytes.length);

		return pack;

	}

	// public void runServer(){

	// 	try{

	// 		socket = new MulticastSocket(PORT);
	// 		group = InetAddress.getByName(GROUP_NAME);

	// 		//Join the multicast group
	// 		socket.joinGroup(group);

	// 		for(;;){

	// 			System.out.println("Server " + SERVER_NUM + " listening on " + group + "...");

	// 			byte[] buffer = new byte[PACKET_SIZE];
	// 			DatagramPacket packet = new DatagramPacket(buffer,buffer.length);

	// 			//Listen on socket
	// 			socket.receive(packet);

	// 			System.out.println(new String(packet.getData()));

	// 			System.out.println(packet.getAddress());

	// 			InetAddress a = packet.getAddress();

	// 			int p = packet.getPort();

	// 			buffer[0] = SERVER_NUM;

	// 			packet = new DatagramPacket(buffer,packet.getLength(),group,PORT);

	// 			socket.send(packet);

	// 			buffer = new byte[PACKET_SIZE];

	// 			packet = new DatagramPacket(buffer,buffer.length);

	// 			int numSentinels = 0;

	// 			for(;;){
	// 				//Listen for incoming server numbers
	// 				socket.receive(packet);
						
	// 				if(packet.getData()[0] > SERVER_NUM){ //If a higher priority server exists, you are not the leader
	// 					//Send a message of concession
	// 					packet.getData()[0] = CONCESSION;
	// 					packet = new DatagramPacket(packet.getData(),packet.getData().length,group,PORT);
	// 					socket.send(packet);

	// 					//Temporary
	// 					System.out.println("Server with higher priority got job");
	// 					System.exit(0); 
	// 				} else if(packet.getData()[0] == CONCESSION){ //Received a message of concession
	// 					System.out.println("Concession msg " + (++numSentinels) + " received");
	// 					if(numSentinels == SERVERS-1){
	// 						System.out.println("Every other server has conceded, I am the leader.");
	// 						break;
	// 					}

	// 				}
	// 				else{ //Message is of lower priority, ignore?
	// 					System.out.println(packet.getData()[0]);
	// 					System.out.println("Lower priority, ignoring for the time being");
	// 				}
	// 			}	
	// 		}

	// 	} catch(IOException ioe){
	// 		ioe.printStackTrace();
	// 	}
	// }



	//Converts byte to unsigned byte equivalent
	//	Used to change range of bytes
	//	from signed: [-128,127] to unsigned: [0,255]
	private int byteToInt(byte b){
		return b & 0xFF;
	}

	//Returns next available server number
	private int nextAvailableSpot(boolean[] bArray){
		for(int i = 0; i < bArray.length; i++)
			if(bArray[i]==false)
				return i;
		return -1;
	}

	//Main method to test functionality
	public static void main(String[] args){
		String multicastAddress = "228.5.6.7";
		AuthServer as = new AuthServer(2699,multicastAddress);
		try{
			as.run();
		} catch(IOException ioe){
			ioe.printStackTrace();
		}
	}

}