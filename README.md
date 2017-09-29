# Rundeck S3 Resource model source

This is a plugin for [Rundeck](http://rundeck.org) > 2.10.0 that uses [Amazon S3](http://aws.amazon.com/s3) to store resources model file.

## Build

    ./gradlew clean build

## Install

Copy the `aws-s3-model-source-x.y.jar` file to the `libext/` directory inside your Rundeck installation.


## AWS Credentials

The plugin will by default use the "credentials provider chain" for AWS access credentials, which allows you to
externally configure the credentials in any of three ways:

1. Environment variables `AWS_ACCESS_KEY_ID` and `AWS_SECRET_KEY`
2. Java system properties `aws.accessKeyId` and `aws.secretKey`
3. Instance Profile credentials, if you are running on EC2. (See [the IAM user guide][1]).

[1]: http://docs.aws.amazon.com/IAM/latest/UserGuide/role-usecase-ec2app.html

If you want to specify access key and secret key, you can do so in the configuration:

## Configuration

To configure the AWS access credentials you can set these property values:

`AWSAccessKeyId` : access key, required if using `AWSSecretKey`

`AWSSecretKey` : secret key, required if using `AWSAccessKeyId`

`AWSCredentialsFile` : properties file which contains `accessKey` and `secretKey` entries.  Alternative to specifying
the `AWSAccessKeyId and `AWSSecretKey`

S3 Connection uses these plugin configuration property values:

`region` : AWS region name to use.

`endpoint`: Optional, a custom S3 compatible endpoint to use, such as `https://my-host.com/s3` or `s3.us-east-2.amazonaws.com`

`pathStyle`: Optional, boolean, default=False, set to True if you need to define the bucket in your S3 like endpoint URL. e.g https://\<s3_like_end_point_url\>/\<your_bucket_name\>
 A custom way of defining buckets in your endpoint. Useful for non-AWS S3 like object storage technology e.g swift stack, Optums, etc. 
 Background information http://docs.aws.amazon.com/AmazonS3/latest/dev/VirtualHosting.html. May be useful if you have an https endpoint URL. 


The resource file use this configurations:

`bucket` : name of the S3 bucket to use

`file` :  Path on the bucket with the node information.

`extension` :  File format, can be `xml`,`yaml`or `json`.

`writable`: Is the resource file writable or not.

