
package info.freelibrary.jiiify.lambda;

/**
 * A response object.
 */
public class ResponseBody {

    private final String myMessage;

    private final String myInput;

    /**
     * Creates a new response object.
     *
     * @param aMessage A response message
     * @param aInput An input string
     */
    public ResponseBody(final String aMessage, final String aInput) {
        myMessage = aMessage;
        myInput = aInput;
    }

    /**
     * Gets the response message.
     *
     * @return The response message
     */
    public String getMessage() {
        return myMessage;
    }

    /**
     * Gets the response input.
     *
     * @return The response input
     */
    public String getInput() {
        return myInput;
    }

}
