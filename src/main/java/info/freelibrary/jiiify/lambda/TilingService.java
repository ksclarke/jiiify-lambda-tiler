//

package info.freelibrary.jiiify.lambda;

import static info.freelibrary.jiiify.lambda.Constants.BUCKET;
import static info.freelibrary.jiiify.lambda.Constants.BUNDLE_NAME;
import static info.freelibrary.jiiify.lambda.Constants.ID;
import static info.freelibrary.jiiify.lambda.Constants.KEY;
import static info.freelibrary.jiiify.lambda.Constants.PUBLIC;
import static info.freelibrary.jiiify.lambda.Constants.S3;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.BucketCrossOriginConfiguration;
import com.amazonaws.services.s3.model.CORSRule;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.TagSet;

import info.freelibrary.iiif.image.Image;
import info.freelibrary.iiif.image.ImageFactory;
import info.freelibrary.iiif.image.api.APIComplianceLevel;
import info.freelibrary.iiif.image.api.ImageException;
import info.freelibrary.iiif.image.api.ImageInfo;
import info.freelibrary.iiif.image.api.Profile;
import info.freelibrary.iiif.image.api.Request;
import info.freelibrary.iiif.image.util.Tiler;
import info.freelibrary.pairtree.PairtreeFactory;
import info.freelibrary.pairtree.PairtreeFactory.PairtreeImpl;
import info.freelibrary.pairtree.PairtreeRoot;
import info.freelibrary.util.FileUtils;
import info.freelibrary.util.IOUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

/**
 * A IIIF tiling service.
 */
public class TilingService extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(TilingService.class, BUNDLE_NAME);

    private static final String HTML_MIME_TYPE = "text/html";

    private static final String EVERYTHING = "*";

    private static final String SCHEME_SYNTAX = "://";

    private static final String ACCESS_KEY = System.getenv("AWS_ACCESS_KEY");

    private static final String SECRET_KEY = System.getenv("AWS_SECRET_KEY");

    @Override
    public void start() throws Exception {
        vertx.eventBus().localConsumer(S3, message -> {
            final AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();

            final JsonObject json = (JsonObject) message.body();
            final String bucket = json.getString(BUCKET);
            final String key = json.getString(KEY);

            final Stream<TagSet> stream = s3.getBucketTaggingConfiguration(bucket).getAllTagSets().stream();
            final Optional<TagSet> tags = stream.filter(set -> set.getTag(Tag.TILE_BUCKET) != null).findFirst();
            final TagSet tagSet = tags.orElseThrow(() -> new TilerRuntimeException(MessageCodes.JLT_006, bucket));

            createTiles(s3, s3.getObject(bucket, key), getConfig(tagSet), message);
        });

    }

    private void createTiles(final AmazonS3 aS3Client, final S3Object aObj, final Properties aConfig,
            final Message<Object> aMessage) {
        try {
            final S3ObjectInputStream s3ObjStream = aObj.getObjectContent();
            final Image image = ImageFactory.getImage(IOUtils.readBytes(s3ObjStream), true);
            final String codec = TilerResponseCodec.class.getSimpleName();
            final DeliveryOptions opts = new DeliveryOptions().setCodecName(codec);
            final ObjectMetadata s3Metadata = aObj.getObjectMetadata();

            // Retrieve a user supplied metadata value for item ID
            String id = s3Metadata.getUserMetaDataOf(ID);

            // If we don't have a metadata ID set, use the S3 object key minus its file extension
            if (id == null) {
                id = FileUtils.stripExt(aObj.getKey());
            }

            final String bucket = aConfig.getProperty(Tag.TILE_BUCKET);
            final BucketCrossOriginConfiguration cors = aS3Client.getBucketCrossOriginConfiguration(bucket);

            // Make sure we have CORS set for public access, if needed
            if ((cors == null) || (cors.getRules().size() == 0)) {
                final BucketCrossOriginConfiguration corsConfig = new BucketCrossOriginConfiguration();
                final List<CORSRule> rules = new ArrayList<CORSRule>();
                final CORSRule rule = new CORSRule();

                rule.withId("jiiify").withAllowedOrigins(Arrays.asList(new String[] { EVERYTHING }));
                rule.withAllowedMethods(Arrays.asList(new CORSRule.AllowedMethods[] { CORSRule.AllowedMethods.GET }));
                rule.withAllowedHeaders(Arrays.asList(new String[] { EVERYTHING }));
                rule.setExposedHeaders("ETag", "Access-Control-Allow-Origin");

                rules.add(rule);
                corsConfig.setRules(rules);

                aS3Client.setBucketCrossOriginConfiguration(bucket, corsConfig);
            }

            // Create an image info file for the image
            final String prefix = aConfig.getProperty(Tag.IIIF_PREFIX);
            final Request imageRequest = new Request(id, prefix);
            final int tileSize = Integer.parseInt(aConfig.getProperty(Tag.TILE_SIZE));
            final ImageInfo info = new ImageInfo(id);
            final int height = image.getHeight();
            final int width = image.getWidth();
            final Profile profile = new Profile(APIComplianceLevel.ZERO);
            final ObjectMetadata infoMetadata = new ObjectMetadata();
            final String server = aConfig.getProperty(Tag.SERVER);
            final String protocol = aConfig.getProperty(Tag.PROTOCOL);

            profile.setFormats(imageRequest.getFormat());
            profile.setQualities(imageRequest.getQuality());
            info.setWidth(width).setHeight(height).setTileSize(tileSize).setProfile(profile);
            info.addTile(tileSize, image.getScaleFactors(tileSize));

            // Store image info file in S3 pairtree or directory structure
            if (Tag.PAIRTREE.equals(aConfig.getProperty(Tag.JIIIFY_OUTPUT))) {
                info.setID(protocol + SCHEME_SYNTAX + Paths.get(server, prefix, info.getID()));

                final byte[] bytes = JsonObject.mapFrom(info).encodePrettily().getBytes();
                final PairtreeFactory factory = PairtreeFactory.getFactory(vertx, PairtreeImpl.S3Bucket);
                final PairtreeRoot ptRoot = factory.getPairtree(bucket, ACCESS_KEY, SECRET_KEY);
                final String path = ptRoot.getObject(imageRequest.getID()).getPath(ImageInfo.FILE_NAME);

                infoMetadata.setContentLength(bytes.length);
                infoMetadata.setContentType(ImageInfo.MIME_TYPE);

                LOGGER.debug(MessageCodes.JLT_016, path);

                final ByteArrayInputStream inStream = new ByteArrayInputStream(bytes);
                final PutObjectRequest req = new PutObjectRequest(bucket, path, inStream, infoMetadata);

                // Set access permissions
                if (Boolean.parseBoolean(aConfig.getProperty(Tag.READABLE))) {
                    req.setCannedAcl(CannedAccessControlList.PublicRead);
                }

                // Write the Image Info file to S3
                aS3Client.putObject(req);
            } else {
                info.setID(protocol + SCHEME_SYNTAX + Paths.get(server, bucket, info.getID()));

                final byte[] bytes = JsonObject.mapFrom(info).encodePrettily().getBytes();
                final String key = Paths.get(info.getID(), ImageInfo.FILE_NAME).toString();

                infoMetadata.setContentLength(bytes.length);
                infoMetadata.setContentType(ImageInfo.MIME_TYPE);

                LOGGER.debug(MessageCodes.JLT_016, key);

                final ByteArrayInputStream inStream = new ByteArrayInputStream(bytes);
                final PutObjectRequest req = new PutObjectRequest(bucket, key, inStream, infoMetadata);

                // Set access permissions
                if (Boolean.parseBoolean(aConfig.getProperty(Tag.READABLE))) {
                    req.setCannedAcl(CannedAccessControlList.PublicRead);
                }

                // Write the Image Info file to S3
                aS3Client.putObject(req);

                // Create a default image index file displaying it with OpenSeadragon
                writeIndex(aS3Client, id, bucket, aConfig);
            }

            // Generate tiles and put them into the S3 bucket
            Tiler.getPaths(prefix, id, tileSize, image.getWidth(), image.getHeight()).forEach(iiifPath -> {
                final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                final String[] pathSegments = iiifPath.split("\\/");

                try {
                    final ObjectMetadata imageMetadata = new ObjectMetadata();
                    final Request request = Request.parse(iiifPath);
                    final ByteArrayInputStream byteStream;
                    final PutObjectRequest objReq;

                    // Do our image transformation
                    image.transform(request).write(request.getFormat(), outStream);
                    byteStream = new ByteArrayInputStream(outStream.toByteArray());

                    // Set S3 metadata for derivative image
                    imageMetadata.setContentLength(byteStream.available());
                    imageMetadata.setContentType(request.getFormat().getMimeType());

                    // Either format our S3 bucket as a Pairtree or a standard file directory
                    if (Tag.PAIRTREE.equals(aConfig.getProperty(Tag.JIIIFY_OUTPUT))) {
                        final PairtreeFactory factory = PairtreeFactory.getFactory(vertx, PairtreeImpl.S3Bucket);
                        final PairtreeRoot ptRoot = factory.getPairtree(bucket, ACCESS_KEY, SECRET_KEY);
                        final String key = Paths.get("", pathSegments).subpath(2, pathSegments.length).toString();
                        final String path = ptRoot.getObject(pathSegments[1]).getPath(key);

                        LOGGER.debug(MessageCodes.JLT_007, path);

                        objReq = new PutObjectRequest(bucket, path, byteStream, imageMetadata);

                        // Set access permissions
                        if (Boolean.parseBoolean(aConfig.getProperty(Tag.READABLE))) {
                            objReq.setCannedAcl(CannedAccessControlList.PublicRead);
                        }

                        // Write the derivative image file to S3
                        aS3Client.putObject(objReq);
                    } else {
                        final String key = Paths.get("", pathSegments).subpath(1, pathSegments.length).toString();

                        LOGGER.debug(MessageCodes.JLT_008, key);

                        objReq = new PutObjectRequest(bucket, key, byteStream, imageMetadata);

                        // Set access permissions
                        if (Boolean.parseBoolean(aConfig.getProperty(Tag.READABLE))) {
                            objReq.setCannedAcl(CannedAccessControlList.PublicRead);
                        }

                        // Write the derivative image file to S3
                        aS3Client.putObject(objReq);
                    }
                } catch (final ImageException | IOException details) {
                    throw new TilerRuntimeException(details);
                } finally {
                    IOUtils.closeQuietly(outStream);

                    try {
                        image.revert();
                    } catch (final IOException details) {
                        throw new TilerRuntimeException(details);
                    }
                }
            });

            aMessage.reply(respond(200, "Image tiled!"), opts);
        } catch (final IOException details) {
            aMessage.fail(1, details.getMessage());
        }
    }

    private void writeInfo(final AmazonS3 aS3Client, final ImageInfo aInfo, final Image aImage, final String aBucket,
            final Properties aConfig) throws IOException {
        final ObjectMetadata metadata = new ObjectMetadata();
        final String server = aConfig.getProperty(Tag.SERVER);
        final String protocol = aConfig.getProperty(Tag.PROTOCOL);
        final int tileSize = Integer.parseInt(aConfig.getProperty(Tag.TILE_SIZE));

        aInfo.setID(protocol + SCHEME_SYNTAX + Paths.get(server, aBucket, aInfo.getID()));
        aInfo.addTile(tileSize, aImage.getScaleFactors(tileSize));

        final byte[] bytes = JsonObject.mapFrom(aInfo).encodePrettily().getBytes();

        metadata.setContentLength(bytes.length);
        metadata.setContentType(ImageInfo.MIME_TYPE);

        final String key = Paths.get(aInfo.getID(), ImageInfo.FILE_NAME).toString();
        final PutObjectRequest req = new PutObjectRequest(aBucket, key, new ByteArrayInputStream(bytes), metadata);

        // Set access permissions
        if (Boolean.parseBoolean(aConfig.getProperty(Tag.READABLE))) {
            req.setCannedAcl(CannedAccessControlList.PublicRead);
        }

        // Write the Image Info file to S3
        aS3Client.putObject(req);
    }

    private void writeIndex(final AmazonS3 aS3Client, final String aID, final String aBucket,
            final Properties aConfig) {
        final InputStream htmlStream = getClass().getResourceAsStream("../../../../index.html");
        final BufferedReader reader = new BufferedReader(new InputStreamReader(htmlStream));
        final String protocol = aConfig.getProperty(Tag.PROTOCOL);
        final String server = aConfig.getProperty(Tag.SERVER);
        final ObjectMetadata metadata = new ObjectMetadata();
        final PutObjectRequest request;
        final byte[] bytes;

        String html = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        String key = Paths.get(aID, ImageInfo.FILE_NAME).toString();

        html = StringUtils.format(html, protocol + SCHEME_SYNTAX + Paths.get(server, aBucket, key));
        key = Paths.get(aID, "index.html").toString();
        bytes = html.getBytes();

        metadata.setContentLength(bytes.length);
        metadata.setContentType(HTML_MIME_TYPE);

        request = new PutObjectRequest(aBucket, key, new ByteArrayInputStream(bytes), metadata);

        // Set access permissions
        if (Boolean.parseBoolean(aConfig.getProperty(Tag.READABLE))) {
            request.setCannedAcl(CannedAccessControlList.PublicRead);
        }

        // Write the index page to S3
        aS3Client.putObject(request);
    }

    // This could be done a lot better, but I'm going to be lazy for now
    private Properties getConfig(final TagSet aTagSet) {
        final String acl = aTagSet.getTag(Tag.TILE_ACL);
        final boolean readable = (acl != null) && PUBLIC.equals(acl.toLowerCase(Locale.US));
        final Properties properties = new Properties();
        final String prefix = aTagSet.getTag(Tag.IIIF_PREFIX);
        final String format = aTagSet.getTag(Tag.JIIIFY_OUTPUT);
        final String tileSize = aTagSet.getTag(Tag.TILE_SIZE);
        final String bucket = aTagSet.getTag(Tag.TILE_BUCKET);
        final String server = aTagSet.getTag(Tag.SERVER);

        properties.setProperty(Tag.READABLE, Boolean.toString(readable));

        if (tileSize == null) {
            throw new TilerRuntimeException(MessageCodes.JLT_012);
        } else {
            // Confirm it is an int before putting it in our config
            properties.setProperty(Tag.TILE_SIZE, Integer.toString(Integer.parseInt(tileSize)));
        }

        if (prefix == null) {
            throw new TilerRuntimeException(MessageCodes.JLT_011);
        } else {
            properties.setProperty(Tag.IIIF_PREFIX, prefix);
        }

        if (format == null) {
            throw new TilerRuntimeException(MessageCodes.JLT_009);
        } else {
            properties.setProperty(Tag.JIIIFY_OUTPUT, format.toUpperCase(Locale.US));
        }

        if (server == null) {
            throw new TilerRuntimeException(MessageCodes.JLT_013);
        } else {
            try {
                properties.setProperty(Tag.PROTOCOL, new URL(server).getProtocol());
                properties.setProperty(Tag.SERVER, server.replaceFirst("http[s?]:\\/\\/", ""));
            } catch (final MalformedURLException details) {
                throw new TilerRuntimeException(MessageCodes.JLT_014, server);
            }
        }

        if (bucket == null) {
            throw new TilerRuntimeException(MessageCodes.JLT_015);
        } else {
            properties.setProperty(Tag.TILE_BUCKET, bucket);
        }

        return properties;
    }

    private TilerResponse respond(final int aCode, final String aMessage) {
        final TilerResponse.Builder response = TilerResponse.builder();
        final ResponseBody body = new ResponseBody("Successful!", aMessage);

        response.setStatusCode(aCode).setObjectBody(body);
        response.setHeaders(Collections.singletonMap("X-Powered-By", "Jiiify Lambda"));

        return response.build();
    }

}
