package lab2_unicast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.nio.ByteBuffer;

public class Client extends Thread {
	
	public Socket sock = null;
	
	public final static int BUFFER_SIZE = 4096;
	public final static int BYTE_IN_INT = 4;
	
	public static void main(String[] args) {
		if(args.length!=2) {
			System.out.println("Usage: Client <ip> <port>");
			System.exit(0);
		}
		String host = args[0];
		int port = Integer.valueOf(args[1]);
		try {
			Client client = new Client(host, port);
			client.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Client(String host, int port) throws UnknownHostException, IOException {
		sock = new Socket(host, port);
	}
	
	public void run() {
		File file = new File("unicast_"+sock.getLocalPort()+".txt");
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		byte[] buffer = new byte[BUFFER_SIZE];
		try {
			InputStream is = sock.getInputStream();
			FileOutputStream fos = new FileOutputStream(file);
			int n=0;
			int acc=0;
			int filelen = 0;
			/* read file size and write first */
			while((n=is.read(buffer)) != -1)
			{
				acc += n;
				bb.put(buffer, 0, n);
				if(acc<BYTE_IN_INT) {
					continue;
				}
				bb.flip(); // change to read mode
				filelen = bb.getInt();
//				System.out.println(filelen); // debug
				fos.write(bb.array(), BYTE_IN_INT, acc-BYTE_IN_INT);
				acc -= BYTE_IN_INT;
				break;
			}
			
			/* read rest data */
			while(acc<filelen && (n=is.read(buffer))!=-1) {
				acc += n;
				fos.write(buffer, 0, n);
			}
			System.out.println("Finished");
			fos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
