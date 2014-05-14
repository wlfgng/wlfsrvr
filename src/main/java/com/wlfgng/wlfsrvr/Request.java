public class Request{

	private final String tag;
	private final String user;
	private final ReqType type;

	public Request(String t, String u, ReqType ty) {
		this.tag = t;
		this.user = u;
		this.type = ty;
	}

	public String getTag(){
		return tag;
	}

	public String getUser(){
		return user;
	}

	public ReqType getType(){
		return type;
	}
}
