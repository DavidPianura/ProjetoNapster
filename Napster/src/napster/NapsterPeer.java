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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
	
	// Variavel para armazenar qual a porta onde o Socket da Thread para responder as requisicoes de ALIVE estara
	static int alivePort = rngNumber(7000, 6000);
	
	// Thread para responder a requisicao ALIVE do server
	public static class ThreadAlive extends Thread {
		
		private DatagramSocket clientSocket;
		private InetAddress IPAddres;
		
		public ThreadAlive(InetAddress IPAddres, DatagramSocket aliveSocket) throws SocketException {
			
			/*
			Assim que a Thread para responder requisicoes de ALIVE eh iniciada, a variavel global alivePort
			recebe o valor do socket exclusivo para responder essas solicitacoes
			*/
			this.clientSocket = aliveSocket;
			this.IPAddres = IPAddres;

		}
		
		public void run() {
	
				Gson gson = new Gson();
				byte[] sendBuf = new byte[1024];
				
				// LinkedList<String> udpPort = new LinkedList<String>();
				
				// Envia a porta que o DatagramSocket original esta enviando as mensagens
				// udpPort.add(Integer.toString(this.port_udp));
				
				NapsterMessage aliveOK = new NapsterMessage("ALIVE_OK", null, myPort, alivePort);
				sendBuf = gson.toJson(aliveOK).getBytes();
				
				DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, IPAddres, 10098);
				
				byte[] recBuffer = new byte[1024];
				DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
				
				while (true) {
					try {
	
						// Aguardando o ALIVE do servidor
						this.clientSocket.receive(recPkt); // BLOCKING
	
						String resposta = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
						NapsterMessage aliveRequest = gson.fromJson(resposta, NapsterMessage.class);
						
						if (aliveRequest.getRequest().equals("ALIVE")) {
							clientSocket.send(sendPacket);

						} else {
							recBuffer = new byte[1024];
							recPkt = new DatagramPacket(recBuffer, recBuffer.length);
							continue;
						}
						
					} catch (Exception e) {
						// TODO: handle exception
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
					
					// Aceita ou rejeita o Download de forma aleatï¿½ria
					// Nesse caso, se o numero aleatorio for impar, a solicitacao de download eh rejeitada
					if (rand % 2 != 0) {
							
						NapsterMessage downloadNegado = new NapsterMessage("DOWNLOAD_NEGADO", this.file_request, myPort, alivePort);
						sendResponse(no, gson.toJson(downloadNegado));
					
					} else {
						// DOWNLOAD ACEITO
						File myFile = new File (this.diretorio + "\\" + this.file_request.get(0));
						int tamanho = (int)myFile.length();

						NapsterMessage downloadAceito = new NapsterMessage("DOWNLOAD_ACEITO", this.file_request, myPort, alivePort);
						
						// Avisa o Peer que o Download foi aceito
						sendResponse(no, gson.toJson(downloadAceito));
						
						sendFile();
					}
						
				} else {
						
					NapsterMessage downloadNegado = new NapsterMessage("DOWNLOAD_NEGADO", this.file_request, myPort, alivePort);
					sendResponse(no, gson.toJson(downloadNegado));
					
				}
				
			} catch (Exception e) {
				
			}
		}
	}
	
	// Metodo para baixar o arquivo. O path para o arquivo sera a juncao das Strings file e folder
	public static void downloadFile(String file, String folder, Socket sock) throws Exception{
		
	    int bytesRead;
	    
	    DataOutputStream dataOutputStream = null;
	    DataInputStream dataInputStream = null;
	    
	    String fName = folder + "\\" + file;
	    
		try {
			
            dataInputStream = new DataInputStream(sock.getInputStream());
            dataOutputStream = new DataOutputStream(sock.getOutputStream());
			
			int bytes = 0;
	        FileOutputStream fileOutputStream = new FileOutputStream(fName);
	        long size = dataInputStream.readLong();         // Le o tamanho do arquivo
	        byte[] buffer = new byte[4*1024];
	        while (size > 0 && (bytes = dataInputStream.read(buffer, 0, (int)Math.min(buffer.length, size))) != -1) {
	            fileOutputStream.write(buffer,0,bytes);
	            size -= bytes;     
	        }
	        fileOutputStream.close();
			
		} catch (Exception e) {
			
		} finally {
			System.out.println("Arquivo " + file + " baixado com sucesso na pasta " + folder);
		    if (sock != null) sock.close();
		}
	}
	
	// Metodo para fazer a Request de Download aos peers que possuem o arquivo desejado
	public static void downloadRequest(LinkedList<String> peers_with_file, LinkedList<String> file_request, String folder) {
		boolean dAccept = false;
		NapsterMessage dRequest = new NapsterMessage("DOWNLOAD", file_request, myPort, alivePort);
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
						downloadFile(file_request.get(0), folder, s);
						break;
					}
				}
		} catch (Exception e) {
			
		}
		}
	}
	
	// Funcao para dar Join ao servidor
	public static void join(NapsterMessage msg, String req, InetAddress IPAddres, DatagramSocket clientSocket) throws IOException {
		boolean received = false;
		
		byte[] sendData_join = new byte[1024];
		Gson gson = new Gson();
		
		sendData_join = req.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData_join, sendData_join.length, IPAddres, 10098);
		clientSocket.send(sendPacket);
		
		byte[] recBuffer = new byte[1024];	
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		
		// Tenta receber a mensagem do servidor, caso o Socket chegue no timeout definido, a mensagem é reenviada
		while (!received) {
			try {
				clientSocket.receive(recPkt);
			    String informacao = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
			    NapsterMessage joinOK = gson.fromJson(informacao, NapsterMessage.class);
			    
			    if (joinOK.getRequest().equals("JOIN_OK")) {
					// Printando infos quando JOIN_OK eh recebido
					InetAddress peer_ip = InetAddress.getLocalHost();
					System.out.println("Sou o peer " + peer_ip.getLoopbackAddress().getHostAddress() + ":" + myPort + " com arquivos " + msg.getProperties());
			    	received = true; 
			    }  else {
					recBuffer = new byte[1024];	
					recPkt = new DatagramPacket(recBuffer, recBuffer.length);
					continue;
			    }
			    
			} catch (SocketTimeoutException e) {
				// tentando reenviar a mensagem
				recBuffer = new byte[1024];	
				recPkt = new DatagramPacket(recBuffer, recBuffer.length);
				clientSocket.send(sendPacket);
				continue;
			}
		}

	}
	
	// funcao para envio do leave
	public static void leave(NapsterMessage msg_leave, String req, InetAddress IPAddres, DatagramSocket clientSocket) throws IOException {
		
		boolean received = false;
		
		byte[] sendData_leave = new byte[1024];
		Gson gson = new Gson();
		
		sendData_leave = req.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData_leave, sendData_leave.length, IPAddres, 10098);
		
		byte[] recBuffer = new byte[1024];	
		DatagramPacket recPkt_leave = new DatagramPacket(recBuffer, recBuffer.length);
		
		clientSocket.send(sendPacket);
		
		// Tenta receber a mensagem de Leave. Caso nao receba ate o timeout do Socket, reenvia a mensagem
		while (!received) {
			try {
				clientSocket.receive(recPkt_leave);
				
				String informacao = new String(recPkt_leave.getData(), recPkt_leave.getOffset(), recPkt_leave.getLength());
				NapsterMessage leaveOK = gson.fromJson(informacao, NapsterMessage.class);
				
				if (leaveOK.getRequest().equals("LEAVE_OK")) {
					received = true;
					System.out.println("SAINDO...");
				}
				else {
					recBuffer = new byte[1024];	
					recPkt_leave = new DatagramPacket(recBuffer, recBuffer.length);
					continue;
				}
					
			} catch (SocketTimeoutException e) {
				recBuffer = new byte[1024];	
				recPkt_leave = new DatagramPacket(recBuffer, recBuffer.length);
				clientSocket.send(sendPacket);
				continue;
			}
		}

	}
	
	public static LinkedList<String> search(NapsterMessage msg, String req, InetAddress IPAddres, DatagramSocket clientSocket) throws IOException {
		
		boolean received = false;
		
		Gson gson = new Gson();
		byte[] sendData = new byte[1024];
		sendData = req.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddres, 10098);
		
		LinkedList<String> peers_with_file = new LinkedList<String>();
		
		byte[] recBuffer = new byte[1024];	
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		
		clientSocket.send(sendPacket);

		// Tenta receber o SEARCH_OK. Caso não receba, tenta reenviar novamente
		while (!received) {
			try {
				clientSocket.receive(recPkt);
	
				String informacao = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
				NapsterMessage searchOK = gson.fromJson(informacao, NapsterMessage.class);
				
				if (searchOK.getRequest().equals("SEARCH_OK")) {
	
					for (int i = 0; i < searchOK.getProperties().size(); i++) 
						peers_with_file.add(searchOK.getProperties().get(i));
					
					System.out.println("peers com arquivo solicitado: " + peers_with_file);
					received = true;
					
				} else {
					recBuffer = new byte[1024];	
					recPkt = new DatagramPacket(recBuffer, recBuffer.length);
					clientSocket.send(sendPacket);
					continue;
				}
			
			} catch (SocketTimeoutException e) {
				recBuffer = new byte[1024];	
				recPkt = new DatagramPacket(recBuffer, recBuffer.length);
				clientSocket.send(sendPacket);
				continue;
			}
		}

		return peers_with_file;		
	}
	
	public static void update (NapsterMessage msg, String req, InetAddress IPAddres, DatagramSocket clientSocket) throws IOException {
		
		boolean received = false;
		byte[] sendData_update = new byte[1024];
		
		Gson gson = new Gson();
		
		sendData_update = req.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData_update, sendData_update.length, IPAddres, 10098);
		
		byte[] recBuffer = new byte[1024];	
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		
		clientSocket.send(sendPacket);
		
		while (!received) {
			try {
				clientSocket.receive(recPkt);
				
				String informacao = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
				NapsterMessage updateOK = gson.fromJson(informacao, NapsterMessage.class);
				
				if (updateOK.getRequest().equals("UPDATE_OK")) received = true;
			
			} catch (SocketTimeoutException e) {
				recBuffer = new byte[1024];	
				recPkt = new DatagramPacket(recBuffer, recBuffer.length);
				clientSocket.send(sendPacket);
				continue;
			}	
		}

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
		DatagramSocket aliveSocket = new DatagramSocket(null);
		
		
		// Variaveis de controle para o menu interativo
		boolean joinDone = false;

		// Tempo de espera por uma resposta do servidor = 30 segundos. Caso exceda esse tempo, uma nova requisicao eh enviada
		clientSocket.setSoTimeout(15000);
		
		InetAddress IPAddres = InetAddress.getByName("127.0.0.1");

		SocketAddress sockaddr = new InetSocketAddress(IPAddres, alivePort);  
		aliveSocket.bind(sockaddr);
		
		boolean l = false;
		Scanner tec = new Scanner (System.in);
		
		LinkedList<String> file_request = new LinkedList<String>();
		LinkedList<String> peers_with_file = new LinkedList<String>();
		LinkedList<String> files = new LinkedList<>();
		
		String folder = "";
		
		ThreadAlive alive = new ThreadAlive(IPAddres, aliveSocket);
		
		// MENU INTERATIVO
		System.out.println("----------------------------------------------");
		System.out.println("              Projeto Napster                 ");
		System.out.println("----------------------------------------------\n");
		
		System.out.println("Nome: David Pianura da Silva");
		System.out.println("RA: 11201810096");
		System.out.println("Professor: Vladimir Emiliano Moreira Rocha\n");
		
		System.out.println("Para escolher uma funcao, digite o numero dela e tecle enter:");
		System.out.println("1. JOIN");
		System.out.println("2. SEARCH");
		System.out.println("3. DOWNLOAD");
		System.out.println("4. UPDATE");
		System.out.println("5. LEAVE");
		
		
		while (!l) {
			
			int option = 0;
			
			while (option <= 0 || option > 5) {
				System.out.print("\nOpcao escolhida: ");
				option = tec.nextInt();
				
				if (option <= 0 || option > 5) {
					System.out.println("Opcao invalida.");
				}
			}
			
			switch(option) {
				case 1:
					
					if (joinDone) {
						System.out.println("Join ja realizado!");
						continue;
					}
					
					System.out.print("Escreva o caminho completo da pasta (e.g.: C:\\Videos\\PeerA): ");
					tec.nextLine();
					
					folder = tec.nextLine();
					files = getInfosFolder(folder);
					
					NapsterMessage msg = new NapsterMessage("JOIN", files, myPort, alivePort);
					
					ThreadDownloadListener listener = new ThreadDownloadListener(files, folder);

					join(msg, gson.toJson(msg), IPAddres, clientSocket);
					
					// Assim que da join no servidor e define a sua pasta, a Thread para responder requisicoes de outros peers eh iniciada
					// assim tambem como a thread para responder as requisicoes de ALIVE oriundas do servidor
					listener.start();
					alive.start();
					
					joinDone = true;
					
					break;
				
				case 2:
					
					if (!joinDone) {
						System.out.println("Realize o Join primeiramente!");
						continue;
					}
					
					tec.nextLine();
					System.out.print("Qual o arquivo a ser pesquisado?: ");
					String arquivo = tec.nextLine();
					
					if (file_request.isEmpty()) {
						// nothing
					} else {
						file_request.remove(0);
					}

					file_request.add(arquivo);
					NapsterMessage pesquisa = new NapsterMessage("SEARCH", file_request, myPort, alivePort);
					peers_with_file = search(pesquisa, gson.toJson(pesquisa), IPAddres, clientSocket);
					break;
				
				case 3:
					if (!joinDone) {
						System.out.println("Realize o Join primeiramente!");
						continue;
					}
					if (peers_with_file.isEmpty()) {
						System.out.println("Realize primeiro uma acao de SEARCH para buscar o arquivo");
					} else {
						downloadRequest(peers_with_file, file_request, folder);
					}
					break;
					
				case 4:
					if (!joinDone) {
						System.out.println("Realize o Join primeiramente!");
						continue;
					}
					files.clear();
					files = getInfosFolder(folder);
					NapsterMessage msgUpdate = new NapsterMessage("UPDATE", files, myPort, alivePort);
					update(msgUpdate, gson.toJson(msgUpdate), IPAddres, clientSocket);
					
					break;
				case 5:
					if (!joinDone) {
						l = true;
						break;
					}
					
					NapsterMessage msg_leave = new NapsterMessage("LEAVE", null, myPort, alivePort);
					leave(msg_leave, gson.toJson(msg_leave), IPAddres, clientSocket);
					l = true;
					System.exit(0);
					break;
			}
		}
		clientSocket.close();
		tec.close();
	}
}
