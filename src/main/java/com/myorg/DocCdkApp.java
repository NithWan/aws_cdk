package com.myorg;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public class DocparserCdkApp {

	static Environment makeEnv(String account, String region) {
		account = (account == null) ? System.getenv("CDK_DEFAULT_ACCOUNT") : account;
		region = (region == null) ? System.getenv("CDK_DEFAULT_REGION") : region;

		return Environment.builder()
				.account(account)
				.region(region)
				.build();
	}

	public static void main(final String[] args) {

		app = new App();

		//Getting the Stage from context
		stage = (String) app.getNode().tryGetContext("stage");

		if(stage.equals(DocparserStackConstants.DEV_STAGE)) {

			awsEnvironmnent = makeEnv("793330927009", "us-east-1");

			docparserVpcStack = new DocparserVpcStack(app, "DocparserVpcStack" + stage, stage, StackProps.builder().env(awsEnvironmnent)
					.stackName(DocparserStackConstants.DOCPARSER_PREFIX + "vpc-stack-" + stage).build());
			docparserEcsInfraStack = new DocparserEcsInfraStack(app, "DocparserEcsInfraStack" + stage, docparserVpcStack.getVpc(), stage, StackProps.builder().env(awsEnvironmnent)
					.stackName(DocparserStackConstants.DOCPARSER_PREFIX + "ecs-infra-stack-" + stage).build());
			docparserEcsServiceStack = new DocparserEcsServiceStack(app, "DocparserEcsServiceStack" + stage, docparserVpcStack.getVpc(),
					docparserEcsInfraStack.getCluster(), stage, docparserEcsInfraStack.getTaskDefinitonList(), StackProps.builder().env(awsEnvironmnent).stackName(DocparserStackConstants.DOCPARSER_PREFIX + "ecs-service-stack-" + stage).build());

		}else if(stage.equals(DocparserStackConstants.QA_STAGE)) {

			awsEnvironmnent = makeEnv("793330927009", "us-east-1");

			docparserVpcStack = new DocparserVpcStack(app, "DocparserVpcStack" + stage, stage, StackProps.builder().env(awsEnvironmnent)
					.stackName(DocparserStackConstants.DOCPARSER_PREFIX + "vpc-stack-" + stage).build());
			docparserEcsInfraStack = new DocparserEcsInfraStack(app, "DocparserEcsInfraStack" + stage, docparserVpcStack.getVpc(), stage, StackProps.builder().env(awsEnvironmnent)
					.stackName(DocparserStackConstants.DOCPARSER_PREFIX + "ecs-infra-stack-" + stage).build());
			docparserEcsServiceStack = new DocparserEcsServiceStack(app, "DocparserEcsServiceStack" + stage, docparserVpcStack.getVpc(),
					docparserEcsInfraStack.getCluster(), stage, docparserEcsInfraStack.getTaskDefinitonList(), StackProps.builder().env(awsEnvironmnent).stackName(DocparserStackConstants.DOCPARSER_PREFIX + "ecs-service-stack-" + stage).build());

		}else if(stage.equals(DocparserStackConstants.PROD_STAGE)) {

			awsEnvironmnent = makeEnv("793330927009", "us-east-1");

			docparserVpcStack = new DocparserVpcStack(app, "DocparserVpcStack" + stage, stage, StackProps.builder().env(awsEnvironmnent)
					.stackName(DocparserStackConstants.DOCPARSER_PREFIX + "vpc-stack-" + stage).build());
			docparserEcsInfraStack = new DocparserEcsInfraStack(app, "DocparserEcsInfraStack" + stage, docparserVpcStack.getVpc(), stage, StackProps.builder().env(awsEnvironmnent)
					.stackName(DocparserStackConstants.DOCPARSER_PREFIX + "ecs-infra-stack-" + stage).build());
			docparserEcsServiceStack = new DocparserEcsServiceStack(app, "DocparserEcsServiceStack" + stage, docparserVpcStack.getVpc(),
					docparserEcsInfraStack.getCluster(), stage, docparserEcsInfraStack.getTaskDefinitonList(), StackProps.builder().env(awsEnvironmnent).stackName(DocparserStackConstants.DOCPARSER_PREFIX + "ecs-service-stack-" + stage).build());

		}

		app.synth();
	}

	private static DocparserEcsServiceStack docparserEcsServiceStack; 
	private static DocparserEcsInfraStack docparserEcsInfraStack;
	private static DocparserVpcStack docparserVpcStack;
	private static Environment awsEnvironmnent;
	private static String stage;
	private static App app;
}
