package lab2_unicast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class Server extends Thread{
	
	private int server_port = 5000;
	private ServerSocket ss = null;
	private ArrayList<Thread> threads;
	private String filename = null;
	private int filelen = 0;
	private byte[] content;
	
	public final static int BYTE_IN_INT = 4;

	public static void main(String[] args) {
		Server server = null; 
		if(args.length!=1 && args.length!=2) {
			System.out.println("Usage: Server <filename> [<port>]");
		}
		
		try {
			if(args.length==2) {
				server = new Server(args[0], Integer.valueOf(args[1])); // set filename and port				
			}
			else {
				server = new Server(args[0]); // set filename
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		server.start();
	}
	
	public Server(String filename) throws IOException {
		init(filename);
	}
	
	public Server(String filename, int port) throws IOException {
		this.server_port = port;
		init(filename);
	}
	
	private void init(String filename) throws IOException {
		this.filename = filename;
		ss = new ServerSocket();
		ss.setReuseAddress(true);
		ss.bind(new InetSocketAddress(server_port));
		threads = new ArrayList<Thread>();
		/* alloc buffer and load file contents */
		File file = new File(filename);
		filelen = (int)file.length(); // only allow file size < INT_MAX byte
		content = new byte[filelen];
		FileInputStream fis = new FileInputStream(file);
		fis.read(content); // load content
		fis.close();
	}
	
	public void run() {
		while(true)
		{
			try {
				Socket cs = ss.accept();
				Thread t = new Thread(new Runnable() {
					
					@Override
					public void run() {
						try {
							System.out.println("Send "+filename+" to "+cs.getInetAddress().toString()+":"+cs.getPort());
							OutputStream os = cs.getOutputStream();
							ByteBuffer bb = ByteBuffer.allocate(BYTE_IN_INT);
							bb.putInt(filelen);
							os.write(bb.array());
							os.write(content);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				});
				t.start();
				t.join();
				threads.add(t);
			} catch (Exception e) {
				// TODO: handle exception
			}
		}
	}
	
	public void setPort(int port) {
		this.server_port = port;
	}
	
}
