package com.amazonaws.serverless.sample.springboot3.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.amazonaws.serverless.sample.springboot3.integration.utils.LambdaCompilerHelper;

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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.*;

/***
 * No resource provider found for "AWS::Serverless::Function". <br/>
 * 
 * To find out if AWS::Serverless::Function is supported in LocalStack Pro, please check out our docs at <br> 
 *  https://docs.localstack.cloud/user-guide/aws/cloudformation/#resources-pro--enterprise-edition <br>
 *  https://docs.localstack.cloud/user-guide/state-management/support/
 * 
 * **/
@Testcontainers
class PetsIntegrationByCloudFormationTest {
	 public static final String DASHES = new String(new char[80]).replace("\0", "-");
	 public static final String DOUBLE_DASHES = new String(new char[80]).replace("\0", "=");

    @Container
    private static final LocalStackContainer localStack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3.8")
    )
    .withServices(CLOUDFORMATION, LAMBDA, API_GATEWAY)
    .withEnv("DEBUG", "1")
    .withEnv("LOCALSTACK_WEB_UI", "1")
    .withExposedPorts(8080)
    .withLogConsumer((log) -> System.out.println(log.getUtf8String()))
//    .waitingFor(
//            Wait.forLogMessage("localStack Ready to accept connections", 1)
//    )
   
    .withStartupTimeout(Duration.ofMinutes(2));
//    .withEnv("LOCALSTACK_API_KEY", "<your_api_key>");

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
        
        StaticCredentialsProvider scs = StaticCredentialsProvider.create(credentials);
        String region = localStack.getRegion();
        
        // Configure clients
        cloudFormationClient = CloudFormationClient.builder()
            .endpointOverride(localStack.getEndpointOverride(CLOUDFORMATION))
            .region(Region.of(region))
            .credentialsProvider(scs)
            .build();

        apiGatewayClient = ApiGatewayClient.builder()
            .endpointOverride(localStack.getEndpointOverride(API_GATEWAY))
            .region(Region.of(region))
            .credentialsProvider(scs)
            .build();

        lambdaClient = LambdaClient.builder()
            .endpointOverride(localStack.getEndpointOverride(LAMBDA))
            .region(Region.of(region))
            .credentialsProvider(scs)
            .build();
    }

    @Test
    @Disabled
    public void testCloudFormationDeployment() throws Exception { 
        // Read CloudFormation template
        String template = new String(
            Files.readAllBytes(Paths.get("src/test/resources/petstore-cloudformation.yaml"))
        );

        // Create Lambda Zip
        createLambdaZip(null);
        
        // Create CloudFormation stack
        String stackName = "PetStoreApiStack";
        CreateStackRequest createStackRequest = CreateStackRequest.builder()
            .stackName(stackName)
            .templateBody(template)
            .capabilities(Capability.CAPABILITY_IAM,Capability.CAPABILITY_NAMED_IAM,Capability.CAPABILITY_AUTO_EXPAND)
            .build();

        CreateStackResponse csr = cloudFormationClient.createStack(createStackRequest);
        String stackId = csr.stackId();
        
        System.out.println(DASHES);
        System.out.println(stackId);
        System.out.println(csr.toString());
        System.out.println(DASHES);
        System.out.println(this.localStack.getLogs());
        
        // Wait for stack creation
        waitForStackCreation(stackName);        
        System.out.println(DASHES);
        System.out.println(this.localStack.getLogs());
        
        // Verify stack resources
        assertNotNull(stackId, "Stack should be created successfully");

        // Additional verifications
        verifyLambdaFunction();
        verifyApiGateway();
    }

    private void waitForStackCreation(String stackName) throws InterruptedException {
        int maxAttempts = 5;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            DescribeStacksRequest describeRequest = DescribeStacksRequest.builder()
                .stackName(stackName)
                .build();
            
            DescribeStacksResponse dsr = cloudFormationClient.describeStacks(describeRequest);
            
           
            List<Stack> stacks = dsr.stacks();
            Stack stack = stacks.get(0);            
            String stackStatus = stack.stackStatusAsString();
            
            if (StackStatus.CREATE_COMPLETE.toString().equals(stackStatus)) {
                return;
            }else   {
            	;
            	String detailedStatusAsString = stack.detailedStatusAsString();
            	System.out.println(DOUBLE_DASHES);
            	System.out.println(dsr.toString());
            	System.out.println(stacks.size());
            	System.out.println(stack.description());
            	System.out.println(stackStatus);
            	System.out.println(detailedStatusAsString);
            	System.out.println(stack.detailedStatus());
            	System.out.println(DOUBLE_DASHES);
            }

            if (stackStatus.endsWith("_FAILED")) {
                fail("Stack creation failed: " + stack.stackStatusReason());
            }

            System.out.println(stackStatus);
            TimeUnit.SECONDS.sleep(10);
        }
        fail("Stack creation timed out");
    }
    private static byte[] createLambdaZip(Class<?> lambdaClass) throws IOException {
    	if(lambdaClass ==null) {
    		return null;
    	}
		// bulk-events-stage/target/lambda.zip
		// single-event-stage/target/lambda.zip
		String simpleName = lambdaClass.getSimpleName();
		String lambdaName = switch (simpleName) {
		case "BulkEventsLambda" -> "bulk-events-stage";
		case "SingleEventLambda" -> "single-event-stage";
		default -> null;
		};
		return LambdaCompilerHelper.getTestJarBytes(lambdaName);
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