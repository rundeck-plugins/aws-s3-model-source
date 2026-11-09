/*
 * Copyright 2017 Rundeck, Inc. (http://rundeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rundeck.plugins.aws;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import com.dtolabs.rundeck.core.common.IFramework;
import com.dtolabs.rundeck.core.common.INodeSet;
import com.dtolabs.rundeck.core.resources.ResourceModelSource;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException;
import com.dtolabs.rundeck.core.resources.SourceType;
import com.dtolabs.rundeck.core.resources.WriteableModelSource;
import com.dtolabs.rundeck.core.resources.format.ResourceFormatParser;
import com.dtolabs.rundeck.core.resources.format.ResourceFormatParserException;
import com.dtolabs.rundeck.core.resources.format.UnsupportedFormatException;
import com.dtolabs.utils.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Resource model source backed by a file stored in an S3 bucket, using the AWS SDK for Java v2.
 */
public class S3Base implements ResourceModelSource, WriteableModelSource {

    private static final Logger logger = LoggerFactory.getLogger(S3Base.class);

    static final String DEFAULT_REGION = "us-east-1";

    /**
     * Matches regional AWS S3 hosts such as {@code s3.us-east-2.amazonaws.com},
     * {@code s3-us-west-2.amazonaws.com} or {@code s3.dualstack.eu-west-1.amazonaws.com}.
     */
    private static final Pattern AWS_S3_HOST =
            Pattern.compile("^(?:.+\\.)?s3[.-](?:dualstack\\.)?([a-z]{2}(?:-[a-z]+)+-\\d+)\\.amazonaws\\.com(?:\\.cn)?$");

    private String accessKey;
    private String secretKey;
    private boolean useKey = false;

    private boolean pathStyle = false;

    private String regionName;
    private String endpoint;
    private boolean useSigV2 = false;

    private String bucket;
    private String filePath;
    private String extension;


    private boolean writable=false;

    private IFramework framework;
    private S3Client s3Client;


    /**
     * Sets the S3 client to use instead of building one from the configuration.
     *
     * @param s3Client client to use
     */
    public void setS3Client(S3Client s3Client){
        this.s3Client = s3Client;
    }

    public S3Base(String bucket, String filePath,
           String extension, IFramework framework){
        this.bucket=bucket;
        this.filePath=filePath;
        this.extension=extension;
        this.framework=framework;
    }

    /**
     * Uses static credentials instead of the default credentials provider chain.
     *
     * @param accessKey AWS access key id
     * @param secretKey AWS secret access key
     */
    public void setCredentials(String accessKey, String secretKey){
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.useKey = true;
    }

    /**
     * Sets the AWS region, also used to sign requests sent to a custom endpoint.
     *
     * @param region region name, e.g. us-east-1
     */
    public void setRegion(String region){
        this.regionName = region;
    }

    public void setWritable(){
        this.writable=true;
    }

    /**
     * Sets a custom S3 endpoint. When no scheme is given, https is assumed.
     *
     * @param endpoint endpoint host or URL
     */
    public void setEndpoint(String endpoint){
        this.endpoint = endpoint;
    }

    /**
     * Enables path-style access ({@code endpoint/bucket}) instead of virtual-hosted style
     * ({@code bucket.endpoint}), needed by most S3-compatible services.
     *
     * @param pathStyle true to use path-style access
     */
    public void setPathStyle(boolean pathStyle){
        this.pathStyle = pathStyle;
    }

    /**
     * Requests Signature Version 2. AWS SDK v2 does not support it, so a warning is logged
     * and Signature Version 4 is used.
     */
    public void setUseSigV2() {
        this.useSigV2=true;
    }

    @Override
    public INodeSet getNodes() throws ResourceModelSourceException {
        final ResourceFormatParser parser;
        try {
            parser = getResourceFormatParser();
        } catch (UnsupportedFormatException e) {
            throw new ResourceModelSourceException(
                    "Response content type is not supported: " + extension, e);
        }
        try (InputStream remoteFile = getFile()) {
            return parser.parseDocument(remoteFile);
        } catch (SdkException e) {//file doesn't exist
            throw new ResourceModelSourceException(
                    "Error requesting Resource Model Source from S3",e);
        } catch (ResourceFormatParserException e) {
            throw new ResourceModelSourceException(
                    "Error requesting Resource Model Source from S3, "
                            + "Content could not be parsed: "+e.getMessage(),e);
        } catch (IOException e) {
            throw new ResourceModelSourceException(
                    "Error reading Resource Model Source from S3: "+e.getMessage(),e);
        }

    }

    @Override
    public SourceType getSourceType() {
        return writable ? SourceType.READ_WRITE : SourceType.READ_ONLY;
    }

    @Override
    public WriteableModelSource getWriteable() {
        return writable ? this : null;
    }


    @Override
    public String getSyntaxMimeType() {
        try {
            return getResourceFormatParser().getPreferredMimeType();
        } catch (UnsupportedFormatException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getSourceDescription() {
        return "S3 bucket: "+bucket+", file:"+filePath;
    }

    @Override
    public long readData(OutputStream sink) throws IOException, ResourceModelSourceException {
        if (!hasData()) {
            return 0;
        }
        try (InputStream inputStream = getFile()) {
            return Streams.copyStream(inputStream, sink);
        }
    }

    /**
     * Checks that the remote file exists with a HEAD request, without opening its content.
     *
     * @return true if the object can be read, false on any error (including not found)
     */
    @Override
    public boolean hasData() {
        try{
            getS3Client().headObject(HeadObjectRequest.builder().bucket(bucket).key(filePath).build());
        }catch (Exception e){
            return false;
        }
        return true;
    }

    @Override
    public long writeData(InputStream data) throws IOException, ResourceModelSourceException {
        if (!writable) {
            throw new IllegalArgumentException("Cannot write to file, it is not configured to be writeable");
        }
        File temp = isToFile(data);
        try {
            final INodeSet set = getResourceFormatParser().parseDocument(temp);
        } catch (ResourceFormatParserException e) {
            throw new ResourceModelSourceException(e);
        }
        putFile(temp);
        return temp.length();
    }

    /**
     * Builds the S3 client builder from the configured credentials, region, endpoint and
     * path-style setting.
     *
     * @return configured builder
     */
    S3ClientBuilder buildClientBuilder(){
        if (useSigV2) {
            logger.warn("Signature V2 is not supported in AWS SDK v2; using Signature V4.");
        }
        AwsCredentialsProvider credentialsProvider = useKey
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                : DefaultCredentialsProvider.builder().build();
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(signingRegion())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
        if (!isBlank(endpoint)) {
            builder.endpointOverride(normalizeEndpoint(endpoint));
        }
        return builder;
    }

    /**
     * Region used to sign requests. When the endpoint is a regional AWS S3 host, its region is
     * used, as AWS SDK v1 did; otherwise the configured region (or us-east-1 if none).
     *
     * @return signing region
     */
    Region signingRegion(){
        if (!isBlank(endpoint)) {
            String host = normalizeEndpoint(endpoint).getHost();
            Matcher matcher = AWS_S3_HOST.matcher(host == null ? "" : host.toLowerCase());
            if (matcher.matches()) {
                return Region.of(matcher.group(1));
            }
        }
        return Region.of(isBlank(regionName) ? DEFAULT_REGION : regionName.trim());
    }

    private static URI normalizeEndpoint(String endpoint){
        String value = endpoint.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        return URI.create(value);
    }

    /**
     * @param value value to check
     * @return true if the value is null, empty or whitespace only
     */
    static boolean isBlank(String value){
        return value == null || value.trim().isEmpty();
    }

    private S3Client getS3Client(){
        if(null == s3Client){
            s3Client = buildClientBuilder().build();
        }
        return s3Client;
    }

    private ResponseInputStream<GetObjectResponse> getFile() {
        logger.info("Reading Resource from S3. "+bucket+" "+filePath);
        ResponseInputStream<GetObjectResponse> object = getS3Client().getObject(
                GetObjectRequest.builder().bucket(bucket).key(filePath).build());
        String amazonContentType = object.response().contentType();
        //we can check the xml and json content type from amazon object data
        if(extension.equalsIgnoreCase("xml") || extension.equalsIgnoreCase("json")){
            if(amazonContentType != null && !amazonContentType.equalsIgnoreCase(getMimeType())){
                logger.warn("S3Object content type isn't equals to input content type.");
                logger.warn(
                        "S3 Object content type: "+amazonContentType + " expected content type:"+getMimeType());
            }
        }
        return object;
    }

    private void putFile(File file){
        logger.info("Writing Resource to S3. "+bucket+" "+filePath);
        getS3Client().putObject(
                PutObjectRequest.builder().bucket(bucket).key(filePath).contentType(getMimeType()).build(),
                RequestBody.fromFile(file));
    }


    private String getMimeType(){
        if(extension.equalsIgnoreCase("yaml")){
            return "text/yaml";
        }
        if(extension.equalsIgnoreCase("json")){
            return "application/json";
        }
        return "application/xml";
    }

    private ResourceFormatParser getResourceFormatParser() throws UnsupportedFormatException {
        return framework.getResourceFormatParserService().getParserForMIMEType(getMimeType());
    }

    private File isToFile(InputStream is) throws IOException, ResourceModelSourceException {
        try {
            final ResourceFormatParser parser = getResourceFormatParser();
            File temp = Files.createTempFile("temp", "." + parser.getPreferredFileExtension()).toFile();
            temp.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(temp)) {
                Streams.copyStream(is, fos);
            }
            return temp;
        } catch (UnsupportedFormatException e) {
            throw new ResourceModelSourceException(
                    "Response content type is not supported: " + extension, e);
        }
    }

}
