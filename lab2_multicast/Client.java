package lab2_multicast;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.backblaze.erasure.ReedSolomon;

public class Client extends Thread{
	
	public final static int CLIENT_PORT = 5000;
	public static final int DATA_SIZE = 21;
	public static final int BUFFER_SIZE = DATA_SIZE+4;
	public final static String MULTI_ADDRESS = "224.0.0.1";
	public final static int TIMEOUT = 5000;
	
	public static final int DATA_SHARDS = 4;
    public static final int PARITY_SHARDS = 2;
    public static final int TOTAL_SHARDS = 6;
    
    public static final int BYTES_IN_INT = 4;
	
	private MulticastSocket sock = null;
	private int last_id = 0;
	private int loss_counter = 0;
	private int unrecover_loss = 0;
	private boolean[][] recv_window;
	private byte[][][] buffer_data;
	private byte[][] shards;
	private ReedSolomon reedSolomon;
	private FileOutputStream output;
	
	
	public static void main(String[] args) {		 
		Client client = new Client();
		client.start();
	}
	
	public Client() {
		last_id = 0;
		loss_counter = 0;
		unrecover_loss = 0;
		recv_window = new boolean[2][TOTAL_SHARDS]; // 4 data shards and 2 parity shards
		buffer_data = new byte[2][TOTAL_SHARDS][DATA_SIZE];
		shards = new byte[TOTAL_SHARDS][DATA_SIZE];
		reedSolomon = new ReedSolomon(DATA_SHARDS, PARITY_SHARDS);
		try {
			output = new FileOutputStream("output.txt");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public void run() {
		System.out.println("Client start");
		
		InetAddress address = null;
		try {
			sock = new MulticastSocket(CLIENT_PORT); // configure client port = 5000
			sock.setSoTimeout(TIMEOUT);
			address = InetAddress.getByName(MULTI_ADDRESS);
			sock.joinGroup(address); // add sock to the group
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		/* recv the data from server */
		byte[] buf = new byte[BUFFER_SIZE];
		DatagramPacket pkt_recv = new DatagramPacket(buf, buf.length);
		boolean socketOn = true;
		int final_times = 0;
		while(socketOn) { // if not timeout => keep receiving data
			try {
				/* extract id and data from packet */
				sock.receive(pkt_recv);
				byte[] ret = pkt_recv.getData();
				ByteBuffer bb = ByteBuffer.wrap(ret);
				int id = bb.getInt();
				
				/* buffer the data corresponding to id */
				if(id<last_id-TOTAL_SHARDS && id!=-1) {
					continue;
				}
//				System.out.println("\nget id "+id); //-- debug
				
				/* eof message => id == -1 */
				if(id==-1) {
					/* debug */
//					System.out.println("Recv eof message");
					int rest_len = bb.getInt();
//					System.out.println("rest len = "+rest_len);
					if(final_times == 0) { // not handle final yet
						rest_handler(rest_len);						
					}
					final_times++;
					
					if(final_times>=2) { // recv duplicate final packet => close socket
						socketOn = false;
						break;						
					}
					continue;
				}
				
				/* get data */
				byte[] data = new byte[DATA_SIZE];
				bb.get(data); // binary data
				/* buffer data */
				int x = (id/TOTAL_SHARDS)%2; //-- fixed 2 state
//				System.out.println("set "+ x + " " + id%TOTAL_SHARDS); // debug
				recv_window[x][id%TOTAL_SHARDS] = true;
				System.arraycopy(data, 0, buffer_data[x][id%TOTAL_SHARDS], 0, data.length);
				
				/* invoke ready to write */
				if(last_id/TOTAL_SHARDS < id/TOTAL_SHARDS) {  //-- packet has to match 6*n
					int w = x==1? 0: 1;
					recover_write(w);
				}
				last_id = last_id<id? id: last_id; // update last_id
				
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.println("\nTimeout => close socket");
				socketOn = false;
			}			
		}
		
		/* clear */
		try {
			sock.leaveGroup(address);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("\nClose socket");
		
		/* show loss packets info (fec) */
		System.out.println("Last packet id = "+last_id);
		if(last_id!=0) {
			System.out.println("loss packet(fec) = "+loss_counter);
			System.out.printf("loss rate(fec) = %.2f %%\n", ((double)loss_counter/last_id*100));
		}
		else {
			System.out.println("loss rate(fec) = 0 %");
		}
		/* show loss packet info (no fec) */
		if(last_id!=0) {
			System.out.println("loss packet(no fec) = "+unrecover_loss);
			System.out.printf("loss rate(no fec) = %.2f %%\n", ((double)unrecover_loss/last_id*100));
		}
		else {
			System.out.println("loss rate(no fec) = 0 %");
		}
		sock.close();
	}
	
	/* timeout => invoke last part recover_write and write rest message without recovery */
	public void rest_handler(int rest_len) {
		int x = (last_id/TOTAL_SHARDS)%2; //-- fixed 2 state
		for(int i=0; i<TOTAL_SHARDS; i++) {
			if(recv_window[x][i]) {
				try {
					// not last data
					if(i!=last_id%TOTAL_SHARDS) {
//						System.out.print(new String(buffer_data[x][i])); //-- debug
						output.write(buffer_data[x][i]);						
					}
					else {
//						System.out.print(new String(Arrays.copyOf(buffer_data[x][i], rest_len))); //-- debug
						output.write(buffer_data[x][i], 0, rest_len);	
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			else { //++ last segment not recover
				if(i<=last_id%TOTAL_SHARDS) {
					unrecover_loss++;
					loss_counter++;			
//					System.out.println("loss packet " + x + " " + i); // debug
				}
			}
		}
	}
	
	/* calculate loss packet in state x */
	public int lossNumber(int x) {
//		System.out.println("check loss"); // debug
		int loss = 0;
		for(int i=0; i<TOTAL_SHARDS; i++) {
			if(!recv_window[x][i]) {
				loss++;
//				System.out.println("loss packet " + x + " " + i);
			}
		}
		return loss;
	}
	
	/* 
	 * check whether the part can recover 
	 * if it is integrity or can recover => write to file
	 * else count number of packet loss
	 * */
	public void recover_write(int x) {
		int loss = lossNumber(x);
		unrecover_loss += loss;
		boolean isIntegrity = false;
		
		/* prepare shardsPresent */
		boolean[] shardPresent = new boolean[TOTAL_SHARDS];
		System.arraycopy(recv_window[x], 0, shardPresent, 0, shardPresent.length);
		Arrays.fill(recv_window[x], Boolean.FALSE); // clear recv_window
		/* prepare shards */
		for(int i=0; i<TOTAL_SHARDS; i++) {
			if(shardPresent[i]) { // if packet exists => fill row 
				System.arraycopy(buffer_data[x][i], 0, shards[i], 0, shards[i].length);
				// no need to clear buffer_data
			}
			else { 
				// do nothing
			}
		}
		
		if(loss==0) { // no loss packet
			isIntegrity = true;
//			System.out.println("\nComplete"); //-- debug
		}
		else {			
			if(TOTAL_SHARDS-loss >= DATA_SHARDS) { // can recover				
				/* start recover */
				/* debug */
//				System.out.println("start recover");
//				System.out.println("check present:");
//				for(int i=0; i<TOTAL_SHARDS; i++) {
//					System.out.print(" "+shardPresent[i]);
//				}
//				System.out.println("");
				reedSolomon.decodeMissing(shards, shardPresent, 0, DATA_SIZE);
				isIntegrity = true;
			}
			else {
				loss_counter += loss;
				/* debug => alert loss window id */
//				System.out.println("Segment can't be recover");
//				System.out.print("Loss packets:");
//				for(int i=0; i<TOTAL_SHARDS; i++) {
//					if(!shardPresent[i]) {
//						System.out.print(" " + i); //++ show packet id instead
//					}
//				}
//				System.out.println("");
			}
		}
		
		if(isIntegrity) {
//			System.out.println("\n-- Integrity write --" + x); //-- debug
			for(int i=0; i<DATA_SHARDS; i++) {
//				System.out.print(new String(shards[i])); //-- debug
				try {
					output.write(shards[i]);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		else { // write only success packet
//			System.out.println("\n-- Partial write --" + x); //-- debug
			for(int i=0; i<DATA_SHARDS; i++) {
				if(shardPresent[i]) {
//					System.out.print(new String(shards[i])); //-- debug
					try {
						output.write(shards[i]);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

}
