
package info.freelibrary.jiiify.lambda;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The response from the tiling service.
 */
public class TilerResponse {

    private final int myStatusCode;

    private final String myBody;

    private final Map<String, String> myHeaders;

    private final boolean myResponseIsBase64Encoded;

    /**
     * Creates a new tiler response.
     *
     * @param aStatusCode The status code of the response
     * @param aBody The body of a response
     * @param aHeaders Any headers that should be set in the response
     * @param aBase64EncodedResponse Whether the response is Base64 encoded
     */
    public TilerResponse(final int aStatusCode, final String aBody, final Map<String, String> aHeaders,
            final boolean aBase64EncodedResponse) {
        myStatusCode = aStatusCode;
        myBody = aBody;
        myHeaders = aHeaders;
        myResponseIsBase64Encoded = aBase64EncodedResponse;
    }

    /**
     * Gets the status code of the response.
     *
     * @return The status code of the response
     */
    public int getStatusCode() {
        return myStatusCode;
    }

    /**
     * Gets the body of the response.
     *
     * @return The body of the response
     */
    public String getBody() {
        return myBody;
    }

    /**
     * Gets the headers to set in the response.
     *
     * @return Response headers
     */
    public Map<String, String> getHeaders() {
        return myHeaders;
    }

    /**
     * Whether the response is Base64 encoded. The API Gateway expects the property to be called isBase64Encoded.
     *
     * @return
     */
    public boolean isBase64Encoded() {
        return myResponseIsBase64Encoded;
    }

    /**
     * Gets a response builder.
     *
     * @return A response builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return Integer.toString(myStatusCode) + " | " + getBody();
    }

    /**
     * A builder that generates the response.
     */
    public static class Builder {

        private static final Logger LOGGER = LoggerFactory.getLogger(TilerResponse.Builder.class);

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private int myStatusCode = 200;

        private Map<String, String> myHeaders = Collections.emptyMap();

        private String myRawBody;

        private Object myObjectBody;

        private byte[] myBinaryBody;

        private boolean myBase64Encoded;

        /**
         * Sets the response status code.
         *
         * @param aStatusCode A response status code
         * @return The builder
         */
        public Builder setStatusCode(final int aStatusCode) {
            myStatusCode = aStatusCode;
            return this;
        }

        /**
         * Sets the response headers
         *
         * @param aHeaders The response headers
         * @return The builder
         */
        public Builder setHeaders(final Map<String, String> aHeaders) {
            myHeaders = aHeaders;
            return this;
        }

        /**
         * Sets the raw body of a response in string form.
         *
         * @param aRawBody The raw body of a response in string form
         * @return The builder
         */
        public Builder setRawBody(final String aRawBody) {
            myRawBody = aRawBody;
            return this;
        }

        /**
         * Sets the object body of a response.
         *
         * @param aObjectBody The object body of a response
         * @return The builder
         */
        public Builder setObjectBody(final Object aObjectBody) {
            myObjectBody = aObjectBody;
            return this;
        }

        /**
         * Sets the binary body to be encoded as Base64. {@link #setBase64Encoded(boolean) setBase64Encoded(true)}
         * will be in invoked automatically.
         *
         * @param aBinaryBody A byte array to be converted to Base64.
         */
        public Builder setBinaryBody(final byte[] aBinaryBody) {
            myBinaryBody = aBinaryBody;
            setBase64Encoded(true);
            return this;
        }

        /**
         * A binary or rather a Base64 encoded responses requires:
         * <ol>
         * <li>"Binary Media Types" to be configured in API Gateway
         * <li>a request with an "Accept" header set to one of the "Binary Media Types"
         * </ol>
         */
        public Builder setBase64Encoded(final boolean aBase64Encoded) {
            myBase64Encoded = aBase64Encoded;
            return this;
        }

        /**
         * Builds a response for the tiler service.
         *
         * @return The tiler response
         */
        public TilerResponse build() {
            String body = null;

            if (myRawBody != null) {
                body = myRawBody;
            } else if (myObjectBody != null) {
                try {
                    body = MAPPER.writeValueAsString(myObjectBody);
                } catch (final JsonProcessingException details) {
                    LOGGER.error("failed to serialize object", details);
                    throw new RuntimeException(details);
                }
            } else if (myBinaryBody != null) {
                body = new String(Base64.getEncoder().encode(myBinaryBody), StandardCharsets.UTF_8);
            }

            return new TilerResponse(myStatusCode, body, myHeaders, myBase64Encoded);
        }
    }

}
