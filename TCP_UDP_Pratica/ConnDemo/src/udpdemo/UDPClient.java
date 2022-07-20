package udpdemo;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPClient {
	public static void main(String[] args) throws Exception {
		
		DatagramSocket clientSocket = new DatagramSocket();
		
		InetAddress IPAddres = InetAddress.getByName("127.0.0.1");
		
		byte[] sendData = new byte[1024];
		sendData = "sou um cliente".getBytes();
		
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddres, 9876);
		
		System.out.println("Mensagem enviada!");
		clientSocket.send(sendPacket);
		
		byte[] recBuffer = new byte[1024];	
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		
		clientSocket.receive(recPkt); // BLOCKING
		
		String informacao = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
		System.out.println("Recebido do servidor: " + informacao);
		
		clientSocket.close();
	}
}
