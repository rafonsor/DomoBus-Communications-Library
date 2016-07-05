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


class UnsupportedCommandException extends Exception{
    public UnsupportedCommandException() {}  
    public UnsupportedCommandException(String msg) { super(msg); }  
    public UnsupportedCommandException(Throwable cause) { super(cause); }  
    public UnsupportedCommandException(String msg, Throwable cause) { super(msg, cause); } 
}

public interface IDComm {
    
    //Outgoing Asynchronous Commands
    public void DComm_send_msg_GET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, boolean priority) throws UnsupportedCommandException;
    public void DComm_send_msg_SET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value, boolean priority) throws UnsupportedCommandException;
    public void DComm_send_msg_NOTIFY(int Dev, byte PropDesc, byte[] value, boolean priority) throws UnsupportedCommandException;
    public void DComm_send_msg_EXEC(int appId, byte command, byte[] arguments, boolean priority) throws UnsupportedCommandException;
    public void DComm_send_msg_DCOMM(int appId, byte command, byte[] arguments, boolean priority) throws UnsupportedCommandException;
    public void DComm_send_msg_RESERVED(int appId, byte operation, byte[] data, boolean priority) throws UnsupportedCommandException;
    
    //Outgoing Synchronous Commands
    public Object[] DComm_send_sync_msg_GET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, boolean priority) throws UnsupportedCommandException;
    public Object[] DComm_send_sync_msg_SET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value, boolean priority) throws UnsupportedCommandException;
    public byte DComm_send_sync_msg_NOTIFY(int Dev, byte PropDesc, byte[] value, boolean priority) throws UnsupportedCommandException;
    public Object[] DComm_send_sync_msg_EXEC(int appId, byte command, byte[] arguments, boolean priority) throws UnsupportedCommandException;
    public Object[] DComm_send_sync_msg_DCOMM(int appId, byte command, byte[] arguments, boolean priority) throws UnsupportedCommandException;
    public Object[] DComm_send_sync_msg_RESERVED(int appId, byte operation, byte[] data, boolean priority) throws UnsupportedCommandException;
    
    //Incomming Commands
    public byte[] DComm_callback_process_GET(byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest) throws UnsupportedCommandException;
    public void DComm_callback_process_ANSWER_GET(byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value) throws UnsupportedCommandException;
    public byte DComm_callback_process_SET(byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value) throws UnsupportedCommandException;
    public void DComm_callback_process_NOTIFY(byte[] DevAddr, byte PropDesc, byte[] value) throws UnsupportedCommandException;
    public boolean DComm_callback_process_EXEC(byte command, byte[] arguments) throws UnsupportedCommandException;
    public boolean DComm_callback_process_RESERVED(byte operation, byte[] data) throws UnsupportedCommandException;
    public void DComm_callback_process_ERROR(int errorCode, byte[] message) throws UnsupportedCommandException;
    
}
