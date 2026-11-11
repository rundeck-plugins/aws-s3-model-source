package com.rundeck.plugins.aws

import com.dtolabs.rundeck.core.common.IFramework
import com.dtolabs.rundeck.core.common.INodeSet
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException
import com.dtolabs.rundeck.core.resources.format.ResourceFormatParser
import com.dtolabs.rundeck.core.resources.format.ResourceFormatParserService
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import spock.lang.Specification


class S3BaseSpec extends Specification{

    def "retrieve resource success"(){
        given:

        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)
        def s3Client = Mock(S3Client)

        S3Base s3 = new S3Base(bucket, filePath, extension, framework)
        s3.setS3Client(s3Client)

        when:
        def result = s3.getNodes()

        then:
        1 * s3Client.getObject({ GetObjectRequest r -> r.bucket() == bucket && r.key() == filePath }) >> objectStream(remoteType)
        result == nodeSet

        where:
        bucket  | filePath                  | extension | remoteType
        'test'  | 'resources/resources.xml' | 'xml'     | 'application/xml'
        'test'  | 'resources/resources.yaml'| 'yaml'    | null
        'test'  | 'resources/resources.json'| 'json'    | 'application/json'

    }

    def "retrieve resource without remote content type does not fail"(){
        given:
        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)
        def s3Client = Mock(S3Client)

        S3Base s3 = new S3Base('test', filePath, extension, framework)
        s3.setS3Client(s3Client)

        when:
        def result = s3.getNodes()

        then:
        1 * s3Client.getObject(_ as GetObjectRequest) >> objectStream(null)
        result == nodeSet

        where:
        filePath                  | extension
        'resources/resources.xml' | 'xml'
        'resources/resources.json'| 'json'
    }

    def "retrieve resource closes the remote stream"(){
        given:
        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)
        def content = new TrackingInputStream("a".getBytes())
        def s3Client = Mock(S3Client){
            getObject(_ as GetObjectRequest) >> objectStream('application/xml', content)
        }

        S3Base s3 = new S3Base('test', 'resources/resources.xml', 'xml', framework)
        s3.setS3Client(s3Client)

        when:
        s3.getNodes()

        then:
        content.closed
    }


    def "catch SdkException and throws ResourceModelSourceException"(){
        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)
        def s3Client = Mock(S3Client){
            getObject(_ as GetObjectRequest) >> {throw SdkClientException.builder().message('').build()}
        }

        S3Base s3 = new S3Base(bucket, filePath, extension, framework)
        s3.setS3Client(s3Client)

        when:
        s3.getNodes()

        then:
        thrown ResourceModelSourceException
        where:
        bucket  | filePath                  | extension
        'test'  | 'resources/resources.xml' | 'xml'

    }

    def "write data remote resource"(){
        given:

        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)

        def is = new ByteArrayInputStream("a".getBytes())
        S3Base s3 = new S3Base(bucket, filePath, extension, framework)
        s3.setWritable()
        def s3Client = Mock(S3Client)
        s3.setS3Client(s3Client)

        when:
        def result = s3.writeData(is)

        then:
        1 * s3Client.putObject(
                { PutObjectRequest r -> r.bucket() == bucket && r.key() == filePath && r.contentType() == contentType },
                _ as RequestBody
        ) >> PutObjectResponse.builder().build()
        result == 1

        where:
        bucket  | filePath                  | extension | contentType
        'test'  | 'resources/resources.xml' | 'xml'     | 'application/xml'
        'test'  | 'resources/resources.yaml'| 'yaml'    | 'text/yaml'
        'test'  | 'resources/resources.json'| 'json'    | 'application/json'
    }

    def "write data on a read only configuration"(){
        given:

        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)

        def is = new ByteArrayInputStream("a".getBytes())
        S3Base s3 = new S3Base(bucket, filePath, extension, framework)
        def s3Client = Mock(S3Client)
        s3.setS3Client(s3Client)

        when:
        s3.writeData(is)

        then:
        thrown IllegalArgumentException
        0 * s3Client.putObject(_, _)

        where:
        bucket  | filePath                  | extension
        'test'  | 'resources/resources.xml' | 'xml'
        'test'  | 'resources/resources.yaml'| 'yaml'
        'test'  | 'resources/resources.json'| 'json'
    }

    def "has data"(){
        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)
        def s3Client = Mock(S3Client)

        S3Base s3 = new S3Base(bucket, filePath, extension, framework)
        s3.setS3Client(s3Client)

        when:
        def result = s3.hasData()

        then:
        1 * s3Client.headObject({ HeadObjectRequest r -> r.bucket() == bucket && r.key() == filePath }) >>
                HeadObjectResponse.builder().build()
        0 * s3Client.getObject(_)
        result

        where:
        bucket  | filePath                  | extension
        'test'  | 'resources/resources.xml' | 'xml'
        'test'  | 'resources/resources.yaml'| 'yaml'
        'test'  | 'resources/resources.json'| 'json'
    }

    def "cant read file, so has no data"(){
        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)
        def s3Client = Mock(S3Client){
            headObject(_ as HeadObjectRequest) >> {throw error}
        }

        S3Base s3 = new S3Base('test', 'resources/resources.xml', 'xml', framework)
        s3.setS3Client(s3Client)

        when:
        def result = s3.hasData()

        then:
        !result

        where:
        error << [
                SdkClientException.builder().message('').build(),
                S3Exception.builder().statusCode(404).message('Not Found').build()
        ]
    }

    def "invalid endpoint configuration means no data"(){
        given:
        S3Base s3 = new S3Base('test', 'resources/resources.xml', 'xml', getFramework(Mock(INodeSet)))
        s3.setEndpoint('my host:9000')

        expect:
        !s3.hasData()
    }

    def "read data fetches the object once"(){
        given:
        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)
        def s3Client = Mock(S3Client)
        def sink = new ByteArrayOutputStream()

        S3Base s3 = new S3Base('test', 'resources/resources.xml', 'xml', framework)
        s3.setS3Client(s3Client)

        when:
        def result = s3.readData(sink)

        then:
        1 * s3Client.headObject(_ as HeadObjectRequest) >> HeadObjectResponse.builder().build()
        1 * s3Client.getObject(_ as GetObjectRequest) >> objectStream('application/xml', new ByteArrayInputStream('abc'.bytes))
        result == 3
        sink.toString() == 'abc'
    }


    private static ResponseInputStream<GetObjectResponse> objectStream(String contentType, InputStream content = new ByteArrayInputStream(new byte[0])) {
        return new ResponseInputStream<>(
                GetObjectResponse.builder().contentType(contentType).build(),
                AbortableInputStream.create(content)
        )
    }

    private IFramework getFramework(INodeSet nodeSet) {
        return Mock(IFramework) {
            _ * getResourceFormatParserService() >> Mock(ResourceFormatParserService) {
                _ * getParserForMIMEType(_) >> Mock(ResourceFormatParser) {
                    _ * parseDocument(_) >> nodeSet
                    _ * getPreferredFileExtension() >> 'xml'

                }
            }
        }
    }

    /**
     * Input stream that records whether it was closed.
     */
    static class TrackingInputStream extends ByteArrayInputStream {
        boolean closed = false

        TrackingInputStream(byte[] buf) {
            super(buf)
        }

        @Override
        void close() throws IOException {
            closed = true
            super.close()
        }
    }

}
