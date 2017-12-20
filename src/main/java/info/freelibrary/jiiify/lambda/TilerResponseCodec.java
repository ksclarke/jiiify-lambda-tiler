
package info.freelibrary.jiiify.lambda;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public class TilerResponseCodec implements MessageCodec<TilerResponse, TilerResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TilerResponseCodec.class);

    private static final String STATUS_CODE = "statusCode";

    private static final String BODY = "body";

    private static final String IS_BASE64 = "isBase64";

    private static final String HEADERS = "headers";

    @Override
    public TilerResponse decodeFromWire(final int aIndex, final Buffer aBuffer) {
        // We can ignore buffer index, because our whole buffer is a JSON object

        final JsonObject json = aBuffer.toJsonObject();
        final String body = json.getString(BODY, "");
        final int statusCode = json.getInteger(STATUS_CODE, 500);
        final boolean isBase64 = json.getBoolean(IS_BASE64, false);
        final Map<String, String> headers = new HashMap<String, String>();
        final JsonObject headersJson = json.getJsonObject(HEADERS, new JsonObject());

        headersJson.forEach(entry -> {
            headers.put(entry.getKey(), entry.getValue().toString());
        });

        return new TilerResponse(statusCode, body, headers, isBase64);
    }

    @Override
    public void encodeToWire(final Buffer aBuffer, final TilerResponse aResponse) {
        final String body = aResponse.getBody();
        final JsonObject json = new JsonObject();
        final JsonObject headersJson = new JsonObject();
        final int statusCode = aResponse.getStatusCode();
        final boolean isBase64Encoded = aResponse.isBase64Encoded();
        final Map<String, String> headers = aResponse.getHeaders();

        // Serialize our response values
        json.put(STATUS_CODE, statusCode);
        json.put(BODY, body);
        json.put(IS_BASE64, isBase64Encoded);

        if (headers != null) {
            final Iterator<String> keys = headers.keySet().iterator();

            while (keys.hasNext()) {
                final String key = keys.next();
                final String value = headers.get(key);

                headersJson.put(key, value);
            }
        }

        // Store serialization in the message buffer
        json.put(HEADERS, headersJson).writeToBuffer(aBuffer);
    }

    @Override
    public String name() {
        return getClass().getSimpleName();
    }

    @Override
    public byte systemCodecID() {
        return -1; // Always -1 for a user defined codec
    }

    @Override
    public TilerResponse transform(final TilerResponse aResponse) {
        return aResponse; // For local message queue, just return same object
    }

}
