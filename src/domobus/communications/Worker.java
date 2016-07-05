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
import java.util.LinkedList;

/**
 *
 * @author Rafael Afonso Rodrigues
 */
public class Worker extends Thread{
    
    private IDComm API;
    private Manager manager;
    private boolean processing;
    private LinkedList<byte[]> processingQueue;
    private LinkedList<byte[]> priorityQueue;
    
    public Worker(IDComm API, Manager manager) {
        this.API = API;
        this.manager = manager;
        processingQueue = new LinkedList<>();
        priorityQueue = new LinkedList<>();
    };
    
    public void Stop() {
        processing = false;
        System.out.println("Stopping Worker.");
    };
    
    @Override
    public void run() {
        manager.Log(Globals.LogType.SYSTEM, "Worker running.");
        
        processing = true;
        while(processing) {
            byte[] message;
            //retrieve next message
            message = priorityQueue.poll();
            if (message != null) {
                manager.Log(Globals.LogType.COMMAND, "Processing new command from priority queue.");
                ProcessMessage(message);
            }
            else {
                message = processingQueue.poll();
                if(message != null) {
                    manager.Log(Globals.LogType.COMMAND, "Processing new command from normal queue.");
                    ProcessMessage(message);
                }
                else try {
                    //queues are empty, sleep
                    Thread.sleep(Globals.VACATION_DURATION);
                    continue;
                } catch (InterruptedException ex) {
                    manager.Log(Globals.LogType.ERROR, "WorkerVacationException\t"+ex);
                }
            }            
        }
        manager.Log(Globals.LogType.SYSTEM, "Worker stopped.");
    };
    
    public boolean AddMessage(boolean priority, byte[] message) {
        //insert new message
        if(priority && priorityQueue.size()<Globals.PRIORITY_QUEUE_SIZE) priorityQueue.add(message);
        else if(processingQueue.size()<Globals.QUEUE_SIZE) processingQueue.add(message);
        
        //this is a response, insert despite queues limits
        else if(Globals.IsBitSet(message[6], Globals.ACK_POS)) {
            if(priority) priorityQueue.add(message);
            else processingQueue.add(message);
        }
        else {
            //queues are full, ignore
            manager.DComm_callback_process_ERROR(Globals.ERROR_QUEUES_FULL, message);
            return false;
        }
        
        return true;
    };
    
    public void ProcessMessage(byte[] message) {
        byte CTR, function;
        byte[] arguments;
        
        //check operation code
        int operation = (((message[6] << 5) & 0xff) >> 5);
        
        //retrieve application of origin
        int appId = Globals.BytesToInt(message, 3);
        
        try {
            switch(operation) {
                case Globals.GET_OPERATION:
                    //GET operation code
                    manager.Log(Globals.LogType.COMMAND, "Processing GET command from Supervisor "+appId+": "+Arrays.copyOfRange(message, 7, message.length-2));
                    ProcessGet(message);
                    break;

                case Globals.SET_OPERATION:
                    //SET operation code
                    manager.Log(Globals.LogType.COMMAND, "Processing SET command from Supervisor "+appId+": "+Arrays.copyOfRange(message, 7, message.length-2));
                    ProcessSet(message);
                    break;

                case Globals.NOTIFY_OPERATION:
                    //NOTIFY operation code
                    
                    manager.Log(Globals.LogType.COMMAND, "Received NOTIFY command from Supervisor "+appId+": "+Arrays.copyOfRange(message, 7, message.length-2));
                    ProcessNotify(Arrays.copyOfRange(message, 7, message.length-1));                    
                    
                    if(Globals.IsBitSet(message[6], Globals.ACK_POS)) {
                        manager.Log(Globals.LogType.COMMAND, "Received NOTIFY acknowledge from Supervisor "+appId);
                        break;
                    }
                    
                    //send back ACK
                    manager.Log(Globals.LogType.COMMAND, "Acknowledging NOTIFY command from Supervisor "+appId+": "+Arrays.copyOfRange(message, 7, message.length-2));
                    appId = Globals.BytesToInt(message, 3);
                    CTR = (byte) (message[6] + Globals.ACK_CTR);
                    manager.CreateMessage(appId, message[5], CTR, Arrays.copyOfRange(message, 7, message.length-2));
                    break;

                case Globals.EXEC_OPERATION:
                    function = message[8];
                    arguments = Arrays.copyOfRange(message, 9, message.length-1);
                    
                    //EXEC operation code, execute and set response's CTR accordingly
                    manager.Log(Globals.LogType.COMMAND, "Processing EXEC command from Supervisor "+appId+": "+Arrays.copyOfRange(message, 7, message.length-2));
                    
                    if(Globals.IsBitSet(message[6], Globals.ACK_POS)) {
                        manager.Log(Globals.LogType.COMMAND, "Received EXEC acknowledge from Supervisor "+appId);
                        API.DComm_callback_process_EXEC(function, arguments);
                        break;
                    }
                    
                    CTR = (byte) (message[6] + Globals.ACK_CTR);
                    if(!ProcessExec(appId, function, arguments)) {
                        manager.Log(Globals.LogType.COMMAND, "Responding to EXEC command with an error: "+Arrays.copyOfRange(message, 7, message.length-2));
                        CTR += Globals.ERROR_CTR;
                    }
                    else manager.Log(Globals.LogType.COMMAND, "Responding to EXEC command with an acknowledgement: "+Arrays.copyOfRange(message, 7, message.length-2));
                    
                    //send back response
                    manager.CreateMessage(appId, message[5], CTR, Arrays.copyOfRange(message, 7, message.length-2));
                    break;

                case Globals.DCOMM_OPERATION:
                    function = message[8];
                    arguments = Arrays.copyOfRange(message, 9, message.length-1);
                    
                    //DCOMM operation code, execute and set response's CTR accordingly
                    manager.Log(Globals.LogType.COMMAND, "Processing DCOMM command from Supervisor "+appId+": "+Arrays.copyOfRange(message, 7, message.length-2));
                    
                    if(Globals.IsBitSet(message[6], Globals.ACK_POS)) {
                        manager.Log(Globals.LogType.COMMAND, "Received DCOMM acknowledge from Supervisor "+appId);
                        break;
                    }
                    
                    CTR = (byte) (message[6] + Globals.ACK_CTR);
                    if(!ProcessDCOMM(appId, function, arguments)) {
                        manager.Log(Globals.LogType.COMMAND, "Responding to DCOMM command with an error: "+Arrays.copyOfRange(message, 7, message.length-2));
                        CTR += Globals.ERROR_CTR;
                    }
                    else manager.Log(Globals.LogType.COMMAND, "Responding to DCOMM command with an acknowledgement: "+Arrays.copyOfRange(message, 7, message.length-2));
                    
                    //send back response
                    manager.CreateMessage(appId, message[5], CTR, Arrays.copyOfRange(message, 7, message.length-2));
                    break;

                default:
                    //RESERVED operation code
                    manager.Log(Globals.LogType.COMMAND, "Processing RESERVED command of type "+operation+" from Supervisor "+appId+": "+Arrays.copyOfRange(message, 7, message.length-2));
                    
                    if(Globals.IsBitSet(message[6], Globals.ACK_POS)) {
                        manager.Log(Globals.LogType.COMMAND, "Received EXEC acknowledge from Supervisor "+appId);
                        API.DComm_callback_process_EXEC((byte)operation, Arrays.copyOfRange(message, 7, message.length-2));
                        break;
                    }
                    
                    CTR = (byte) (message[6] + Globals.ACK_CTR);
                    if (!ProcessReserved(appId, (byte)operation, Arrays.copyOfRange(message, 7, message.length-2))) {
                        manager.Log(Globals.LogType.COMMAND, "Responding to RESERVED command of type "+operation+" with an error: "+message);
                        CTR += Globals.ERROR_CTR;
                    }
                    else manager.Log(Globals.LogType.COMMAND, "Responding to RESERVED command of type "+operation+" with an acknowledgement: "+message);
                    
                    //send back response
                    manager.CreateMessage(appId, message[5], CTR, Arrays.copyOfRange(message, 7, message.length-2));              
                    break;
            }
        } catch(UnsupportedCommandException ex) {
            //command not supported, reply with error
            manager.Log(Globals.LogType.ERROR, "Unsupported command of type "+operation+" received from Supervisor "+appId+": "+message);
            
            //toggle ERROR bit in CTR field
            CTR = (byte) (Globals.CTR + Globals.ACK_CTR + Globals.ERROR_CTR);
            
            //send error
            appId = Globals.BytesToInt(message, 3);
            manager.CreateMessage(appId, message[5], CTR, new byte[]{Globals.ERROR_UNSUPPORTED_COMMAND});
        }
        
        //message processed, remove sequence number
        manager.RemoveSequence(Globals.BytesToInt(message, 1), message[5]);
    };
    
    public void ProcessGet(byte[] message) throws UnsupportedCommandException {
        //extract GET data fields
        byte[] DevAddrOrig = Arrays.copyOfRange(message, 7, 11);
        byte PropDescOrig = message[11];
        byte[] DevAddrDest = Arrays.copyOfRange(message, 12, 16);
        byte PropDescDest = message[16];
        byte[] value = Arrays.copyOfRange(message, 17, message.length-2);
        
        //received a GET response
        if(Globals.IsBitSet(message[6], Globals.ACK_POS)) {
            if(!Globals.IsBitSet(message[6], Globals.ERROR_POS)) {
                //received a successful response
                manager.Log(Globals.LogType.COMMAND, "Processed GET command response: "+Arrays.copyOfRange(message, 7, message.length-2));
                API.DComm_callback_process_ANSWER_GET(DevAddrOrig, PropDescOrig, DevAddrDest, PropDescDest, value);
                return;
            }
            else {
                //received an error
                manager.Log(Globals.LogType.COMMAND, "Processed GET command error response: "+Arrays.copyOfRange(message, 7, message.length-2));
                manager.DComm_callback_process_ERROR(Globals.ERROR_GET_COMMAND, message);
                return;
            }
        }
        
        //transmit GET command
        byte[] response = API.DComm_callback_process_GET(DevAddrOrig, PropDescOrig, DevAddrDest, PropDescDest);
        
        //prepare response
        byte CTR = (byte) (message[6] + Globals.ACK_CTR);
        byte[] data;
        
        //positive response
        if (response != null) {   
            //add response value
            data = new byte[10+response.length];
            System.arraycopy(response, 0, data, 10, response.length);
            manager.Log(Globals.LogType.COMMAND, "Responding to GET command with current value: "+data);
        }
        
        //error response
        else {
            //update CTR by toggling ERROR bit
            CTR += Globals.ERROR_CTR;
            //add null response value
            data = new byte[11];
            data[10] = 0;
            manager.Log(Globals.LogType.COMMAND, "Responding to GET command with an error: "+data);
        }

        //copy devices data
        System.arraycopy(message, 7, data, 0, 10);

        //send response
        int dest = Globals.BytesToInt(message, 3);
        manager.CreateMessage(dest, message[5], CTR, data);        
    };
    
    public void ProcessSet(byte[] message) throws UnsupportedCommandException {
        //extract SET data fields
        byte[] DevAddrOrig = Arrays.copyOfRange(message, 7, 11);
        byte PropDescOrig = message[11];
        byte[] DevAddrDest = Arrays.copyOfRange(message, 12, 16);
        byte PropDescDest = message[16];
        byte[] value = Arrays.copyOfRange(message, 17, message.length-2);
        
        //received a SET response
        if(Globals.IsBitSet(message[6], Globals.ACK_POS)) {        
            //received a SET error
            if(Globals.IsBitSet(message[6], Globals.ERROR_POS)) {
                manager.Log(Globals.LogType.COMMAND, "Processed SET command error response: "+Arrays.copyOfRange(message, 7, message.length-2));
                API.DComm_callback_process_ERROR(Globals.ERROR_SET_COMMAND, message);
            }
            else manager.Log(Globals.LogType.COMMAND, "Processed SET command response: "+Arrays.copyOfRange(message, 7, message.length-2));
            return;
        }
        
        //transmit SET command
        byte response = API.DComm_callback_process_SET(DevAddrOrig, PropDescOrig, DevAddrDest, PropDescDest, value);
        
        //set response CTR
        byte CTR = (byte) (message[6] + response);
        
        //add command data
        byte[] data = Arrays.copyOfRange(message, 7, message.length-1);

        //send response
        manager.Log(Globals.LogType.COMMAND, "Responding to SET command: "+data);
        int dest = Globals.BytesToInt(message, 3);
        manager.CreateMessage(dest, message[5], CTR, data);
        
        //Send notifications if there was not error
        if(!Globals.IsBitSet(CTR, Globals.ERROR_POS)) {
            int device = Globals.BytesToInt(DevAddrOrig, 2);
            manager.DComm_send_msg_NOTIFY(device, PropDescOrig, value, Globals.IsBitSet(CTR, Globals.PRIORITY_POS));
        }
    };
    
    public void ProcessNotify(byte[] data) throws UnsupportedCommandException {
        //extract NOTIFY fields
        byte[] DevAddr = Arrays.copyOf(data, 4);
        byte PropDesc = data[4];
        byte[] value = Arrays.copyOfRange(data, 5, data.length);
        
        //transmit NOTIFY command
        API.DComm_callback_process_NOTIFY(DevAddr, PropDesc, value);
    };
    
    public boolean ProcessExec(int appId, byte function, byte[] arguments) throws UnsupportedCommandException {
        //transmit EXEC commands handled by supervisor or devices
        return API.DComm_callback_process_EXEC(function, arguments);
    };
    
    public boolean ProcessDCOMM(int appId, byte function, byte[] arguments) throws UnsupportedCommandException {
        int device, prop;
        
        switch(function) {
            case Globals.DCOMM_SUBSCRIBE:                
                if(arguments.length != 3) return false;
                device = Globals.BytesToInt(arguments, 0);
                prop = arguments[2];
                
                manager.NewSubscription(appId, device, prop);
                return true;
                
            case Globals.DCOMM_UNSUBSCRIBE:
                if(arguments.length != 3) return false;
                device = Globals.BytesToInt(arguments, 0);
                prop = arguments[2];
                
                manager.RemoveSubscription(appId, device, prop);
                return true;
                
            case Globals.DCOMM_UNSUBSCRIBE_ALL:
                manager.Unsubscribe(appId);
                return true;
                
            case Globals.DCOMM_LIST_PUBLISHERS:
                //list of publishers returned by supervisor
                return API.DComm_callback_process_EXEC(function, arguments);
                
            default:
                //unsupported DCOMM function
                break;
        }
        return false;
    }
    
    public boolean ProcessReserved(int appId, byte operation, byte[] data) throws UnsupportedCommandException {
        //transmit custom command
        return API.DComm_callback_process_RESERVED(operation, data);
    };
}