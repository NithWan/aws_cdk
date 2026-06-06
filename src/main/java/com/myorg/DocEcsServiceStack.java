package com.myorg;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.core.Tag;
import software.amazon.awscdk.core.TagProps;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroupProps;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SecurityGroupProps;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.UserData;
import software.amazon.awscdk.services.ecs.AddCapacityOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.Ec2Service;
import software.amazon.awscdk.services.ecs.Ec2ServiceProps;
import software.amazon.awscdk.services.ecs.EcsOptimizedImage;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateServiceProps;
import software.amazon.awscdk.services.ecs.LoadBalancerTargetOptions;
import software.amazon.awscdk.services.ecs.TaskDefinition;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetGroupsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListenerRule;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListenerRuleProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroupProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationLoadBalancerTarget;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.iam.IManagedPolicy;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;



public class DocparserEcsServiceStack extends Stack{
	
	public DocparserEcsServiceStack(Construct scope, String id, Cluster cluster, String stage, List<TaskDefinition> taskDefinitionList, IVpc vpc) {
		this(scope, id, vpc, cluster, stage, taskDefinitionList, null);
	}
	
	public DocparserEcsServiceStack(Construct scope, String id, IVpc vpc, Cluster cluster, String stage, List<TaskDefinition> taskDefinitionList ,StackProps props) {
		super(scope,id,props);

		this.vpc = vpc;
		this.cluster = cluster;
		this.taskDefinitonList = taskDefinitionList;

		//Adding tags to the created resources 
		Tag.add(this, "Owner", "ocparser", TagProps.builder().applyToLaunchedInstances(true).build());

		//Security Group for ALB
		albSecurityGroup = new SecurityGroup(this, DocparserStackConstants.DOCPARSER_PREFIX + "alb-sg-" + stage, SecurityGroupProps.builder()
				.vpc(vpc)
				.securityGroupName(DocparserStackConstants.DOCPARSER_PREFIX +"alb-sg-"+ stage)
				.description("Sercurity group for clusters load balacerns")
				.allowAllOutbound(true)
				.build());
		albSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(80));

		clusterManagedPolicyList = new ArrayList<IManagedPolicy>();
		clusterManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("AmazonS3FullAccess"));
		clusterManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("CloudWatchFullAccess"));
		clusterManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("AmazonTextractFullAccess"));
		clusterManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("ComprehendFullAccess"));
		clusterManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonEC2ContainerServiceforEC2Role"));
		clusterManagedPolicyList.add(ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonEC2ContainerServiceRole"));

		//Create Instance role
		ecsInstanceRole = new Role(this, DocparserStackConstants.DOCPARSER_PREFIX + "cluster-role-"+ stage, RoleProps.builder()
				.roleName("ecsInstanceRoleCdk"+ stage)
				.assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
				.managedPolicies(clusterManagedPolicyList)
				.build());
		
		//Bootstrap with Sophos Antivirus
		UserData userData = UserData.forLinux();
		userData.addCommands("yum -y update" ,
		 		"yum -y install python-pip" ,
		 		"pip install awscli" ,
		 		"aws s3 cp --region us-east-1 s3://docparser-antivirus/Sophos/SophosInstall.sh /tmp/" ,
		 		"chmod +x /tmp/SophosInstall.sh", 
		 		"/tmp/SophosInstall.sh");

		//Create Auto Scaling Group
		autoScalingGroup = new AutoScalingGroup(this, DocparserStackConstants.DOCPARSER_PREFIX + "auto-scaling-grp-"+ stage, AutoScalingGroupProps.builder()
				.minCapacity(0)
				.maxCapacity(0)
				.desiredCapacity(0)
				.keyName("DocparserKey")
				.vpc(vpc)				
				.associatePublicIpAddress(true)
				.vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
				.instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.SMALL))
				.machineImage(EcsOptimizedImage.amazonLinux2())
				.userData(userData)
				.role(ecsInstanceRole)
				.build());
		cluster.addAutoScalingGroup(autoScalingGroup);
		

		//Create Application Load balancer's and Listener
		applicationLoadBalancer = new ApplicationLoadBalancer(this, DocparserStackConstants.DOCPARSER_PREFIX + "app-load-balancer-" + stage, ApplicationLoadBalancerProps.builder()
				.loadBalancerName(DocparserStackConstants.DOCPARSER_PREFIX + "alb-" + stage)
				.internetFacing(false)
				.vpc(vpc)
				.vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
				.securityGroup(albSecurityGroup)
				.build());

		applicationListener = applicationLoadBalancer.addListener("public-listener-" + stage, 
				BaseApplicationListenerProps.builder()
				.open(true)
				.port(80)
				.build()); 	 	

		//Create ECS Service for Parser App
		parserEc2Service = new Ec2Service(this, DocparserStackConstants.DOCPARSER_PREFIX + "parser-service-" + stage, 
				Ec2ServiceProps.builder()
				.serviceName(DocparserStackConstants.DOCPARSER_PREFIX + "parser-service-" + stage)
				.taskDefinition(taskDefinitonList.get(0))
				.desiredCount(0)
				.healthCheckGracePeriod(Duration.seconds(500))
				.cluster(cluster)
				.build());

		parserTargetList = new ArrayList<IApplicationLoadBalancerTarget>();
		parserTargetList.add(parserEc2Service.loadBalancerTarget(LoadBalancerTargetOptions.builder().containerName(DocparserStackConstants.DOCPARSER_PREFIX + "parser-container-" + stage).containerPort(8088).build()));

		parserTargetGroup = new ApplicationTargetGroup(this, DocparserStackConstants.DOCPARSER_PREFIX + "parser-tg-" + stage, ApplicationTargetGroupProps.builder()
				.targetGroupName(DocparserStackConstants.DOCPARSER_PREFIX + "parser-tg-" + stage)
				.vpc(vpc)
				.targetType(TargetType.INSTANCE)
				.targets(parserTargetList)
				.port(80)
				.protocol(ApplicationProtocol.HTTP)
				.healthCheck(HealthCheck.builder().interval(Duration.seconds(300)).timeout(Duration.seconds(5)).path("/document").healthyHttpCodes("200-499").build())
				.build());

		parserTargetGroupList = new ArrayList<IApplicationTargetGroup>();
		parserTargetGroupList.add(parserTargetGroup);

		// Create Fargate Service for Workflow App
		workflowFargateService = new FargateService(this, DocparserStackConstants.DOCPARSER_PREFIX + "workflow-service-" + stage, 
				FargateServiceProps.builder()
				.serviceName(DocparserStackConstants.DOCPARSER_PREFIX + "workflow-service-" + stage)
				.taskDefinition(taskDefinitonList.get(1))
				.desiredCount(0)
				.assignPublicIp(true)
				.healthCheckGracePeriod(Duration.seconds(500))
				.vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
				.cluster(cluster)
				.build());

		workflowServiceList = new ArrayList<IApplicationLoadBalancerTarget>();
		workflowServiceList.add(workflowFargateService.loadBalancerTarget(LoadBalancerTargetOptions.builder().containerName(DocparserStackConstants.DOCPARSER_PREFIX + "workflow-container-" + stage).containerPort(8081).build()));

		workflowTargetGroup = new ApplicationTargetGroup(this, DocparserStackConstants.DOCPARSER_PREFIX + "workflow-tg-" + stage, ApplicationTargetGroupProps.builder()
				.targetGroupName(DocparserStackConstants.DOCPARSER_PREFIX + "workflow-tg-" + stage)
				.vpc(vpc)
				.targetType(TargetType.IP)
				.targets(workflowServiceList)
				.port(80)
				.protocol(ApplicationProtocol.HTTP)
				.healthCheck(HealthCheck.builder().interval(Duration.seconds(300)).timeout(Duration.seconds(5)).path("/document").healthyHttpCodes("200-499").build())
				.build());

		workflowTargetGroupList = new ArrayList<IApplicationTargetGroup>();
		workflowTargetGroupList.add(workflowTargetGroup);
		
		pythonEngineEc2Service = new Ec2Service(this, DocparserStackConstants.DOCPARSER_PREFIX + "pyhthon-engine-service-" + stage, 
				Ec2ServiceProps.builder()
				.serviceName(DocparserStackConstants.DOCPARSER_PREFIX + "pyhthon-engine-service-" + stage)
				.taskDefinition(taskDefinitonList.get(2))
				.desiredCount(0)
				.healthCheckGracePeriod(Duration.seconds(500))
				.cluster(cluster)
				.build());

		pythonEngineEc2ServiceList = new ArrayList<IApplicationLoadBalancerTarget>();
		pythonEngineEc2ServiceList.add(pythonEngineEc2Service.loadBalancerTarget(LoadBalancerTargetOptions.builder().containerName(DocparserStackConstants.DOCPARSER_PREFIX + "python-engine-container-" + stage).containerPort(5000).build()));

		pythonEngineTargetGroup = new ApplicationTargetGroup(this, DocparserStackConstants.DOCPARSER_PREFIX + "python-engine-tg-" + stage, ApplicationTargetGroupProps.builder()
				.targetGroupName(DocparserStackConstants.DOCPARSER_PREFIX + "python-engine-tg-" + stage)
				.vpc(vpc)
				.targetType(TargetType.INSTANCE)
				.targets(pythonEngineEc2ServiceList)
				.port(80)
				.protocol(ApplicationProtocol.HTTP)
				.healthCheck(HealthCheck.builder().interval(Duration.seconds(300)).timeout(Duration.seconds(5)).path("/textractpython").healthyHttpCodes("200-499").build())
				.build());

		pythonEngineTargetGroupList = new ArrayList<IApplicationTargetGroup>();
		pythonEngineTargetGroupList.add(pythonEngineTargetGroup);
		
		emailReaderFargateService = new FargateService(this, DocparserStackConstants.DOCPARSER_PREFIX + "email-reader-service-" + stage, 
				FargateServiceProps.builder()
				.serviceName(DocparserStackConstants.DOCPARSER_PREFIX + "email-reader-service-" + stage)
				.taskDefinition(taskDefinitonList.get(3))
				.desiredCount(0)
				.assignPublicIp(true)
				.vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
				.cluster(cluster)
				.build());

		//Creating the rules for listener
		ApplicationListenerRule listenerRule1 = new ApplicationListenerRule(this, DocparserStackConstants.DOCPARSER_PREFIX + "rule1-" + stage, ApplicationListenerRuleProps.builder()
				.pathPattern("/document/api/rossumProcess/*").priority(1)
				.listener(applicationListener)
				.targetGroups(parserTargetGroupList)
				.build());

		ApplicationListenerRule listenerRule2 = new ApplicationListenerRule(this, DocparserStackConstants.DOCPARSER_PREFIX + "rule2-" + stage, ApplicationListenerRuleProps.builder()
				.pathPattern("/document/api/textractProcess/*").priority(2)
				.listener(applicationListener)
				.targetGroups(parserTargetGroupList)
				.build()); 

		ApplicationListenerRule listenerRule3 = new ApplicationListenerRule(this, DocparserStackConstants.DOCPARSER_PREFIX + "rule3-" + stage, ApplicationListenerRuleProps.builder()
				.pathPattern("/document/*").priority(3)
				.listener(applicationListener)
				.targetGroups(workflowTargetGroupList)
				.build());

		ApplicationListenerRule listenerRule4 = new ApplicationListenerRule(this, DocparserStackConstants.DOCPARSER_PREFIX + "rule4-" + stage, ApplicationListenerRuleProps.builder()
				.pathPattern("/textractpython/*").priority(4)
				.listener(applicationListener)
				.targetGroups(pythonEngineTargetGroupList)
				.build());

		applicationListener.addTargetGroups(DocparserStackConstants.DOCPARSER_PREFIX + "target-" + stage, AddApplicationTargetGroupsProps.builder().targetGroups(workflowTargetGroupList).build());
		
		CfnOutput.Builder.create(this, DocparserStackConstants.DOCPARSER_PREFIX + "load-balancer-dns-" + stage).value(applicationLoadBalancer.getLoadBalancerDnsName()).build();
		
		//Success message
		CfnOutput.Builder.create(this, DocparserStackConstants.DOCPARSER_PREFIX + "ecs-services-stack-"+ stage+"-result")
								.value("Successfully Created Docparser Services in " + stage.toUpperCase()).build();
	}	

	private ApplicationListener applicationListener;
	private ApplicationLoadBalancer applicationLoadBalancer;
	private ArrayList<IApplicationTargetGroup> pythonEngineTargetGroupList;
	private ApplicationTargetGroup pythonEngineTargetGroup;
	private List<IApplicationTargetGroup> workflowTargetGroupList;
	private ApplicationTargetGroup workflowTargetGroup;
	private List<IApplicationTargetGroup> parserTargetGroupList;
	private ApplicationTargetGroup parserTargetGroup;
	private List<IApplicationLoadBalancerTarget> emailReaderFargateServiceList;
	private List<IApplicationLoadBalancerTarget> pythonEngineFargateServiceList;
	private List<IApplicationLoadBalancerTarget> pythonEngineEc2ServiceList;
	private List<IApplicationLoadBalancerTarget> workflowServiceList;
	private List<IApplicationLoadBalancerTarget> parserTargetList;
	private Ec2Service pythonEngineEc2Service;
	private FargateService workflowFargateService;
	private FargateService emailReaderFargateService;
	private Ec2Service parserEc2Service;
	private AutoScalingGroup autoScalingGroup;
	private Role ecsInstanceRole;
	private SecurityGroup ec2SecurityGroup;
	private SecurityGroup albSecurityGroup;
	private List<IManagedPolicy> clusterManagedPolicyList;
	private List<TaskDefinition> taskDefinitonList;
	private Cluster cluster;
	private IVpc vpc;

}
