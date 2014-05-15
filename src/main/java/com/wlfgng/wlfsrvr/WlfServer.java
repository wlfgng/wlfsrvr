import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import java.io.IOException;


import java.nio.ByteBuffer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

public class WlfServer{

	//Single Byte OpCodes for packets
	public static final byte INTRO_BYTE = 1;
	public static final byte CENSUS_BYTE = 2;
	public static final byte DEPART_BYTE = 3;
	public static final byte CLAIM_BYTE = 4;
	public static final byte PING_BYTE = 5;
	public static final byte TASK_BYTE = 6;
	public static final byte HEART_BYTE = 7;
	public static final byte RESP_BYTE = 8;
	public static final byte UPDATE_BYTE = 9;

	//Sizes for packets
	public static final int CENSUS_PACKET_SIZE = 2; // 1 byte OpCode + 1 byte ServerNum
	public static final int PING_PACKET_SIZE = 2; //1 byte OpCode + 1 byte ServerNum
	public static final int INTRO_PACKET_SIZE = 1;
	public static final int DEPART_PACKET_SIZE = 2;
	public static final int CLAIM_PACKET_SIZE = 34; //1b opCode + 1b serverNum + 32b id
	public static final int TASK_PACKET_SIZE = 5; //1b opCode + 32b userID 
	public static final int RESP_PACKET_SIZE = 2;

	public static final int MAX_PACKET_SIZE = 34;

	private final int CENSUS_TIMEOUT = 1000; //Census timeout in milliseconds
	private final int PING_TIMEOUT = 500;
	private final int TCP_TIMEOUT = 2000;
	private final int PULSE_TIME = 2000;

	private final int MAX_SERVERS = 255; //Max servers allowed in a group

	//Server number and count 
	private int  serverNumber;
	private int serverCount;

	private HashMap<String,Integer> taskMap;
	private ConcurrentHashMap<Integer,String> portMap;

	private final int PORT;
	private final String GROUP_NAME;
	private InetAddress group;
	private MulticastSocket socket;

	public WlfServer(int p, String g){
		PORT = p;
		GROUP_NAME = g;
		portMap = new ConcurrentHashMap<Integer,String>(3,0.75f,1);
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
		//Initialize port map
		initPortMap();
	}

	public void introduce() throws IOException{
		//Construct intro packet
		DatagramPacket intro = newIntroPacket();

		//send introduction
		socket.send(intro);
	}

	private void initPortMap(){
		portMap.put(new Integer(2699),"");
		portMap.put(new Integer(2708),"");
		portMap.put(new Integer(2710),"");
	}

	public void takeCensus() throws SocketException, IOException{
		//Set max time to wait in between greetings
		socket.setSoTimeout(CENSUS_TIMEOUT);
		//Initialize the timeout manager and flag
		AtomicBoolean flag = new AtomicBoolean(false);
		TimeoutManager timeout = new TimeoutManager(CENSUS_TIMEOUT,flag);

		//Array for census of servers that respond
		boolean[] aliveServers = new boolean[MAX_SERVERS-1];

		//Number of servers reported in
		int numReports = 0;

		//Start the timeout timer
		timeout.resetTimeout();

		//Wait for reports from servers until timeout occurs
		try{
			for(;;){
				//If timeout manager has recorded a timeout
				if(flag.get()){
					System.out.println("Thread timeout");
					break;
				}
				//Construct packet
				DatagramPacket cPacket = newCensusPacket(false);

				//Receive on socket
				socket.receive(cPacket);

				//Verify OpCode
				if(cPacket.getData()[0] != CENSUS_BYTE) continue;

				//Reset the timer
				timeout.resetTimeout();

				//Record the server alive
				aliveServers[byteToInt(cPacket.getData()[1])] = true;
				numReports++;
			}
		} catch(SocketTimeoutException t){ //Timeout reached, census gather is over
			System.out.println("Census timeout reached, finished waiting on reports.");
			timeout = null;
		}

		//Reset socket timeout
		socket.setSoTimeout(0);

		//Get next available spot, either end of list 
		int nextSpot = nextAvailableSpot(aliveServers);
		boolean spotAvailable = false;

		//Check bounds, no more than 255 servers allowed
		if(nextSpot == -1){
			sendDepartMessage();
			throw new IOException("At max servers, can't register server.");
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

		//start the heartbeat thread
		new Thread(new HeartbeatWorker(GROUP_NAME,PORT,PULSE_TIME,serverCount)).start();

		//If you're not the highest server, you took the place of a dead server
		if(serverNumber != serverCount-1){
			//Adjust other servers counts minus one
			System.out.println("Adjusting other servers count ");
			sendDepartMessage();
		}


	}

	private boolean pingServer(int serverNum, int ttw) throws SocketException, IOException{
		System.out.println("Pinging "+serverNum);
		//Create packet
		DatagramPacket packet = newPingPacket(serverNum,true);

		//Send ping
		socket.send(packet);

		//Create the receive packet
		packet = newPingPacket(serverNum,false);

		//Set socket timeout
		socket.setSoTimeout(ttw);
		//Initialize the timeout manager
		AtomicBoolean flag = new AtomicBoolean(false);
		TimeoutManager timeout = new TimeoutManager(CENSUS_TIMEOUT,flag);

		boolean response = false;

		try{ //Wait for server to respond
			for(;;){
				//Check for a timeout manager timeout
				if(flag.get())
					break;
				socket.receive(packet);
				//Check packet header
				if(packet.getData()[0] == PING_BYTE)
					if(packet.getData()[1] == (byte)serverNum)
						response = true;
			}

		} catch(SocketTimeoutException ex){
			response =  false;
		}

		//Reset socket timeout
		socket.setSoTimeout(0);

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
		socket.send(departPack);

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
		System.out.print("listening on " + GROUP_NAME + " ");
		System.out.print(" on port " + PORT + "\n");

		//Print group information
		System.out.println("Current server count: " + serverCount);

		listen();

	}

	private void listen(){

		try{
			for(;;){
				//Construct new packet
				DatagramPacket packet = newDefaultPacket();
				//receive packet
				socket.receive(packet);
				//Handle the packet
				handlePacket(packet);
			}
		} catch(SocketTimeoutException ste){
			System.out.println("Timed out in listen thread...");
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
			case CENSUS_BYTE:
				handleCensus(pack);
				break;
			case DEPART_BYTE:
				handleDepart(pack);
				break;
			case CLAIM_BYTE:
				handleClaim(pack);
				break;
			case PING_BYTE:
				handlePing(pack);
				break;
			case TASK_BYTE:
				handleTask(pack);
				break;
			case HEART_BYTE:
				handleHeartbeat(pack);
				break;
			case RESP_BYTE:
				handleResponse(pack);
				break;
			case UPDATE_BYTE:
				handleUpdate(pack);
				break;
			default:
				System.out.println("Wrong OpCode " + opCode + ", unable to handle");
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
		String claimID = getClaimID(p);
		claimID = claimID.trim();
		
		//If the ID isn't in the map, put  
		if(!taskMap.containsKey(claimID))
			taskMap.put(claimID,new Integer(0));

		//Get how many concessions (or if you've conceded)
		int conNum = taskMap.get(claimID).intValue();
		if(conNum == -1){
			System.out.println("Already conceded, ignore the packet");
		} else{ //You haven't conceded yet, handle the claim
			//Get the number of the server
			int claimServ = byteToInt(p.getData()[1]); 
			//Ignore your own claim
			if(claimServ == serverNumber){
				System.out.println("Ignored self claim");
				return;
			}

			if(claimServ > serverNumber){//Higher server is claiming the id, concede
				System.out.println("Conceded " + claimID + " to " + claimServ);
				//Put sentinel value in map
				taskMap.put(claimID,new Integer(-1));
			} else{//You outrank them, the claim message serves as a concession
				System.out.println("Server "+claimServ+" concession received for "+claimID);
				//Increment your number of concessions
				conNum++;
				System.out.println("Concession count: " + conNum);
				//Check if you have seen enough concession
				if(conNum != serverCount-1){
					//Update the map
					taskMap.put(claimID,new Integer(conNum));
				} else {
					System.out.println("Doing the task");
					//Remove task from the map
					taskMap.remove(claimID);
					//You're the winner, carry out the task
					doTask(claimID);
				}
			}//end else outrank
		//	System.out.println("End of handle");
		}//end else handle claim
	}

	private void handleTask(DatagramPacket pack) throws  IOException{
		//Get the user id
		String userID = pack.getAddress().getHostName();
		int claimNum = getClaimNum(pack.getData());
		System.out.println("User " + userID + " requesting connection");
		int portClaim = getNextAvailablePort();
		if(portClaim != -1){
			//Claim the task
			claimTask(userID.trim(),claimNum,portClaim);
		} else{
			System.out.println("Conceded, no available ports");
			//Concede the task
			concedeTask(userID.trim(),claimNum);
		}
	}


	private void handleDepart(DatagramPacket p) throws IOException{
		if(byteToInt(p.getData()[1]) != serverNumber){
			//Decrement server count
			serverCount--;

			System.out.println("Server " + p.getData()[1] +  "at "+p.getAddress()+" departed, server count: "+serverCount);
		} else {
			//In place for handling inserting server in place of dead server
			System.out.println("Ignored self depart message");
		}
	}

	private void handleCensus(DatagramPacket p) throws IOException{
		System.out.println("Census from " + p.getData()[1]);
	}

	private void handlePing(DatagramPacket p) throws IOException{
		//Get the target of the ping
		int targetServer = byteToInt(p.getData()[1]);
		//If it's you, respond to it
		if(targetServer == serverNumber){
			respondToPing(targetServer);
		}
	}

	private void handleHeartbeat(DatagramPacket pack) throws IOException{
		/* TODO Respond to the heartbeat with a response
			 packet */
		//System.out.println("Heartbeat pulse received");
		//Create new response packet
		DatagramPacket response = newResponsePacket();

		//Send the response
		socket.send(response);
	}

	private void handleResponse(DatagramPacket pack){
		/* TODO Handle responses... probably just drop them...*/
	}

	private void handleUpdate(DatagramPacket pack){
		/* TODO update your server count, assess claims */
		//Get the data from the packet
		byte[] updateBuffer = pack.getData();

		//Get the new server count
		int newServerCount  = byteToInt(updateBuffer[1]);

		//System.out.println("Handling update " + newServerCount);

		//Check the byte for validity
		if(newServerCount >= 1){
			System.out.println("Updated server count: " + newServerCount);
			serverCount = newServerCount;
			checkClaims();
		}
	}

	private void checkClaims(){
		//Check each task  
		for(Iterator<Map.Entry<String,Integer>> it = taskMap.entrySet().iterator();
				it.hasNext(); ){
			Map.Entry<String,Integer> entry = it.next();
			//Get the concession number from the task
			int cons = entry.getValue().intValue();
			//Check if the task is claimed
			if(cons == serverCount-1){
				doTask(entry.getKey());
				it.remove();
			}
		}
		//Clean up the map
		cleanupClaims();
	}

	private void cleanupClaims(){
		//Iterate over the map
		for(Iterator<Map.Entry<String,Integer>> it = taskMap.entrySet().iterator();
				it.hasNext(); ){
			Map.Entry<String,Integer> entry = it.next();
			//Get the concession number from the task
			int cons = entry.getValue().intValue();
			if(cons == -1){//Remove it if you conceded
				it.remove();
			}
		}
	}

	private String getClaimID(DatagramPacket p){
		if(p.getData()[0] != CLAIM_BYTE){
			System.out.println("Not a claim packet");
			return null;
		} else {
			byte[] pb = p.getData();
			return new String(pb,2,pb.length-2);
		}
	}

	private void doTask(String taskID){
		//Extract the client address
		String address = extractAddress(taskID);
		//Get the TCP port to be used
		int tcpPort = getPort(taskID);
		//Start the work in a separate thread
		new Thread(new TaskWorker(tcpPort,TCP_TIMEOUT,address)).start();
	}

	private void concedeTask(String uid, int cn) throws IOException{
		//Create a claimID for the task
		String claimID = generateClaimID(uid,cn);
		//Make a "concede" packet
		DatagramPacket concedePack = newClaimPacket(claimID,true);
		//Send your concession
		socket.send(concedePack);
	}

	private void claimTask(String uid, int cn,  int p) throws IOException{
		//Create a claimID for the task
		String claimID = generateClaimID(uid,cn);
		//Store the claim id with the port
		portMap.put(new Integer(p),claimID);
		//Make a claim packet
		DatagramPacket claimPack = newClaimPacket(claimID,false);
		//Send your claim
		System.out.println("Claiming "+claimID);
		socket.send(claimPack);
	}

	private int getPort(String cid){

		for(Map.Entry<Integer,String> entry : portMap.entrySet()){
			String val = entry.getValue();
			for(char c: cid.toCharArray())
				System.out.print("["+(byte)c+"]");
			System.out.println(val.length()+"|"+cid.length());
			System.out.println(val.compareTo(cid));
			System.out.println("["+val+"]|["+cid+"]");
			if(val.equals(cid)){
				System.out.println("EQUALS");
				return entry.getKey().intValue();
			}
		}
		
		return -1;
	}

	private int getNextAvailablePort(){
		//Available ports have values of "" for their string value
		for(Map.Entry<Integer,String> entry : portMap.entrySet()){
			if(entry.getValue().equals("")){
				System.out.println(entry.getKey()+" selected");
				return entry.getKey().intValue();
			}
		}
		
		//Return -1 if no ports available
		return 0;
	}

	private String extractAddress(String tid){
		//TODO MAKE THIS ACTUALLY DO SOMETHING
		//TODO BETTER
		String[] split = tid.split(",");
		return split[0];
	}

	private int getClaimNum(byte[] bytes){
		System.out.println(bytes.length);
		byte[] numByteArray = new byte[TASK_PACKET_SIZE-1];
		System.arraycopy(bytes,1,numByteArray,0,numByteArray.length);
		return byteArrayToInt(numByteArray);
	}


	private String generateClaimID(String userID, int claimN){
		//TODO MAKE THIS ACTUALLY DO SOMETHING
		return userID+","+claimN;
	}

	private void respondToPing(int target) throws IOException{
		//Make new packet for sending
		DatagramPacket pack = newPingPacket(target,true);
		//Send the packet
		socket.send(pack);
	}

	private void sendCensusInfo() throws IOException{
		//Construct packet to send census info
		DatagramPacket sendPacket = newCensusPacket(true);
		//send the packet
		socket.send(sendPacket);

		System.out.println("Census packet sent.");

	}

	private DatagramPacket newDefaultPacket(){
		DatagramPacket pack;
		//Buffer
		byte[] buf = new byte[MAX_PACKET_SIZE];
		pack = new DatagramPacket(buf,buf.length);
		return pack;
	}
	
	public DatagramPacket newResponsePacket(){
		//Construct buffer
		byte[] respBytes = new byte[RESP_PACKET_SIZE];
		respBytes[0]  = RESP_BYTE;
		respBytes[1] = (byte)serverNumber;
		//Construct packet
		return new DatagramPacket(respBytes,respBytes.length,group,PORT);
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

	public DatagramPacket newIntroPacket(){
		DatagramPacket pack;
		//Construct buffer
		byte[] introBytes = new byte[INTRO_PACKET_SIZE];
		//Fill in OpCode
		introBytes[0] = INTRO_BYTE;
		//Create packet
		pack = new DatagramPacket(introBytes,introBytes.length,group,PORT);

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
			pack = new DatagramPacket(cBytes,cBytes.length,group,PORT);
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
		pack = new DatagramPacket(dBytes,dBytes.length,group,PORT);

		return pack;
	}

	private DatagramPacket newClaimPacket(String id, boolean pass){

		DatagramPacket pack;
		//Buffer
		byte[] claimBytes = new byte[CLAIM_PACKET_SIZE];
		//Opcode
		claimBytes[0] = CLAIM_BYTE;
		if(!pass){//If not passing on the task
			//ServerNumber
			claimBytes[1] = (byte)serverNumber; 
		} else{
			claimBytes[1] = (byte)-1;
		}
		//Get string as bytes
		byte[] idBytes = id.getBytes();
		//Copy the bytes to the packet buffer
		System.arraycopy(idBytes,0,claimBytes,2,idBytes.length);
		//Make the packet
		pack = new DatagramPacket(claimBytes,claimBytes.length,group,PORT);

		return pack;

	}

	//Converts byte to unsigned byte equivalent int
	//	Used to change range of bytes
	//	from signed: [-128,127] to unsigned: [0,255]
	public static int byteToInt(byte b){
		return b & 0xFF;
	}

	//Returns next available server number
	private int nextAvailableSpot(boolean[] bArray){
		for(int i = 0; i < bArray.length; i++)
			if(bArray[i]==false)
				return i;
		return -1;
	}
	
	public static byte[] intToByteArray(int i) {
		return ByteBuffer.allocate(4).putInt(i).array();
	}

	public  static int byteArrayToInt(byte[] b) {
		return ByteBuffer.allocate(4).put(b).getInt(0);
	}

	//Main method to test functionality
	public static void main(String[] args){
		String multicastAddress = "228.5.6.7";
		WlfServer as = new WlfServer(22699,multicastAddress);
		try{
			as.run();
		} catch(IOException ioe){
			ioe.printStackTrace();
		}
	}

}
