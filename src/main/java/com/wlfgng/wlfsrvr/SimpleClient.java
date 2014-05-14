import java.util.Scanner;


import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

public class SimpleClient {
	private Cluster cluster;
	private Session session;
	private final int PORT = 32699;

	public void connect(String node){
		cluster = Cluster.builder().withPort(PORT).addContactPoint(node).build();
		Metadata metadata = cluster.getMetadata();
		for(Host host: metadata.getAllHosts()){
			System.out.printf("Datacenter: %s; Host: %s; Rack: %s\n",
					host.getDatacenter(), host.getAddress(), host.getRack());
		}
		session = cluster.connect();

	}

	public void createSchema(){
		session.execute("CREATE KEYSPACE mykeyspace WITH REPLICATION"
				+ "= {'class':'SimpleStrategy','replication_factor':1};");

		session.execute("CREATE TABLE users ("
				+ "user_id int PRIMARY KEY,"
				+ "fname text,"
				+ "lname text);");
	}

	public void loadData(String id, String f, String l){
		session.execute("INSERT INTO mykeyspace.users(user_id,fname,lname)"
				+ "VALUES ("+id+","+"'"+f+"','"+l+"');");
	}

	public void querySchema(String id){
		ResultSet results = session.execute("SELECT * FROM mykeyspace.users "
				+ "WHERE user_id = "+id+";");
		for(Row row : results){
			System.out.println("UserID: +"+row.getInt("user_id")+"\tFName: "+row.getString("fname")+"\tLName: "+row.getString("lname"));
		}		
	}

	public void selectAll(){
		ResultSet results = session.execute("SELECT * FROM mykeyspace.users;");
		for(Row row : results){
			System.out.println("UserID: +"+row.getInt("user_id")+"\tFName: "+row.getString("fname")+"\tLName: "+row.getString("lname"));
		}
	}

	public void close(){
		//cluster.shutdown();
	}

	public static void main(String[] args){
		String hostname = "localhost";
		Scanner scan = new Scanner(System.in);

		System.out.print("Input host: ");
		hostname = scan.nextLine();


		SimpleClient client = new SimpleClient();
		client.connect(hostname);

		if(args.length > 1){
			System.out.println("Creating Schema");
			client.createSchema();
		}
		String id = "";
		String fn = "";
		String ln = "";

		while(id.compareTo("x") != 0){
			System.out.print("Input id (or x to quit): ");
			id = scan.nextLine();
			if(id.compareTo("x") != 0){
				System.out.print("Input first name: ");
				fn = scan.nextLine();
				System.out.print("Input last name: ");
				ln = scan.nextLine();
				client.loadData(id, fn, ln);
			}
		}
		id = "";
		while(id.compareTo("x") != 0){
			System.out.print("Input id to search for: ");
			id = scan.nextLine();
			if(id.compareTo("x") != 0)
				if(id.compareTo("A") == 0)
					client.selectAll();
				else
					client.querySchema(id);
		}

		client.close();
	}
}

