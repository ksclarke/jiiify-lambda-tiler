
package info.freelibrary.jiiify.lambda;

import static info.freelibrary.jiiify.lambda.Constants.BUNDLE_NAME;

import info.freelibrary.util.I18nRuntimeException;

/**
 * A runtime exception encountered by the tiler.
 *
 * @author <a href="mailto:ksclarke@ksclarke.io">Kevin S. Clarke</a>
 */
public class TilerRuntimeException extends I18nRuntimeException {

    /**
     * The <code>serialVersionUID</code> for the exception.
     */
    private static final long serialVersionUID = 671822685255593439L;

    /**
     * Creates a tiler runtime exception from the supplied message.
     *
     * @param aMessage A details message
     */
    public TilerRuntimeException(final String aMessage) {
        super(BUNDLE_NAME, aMessage);
    }

    /**
     * Creates a tiler runtime exception from the supplied message.
     *
     * @param aMessage A details message
     */
    public TilerRuntimeException(final String aMessage, final Object aDetailsArray) {
        super(BUNDLE_NAME, aMessage);
    }

    /**
     * Creates a tiler runtime exception from the supplied throwable.
     *
     * @param aThrowable A throwable that caused this tiler runtime exception
     */
    public TilerRuntimeException(final Throwable aThrowable) {
        super(aThrowable);
    }

    /**
     * Creates a tiler runtime exception from the supplied details message and throwable.
     *
     * @param aMessage A details message
     * @param aThrowable A throwable that caused this tiler runtime exception
     */
    public TilerRuntimeException(final Throwable aThrowable, final String aMessage) {
        super(aThrowable, BUNDLE_NAME, aMessage);
    }

}
