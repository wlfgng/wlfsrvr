import java.io.Serializable;

import java.util.HashMap;

public class Response implements Serializable{

	private RespType type;
	private HashMap<String,String> results;

	public Response(RespType t){
		this.type = t;
		results = null;
	}

	public Response(RespType t, HashMap<String,String> r){
		this.type = t;
		this.results = r;
	}

	public HashMap<String,String> getResults(){
		return results;
	}

	public RespType getType(){
		return type;
	}

}
