package com.myorg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.core.Tag;
import software.amazon.awscdk.core.TagProps;
import software.amazon.awscdk.services.cloudwatch.MetricProps;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.RepositoryProps;
import software.amazon.awscdk.services.ecs.AddCapacityOptions;
import software.amazon.awscdk.services.ecs.AwsLogDriver;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ClusterProps;
import software.amazon.awscdk.services.ecs.Compatibility;
import software.amazon.awscdk.services.ecs.ContainerDefinition;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.FargateTaskDefinitionProps;
import software.amazon.awscdk.services.ecs.ICluster;
import software.amazon.awscdk.services.ecs.NetworkMode;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.TaskDefinition;
import software.amazon.awscdk.services.ecs.TaskDefinitionProps;
import software.amazon.awscdk.services.iam.IManagedPolicy;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.LogGroupProps;

public class DocparserEcsInfraStack extends Stack {

	public DocparserEcsInfraStack(Construct scope, String id, IVpc vpc, String stage) {
		this(scope, id, vpc, stage, null);
	}

	public DocparserEcsInfraStack(Construct scope, String id, IVpc vpc, String stage, StackProps props)  {
		super(scope, id, props);

		this.vpc = vpc;

		//Adding tags to the created resources 
		Tag.add(this, DocparserStackConstants.TAG_KEY, DocparserStackConstants.TAG_VALUE , TagProps.builder().applyToLaunchedInstances(true).build());

		if(stage.equals(DocparserStackConstants.QA_STAGE)) {
			Tag.add(this, DocparserStackConstants.ENV_TAG_KEY, DocparserStackConstants.ENV_TAG_VALUE_QA, TagProps.builder().applyToLaunchedInstances(true).build());
		}else if(stage.equals(DocparserStackConstants.PROD_STAGE)) {
			Tag.add(this, DocparserStackConstants.ENV_TAG_KEY, DocparserStackConstants.ENV_TAG_VALUE_PROD, TagProps.builder().applyToLaunchedInstances(true).build());
		}else if(stage.equals(DocparserStackConstants.DEV_STAGE)){
			Tag.add(this, DocparserStackConstants.ENV_TAG_KEY, DocparserStackConstants.ENV_TAG_VALUE_DEV, TagProps.builder().applyToLaunchedInstances(true).build());
		}

		taskManagedPolicyList = new ArrayList<IManagedPolicy>();
		taskManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("AmazonDynamoDBFullAccess"));
		taskManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("AmazonS3FullAccess"));
		taskManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("CloudWatchFullAccess"));
		taskManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("AmazonTextractFullAccess"));
		taskManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("ComprehendFullAccess"));
		taskManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy"));

		ecsTaskExecutionRole = new Role(this, DocparserStackConstants.DOCPARSER_PREFIX + "task-role-" + stage, RoleProps.builder().roleName("docparserTaskExecutionRoleCdk"+ stage).assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com")).managedPolicies(taskManagedPolicyList).build());

		//Create the ECS Cluster within the Vpc
		cluster = new Cluster(this, DocparserStackConstants.DOCPARSER_PREFIX + "cluster-" + stage, ClusterProps.builder()
				.clusterName(DocparserStackConstants.DOCPARSER_PREFIX + stage)
				.vpc(vpc)
				.containerInsights(true)
				.build());
		
		//		sonarcubeCluster = new Cluster(this, DocparserStackConstants.SONARCUBE_PREFIX + "cluster-" + stage + ClusterProps.builder()
		//		.clusterName(DocparserStackConstants.SONARCUBE_PREFIX + stage)
		//		.vpc(vpc)
		//		.build());

		//		//Create repository for parser
		//		parserRepository = new Repository(this, DocparserStackConstants.DOCPARSER_PREFIX + "parser-repository-" + stage, RepositoryProps.builder().repositoryName("docparser-"+ stage).build());
		//		CfnOutput.Builder.create(this, "paser-repo-name").value("Parser Repo URI -"+ parserRepository.getRepositoryUri()).build();
		//
		//		//Create repository for workflow
		//		workflowRepository = new Repository(this, DocparserStackConstants.DOCPARSER_PREFIX + "workflow-repository-" + stage, RepositoryProps.builder().repositoryName("docworkflow"+ stage).build());
		//		CfnOutput.Builder.create(this, "workflow-repo").value("workflow Repo URI -"+ workflowRepository.getRepositoryUri()).build();
		//
		//		//Create repository for workflow
		//		pythonEngineRepository = new Repository(this, DocparserStackConstants.DOCPARSER_PREFIX + "python-engine-repository-" + stage, RepositoryProps.builder().repositoryName("pythonengine"+ stage).build());
		//		CfnOutput.Builder.create(this, "python-engine-repo").value("workflow Repo URI -"+ pythonEngineRepository.getRepositoryUri()).build();
		//
		//		//Create repository for workflow
		//		emailReaderRepository = new Repository(this, DocparserStackConstants.DOCPARSER_PREFIX + "email-reader-repository-" + stage, RepositoryProps.builder().repositoryName("emailreader"+ stage).build());
		//		CfnOutput.Builder.create(this, "email-reader-repo").value("workflow Repo URI -"+ emailReaderRepository.getRepositoryUri()).build();

		//Create ECS Parser Task Definition 
		parserTaskDefinition = new TaskDefinition(this, DocparserStackConstants.DOCPARSER_PREFIX + "parser-task-definition-" + stage, TaskDefinitionProps.builder()
				.cpu("1024")
				.memoryMiB("1024")
				.networkMode(NetworkMode.HOST)
				.compatibility(Compatibility.EC2)
				.executionRole(ecsTaskExecutionRole)
				.taskRole(ecsTaskExecutionRole)
				.build());

		//Log drivers for logging into CloudWatch logs
		parserLogGroup = new LogGroup(this, DocparserStackConstants.DOCPARSER_PREFIX + "parser-lg-" + stage, LogGroupProps.builder().logGroupName(DocparserStackConstants.DOCPARSER_PREFIX + stage + "-parser-lg").build());
		parserLogger = new AwsLogDriver(AwsLogDriverProps.builder().logGroup(parserLogGroup).streamPrefix("cdk-docparser-" + stage).build());

		//Adding Containers to ECS Parser Task definition
		parserContainer = parserTaskDefinition.addContainer(DocparserStackConstants.DOCPARSER_PREFIX + "parser-container-" + stage, ContainerDefinitionOptions.builder()
				.image(ContainerImage.fromEcrRepository(Repository.fromRepositoryName(this, DocparserStackConstants.DOCPARSER_PREFIX + "parser-image-" + stage, "docparser"), "v1"))
				.memoryLimitMiB(1024)
				.logging(parserLogger)
				.build());

		parserContainer.addPortMappings(PortMapping.builder().containerPort(8088).build());

		//Create Fargate Workflow Task Definition
		workflowTaskDefinition = new FargateTaskDefinition(this, DocparserStackConstants.DOCPARSER_PREFIX + "workflow-task-definition-" + stage, FargateTaskDefinitionProps.builder()
				.cpu(512)
				.memoryLimitMiB(1024)
				.executionRole(ecsTaskExecutionRole)
				.taskRole(ecsTaskExecutionRole)
				.build());

		//Log drivers for logging into CloudWatch logs
		workflowLogGroup = new LogGroup(this, DocparserStackConstants.DOCPARSER_PREFIX + "workflow-lg-" + stage, LogGroupProps.builder().logGroupName(DocparserStackConstants.DOCPARSER_PREFIX  + stage + "-workflow-lg").build());
		workflowLogger = new AwsLogDriver(AwsLogDriverProps.builder().logGroup(workflowLogGroup).streamPrefix("cdk-workflow-" + stage).build());

		//Adding environment variables required by Workflow Container Definition
		containerEnvironment = new HashMap<String, String>();
		containerEnvironment.put("GOOGLE_APPLICATION_CREDENTIALS", "automl.json");

		//Adding Containers to Fargate Workflow Task definitions
		workflowContainer = workflowTaskDefinition.addContainer(DocparserStackConstants.DOCPARSER_PREFIX + "workflow-container-" + stage, ContainerDefinitionOptions.builder()
				.image(ContainerImage.fromEcrRepository(Repository.fromRepositoryName(this,  DocparserStackConstants.DOCPARSER_PREFIX + "workflow-image-" + stage, "docworkflow"), "v1"))
				.memoryLimitMiB(1024)
				.environment(containerEnvironment)
				.logging(workflowLogger)
				.build());

		workflowContainer.addPortMappings(PortMapping.builder().containerPort(8081).build());

		//Create ECS Python Task Definition
		pythonEngineTaskDefinition = new TaskDefinition(this, DocparserStackConstants.DOCPARSER_PREFIX + "python-engine-task-definition-" + stage, TaskDefinitionProps.builder()
				.cpu("1024")
				.memoryMiB("1024")
				.networkMode(NetworkMode.HOST)
				.compatibility(Compatibility.EC2)
				.executionRole(ecsTaskExecutionRole)
				.taskRole(ecsTaskExecutionRole)
				.build());

		//Log drivers for logging into CloudWatch logs
		pythonEngineLogGroup = new LogGroup(this, DocparserStackConstants.DOCPARSER_PREFIX + "python-engine-lg-" + stage, LogGroupProps.builder().logGroupName(DocparserStackConstants.DOCPARSER_PREFIX +  stage + "-python-engine-lg").build());
		pythonEngineLogger = new AwsLogDriver(AwsLogDriverProps.builder().logGroup(pythonEngineLogGroup).streamPrefix("cdk-pythonengine-" + stage).build());

		//Adding Containers to ECS Python Task definitions
		pythonEngineContainer = pythonEngineTaskDefinition.addContainer(DocparserStackConstants.DOCPARSER_PREFIX + "python-engine-container-" + stage, ContainerDefinitionOptions.builder()
				.image(ContainerImage.fromEcrRepository(Repository.fromRepositoryName(this,  DocparserStackConstants.DOCPARSER_PREFIX + "python-engine-image-" + stage, "pythonengine"), "v1"))
				.memoryLimitMiB(1024)
				.logging(pythonEngineLogger)
				.build());

		pythonEngineContainer.addPortMappings(PortMapping.builder().containerPort(5000).build());

		//Create Fargate Email Reader Task Definition 
		emailReaderTaskDefinition = new FargateTaskDefinition(this, DocparserStackConstants.DOCPARSER_PREFIX + "email-reader-task-definition-" + stage, FargateTaskDefinitionProps.builder()
				.cpu(256)
				.memoryLimitMiB(512)
				.executionRole(ecsTaskExecutionRole)
				.taskRole(ecsTaskExecutionRole)
				.build());

		//Log drivers for logging into CloudWatch logs
		emailReaderLogGroup = new LogGroup(this, DocparserStackConstants.DOCPARSER_PREFIX + "email-reader-lg-" + stage, LogGroupProps.builder().logGroupName(DocparserStackConstants.DOCPARSER_PREFIX  + stage + "-email-reader-lg").build());
		emailReaderLogger = new AwsLogDriver(AwsLogDriverProps.builder().logGroup(emailReaderLogGroup).streamPrefix("cdk-emailreader-" + stage).build());

		//Adding Containers to Fargate Email Reader Task definition
		emailReaderContainer = emailReaderTaskDefinition.addContainer(DocparserStackConstants.DOCPARSER_PREFIX + "email-reader-container-" + stage, ContainerDefinitionOptions.builder()
				.image(ContainerImage.fromEcrRepository(Repository.fromRepositoryName(this, DocparserStackConstants.DOCPARSER_PREFIX + "email-reader-image-" + stage, "emailreader"), "v1"))
				.memoryLimitMiB(512)
				.logging(emailReaderLogger)
				.build());

		//Create ECS Sonarcube Task Definition
//		sonarcubeTaskDefinition = new TaskDefinition(this, DocparserStackConstants.SONARCUBE_PREFIX + "task-definition-" + stage, TaskDefinitionProps.builder()
//				.networkMode(NetworkMode.HOST)
//				.executionRole(ecsTaskExecutionRole)
//				.taskRole(ecsTaskExecutionRole)
//				.compatibility(Compatibility.EC2)
//				.build());

		//Log drivers for logging into CloudWatch logs
		//		sonarcubeLogGroup = new LogGroup(this, DocparserStackConstants.SONARCUBE_PREFIX + "lg-" + stage, LogGroupProps.builder().logGroupName(DocparserStackConstants.SONARCUBE_PREFIX + "sonarcube-log-group-" + stage).build());
		//		sonarcubeLogger = new AwsLogDriver(AwsLogDriverProps.builder().logGroup(sonarcubeLogGroup).streamPrefix("cdk-sonarcube-" + stage).build());
		//
		//		//Adding Containers to ECS Sonarcube Task definitions
		//		sonarcubeContainer = sonarcubeTaskDefinition.addContainer(DocparserStackConstants.DOCPARSER_PREFIX + "container-" + stage, ContainerDefinitionOptions.builder()
		//				.image(ContainerImage.fromRegistry("sonarcube:latest"))
		//				.logging(sonarcubeLogger)
		//				.memoryLimitMiB(512)
		//				.build());
		//
		//		sonarcubeContainer.addPortMappings(PortMapping.builder().containerPort(8090).build());

		taskDefinitonList = new ArrayList<TaskDefinition>();
		taskDefinitonList.add(parserTaskDefinition);
		taskDefinitonList.add(workflowTaskDefinition);
		taskDefinitonList.add(pythonEngineTaskDefinition);
		taskDefinitonList.add(emailReaderTaskDefinition);
		taskDefinitonList.add(sonarcubeTaskDefinition);

		//Success message
		CfnOutput.Builder.create(this, DocparserStackConstants.DOCPARSER_PREFIX + "ecs-infra-stack-"+ stage+"-result")
		.value("Successfully Created Docparser ECS Infra Stack in " + stage).build(); 

	}

	public Cluster getCluster() {
		return cluster;
	}

	public void setCluster(Cluster cluster) {
		this.cluster = cluster;
	}

	public List<TaskDefinition> getTaskDefinitonList() {
		return taskDefinitonList;
	}

	public void setTaskDefinitonList(List<TaskDefinition> taskDefinitonList) {
		this.taskDefinitonList = taskDefinitonList;
	}

	private List<IManagedPolicy> taskManagedPolicyList;
	private Map<String, String> containerEnvironment;
	private ContainerDefinition sonarcubeContainer;
	private ContainerDefinition emailReaderContainer;
	private ContainerDefinition pythonEngineContainer;
	private ContainerDefinition workflowContainer;
	private ContainerDefinition parserContainer;
	private LogGroup sonarcubeLogGroup;
	private LogGroup emailReaderLogGroup;
	private LogGroup pythonEngineLogGroup;
	private LogGroup workflowLogGroup;
	private LogGroup parserLogGroup;
	private AwsLogDriver sonarcubeLogger;
	private AwsLogDriver emailReaderLogger;
	private AwsLogDriver pythonEngineLogger;
	private AwsLogDriver workflowLogger;
	private AwsLogDriver parserLogger;
	private List<TaskDefinition> taskDefinitonList;
	private Repository emailReaderRepository;
	private Repository pythonEngineRepository;
	private Repository parserRepository;
	private Repository workflowRepository;
	private FargateTaskDefinition emailReaderTaskDefinition;
	private FargateTaskDefinition workflowTaskDefinition;
	private TaskDefinition sonarcubeTaskDefinition;
	private TaskDefinition pythonEngineTaskDefinition;
	private TaskDefinition parserTaskDefinition;
	private Role ecsTaskExecutionRole;
	private Cluster sonarcubeCluster;
	private Cluster cluster;
	private IVpc vpc;
}
