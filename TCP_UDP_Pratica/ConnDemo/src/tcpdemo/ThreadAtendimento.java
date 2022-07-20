package tcpdemo;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

public class ThreadAtendimento extends Thread{
	
	private Socket no = null;
	public ThreadAtendimento (Socket node) {
		no = node;
	}
	
	public void run() {
		try {
			InputStreamReader is = new InputStreamReader(no.getInputStream());
			BufferedReader reader = new BufferedReader(is);
			
			OutputStream os = no.getOutputStream();
			DataOutputStream writer = new DataOutputStream(os);
			
			String texto = reader.readLine();
			writer.writeBytes(texto.toUpperCase() + "\n");
		} catch (IOException e) {
 
		}
		
		
	}
}
