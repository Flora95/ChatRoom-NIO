package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

public class ChatRoomClient {

	private Selector selector = null;
	private SocketChannel sc = null;
	
	private static final String HOSTNAME = "127.0.0.1";
	private static final int PORT = 8216;
	private static final int BUFF_SIZE = 1024;
	private Charset charset = Charset.forName("UTF-8");
	
	public void init() throws IOException {
		selector = Selector.open();
		
		// ���ӷ����
		sc = SocketChannel.open(new InetSocketAddress(HOSTNAME, PORT));
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ);
		
		// ����һ���߳̽��ܷ���˷��ص���Ϣ
		new Thread(new ClientThread()).start();
		
		// ���߳����ڴ�console��ȡ�û����벢���͵������
		Scanner scanner = new Scanner(System.in);
		
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			
			if (line.length() == 0) {
				continue;
			}
			
			sc.write(charset.encode(line));
		}
	}
	
	public static void main(String[] args) throws IOException {
		new ChatRoomClient().init();
	}
	
	class ClientThread implements Runnable {

		@Override
		public void run() {
			while (true) {
				try {
					int ready = selector.select();
					if (ready == 0) {
						continue;
					}
					
					Set<SelectionKey> selectionKeys = selector.selectedKeys();
					Iterator<SelectionKey> iterator = selectionKeys.iterator();
					
					while (iterator.hasNext()) {
						SelectionKey key = (SelectionKey) iterator.next();
						processKey(key);
						iterator.remove();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		private void processKey(SelectionKey key) throws IOException {
			if (key.isReadable()) {
				SocketChannel sc = (SocketChannel) key.channel();
				
				ByteBuffer buffer = ByteBuffer.allocate(BUFF_SIZE);
				StringBuffer msg = new StringBuffer();
				
				while (sc.read(buffer) > 0) {
					buffer.flip();
					msg.append(charset.decode(buffer));
					buffer.clear();
				}
				
				System.out.println(msg);
				
				key.interestOps(SelectionKey.OP_READ);
			}
		}
	}
}
