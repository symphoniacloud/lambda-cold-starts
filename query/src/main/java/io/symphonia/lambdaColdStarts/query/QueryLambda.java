package io.symphonia.lambdaColdStarts.query;

import com.amazonaws.services.lambda.runtime.Context;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.ServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class QueryLambda {
    private final ObjectMapper objectMapper =
            new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final LambdaClient lambdaClient = LambdaClient.create();
    private final S3Client s3Client = S3Client.builder().region(Region.US_EAST_1).build();

    private final String applicationId = System.getenv("APPLICATION_ID");
    private final String reportingBucketName = System.getenv("REPORTING_BUCKET");
    private final String testReportingBucketName = System.getenv("TEST_REPORTING_BUCKET");

    private final List<String> runtimes = List.of("java8", "java11");
    private final List<String> memorySizes = List.of("512", "1536", "3008");
    private final List<String> artifactSizes = List.of("small", "large");
    private final List<String> ids = List.of("one", "two", "three");

    public void handler(QueryParameters queryParameters, Context context) {
        context.getLogger().log("Lambda called with: " + queryParameters.toString());
        final String bucket = queryParameters.isScheduledTrigger() ? reportingBucketName : testReportingBucketName;
        ids.forEach(id -> invokeBenchmarkLambdaForId(
                id, queryParameters.invocationClass, bucket, context.getLogger()));
    }

    public void invokeBenchmarkLambdaForId(String id, String invocationClass, String bucket, LambdaLogger logger) {
        runtimes.forEach(runtime ->
                memorySizes.forEach(memorySize ->
                        artifactSizes.forEach(artifactSize ->
                                invokeBenchmarkLambda(runtime, memorySize, artifactSize, id, invocationClass, bucket, logger)
                        )));

        invokeBenchmarkLambda("java8", "512", "largeuberjar", id, invocationClass, bucket, logger);
        invokeBenchmarkLambda("python38", "1536", "small", id, invocationClass, bucket, logger);
    }

    public void invokeBenchmarkLambda(String runtime, String memorySize, String artifactSize, String id, String invocationClass, String bucket, LambdaLogger logger) {
        try {
            final String functionName = String.format("%s-%s-%s-%s-%s", applicationId, runtime, memorySize, artifactSize, id);
            final InvokeBenchmarkDetails benchmarkDetails = invoke(functionName, logger);
            saveResult(bucket, benchmarkDetails, runtime, memorySize, artifactSize, id, logger, invocationClass);
        } catch(ServiceException | JsonProcessingException e) {
            logger.log(e.toString());
            throw new RuntimeException(e);
        }
    }

    private InvokeBenchmarkDetails invoke(String functionName, LambdaLogger logger) {
        logger.log(String.format("Invoking Lambda Function: %s", functionName));
        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromUtf8String("\"\""))
                .build();

        Instant start = Instant.now();
        lambdaClient.invoke(request);
        Instant end = Instant.now();

        return new InvokeBenchmarkDetails(start, end);
    }

    private void saveResult(String bucket, InvokeBenchmarkDetails benchmarkDetails, String runtime, String memorySize, String artifactSize, String id, LambdaLogger logger, String invocationClass) throws JsonProcessingException {
        final String key = String.format("timings/date=%s/%s.json", new SimpleDateFormat("yyyy-MM-dd").format(new Date()), UUID.randomUUID().toString());
        logger.log(String.format("Saving reporting record to: %s", key));
        final ReportingRecord record = new ReportingRecord(benchmarkDetails, runtime, memorySize, artifactSize, id, invocationClass);
        final String content = objectMapper.writeValueAsString(record);
        logger.log(content);

        final PutObjectRequest s3Request = PutObjectRequest.builder().bucket(bucket).key(key).build();
        s3Client.putObject(s3Request, RequestBody.fromString(content));
    }

    private static class InvokeBenchmarkDetails {
        private final Instant start;
        private final long durationInMillis;

        public InvokeBenchmarkDetails(Instant start, Instant end) {
            this.start = start;
            this.durationInMillis = Duration.between(start, end).toMillis();
        }
    }

    private static class ReportingRecord {
        public final String timestamp;
        public final long durationMillis;
        public final String invocationClass;
        public final String region = System.getenv("AWS_REGION");
        public final String runtime;
        public final String artifactSize;
        public final String memorySize;
        public final String id;

        public ReportingRecord(InvokeBenchmarkDetails invokeDetails, String runtime, String memorySize, String artifactSize, String id, String invocationClass) {
            this.timestamp = invokeDetails.start.toString();
            this.durationMillis = invokeDetails.durationInMillis;
            this.invocationClass = invocationClass;
            this.runtime = runtime;
            this.artifactSize = artifactSize;
            this.memorySize = memorySize;
            this.id = id;
        }
    }
}
