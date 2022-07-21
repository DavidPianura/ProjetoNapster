package napster;

import java.util.LinkedList;

public class NapsterMessage {
	private String request;
	private LinkedList<String> properties;
	private int myPort;
	
	public NapsterMessage(String request, LinkedList<String> properties, int port) {
		this.request = request;
		this.properties = properties;
		this.myPort = port;
	}

	public LinkedList<String> getProperties() {
		return properties;
	}
	
	public String getRequest() {
		return request;
	}
	
	public int getPort() {
		return myPort;
	}
	
}
