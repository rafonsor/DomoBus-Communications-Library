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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Rafael Afonso Rodrigues
 */
public class Manager extends Thread implements IDComm{
    private IDComm API;
    private boolean running;
    private int id;
    private int port;
    private String configurationFile;
    private boolean configurationChanged;
    private ArrayList<String> configAppend;
    private ArrayList<String> configRemove;
    private Timer configurationTask;
    private String dnsHost;
    private int dnsPort;
    private Logger logger;
    private Worker worker;
    private Dispatcher dispatcher;
    private Map<Integer, ArrayList<Byte>> sequences;
    private Map<Integer, Byte> currentSequence;
    private Map<String, byte[]> messageList;
    private Map<Integer, Map<Integer, ArrayList<Integer>>> subscriptions;
    private Map<Integer, Peer> peerList;
    private ReentrantLock LOCK;
    private DatagramSocket serverSocket;
    
    public Manager(IDComm API, final String configurationFile) throws FileNotFoundException, IOException, InstantiationException {
        this.API = API;
        this.logger = new Logger();
        
        this.id = -1;
        this.port = -1;
        this.sequences = new HashMap<>();
        this.currentSequence = new HashMap<>();
        this.messageList = new HashMap<>();
        this.subscriptions = new HashMap<>();
        this.configurationChanged = false;
        this.configAppend = new ArrayList<>();
        this.configRemove = new ArrayList<>();
        this.peerList = new HashMap<>();
        this.LOCK = new ReentrantLock(true);
        this.running = true;
        
        //open configuration file
        this.configurationFile = configurationFile;
        BufferedReader br = new BufferedReader(new FileReader(configurationFile));
        
        String line;
        int dev, prop, sub, appId;
        HashMap<Integer, ArrayList<Integer>> temp;
        
        //iterate through lines
        while((line = br.readLine()) != null) {
            String[] tokens = line.split(" ");
            switch(tokens[0]) {
                case Globals.PREFIX_APP_ID:
                    //retrieve appId
                    this.id = Integer.parseInt(tokens[1]);
                    break;
                    
                case Globals.PREFIX_APP_PORT:
                    //retrieve API port
                    this.port = Integer.parseInt(tokens[1]);
                    break;
                    
                case Globals.PREFIX_PEERS:
                    //retrieve peers
                    appId = Integer.parseInt(tokens[1]);
                    peerList.put(appId,new Peer(this, null, appId, tokens[2], Integer.parseInt(tokens[3])));
                    break;
                    
                case Globals.PREFIX_SUBSCRIBERS:
                    //retrieve subscriptions
                    dev = Integer.parseInt(tokens[1]);
                    prop = Integer.parseInt(tokens[2]);
                    sub = Integer.parseInt(tokens[3]);
                    
                    ArrayList<Integer> tempList = new ArrayList<>();
                    tempList.add(sub);
                    
                    //new publisher device
                    if(!subscriptions.containsKey(dev)) {
                        temp = new HashMap<>();
                        temp.put(prop, tempList);
                        subscriptions.put(dev, temp);
                    }
                    //new subscribed property
                    else if(!subscriptions.get(dev).containsKey(prop)) subscriptions.get(dev).put(prop, tempList);
                    //new subscriber
                    else subscriptions.get(dev).get(prop).add(sub);
                    break;
                    
                case Globals.PREFIX_DNS:
                    dnsHost = tokens[1];
                    dnsPort = Integer.parseInt(tokens[2]);
                    break;
                    
                default:
                    //unrecognized configuration prefix, ignore
                    break;
            }
        }
        br.close();
        
        //Configuration file corrupted, missing essential options
        if(this.id < 0 || this.port < 0) throw new InstantiationException();
        
        System.out.println("Configuration file loaded.");
        
        //Setup update task for configuration file
        this.configurationTask = new Timer();
        configurationTask.schedule(new TimerTask() {
            @Override
            public void run() {
                Log(Globals.LogType.SYSTEM, "Manager is updating the configuration file.");
                
                if(!configurationChanged) {
                    Log(Globals.LogType.SYSTEM, "No update required for the configuration file.");
                    return;
                }
                
                try {
                    FileWriter fw;
                    if(configRemove.size() > 0) {
                        BufferedReader br = new BufferedReader(new FileReader(configurationFile));
                        File temp = new File(configurationFile+".temp");
                        fw = new FileWriter(temp);
                        String line;
                        while((line = br.readLine()) != null) {
                            if(configRemove.contains(line)) {
                                Log(Globals.LogType.SYSTEM, "Removing from configuration file the property: "+line);
                                configRemove.remove(line);
                            }
                            else fw.append(line+"\n");
                        }
                        br.close();
                        fw.close();
                        
                        temp.renameTo(new File(configurationFile));
                    }
                    if(configAppend.size() > 0) {
                        fw = new FileWriter(configurationFile);
                        for(String entry: configAppend) {
                            Log(Globals.LogType.SYSTEM, "Adding to configuration file the property: "+entry);
                            configAppend.remove(entry);
                            fw.append(entry+"\n");
                        }
                        fw.close();
                    }
                    configurationChanged = false;
                    
                } catch(IOException ex) {
                    Log(Globals.LogType.ERROR, "Exception while updating configuration file.");
                }
            }
        }, Globals.BACKUP_PERIOD, Globals.BACKUP_PERIOD);
        
        this.worker = new Worker(API, this);
        this.dispatcher = new Dispatcher(this);
    };
    
    public void Disconnect() {
        //Stop dispatching messages
        dispatcher.Stop();
        System.out.println("Dispatcher stopped.");
        //Stop accepting new connections
        running = false;
        //Close open connections
        for(Peer peer: peerList.values()) peer.Disconnect();
        System.out.println("Stopped incomming connections.");
        //Stop processing messages
        worker.Stop();
        System.out.println("Worker stopped.");
        //Close logs
        logger.Stop();
        System.out.println("Logs closed.");
    };
    
    public int SelfId() {
        return id;
    };
    
    @Override
    public void run() {
        try {
            if(DNSRegistration()) System.out.println("Registered with DNS.");
            
            //start Worker
            worker.start();
            
            //start Dispatcher
            dispatcher.start();
            
            //start listening to known peers
            for(Peer peer: peerList.values()) peer.start();
            
            //start accepting new connections
            serverSocket = new DatagramSocket(port);
            byte[] receiveData = new byte[Globals.MAX_PACKET_LENGTH];
            Log(Globals.LogType.SYSTEM, "Manager listening to public port "+port);
            
            while(running) {
                try {
                    //wait for a new packet
                    DatagramPacket packet = new DatagramPacket(receiveData, Globals.MAX_PACKET_LENGTH);
                    LOCK.lock();
                    serverSocket.receive(packet);
                    LOCK.unlock();
                    
                    //extract message
                    byte[] message = Arrays.copyOf(packet.getData(), packet.getLength());
                    if(message.length < Globals.MIN_PACKET_LENGTH || message.length > Globals.MAX_PACKET_LENGTH) continue; //bad format

                    //get appId of origin
                    final int peerId = Globals.BytesToInt(message, 3);

                    //retrieve socket remote address
                    String peerIp = packet.getAddress().getHostAddress();

                    //verify address and id corresponds to a known application
                    boolean known = false;

                    for(int appId: peerList.keySet()) {
                        String ip = peerList.get(appId).GetIp();
                        if(appId == peerId && ip.equals(peerIp)) {
                            Log(Globals.LogType.SYSTEM, "Manager received new message from Supervisor "+appId);

                            //update connection
                            if(!peerList.get(appId).IsRunning()) {
                                int port = peerList.get(appId).GetPort();
                                peerList.get(peerId).Disconnect();
                                peerList.put(peerId, new Peer(this, serverSocket, peerId, ip, port));
                                peerList.get(peerId).start();
                            }

                            //register message             
                            NewMessage(peerId, message);
                            known = true;
                            break;
                        }
                    }
                
                    if(!known) {
                        //Peer not found, sending request to DNS in a separate thread
                        Log(Globals.LogType.SYSTEM, "Manager received new message from an unknown source "+peerIp+":"+packet.getPort());

                        Timer dnsTask = new Timer();
                        dnsTask.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            DNSRequest(peerId);
                        }
                    }, 0);
                    }
                } catch(SocketTimeoutException ex) {
                    if(LOCK.isHeldByCurrentThread()) LOCK.unlock();
                }
            }
            Log(Globals.LogType.SYSTEM, "Manager stopped listening to public port.");
            
        } catch (BindException ex) {
            System.out.println("DCOMM port is already in use, please specify an unused port or close the program using it.");
            Log(Globals.LogType.ERROR, "Manager\tPublic port already in use, stopping communications module.");
            System.exit(-1);
            
        } catch (Exception ex) {
            Log(Globals.LogType.ERROR, "CriticalException\toccured within Manager thread: "+ex+", stopping communications module.");
            System.out.println(ex);
            System.exit(-1);
        }
    };

    //////////DNS methods//////////
    public boolean DNSRegistration() {
        Log(Globals.LogType.DNS, "Registrating with DNS.");
        
        if(dnsHost != null && dnsPort > 0) {
            try {
                //open socket with DNS
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(Globals.DNS_REQUEST_TIMEOUT);
                socket.connect(InetAddress.getByName(dnsHost), dnsPort);
                
                //set request message
                byte[] request = new byte[12];
                request[0] = 12;
                
                //null appDest since it's for the DNS
                request[1] = 0;
                request[2] = 0;
                
                //our appId
                Globals.IntToBytes(id, 3, request);
                
                //null sequence number
                request[5] = 0;
                
                //CTR with DNS opCode
                request[6] = (byte) (Globals.CTR + Globals.DNS_OPERATION);
                
                //registration data
                request[7] = 4;
                request[8] = Globals.DNS_REGISTER;
                Globals.IntToBytes(port, 9, request);
                
                //CRC
                request[11] = Globals.CKS(Arrays.copyOf(request, request.length-1));
                
                //send request
                logger.Log(Globals.LogType.DNS, "Sending registration to DNS.");
                socket.send(new DatagramPacket(request, request.length));
                
                //wait for response
                DatagramPacket responsePacket = new DatagramPacket(new byte[1024], 1024);
                socket.receive(responsePacket);
                
                byte[] response = Arrays.copyOf(responsePacket.getData(), responsePacket.getLength());
                socket.close();
                
                //verify response's length, CRC and CTR bits
                if(response.length >= Globals.MIN_PACKET_LENGTH && response[response.length-1] == Globals.CKS(Arrays.copyOf(response, response.length-1)) && Globals.IsBitSet(response[6], Globals.ACK_POS) && !Globals.IsBitSet(response[6], Globals.ERROR_POS)) {
                    logger.Log(Globals.LogType.DNS, "DNS registration complete.");
                    return true;
                }                
                
            } catch(IOException ex) {
                logger.Log(Globals.LogType.ERROR, "DNS registration timed out.");
            }
        }
        
        logger.Log(Globals.LogType.ERROR, "DNS registration failed.");
        return false;
    };
    
    public void DNSRequest(int appId) {
        Log(Globals.LogType.DNS, "Requesting information of Supervisor "+appId);
        
        if(dnsHost != null && dnsPort > 0) {
            try {
                //open socket with DNS
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(Globals.DNS_REQUEST_TIMEOUT);
                socket.connect(InetAddress.getByName(dnsHost), dnsPort);
                
                //set request message
                byte[] request = new byte[12];
                request[0] = 12;
                
                //null appDest since it's for the DNS
                request[1] = 0;
                request[2] = 0;
                
                //our appId
                Globals.IntToBytes(id, 3, request);
                
                //null sequence number
                request[5] = 0;
                
                //CTR with DNS opCode
                request[6] = (byte) (Globals.CTR + Globals.DNS_OPERATION);
                
                //request data
                request[7] = 4;
                request[8] = Globals.DNS_GET;
                Globals.IntToBytes(appId, 9, request);
                
                //CRC
                request[11] = Globals.CKS(Arrays.copyOf(request, request.length-1));
                
                //send request
                logger.Log(Globals.LogType.DNS, "Sending request for Application "+appId);
                socket.send(new DatagramPacket(request, request.length));
                
                //wait for response
                DatagramPacket responsePacket = new DatagramPacket(new byte[1024], 1024);
                socket.receive(responsePacket);                
                socket.close();
                
                byte[] response = Arrays.copyOf(responsePacket.getData(), responsePacket.getLength());
                
                //verify CRC and response bits
                if(response[response.length-1] == Globals.CKS(Arrays.copyOf(response, response.length-1)) && Globals.IsBitSet(response[6], Globals.ACK_POS) && !Globals.IsBitSet(response[6], Globals.ERROR_POS)) {
                    //extract peer data
                    String peerIp = (response[9] & 0xFF)+"."+(response[10] & 0xFF)+"."+(response[11] & 0xFF)+"."+(response[12] & 0xFF);
                    int peerPort = Globals.BytesToInt(response, 13);
                    
                    //add peer to list
                    peerList.put(appId, new Peer(this, null, appId, peerIp, peerPort));
                    peerList.get(appId).start();
                    
                    //keep peer data to save into the configuration file during the next update task
                    configAppend.add("\n"+Globals.PREFIX_PEERS+" "+appId+" "+peerIp+" "+peerPort);
                    
                    logger.Log(Globals.LogType.DNS, "Request fulfilled for Application "+appId+" - "+peerIp+":"+peerPort);
                }
                
            } catch(SocketTimeoutException ex) {
                logger.Log(Globals.LogType.ERROR, "DNS request for Supervisor "+appId+" timed out.");
            } catch(IOException ex) {
                logger.Log(Globals.LogType.ERROR, "Failed to send a new request to the DNS server for Supervisor "+appId);
            }
        }
        else Log(Globals.LogType.ERROR, "No DNS found, cancelling DNS request for Supervisor "+appId);
    };
    //////////End of DNS methods//////////
    
    
    //////////Sequence numbers handling methods//////////
    public byte GetSequence(int appId, boolean next) {
        if(!currentSequence.containsKey(appId)) currentSequence.put(appId, (byte) 0);
        
        byte seq = currentSequence.get(appId);
        if(next) currentSequence.put(appId, (byte) (seq+1));
        return seq;
    };
    
    public void NewSequence(int appId, byte seq) {
        currentSequence.put(appId, seq);
        AddSequence(appId, seq);
    };
    
    public void AddSequence(int appId, byte seq) {
        if(!sequences.containsKey(appId)) sequences.put(appId, new ArrayList<Byte>());
        sequences.get(appId).add(seq);
    };
    
    public void RemoveSequence(int appId, byte seq) {
        if(sequences.containsKey(appId)) sequences.get(appId).remove((Byte) seq);
    };
    
    public boolean HasSequence(int appId, byte seq) {
        if(!sequences.containsKey(appId)) return false;
        return (sequences.get(appId).indexOf(seq) != -1);
    };
    //////////End of sequence numbers handling methods//////////
    
    
    //////////Bridge methods between logger, worker and dispatcher//////////
    public void Log(Globals.LogType type, String entry) {
        logger.Log(type, entry);
    }
    
    public void NewMessage(int appId, byte[] packet) {
        dispatcher.NewMessage(appId, packet);
    };
    
    public void CreateMessage(int appId, byte seq, byte CTR, byte[] data) {
        dispatcher.CreateMessage(appId, seq, CTR, data);
    };
    
    public boolean AddMessage(boolean priority, byte[] message) {
        return worker.AddMessage(priority, message);
    };
        
    public boolean SendTo(int appId, byte[] message) throws IOException {
        if(peerList.containsKey(appId)) {
            peerList.get(appId).SendMessage(message);
            return true;
        }
        
        //Dispatcher takes care of DNS request
        return false;
    };

    public Object[] SendSyncTo(int appId, byte[] request) throws IOException {
        if (!peerList.containsKey(appId)) {
            //unknown Supervisor, requesting contact
            DNSRequest(appId);
            throw new IOException();
        }
        
        Object[] response = new Object[2];
        try {
            if(!peerList.get(appId).SendSyncMessage(request)) throw new SocketTimeoutException();
            
            //acquire timed lock for synchronous call
            LOCK.tryLock(Globals.CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
            
            //wait for corresponding response or timeout
            long timeout = Calendar.getInstance().getTimeInMillis() + Globals.CONNECTION_TIMEOUT;
            while(true) {
                if(timeout < Calendar.getInstance().getTimeInMillis()) throw new InterruptedException();
                
                DatagramPacket packet = new DatagramPacket(new byte[Globals.MAX_PACKET_LENGTH], Globals.MAX_PACKET_LENGTH);
                serverSocket.receive(packet);
                Log(Globals.LogType.COMMAND, "received new message from Supervisor "+appId);
                
                byte[] message = Arrays.copyOf(packet.getData(), packet.getLength());                
                if(message.length < Globals.MIN_PACKET_LENGTH || Globals.CKS(Arrays.copyOf(message, message.length-1)) != message[message.length-1]) continue; //bad message
                
                //process response
                if(message[5] == request[5] && Globals.BytesToInt(message, 3) == appId) {
                    //handle error responses
                    if(Globals.IsBitSet(message[6], Globals.ACK_POS) && Globals.IsBitSet(message[6], Globals.ERROR_POS)) {
                        response[0] = Globals.ERROR_CTR;
                        //retrieve error code
                        response[1] = message[message.length-2];
                        break;
                    }
                    
                    //handle ACK responses
                    int opCode = (((message[6] << 5) & 0xFF) >> 5);
                    switch(opCode) {
                        //return ACK and values
                        case Globals.GET_OPERATION:
                            response[0] = Globals.ACK_CTR;
                            response[1] = Arrays.copyOfRange(message, 7, message.length-1);
                            break;
                            
                        //return ACK
                        case Globals.SET_OPERATION:
                        case Globals.NOTIFY_OPERATION:
                        default: //Reserved Operations
                            response[0] = Globals.ACK_CTR;
                            break;
                            
                        //return ACK and eventual values
                        case Globals.EXEC_OPERATION:
                        case Globals.DCOMM_OPERATION:
                            response[0] = Globals.ACK_CTR;
                            if(response.length > 10) response[1] = Arrays.copyOfRange(message, 9, message.length-1);
                            break;
                    }
                    break;
                }
                
                //send for processing since it is not the expected packet
                NewMessage(appId, message);  
            }            
            
        } catch(InterruptedException ex) {
            response[0] = Globals.ERROR_CTR;
            response[1] = Globals.ERROR_TIMEOUT;
        }
        
        return response;
    };
    
    //////////End of bridge methods between logger, worker and dispatcher//////////
    
    
    //////////Subscriptions handling methods//////////
    public void NewSubscription(int appId, int device, int property) {
        ArrayList<Integer> tempList = new ArrayList<>();
        tempList.add(appId);
        
        //add application to subscribers list
        if(!subscriptions.containsKey(device)) {
            HashMap<Integer, ArrayList<Integer>> tempMap = new HashMap<>();
            tempMap.put(property, tempList);
            subscriptions.put(device, tempMap);
        }
        else if(!subscriptions.get(device).containsKey(property)) {
            subscriptions.get(device).put(property, tempList);
        }
        else if(!subscriptions.get(device).get(property).contains(appId)) {
            subscriptions.get(device).get(property).add(appId);

        }
        else return; //unless it is already subscribed
        
        //signal changes to configuration file
        configurationChanged = true;
        configAppend.add(Globals.PREFIX_SUBSCRIBERS+" "+device+" "+property+" "+appId);
        
        Log(Globals.LogType.COMMAND, "Added subscription for Supervisor "+appId+" of property "+property+" from device "+device);
    };
    
    public void RemoveSubscription(int appId, int device, int property) {
        if(subscriptions.containsKey(device) && subscriptions.get(device).containsKey(property) && subscriptions.get(device).get(property).contains(appId)) {
            //remove application from subscribers list
            Log(Globals.LogType.COMMAND, "Removed subscription from Supervisor "+appId+" for property "+property+" from device "+device);
            subscriptions.get(device).get(property).remove((Integer) appId);
            
            //signal changes to configuration file
            configurationChanged = true;
            configRemove.add(Globals.PREFIX_SUBSCRIBERS+" "+device+" "+property+" "+appId);
        }
    };
    
    public void Unsubscribe(int appId) {
        Log(Globals.LogType.COMMAND, "Removing all subscriptions of Supervisor "+appId);
        for(int pub: subscriptions.keySet()) for(int prop: subscriptions.get(pub).keySet()) {
            if(subscriptions.get(pub).get(prop).contains(appId)) {
                //remove application from subscribers list
                Log(Globals.LogType.COMMAND, "Removed subscription from Supervisor "+appId+" for property "+prop+" from device "+pub);
                subscriptions.get(pub).get(prop).remove((Integer) appId);
                
                //signal changes to configuration file
                configurationChanged = true;
                configRemove.add(Globals.PREFIX_SUBSCRIBERS+" "+pub+" "+prop+" "+appId);                
            }
        }
    };
    //////////End of Subscriptions management methods//////////
    
    
    //////////Command asynchronous call methods//////////
    @Override
    public void DComm_send_msg_GET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of asynchronous GET command with destination the application "+appId);
        //make data field
        byte[] data = new byte[11];
        System.arraycopy(DevAddrOrig, 0, data, 0, 4);
        data[4] = PropDescOrig;
        System.arraycopy(DevAddrDest, 0, data, 5, 4);
        data[9] = PropDescDest;
        data[10] = 0;
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + Globals.GET_OPERATION);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //create and send message
        dispatcher.CreateMessage(appId, GetSequence(appId, true), CTR, data);
    };
    
    @Override
    public void DComm_send_msg_SET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of asynchronous SET command with destination the application "+appId);
        //make data field
        byte[] data = new byte[10+value.length];
        System.arraycopy(DevAddrOrig, 0, data, 0, 4);
        data[4] = PropDescOrig;
        System.arraycopy(DevAddrDest, 0, data, 5, 4);
        data[9] = PropDescDest;
        System.arraycopy(value, 0, data, 10, value.length);
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + Globals.SET_OPERATION);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //create and send message
        dispatcher.CreateMessage(appId, GetSequence(appId, true), CTR, data);
    };
    
    @Override
    public void DComm_send_msg_NOTIFY(int Dev, byte Prop, byte[] value, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of asynchronous NOTIFY commands for property "+Prop+" of device "+Dev);
        //check if there are any subscribers
        if(!subscriptions.containsKey(Dev) || !subscriptions.get(Dev).containsKey((int)Prop)) {
            Log(Globals.LogType.COMMAND, "No subscriptions for property "+Prop+" of device "+Dev);
            return;
        }
        
        //check value
        int length = 0;
        if(value != null) length = value.length;
        
        //make data field
        byte[] data = new byte[5+length];
        //add device address
        Globals.IntToBytes(id, 0, data);
        Globals.IntToBytes(Dev, 2, data);
        //add property description
        switch(length) {
            case 0:
                //invalid value
                data[4] = (byte) (Prop + Globals.INVALID_VALUE);
                break;
                
            case 1:
                //8-bit value
                data[4] = (byte) (Prop + Globals.MASK_8BIT);
                break;
                
            case 2:
                //16-bit value
                data[4] = (byte) (Prop + Globals.MASK_16BIT);
                break;
                
            default:
                //DomoBus Array value
                data[4] = (byte) (Prop + Globals.MASK_ARRAY);
                break;
        }
        //add value
        if (length > 0) System.arraycopy(value, 0, data, 5, length);
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + Globals.NOTIFY_OPERATION);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //create and send message to all subscribers
        for(int sub: subscriptions.get(Dev).get((int)Prop)) {
            Log(Globals.LogType.COMMAND, "Notifying Supervisor "+sub+" the current value of property "+Prop+" of device "+Dev+": "+value);
            dispatcher.CreateMessage(sub, GetSequence(sub, true), CTR, data);
        }
    };
    
    @Override
    public void DComm_send_msg_EXEC(int appId, byte command, byte[] arguments, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of asynchronous EXEC command with destination the application "+appId);
        
        //make data field
        byte[] data = new byte[2+arguments.length];
        data[0] = (byte) (1+arguments.length);
        data[1] = command;
        System.arraycopy(arguments, 0, data, 2, arguments.length);
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + Globals.EXEC_OPERATION);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //create and send message
        dispatcher.CreateMessage(appId, GetSequence(appId, true), CTR, data);
    };
        
    @Override
    public void DComm_send_msg_DCOMM(int appId, byte command, byte[] arguments, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of asynchronous DCOMM command with destination the application "+appId);
        
        //process command if it's destined to this application
        if(appId == id) {
            try {
                worker.ProcessDCOMM(appId, command, arguments);
                return;
            } catch(UnsupportedCommandException ex) {
                Log(Globals.LogType.ERROR, "DCOMM commands are unsupported.");
            }
        }
        
        //make data field
        byte[] data = new byte[2+arguments.length];
        data[0] = (byte) (2+arguments.length);
        data[1] = command;
        
        System.arraycopy(arguments, 0, data, 2, arguments.length);
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + Globals.DCOMM_OPERATION);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //create and send message
        dispatcher.CreateMessage(appId, GetSequence(appId, true), CTR, data);
    };
    
    @Override
    public void DComm_send_msg_RESERVED(int appId, byte operation, byte[] data, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of asynchronous RESERVED command of type "+operation+" with destination the application "+appId);
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + operation);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //create and send message
        dispatcher.CreateMessage(appId, GetSequence(appId, true), CTR, data);
    };
    //////////End of command asynchronous call methods//////////
    
    
    //////////Command synchronous call methods//////////
    @Override
    public Object[] DComm_send_sync_msg_GET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of synchronous GET command with destination the application "+appId);
        Object[] response = new Object[2];
        
        //make data field
        byte[] data = new byte[11];
        System.arraycopy(DevAddrOrig, 0, data, 0, 4);
        data[4] = PropDescOrig;
        System.arraycopy(DevAddrDest, 0, data, 5, 4);
        data[9] = PropDescDest;
        data[10] = 0;
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + Globals.GET_OPERATION);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //create and send message
        try{
            response = SendSyncTo(appId, dispatcher.CreateSyncMessage(appId, GetSequence(appId, true), CTR, data));
            if(Globals.IsBitSet((byte) (int)response[0], Globals.ERROR_POS)) Log(Globals.LogType.ERROR, "GetCommand\tReceived error response from GET command: "+data+", error received: "+response[1]);
            else Log(Globals.LogType.COMMAND, "GET command "+data+" returned the value "+response[1]);
            
        } catch(IOException ex) {
            response[0] = Globals.ERROR_CTR;
            response[1] = new byte[] {Globals.ERROR_TRANSMISSION_FAILED};
            Log(Globals.LogType.ERROR, "TransmissionFailed\tThe message failed to be transmitted to its destination and has subsequently discarded. "+data);
        }
        
        return response;
    };
    
    @Override
    public Object[] DComm_send_sync_msg_SET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of synchronous SET command with destination the application "+appId);
        Object[] response = new Object[2];
        
        //make data field
        byte[] data = new byte[10+value.length];
        System.arraycopy(DevAddrOrig, 0, data, 0, 4);
        data[4] = PropDescOrig;
        System.arraycopy(DevAddrDest, 0, data, 5, 4);
        data[9] = PropDescDest;
        System.arraycopy(value, 0, data, 10, value.length);
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + Globals.SET_OPERATION);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //send a DNS request beforehand if necessary
        if(!peerList.containsKey(appId)) DNSRequest(appId);
        
        //create and send message
        try{
            response = SendSyncTo(appId, dispatcher.CreateSyncMessage(appId, GetSequence(appId, true), CTR, data));
            if(Globals.IsBitSet((byte) (int)response[0], Globals.ERROR_POS)) Log(Globals.LogType.ERROR, "SetCommand\tReceived error response from SET command: "+data+", error received: "+response[1]);
            else Log(Globals.LogType.COMMAND, "SET command "+data+" was executed");
            
        } catch(IOException ex) {
            response[0] = Globals.ERROR_CTR;
            response[1] = new byte[] {Globals.ERROR_TRANSMISSION_FAILED};
            Log(Globals.LogType.ERROR, "TransmissionFailed\tThe message failed to be transmitted to its destination and has subsequently discarded. "+data);
        }        
        return response;
    };
    
    @Override
    public byte DComm_send_sync_msg_NOTIFY(int Dev, byte Prop, byte[] value, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of synchronous NOTIFY commands for property "+Prop+" of device "+Dev);
        //check if there are any subscribers
        if(!subscriptions.containsKey(Dev)) return Globals.ACK_CTR;
        if(!subscriptions.get(Dev).containsKey((int)Prop)) return Globals.ACK_CTR;
        
        //check value
        int length = 0;
        if(value != null) length = value.length;
        
        //make data field
        byte[] data = new byte[5+length];
        //add device address
        Globals.IntToBytes(id, 0, data);
        Globals.IntToBytes(Dev, 2, data);
        //add property description
        switch(length) {
            case 0:
                //invalid value
                data[4] = (byte) (Prop + Globals.INVALID_VALUE);
                break;
                
            case 1:
                //8-bit value
                data[4] = (byte) (Prop + Globals.MASK_8BIT);
                break;
                
            case 2:
                //16-bit value
                data[4] = (byte) (Prop + Globals.MASK_16BIT);
                break;
                
            default:
                //DomoBus Array value
                data[4] = (byte) (Prop + Globals.MASK_ARRAY);
                break;
        }
        //add value
        if (length > 0) System.arraycopy(value, 0, data, 5, length);
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + Globals.NOTIFY_OPERATION);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //create and send message to all subscribers
        byte[] message;
        Map<Integer, Byte> seqs = new HashMap<>();
        int acks = 0;
        
        for(int sub: subscriptions.get(Dev).get((int)Prop)) {
            seqs.put(sub, GetSequence(sub, true));
            message = dispatcher.CreateSyncMessage(sub, seqs.get(sub), CTR, data);
            Log(Globals.LogType.COMMAND, "Notifying Supervisor "+sub+" the current value of property "+Prop+" of device "+Dev+": "+value);
            
            try {
                if((byte)SendSyncTo(sub, message)[0] == Globals.ACK_CTR) acks++;
                else Log(Globals.LogType.ERROR, "Failed to notify Supervisor "+sub+" the current value of property "+Prop+" of device "+Dev);
                
            } catch(IOException ex) {
                Log(Globals.LogType.ERROR, "Failed to notify Supervisor "+sub+" the current value of property "+Prop+" of device "+Dev);
            }
        }
        
        if(acks == subscriptions.get(Dev).get((int)Prop).size()) {
            Log(Globals.LogType.COMMAND, "Notified all subscribers of property "+Prop+" of device "+Dev+": "+value);
            return Globals.ACK_CTR;
        }        
        Log(Globals.LogType.COMMAND, "Failed to notify all subscribers of property "+Prop+" of device "+Dev+": "+value);
        return Globals.ERROR_CTR;
    };
    
    @Override
    public Object[] DComm_send_sync_msg_EXEC(int appId, byte command, byte[] arguments, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of synchronous EXEC command with destination the application "+appId);
        Object[] response = new Object[2];
        
        //make data field
        byte[] data = new byte[2+arguments.length];
        data[0] = (byte) (1+arguments.length);
        data[1] = command;
        System.arraycopy(arguments, 0, data, 2, arguments.length);
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + Globals.EXEC_OPERATION);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //create and send message
        try{
            response = SendSyncTo(appId, dispatcher.CreateSyncMessage(appId, GetSequence(appId, true), CTR, data));
            if(Globals.IsBitSet((byte) (int)response[0], Globals.ERROR_POS)) Log(Globals.LogType.ERROR, "ExecCommand\tReceived error response from EXEC command: "+data+", error received: "+response[1]);
            else Log(Globals.LogType.COMMAND, "EXEC command "+data+" was executed");
            
        } catch(IOException ex) {
            response[0] = Globals.ERROR_CTR;
            response[1] = new byte[] {Globals.ERROR_TRANSMISSION_FAILED};
            Log(Globals.LogType.ERROR, "TransmissionFailed\tThe message failed to be transmitted to its destination and has subsequently discarded. "+data);
        }        
        return response;
    };
        
    @Override
    public Object[] DComm_send_sync_msg_DCOMM(int appId, byte command, byte[] arguments, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of synchronous DCOMM command with destination the application "+appId);
        Object[] response = new Object[2];
        
        //process command if it's destined to this application
        if(appId == id) {
            try {
                if(worker.ProcessDCOMM(appId, command, arguments)) {
                    response[0] = Globals.ACK_CTR;
                    Log(Globals.LogType.COMMAND, "The DCOMM function "+command+", with arguments "+arguments+", was locally executed.");
                }
                else {
                    response[0] = Globals.ERROR_CTR;
                    response[1] = new byte[]{(byte) Globals.ERROR_DCOMM_COMMAND};
                    Log(Globals.LogType.ERROR, "DCommCommand\tThe DCOMM function "+command+", with arguments "+arguments+", processed locally generated an error");
                }
                
            } catch(UnsupportedCommandException ex) {
                Log(Globals.LogType.ERROR, "DCOMM commands are unsupported.");
                response[0] = Globals.ERROR_CTR;
                response[1] = new byte[]{Globals.ERROR_UNSUPPORTED_COMMAND};
            }
            return response;
        }
        
        //make data field
        byte[] data = new byte[2+arguments.length];
        data[0] = (byte) (1+arguments.length);
        data[1] = command;
        System.arraycopy(arguments, 0, data, 2, arguments.length);
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + Globals.EXEC_OPERATION);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //create and send message
        try{
            response = SendSyncTo(appId, dispatcher.CreateSyncMessage(appId, GetSequence(appId, true), CTR, data));
            if(Globals.IsBitSet((byte) (int)response[0], Globals.ERROR_POS)) Log(Globals.LogType.ERROR, "DCommCommand\tReceived error response from DCOMM command: "+data+", error received: "+response[1]);
            else Log(Globals.LogType.COMMAND, "DCOMM command "+data+" was executed");
            
        } catch(IOException ex) {
            response[0] = Globals.ERROR_CTR;
            response[1] = new byte[] {Globals.ERROR_TRANSMISSION_FAILED};
            Log(Globals.LogType.ERROR, "TransmissionFailed\tThe message failed to be transmitted to its destination and has subsequently discarded. "+data);
        }        
        return response;
    };
    
    @Override
    public Object[] DComm_send_sync_msg_RESERVED(int appId, byte operation, byte[] data, boolean priority) {
        Log(Globals.LogType.COMMAND, "Generation of synchronous RESERVED command of type "+operation+" with destination application "+appId);
        Object[] response;
        
        //set CTR field
        byte CTR = (byte) (Globals.CTR + operation);
        if(priority) CTR = (byte) (CTR + Globals.PRIORITY_CTR);
        
        //create and send message
        byte[] message = dispatcher.CreateSyncMessage(appId, GetSequence(appId, true), CTR, data);
        try {
            response = SendSyncTo(appId, message);
            if(Globals.IsBitSet((byte) (int)response[0], Globals.ERROR_POS)) Log(Globals.LogType.ERROR, "ReservedCommand\tReceived error response from EXEC commandof type "+operation+": "+data+", error received: "+response[1]);
            else Log(Globals.LogType.COMMAND, "RESERVED command of type "+operation+" "+data+" was executed");
            
        } catch(IOException ex) {
            response = new Object[]{Globals.ERROR_CTR, Globals.ERROR_TRANSMISSION_FAILED};
            Log(Globals.LogType.ERROR, "TransmissionFailed\tThe message failed to be transmitted to its destination and has subsequently discarded. "+data);
        }
        
        return response;
    };
    //////////End of command synchronous call methods//////////
    
    @Override
    public void DComm_callback_process_ERROR(int errorCode, byte[] message){
        try {
            API.DComm_callback_process_ERROR(errorCode, message);
            String entry;
            switch(errorCode) {
                case Globals.ERROR_QUEUES_FULL:
                    entry = "QueuesFull\tAll queues are currently full and the message was not kept. ";
                    break;
                    
                case Globals.ERROR_TRANSMISSION_FAILED:
                    entry = "TransmissionFailed\tThe message failed to be transmitted to its destination and has subsequently discarded. ";
                    break;
                    
                case Globals.ERROR_TIMEOUT:
                    entry = "Timeout\tA timeout occured during the processing of the following event: ";
                    break;
                    
                case Globals.ERROR_NOT_FOUND:
                    entry = "NotFound\tThe resource request could not be found: ";
                    break;
                    
                case Globals.ERROR_UNSUPPORTED_COMMAND:
                    entry = "UnsupportedCommandException\tThe destination did not implement the desired command and thus the request could not be fulfilled. ";
                    break;
                    
                case Globals.ERROR_GET_COMMAND:
                    entry = "GetCommand\tThe following GET command generated an error: ";
                    break;
                    
                case Globals.ERROR_SET_COMMAND:
                    entry = "SetCommand\tThe following SET command generated an error: ";
                    break;
                    
                case Globals.ERROR_NOTIFY_COMMAND:
                    entry = "NotifyCommand\tThe following NOTIFY command generated an error: ";
                    break;
                    
                case Globals.ERROR_EXEC_COMMAND:
                    entry = "ExecCommand\tThe following EXEC command generated an error: ";
                    break;
                    
                case Globals.ERROR_DCOMM_COMMAND:
                    entry = "DCommCommand\tThe following DCOMM command generated an error: ";
                    break;
                    
                case Globals.ERROR_RESERVED_COMMAND:
                    entry = "ReservedCommand\tThe following special command generated an error: ";
                    break;
                    
                default:
                    entry = "CustomError\tThe following event resulted in an error: "+errorCode+". Event: ";
                    break;
            }            
            Log(Globals.LogType.ERROR, entry+message);
            
        } catch (UnsupportedCommandException ex) {
            Log(Globals.LogType.ERROR, "UnsupportedCommandException\tAPI has not implemented DComm_callback_process_ERROR. During event: error code: "+errorCode+", message: "+message);
        }
    };
    
    //un-used interface methods    
    @Override
    public byte[] DComm_callback_process_GET(byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest) throws UnsupportedCommandException {throw new UnsupportedCommandException();};
    @Override
    public void DComm_callback_process_ANSWER_GET(byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value) throws UnsupportedCommandException {throw new UnsupportedCommandException();};
    @Override
    public byte DComm_callback_process_SET(byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value) throws UnsupportedCommandException {throw new UnsupportedCommandException();};
    @Override
    public void DComm_callback_process_NOTIFY(byte[] DevAddr, byte PropDesc, byte[] value) throws UnsupportedCommandException {throw new UnsupportedCommandException();};
    @Override
    public boolean DComm_callback_process_EXEC(byte command, byte[] arguments) throws UnsupportedCommandException {throw new UnsupportedCommandException();};        
    @Override
    public boolean DComm_callback_process_RESERVED(byte operation, byte[] data) throws UnsupportedCommandException {throw new UnsupportedCommandException();};  
}
