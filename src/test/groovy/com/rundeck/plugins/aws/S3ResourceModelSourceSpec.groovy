package com.rundeck.plugins.aws

import com.dtolabs.rundeck.core.common.IFramework
import com.dtolabs.rundeck.core.plugins.configuration.Property
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class S3ResourceModelSourceSpec extends Specification {

    @TempDir
    Path tempDir

    S3Client client

    def cleanup() {
        client?.close()
    }

    def "key and secret build a static credentials provider"() {
        when:
        client = buildClient(key: 'AKIDEXAMPLE', secret: 'secretExample')
        def provider = client.serviceClientConfiguration().credentialsProvider()

        then:
        provider instanceof StaticCredentialsProvider
        provider.resolveCredentials().accessKeyId() == 'AKIDEXAMPLE'
        provider.resolveCredentials().secretAccessKey() == 'secretExample'
    }

    def "credentials file is read for accessKey and secretKey"() {
        given:
        def file = tempDir.resolve('AwsCredentials.properties').toFile()
        file.text = 'accessKey=AKIDFILE\nsecretKey=secretFile\n'

        when:
        client = buildClient(credential: file.absolutePath)
        def provider = client.serviceClientConfiguration().credentialsProvider()

        then:
        provider instanceof StaticCredentialsProvider
        provider.resolveCredentials().accessKeyId() == 'AKIDFILE'
        provider.resolveCredentials().secretAccessKey() == 'secretFile'
    }

    def "credentials file missing a key is rejected"() {
        given:
        def file = tempDir.resolve('AwsCredentials.properties').toFile()
        file.text = content

        when:
        createSource(credential: file.absolutePath)

        then:
        thrown IllegalArgumentException

        where:
        content << ['accessKey=AKIDFILE\n', 'secretKey=secretFile\n', '']
    }

    def "credentials file that does not exist is rejected"() {
        when:
        createSource(credential: tempDir.resolve('missing.properties').toString())

        then:
        thrown IllegalArgumentException
    }

    def "no explicit credentials use the default credentials chain"() {
        when:
        client = buildClient(props)

        then:
        client.serviceClientConfiguration().credentialsProvider() instanceof DefaultCredentialsProvider

        where:
        props << [
                [:],
                [key: '', secret: ''],
                [key: '  ', secret: 'secretExample'],
                [key: 'AKIDEXAMPLE', secret: ''],
        ]
    }

    def "region without endpoint configures the region and no endpoint override"() {
        when:
        client = buildClient(region: 'eu-west-1')

        then:
        client.serviceClientConfiguration().region() == Region.EU_WEST_1
        !client.serviceClientConfiguration().endpointOverride().isPresent()
    }

    def "custom endpoint is used as endpoint override and the configured region is kept for signing"() {
        when:
        client = buildClient(region: 'us-west-2', endpoint: endpoint)

        then:
        client.serviceClientConfiguration().endpointOverride().get() == URI.create(expected)
        client.serviceClientConfiguration().region() == Region.US_WEST_2

        where:
        endpoint                 | expected
        'localhost:9000'         | 'https://localhost:9000'
        'http://minio:9000'      | 'http://minio:9000'
        'https://s3.example.com' | 'https://s3.example.com'
    }

    def "regional AWS endpoint host determines the signing region"() {
        when:
        client = buildClient(region: 'us-east-1', endpoint: endpoint)

        then:
        client.serviceClientConfiguration().region() == Region.of(expected)

        where:
        endpoint                                   | expected
        's3.us-east-2.amazonaws.com'               | 'us-east-2'
        'https://s3.eu-west-1.amazonaws.com'       | 'eu-west-1'
        's3-us-west-2.amazonaws.com'               | 'us-west-2'
        'https://s3.dualstack.ap-south-1.amazonaws.com' | 'ap-south-1'
        'https://S3.CA-CENTRAL-1.AMAZONAWS.COM'    | 'ca-central-1'
        's3.cn-north-1.amazonaws.com.cn'           | 'cn-north-1'
        's3.amazonaws.com'                         | 'us-east-1'
        'http://minio:9000'                        | 'us-east-1'
        'https://storage.example.com/s3'           | 'us-east-1'
    }

    def "path style setting controls the URL style"() {
        when:
        client = buildClient(props + [endpoint: 'http://minio:9000'])
        def url = client.utilities().getUrl { it.bucket('bkt').key('k') }.toString()

        then:
        url == expected

        where:
        props               | expected
        [pathstyle: 'true'] | 'http://minio:9000/bkt/k'
        [pathstyle: 'false']| 'http://bkt.minio:9000/k'
        [:]                 | 'http://bkt.minio:9000/k'
    }

    def "signature v2 is ignored and the client still builds"() {
        when:
        client = buildClient(key: 'AKIDEXAMPLE', secret: 'secretExample', useSigV2: 'true')

        then:
        client != null
    }

    def "force signature v4 no longer sets a JVM system property"() {
        given:
        def before = new HashSet(System.properties.keySet())

        when:
        client = buildClient(forcev4: 'true')

        then:
        (System.properties.keySet() - before).isEmpty()
    }

    def "plugin configuration properties are unchanged"() {
        when:
        def properties = new S3ResourceModelSource(Mock(IFramework)).description.properties.collect {
            [it.name, it.type, it.defaultValue, it.required]
        }

        then:
        properties == [
                ['key', Property.Type.String, null, false],
                ['secret', Property.Type.String, null, false],
                ['credential', Property.Type.String, null, false],
                ['region', Property.Type.FreeSelect, 'us-east-1', true],
                ['endpoint', Property.Type.String, null, false],
                ['forcev4', Property.Type.Boolean, 'false', false],
                ['useSigV2', Property.Type.Boolean, 'false', false],
                ['pathstyle', Property.Type.Boolean, 'false', false],
                ['bucket', Property.Type.String, null, true],
                ['file', Property.Type.String, null, true],
                ['extension', Property.Type.Select, 'xml', true],
                ['writable', Property.Type.Boolean, 'false', false],
        ]
    }

    private S3Base createSource(Map<String, String> config) {
        def props = new Properties()
        props.putAll([region: 'us-east-1', bucket: 'bkt', file: 'nodes.xml', extension: 'xml'] + config)
        return (S3Base) new S3ResourceModelSource(Mock(IFramework)).createResourceModelSource(props)
    }

    private S3Client buildClient(Map<String, String> config) {
        return createSource(config).buildClientBuilder().build()
    }
}
