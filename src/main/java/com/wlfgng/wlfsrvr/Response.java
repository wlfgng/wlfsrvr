import java.io.Serializable;

import java.util.HashMap;

public class Response implements Serializable{

	private RespType type;
	private HashMap<String,String> results;
	private String message;

	public Response(RespType t){
		this.type = t;
		this.message = null;
		this.results = null;
	}

	public Response(RespType t, String m){
		this.type = t;
		this.message = m;
		this.results = null;
	}

	public Response(RespType t, HashMap<String,String> r){
		this.type = t;
		this.results = r;
	}

	public void setMessage(String msg){
		this.message = msg;
	}

	public HashMap<String,String> getResults(){
		return results;
	}

	public RespType getType(){
		return type;
	}

	public String getMessage(){
		return message;
	}

}
