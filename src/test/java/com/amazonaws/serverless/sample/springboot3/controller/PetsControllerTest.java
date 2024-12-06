package com.amazonaws.serverless.sample.springboot3.controller;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.GetRestApisRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.*;
@Testcontainers
class PetsControllerTest {

    @Container
    private static final LocalStackContainer localStack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3.8")
    )
    .withServices(CLOUDFORMATION, LAMBDA, API_GATEWAY)
    .withStartupTimeout(Duration.ofMinutes(2));

    private static CloudFormationClient cloudFormationClient;
    private static ApiGatewayClient apiGatewayClient;
    private static LambdaClient lambdaClient;

    @BeforeAll
    public static void setup() {
        // Prepare credentials
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
            localStack.getAccessKey(), 
            localStack.getSecretKey()
        );

        // Configure clients
        cloudFormationClient = CloudFormationClient.builder()
            .endpointOverride(localStack.getEndpointOverride(CLOUDFORMATION))
            .region(Region.of(localStack.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build();

        apiGatewayClient = ApiGatewayClient.builder()
            .endpointOverride(localStack.getEndpointOverride(API_GATEWAY))
            .region(Region.of(localStack.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build();

        lambdaClient = LambdaClient.builder()
            .endpointOverride(localStack.getEndpointOverride(LAMBDA))
            .region(Region.of(localStack.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build();
    }

    @Test
    public void testCloudFormationDeployment() throws Exception {
        // Read CloudFormation template
        String template = new String(
            Files.readAllBytes(Paths.get("src/test/resources/petstore-cloudformation.yaml"))
        );

        // Create CloudFormation stack
        String stackName = "PetStoreApiStack";
        CreateStackRequest createStackRequest = CreateStackRequest.builder()
            .stackName(stackName)
            .templateBody(template)
            .capabilities(Capability.CAPABILITY_AUTO_EXPAND)
            .build();

        String stackId = cloudFormationClient.createStack(createStackRequest).stackId();

        // Wait for stack creation
        waitForStackCreation(stackName);

        // Verify stack resources
        assertNotNull(stackId, "Stack should be created successfully");

        // Additional verifications
        verifyLambdaFunction();
        verifyApiGateway();
    }

    private void waitForStackCreation(String stackName) throws InterruptedException {
        int maxAttempts = 30;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            DescribeStacksRequest describeRequest = DescribeStacksRequest.builder()
                .stackName(stackName)
                .build();

            var stackDescription = cloudFormationClient.describeStacks(describeRequest)
                .stacks().get(0);

            if (stackDescription.stackStatusAsString().equals(StackStatus.CREATE_COMPLETE.toString())) {
                return;
            }

            if (stackDescription.stackStatusAsString().endsWith("_FAILED")) {
                fail("Stack creation failed: " + stackDescription.stackStatusReason());
            }

            TimeUnit.SECONDS.sleep(10);
        }
        fail("Stack creation timed out");
    }

    private void verifyLambdaFunction() {
        var functions = lambdaClient.listFunctions(ListFunctionsRequest.builder().build())
            .functions();
        
        assertTrue(
            functions.stream().anyMatch(f -> f.functionName().equals("pet-store-boot-3")),
            "Lambda function should be created"
        );
    }

    private void verifyApiGateway() {
        var restApis = apiGatewayClient.getRestApis(GetRestApisRequest.builder().build())
            .items();
        
        assertFalse(restApis.isEmpty(), "API Gateway should have at least one REST API");
    }

    @AfterAll
    public static void cleanup() {
        // Clean up resources
        if (cloudFormationClient != null) {
            cloudFormationClient.deleteStack(
                DeleteStackRequest.builder()
                    .stackName("PetStoreApiStack")
                    .build()
            );
        }
    }
}