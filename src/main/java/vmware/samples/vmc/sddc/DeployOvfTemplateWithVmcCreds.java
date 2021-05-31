/*
 * *******************************************************
 * Copyright VMware, Inc. 2019.  All Rights Reserved.
 * SPDX-License-Identifier: MIT
 * *******************************************************
 *
 * DISCLAIMER. THIS PROGRAM IS PROVIDED TO YOU "AS IS" WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, WHETHER ORAL OR WRITTEN,
 * EXPRESS OR IMPLIED. THE AUTHOR SPECIFICALLY DISCLAIMS ANY IMPLIED
 * WARRANTIES OR CONDITIONS OF MERCHANTABILITY, SATISFACTORY QUALITY,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE.
 */
package vmware.samples.vmc.sddc;

import com.vmware.content.library.ItemTypes.FindSpec;
import com.vmware.vapi.bindings.Structure;
import com.vmware.vapi.client.ApiClient;
import com.vmware.vapi.data.DataValue;
import com.vmware.vapi.data.StringValue;
import com.vmware.vapi.protocol.HttpConfiguration;
import com.vmware.vcenter.VM;
import com.vmware.vcenter.ovf.ExtraConfig;
import com.vmware.vcenter.ovf.ExtraConfigParams;
import com.vmware.vcenter.ovf.LibraryItemTypes.DeploymentResult;
import com.vmware.vcenter.ovf.LibraryItemTypes.DeploymentTarget;
import com.vmware.vcenter.ovf.LibraryItemTypes.OvfSummary;
import com.vmware.vcenter.ovf.LibraryItemTypes.ResourcePoolDeploymentSpec;
import com.vmware.vcenter.ovf.OvfParams;
import com.vmware.vcenter.vm.hardware.Ethernet;
import com.vmware.vcenter.vm.hardware.EthernetTypes;
import com.vmware.vcenter.vm.hardware.EthernetTypes.BackingType;
import com.vmware.vmc.model.Sddc;
import com.vmware.vmc.orgs.Sddcs;
import org.apache.commons.cli.Option;
import org.apache.commons.lang.StringUtils;
import vmware.samples.common.VcenterAuthorizationStubUtil;
import vmware.samples.common.VmcSamplesAbstractBase;
import vmware.samples.common.authentication.VapiAuthenticationHelper;
import vmware.samples.common.authentication.VmcAuthenticationHelper;
import vmware.samples.contentlibrary.client.ClsApiClient;
import vmware.samples.vcenter.helpers.FolderHelper;
import vmware.samples.vcenter.helpers.NetworkHelper;
import vmware.samples.vcenter.helpers.ResourcePoolHelper;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demonstrates the workflow to deploy an OVF library item to a resource pool in VMware Cloud on AWS.
 * Note: the sample needs an existing library item with an OVF template
 * and an existing resource pool with resources for deploying the VM.
 *
 * Author: VMware, Inc.
 * Sample Prerequisites: The sample needs an existing OVF
 * library item and a resources for creating the VM.
 * 
 *
 */
public class DeployOvfTemplateWithVmcCreds extends VmcSamplesAbstractBase {

    public static final String proxySharedSecretVal = "lfADEuxqVN+JlG+b/wOW5BjiBUJur5avxKjk4GLUeOlcZWSX/NOxZOlavOTPd5rReu+qpV1AfKUMJ0tOKQlg7zqKwzz4K1GUes6ev3QgMLbIaUxGqPXDL9XDb/4DCNGofRpiwMZrw8PWln2Kc4NE7WwKQRWuTupr5QlhO9zdUpVPg2A0J+7paTZRkA3ZQNGz3W+6s1PXiK3gRBXBCE00ztL2WcFJvemrqc9i/zcVf9Pxs52J4RRXSa8/Ibtf2uo/Dg3M9t/YmwTZs+22yeFpCpm/qpocA72q8uqMTxrkNP1QVjkPlMr0KSHcBSr4QSgNU1o0yZ6USUNLBx/e0U+k5UQ==";
    private String libItemName;
    private String vmName;
    private String  resourcePoolName, opaqueNetwrokName;
	private ClsApiClient contentLibClient;
	private String resourcePoolID;
	private String vmId;
	private Ethernet ethernetService;
	private VM vmService;
	private String folderName;
	private String folderID;
    private Sddcs sddcsStub;
    private VM vmStub;
    private ApiClient vmcClient;
    private VapiAuthenticationHelper vapiAuthHelper;
    private String orgId, sddcId;
    protected VcenterAuthorizationStubUtil authorizationStubUtil;

    /**
     * Define the options specific to this sample and configure the sample using
     * command-line arguments or a config file
     *
     * @param args command line arguments passed to the sample
     */
    protected void parseArgs(String[] args) {
        Option orgOption = Option.builder()
                .longOpt("org_id")
                .desc("Specify the organization id")
                .argName("ORGANIZATION ID")
                .required(true)
                .hasArg()
                .build();
        Option sddcOption = Option.builder()
                .longOpt("sddc_id")
                .desc("Specify the SDDC id")
                .argName("SDDC ID")
                .required(true)
                .hasArg()
                .build();
        //Parse the command line options or use config file        
        Option libItemNameOption = Option.builder()
                .longOpt("libitemname")
                .desc("REQUIRED: The name of the library item to"
                      + "deploy. The library item "
                      + "should contain an OVF package")
                .required(true)
                .hasArg()
                .argName("CONTENT LIBRARY ITEM")
                .build();

        Option resourcePoolOption = Option.builder()
        		.longOpt("resourcepoolname")
                .desc("REQUIRED: The name of the resource pool to be used.")
                .required(true)
                .hasArg()
                .argName("RESOURCE POOL")
                .build();

        Option opaqueNetworkName = Option.builder()
        		.longOpt("opaquenetworkname")
                .required(true)
                .desc("REQUIRED: The name of the opaque network to be added to the deployed vm")
                .hasArg()
                .argName("OPEQUE NETWORK NAME")
                .build();

        Option vmNameOption = Option.builder()
                .longOpt("vmname")
                .desc("OPTIONAL: The name of the VM to be created in "
                	  + "the cluster. Defaults to a generated VM name "
                	  + "based on the current date if not specified")
                .required(false)
                .hasArg()
                .argName("VM NAME")
                .build();       

        Option folderNameOption = Option.builder()
        		.longOpt("foldername")
                .required(false)
                .desc("OPTIONAL:  The name of the folder to be used. Defaults to 'Workloads'")
                .hasArg()
                .argName("FOLDER NAME")
                .build();     

        List<Option> optionList = Arrays.asList(libItemNameOption,
        		vmNameOption, resourcePoolOption, opaqueNetworkName,
        		folderNameOption,orgOption,sddcOption);
        
        super.parseArgs(optionList, args);       
        
        this.resourcePoolName = (String) parsedOptions.get("resourcepoolname");
        this.opaqueNetwrokName =  (String) parsedOptions.get("opaquenetworkname");
        this.libItemName = (String) parsedOptions.get("libitemname");
        this.vmName =  (String) parsedOptions.get("vmname");
        this.folderName = (String) parsedOptions.get("foldername");
        this.orgId = (String) parsedOptions.get("org_id");
        this.sddcId = (String) parsedOptions.get("sddc_id");
    }

    protected void setup() throws Exception {
        // Get vCenter hostname, username and password
        this.vmcAuthHelper = new VmcAuthenticationHelper();
        this.vmcClient = this.vmcAuthHelper.newVmcClient(this.vmcServer, this.cspServer, this.refreshToken);
        this.sddcsStub = vmcClient.createStub(Sddcs.class);
        this.vmStub = vmcClient.createStub(VM.class);

        Sddc sddc = sddcsStub.get(orgId, sddcId);
        URL vcServerUrl = new URL(sddc.getResourceConfig().getVcUrl());
        String vcServer = vcServerUrl.getHost();
        String vcUsername = sddc.getResourceConfig().getCloudUsername();
        String vcPassword = sddc.getResourceConfig().getCloudPassword();

        vapiAuthHelper = new VapiAuthenticationHelper();
        this.sessionStubConfig =
                vapiAuthHelper.loginByUsernameAndPassword(
                        vcServer, vcUsername, vcPassword, new HttpConfiguration.Builder().getConfig());

        this.contentLibClient = new ClsApiClient(
        		vapiAuthHelper.getStubFactory(), sessionStubConfig);
        this.resourcePoolID = ResourcePoolHelper.getResourcePool(
        		vapiAuthHelper.getStubFactory(),
        		sessionStubConfig, resourcePoolName);
        System.out.println("this.resourcePool: "+this.resourcePoolID);
        //Generate a default VM name if it is not provided
        if (StringUtils.isBlank(this.vmName)) {
            this.vmName = "deployed-vm-opaque-Nw-"+ UUID.randomUUID();
        }
        this.ethernetService = vapiAuthHelper.getStubFactory().createStub(
                Ethernet.class, this.sessionStubConfig);
        if(clearData)
        	this.vmService = vapiAuthHelper.getStubFactory().createStub(VM.class,
                    sessionStubConfig);
        if(null == this.folderName)
        	this.folderName = "Workloads";
        this.folderID = FolderHelper.getFolder(vapiAuthHelper.getStubFactory(),
        		sessionStubConfig, this.folderName);
        
    }

    protected void nonsetup() throws Exception {
        // Get vCenter hostname, username and password
        this.vmcAuthHelper = new VmcAuthenticationHelper();
        this.vmcClient = this.vmcAuthHelper.newVmcClient(this.vmcServer, this.cspServer, this.refreshToken);
        this.sddcsStub = vmcClient.createStub(Sddcs.class);
        this.vmStub = vmcClient.createStub(VM.class);

        Sddc sddc = sddcsStub.get(orgId, sddcId);
        URL vcServerUrl = new URL(sddc.getResourceConfig().getVcUrl());
        String vcServer = vcServerUrl.getHost();
        String vcUsername = sddc.getResourceConfig().getCloudUsername();
        String vcPassword = sddc.getResourceConfig().getCloudPassword();

        this.authorizationStubUtil = new VcenterAuthorizationStubUtil();
        this.sessionStubConfig = authorizationStubUtil.
                loginUsingClientCredentialsGrantType(vcServer, "clientId", "clientSecret", orgId, true);

        this.vapiAuthHelper = new VapiAuthenticationHelper();
        this.vapiAuthHelper.createStubFactory(vcServer, true);

        this.contentLibClient = new ClsApiClient(
                vapiAuthHelper.getStubFactory(), sessionStubConfig);
        this.resourcePoolID = ResourcePoolHelper.getResourcePool(
                vapiAuthHelper.getStubFactory(),
                sessionStubConfig, resourcePoolName);
        System.out.println("this.resourcePool: "+this.resourcePoolID);
        //Generate a default VM name if it is not provided
        if (StringUtils.isBlank(this.vmName)) {
            this.vmName = "deployed-vm-opaque-Nw-"+ UUID.randomUUID();
        }
        this.ethernetService = vapiAuthHelper.getStubFactory().createStub(
                Ethernet.class, this.sessionStubConfig);
        if(clearData)
            this.vmService = vapiAuthHelper.getStubFactory().createStub(VM.class,
                    sessionStubConfig);
        if(null == this.folderName)
            this.folderName = "Workloads";
        this.folderID = FolderHelper.getFolder(vapiAuthHelper.getStubFactory(),
                sessionStubConfig, this.folderName);

    }

    protected void run() throws Exception {
        FindSpec findSpec = new FindSpec();
        findSpec.setName(this.libItemName);
        List<String> itemIds = this.contentLibClient.itemService().find(findSpec);
        assert !itemIds.isEmpty() : "Unable to find a library item with name: "
                                    + this.libItemName;
        String libItemId = itemIds.get(0);
        System.out.println("Library item ID : " + libItemId);

        // Deploy a VM from the library item on the given cluster
        System.out.println("Deploying Vm : " + this.vmName);
        deployVMFromOvfItem(libItemId);
        assert this.vmId != null;
        System.out.println("Vm created : " + this.vmId);
        //Add an opaque network portgroup to the deployed VM
        addOpaqueNetworkPortGroup();
    }

    protected void cleanup() throws Exception {
    	if(null != this.vmId) {
    		System.out.println("\n\n#### Deleting the Deployed VM");
    		this.vmService.delete(this.vmId);
    		System.out.println("\n\n#### Deleted the Deployed VM :" + this.vmName);
    	}
    }

    /**
     * Deploying a VM from the Content Library into a cluster.
     *
     * @param libItemId identifier of the OVF library item to deploy
     * @return 
     */
    private void deployVMFromOvfItem(String libItemId) {
        // Creating the deployment.
        DeploymentTarget deploymentTarget = new DeploymentTarget();
        //Setting the target resource pool.
        deploymentTarget.setResourcePoolId(this.resourcePoolID);
        //Setting the target Folder.
        deploymentTarget.setFolderId(this.folderID);
        // Creating and setting the resource pool deployment spec.
        ResourcePoolDeploymentSpec deploymentSpec = 
        		new ResourcePoolDeploymentSpec();
        deploymentSpec.setName(this.vmName);
        deploymentSpec.setAcceptAllEULA(true);

        deploymentSpec.setAdditionalParameters(getExtraConfigProperties());
        //deploymentSpec.setAdditionalParameters(getOvfProperties());
        // Retrieve the library items OVF information and use it for populating
        // deployment spec.
        OvfSummary ovfSummary = this.contentLibClient.ovfLibraryItemService()
            .filter(libItemId, deploymentTarget);
        // Setting the annotation retrieved from the OVF summary.
        deploymentSpec.setAnnotation(ovfSummary.getAnnotation());
        // Calling the deploy and getting the deployment result.
        DeploymentResult deploymentResult = 
        		this.contentLibClient.ovfLibraryItemService()
        		.deploy(UUID.randomUUID().toString(),
                libItemId,
                deploymentTarget,
                deploymentSpec);
        if (deploymentResult.getSucceeded())
            this.vmId =  deploymentResult.getResourceId().getId();            
        else
            throw new RuntimeException(deploymentResult.getError().toString());
    }

    private List<Structure> getOvfProperties() {
        List<Structure> propList = new ArrayList<>();
        StringValue proxyShared = new StringValue("lfADEuxqVN+JlG+b/wOW5BjiBUJur5avxKjk4GLUeOlcZWSX/NOxZOlavOTPd5rReu+qpV1AfKUMJ0tOKQlg7zqKwzz4K1GUes6ev3QgMLbIaUxGqPXDL9XDb/4DCNGofRpiwMZrw8PWln2Kc4NE7WwKQRWuTupr5QlhO9zdUpVPg2A0J+7paTZRkA3ZQNGz3W+6s1PXiK3gRBXBCE00ztL2WcFJvemrqc9i/zcVf9Pxs52J4RRXSa8/Ibtf2uo/Dg3M9t/YmwTZs+22yeFpCpm/qpocA72q8uqMTxrkNP1QVjkPlMr0KSHcBSr4QSgNU1o0yZ6USUNLBx/e0U+k5UQ==");
        propList.add(createOvfParam("Proxy_Shared_Secret", proxyShared));
        propList.add(createOvfParam("App_Init", new StringValue(getCommonAppInitProperties())));
//        propList.add(createOvfParam("DNS", new StringValue("8.8.8.8 8.8.4.4")));
//        propList.add(createOvfParam("Default_Gateway", new StringValue("10.72.126.1")));
//        propList.add(createOvfParam("IP_Address", new StringValue("10.72.126.190")));
//        propList.add(createOvfParam("Domain_Search", new StringValue("vmware.com")));
//        propList.add(createOvfParam("NTP", new StringValue("pool.ntp.org")));
//        propList.add(createOvfParam("SSH_User_Password", new StringValue("InfraSof")));
//        propList.add(createOvfParam("CLI_User_Password", new StringValue("InfraSof")));
//        propList.add(createOvfParam("Netmask", new StringValue("255.255.255.0")));

        return propList;
    }

    private String getCommonAppInitProperties() {
        StringBuilder appInitProps = new StringBuilder("VRNICUSTOMPROP");
        Map<String, String> properties = new HashMap();

        properties.put("IP_Address", "10.72.126.191");
        properties.put("Netmask", "255.255.255.0");
        properties.put("Default_Gateway", "10.72.126.1");
        properties.put("DNS","8.8.8.8 8.8.4.4");
        properties.put("Domain_Search", "vmware.com");
        properties.put("NTP", "ntp3-eat1.vmware.com");
        properties.put("SSH_User_Password", "InfraSof");
        properties.put("CLI_User_Password", "consoleuser");
        properties.put("Auto-Configure", "True");

        properties.entrySet().stream().forEach(e -> {
            if (appInitProps.length() > 0) {
                appInitProps.append(':');
            }
            appInitProps.append(String.format("%s=%s",e.getKey(), e.getValue()));
        });
        appInitProps.append(':');
        appInitProps.append("VRNICUSTOMPROP");
        return appInitProps.toString();
    }

    private OvfParams createOvfParam(String fieldName, DataValue dataValue) {
        OvfParams.Builder paramBuilder = new OvfParams.Builder();
        paramBuilder.setType("string");
        OvfParams param = paramBuilder.build();
        param._setDynamicField(fieldName, dataValue);
        return param;
    }

    private List<Structure> getExtraConfigProperties() {
        List<Structure> propList = new ArrayList<>();
        propList.add(createSharedSecretKey());
        propList.add(createExtraParams());
        return propList;
    }

    private ExtraConfigParams createExtraParams(){
        ExtraConfigParams param = new ExtraConfigParams();
        param.setType("string");
        List<ExtraConfig> extraConfigs = new ArrayList<>();
        extraConfigs.add(createExtraConfig("Proxy_Shared_Secret", proxySharedSecretVal));
        extraConfigs.add(createExtraConfig("IP_Address", "10.72.126.191"));
        extraConfigs.add(createExtraConfig("Netmask", "255.255.255.0"));
        extraConfigs.add(createExtraConfig("Default_Gateway", "10.72.126.1"));
        extraConfigs.add(createExtraConfig("DNS","8.8.8.8 8.8.4.4"));
        extraConfigs.add(createExtraConfig("Domain_Search", "vmware.com"));
        extraConfigs.add(createExtraConfig("NTP", "ntp3-eat1.vmware.com"));
        extraConfigs.add(createExtraConfig("SSH_User_Password", "InfraSof"));
        extraConfigs.add(createExtraConfig("CLI_User_Password", "consoleuser"));
        extraConfigs.add(createExtraConfig("Auto-Configure", "True"));
        extraConfigs.add(createExtraConfig("App_Init", getCommonAppInitProperties()));
        param.setExtraConfigs(extraConfigs);
        param._setDynamicField("App_Init",new StringValue(getCommonAppInitProperties()));
        return param;
    }

    private ExtraConfigParams createSharedSecretKey(){
        ExtraConfigParams param = new ExtraConfigParams();
        param.setType("string");
        List<ExtraConfig> extraConfigs = new ArrayList<>();
        extraConfigs.add(createExtraConfig("Proxy_Shared_Secret", proxySharedSecretVal));
        param.setExtraConfigs(extraConfigs);
        param._setDynamicField("Proxy_Shared_Secret",new StringValue(proxySharedSecretVal));
        return param;
    }

    private ExtraConfig createExtraConfig(String fieldName, String value) {
        ExtraConfig cfg = new ExtraConfig();
        cfg.setKey(fieldName);
        cfg.setValue(value);
        return cfg;
    }
    
    /**
     * Adds Opaque Network backing to the newly deployed VM.
     *     
     * @return 
     */
    private void addOpaqueNetworkPortGroup()
    {
        if (null != this.opaqueNetwrokName){        	
            String opaqueNetworkBacking = NetworkHelper.getOpaqueNetworkBacking(
                    vapiAuthHelper.getStubFactory(),
                    sessionStubConfig,
                    this.opaqueNetwrokName);
            //Create a nic with Opaque network backing
            EthernetTypes.BackingSpec nicBackingSpec =
                    new EthernetTypes.BackingSpec.Builder(
                        BackingType.OPAQUE_NETWORK).setNetwork(
                        		opaqueNetworkBacking).build();
            EthernetTypes.CreateSpec nicCreateSpec =
                    new EthernetTypes.CreateSpec.Builder().setStartConnected(true)
                        .setBacking(nicBackingSpec)
                        .build();
            String nicId = this.ethernetService.create(this.vmId, nicCreateSpec);
            System.out.println(nicCreateSpec);
            EthernetTypes.Info nicInfo = this.ethernetService.get(this.vmId, nicId);
            System.out.println("Ethernet NIC ID=" + nicId);
            System.out.println(nicInfo);
        }
    }
    public static void main(String[] args) throws Exception {
        /*
         * Execute the sample using the command line arguments or parameters
         * from the configuration file. This executes the following steps:
         * 1. Parse the arguments required by the sample
         * 2. Login to the server
         * 3. Setup any resources required by the sample run
         * 4. Run the sample
         * 5. Cleanup any data created by the sample run, if cleanup=true
         * 6. Logout of the server
         */

        args = new String[]{
                "--refreshtoken", "6msCQY6s3exPdC2aPPLZMEKmvHMWSf0SZnfa9xjfte8qYtMdPb07IMuOt1SC8KMN",
                "--org_id", "c5a81416-dc30-4a2e-83d2-3348a036de85",
                "--sddc_id", "acdbb792-b751-44c6-955d-56540cae8796",
                "--vmcserver", "vmc.vmware.com",
                "--cspserver", "console.cloud.vmware.com",
                "--vmname", "vrni-collector-asdk1",
                "--libitemname", "VMWare-Network-Insight-Collector",
                "--resourcepoolname", "Compute-ResourcePool",
                "--opaquenetworkname", "CMBU-SDDC-VM"
        };

//        args = new String[]{
//                "--refreshtoken", "6msCQY6s3exPdC2aPPLZMEKmvHMWSf0SZnfa9xjfte8qYtMdPb07IMuOt1SC8KMN",
//                "--org_id", "c5a81416-dc30-4a2e-83d2-3348a036de85",
//                "--sddc_id", "acdbb792-b751-44c6-955d-56540cae8796",
//                "--vmcserver", "vmc.vmware.com",
//                "--cspserver", "console.cloud.vmware.com",
//                "--vmname", "gg-photon-test1",
//                "--libitemname", "photon-os",
//                "--resourcepoolname", "Compute-ResourcePool",
//                "--opaquenetworkname", "CMBU-SDDC-VM"
//        };

//        args = new String[]{
//                "--refreshtoken", "h9IXxXLsWLMWIHGBtLyCnHplD7ShplpVXZpgzOYPD7LelSIg89ngkGA1bO9LyP28",
//                "--org_id", "6b87a4ff-e751-4a34-baa8-8d4e20926525",
//                "--sddc_id", "4aaec0e1-8842-4f09-812f-6cb86ac75a40",
//                "--vmcserver", "vmc.vmware.com",
//                "--cspserver", "console.cloud.vmware.com",
//                "--vmname", "vrni-collector",
//                "--libitemname", "VMWare-Network-Insight-Collector",
//                "--resourcepoolname", "Compute-ResourcePool",
//                "--opaquenetworkname", "vrni-collector-segment"
//        };
        new DeployOvfTemplateWithVmcCreds().execute(args);
    }
}
