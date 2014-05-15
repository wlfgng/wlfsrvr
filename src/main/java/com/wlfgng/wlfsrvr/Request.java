import java.io.Serializable;

public class Request implements Serializable{

	private final String tag;
	private final String pck;
	private final String user;
	private final String pass;
	private final ReqType type;

	public Request(String t, String pk, String u, String pw, ReqType ty) {
		this.tag = t;
		this.pck = pk;
		this.user = u;
		this.pass = pw;
		this.type = ty;
	}
	
	public Request(String t, String pk, ReqType ty){
		this.tag = t;
		this.pck = pk;
		this.user = this.pass = null;
		this.type = ty;
	}

	public String getTag(){
		return tag;
	}

	public String getUser(){
		return user;
	}

	public String getPck(){
		return pck;
	}

	public String getPass(){
		return pass;
	}

	public ReqType getType(){
		return type;
	}
}
