package util.cli;

import java.util.*;
import java.io.*;

 /**
  * A helper class that extends the Thread class and reads an input stream 
  * (e.g. from a spawned process )and captures the result in an ArrayList for easy access.
  * @author BLMM_M
  *
  */
class StreamGobbler extends Thread
{
    /**
     * The input stream that is read
     */
	InputStream is;
	
	/**
	 * The type of data that is to be read
	 */
    String type;
    
    /**
     * The result of the input stream
     */
    ArrayList<String> result;
    
    /**
     * Constructor
     * @param is the input stream to monitor
     * @param type the type of data that is expected in the input stream
     */
    StreamGobbler(InputStream is, String type)
    {
        this.is = is;
        this.type = type;
    }

    /**
     * Main method to start the collection of data from the input stream                   
     */
    public void run()
    {
        try
        {
            result = new ArrayList<>();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line=null;
            while ( (line = br.readLine()) != null)
                result.add(line);
        } catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
    }

    /*
     * Retrieve the collected output as an arraylist of strings
     */
    ArrayList<String> getOutput()
    {
        return result;
    }
}
