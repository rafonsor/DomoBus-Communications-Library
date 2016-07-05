/**
 * MIT License
 * 
 * Copyright (c) 2016 Rafael Afonso Rodrigues

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
**/

package domobus.communications;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Rafael Afonso Rodrigues
 */
public class Peer extends Thread{
    private Manager manager;
    private int appId;
    private int port;
    private String ip;
    private DatagramSocket socket;
    private boolean running;
    private ReentrantLock LOCK;
    
    public Peer(Manager manager, DatagramSocket socket, int appId, String ip, int port) {
        this.manager = manager;
        this.socket = socket;
        this.appId = appId;
        this.ip = ip;
        this.port = port;
        this.LOCK = new ReentrantLock(true);
    };
    
    @Override
    public void run() {        
        running = true;
        if(socket == null) {
            try {
                //connect to peer
                socket = new DatagramSocket();
                socket.setSoTimeout(Globals.CONNECTION_TIMEOUT);
                socket.connect(InetAddress.getByName(ip), port);
                manager.Log(Globals.LogType.SYSTEM, "opened connection with Supervisor "+appId);
                
            } catch (UnknownHostException | SocketException ex) {
                manager.Log(Globals.LogType.ERROR, "Failed to open connection with Supervisor "+appId);
            }
        }
        
        manager.Log(Globals.LogType.SYSTEM, "listening to Supervisor "+appId);
        while(running) {
            try {
                //wait for a new message
                LOCK.lock();
                DatagramPacket packet = new DatagramPacket(new byte[Globals.MAX_PACKET_LENGTH], Globals.MAX_PACKET_LENGTH);
                socket.receive(packet);
                LOCK.unlock();
                
                //send for processing
                manager.NewMessage(this.appId, packet.getData());
            } catch (Exception ex) {
                if(LOCK.isHeldByCurrentThread()) LOCK.unlock();
            }
        }
        running = false;
    };
    
    public void SendMessage(byte[] message) throws IOException {
        LOCK.lock();
        try {
            socket.send(new DatagramPacket(message, message.length));
            manager.Log(Globals.LogType.COMMAND, "sent new message to Supervisor "+appId);
        } finally {
            LOCK.unlock();
        }
    };
    
    public boolean SendSyncMessage(byte[] request) throws IOException {
        try {
            //acquire timed lock for synchronous call
            LOCK.tryLock(Globals.CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
            
            //send command
            socket.send(new DatagramPacket(request, request.length));
            manager.Log(Globals.LogType.COMMAND, "sent new message to Supervisor "+appId);
            
            LOCK.unlock();
            
        } catch(InterruptedException ex) {
            return false;
        }
        
        return true;
    };

    public void Disconnect() {
        running = false;        
        manager.Log(Globals.LogType.SYSTEM, "closed connection with Supervisor "+appId);
    };
    
    public String GetIp() {
        return ip;
    };
    
    public int GetPort() {
        return port;
    };
    
    public boolean IsRunning() {
        return running;
    };
}
