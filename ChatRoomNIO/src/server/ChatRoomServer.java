package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import server.query.QueryServiceInterface;
import server.query.QueryWeatherService;

public class ChatRoomServer {

	private Selector selector = null;
	
	private static final int PORT = 8216;
	private static final int BUFF_SIZE = 1024;
	private static final String MSG_USER_EXIST = "User name exist! Please try again!";
	private static final String MSG_QUERY_FAIL = "Query failed";
	private static final String CMD_QUIT = "quit";
	private Charset charset = Charset.forName("UTF-8");
	
	// ��¼��ǰ�����û����û�IP��ַ-�û�����ӳ��
	private static Map<SocketAddress, String> users = new HashMap<SocketAddress, String>();
	// �û�IP��ַ-�û��ϴλ�Ծʱ���ӳ��
	private static Map<SocketAddress, Date> activeTime = new HashMap<SocketAddress, Date>();
	
	// ��ʱ�����ʱ��
	private static final Timer TIMER = new Timer();
	private static final long TIMER_DELAY_MILLIS = 2000;
	private static final long TIMER_PERIOD_MILLIS = 1000;
	private static final long TIMEOUT_MILLIS = 60000;
	
	private void init() throws IOException {
		// ��ʼ����ʱ��
		TIMER.scheduleAtFixedRate(new TimeoutTask(), TIMER_DELAY_MILLIS, TIMER_PERIOD_MILLIS);
		
		// ��ʼ�������channel����ʼ����
		selector = Selector.open();
		
		ServerSocketChannel ssc = ServerSocketChannel.open();
		ssc.socket().bind(new InetSocketAddress(PORT));
		ssc.configureBlocking(false);
		
		ssc.register(selector, SelectionKey.OP_ACCEPT);
		
		System.out.println("Server is listening to port " + PORT + " now...");
		
		while (true) {
			int ready = selector.select();
			if (ready == 0) {
				continue;
			}
			
			Set<SelectionKey> selectedKeys = selector.selectedKeys();
			Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
			
			while (keyIterator.hasNext()) {
				SelectionKey key = (SelectionKey) keyIterator.next();
				processKey(ssc, key);
				keyIterator.remove();
			}
		}
	}
	
	private void processKey(ServerSocketChannel ssc, SelectionKey key) throws IOException {
		// ���û�����
		if (key.isAcceptable()) {
			SocketChannel sc = ssc.accept();
			sc.configureBlocking(false);
			
			// ��������ע�ᵽselector�У����ڶ�ȡ���û��ĺ�����Ϣ
			sc.register(selector, SelectionKey.OP_READ);
			
			// ���õ�ǰ��channel���Ա���������û��ĵ�¼����
			key.interestOps(SelectionKey.OP_ACCEPT);
			
			System.out.println("Server accepted connection from client: " + sc.getRemoteAddress());
			
			sc.write(charset.encode("Please enter your name: "));
		}
		
		// �ѵ�¼�û���Ϣ
		else if (key.isReadable()) {
			SocketChannel sc = (SocketChannel) key.channel();
			
			ByteBuffer buffer = ByteBuffer.allocate(BUFF_SIZE);
			StringBuffer msg = new StringBuffer();
			
			try {
				while (sc.read(buffer) > 0) {
					buffer.flip();
					msg.append(charset.decode(buffer));
					buffer.clear();
				}
				
				// ���õ�ǰ��channel���Ա���ո��û��ĺ�����Ϣ
				key.interestOps(SelectionKey.OP_READ);
				
				if (msg.length() <= 0) {
					return;
				}
				
				System.out.println("Server received message from client " + sc.getRemoteAddress() + ": [" + msg + "]");
			} catch (IOException e) {
				key.cancel();
				
				if (key.channel() != null) {
					key.channel().close();
				}
			}
			
			processMsg(sc, msg);
		}
	}

	private void processMsg(SocketChannel sc, StringBuffer msg) throws IOException {
		if (msg == null || msg.length() <= 0) {
			return;
		}
		
		SocketAddress address = sc.getRemoteAddress();
		activeTime.put(address, new Date());
		
		if (msg.charAt(0) == '/' && msg.length() > 1) {
			processCmd(sc, msg.substring(1));
			return;
		}
		
		// �ѵ�¼�û����㲥��Ϣ
		if (users.containsKey(address)) {
			String userName = users.get(address);
			
			String broadcastMsg = userName + ": " + msg.toString();
			broadcastMsg(address, broadcastMsg);
		}
		// �µ�¼�û�
		else {
			// �ǳ��Ѵ���
			if (users.containsValue(msg.toString())) {
				sc.write(charset.encode(MSG_USER_EXIST));
			}
			// ��¼�û������ػ�ӭ��Ϣ���㲥������Ϣ
			else {
				users.put(address, msg.toString());
				
				String welcomeMsg = "Welcome to the chat room, " + msg + "! Current online user number is: " + users.size();
				sc.write(charset.encode(welcomeMsg));
				
				broadcastMsg(address, "User " + msg + " online!");
			}
		}
	}

	private void processCmd(SocketChannel sc, String cmd) throws IOException {
		if (cmd.length() == 0) {
			return;
		}
		
		// �˳�����
		if (cmd.equals(CMD_QUIT)) {
			logout(sc.getRemoteAddress());
		}
		// ��ѯ����
		else {
			String[] tokens = cmd.split(" ");
			
			String queryType = tokens[0];
			String[] paras = new String[tokens.length - 1];
			for (int i = 1; i < tokens.length; i++) {
				paras[i - 1] = tokens[i];
			}
			
			QueryServiceInterface queryService;
			
			String result = MSG_QUERY_FAIL;
			switch (queryType) {
			case "����":
				queryService = new QueryWeatherService();
				result = queryService.query(paras);
				break;
			}
			
			sc.write(charset.encode(result));
		}
	}

	private void broadcastMsg(SocketAddress address, String msg) throws IOException {
		for (SelectionKey key : selector.keys()) {
			if (!(key.channel() instanceof SocketChannel)) {
				continue;
			}
			
			SocketChannel targetChannel = (SocketChannel) key.channel();
			SocketAddress targetAddress = targetChannel.getRemoteAddress();
			
			// ���ط����������û�
			if (!users.containsKey(targetAddress)) {
				continue;
			}
			
			// ���ط��㲥��Ϣ�����ʹ����ݵĿͻ���
			if (address != null && targetAddress.equals(address)) {
				continue;
			}
			
			// ִ��ת��
			targetChannel.write(charset.encode(msg));
		}
	}
	
	private void logout(SocketAddress address) throws IOException {
		String msg = "User " + users.get(address) + " offline!";
		broadcastMsg(address, msg);
		users.remove(address);
		
		System.out.println(msg);
	}

	public static void main(String[] args) throws IOException {
		new ChatRoomServer().init();
	}
	
	/**
	 * ��ʱ�����ʱ��������
	 * @author Flora95
	 *
	 */
	class TimeoutTask extends TimerTask {

		@Override
		public void run() {
			Date now = new Date();
			
			Iterator<Entry<SocketAddress, Date>> iterator = activeTime.entrySet().iterator();
			while (iterator.hasNext()) {
				Entry<SocketAddress, Date> entry = iterator.next();
				
				Date activeDate = entry.getValue();
				if (now.getTime() - activeDate.getTime() >= TIMEOUT_MILLIS) {
					try {
						logout(entry.getKey());
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					
					iterator.remove();
				}
			}
		}
	}
}
