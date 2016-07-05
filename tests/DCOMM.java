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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Scanner;


public class DCOMM {
    
    private static int id;
    private static boolean ASYNC = true;
    
    public static void main(String[] args) throws UnknownHostException {
        
        String configurationFile = Globals.DEFAULT_CONFIG_FILE;
        String commandsFile = null;
        
        //Check eventual options added at runtime
        if (args.length > 0) {
            if ((args.length == 1) && args[0].equals("-help")) PrintHelp();
            
            else if ((args.length % 2) == 0) {
                for(int i = 0; i < args.length; i=i+2) {
                    switch(args[i]) {
                        case "-config":
                            File config = new File(args[i+1]);
                            if(config.exists() && config.isFile()) configurationFile = args[i+1];
                            else BadArguments();
                            break;
                            
                        case "-execute":
                            File commands = new File(args[i+1]);
                            if(commands.exists() && commands.isFile()) commandsFile = args[i+1];
                            else BadArguments();
                            break;
                            
                        default:
                            BadArguments();
                            break;
                    }
                }
            }
            else BadArguments();
        }
        
        try {
            //Start DCOMM
            System.out.println("Starting DomoBus Communications API v1");
            IDCommImpl API = new IDCommImpl();
            System.out.println("Initializing Communications Manager.");
            Manager MGR = new Manager(API, configurationFile);
            id = MGR.SelfId();
            
            System.out.println("Launching Communications Manager.");
            MGR.start();
            Thread.sleep(250); //wait for Manager thread
            
            System.out.println("API is operating.");
            Scanner scanner;
            
            if(commandsFile != null) {
                System.out.println("");
                System.out.println("Executing file received.");
                ExecuteFromFile(MGR, commandsFile);
            }
            
            String command, commandType;
            int appDest = -1, devOrig, devDest;
            byte[] devAddrOrig = new byte[4], devAddrDest = new byte[4], value, arguments;
            byte propDescOrig, propDescDest, function;
            Object[] response;
            
            Globals.IntToBytes(id, 0, devAddrOrig);
            
            while(true) {
                scanner = new Scanner(System.in);
                String os = System.getProperty("os.name");
                if(os != null) {
                    if(os.startsWith("Win")) {
                        System.out.println("");
                        System.out.println("");
                        System.out.println("");
                        System.out.println("");
                    }
                    else Runtime.getRuntime().exec("cls");
                }
                
                System.out.println("DomoBus Communications API v1.");
                System.out.println("");
                System.out.println("Available Commands:");
                System.out.println("\t- CMD : to type and send a new command.");
                System.out.println("\t- EXECUTE : to execute a list of commands from a file.");
                System.out.println("\t- EXIT : to shutdown all services.");
                System.out.println("");
                
                command = scanner.nextLine();
                System.out.println("");
                switch(command) {
                    case "EXECUTE":
                        System.out.println("State the file you wish to execute:");
                        commandsFile = scanner.nextLine();
                        File temp = new File(commandsFile);
                        System.out.println("File specified: "+commandsFile);
                        if(temp.exists() && temp.isFile()) ExecuteFromFile(MGR, commandsFile);
                        else System.out.println("The file specified is invalid, aborting.");
                        break;                        
                    
                    case "CMD":
                        try {
                            if(appDest > -1) {
                                System.out.println("Keep current destinatary application, "+appDest+"? (Y/N)");
                                String choice = scanner.nextLine();
                                if(!choice.startsWith("Y") && !choice.startsWith("y")) appDest = -1;
                            }
                            if(appDest < 0) {
                                System.out.print("Enter the desired destination application: ");
                                appDest = scanner.nextInt();
                                scanner.nextLine(); //flush newline character
                                if(appDest < 0 || appDest == id) {
                                    System.out.println("Invalid destination application ID");
                                    break;
                                }
                            }
                            Globals.IntToBytes(appDest, 0, devAddrDest);

                            System.out.println("Select the command type you wish to execute:");
                            System.out.println("- 'GET': for getting the value of a property.");
                            System.out.println("- 'SET': for setting the value of a property.");
                            System.out.println("- 'NOTIFY': for notifying the value of a property to another device.");
                            System.out.println("- 'EXEC': for sending commands to another application.");
                            System.out.println("- 'DCOMM': for sending commands to another DomoBus Communications API.");
                            System.out.print("Choice: ");
                            commandType = scanner.nextLine();

                            switch(commandType) {
                                case "GET":
                                    System.out.println("Preparing GET command.");
                                    
                                    System.out.print("Enter the origin device id: ");
                                    devOrig = scanner.nextInt();
                                    scanner.nextLine(); //flush newline character
                                    System.out.print("Enter the origin device's property: ");
                                    propDescOrig = (byte) scanner.nextInt();
                                    scanner.nextLine(); //flush newline character
                                    
                                    System.out.print("Enter the destination device id: ");
                                    devDest = scanner.nextInt();
                                    scanner.nextLine(); //flush newline character
                                    System.out.print("Enter the destination's property: ");
                                    propDescDest = (byte) scanner.nextInt();
                                    scanner.nextLine(); //flush newline character

                                    Globals.IntToBytes(devOrig, 2, devAddrOrig);
                                    Globals.IntToBytes(devDest, 2, devAddrDest);

                                    MGR.DComm_send_msg_GET(appDest, devAddrOrig, propDescOrig, devAddrDest, propDescDest, false);
                                    System.out.print("Command has been sent.");
                                    break;

                                case "SET":
                                    System.out.println("Preparing SET command.");
                                    
                                    System.out.print("Enter the origin device id: ");
                                    devOrig = scanner.nextInt();
                                    scanner.nextLine(); //flush newline character
                                    System.out.print("Enter the origin device's property: ");
                                    propDescOrig = (byte) scanner.nextInt();
                                    scanner.nextLine(); //flush newline character
                                    
                                    System.out.print("Enter the destination device id: ");
                                    devDest = scanner.nextInt();
                                    scanner.nextLine(); //flush newline character
                                    System.out.print("Enter the destination's property: ");
                                    propDescDest = (byte) (scanner.nextInt() + Globals.MASK_16BIT);
                                    scanner.nextLine(); //flush newline character

                                    Globals.IntToBytes(devOrig, 2, devAddrOrig);
                                    Globals.IntToBytes(devDest, 2, devAddrDest);

                                    System.out.print("Enter the new value: ");
                                    int v = scanner.nextInt();
                                    scanner.nextLine(); //flush newline character
                                    value = new byte[2];
                                    Globals.IntToBytes(v, 0, value);
                                    
                                    MGR.DComm_send_msg_SET(appDest, devAddrOrig, propDescOrig, devAddrDest, propDescDest, value, false);
                                    System.out.print("Command has been sent.");
                                    break;

                                case "NOTIFY":
                                    System.out.println("Preparing NOTIFY command.");
                                    
                                    System.out.print("Enter which device to choose: ");
                                    devDest = scanner.nextInt();
                                    scanner.nextLine(); //flush newline character

                                    System.out.print("Select the wanted property for notification: ");
                                    propDescDest = (byte) (scanner.nextByte() + Globals.MASK_16BIT);

                                    System.out.print("Enter the current value of that property: ");
                                    v = scanner.nextInt();
                                    scanner.nextLine(); //flush newline character
                                    value = new byte[2];
                                    Globals.IntToBytes(v, 0, value);

                                    MGR.DComm_send_msg_NOTIFY(devDest, propDescDest, value, false);
                                    System.out.print("Command has been sent.");
                                    break;

                                case "EXEC":
                                    System.out.println("Preparing EXEC command.");
                                    
                                    System.out.print("Enter which function to execute: ");
                                    function = (byte) scanner.nextInt();
                                    scanner.nextLine(); //flush newline character
                                    
                                    System.out.print("Enter the arguments: ");
                                    arguments = scanner.nextLine().getBytes();

                                    MGR.DComm_send_msg_EXEC(appDest, function, arguments, true);
                                    System.out.print("Command has been sent.");
                                    break;

                                case "DCOMM":
                                    System.out.println("Preparing DCOMM command.");
                                    
                                    System.out.print("Enter which DCOMM function to execute: ");
                                    function = (byte) scanner.nextInt();
                                    scanner.nextLine(); //flush newline character
                                    
                                    System.out.print("Enter the arguments: ");
                                    arguments = scanner.nextLine().getBytes();

                                    MGR.DComm_send_msg_DCOMM(appDest, function, arguments, true);
                                    System.out.print("Command has been sent.");
                                    break;

                                default:
                                    System.out.println("Unsupported command type.");
                                    break;   
                            }
                        } catch(Exception ex) {
                            System.out.println("Incorrect data type detected, cancelling command.");
                        }
                        Thread.sleep(500);
                        break;
                        
                    case "EXIT":
                        System.out.println("Stopping all services.");
                        MGR.Disconnect();
                        System.out.println("Exiting.");
                        System.exit(0);
                        break;
                        
                    default:
                        System.out.println("Unrecognized command entered, please repeat.");
                        break;                        
                }                
            }
            
        } catch (FileNotFoundException ex) {
            System.out.println("Configuration file not found.");
            System.exit(-1);
        } catch (IOException ex) {
            System.out.println("Exception while reading from configuration file.");
            System.out.println(ex);
            System.exit(-1);
        } catch (InstantiationException ex) {
            System.out.println("Currupt configuration file, missing mandatory properties.");
            System.out.println(ex);
            System.exit(-1);
        } catch (Exception ex) {
            System.out.println(ex);
            System.exit(-1);
        }
    }
    
    public static void ExecuteFromFile(Manager MGR, String file) {
        MGR.Log(Globals.LogType.COMMAND, "Executing commands file: "+file);
        
        Object[] result;
        
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String command;
            String[] tokens;
            int appId = -1, devOrig, devDest, propOrig, propDest;
            byte[] devAddrOrig = new byte[4], devAddrDest = new byte[4], value, args;
            byte propDescOrig, propDescDest, cmd;
            
            Globals.IntToBytes(id, 0, devAddrOrig);
            
            while((command = br.readLine()) != null) {
                tokens = command.split(" ");
                if(tokens.length == 0) continue;

                switch(tokens[0]) {
                    case "SD":
                        appId = Integer.parseInt(tokens[1]);
                        System.out.println("Set destination application to "+appId);
                        Globals.IntToBytes(appId, 0, devAddrOrig);
                        break;

                    case "GET":
                        System.out.println("Sending a new GET command: "+command);
                        
                        //Set origin device
                        devOrig = Integer.parseInt(tokens[1]);
                        Globals.IntToBytes(devOrig, 2, devAddrOrig);
                        
                        //Set origin device's property
                        propDescOrig = (byte) Integer.parseInt(tokens[2]);
                        
                        //Set destination device
                        devDest = Integer.parseInt(tokens[3]);
                        Globals.IntToBytes(devDest, 2, devAddrDest);
                        
                        //Set destination device's property
                        propDescDest = (byte) Integer.parseInt(tokens[4]);
                        
                        //Send asynchronous command
                        if(ASYNC) MGR.DComm_send_msg_GET(appId, devAddrOrig, propDescOrig, devAddrDest, propDescDest, true);
                        
                        //Send synchronous command
                        else {
                            result = MGR.DComm_send_sync_msg_GET(appId, devAddrOrig, propDescOrig, devAddrDest, propDescDest, true);
                            if(Globals.IsBitSet((byte) (int)result[0], Globals.ERROR_POS)) System.out.println("GET Command resulted in an error.");
                            else System.out.println("ACK received for the GET command.");
                            System.out.println("Value received: "+result[1]);
                        }
                        break;

                    case "SET":
                        System.out.println("Sending a new SET command: "+command);
                        
                        //Set origin device
                        devOrig = Integer.parseInt(tokens[1]);
                        Globals.IntToBytes(devOrig, 2, devAddrOrig);
                        
                        //Set origin device's property
                        propDescOrig = (byte) Integer.parseInt(tokens[2]);
                        
                        //Set destination device
                        devDest = Integer.parseInt(tokens[3]);
                        Globals.IntToBytes(devDest, 2, devAddrDest);
                        
                        //Set destination device's property
                        propDescDest = (byte) Integer.parseInt(tokens[4]);
                        
                        //Retrieve value
                        value = tokens[5].getBytes();
                        switch(value.length) {
                            case 1:
                                propDescDest += Globals.MASK_8BIT;
                                break;
                            case 2:
                                propDescDest += Globals.MASK_16BIT;
                                break;
                            default:
                                propDescDest += Globals.MASK_ARRAY;
                                break;
                        }
                        
                        //Send asynchronous command
                        if(ASYNC)MGR.DComm_send_msg_SET(appId, devAddrOrig, propDescOrig, devAddrDest, propDescDest, value, true);
                        
                        //Send synchronous command
                        else {
                            result = MGR.DComm_send_sync_msg_GET(appId, devAddrOrig, propDescOrig, devAddrDest, propDescDest, true);
                            if(Globals.IsBitSet((byte) (int)result[0], Globals.ERROR_POS)) System.out.println("SET Command resulted in an error: "+result[1]);
                            else System.out.println("ACK received for the SET command.");
                        }
                        break;

                    case "NOTIFY":
                        System.out.println("Sending a new NOTIFY command: "+command);
                        
                        //Set originating device
                        devOrig = Integer.parseInt(tokens[1]);
                        
                        //Set originating device's property
                        propDescOrig = (byte) Integer.parseInt(tokens[2]);
                        
                        //Retrieve value
                        value = tokens[3].getBytes();
                        switch(value.length) {
                            case 1:
                                propDescOrig += Globals.MASK_8BIT;
                                break;
                            case 2:
                                propDescOrig += Globals.MASK_16BIT;
                                break;
                            default:
                                propDescOrig += Globals.MASK_ARRAY;
                                break;
                        }
                        
                        //Send command
                        MGR.DComm_send_msg_NOTIFY(devOrig, propDescOrig, value, true);
                        break;

                    case "EXEC":
                        System.out.println("Sending a new EXEC command: "+command);
                        
                        //Get command
                        cmd = (byte) Integer.parseInt(tokens[1]);
                        
                        //Get arguments
                        if(tokens.length == 3) {
                            args = new byte[tokens[2].length()];
                            //we want the byte of the decimal value and not of the numeric character
                            for(int i = 0; i < args.length; i++) args[i] = (byte) Integer.parseInt((String.valueOf(tokens[2].charAt(i))));
                        }
                        else args = new byte[0];
                        
                        //Send asynchronous command
                        if(ASYNC) MGR.DComm_send_msg_EXEC(appId, cmd, args, true);
                        
                        //Send synchronous command
                        else {
                            result = MGR.DComm_send_sync_msg_EXEC(appId, cmd, args, true);
                            if(Globals.IsBitSet((byte) (int)result[0], Globals.ERROR_POS)) System.out.println("EXEC Command resulted in an error: "+result[1]);
                            else System.out.println("ACK received for the EXEC command.");
                        }
                        break;

                    case "DCOMM":
                        System.out.println("Sending a new DCOMM command: "+command);
                        
                        //Get command
                        cmd = (byte) Integer.parseInt(tokens[1]);
                        
                        //Get arguments
                        if(tokens.length == 3) {
                            args = new byte[tokens[2].length()];
                            //we want the byte of the decimal value and not of the numeric character
                            for(int i = 0; i < args.length; i++) args[i] = (byte) Integer.parseInt((String.valueOf(tokens[2].charAt(i))));
                        }
                        else args = new byte[0];
                        
                        //Send command
                        MGR.DComm_send_msg_DCOMM(appId, cmd, args, true);
                        break;

                    case ";":
                    case "//":
                    case "#":
                        //Ignore Comments
                        break;
                        
                    default:
                        System.out.println("Unsupported command found.");
                        break;
                }
            }
            System.out.println("");
            System.out.println("Finished processing file.");
            br.close();
            MGR.Log(Globals.LogType.COMMAND, "Successfully finished execution of commands file: "+file);
            
        } catch(IOException ex) {
            System.out.println("Exception reached while reading this file. Stopping execution.");
            MGR.Log(Globals.LogType.ERROR, "Failed to finished execution of commands file: "+file);
        }
    }
    
    public static void PrintHelp() {
        System.out.println("");
        System.out.println("DomoBus Communications API v1");
        System.out.println("");
        System.out.println("Run application with: java -jar DCOMM.jar [Option Value]");
        System.out.println("");
        System.out.println("Available options:");
        System.out.println("\t-config:\tspecify a configuration file different from default.");
        System.out.println("\t-execute:\tspecify a file with a list of commands to execute.");
        System.out.println("\t-help:\t\tview help.");
        System.out.println("");
        System.out.println("Example: java -jar DCOMM.jar -config dcomm.cfg -execute commands.txt");
        System.out.println("");
        System.exit(0);
    }
    
    public static void BadArguments() {        
        //DCOMM launch options not in accordance with expectations
        System.out.println("Incorrect arguments inserted.");
        System.out.println("Run program with \"-help\" option to know more.");
        System.exit(-1);
    }
}
