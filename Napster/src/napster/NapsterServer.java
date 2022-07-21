package napster;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;

public class NapsterServer {
	
	// HashMap com os peers inseridos no sistema
	static HashMap<String, LinkedList<String>> peers = new HashMap<String, LinkedList<String>>();
	
	public static class ThreadJoin extends Thread {
		
		private DatagramSocket serverSocket;
		
		public ThreadJoin(DatagramSocket serverSocket) {
			
			this.serverSocket = serverSocket;
			
		}
		
		public void run(NapsterMessage peer, DatagramPacket recPkt) {
			try {
				
				byte[] sendBuf = new byte[1024];
				sendBuf = "JOIN_OK".getBytes();
				
				InetAddress IPAddres = recPkt.getAddress();
				int peerPort = peer.getPort();
				
				String key = IPAddres.getHostAddress() +  ":" + peerPort;
				peers.put(key, peer.getProperties());
				
				DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, IPAddres, recPkt.getPort());
				serverSocket.send(sendPacket);
				
				System.out.println("Peer " + IPAddres.getHostAddress() + ":" + peerPort + " adicionado com arquivos " + peers.get(key));
			} catch (IOException e) {
				// TODO Auto-generated catch block
			}
			
		}
	}
	
	public static class ThreadLeave extends Thread {
		
		private DatagramSocket serverSocket;
		
		public ThreadLeave(DatagramSocket serverSocket) {
			this.serverSocket = serverSocket;
		}
		
		public void run(NapsterMessage peer, DatagramPacket recPkt) {
			try {
				
				byte[] sendBuf = new byte[1024];
				sendBuf = "LEAVE_OK".getBytes();
				
				InetAddress IPAddres = recPkt.getAddress();
				int port = recPkt.getPort();
				
				String key = IPAddres.getHostAddress() +  ":" + String.valueOf(port);
				
				DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, IPAddres, port);
				
				serverSocket.send(sendPacket);
				
				peers.remove(key);
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				
			}
			
			
		}
	}
	
	public static class ThreadSearch extends Thread {
		
		private DatagramSocket serverSocket;
		
		public ThreadSearch(DatagramSocket serverSocket) {
			
			this.serverSocket = serverSocket;
			
		}
		
		public void run(NapsterMessage peer, DatagramPacket recPkt) {
			try {
				
				Gson gson = new Gson();
				byte[] sendBuf = new byte[1024];
				
				System.out.println("Peer " + recPkt.getAddress().toString().replace("/", "") + ":" + peer.getPort() + " solicitou o arquivo " + peer.getProperties().get(0));
				
				InetAddress IPAddres = recPkt.getAddress();
				int port = recPkt.getPort();
				
				// Lista ligada para armazenar os peers que contem o arquivo pedido
				LinkedList<String> has_file = new LinkedList<String>();
				
				// Iterando sobre o HashMap para popular a lista de peers com arquivos
				for (Entry<String, LinkedList<String>> entry: peers.entrySet()) {
					if (entry.getValue().contains(peer.getProperties().get(0))) {
						has_file.add(entry.getKey());
					}
				}
				
				sendBuf = gson.toJson(has_file).getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, IPAddres, port);
				serverSocket.send(sendPacket);
				
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
			}
			
		}
	}
	
	public static void main(String[] args) throws Exception {
		
		DatagramSocket serverSocket = new DatagramSocket(10098);
		Gson gson = new Gson();
		
		ThreadJoin join = new ThreadJoin(serverSocket);
		ThreadLeave leave = new ThreadLeave(serverSocket);
		ThreadSearch search = new ThreadSearch(serverSocket);
		
		while (true) {
			
			byte[] recBuffer = new byte[1024];
			
			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
			
			serverSocket.receive(recPkt); // BLOCKING
			
			String informacao = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
			NapsterMessage peer = gson.fromJson(informacao, NapsterMessage.class);
			
			if (peer.getRequest().equals("JOIN")) {	
				join.run(peer, recPkt);
				// System.out.println(peers);
			}
			
			if (peer.getRequest().equals("LEAVE")) {
				leave.run(peer, recPkt);
				// System.out.println(peers);
			}
			
			if (peer.getRequest().equals("SEARCH")) {
				search.run(peer, recPkt);
			}
		}
	}

}
