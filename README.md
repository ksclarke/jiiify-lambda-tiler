# jiiify-lambda-tiler

An experimental AWS lambda-based IIIF tile generator. It generates tiles into an S3 bucket using the IIIF URL pattern or, optionally, it can put them in a Pairtree inside the bucket for use by Jiiify.

## Building

The project uses the [Serverless Framework](https://serverless.com) so that needs to be installed before doing anything else. Consult [their documentation](https://serverless.com/framework/docs/getting-started/) for how to do that. This might be changed to Terraform in the future, fwiw. [Maven](http://maven.apache.org) and JDK 8 are also required to build it.

To use, check out the jiiify-lambda-tiler repository; copy the config-sample.yml file to config.yml (and edit it); then, deploy to AWS:

    git clone https://github.com/ksclarke/jiiify-lambda-tiler.git
    cd jiiify-lambda-tiler
    cp config-sample.yml config.yml
    # Edit config.yml using your favorite text editor
    mvn clean deploy

# Getting Started

Documentation forthcoming.

## Tips and Hints (Obsolete now that YAML is used for the config -- TODO: update this)

To delete everything in an S3 bucket (must have `jq` installed):

    aws --profile $(jq -r '.provider.profile' config.json) s3 rm s3://$(jq -r '.sourceBucket' config.json) --recursive

To copy a file into the S3 bucket (assumes the jiiify-image directory):

    aws --profile $(jq -r '.provider.profile' config.json) s3 cp src/test/resources/images/test.tif s3://$(jq -r '.sourceBucket' config.json)

## Contact

Kevin S. Clarke &lt;<a href="mailto:ksclarke@ksclarke.io">ksclarke@ksclarke.io</a>&gt;
