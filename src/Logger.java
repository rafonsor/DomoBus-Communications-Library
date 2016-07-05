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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * @author Rafael Afonso Rodrigues
 */
public class Logger {
    
    private FileWriter logger;
    private Calendar currentDate;
    private Timer updateTask;
    private String prefix;
    
    public Logger() throws IOException {
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH);
        int day = now.get(Calendar.DAY_OF_MONTH);
        currentDate = now;
        
        //Check logs folder exist
        File folder = new File(Globals.LOG_FOLDER);
        if(!folder.exists()) folder.mkdir();
        
        //Open current log file, according the Operating System/Environment used
        String os = System.getProperty("os.name");
        if(os == null && os.startsWith("Win")) prefix = Globals.LOG_FOLDER+"\\"; //Windows or Netbeans
        else prefix = Globals.LOG_FOLDER+"/";
        
        logger = new FileWriter(prefix+year+"-"+String.format("%02d", (month+1))+"-"+String.format("%02d", day)+Globals.LOG_EXTENSION, true);
        
        //Set timed task for changing log files
        now.set(Calendar.DAY_OF_MONTH, day+1);
        updateTask = new Timer();
        updateTask.schedule(new TimerTask() {
            @Override
            public void run() {
                CheckDate();
            }
        }, now.getTime(), Globals.LOG_PERIOD);
        
        Log(Globals.LogType.SYSTEM, "Communications Module Started.");
    };
    
    public void Stop() {
        updateTask.cancel();
        try {
            Log(Globals.LogType.SYSTEM, "Communications Module Stopped.");
            logger.close();
        } catch(IOException ex) {
            System.out.println("Could not close log file.");
        }
    };
    
    public void CheckDate() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH);
        int day = now.get(Calendar.DAY_OF_MONTH);
        
        if(day > currentDate.get(Calendar.DAY_OF_MONTH) && month > currentDate.get(Calendar.MONTH) && year > currentDate.get(Calendar.YEAR)) {
            //Close current log file and create new one
            try {
                logger.close();
                logger = new FileWriter(prefix+year+"-"+String.format("%02d", (month+1))+"-"+String.format("%02d", day)+Globals.LOG_EXTENSION, true);
                currentDate.set(year, month, day);
            } catch(IOException ex) {
                System.out.println("Could not create a new log file.");
            }
        }
    };
    
    public void Log(Globals.LogType type, String entry) {
        Calendar now = Calendar.getInstance();
        entry = now.getTime()+"\t"+type+"\t"+entry+System.lineSeparator();
        
        try {
            //Append new log entry
            logger.append(entry);
            logger.flush();
        } catch(IOException ex) {
            System.out.println("Could not write to log file: "+entry);
        }
    };
    
}
