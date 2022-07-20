package napster;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

public class NapsterPeer {
	
	static int portaPeer;
	
	public static class ThreadDownload extends Thread {
		
		private DatagramSocket clientSocket;
		private int downloadMode; // 0 - Aguardando Requisicao de Download; 1 - Realizando requisicao de Download
		private LinkedList<String> peers_with_file, file_request;
		
		public ThreadDownload(DatagramSocket clientSocket, int dMode, LinkedList<String> pWithFile, LinkedList<String> fRequest) {
			this.clientSocket = clientSocket;
			this.downloadMode = dMode;
			this.peers_with_file = pWithFile;
			this.file_request = fRequest;
		}
		
		// Metodo para realizar requisicoes de Download para outros peers
		public void downloadMode1() {
			try {
				boolean dAccept = false;
				NapsterMessage dRequest = new NapsterMessage("DOWNLOAD", this.file_request);
				Gson gson = new Gson();
				int rejeitados = 0;
				
				while (!dAccept) {
					
					// Se todos negaram, aguarda 5 segundos e faz a requisicao novamente
					if (rejeitados == this.peers_with_file.size()) {
						TimeUnit.SECONDS.sleep(5);
					}
					
					for (int i = 0; i < peers_with_file.size(); i++) {
						// Enviando requisicao de download para peers que possuem o arquivo
						int anotherPeerPort = Integer.parseInt(this.peers_with_file.get(i).split(":")[1]);
						Socket s = new Socket("127.0.0.1", anotherPeerPort);
						
						OutputStream os = s.getOutputStream();
						DataOutputStream writer = new DataOutputStream(os);
						
						InputStreamReader is = new InputStreamReader(s.getInputStream());
						BufferedReader reader = new BufferedReader(is);
						
						writer.writeBytes(gson.toJson(dRequest) + "\n");
						
						NapsterMessage response = gson.fromJson(reader.readLine(), NapsterMessage.class);
						
						if (response.getRequest().equals("DOWNLOAD_NEGADO")) {
							s.close();
							rejeitados++;
						} else {
							dAccept = true;
							// Chamar metodo de download
							break;
						}
					}
				}
			} catch (Exception e) {
				
			}
		}
		
		public void downloadMode0() throws IOException {
			ServerSocket serverSocket = new ServerSocket(portaPeer);
			int rand;
			
			while (true) {
				rand = ThreadLocalRandom.current().nextInt(1, 11);
				Socket no = serverSocket.accept();
			}
		}
		
		public void run(){
			
			// Quando a thread eh instanciada para aguardar requisicoes de download de outros peers
			if (this.downloadMode == 0) {
				
			}
			
			// Quando a thread eh instanciada para realizar requisicao de download para outros peers
			if (this.downloadMode == 1) {
				downloadMode1();
			}
		}
	}
	
	public static void join(NapsterMessage msg, String req, InetAddress IPAddres, DatagramSocket clientSocket) throws IOException {
		byte[] sendData_join = new byte[1024];
		sendData_join = req.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData_join, sendData_join.length, IPAddres, 10098);
		clientSocket.send(sendPacket);
		
		byte[] recBuffer = new byte[1024];	
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		clientSocket.receive(recPkt);
		
		String informacao = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
		
		// Printando infos quando JOIN_OK eh recebido
		InetAddress peer_ip = InetAddress.getLocalHost();
		int peer_port = clientSocket.getLocalPort();
		System.out.println("Sou o peer " + peer_ip.getLoopbackAddress().getHostAddress() + ":" + peer_port + " com arquivos " + msg.getProperties());
		
	}
	
	public static void leave(NapsterMessage msg_leave, String req, InetAddress IPAddres, DatagramSocket clientSocket) throws IOException {
		byte[] sendData_leave = new byte[1024];
		sendData_leave = req.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData_leave, sendData_leave.length, IPAddres, 10098);
		clientSocket.send(sendPacket);
		
		byte[] recBuffer = new byte[1024];	
		DatagramPacket recPkt_leave = new DatagramPacket(recBuffer, recBuffer.length);
		clientSocket.receive(recPkt_leave);
		
		String leave_ok = new String(recPkt_leave.getData(), recPkt_leave.getOffset(), recPkt_leave.getLength());
		
	}
	
	public static LinkedList<String> search(NapsterMessage msg, String req, InetAddress IPAddres, DatagramSocket clientSocket) throws IOException {
		
		Gson gson = new Gson();
		byte[] sendData = new byte[1024];
		sendData = req.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddres, 10098);
		clientSocket.send(sendPacket);
		
		byte[] recBuffer = new byte[1024];	
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		clientSocket.receive(recPkt);
		
		String informacao = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
		
		System.out.println("peers com arquivo solicitado: " + gson.fromJson(informacao, LinkedList.class));
		
		return gson.fromJson(informacao, LinkedList.class);
		
	}
	
	public static LinkedList<String> getInfosFolder(String folder) {
		
		File diretorio = new File(folder);
		LinkedList<String> arquivos = new LinkedList<String>();
		
		for (File file:diretorio.listFiles()) {
			arquivos.add(file.getName());
		}
		return arquivos;
		
	}
	
	
	public static void main(String[] args) throws Exception{
		
		Gson gson = new Gson();
		DatagramSocket clientSocket = new DatagramSocket();
		portaPeer = clientSocket.getPort();
		
		InetAddress IPAddres = InetAddress.getByName("127.0.0.1");
		
		boolean l = false;
		Scanner tec = new Scanner (System.in);
		
		System.out.println("----------------------------------------------");
		System.out.println("              Projeto Napster                 ");
		System.out.println("----------------------------------------------\n");
		
		
		while (!l) {
			
			int option = 0;
			
			// MENU INTERATIVO
			System.out.println();
			System.out.println("Para escolher uma funcao, digite o numero e tecle enter:");
			System.out.println("1. JOIN");
			System.out.println("2. SEARCH");
			System.out.println("3. DOWNLOAD");
			System.out.println("4. UPDATE");
			System.out.println("5. LEAVE");
			
			while (option <= 0 || option > 5) {
				System.out.print("Opcao escolhida: ");
				option = tec.nextInt();
				
				if (option <= 0 || option > 5) {
					System.out.println("Opcao invalida");
				}
			}
			
			switch(option) {
				case 1:
					String folder;
					System.out.print("Escreva o caminho completo da pasta: ");
					tec.nextLine();
					folder = tec.nextLine();
					LinkedList<String> files = new LinkedList<>();
					files = getInfosFolder(folder);
					NapsterMessage msg = new NapsterMessage("JOIN", files);
					join(msg, gson.toJson(msg), IPAddres, clientSocket);
					
					break;
				
				case 2:
					tec.nextLine();
					System.out.print("Qual o arquivo a ser pesquisado?: ");
					String arquivo = tec.nextLine();
					LinkedList<String> file_request = new LinkedList<String>();
					file_request.add(arquivo);
					NapsterMessage pesquisa = new NapsterMessage("SEARCH", file_request);
					LinkedList<String> peers_with_file = new LinkedList<String>();
					peers_with_file = search(pesquisa, gson.toJson(pesquisa), IPAddres, clientSocket);
					break;
					
				case 5:
					NapsterMessage msg_leave = new NapsterMessage("LEAVE", null);
					leave(msg_leave, gson.toJson(msg_leave), IPAddres, clientSocket);
					break;
				
			}
		}
		clientSocket.close();
		tec.close();
	}
}