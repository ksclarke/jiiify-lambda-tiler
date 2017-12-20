
package info.freelibrary.jiiify.lambda;

import static info.freelibrary.jiiify.lambda.Constants.BUCKET;
import static info.freelibrary.jiiify.lambda.Constants.BUNDLE_NAME;
import static info.freelibrary.jiiify.lambda.Constants.ID;
import static info.freelibrary.jiiify.lambda.Constants.IMAGE;
import static info.freelibrary.jiiify.lambda.Constants.KEY;
import static info.freelibrary.jiiify.lambda.Constants.NAME;
import static info.freelibrary.jiiify.lambda.Constants.OBJECT;
import static info.freelibrary.jiiify.lambda.Constants.PARAMS;
import static info.freelibrary.jiiify.lambda.Constants.RECORDS;
import static info.freelibrary.jiiify.lambda.Constants.S3;
import static io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.SLF4JLogDelegateFactory;

/**
 * A route handler that allows a single function to handle different types of requests.
 */
public class VertxHandler implements RequestHandler<Map<String, Object>, TilerResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VertxHandler.class, BUNDLE_NAME);

    private static final String IGNORE_FILESYSTEM = "vertx.disableFileCPResolving";

    private final EventBus myEventBus;

    /**
     * Creates a new route handler.
     */
    public VertxHandler() {
        System.setProperty(IGNORE_FILESYSTEM, "true");
        System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, SLF4JLogDelegateFactory.class.getName());

        final Vertx vertx = Vertx.vertx();

        // Initiate our routing system
        myEventBus = vertx.eventBus();
        myEventBus.registerCodec(new TilerResponseCodec());

        // We leave the routing to Vert.x's event bus
        vertx.deployVerticle(new TilingService());
        vertx.deployVerticle(new WebService());
    }

    @Override
    public TilerResponse handleRequest(final Map<String, Object> aInput, final Context aContext) {
        final CompletableFuture<TilerResponse> future = new CompletableFuture<TilerResponse>();
        final Object method = aInput.get("httpMethod");
        final Object resource = aInput.get("resource");
        final JsonObject message;
        final String address;

        // Check to see if our input is coming from the AWS API Gateway or an S3 bucket event
        if ((method == null) || (resource == null)) {
            JsonObject json = JsonObject.mapFrom(aInput);

            if (json.containsKey(RECORDS)) {
                json = json.getJsonArray(RECORDS).getJsonObject(0);

                if (json.containsKey(S3)) {
                    final JsonObject s3 = json.getJsonObject(S3);
                    final String bucket = s3.getJsonObject(BUCKET).getString(NAME);
                    final String key = s3.getJsonObject(OBJECT).getString(KEY);

                    LOGGER.info(MessageCodes.JLT_005, bucket, key);

                    message = new JsonObject().put(BUCKET, bucket).put(KEY, key);
                    address = S3;
                } else {
                    throw new TilerRuntimeException(MessageCodes.JLT_004);
                }
            } else {
                throw new TilerRuntimeException(MessageCodes.JLT_003);
            }
        } else {
            try {
                final JsonObject json = new JsonObject(new ObjectMapper().writeValueAsString(aInput));
                final JsonObject params = json.getJsonObject(PARAMS);

                if (params == null) {
                    throw new TilerRuntimeException(MessageCodes.JLT_001);
                } else if (params.containsKey(IMAGE) && params.containsKey(ID)) {
                    message = new JsonObject();
                    message.put(IMAGE, params.getString(IMAGE));
                    message.put(ID, params.getString(ID));
                    address = method.toString() + ":" + resource.toString();
                } else if (params.containsKey(IMAGE)) {
                    throw new TilerRuntimeException(MessageCodes.JLT_002, ID);
                } else {
                    throw new TilerRuntimeException(MessageCodes.JLT_002, IMAGE);
                }
            } catch (final JsonProcessingException details) {
                throw new TilerRuntimeException(details);
            }
        }

        // Configure a default test address if we didn't receive one from the AWS API Gateway
        myEventBus.send(address, message, asyncResult -> {
            if (asyncResult.succeeded()) {
                final TilerResponse response = (TilerResponse) asyncResult.result().body();

                LOGGER.debug(MessageCodes.JLT_010, response);

                future.complete(response);
            } else {
                future.completeExceptionally(asyncResult.cause());
            }
        });

        try {
            return future.get(5, TimeUnit.MINUTES);
        } catch (final TimeoutException | ExecutionException | InterruptedException details) {
            throw new TilerRuntimeException(details);
        }
    }

}
