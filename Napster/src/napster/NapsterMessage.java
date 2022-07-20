package napster;

import java.util.LinkedList;

public class NapsterMessage {
	private String request;
	private LinkedList<String> properties;
	
	public NapsterMessage(String request, LinkedList<String> properties) {
		this.request = request;
		this.properties = properties;
	}

	public LinkedList<String> getProperties() {
		return properties;
	}
	
	public String getRequest() {
		return request;
	}
	
}
