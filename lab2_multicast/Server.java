package lab2_multicast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.backblaze.erasure.ReedSolomon;

public class Server extends Thread{

	/* global variable */
	private DatagramSocket sock = null;
	private byte[] buf;
	private String filepath = null;
	private int idcnt = 0;
	private byte[][] shards;
	private double lossRate = 0;

	/* constant values */
	public static final int DATA_SIZE = 21;
	public static final int BUFFER_SIZE = DATA_SIZE+4;
	public final static int CLIENT_PORT = 5000;
	public final static int SERVER_PORT = 3000;
	public final static String MULTI_ADDRESS = "224.0.0.1";
	public final static int EOF_TIMES = 3;
	
	public static final int DATA_SHARDS = 4;
    public static final int PARITY_SHARDS = 2;
    public static final int TOTAL_SHARDS = 6;
    
    public static final int BYTES_IN_INT = 4;
    
	private ReedSolomon reedSolomon;
	
	public static void main(String[] args) {
		Server server = null;
		
		/* check arguments */
		if(args.length!=1 && args.length!=2) {
			System.out.println("Usage: Server <FilePath> [<packet loss rate>]");
			System.exit(0);
		}
		else if(args.length == 1) {
			server = new Server();			
		}
		else {
			server = new Server(args[1]);
		}
		
		/* server start */
		server.filepath = args[0]; // set filepath
		server.start();
  	}
	
	public Server() {
		/* some init */
		buf = new byte[DATA_SIZE];
		shards = new byte[TOTAL_SHARDS][DATA_SIZE];
		reedSolomon = new ReedSolomon(DATA_SHARDS, PARITY_SHARDS);
		lossRate = 0;
		idcnt = 0;
	}
	
	public Server(String rate) {
		this();
		lossRate = Double.valueOf(rate);
	}
	
	public void run() {
		System.out.println("Server start"); // debug
		
		try {
			sock = new DatagramSocket(SERVER_PORT); // configure server port
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		/* read file and prepare packet */
		File file = new File(filepath);
		FileInputStream in;
		int sendCounter = 0;
		try {
			in = new FileInputStream(file);
			InetAddress address = null;
			address = InetAddress.getByName(MULTI_ADDRESS); // get address
			
			int n; // read_size = n
			int rest = 0;
			while((n=in.read(buf))!=-1) { // read data into buffer
				rest = n;
				/* put id and data in to buffer */
				ByteBuffer b = ByteBuffer.allocate(BUFFER_SIZE); // alloc id sizeof(int) + data size 
				b.putInt(idcnt++); // put id and data
				b.put(buf);
				
				int id = idcnt-1;
				/* debug => show buffer content */
//				b.rewind();
//				int pkt_id = b.getInt();
//				byte[] output = new byte[n];
//				b.get(output);
//				System.out.println("id: " + pkt_id + ",  size: " + n + "\ndata:\n" + new String(output));
				
				
				/* prepare packet */
				byte[] pack = b.array();
				DatagramPacket pkt_send = null;
				pkt_send = new DatagramPacket(pack, n+BYTES_IN_INT, address, CLIENT_PORT); // data_len = read_size + sizeof(int)
				
				/* send message to client */
				if(Math.random() < 1-lossRate) {
					sock.send(pkt_send); // send message 	
					sendCounter++;
				}
				else  {
//					System.out.println("loss " + id); // debug
				}
				
				
				/* Parity shards */
				System.arraycopy(buf, 0, shards[id%TOTAL_SHARDS], 0, DATA_SIZE); // copy data shards
				if(idcnt%TOTAL_SHARDS==DATA_SHARDS) {
					reedSolomon.encodeParity(shards, 0, DATA_SIZE); // encode data
					
					/* send all parity shards */
					for(int i=DATA_SHARDS; i<TOTAL_SHARDS; i++) {
						b = ByteBuffer.allocate(BUFFER_SIZE); // alloc id sizeof(int) + data size 
						b.putInt(idcnt++); // put id and data
						b.put(shards[i]);
						pack = b.array();
						pkt_send = new DatagramPacket(pack, n+BYTES_IN_INT, address, CLIENT_PORT);
						if(Math.random() < 1-lossRate) {
							sock.send(pkt_send); // send message 				
							sendCounter++; // debug - count packet
						}
						else  {
//							System.out.println("loss " + id); // debug
						}
						
						/* debug message */
//						b.rewind();
//						id = b.getInt();
//						output = new byte[n];
//						b.get(output);
//						System.out.println("Parity part:");
//						System.out.println("id: " + id + ",  size: " + n);
					}
					
					
					/* testing recover */
//					System.out.println("test recover from server");
//					System.out.println("----------------------------");
//					System.out.println(new String(shards[0]));
//					Arrays.fill(shards[1], (byte)0);
//					boolean[] shardPresent = {true, false, true, true, true, true};
//					reedSolomon.decodeMissing(shards, shardPresent, 0, DATA_SIZE);
//					System.out.println(new String(shards[0]));
//					System.out.println("---------------------------");
				}
			}	
			
			/* send EOF message => id=-1 
			 * if EOF message packet loss => client wait for timeout to close the socket
			 * */
			byte[] eof_buf = new byte[BYTES_IN_INT*2]; // id and rest len
			ByteBuffer bb = ByteBuffer.wrap(eof_buf);
			bb.putInt(-1);
			bb.putInt(rest);
			DatagramPacket pkt_send = null;
			pkt_send = new DatagramPacket(eof_buf, BYTES_IN_INT*2, address, CLIENT_PORT);				
			for(int i=0; i<EOF_TIMES; i++) {
//				System.out.println("Send final packet");
				sock.send(pkt_send); // send eof message
			}
			
			System.out.println("Finished!!");
			System.out.println("Total packets: " + idcnt + "\nSend packets: " + sendCounter);
			System.out.printf("Simulate loss rate: %.2f %%\n", (double)(idcnt-sendCounter)/idcnt*100);
			
		} catch (FileNotFoundException e2) {
			e2.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	/* setter */
	public void setFilePath(String filepath) {
		this.filepath = filepath;
	}
}
