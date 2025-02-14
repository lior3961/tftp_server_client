package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.impl.tftp.TftpEncoderDecoder;
import bgu.spl.net.impl.tftp.TftpProtocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<byte[]> {

    private final TftpProtocol protocol;
    private final TftpEncoderDecoder encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;
    private ServerData serverData;
    private int connectionId;

    public BlockingConnectionHandler(Socket sock, TftpEncoderDecoder reader, TftpProtocol protocol, int connectionId, ServerData serverData) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.serverData = serverData;
        this.connectionId = connectionId;
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            int read;
            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());
            this.protocol.start(connectionId, serverData.getConnections());
            this.serverData.connect(this.connectionId, this);
            System.out.println("Client: " + this.connectionId + " connected to the server");
            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                byte[] nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) 
                {
                    this.protocol.process(nextMessage);
                }
            }
            close();
        } catch (IOException ex) { //where user close terminal
            ex.printStackTrace();
        }
        finally
        {
            this.serverData.disconnectUser(connectionId);
            System.out.println("Discconect client: " + this.connectionId);
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    @Override
    public void send(byte[] msg) {
        try
        {
            out.write(encdec.encode(msg));
            out.flush();
            System.out.println("Sent answer to client from buffer");
        } catch(IOException ex)
        {
            ex.printStackTrace();
        }

    }
}
