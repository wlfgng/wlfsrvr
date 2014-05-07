import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.net.*;


public class AuthServer{

	private final int CENSUS_TIMEOUT = 1000; //Census timeout in milliseconds
	private final int PING_TIMEOUT = 500;

	private final int MAX_SERVERS = 255; //Max servers allowed in a group

	//OpCodes for chatter packets
	private final byte INTRO_BYTE = 1;
	private final byte CENSUS_BYTE = 2;
	private final byte DEPART_BYTE = 3;
	private final byte CLAIM_BYTE = 4;
	private final byte PING_BYTE = 5;
	private final byte TASK_BYTE = 6;



	//Sizes for chatter packets
	private final int CENSUS_PACKET_SIZE = 2; // 1 byte OpCode + 1 byte ServerNum
	private final int PING_PACKET_SIZE = 2; //1 byte OpCode + 1 byte ServerNum
	private final int INTRO_PACKET_SIZE = 1;
	private final int DEPART_PACKET_SIZE = 2;
	private final int CLAIM_PACKET_SIZE = 18; //1b opCode + 16b claimID + 1b serv num
	private final int TASK_PACKET_SIZE = 17; //1b opCode + 16b userID 


	private final int MAX_CHATTER_SIZE = 5;


	//REMOVE WHEN DONE TESTING
	private final byte CONCESSION = 2;

	//Server number for chatter
	private int  serverNumber;
	private int serverCount = 3;

	private Map<String,Integer> taskMap;

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

		taskMap = new HashMap<String,Integer>();

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
		boolean[] aliveServers = new boolean[MAX_SERVERS-1];

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
			sendDepartMessage();
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
		serverNumber = nextSpot;

		//Record how many servers exists
		serverCount = numReports+1;

		//If you're not the highest server, you took the place of a dead server
		if(serverNumber != serverCount-1){
			//Adjust other servers counts minus one
			System.out.println("Adjusting other servers count " + serverNumber + "|"+byteToInt((byte)serverNumber) );
			sendDepartMessage();
		}


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

	public void sendDepartMessage() throws IOException{
		System.out.println("Sending a depart message");
		//New depart pack
		DatagramPacket departPack = newDepartPacket();

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
			case PING_BYTE:
				handlePing(pack);
				break;
			case CLAIM_BYTE:
				handleClaim(pack);
				break;
			case TASK_BYTE:
				handleTask(pack);
				break;
			default:
				System.out.println("Packet received with wrong OpCode " + opCode + ", unable to handle");
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


	private void handleClaim(DatagramPacket p) throws IOException{
		//Get the claim ID from the packet
		String claimID = new String(p.getData(),1,16);

		//If the ID isn't in the map, put  
		if(!taskMap.containsKey(claimID))
			taskMap.put(claimID,new Integer(0));

		//Get how many concessions (or if you've conceded)
		int conNum = taskMap.get(claimID).intValue();
		if(conNum == -1){
			System.out.println("Already conceded, ignore the packet");
		} else{ //You haven't conceded yet, handle the claim
			//Get the number of the server
			int claimServ = byteToInt(p.getData()[0]);
			if(claimServ > serverNumber){//A higher server is claiming the id, concede
				System.out.println("Conceded " + claimID + " to " + claimServ);
				//Put sentinel value in map
				taskMap.put(claimID,new Integer(-1));
			} else{//You outrank them, the claim message serves as a concession
				System.out.println("Server "+claimServ+" concession received for "+claimID);
				//Increment your number of concessions
				conNum++;
				//Check if you have seen enough concession
				if(conNum == serverCount-1){
					//Update the map
					taskMap.put(claimID,new Integer(conNum));
				} else {
					//You're the winner, handle the task
					//TODO HANDLE THE TASK
				}
			}//end else outrank
		}//end else handle claim
	}

	private void handleTask(DatagramPacket pack) throws  IOException{
		//Get the user id
		String userID = new String(pack.getData(),1,16);
		//Claim the task
		claimTask(userID.trim());
	}


	private void handleDepart(DatagramPacket p) throws IOException{
		if(byteToInt(p.getData()[1]) != serverNumber){
			//Decrement server count
			serverCount--;

			System.out.println("Server " + p.getData()[1] +  "at "+p.getAddress()+" departed, server count: "+serverCount);
		} else System.out.println("Ignored self depart message");
	}

	private void handleCensus(DatagramPacket p) throws IOException{
		System.out.println("Census from " + p.getData()[1]);
	}

	private void handlePing(DatagramPacket p) throws IOException{
		//System.out.println("Stub method for handling pings, do something better here");
		int targetServer = byteToInt(p.getData()[1]);
		if(targetServer == serverNumber){
			respondToPing(targetServer);
		}
	}

	private void claimTask(String uid) throws IOException{
		//Create a claimID for the task
		String claimID = generateClaimID(uid);
		//Make a claim packet
		DatagramPacket claimPack = newClaimPacket(claimID);
		//Send your claim
		chSocket.send(claimPack);

	}

	private String generateClaimID(String userID){
		//TODO MAKE THIS ACTUALLY DO SOMETHING
		return userID;
	}

	private void respondToPing(int target) throws IOException{
		//Make new packet for sending
		DatagramPacket pack = newPingPacket(target,true);
		//Send the packet
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

	public DatagramPacket newDepartPacket(){
		DatagramPacket pack;
		//Construct buffer
		byte[] dBytes = new byte[DEPART_PACKET_SIZE];
		//OpCode
		dBytes[0] = DEPART_BYTE;
		//Server number
		dBytes[1] = (byte)serverNumber;
		//Make
		pack = new DatagramPacket(dBytes,dBytes.length,chGroup,CH_PORT);

		return pack;
	}

	private DatagramPacket newClaimPacket(String id){

		DatagramPacket pack;
		//Buffer
		byte[] claimBytes = new byte[CLAIM_PACKET_SIZE];
		//Opcode
		claimBytes[0] = CLAIM_BYTE;
		//Get string as bytes
		byte[] idBytes = id.getBytes();
		//Copy the bytes to the packet buffer
		System.arraycopy(idBytes,0,claimBytes,1,idBytes.length);
		//Make the packet
		pack = new DatagramPacket(claimBytes,claimBytes.length,chGroup,CH_PORT);

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
