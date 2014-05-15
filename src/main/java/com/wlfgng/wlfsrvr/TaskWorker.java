import java.net.Socket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException; 

import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

import java.util.HashMap;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

import com.datastax.driver.core.exceptions.NoHostAvailableException;

public class TaskWorker implements Runnable{

	private final int TIME_TO_WAIT;
	private final String HOST;
	private final int PORT;

	private final String[] C_NAMES = {"wolf.cs.oswego.edu","pi.cs.oswego.edu","rho.cs.oswego.edu"};
	private final int C_PORT = 32699;
	private int cTry;

	private Cluster cluster;
	private Session session;

	public TaskWorker(int p, int t, String h){
		this.PORT = p;
		this.TIME_TO_WAIT = t;
		this.HOST = h;
		this.cTry = 0;
	}

	public void run(){
		Socket socket  = null;
		ObjectOutputStream out = null;
		ObjectInputStream in = null;
		try{
			//Get address of socket
			InetAddress address = InetAddress.getByName(HOST);
			//Initialize socket
			System.out.println("Connecting to "+address+"...");
			socket = new Socket(address,22710);

			//Get input and output streams
			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());

			out.flush();
			//TODO Change this to sending your key
			String msg = "Hello Message";
			out.writeObject(msg);
			out.flush();

			//Wait to receive the request
			Object clientInput = in.readObject();
			Request clientReq;
			if(clientInput instanceof Request){
				clientReq = (Request)clientInput;
				System.out.println("REQ");
			}
			else{
				System.out.println("ELSE");
				return;
			}

			Response response = fulfillRequest(clientReq);

			out.writeObject(response);
		
		} catch(SocketTimeoutException timeout){
			System.out.println("Client connection not made...");
		} catch(SocketException se){
			se.printStackTrace();
		} catch(IOException ioe){
			ioe.printStackTrace();
		} catch(ClassNotFoundException cnf){
			cnf.printStackTrace();
		} finally {
			try{
				if(socket != null)
					socket.close();
				if(in != null)
					in.close();
				if(out != null)
					out.close();
			} catch(IOException ioe){
				System.exit(-1);
			}
		}
	}//End run

	public Response fulfillRequest(Request req){
		//Get the type of request
		ReqType type = req.getType();
		//Query cassandra
		Response response = null;
		switch(type){
			case ADD:
				System.out.println("ADD");
				response = addRow(req);
				break;
			case REMOVE:
				System.out.println("REMOVE");
				response = removeRow(req);
				break;
			case UPDATE:
				System.out.println("UDPATE");
				response = addRow(req);
				break;
			case GET:
				System.out.println("GET");
				response = getRow(req);
				break;
			case GETALL:
				System.out.println("GETALL");
				response  = getAllRows(req);
				break;
			default:
				System.out.println("ERR");
				response = new Response(RespType.FAILURE);
				break;
		}

		return response;

	}

	public Response addRow(Request req){
		System.out.println("ADDING ROW");
		
		//Make connection
		session = makeConnection();

		//Check for no connection
		if(session == null)
			return new Response(RespType.FAILURE);

		//construct query
		String query = "INSERT INTO  wlfpck.wlfdn(pckname,tag,username,password)";
		query += " VALUES  ('" + req.getPck() + "','" + req.getTag() + "','";
		query += req.getUser() + "','" + req.getPass() + "');";

		session.execute(query);

		closeConnection();

		return new Response(RespType.SUCCESS);
	}

	public Response removeRow(Request req){
		System.out.println("removing row");
	
		//Make connection
		session = makeConnection();

		//Check for no connection
		if(session == null)
			return new Response(RespType.FAILURE);

		//Construct query
		String query = "DELETE from wlfpck.wlfdn WHERE ";
		query += "pckname= '" + req.getPck() + "'";
		query += " AND tag= '" + req.getTag() + "';";

		session.execute(query);

		closeConnection();

		return new Response(RespType.SUCCESS);
	}

	public Response getRow(Request req){
		System.out.println("GETTING ROW");
		
		//Make the connection
		session = makeConnection();
		
		//Check for no connection
		if(session == null)
			return new Response(RespType.FAILURE);

		//Construct query
		String query = "SELECT username,password  FROM wlfpck.wlfdn WHERE ";
		query += "pckname= '" + req.getPck() + "'";
		query += " AND tag= '" + req.getTag() + "';";

		//Query the database
		ResultSet results = session.execute(query);

		String resultString = "";
		int resultCount  = 0;
		for(Row row : results){
			//Increment count
			resultCount++;
			resultString += row.getString("username") + ",";
			resultString += row.getString("password") + "|";
		}

		String[] rows = resultString.split("\\|");

		HashMap<String,String> resultMap = new HashMap<String,String>();

		System.out.println(resultString);
		if(resultCount==0)
			return new Response(RespType.FAILURE);
		else{
			//Fill up hash map
			for(String row : rows){
				String[] values = row.split(",");
				resultMap.put(values[0],values[1]);
			}
			return new Response(RespType.RESULTS,resultMap);
		}

	}

	public Response getAllRows(Request req){
		System.out.println("GETTING ROW");

		//Make the connection
		session = makeConnection();

		//Check for no connection
		if(session == null)
			return new Response(RespType.FAILURE);

		//Construct query
		String query = "SELECT tag,username,password  FROM wlfpck.wlfdn WHERE ";
		query += "pckname= '" + req.getPck() + "';";

		//Query the database
		ResultSet results = session.execute(query);

		String resultString = "";
		int resultCount  = 0;
		for(Row row : results){
			//Increment count
			resultCount++;
			resultString += row.getString("tag") +"|";
			resultString += row.getString("username") + ",";
			resultString += row.getString("password") + "\n";
		}

		String[] rows = resultString.split("\n");

		HashMap<String,String> resultMap = new HashMap<String,String>();

		System.out.println(resultString);
		if(resultCount==0)
			return new Response(RespType.FAILURE);
		else{
			//Fill up hash map
			for(String row : rows){
				String[] values = row.split("\\|");
				System.out.println(values);
				if(values.length >= 2){
					System.out.println("PUT");
					resultMap.put(values[0],values[1]);
				}
			}
			System.out.println("RESM: "+resultMap.size());
			return new Response(RespType.RESULTS,resultMap);
		}

	}

	public void closeConnection(){
		cluster.shutdown();
	}

	public Session makeConnection(){
		//Setup the connection
		for(;;){
			try{
				System.out.println("Connecting to cassandra with: "+C_NAMES[cTry]+":"+C_PORT);
				cluster = Cluster.builder().withPort(C_PORT).addContactPoint(C_NAMES[cTry]).build();
				if(cluster != null)
					break;
			} catch(NoHostAvailableException noh){
				System.out.println(C_NAMES[cTry] + " not available");
				cTry++;
				if(cTry >2)
					return null;
			}
		}
		Metadata metadata = cluster.getMetadata();

		//Echo some information
		for(Host host: metadata.getAllHosts()){
			System.out.printf("Datacenter: %s; Host: %s; Rack: %s\n",
					host.getDatacenter(), host.getAddress(), host.getRack());
		}

		//Make connection
		return cluster.connect();

	}
}
