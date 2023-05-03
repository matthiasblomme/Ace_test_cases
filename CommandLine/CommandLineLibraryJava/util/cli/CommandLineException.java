package util.cli;

/**
 * A helper class to be able to throw (and catch) a custom exception with a single string message. 
 * It contains a single constructor with a single string parameter for the exception message.
 * @author BLMM_M
 *
 */
public class CommandLineException extends Exception {
	
	private static final long serialVersionUID = -4026027942905684355L;

	/**
	 * Constructor
	 */
	CommandLineException() {
    }

	/**
	 * Constructor
	 * @param msg The message to put into the exception
	 */
	CommandLineException(String msg) {
        super(msg);
    }
}
