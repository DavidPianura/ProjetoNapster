package napster;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

public class NapsterPeer {
	
	public static int rngNumber(int max, int min) {
		int rng = (int) (Math.random() * (max - min + 1) + min);
		return rng;
	}
	
	// Definindo uma porta para o Peer
	static int myPort = rngNumber(9000, 8000);
	
	
	public static class ThreadDownloader extends Thread {
		private LinkedList<String> peers_with_file, file_request;
		
		public ThreadDownloader (LinkedList<String> peersFiles, LinkedList<String> file) {
			this.peers_with_file = peersFiles;
			this.file_request = file;
		}
		
		public void run() {
			
			boolean dAccept = false;
			NapsterMessage dRequest = new NapsterMessage("DOWNLOAD", this.file_request, myPort);
			Gson gson = new Gson();
			int rejeitados = 0;
			
			while (!dAccept) {
				try {
					
					// Se todos negaram, aguarda 5 segundos e faz a requisicao novamente
					if (rejeitados == this.peers_with_file.size()) {
						TimeUnit.SECONDS.sleep(5);
					}
					
					for (int i = 0; i < peers_with_file.size(); i++) {
						
						// Enviando requisicao de download para peers que possuem o arquivo
						String anotherPeerIP = this.peers_with_file.get(i).split(":")[0];
						int anotherPeerPort = Integer.parseInt(this.peers_with_file.get(i).split(":")[1]);
						Socket s = new Socket(anotherPeerIP, anotherPeerPort);
						
						OutputStream os = s.getOutputStream();
						DataOutputStream writer = new DataOutputStream(os);
						
						InputStreamReader is = new InputStreamReader(s.getInputStream());
						BufferedReader reader = new BufferedReader(is);
						
						writer.writeBytes(gson.toJson(dRequest) + "\n");
						
						NapsterMessage response = gson.fromJson(reader.readLine(), NapsterMessage.class);
						
						if (response.getRequest().equals("DOWNLOAD_NEGADO")) {
							
							if (i < peers_with_file.size()-1) {
								System.out.println("Peer " + peers_with_file.get(i) + " negou o download, pedindo agora para o peer " + peers_with_file.get(i+1));
							} else {
								System.out.println("Peer " + peers_with_file.get(i) + " negou o download, pedindo agora para o peer " + peers_with_file.get(0));
							}
							
							// s.close();
							rejeitados++;
							
						} else {
							dAccept = true;
							// Chamar metodo de download
							break;
						}
					}
			} catch (Exception e) {
				
			}
			}
		} 
		
	}
	
	// Thread para ficar escutando na porta por requisicoes de download de outros peers
	public static class ThreadDownloadListener extends Thread {
		
		private LinkedList<String> myFiles;
		private String diretorio;
		
		public ThreadDownloadListener(LinkedList<String> myFiles, String folder) {
			this.myFiles = myFiles;
			this.diretorio = folder;
		}
		
		public void run() {
			try {
				
				ServerSocket serverSocket = new ServerSocket(myPort);
				
				while (true) {
					Socket no = serverSocket.accept(); // BLOCKING
					ThreadDownloadResponse responser = new ThreadDownloadResponse(this.myFiles, no, this.diretorio);
					responser.start();
				}
				
			} catch (Exception e) {
				
			}
		}
	}
	
	// Thread que a ThreadDownloadListener vai chamar quando chegar uma requisicao de download
	public static class ThreadDownloadResponse extends Thread {
		
		private LinkedList<String> file_request, myFiles;
		private Socket no;
		private String diretorio;
		
		public ThreadDownloadResponse(LinkedList<String> myFiles, Socket node, String folder) {
			this.myFiles = myFiles;
			this.no = node;
			this.diretorio = folder;
		}
		
		public void sendResponse (Socket no, String response) throws Exception {
			
			OutputStream os = no.getOutputStream();
			DataOutputStream writer = new DataOutputStream(os);
			
			writer.writeBytes(response + "\n");
		}
		
		// Pega qual o arquivo o peer quer baixar
		public void getPeerRequest(Socket no) throws Exception{
			
			Gson gson = new Gson();
			InputStreamReader is = new InputStreamReader(no.getInputStream());
			BufferedReader reader = new BufferedReader(is);
			
			String texto = reader.readLine();
			NapsterMessage request = gson.fromJson(texto, NapsterMessage.class);
			
			this.file_request = request.getProperties();
		}
		
		public void sendFile() throws Exception {
			
		    String FILE_TO_SEND = this.diretorio + "\\" + this.file_request.get(0);
		    
		    DataOutputStream dataOutputStream = null;
		    DataInputStream dataInputStream = null;
		    
			try {
		   
		        int bytes = 0;
		        File file = new File(FILE_TO_SEND);
		        FileInputStream fileInputStream = new FileInputStream(file);
	            dataInputStream = new DataInputStream(this.no.getInputStream());
	            dataOutputStream = new DataOutputStream(this.no.getOutputStream());
		        
		        // send file size
		        dataOutputStream.writeLong(file.length());  
		        // break file into chunks
		        byte[] buffer = new byte[4*1024];
		        while ((bytes=fileInputStream.read(buffer))!=-1){
		            dataOutputStream.write(buffer,0,bytes);
		            dataOutputStream.flush();
		        }
				
		        fileInputStream.close();
		        
			}catch (Exception e) {
				
			} finally { 
		         if (no !=null) no.close();
		         if (dataInputStream !=null) dataInputStream.close();
			}
		}
		
		public void run() {
			try {
				
				int rand;
				Gson gson = new Gson();
				
				// Socket no = serverSocket.accept(); // BLOCKING
				getPeerRequest(no);
				
				// Checa se o Peer possui o arquivo requerido para download
				if (this.myFiles.contains(this.file_request.get(0))) {
								
					rand = rngNumber(1, 10);
					
					// Aceita ou rejeita o Download de forma aleatória
					// Nesse caso, se o numero aleatorio for impar, a solicitacao de download eh rejeitada
					if (rand % 2 != 0) {
							
						NapsterMessage downloadNegado = new NapsterMessage("DOWNLOAD_NEGADO", this.file_request, myPort);
						sendResponse(no, gson.toJson(downloadNegado));
					
					} else {
						// DOWNLOAD ACEITO
						File myFile = new File (this.diretorio + "\\" + this.file_request.get(0));
						int tamanho = (int)myFile.length();

						NapsterMessage downloadAceito = new NapsterMessage("DOWNLOAD_ACEITO", this.file_request, tamanho);
						
						// Avisa o Peer que o Download foi aceito
						sendResponse(no, gson.toJson(downloadAceito));
						
						sendFile();
					}
						
				} else {
						
					NapsterMessage downloadNegado = new NapsterMessage("DOWNLOAD_NEGADO", this.file_request, myPort);
					sendResponse(no, gson.toJson(downloadNegado));
					
				}
				
			} catch (Exception e) {
				
			}
		}
	}
	
	// Metodo para baixar o arquivo. A Str FILE eh o caminho completo do arquivo
	public static void downloadFile(String file, Socket sock) throws Exception{
		
	    int bytesRead;

	    DataOutputStream dataOutputStream = null;
	    DataInputStream dataInputStream = null;

		try {
			
            dataInputStream = new DataInputStream(sock.getInputStream());
            dataOutputStream = new DataOutputStream(sock.getOutputStream());
			
			int bytes = 0;
	        FileOutputStream fileOutputStream = new FileOutputStream(file);
	        System.out.println("ENTREI NA FUNCAO DE DOWNLOAD");
	        long size = dataInputStream.readLong();         // Le o tamanho do arquivo
	        System.out.println("SE LIGA NO TAMANHO: " + size);
	        byte[] buffer = new byte[4*1024];
	        while (size > 0 && (bytes = dataInputStream.read(buffer, 0, (int)Math.min(buffer.length, size))) != -1) {
	            fileOutputStream.write(buffer,0,bytes);
	            size -= bytes;     
	        }
	        fileOutputStream.close();
			
		} catch (Exception e) {
			
		} finally {

		     if (sock != null) sock.close();
		}
	}
	
	// Metodo para fazer a Request de Download aos peers que possuem o arquivo desejado
	public static void downloadRequest(LinkedList<String> peers_with_file, LinkedList<String> file_request, String folder) {
		boolean dAccept = false;
		NapsterMessage dRequest = new NapsterMessage("DOWNLOAD", file_request, myPort);
		Gson gson = new Gson();
		int rejeitados = 0;
		
		while (!dAccept) {
			try {
				
				// Se todos negaram, aguarda 5 segundos e faz a requisicao novamente
				if (rejeitados == peers_with_file.size()) {
					TimeUnit.SECONDS.sleep(5);
				}
				
				for (int i = 0; i < peers_with_file.size(); i++) {
					
					// Enviando requisicao de download para peers que possuem o arquivo
					String anotherPeerIP = peers_with_file.get(i).split(":")[0];
					int anotherPeerPort = Integer.parseInt(peers_with_file.get(i).split(":")[1]);
					
					Socket s = new Socket(anotherPeerIP, anotherPeerPort);
					
					OutputStream os = s.getOutputStream();
					DataOutputStream writer = new DataOutputStream(os);
					
					InputStreamReader is = new InputStreamReader(s.getInputStream());
					BufferedReader reader = new BufferedReader(is);
					
					writer.writeBytes(gson.toJson(dRequest) + "\n");
					
					String resposta = reader.readLine(); // BLOCKING
					NapsterMessage response = gson.fromJson(resposta, NapsterMessage.class);
					
					if (response.getRequest().equals("DOWNLOAD_NEGADO")) {
						
						if (i < peers_with_file.size()-1) {
							System.out.println("Peer " + peers_with_file.get(i) + " negou o download, pedindo agora para o peer " + peers_with_file.get(i+1));
						} else {
							System.out.println("Peer " + peers_with_file.get(i) + " negou o download, pedindo agora para o peer " + peers_with_file.get(0));
						}
						
						s.close();
						rejeitados++;
						
					} else {
						dAccept = true;
						System.out.println("DOWNLOAD ACEITO!");
						// Chamar metodo de download
						downloadFile(folder + "\\" + file_request.get(0), s);
						break;
					}
				}
		} catch (Exception e) {
			
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
		System.out.println("Sou o peer " + peer_ip.getLoopbackAddress().getHostAddress() + ":" + myPort + " com arquivos " + msg.getProperties());
		
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
	
	// Metodo para criar a lista de arquivos do Peer
	public static LinkedList<String> getInfosFolder(String folder) {
		
		File diretorio = new File(folder);
		LinkedList<String> arquivos = new LinkedList<String>();
		
		for (File file:diretorio.listFiles()) {
			if (!file.isDirectory()) arquivos.add(file.getName());
		}
		return arquivos;
		
	}
	
	public static void main(String[] args) throws Exception{
		
		Gson gson = new Gson();
		DatagramSocket clientSocket = new DatagramSocket();
		
		InetAddress IPAddres = InetAddress.getByName("127.0.0.1");
		
		boolean l = false;
		Scanner tec = new Scanner (System.in);
		
		LinkedList<String> file_request = new LinkedList<String>();
		LinkedList<String> peers_with_file = new LinkedList<String>();
		String folder = "";
		
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
					System.out.print("Escreva o caminho completo da pasta: ");
					tec.nextLine();
					folder = tec.nextLine();
					LinkedList<String> files = new LinkedList<>();
					files = getInfosFolder(folder);
					NapsterMessage msg = new NapsterMessage("JOIN", files, myPort);
					join(msg, gson.toJson(msg), IPAddres, clientSocket);
					
					// Assim que da join no servidor e define a sua pasta, a Thread para responder
					// requisicoes de outros peers eh iniciada
					ThreadDownloadListener listener = new ThreadDownloadListener(files, folder);
					listener.start();
				
					break;
				
				case 2:
					tec.nextLine();
					System.out.print("Qual o arquivo a ser pesquisado?: ");
					String arquivo = tec.nextLine();
					
					if (file_request.isEmpty()) {
						// nothing
					} else {
						file_request.remove(0);
					}

					file_request.add(arquivo);
					NapsterMessage pesquisa = new NapsterMessage("SEARCH", file_request, myPort);
					peers_with_file = search(pesquisa, gson.toJson(pesquisa), IPAddres, clientSocket);
					break;
				
				case 3:
					if (peers_with_file.isEmpty()) {
						System.out.println("Realize primeiro uma acao de SEARCH para buscar o arquivo");
					} else {
						downloadRequest(peers_with_file, file_request, folder);
					}
					break;
				case 5:
					NapsterMessage msg_leave = new NapsterMessage("LEAVE", null, myPort);
					leave(msg_leave, gson.toJson(msg_leave), IPAddres, clientSocket);
					break;
				
			}
		}
		clientSocket.close();
		tec.close();
	}
}
