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
	
	// 记录当前在线用户：用户IP地址-用户名的映射
	private static Map<SocketAddress, String> users = new HashMap<SocketAddress, String>();
	// 用户IP地址-用户上次活跃时间的映射
	private static Map<SocketAddress, Date> activeTime = new HashMap<SocketAddress, Date>();
	
	// 超时处理计时器
	private static final Timer TIMER = new Timer();
	private static final long TIMER_DELAY_MILLIS = 2000;
	private static final long TIMER_PERIOD_MILLIS = 1000;
	private static final long TIMEOUT_MILLIS = 60000;
	
	private void init() throws IOException {
		// 初始化计时器
		TIMER.scheduleAtFixedRate(new TimeoutTask(), TIMER_DELAY_MILLIS, TIMER_PERIOD_MILLIS);
		
		// 初始化服务端channel，开始监听
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
		// 新用户连接
		if (key.isAcceptable()) {
			SocketChannel sc = ssc.accept();
			sc.configureBlocking(false);
			
			// 将新连接注册到selector中，用于读取该用户的后续消息
			sc.register(selector, SelectionKey.OP_READ);
			
			// 重置当前的channel，以便接收其他用户的登录请求
			key.interestOps(SelectionKey.OP_ACCEPT);
			
			System.out.println("Server accepted connection from client: " + sc.getRemoteAddress());
			
			sc.write(charset.encode("Please enter your name: "));
		}
		
		// 已登录用户消息
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
				
				// 重置当前的channel，以便接收该用户的后续消息
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
		
		// 已登录用户，广播消息
		if (users.containsKey(address)) {
			String userName = users.get(address);
			
			String broadcastMsg = userName + ": " + msg.toString();
			broadcastMsg(address, broadcastMsg);
		}
		// 新登录用户
		else {
			// 昵称已存在
			if (users.containsValue(msg.toString())) {
				sc.write(charset.encode(MSG_USER_EXIST));
			}
			// 登录用户，返回欢迎信息，广播上线消息
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
		
		// 退出命令
		if (cmd.equals(CMD_QUIT)) {
			logout(sc.getRemoteAddress());
		}
		// 查询命令
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
			case "天气":
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
			
			// 不回发给已下线用户
			if (!users.containsKey(targetAddress)) {
				continue;
			}
			
			// 不回发广播消息给发送此内容的客户端
			if (address != null && targetAddress.equals(address)) {
				continue;
			}
			
			// 执行转发
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
	 * 超时处理计时器任务类
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
