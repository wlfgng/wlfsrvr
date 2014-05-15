import java.net.Socket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException; 

import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

public class TaskWorker implements Runnable{
	private final int PORT;
	private final int TIME_TO_WAIT;
	private final String HOST;
	
	private final String C_NAME = "wolf.cs.oswego.edu";
	private final int C_PORT = 32699;

	public TaskWorker(int p, int t, String h){
		this.PORT = p;
		this.TIME_TO_WAIT = t;
		this.HOST = h;
	}

	public void run(){
		Socket socket  = null;
		try{
			//Get address of socket
			InetAddress address = InetAddress.getByName(HOST);
			//Initialize socket
			System.out.println("Connecting to "+address+"...");
			socket = new Socket(address,22710);

			//Get input and output streams
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

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

			String res = fulfillRequest(clientReq);
		
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
			} catch(IOException ioe){
				System.exit(-1);
			}
		}
	}//End run

	public String fulfillRequest(Request req){
		//Get the type of request
		ReqType type = req.getType();
		//Query cassandra
		String results = "";
		switch(type){
			case ADD:
				System.out.println("ADD");
				results = addRow(req);
				break;
			case REMOVE:
				System.out.println("REMOVE");
				//results = removeRow(req);
				break;
			case UPDATE:
				System.out.println("UDPATE");
				results = addRow(req);
				break;
			case GET:
				System.out.println("GET");
			//	results = getRow(req);
				break;
			case GETALL:
				System.out.println("GETALL");
			//	results = getAllRows(req);
				break;
			default:
				System.out.println("ERR");
				results = "ERR";
				break;
		}

		return results;

	}

	public String addRow(Request req){
		System.out.println("ADDING ROW");
		//Setup the connection to cassandra
		Cluster cluster = Cluster.builder().withPort(C_PORT).addContactPoint(C_NAME).build();
		Metadata metadata = cluster.getMetadata();

		//Echo some information
		for(Host host: metadata.getAllHosts()){
			System.out.printf("Datacenter: %s; Host: %s; Rack: %s\n",
					host.getDatacenter(), host.getAddress(), host.getRack());
		}

		//Make connection
		Session session = cluster.connect();

		//Construct query
		String query = "INSERT INTO wlfpck.wlfdn(pckname,tag,username,password)";
		query += " VALUES ('" + req.getPck() + "','" + req.getTag() + "','";
		query += req.getUser() + "','" + req.getPass() + "');";


		session.execute(query);

		cluster.shutdown();

		return "Success";
	}
}
