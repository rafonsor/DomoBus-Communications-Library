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

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

public class Dispatcher extends Thread{
    
    private boolean dispatching;
    private Manager manager;
    private Map<String, Integer> retransmissionQueue;
    private Map<String, Long> retransmissionTime;
    private Map<String, byte[]> messageList;
    
    public Dispatcher(Manager manager) {
        this.dispatching = true;        
        this.manager = manager;       
        this.retransmissionQueue = new LinkedHashMap<>();
        this.retransmissionTime = new LinkedHashMap<>();
        this.messageList = new HashMap<>();
    };
    
    public void Stop() {
        dispatching = false;        
        System.out.println("Stopping Dispatcher.");
    };
    
    @Override
    public void run() {
        manager.Log(Globals.LogType.SYSTEM, "Dispatcher running.");
        
        while(dispatching) {
            try {
                long now = System.currentTimeMillis();
                
                //get next message
                String next = retransmissionTime.keySet().iterator().next();
                if (retransmissionTime.get(next) > now) continue; //still too early to resend
                
                retransmissionTime.remove(next);
                byte[] message = messageList.get(next);
                
                //retrieve destinatary
                int appId = Globals.BytesToInt(message, 1);
                
                //send message
                manager.Log(Globals.LogType.COMMAND, "Retransmitting message to Supervisor "+appId+": "+message);
                SendMessage(appId, next, message, true);
                
            }catch(NoSuchElementException | NullPointerException | ConcurrentModificationException ex) {
                try {
                    //retransmission queue is empty
                    Thread.sleep(Globals.VACATION_DURATION);
                } catch (InterruptedException ex1) {
                    manager.Log(Globals.LogType.ERROR, "DispatcherVacationException\t"+ex1);
                }
            }
        }
        manager.Log(Globals.LogType.SYSTEM, "Dispatcher stopped.");
    };
    
    //Handle a new message received
    public void NewMessage(int appId, byte[] packet) {
        if (Globals.CKS(Arrays.copyOf(packet, packet.length-1)) != packet[packet.length-1]) return; //bad checksum, discard packet
        if (packet.length != (short)packet[0]) return; //length doesn't match, discard packet
        
        int dest = Globals.BytesToInt(packet, 1);
        if (manager.SelfId() != dest) return; //wrong destinatary, discard packet
        
        String msgId = ""+appId+packet[5];
        
        //Response received
        if(manager.HasSequence(appId, packet[5])) {
            //acknowledge flag      
            if(Globals.IsBitSet(packet[6], Globals.ACK_POS) && !Globals.IsBitSet(packet[6], Globals.ERROR_POS)) {
                manager.Log(Globals.LogType.COMMAND, "received new response from Supervisor "+appId);
                retransmissionTime.remove(msgId);
                retransmissionQueue.remove(msgId);
                messageList.remove(msgId);
                manager.RemoveSequence(appId, packet[5]);
                
                //add to processing queue
                manager.AddMessage(Globals.IsBitSet(packet[6], Globals.PRIORITY_POS), packet);
                return;
            }
            
            //error flag
            else if(Globals.IsBitSet(packet[6], Globals.ACK_POS) && Globals.IsBitSet(packet[6], Globals.ERROR_POS)) {
                manager.Log(Globals.LogType.COMMAND, "received new error response from Supervisor "+appId);
                byte[] message;
                
                if (packet[7] == Globals.ERROR_QUEUES_FULL) return; //queues full, leave for retransmission
                
                retransmissionTime.remove(msgId);
                retransmissionQueue.remove(msgId);
                manager.RemoveSequence(appId, packet[5]);
                
                switch(packet[7]) {                        
                    case Globals.ERROR_UNSUPPORTED_COMMAND:                        
                        //command not supported, call Error method from API
                        message = messageList.get(msgId);
                        manager.DComm_callback_process_ERROR(Globals.ERROR_UNSUPPORTED_COMMAND, message);
                        break;
                        
                    case Globals.ERROR_GET_COMMAND | Globals.ERROR_SET_COMMAND | Globals.ERROR_NOTIFY_COMMAND | Globals.ERROR_EXEC_COMMAND | Globals.ERROR_RESERVED_COMMAND:
                        //error response to a command sent, call Error method from API
                        message = messageList.get(msgId);
                        manager.DComm_callback_process_ERROR(packet[7], message);
                        break;
                        
                    default:
                        //unrecognized error code, let worker handle the response
                        manager.AddMessage(Globals.IsBitSet(packet[6], Globals.PRIORITY_POS), packet);
                        break;
                }
                
                messageList.remove(msgId);
                return;
            }
            
            //retransmission flag, discard
            else if(Globals.IsBitSet(packet[6], Globals.RETRANSMISSION_POS)) {
                manager.Log(Globals.LogType.COMMAND, "discarded retransmitted command from Supervisor "+appId);
                return;
            }
            
            //duplicate packet, discard
            else {
                manager.Log(Globals.LogType.COMMAND, "discarded duplicate command from Supervisor "+appId);
                return;
            }                    
        }
        //invalid sequence number, discard
        if (packet[5] < manager.GetSequence(appId, false)) return;
        
        //New command received, add packet to processing queue while checking its priority bit
        if(!manager.AddMessage(Globals.IsBitSet(packet[6], Globals.PRIORITY_POS), packet)) {
            //queues are full, reply with error and discard packet
            byte[] res = new byte[9];
            res[0] = 9;
            
            //switch destinatary and origin appIds
            Globals.IntToBytes(appId, 1, res);
            Globals.IntToBytes(manager.SelfId(), 3, res);
            
            //set same sequence number
            res[5] = packet[5];
            
            //set CTR field and error code
            res[6] = (byte) (Globals.CTR + Globals.ACK_CTR + Globals.ERROR_CTR);
            res[7] = (byte) Globals.ERROR_QUEUES_FULL;
            
            //set CRC
            res[8] = Globals.CKS(Arrays.copyOf(res, 8));
            
            SendMessage(appId, msgId, res, false);
            return;
        }
        manager.Log(Globals.LogType.COMMAND, "received new command from Supervisor "+appId);
        manager.NewSequence(appId, packet[5]);
    };
    
    //Transmit a new message
    public void SendMessage(int appId, String msgId, byte[] message, boolean retransmission) {
        try {
            //send message
            if(!manager.SendTo(appId, message)) {
                final int id = appId;
                //application not found, sending a DNS request instead
                Timer dnsTask = new Timer();
                dnsTask.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        manager.DNSRequest(id);
                    }
                }, 0);
                throw new Exception();
            }
            
            //adding message to retransmission queue, awaiting response
            if(retransmission) {
                //update number of retries
                retransmissionQueue.put(msgId, retransmissionQueue.get(msgId)-1);
                
                if (retransmissionQueue.get(msgId) == 0) {
                    //maximum retries made, drop message
                    retransmissionQueue.remove(msgId);
                    messageList.remove(msgId);
                    manager.RemoveSequence(appId, message[5]);
                    //inform supervisor if this isn't a response message and we haven't received a response so far
                    if(!Globals.IsBitSet(message[6], Globals.ACK_POS) && !Globals.IsBitSet(message[6], Globals.ERROR_POS) && manager.HasSequence(appId, message[5])) manager.DComm_callback_process_ERROR(Globals.ERROR_TRANSMISSION_FAILED, message);
                }
                else retransmissionTime.put(msgId, System.currentTimeMillis()+Globals.RESTRANSMISSION_PERIOD); //set time of next retry
            }
            else {
                //toggle retransmission bit of CTR field
                message[6] = (byte) (message[6] + Globals.RETRANSMISSION_CTR);
                //update CRC
                message[message.length-1] = Globals.CKS(Arrays.copyOf(message, message.length-1));

                //put message up for retransmission
                manager.AddSequence(appId, message[5]);
                messageList.put(msgId, message);
                retransmissionQueue.put(msgId, Globals.MAX_RETRANSMISSIONS);
                retransmissionTime.put(msgId, System.currentTimeMillis()+Globals.RESTRANSMISSION_PERIOD);
            }
        } catch (Exception ex) {
            //failed to send message
            manager.Log(Globals.LogType.COMMAND, "failed to transmit message to Supervisor "+appId);
            
            if(retransmission) {
                //if it was already a retransmission, update number of retries made
                retransmissionQueue.put(msgId, retransmissionQueue.get(msgId)-1);
                
                if (retransmissionQueue.get(msgId) == 0) {
                    //maximum retries made, drop message
                    retransmissionQueue.remove(msgId);
                    messageList.remove(msgId);
                    manager.RemoveSequence(appId, message[5]);
                    //inform supervisor if this isn't a response message and we haven't received a response so far
                    if(!Globals.IsBitSet(message[6], Globals.ACK_POS) && !Globals.IsBitSet(message[6], Globals.ERROR_POS) && manager.HasSequence(appId, message[5])) manager.DComm_callback_process_ERROR(Globals.ERROR_TRANSMISSION_FAILED, message);
                }
                else retransmissionTime.put(msgId, System.currentTimeMillis()+Globals.RESTRANSMISSION_PERIOD); //set time of next retry
            }
            else {  
                //toggle retransmission bit of CTR field
                message[6] = (byte) (message[6] + Globals.RETRANSMISSION_CTR);
                //update CRC
                message[message.length-1] = Globals.CKS(Arrays.copyOf(message, message.length-1));

                //put message up for retransmission
                manager.AddSequence(appId, message[5]);
                messageList.put(msgId, message);
                retransmissionQueue.put(msgId, Globals.MAX_RETRANSMISSIONS);
                retransmissionTime.put(msgId, System.currentTimeMillis()+Globals.RESTRANSMISSION_PERIOD);
            }
        }
    };
    
    //Create and transmit a new message
    public void CreateMessage(int appId, byte seq, byte CTR, byte[] data) {
        byte[] message = new byte[8+data.length];
        
        //set message length
        message[0] = (byte) (8+data.length);
        
        //set destinatary appId
        Globals.IntToBytes(appId, 1, message);
        
        //add our appId as origin
        Globals.IntToBytes(manager.SelfId(), 3, message);
        
        //add sequence number
        message[5] = seq;
        
        //add CTR
        message[6] = CTR;
        
        //add data
        System.arraycopy(data, 0, message, 7, data.length);
        
        //add CRC
        message[message.length-1] = Globals.CKS(Arrays.copyOf(message, message.length-1));
        
        //send message
        String msgId = ""+appId+seq;
        SendMessage(appId, msgId, message, false);
    };
    
    //Create and transmit a new message
    public byte[] CreateSyncMessage(int appId, byte seq, byte CTR, byte[] data) {
        byte[] message = new byte[8+data.length];
        
        //set message length
        message[0] = (byte) (8+data.length);
        
        //set destinatary appId
        Globals.IntToBytes(appId, 1, message);
        
        //add our appId as origin
        Globals.IntToBytes(manager.SelfId(), 3, message);
        
        //add sequence number
        message[5] = seq;
        
        //add CTR
        message[6] = CTR;
        
        //add data
        System.arraycopy(data, 0, message, 7, data.length);
        
        //add CRC
        message[message.length-1] = Globals.CKS(Arrays.copyOf(message, message.length-1));
        
        return message;
    };
}
