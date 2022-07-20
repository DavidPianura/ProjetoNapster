package tcpdemo;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

public class TCPClient {
	public static void main(String[] args) throws Exception{
		Socket s = new Socket("127.0.0.1", 9000);
		
		OutputStream os = s.getOutputStream();
		DataOutputStream writer = new DataOutputStream(os);
		
		InputStreamReader is = new InputStreamReader(s.getInputStream());
		BufferedReader reader = new BufferedReader(is);
		
		BufferedReader InFromUser = new BufferedReader(new InputStreamReader(System.in));
		String texto = InFromUser.readLine();
		
		writer.writeBytes(texto + "\n");
		
		String response = reader.readLine();
		System.out.println("DoServidor" + response);
		s.close();
	}

}
