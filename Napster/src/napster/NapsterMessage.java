package napster;

import java.util.LinkedList;

/* INFORMACOES SOBRE O OBJETO MENSAGEM
 * 
 * A classe NapsterMessage representa as mensagens que ser�o enviadas. O atributo request armazena qual a requisicao
 * que esta sendo feita, a lista ligada properties eh utilizada para armazenar detalhes da solicitacao, i.e., arquivos
 * do peer, qual arquivo ele quer baixar, etc.
 * O atributo myPort armazena a porta onde o peer realizara a conexao via TCP para disponibilizar seus arquivos a outro
 * peer. O atributo alivePort indica a porta onde a Thread para responder requisicoes de alive esta escutando
 * 
 */

public class NapsterMessage {
	
	private String request;
	private LinkedList<String> properties;
	private int myPort;
	private int alivePort;
	
	public NapsterMessage(String request, LinkedList<String> properties, int port, int aPort) {
		this.request = request;
		this.properties = properties;
		this.myPort = port;
		this.alivePort = aPort;
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
	
	public int getAlivePort() {
		return alivePort;
	}
	
}
