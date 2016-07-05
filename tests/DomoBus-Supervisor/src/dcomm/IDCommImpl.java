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

package dcomm;


public class IDCommImpl implements IDComm{

    /////Incomming Methods/////
    @Override
    public byte[] DComm_callback_process_GET(byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest) {
        System.out.println("Get Command received.");
        System.out.println("From device: "+DevAddrOrig);
        System.out.println("Requesting value of property "+PropDescDest+" of device "+DevAddrDest);
        byte[] value = new byte[]{55};
        System.out.println("Current value: "+value);
        return value;
    }

    @Override
    public void DComm_callback_process_ANSWER_GET(byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value) throws UnsupportedCommandException {
        System.out.println("Get Answer received.");
        System.out.println("For device: "+DevAddrOrig);
        System.out.println("Responding with the value of property "+PropDescDest+" of device "+DevAddrDest);
        System.out.println("Current value: "+value);
    }

    @Override
    public byte DComm_callback_process_SET(byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value) {
        System.out.println("Set Command received.");
        System.out.println("From device: "+DevAddrOrig);
        System.out.println("Updating property "+PropDescDest+" of device "+DevAddrDest);
        System.out.println("Setting value to: "+value);
        return Globals.ACK_CTR;
    }

    @Override
    public void DComm_callback_process_NOTIFY(byte[] DevAddr, byte PropDesc, byte[] value) throws UnsupportedCommandException {
        System.out.println("Notification received.");
        System.out.println("Device address: "+DevAddr);
        System.out.println("Property description: "+PropDesc);
        System.out.println("Value: "+value);
    }

    @Override
    public boolean DComm_callback_process_EXEC(byte command, byte[] arguments) throws UnsupportedCommandException {
        System.out.println("Exec Command received.");
        System.out.println("Command requested: "+command);
        System.out.println("Arguments given: "+arguments);
        return true;
    }

    @Override
    public boolean DComm_callback_process_RESERVED(byte operation, byte[] data) throws UnsupportedCommandException {
        throw new UnsupportedOperationException("Reserved Commands Not supported yet.");
    }

    @Override
    public void DComm_callback_process_ERROR(int errorCode, byte[] message) throws UnsupportedCommandException {
        System.out.println("Error response received.");
        System.out.println("Error code: "+errorCode);
        System.out.println("Message concerned: "+message);
    }
    
    /////Outgoing Methods - Implemented by the Manager /////
    @Override
    public void DComm_send_msg_GET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, boolean priority) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    @Override
    public void DComm_send_msg_SET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value, boolean priority) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    @Override
    public void DComm_send_msg_NOTIFY(int Dev, byte PropDesc, byte[] value, boolean priority) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    @Override
    public void DComm_send_msg_EXEC(int appId, byte command, byte[] arguments, boolean priority) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    @Override
    public void DComm_send_msg_DCOMM(int appId, byte command, byte[] arguments, boolean priority) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    @Override
    public void DComm_send_msg_RESERVED(int appId, byte operation, byte[] data, boolean priority) {
        throw new UnsupportedOperationException("Reserved Commands Not supported yet.");
    }
    @Override
    public Object[] DComm_send_sync_msg_GET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, boolean priority) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    @Override
    public Object[] DComm_send_sync_msg_SET(int appId, byte[] DevAddrOrig, byte PropDescOrig, byte[] DevAddrDest, byte PropDescDest, byte[] value, boolean priority) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    @Override
    public byte DComm_send_sync_msg_NOTIFY(int Dev, byte PropDesc, byte[] value, boolean priority) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    @Override
    public Object[] DComm_send_sync_msg_EXEC(int appId, byte command, byte[] arguments, boolean priority) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    @Override
    public Object[] DComm_send_sync_msg_DCOMM(int appId, byte command, byte[] arguments, boolean priority) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    @Override
    public Object[] DComm_send_sync_msg_RESERVED(int appId, byte operation, byte[] data, boolean priority) {
        throw new UnsupportedOperationException("Reserved Commands Not supported yet.");
    }
}
