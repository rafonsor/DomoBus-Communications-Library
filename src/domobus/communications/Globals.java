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

import java.nio.ByteOrder;

public class Globals {
    
    //Configuration File
    public static final String DEFAULT_CONFIG_FILE = "dcomm.cfg";
    public static final String PREFIX_APP_ID = "APP_ADDR";
    public static final String PREFIX_APP_PORT = "APP_PORT";
    public static final String PREFIX_PEERS = "ADDR_MAP";
    public static final String PREFIX_SUBSCRIBERS = "SUBSCRIBE";
    public static final String PREFIX_DNS = "DNS";
    
    //Operation codes
    public static final int GET_OPERATION = 0x00;
    public static final int SET_OPERATION = 0x01;
    public static final int NOTIFY_OPERATION = 0x02;
    public static final int EXEC_OPERATION = 0x03;
    public static final int DNS_OPERATION = 0x07;
    public static final int DCOMM_OPERATION = 0x07;
    
    //CTR field
    public static final int CTR = 0x80;
    //CTR Modifiers
    public static final int PRIORITY_CTR = 0x40;
    public static final int RETRANSMISSION_CTR = 0x20;
    public static final int ERROR_CTR = 0x10;
    public static final int ACK_CTR = 0x08;
    //CTR positions
    public static final int PRIORITY_POS = 6;
    public static final int RETRANSMISSION_POS = 5;
    public static final int ERROR_POS = 4;
    public static final int ACK_POS = 3;
    
    //Values Masks
    public static final int MASK_8BIT = 0x00;
    public static final int MASK_16BIT = 0x40;
    public static final int MASK_ARRAY = 0x80;
    public static final int MASK_RESERVED = 0xC0;
    public static final int INVALID_VALUE = 0x20;
    
    //Error codes
    public static final int ERROR_QUEUES_FULL = 0x00;
    public static final int ERROR_UNSUPPORTED_COMMAND = 0x01;
    public static final int ERROR_TRANSMISSION_FAILED = 0x02;
    public static final int ERROR_TIMEOUT = 0x03;
    public static final int ERROR_NOT_FOUND = 0x04;
    public static final int ERROR_GET_COMMAND = 0x10;
    public static final int ERROR_SET_COMMAND = 0x20;
    public static final int ERROR_NOTIFY_COMMAND = 0x30;
    public static final int ERROR_EXEC_COMMAND = 0x40;
    public static final int ERROR_RESERVED_COMMAND = 0x50;
    public static final int ERROR_DCOMM_COMMAND = 0x80;
    
    //Defaults
    public static final int QUEUE_SIZE = 100;
    public static final int PRIORITY_QUEUE_SIZE = 25;
    public static final int VACATION_DURATION = 100; //pausing duration, in milliseconds, when message queues are empty
    public static final int RESTRANSMISSION_PERIOD = 1000;
    public static final int MAX_RETRANSMISSIONS = 5;
    public static final int MAX_PACKET_LENGTH = 255;
    public static final int MIN_PACKET_LENGTH = 8;
    public static final int CONNECTION_TIMEOUT = 5000;
    public static final int BACKUP_PERIOD = 1800000; //configuration file persisted every 30 minutes
    
    //Logs
    public static enum LogType {SYSTEM, ERROR, COMMAND, DNS};
    public static final String LOG_FOLDER = "logs";
    public static final String LOG_EXTENSION = ".log";
    public static final long LOG_PERIOD = 86400;
    
    //DNS
    public static final int DNS_REQUEST_TIMEOUT = 5000;
    public static final int DNS_REGISTER = 0x00;
    public static final int DNS_GET = 0x01;
    
    //DCOMM Commands
    public static final int DCOMM_SUBSCRIBE = 0x02;
    public static final int DCOMM_UNSUBSCRIBE = 0x03;
    public static final int DCOMM_UNSUBSCRIBE_ALL = 0x04;
    public static final int DCOMM_LIST_PUBLISHERS = 0x05;
    
    
    //Auxilliary Functions
    static public boolean IsBitSet(byte data, int position) {
        return (data >> position & 1) == 1;
    }
    
    static public byte CRC8(byte[] data) {
        int temp, res = 0;
        for(int i = 0; i < data.length; i++) {
            temp = res << 1;
            temp += 0xff & data[i];
            res = ((temp & 0xff) + (temp >> 8)) & 0xff;
        }
        return (byte)res;
    };
    
    private static final byte DCOMM_CHECKSUM_SEED = 0x5A;
    static public byte CKS(byte[] data) {
        byte cks = DCOMM_CHECKSUM_SEED;
        for(byte b: data) cks += b;
        return cks;
    };
    
    private static final ByteOrder ENDIANNESS = ByteOrder.BIG_ENDIAN;
    static public int BytesToInt(byte[] input, int offset) {
        //Big Endian encoding
        if(ENDIANNESS == ByteOrder.BIG_ENDIAN) return (input[offset] << 8)| input[offset+1] & 0xFF;
        
        //Little Endian encoding
        else return (input[1] << 8) | input[0] & 0xFF;
    };     
    static public void IntToBytes(int input, int offset, byte[] output) {
        //Big Endian encoding
        if(ENDIANNESS == ByteOrder.BIG_ENDIAN){
            output[offset] = (byte) (input >>> 8);
            output[offset+1] = (byte) (input);
        }
        //Little Endian encoding
        else {
            output[offset] = (byte) (input);
            output[offset+1] = (byte) (input >>> 8);
        }
    };
    
}