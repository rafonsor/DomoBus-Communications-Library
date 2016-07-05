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

package domobusnameserver;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import domobus.communications.Globals;
import domobus.communications.Logger;

public class DomoBusNameServer {

    public static void main(String[] args) throws SocketException, IOException {
        
        System.out.println("Launching DomoBus Name Server");
        int port = 21000;
        for(int i = 0; i < args.length; i+=2) {
            if(args[i].equals("-port")) port = Integer.parseInt(args[i+1]);
        }
        
        Logger logger = new Logger();
        Map<Integer, byte[]> supervisors = new HashMap<>();
        
        DatagramSocket serverSocket = new DatagramSocket(port);
        int appId = -1;
        byte[] response;
        
        System.out.println("DomoBus Name Server is operating and listening to port "+port);
        while(true) {
            System.out.println("");
            try {
                //wait for a new request
                DatagramPacket packet = new DatagramPacket(new byte[Globals.MAX_PACKET_LENGTH], Globals.MAX_PACKET_LENGTH);
                serverSocket.receive(packet);
                System.out.println("Processing new request.");
                
                byte[] message = Arrays.copyOf(packet.getData(), packet.getLength());
                
                
                System.out.println(message.length < Globals.MIN_PACKET_LENGTH);
                System.out.println(message.length != message[0]);
                System.out.println(message.length+" vs "+message[0]);
                System.out.println(Globals.CKS(Arrays.copyOf(message, message.length-1)) != message[message.length-1]);
                System.out.println(Globals.CKS(Arrays.copyOf(message, message.length-1))+" vs "+message[message.length-1]);
                
                //check packet format and integrity
                if(message.length < Globals.MIN_PACKET_LENGTH || message.length != message[0] || Globals.CKS(Arrays.copyOf(message, message.length-1)) != message[message.length-1]) throw new IOException();
                
                switch(message[8]) {
                    case Globals.DNS_REGISTER:
                        byte[] peer = new byte[6];
                        
                        //try to retrieve remote data
                        try {
                            //retrieve id
                            appId = Globals.BytesToInt(message, 3);
                            
                            //retrieve ip
                            System.arraycopy(packet.getAddress().getAddress(),0,peer,0,4);

                            //retrieve port
                            System.arraycopy(message, 9, peer, 4, 2);
                            int remotePort = Globals.BytesToInt(peer, 4);
                            if(remotePort <1 || remotePort > 65535) throw new Exception();
                            
                            //save peer
                            supervisors.put(appId, peer);
                            
                            //send back ACK
                            response = new byte[8];
                            response[0] = 8;
                            response[1] = message[3];
                            response[2] = message[4];
                            response[3] = message[1];
                            response[4] = message[2];
                            response[5] = message[5];
                            response[6] = (byte) (message[6] + Globals.ACK_CTR);
                            response[7] = Globals.CKS(Arrays.copyOf(response, response.length-1));
                            
                            serverSocket.send(new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort()));
                            System.out.println("Registered Supervisor"+ appId+"- "+packet.getAddress().getAddress()+":"+remotePort+".");
                            logger.Log(Globals.LogType.DNS, "Registered Supervisor"+ appId+"- "+packet.getAddress().getAddress()+":"+remotePort+".");
                            
                        } catch (Exception ex) {
                            //send back ERROR response
                            response = new byte[9];
                            response[0] = 8;
                            response[1] = message[3];
                            response[2] = message[4];
                            response[3] = message[1];
                            response[4] = message[2];
                            response[5] = message[5];
                            response[6] = (byte) (message[6] + Globals.ACK_CTR + Globals.ERROR_CTR);
                            response[7] = (byte) Globals.ERROR_DCOMM_COMMAND;
                            response[8] = Globals.CKS(Arrays.copyOf(response, response.length-1));
       
                            serverSocket.send(new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort()));
                            if(appId < 0) {
                                System.out.println("Invalid request received from Supervisor"+ appId+".");
                                logger.Log(Globals.LogType.DNS, "Invalid request from Supervisor"+ appId+": "+message+".");
                            }
                            else {
                                System.out.println("Invalid request received from an unknown peer.");
                                logger.Log(Globals.LogType.DNS, "Invalid request from an unknown peer: "+message+".");
                            }
                        
                        }
                        appId = -1;
                        break;
                        
                    case Globals.DNS_GET:
                        try {
                            //retrieve requested id
                            appId = Globals.BytesToInt(message, 9);
                            if(appId < 0) throw new Exception();
                            
                            //verify if its a known peer
                            if(!supervisors.containsKey(appId)) throw new NoSuchFieldException();
                            
                            //send back ACK
                            response = new byte[16];
                            response[0] = 0;
                            response[1] = message[3];
                            response[2] = message[4];
                            response[3] = message[1];
                            response[4] = message[2];
                            response[5] = message[5];
                            response[6] = (byte) (message[6] + Globals.ACK_CTR);
                            response[7] = 8;
                            response[8] = Globals.DNS_GET;
                            System.arraycopy(supervisors.get(appId), 0, response, 9, 6);
                            response[response.length-1] = Globals.CKS(Arrays.copyOf(response, response.length-1));
                            
                            serverSocket.send(new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort()));
                            System.out.println("Fulfilled request for Supervisor"+ appId+" address.");
                            logger.Log(Globals.LogType.DNS, "Fulfilled request for Supervisor"+ appId+" address.");
                            
                        } catch(NoSuchFieldException ex) {
                            //request Supervisor was not found, send back ERROR response
                            response = new byte[9];
                            response[0] = 8;
                            response[1] = message[3];
                            response[2] = message[4];
                            response[3] = message[1];
                            response[4] = message[2];
                            response[5] = message[5];
                            response[6] = (byte) (message[6] + Globals.ACK_CTR + Globals.ERROR_CTR);
                            response[7] = (byte) Globals.ERROR_NOT_FOUND;
                            response[response.length-1] = Globals.CKS(Arrays.copyOf(response, response.length-1));
       
                            serverSocket.send(new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort()));
                            System.out.println("Requested Supervisor"+ appId+" was not found.");
                            logger.Log(Globals.LogType.DNS, "Requested Supervisor"+ appId+" was not found.");
                            
                        } catch (Exception ex) {
                            //send back ERROR response
                            response = new byte[9];
                            response[0] = 8;
                            response[1] = message[3];
                            response[2] = message[4];
                            response[3] = message[1];
                            response[4] = message[2];
                            response[5] = message[5];
                            response[6] = (byte) (message[6] + Globals.ACK_CTR + Globals.ERROR_CTR);
                            response[7] = (byte) Globals.ERROR_DCOMM_COMMAND;
                            response[response.length-1] = Globals.CKS(Arrays.copyOf(response, response.length-1));
       
                            serverSocket.send(new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort()));
                            if(appId < 0) {
                                System.out.println("Invalid request received from Supervisor"+ appId+".");
                                logger.Log(Globals.LogType.DNS, "Invalid request from Supervisor"+ appId+": "+message+".");
                            }
                            else {
                                System.out.println("Invalid request received from an unknown peer.");
                                logger.Log(Globals.LogType.DNS, "Invalid request from an unknown peer: "+message+".");
                            }                        
                        }
                        appId = -1;
                        break;
                        
                    default:
                        response = message;
                        response[6] += Globals.ACK_CTR + Globals.ERROR_CTR;
                        response[response.length-1] = Globals.CKS(Arrays.copyOf(response, response.length-1));
                        serverSocket.send(new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort()));
                }                
                
            } catch (IOException ex) {
                System.out.println("Invalid request received.");                
            }
        }
    }
}
