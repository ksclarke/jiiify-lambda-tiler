
package info.freelibrary.jiiify.lambda;

import static info.freelibrary.jiiify.lambda.Constants.BUNDLE_NAME;
import static info.freelibrary.jiiify.lambda.Constants.HTTP_ADDRESS;

import java.util.Collections;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.DeliveryOptions;

public class WebService extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebService.class, BUNDLE_NAME);

    @Override
    public void start() throws Exception {
        vertx.eventBus().localConsumer(HTTP_ADDRESS, message -> {
            final String codec = TilerResponseCodec.class.getSimpleName();
            final DeliveryOptions opts = new DeliveryOptions().setCodecName(codec);

            message.reply(respond(200, message.body().toString()), opts);
        });
    }

    private TilerResponse respond(final int aCode, final String aMessage) {
        final TilerResponse.Builder response = TilerResponse.builder();
        final ResponseBody body = new ResponseBody("Successful!", aMessage);

        response.setStatusCode(aCode).setObjectBody(body);
        response.setHeaders(Collections.singletonMap("X-Powered-By", "Jiiify Lambda"));

        return response.build();
    }

}
