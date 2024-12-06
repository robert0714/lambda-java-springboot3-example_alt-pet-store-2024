# Serverless Spring Boot 3 example
A basic pet store written with the [Spring Boot 3 framework](https://projects.spring.io/spring-boot/). Unlike older examples, this example is relying on the new 
`SpringDelegatingLambdaContainerHandler`, which you simply need to identify as a   _handler_  of the Lambda function. The main configuration class identified as `MAIN_CLASS`
environment variable or `Start-Class` or `Main-Class` entry in Manifest file. See provided `template.yml` file  for reference. 


The application can be deployed in an AWS account using the [Serverless Application Model](https://github.com/awslabs/serverless-application-model). The `template.yml` file in the root folder contains the application definition.

## Pre-requisites
* [AWS CLI](https://aws.amazon.com/cli/)
* [SAM CLI](https://github.com/awslabs/aws-sam-cli)
* [Gradle](https://gradle.org/) or [Maven](https://maven.apache.org/)

## Deployment
In a shell, navigate to the sample's folder and use the SAM CLI to build a deployable package
```
$ sam deploy --s3-bucket $CF_BUCKET --stack-name alt-pet-store --capabilities CAPABILITY_IAM
```
Original command line:
```bash
$ sam build
```
This command compiles the application and prepares a deployment package in the `.aws-sam` sub-directory.

To deploy the application in your AWS account, you can use the SAM CLI's guided deployment process and follow the instructions on the screen

```
$ sam deploy --guided
```

Once the deployment is completed, the SAM CLI will print out the stack's outputs, including the new application URL. You can use `curl` or a web browser to make a call to the URL

```
...
---------------------------------------------------------------------------------------------------------
OutputKey-Description                        OutputValue
---------------------------------------------------------------------------------------------------------
PetStoreApi - URL for application            https://xxxxxxxxxx.execute-api.us-west-2.amazonaws.com/pets
---------------------------------------------------------------------------------------------------------

$ curl https://xxxxxxxxxx.execute-api.us-west-2.amazonaws.com/pets
```

You can also try a complex request passing both path and request parameters to  complex endpoint such as this:


```
@RequestMapping(path = "/foo/{gender}/bar/{age}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
public String complexRequest(@RequestBody String body,
        @PathVariable("gender") String gender,
        @PathVariable("age") String age,
        @RequestParam("name") String name
)
```
For example.

```
curl -d '{"key1":"value1", "key2":"value2"}' -H "Content-Type: application/json" -X POST https://zuhd709386.execute-api.us-east-2.amazonaws.com/foo/male/bar/25?name=Ricky
```

## Reference
* https://github.com/aws/serverless-java-container/wiki/Quick-start---Spring-Boot3
* Orign source: 
  * https://github.com/aws/serverless-java-container/tree/main/samples/springboot3/alt-pet-store
  * https://aws.amazon.com/cn/blogs/china/re-platforming-java-applications-using-the-updated-aws-serverless-java-container/

# TEARING DOWN RESOURCES
When you run `sam deploy`, it creates or updates a CloudFormation `stack`—a set of resources that has a name, which you’ve seen already with the `--stack-name` parameter of `sam deploy`.

When you want to clean up your AWS account after trying an example, the simplest method is to find the corresponding CloudFormation stack in the AWS Web Console (in the CloudFormation section) and delete the stack using the **Delete** button.

Alternatively, you can tear down the stack from the command line. For example, to tear down the **alt-pet-store** stack, run the following:
```bash
$ PIPELINE_BUCKET="$(aws cloudformation describe-stack-resource --stack-name alt-pet-store --logical-resource-id PipelineStartBucket --query 'StackResourceDetail.PhysicalResourceId' --output text)"
$ aws s3 rm s3://${PIPELINE_BUCKET}/sampledata.json
$ aws s3 rm s3://${PIPELINE_BUCKET} --recursive
$ aws cloudformation delete-stack --stack-name alt-pet-store
```

