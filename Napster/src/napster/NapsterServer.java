package napster;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

public class NapsterServer {
	
	// HashMap com os peers inseridos no sistema
	static HashMap<String, LinkedList<String>> peers = new HashMap<String, LinkedList<String>>();
	static LinkedList<String> peers_ports_udp = new LinkedList<String>();
	static LinkedList<String> peers_alive = new LinkedList<String>(); 
	
	public static class ThreadAlive extends Thread {
		private DatagramSocket serverSocket;

		public ThreadAlive(DatagramSocket serverSocket) {
			this.serverSocket = serverSocket;
		}
		
		public void whoIsAlive() {
			
			int tamPeers = peers_ports_udp.size();
			int tamAlive = peers_alive.size();
			LinkedList<Integer> indicesToRemove = new LinkedList<Integer>();
			
			if (tamPeers != tamAlive) {
				for (int i = 0; i < peers_ports_udp.size(); i++) {
					if (!peers_alive.contains(peers_ports_udp.get(i))) {
						String keyRemoveHash = peers_ports_udp.get(i).split(":")[0] + ":" + peers_ports_udp.get(i).split(":")[2];
						System.out.println("Peer " + keyRemoveHash + " morto. Eliminando seus arquivos " + peers.get(keyRemoveHash));
						peers.remove(keyRemoveHash);
						indicesToRemove.add(i);
					}
				}
				for (int i = 0; i < peers_ports_udp.size(); i++) peers_ports_udp.remove(i);
			}
		}
		
		public void run(){
			Gson gson = new Gson();
			NapsterMessage alive = new NapsterMessage("ALIVE", null, 10098);
			String req = gson.toJson(alive);
			
			while (true) {
				try {
					if (!peers_ports_udp.isEmpty()) {
						for (int i = 0; i < peers_ports_udp.size(); i++) {
							
							byte[] sendData_alive = new byte[1024];
							sendData_alive = req.getBytes();
							
							InetAddress IPAddres = InetAddress.getByName(peers_ports_udp.get(i).split(":")[0]);
							int port = Integer.parseInt(peers_ports_udp.get(i).split(":")[1]);
							DatagramPacket sendPacket = new DatagramPacket(sendData_alive, sendData_alive.length, IPAddres, port);
							serverSocket.send(sendPacket);
						}
						
						// Roda periodicamente a cada 30s
						System.out.println("Agora a Thread vai dormir");
						ThreadAlive.sleep(30000);
						// Checa quem recebeu o ALIVE e respondeu
						whoIsAlive();
						
						// Reseta a lista dos Peers vivos para a proxima checagem
						peers_alive.clear();
					}
				} catch (Exception e) {
					
				}
			}
		}
		
	}
	
	public static class ThreadJoin extends Thread {
		
		private DatagramSocket serverSocket;
		
		public ThreadJoin(DatagramSocket serverSocket) {
			
			this.serverSocket = serverSocket;
			
		}
		
		public void run(NapsterMessage peer, DatagramPacket recPkt) {
			try {
				
				Gson gson = new Gson();
				
				byte[] sendBuf = new byte[1024];
				
				NapsterMessage joinOK = new NapsterMessage("JOIN_OK", null, 10098);
				sendBuf = gson.toJson(joinOK).getBytes();
				
				InetAddress IPAddres = recPkt.getAddress();
				int peerPort = peer.getPort();
				
				String key = IPAddres.getHostAddress() +  ":" + peerPort;
				peers.put(key, peer.getProperties());
				
				String key_port_udp = IPAddres.getHostAddress() +  ":" + recPkt.getPort() + peer.getPort();
				peers_ports_udp.add(key_port_udp);
				
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
				Gson gson = new Gson();
				byte[] sendBuf = new byte[1024];
				NapsterMessage leaveOK = new NapsterMessage("LEAVE_OK", null, 10098);
				sendBuf = gson.toJson(leaveOK).getBytes();
				
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
	
	public static class ThreadUpdate extends Thread {
		private DatagramSocket serverSocket;
		
		public ThreadUpdate(DatagramSocket serverSocket) {
			
			this.serverSocket = serverSocket;
			
		}
		
		public void run(NapsterMessage peer, DatagramPacket recPkt) {
			try {

				Gson gson = new Gson();
				byte[] sendBuf = new byte[1024];
				
				InetAddress IPAddres = recPkt.getAddress();
				int port = recPkt.getPort();
				
				// Pega a chave do peer que enviou a requisicao para atualizar seus arquivos no HashMap
				String keyToUpdate = recPkt.getAddress().getHostAddress() + ":" + peer.getPort();
				
				// Atualiza os arquivos do Peer
				peers.put(keyToUpdate, peer.getProperties());
				
				NapsterMessage updateOK = new NapsterMessage("UPDATE_OK", null, 10098);
				
				sendBuf = gson.toJson(updateOK).getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, IPAddres, port);
				serverSocket.send(sendPacket);
				
			} catch (Exception e) {
				// TODO: handle exception
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		
		DatagramSocket serverSocket = new DatagramSocket(10098);
		Gson gson = new Gson();
		
		ThreadJoin join = new ThreadJoin(serverSocket);
		ThreadLeave leave = new ThreadLeave(serverSocket);
		ThreadSearch search = new ThreadSearch(serverSocket);
		ThreadAlive alive = new ThreadAlive(serverSocket);
		ThreadUpdate update = new ThreadUpdate(serverSocket);
		
		while (true) {
			
			byte[] recBuffer = new byte[1024];
			
			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
			
			serverSocket.receive(recPkt); // BLOCKING
			
			String informacao = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
			NapsterMessage peer = gson.fromJson(informacao, NapsterMessage.class);
			
			if (peer.getRequest().equals("ALIVE_OK")) {
				String udpKey = recPkt.getAddress().getHostAddress() + ":" + recPkt.getPort() + peer.getPort();
				peers_alive.add(udpKey);
			}
			
			if (peer.getRequest().equals("JOIN")) {	
				join.run(peer, recPkt);
				// alive.run();
				// System.out.println(peers);
			}
			
			if (peer.getRequest().equals("LEAVE")) {
				leave.run(peer, recPkt);
				// System.out.println(peers);
			}
			
			if (peer.getRequest().equals("SEARCH")) {
				search.run(peer, recPkt);
			}
			
			if (peer.getRequest().equals("UPDATE")) {
				update.run(peer, recPkt);
			}
		}
	}

}
