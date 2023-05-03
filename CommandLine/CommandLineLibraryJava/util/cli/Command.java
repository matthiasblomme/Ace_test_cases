package util.cli;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.broker.plugin.MbUserException;

/**
 * A class to execute system commands or scripts and handle the response
 * in a clean manner.
 * @author Matthias
 *
 */
public class Command
{
    /**
     * The stream gobbler for stderr
     */
	StreamGobbler errorGobbler;
	
	/**
	 * The stream gobbler for stdout
	 */
    StreamGobbler outputGobbler;
    
    /**
     * The result of the outptuGobbler after command completion
     */
    ArrayList<String> output;
    
    /**
     * The result of the errorGobbler after command completion
     */
    ArrayList<String> error;
    
    /**
     * The name of the OS we are running on
     */
    String osName;
    
    /**
     * The internal logging that is used for error and debug prints
     */
    Logging logger;
    
    /**
     * 
     * @param logger
     */
    public Command(Logging logger) {
    	this.logger = (logger == null ? new Logging() : logger); 
    	osName = System.getProperty("os.name");
    	if (osName.toLowerCase().contains("windows")) {
    		logger.log_debug("Running on " + osName);
    	}
    }
    
    /**
     * 
     * @return
     */
    public ArrayList<String> retreiveOutput() {
    	return (outputGobbler != null ? outputGobbler.getOutput() : new ArrayList<String> ());
    }

    /**
     * 
     * @return
     */
    public ArrayList<String> retreiveError() {
    	return (errorGobbler != null ? errorGobbler.getOutput() : new ArrayList<String> ());
    }
    
    /**
     * 
     * @return
     */
    public ArrayList<String> getOutput() {
    	return output;
    }

    /**
     * 
     * @return
     */
    public ArrayList<String> getError() {
    	return error;
    }
    
    /**
     * 
     * @param commandLine
     * @return
     * @throws SecurityException 
     * @throws MbUserException 
     */
	public int Exec(String commandLine) throws CommandLineException {
		if (osName.toLowerCase().contains("windows")) {
			if (commandLine.toLowerCase().endsWith(".sh")){
				logger.log_error("sh script not allowed on " + osName);
				throw new CommandLineException("sh script not allowed on windows");
			}
		} else {
			if (commandLine.toLowerCase().endsWith(".bat")){
				logger.log_error("bat script not allowed on " + osName);
				throw new CommandLineException("bat script not allowed on " + osName);
			}
		}
		
		String[] commandParams = ParseCommandString(commandLine);
		return Exec(commandParams);
	}
    
    /**
     * 
     * @param args
     * @return
     */
    public int Exec(String[] args){
    	ArrayList<String> list = new ArrayList<>();
    	if (osName.toLowerCase().contains("windows")) list.add("/c");
    	list.addAll(Arrays.asList(args));
        return osName.toLowerCase().contains("windows") ? Exec("cmd", (String[]) list.toArray(new String[0])) : Exec("sh", (String[]) list.toArray(new String[0]));
    }

    /**
     * 
     * @param executable
     * @param args
     * @return
     */
    public int Exec(String executable, String[] args){
        return Exec(System.getProperty("user.home"), executable, args);
    }

    /**
     * 
     * @param runDirectory
     * @param executable
     * @param args
     * @return
     */
    public int Exec(String runDirectory, String executable, String[] args)
    {
    	int returnValue = -1;
    	
    	try
        {
            ArrayList<String> command = new ArrayList<>();
            command.add(executable);
            command.addAll(Arrays.asList(args));

            logger.log_debug(runDirectory);
            logger.log_debug(command);
            
            ProcessBuilder builder = new ProcessBuilder();
            builder.directory(new File(runDirectory));
            builder.command(command);
            Process proc = builder.start();
            
            errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR");
            outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");
            errorGobbler.start();
            outputGobbler.start();

            returnValue = proc.waitFor();
            output = retreiveOutput();
            error = retreiveError();
        } 
        catch (Throwable t)
        {
            t.printStackTrace();
            error = new ArrayList<String> ();
            error.add(t.getMessage());
        }
        
        return returnValue;
    }
    
    /**
     * 
     * @param commandString
     * @return
     */
    public String[] ParseCommandString(String commandString){
    	String quotePattern = ".*?(\".*?\").*?";
    	HashMap<String,String> replaceList = new HashMap<>();
    	Pattern p = Pattern.compile(quotePattern, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(commandString);

        int matchNumber = 0;
        while (m.find()) {
            matchNumber++;
            String replaceId = "##param_" + matchNumber + "##";
            replaceList.put(replaceId, m.group(1));
            commandString = commandString.replace(m.group(1), replaceId);
        }
        ArrayList<String> paramsListTemp = new ArrayList<>(Arrays.asList(commandString.split(" ")));
        
        ArrayList<String> paramList = new ArrayList<String>();

        paramsListTemp.forEach(k -> {
            paramList.add(replaceList.containsKey(k) ? replaceList.get(k) : k);
        }); 
        
        if (paramList.size() == 0) paramList.add(commandString) ;
    	
        return (String[]) paramList.toArray(new String[0]);
    }
    
    /**
     * 
     * @param outputData
     * @throws CommandLineException 
     * @throws IOException 
     */
    public void WriteOutputToFile(ArrayList<String> outputData, String outputFile) throws CommandLineException {
    	BufferedWriter writer;
    	
    	try {
    		File f = new File(outputFile);
        	if (!f.exists()) f.createNewFile();
        	
			writer = new BufferedWriter(new FileWriter(outputFile,true));
			for(String line: outputData) {
				writer.write(line);
				writer.write(System.lineSeparator());
			}
			writer.close();
		} catch (IOException e) {
			logger.log_error(e.getMessage());
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			logger.log_debug(sw.toString());
			throw new CommandLineException("An exception occured trying to write to " + outputFile + ": " + e.getMessage());
		}
    }
}
