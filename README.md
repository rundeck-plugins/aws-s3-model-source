# Rundeck S3 Resource model source

This is a plugin for [Rundeck](http://rundeck.org) > 2.10.0 that uses [Amazon S3](http://aws.amazon.com/s3) to store resources model file.

## Build

    ./gradlew clean build

## Install

Copy the `aws-s3-model-source-x.y.jar` file to the `libext/` directory inside your Rundeck installation.


## AWS Credentials

The plugin uses the AWS SDK for Java v2. By default it uses the SDK's default credentials provider chain, which
allows you to externally configure the credentials in any of these ways:

1. Java system properties `aws.accessKeyId` and `aws.secretAccessKey`
2. Environment variables `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
3. Web identity token credentials (for example IAM roles for service accounts on EKS)
4. The shared credentials and config files (`~/.aws/credentials`, `~/.aws/config`)
5. Container credentials (ECS) or Instance Profile credentials, if you are running on EC2. (See [the IAM user guide][1]).

Note: the AWS SDK v1 names `AWS_SECRET_KEY` and `aws.secretKey` are no longer read.

[1]: http://docs.aws.amazon.com/IAM/latest/UserGuide/role-usecase-ec2app.html

If you want to specify access key and secret key, you can do so in the configuration:

## Configuration

To configure the AWS access credentials you can set these property values:

`key` : access key, required if using `secret`

`secret` : secret key, required if using `key`

`credential` : path to a properties file which contains `accessKey` and `secretKey` entries.  Alternative to specifying
the `key` and `secret`.

S3 Connection uses these plugin configuration property values:

`region` : AWS region name to use. When a custom `endpoint` is set, the region is taken from regional AWS hosts
(e.g. `s3.us-east-2.amazonaws.com`); for any other endpoint the configured region is used to sign requests
(S3-compatible services such as MinIO usually accept `us-east-1`).

`endpoint`: Optional, a custom S3 compatible endpoint to use, such as `https://my-host.com/s3` or
`https://s3.us-east-2.amazonaws.com`. If no scheme is given, `https://` is used.

`pathstyle`: Optional, boolean, default=False, set to True if you need to define the bucket in your S3 like endpoint URL. e.g https://\<s3_like_end_point_url\>/\<your_bucket_name\>
 A custom way of defining buckets in your endpoint. Useful for non-AWS S3 like object storage technology e.g swift stack, Optums, etc. 
 Background information http://docs.aws.amazon.com/AmazonS3/latest/dev/VirtualHosting.html. May be useful if you have an https endpoint URL. 

`forcev4`, `useSigV2`: Deprecated and ignored. The AWS SDK v2 always uses Signature Version 4; if `useSigV2` is set,
a warning is logged.

The resource file use this configurations:

`bucket` : name of the S3 bucket to use

`file` :  Path on the bucket with the node information.

`extension` :  File format, can be `xml`,`yaml`or `json`.

`writable`: Is the resource file writable or not.

