package util.cli;

import java.io.IOException;
import java.util.ArrayList;

public class UsageExamples {
	/**
	 * Main class to demo the classes of psa.util
	 * @param args standard main class arguments, not used
	 * @throws IOException exception thrown from the streamGobblerExample method
	 * @throws InterruptedException exception thrown from the streamGobblerExample method
	 * @throws CommandLineException exception thrown from the commandExample method
	 */
	public static void main(String[] args) throws IOException, InterruptedException, CommandLineException {
		
		commandLineExample();
		
		loggingExample();
		
		streamGobblerExample();
		
		commandExample();
	}
	
	/**
	 * Example code for the CommandLineException class
	 */
	public static void commandLineExample() {
		//CommandLineException example
		try {
			CommandLineException e = new CommandLineException("this is a custom exception");
		throw e;
		} catch (CommandLineException cle) {
			System.out.println(cle.getMessage());
		}
	}
	
	/**
	 * Example code for the Logging class
	 */
	public static void loggingExample() {
		//Logging example
		Logging log = new Logging("DEBUG");
		log.log_info("one log line");
		ArrayList<String> array = new ArrayList<String>() {
			private static final long serialVersionUID = 1L;
			{
				add("line1");
				add("line2");
			}
		};
		log.log_debug(array);
	}
	
	/**
	 * Example code for the StreamGobbler class
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void streamGobblerExample() throws IOException, InterruptedException {
		//StreamGobbler example
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("cmd", "/c", "dir");
        Process proc = builder.start();
        StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");
        outputGobbler.start();
        int returnValue = proc.waitFor();
        ArrayList<String> output = outputGobbler.getOutput();
        System.out.println("Return value: " + String.valueOf(returnValue));
        output.forEach((l) -> System.out.println(l));
	}
	
	/**
	 * Example code for the Command class
	 * @throws CommandLineException
	 */
	public static void commandExample() throws CommandLineException {
		//Command example
		Logging logger = new Logging("DEBUG");
		Command command = new Command(logger);
		Object returnValue = command.Exec("dir");
		logger.log_info("Return value: " + String.valueOf(returnValue));
		ArrayList<String> output = command.getOutput();     
		output.forEach((l) -> System.out.println(l));
	}
	
}
