package util.cli;

import java.util.ArrayList;

/**
 * A helper class to allow multilevel logging. 
 * By setting the log level on the constructor or via the setLogLevel method 
 * you can choose what level of logging to show when running the code.
	Each log level has a specific value assigned
	• NONE = 0
	• ERROR = 1
	• INFO = 2
	• DEBUG = 3
 * @author BLMM_M
 *
 */
public class Logging {

	/**
	 * Log level translated into an integer
	 */
	private int logLevel = 0;
	
	/**
	 * Constructor
	 * @param logLevel the log level for this instance of the Logging
	 */
	public Logging(String logLevel){
		setLogLevel(logLevel);
	}
	
	/**
	 * Constructor 
	 */
	Logging(){}
	
	/**
	 * Translate logLevel in the integer value
	 */
	protected void setLogLevel(String logLevel) {
		switch (logLevel.toUpperCase()) {
    	case "NONE":
    		this.logLevel = 0;
    		break;
    	case "ERROR":
    		this.logLevel = 1;
    		break;
    	case "INFO":
    		this.logLevel = 2;
    		break;
    	case "DEBUG":
    		this.logLevel = 3;
    	}
	}
	
	/**
	 * Log an error message
	 * @param logData String to log
	 */
	protected void log_error(String logData) {
		if (logLevel >= 1) System.out.println(logData);
	}

	/**
	 * Log an error message
	 * @param logData ArrayList of strings to log
	 */
	protected void log_error(ArrayList<String> logData) {
		if (logLevel >= 1) System.out.println(logData);
	}
	
	/**
	 * Log an info message
	 * @param logData String to log
	 */
	protected void log_info(String logData) {
		if (logLevel >= 2) System.out.println(logData);
	}
	
	/**
	 * Log an info message
	 * @param logData ArrayList of strings to log
	 */
	protected void log_info(ArrayList<String> logData) {
		if (logLevel >= 2) System.out.println(logData);
	}
	
	/**
	 * Log a debug message
	 * @param logData String to log
	 */
	protected void log_debug(String logData) {
		if (logLevel >= 3) System.out.println(logData);
	}
	
	/**
	 * Log a debug message
	 * @param logData ArrayList of strings to log
	 */
	protected void log_debug(ArrayList<String> logData) {
		if (logLevel >= 3) System.out.println(logData);
	}
}
