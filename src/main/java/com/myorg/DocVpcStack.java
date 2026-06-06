package com.myorg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.core.Tag;
import software.amazon.awscdk.core.TagProps;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SelectedSubnets;
import software.amazon.awscdk.services.ec2.Subnet;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.iam.Role;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;

/**
 * Class responsible for creation of VPC Stack
 *
 */

public class DocparserVpcStack extends Stack {
	
	public DocparserVpcStack(final Construct scope, final String id) {
		this(scope, id, null, null);
	}

	public DocparserVpcStack(final Construct scope, final String id, final String stage) {
		this(scope, id, stage, null);
	}

	public DocparserVpcStack(final Construct scope, final String id, final String stage, final StackProps props) {
		super(scope, id, props);

		this.awsEnv = props.getEnv();
		this.stage = stage;

		//Adding Tags to the created resources 
		Tag.add(this, DocparserStackConstants.TAG_KEY, DocparserStackConstants.TAG_VALUE, TagProps.builder().applyToLaunchedInstances(true).build());
		
		if(stage.equals(DocparserStackConstants.QA_STAGE)) {
			Tag.add(this, DocparserStackConstants.ENV_TAG_KEY, DocparserStackConstants.ENV_TAG_VALUE_QA, TagProps.builder().applyToLaunchedInstances(true).build());
		}else if(stage.equals(DocparserStackConstants.PROD_STAGE)) {
			Tag.add(this, DocparserStackConstants.ENV_TAG_KEY, DocparserStackConstants.ENV_TAG_VALUE_PROD, TagProps.builder().applyToLaunchedInstances(true).build());
		}else if(stage.equals(DocparserStackConstants.DEV_STAGE)){
			Tag.add(this, DocparserStackConstants.ENV_TAG_KEY, DocparserStackConstants.ENV_TAG_VALUE_PROD, TagProps.builder().applyToLaunchedInstances(true).build());
		}
		
		//Getting the VPC Id from the context
		vpcId = (String)this.getNode().tryGetContext("vpcid");

		//Using the existing VPC from the account
		this.vpc = Vpc.fromLookup(this, DocparserStackConstants.DOCPARSER_PREFIX +"vpc-stack-" + stage, VpcLookupOptions.builder()
				.vpcId(vpcId).subnetGroupNameTag("aws-cdk:subnet-name").build());

		SelectedSubnets pubSubnets = vpc.selectSubnets(SubnetSelection.builder()
				.subnetType(SubnetType.PUBLIC).build());

		SelectedSubnets privSubnets = vpc.selectSubnets(SubnetSelection.builder()
				.subnetType(SubnetType.PRIVATE).build());

				CfnOutput.Builder.create(this, "stack-subnets-public")
				.value(pubSubnets.getSubnetIds().toString()).build(); 

				CfnOutput.Builder.create(this, "stack-subnets-private")
				.value(privSubnets.getSubnetIds().toString()).build(); 

		CfnOutput.Builder.create(this, DocparserStackConstants.DOCPARSER_PREFIX + "vpc-stack-"+ stage +"result")
		.value("Successfully Created Docparser VPC Stack in " + stage).build(); 
		
	}
	
	/*
	 * Getters and Setters
	 * */

	public IVpc getVpc() {
		return vpc;
	}

	public void setVpc(IVpc vpc) {
		this.vpc = vpc;
	}

	public List<Role> getRoleList() {
		return roleList;
	}

	public void setRoleList(List<Role> roleList) {
		this.roleList = roleList;
	}

	public String getStage() {
		return stage;
	}

	public void setStage(String stage) {
		stage = stage;
	}


	List<Role> roleList;
	private String stage;
	private String vpcId;
	private IVpc vpc;
	private Environment awsEnv;

}
