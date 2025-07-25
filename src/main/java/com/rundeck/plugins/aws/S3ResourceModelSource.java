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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.s3.S3ClientOptions;
import com.dtolabs.rundeck.core.common.IFramework;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyUtil;
import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants;
import com.dtolabs.rundeck.core.resources.ResourceModelSource;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceFactory;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;

@Plugin(name = "aws-s3-source", service = ServiceNameConstants.ResourceModelSource)
public class S3ResourceModelSource implements ResourceModelSourceFactory,Describable {
    public static final String PROVIDER_NAME = "aws-s3-source";

    private IFramework framework;


    public static final String KEY = "key";
    public static final String SECRET = "secret";
    public static final String CREDENTIALFILE = "credential";
    public static final String BUCKET = "bucket";
    public static final String REGION = "region";
    public static final String ENDPOINT = "endpoint";
    public static final String FORCEV4 = "forcev4";
    public static final String USESIGV2 = "useSigV2";
    public static final String PATHSTYLE = "pathstyle";

    public static final String FILE = "file";
    public static final String EXTENSION = "extension";
    public static final String WRITABLE="writable";





    private static List<String> selectValues = new ArrayList<>(Arrays.asList
            ("us-east-1",
                    "us-east-2",
                    "us-west-1",
                    "us-west-2",
                    "ca-central-1",
                    "ap-south-1",
                    "ap-northeast-2",
                    "ap-southeast-1",
                    "ap-southeast-2",
                    "ap-northeast-1",
                    "eu-central-1",
                    "eu-west-1",
                    "eu-west-2",
                    "sa-east-1"

            ));
    private static List<String> formats = new ArrayList<>(Arrays.asList
            (
                    "xml",
                    "yaml",
                    "json"
            ));

    final static Map<String, Object> renderingOptionsAuthentication = getRenderOpt("Credentials",false, true);
    final static Map<String, Object> renderingOptionsConnection = getRenderOpt("Connection",false, false);
    final static Map<String, Object> renderingOptionsResource = getRenderOpt("Resource",false, false);

    protected static Map<String, Object> getRenderOpt(String value, boolean secondary, boolean password) {
        Map<String, Object> ret = new HashMap<>();
        ret.put(StringRenderingConstants.GROUP_NAME,value);
        if(secondary){
            ret.put(StringRenderingConstants.GROUPING,"secondary");
        }
        if(password){
            ret.put("displayType",StringRenderingConstants.DisplayType.PASSWORD);
        }
        return ret;
    }


    static Description DESC = DescriptionBuilder.builder()
            .name(PROVIDER_NAME)
            .title("AWS S3 remote model source")
            .description("Obtain nodes information from a file located in a S3 bucket")
            .property(PropertyUtil.string(KEY, "AWS Access Key", "AWS Access Key.", false,
                    null,null,null, renderingOptionsAuthentication))
            .property(PropertyUtil.string(SECRET, "AWS Secret Key", "AWS Secret Key.", false,
                    null,null,null, renderingOptionsAuthentication))
            .property(PropertyUtil.string(CREDENTIALFILE, "AWS Credentials File", "Path to a AWSCredentials.properties file " +
                    "containing 'accessKey' and 'secretKey'.", false,
                    null,null,null, renderingOptionsAuthentication))

            .property(PropertyUtil.freeSelect(REGION,"S3 Region",
                    "AWS S3 Region to use.  You can use one of the supported region names.",
                    true, "us-east-1",selectValues,null,null,renderingOptionsConnection))
            .property(PropertyUtil.string(ENDPOINT, "S3 Endpoint",
                    "S3 endpoint to connect to, the region is ignored if this is set.", false,
                    null,null,null,renderingOptionsConnection))
            .property(PropertyUtil.bool(FORCEV4, "Force Signature v4",
                    "Whether to force use of Signature Version 4 authentication. Default: false",
                    false,"false",null,renderingOptionsConnection))
            .property(PropertyUtil.bool(USESIGV2, "Use Signature v2",
                    "Use of Signature Version 2 authentication for old container. Default: false",
                    false,"false",null,renderingOptionsConnection))
            .property(PropertyUtil.bool(PATHSTYLE, "Use Path Style",
                    "Whether to access the Endpoint using `endpoint/bucket` style, default: false. The default will " +
                            "use DNS style `bucket.endpoint`, which may be incompatible with non-AWS S3-compatible services",
                    false,"false",null,renderingOptionsConnection))

            .property(PropertyUtil.string(BUCKET, "Bucket name", "Bucket name.", true,
                    null,null,null,renderingOptionsResource))
            .property(PropertyUtil.string(FILE, "Resource file", "Path on the bucket with the node information.", true,
                    null,null,null,renderingOptionsResource))
            .property(PropertyUtil.select(EXTENSION,"File format",
                    "File format.",
                    true, "xml",formats,null,renderingOptionsResource))
            .property(PropertyUtil.bool(WRITABLE, "Writable",
                    "Allow to write the remote file.",
                    false,"false",null,renderingOptionsResource))
            .build();


    public Description getDescription() {
        return DESC;
    }

    public S3ResourceModelSource(final IFramework framework) {
        this.framework = framework;
    }

    public ResourceModelSource createResourceModelSource(final Properties properties) throws ConfigurationException {
        String regionName = properties.getProperty(REGION);
        String bucketName = properties.getProperty(BUCKET);
        String fileName = properties.getProperty(FILE);
        String extension =  properties.getProperty(EXTENSION);
        final S3Base s3 = new S3Base(bucketName,fileName,extension,framework);
        if(properties.getProperty(KEY) != null && properties.getProperty(SECRET) != null) {
            s3.setCredentials(properties.getProperty(KEY), properties.getProperty(SECRET));
        }else if(properties.getProperty(CREDENTIALFILE) != null){
            File creds = new File(properties.getProperty(CREDENTIALFILE));
            if (!creds.exists() || !creds.canRead()) {
                throw new IllegalArgumentException("Credentials file does not exist or cannot be read: " +
                        properties.getProperty(CREDENTIALFILE));
            }
            try {
                PropertiesCredentials cred = new PropertiesCredentials(creds);
                s3.setCredentials(cred);
            } catch (IOException e) {
                throw new RuntimeException("Credentials file could not be read: " + properties.getProperty(CREDENTIALFILE) + ": " + e
                        .getMessage(), e);
            }
        }
        if (Boolean.valueOf(properties.getProperty(FORCEV4))) {
            System.setProperty(SDKGlobalConfiguration.ENFORCE_S3_SIGV4_SYSTEM_PROPERTY, "true");
        }
        if (null == properties.getProperty(ENDPOINT) || "".equals(properties.getProperty(ENDPOINT).trim())) {
            s3.setRegion(regionName);
        } else {
            s3.setEndpoint(properties.getProperty(ENDPOINT));
        }
        if (Boolean.valueOf(properties.getProperty(WRITABLE))) {
            s3.setWritable();
        }
        if (Boolean.valueOf(properties.getProperty(USESIGV2))) {
            s3.setUseSigV2();
        }

        if(Boolean.valueOf(properties.getProperty(PATHSTYLE))) {
            S3ClientOptions clientOptions = new S3ClientOptions();
            clientOptions.setPathStyleAccess(Boolean.valueOf(properties.getProperty(PATHSTYLE)));
            s3.setS3ClientOptions(clientOptions);
        }

        return s3;
    }


}