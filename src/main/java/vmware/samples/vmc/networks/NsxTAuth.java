/*
 * *******************************************************
 * Copyright VMware, Inc. 2018.  All Rights Reserved.
 * SPDX-License-Identifier: MIT
 * *******************************************************
 *
 * DISCLAIMER. THIS PROGRAM IS PROVIDED TO YOU "AS IS" WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, WHETHER ORAL OR WRITTEN,
 * EXPRESS OR IMPLIED. THE AUTHOR SPECIFICALLY DISCLAIMS ANY IMPLIED
 * WARRANTIES OR CONDITIONS OF MERCHANTABILITY, SATISFACTORY QUALITY,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package vmware.samples.vmc.networks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.nsx_policy.infra.domains.GatewayPolicies;
import com.vmware.nsx_policy.infra.domains.Groups;
import com.vmware.nsx_policy.infra.tier_1s.Segments;
import com.vmware.nsx_policy.infra.tier_1s.nat.NatRules;
import com.vmware.nsx_policy.model.GatewayPolicy;
import com.vmware.nsx_policy.model.Group;
import com.vmware.nsx_policy.model.PolicyNatRule;
import com.vmware.nsx_policy.model.Rule;
import com.vmware.nsx_policy.model.Segment;
import com.vmware.nsx_policy.model.SegmentSubnet;
import com.vmware.nsx_vmc.client.VmcNsxClients;
import com.vmware.nsx_vmc_app.infra.PublicIps;
import com.vmware.nsx_vmc_app.model.PublicIp;

import com.vmware.vapi.client.ApiClient;

/*-
 * This example shows how to authenticate to the VMC (VMware Cloud on AWS)
 * service, using a VMC refresh token to obtain an authentication token that
 * can then be used to authenticate to the NSX-T instance in a Software
 * Defined Data Center (SDDC). It also shows how to list several types
 * of entities.
 */
public class NsxTAuth {

    public String proxySharedSecret = "jWFEVgfjcCpFe8ZuNKSi+hJvnm8Jd0zAYPED30pl2wdfjG4uFybgSU19UTX2UtnlQp/RTpcPNt7vcDJHf260XcWElxdiWJwAZbQPrgQCuRmDvVeWkkNQ3lx1iW27IfyCT5SqU45we5IUkZ7ALIg6rXp2kZDbB8iITq3JZx56xxhvSGZj8QCOw4NK+JtIf9mtoJyRe7ecH8Dkn9BVW22DxWo8BUEMnCjm2shUs38kNaq8xwlhZnodCIdYWaIrxOkqCTGX1sFLni1c4IfBXg/XOr/aEYq7dTZN5c/3OyyDoBjCWdpPkaCCmogLWEv2nPyo/1xt/1eT0N2b4ndkt4gOQg==";
    public String ovaUrl = "https://s3-us-west-2.amazonaws.com/vrni-packages-archive-symphony-stag/latest/VMWare-Network-Insight-Collector.ova";
    public String collectorIp = "10.73.127.13";
    public String segmentNetwork = "10.73.127.0/24";
    public String segmentGateway = "10.73.127.1/24";
    public String vcIP = "10.73.239.196";
    public String ntp = "ntp3-eat1.vmware.com";
    public String ovftoolLoc = "";
    public String vcURL = "vcenter.sddc-35-155-98-158.vmwarevmc.com";

    public static final String VC_NAT_RULE = "vc_public_private_ip_match_nat_rule";
    public static final String NAT_ID = "USER";
    public static final String ACTION_DNAT = "DNAT";
    public static final String NAT_FW_MATCH = "MATCH_INTERNAL_ADDRESS";
    public static final String PORTS_HTTPS = "443";
    public static final String SEGMENT_REPLICATION_TYPE_MTEP = "MTEP";
    public static final String ROUTED = "ROUTED";
    public static final String VAPP_PROXY_NAME = "vrni_proxy_ensemble";
    public static final String DEFAULT_VM_FOLDER = "Workloads";
    public static final String DEFAULT_DATASTORE = "WorkloadDatastore";

    public static final String DEFAULT_POLICY = "default";
    public static final String GATEWAY_POLICY_RULE_TYPE = "Rule";

    public static final String DESTIBATION_GROUP_VC = "/infra/domains/mgw/groups/VCENTER"; // will these be available or to be verified
    public static final String DESTIBATION_GROUP_NSX = "/infra/domains/mgw/groups/NSX-MANAGER";// will these be available or to be verified

    public static final String CGW_GROUPS_PATH = "/infra/domains/cgw/groups/"; // domain name/id fixed ?
    public static final String MGW_GROUPS_PATH = "/infra/domains/mgw/groups/"; // domain name/id fixed ?

    public static final String CGW_RULE_SCOPE = "/infra/labels/cgw-all"; // scopes fixed ?
    public static final String MGW_RULE_SCOPE = "/infra/labels/mgw";

    public static final String SERVICE_TYPE_ICMP = "/infra/services/ICMP-ALL";
    public static final String SERVICE_TYPE_HTTPS = "/infra/services/HTTPS";
    public static final String SERVICE_TYPE_DNS = "/infra/services/DNS";
    public static final String SERVICE_TYPE_DNS_UDP = "/infra/services/DNS-UDP";
    public static final String SERVICE_TYPE_NTP = "/infra/services/NTP";

    public static final String CGW_RULE_NAME = "vrni-services-allow";
    public static final String MGW_VC_RULE_NAME = "vrni-proxy-vc";
    public static final String MGW_NSX_RULE_NAME = "vrni-proxy-nsx";

    public static final String VC_IP = "vc-ip";

    public static String SEGMENT_NAME = "vrni-proxy-segment";
    public static String GROUP_ID = "vrni-proxy-group";

    public static String CGW_ID = "cgw";//fixed ??
    public static String MGW_ID = "mgw";

    private GatewayPolicies gatewayPolicies;

    private Groups groupsApiClient;
    private Segments segmentsApiClient;
    private PublicIps publicIpsClient;
    private NatRules natRulesClient;

    public NsxTAuth(String orgId, String sddcId, String refreshToken, boolean verifyServerCertificate, boolean verifyServerHostname) {
        ApiClient apiClient = VmcNsxClients.custom()
                .setRefreshToken(refreshToken.toCharArray())
                .setOrganizationId(orgId).setSddcId(sddcId)
                .setVerifyServerCertificate(verifyServerCertificate)
                .setVerifyServerHostname(verifyServerHostname).build();

        this.segmentsApiClient = apiClient.createStub(Segments.class);
        this.groupsApiClient = apiClient.createStub(Groups.class);
        this.gatewayPolicies = apiClient.createStub(GatewayPolicies.class);
        this.publicIpsClient = apiClient.createStub(PublicIps.class);
        this.natRulesClient = apiClient.createStub(NatRules.class);
    }


    public void performPreDeploymentProcesses() {


        PublicIp publicIp = getOrCreatePublicIp();

        //User not authorized

        // unable to retrieve nat rules with these vmc creds : 6msCQY6s3exPdC2aPPLZMEKmvHMWSf0SZnfa9xjfte8qYtMdPb07IMuOt1SC8KMN
        PolicyNatRule natRule = getOrCreateNATRule(publicIp.getIp());

        //this.publicIpsClient.delete(HOST_IP, true);
        // unable to create segments with these vmc creds : 6msCQY6s3exPdC2aPPLZMEKmvHMWSf0SZnfa9xjfte8qYtMdPb07IMuOt1SC8KMN
        Segment segment = getOrCreateSegment();

        if (null == segment) {
            System.out.println("Exiting no segment with name " + SEGMENT_NAME + " found in Tier 1 : +" + CGW_ID);
            return;
        }
        Group cgw_group = getOrCreateGroup(CGW_ID);
        Group mgw_group = getOrCreateGroup(MGW_ID);

        if (null == cgw_group || null == mgw_group) {
            System.out.println("Exiting could not find or create group : " + GROUP_ID);
            return;
        }
        GatewayPolicy cgw_default_policy = getOrCreateCGWPolicy(cgw_group);
        GatewayPolicy mgw_default_policy = getOrCreateMGWPolicies(mgw_group);


        // Create OVF tool command
        String deployOVFCommand = getDeployCmd(publicIp, segment);

        System.out.println("Command generated\n\n");
        System.out.println(deployOVFCommand);
        // Deplot OVF
        System.out.println("\n\nDeploying ova");
        ProcessBuilder procBuilder = new ProcessBuilder();
        procBuilder.command("bash", "-c", deployOVFCommand);
        try {
            Process process = procBuilder.start();

            // blocked :(
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            System.out.println("\nExited with error code : " + exitCode);
            if (exitCode == 0) {
                System.out.println("Success!");

                System.exit(0);
            } else {
                System.out.println("Failed!");
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Done");
    }

    private String getDeployCmd(PublicIp publicIp, Segment segment) {
        StringBuilder deployOVFCommand = new StringBuilder();
        deployOVFCommand.append(ovftoolLoc)
                .append("ovftool")
                .append(" --machineOutput")
                .append(" --X:logLevel=verbose")
                .append(" --X:enableHiddenProperties")
                .append(" -dm=thin")
                .append(" --acceptAllEulas")
                .append(" --allowExtraConfig")
                .append(" --noSSLVerify")
                .append(" --name=").append(VAPP_PROXY_NAME)
                .append(" --datastore=").append(DEFAULT_DATASTORE)
                .append(" --network=").append(segment.getId()) // adding the created segment id
                .append(" --viCpuResource=:").append("1251:1251")
                .append(" --viMemoryResource=:").append("16384:16384")
                .append(" --numberOfCpus:").append(VAPP_PROXY_NAME).append("=4")
                .append(" --memorySize:").append(VAPP_PROXY_NAME).append("=16384")
                .append(" --overwrite")
                .append(" --powerOffTarget")
                .append(" --powerOn")
                .append(" --prop:DNS=").append("10.148.20.5,10.148.20.6")
                .append(" --prop:Default_Gateway=").append(segmentGateway.split("\"")[0])
                .append(" --prop:IP_Address=").append(collectorIp)
                .append(" --prop:Domain_Search=").append("vmware.com")
                .append(" --prop:NTP=").append(ntp)
                .append(" --prop:SSH_User_Password=").append("ark1nc0113ct0r")
                .append(" --prop:CLI_User_Password=").append("ark1nc0ns0l3")
                .append(" --vmFolder=").append(DEFAULT_VM_FOLDER)
                .append(" --prop:Netmask=").append("255.255.255.0")
                .append(" --prop:Proxy_Shared_Secret=").append(proxySharedSecret)
                .append(" --prop:Auto-Configure=").append("True")
                .append(" ").append(ovaUrl)/////////////////////////////////////////"Ek+E06yE-dpkHQH".append("u!JLSyv*7DKlI2a")$!# %24 %21 %23
                .append(" 'vi://").append("cloudadmin@vmc.local").append(":").append("u!JLSyv*7DKlI2a").append("@")
                .append(vcURL).append("/SDDC-Datacenter/host/Cluster-1/Resources/Compute-ResourcePool").append("'");
        return deployOVFCommand.toString();
    }

    private PolicyNatRule getOrCreateNATRule(String publicIp) {
        //PolicyNatRuleListResult natResult = this.natRulesClient.list(CGW_ID, NAT_ID, null, false, null, 1000L, false, null);
        PolicyNatRule publicIpRule = null;
        try {
            publicIpRule = this.natRulesClient.get(CGW_ID, NAT_ID, VC_NAT_RULE);
            System.out.println("Found NAT Rule : " + VC_NAT_RULE);
        } catch (Exception e) {
            System.out.println("Creating NAT rule :" + VC_NAT_RULE + " for public ip : " + publicIp + " private ip : " + vcIP);
            publicIpRule = new PolicyNatRule();
            publicIpRule.setAction(ACTION_DNAT);
            publicIpRule.setDestinationNetwork(publicIp);
            publicIpRule.setTranslatedNetwork(vcIP);
            publicIpRule.setService(SERVICE_TYPE_HTTPS);
            publicIpRule.setTranslatedPorts(PORTS_HTTPS);
            publicIpRule.setFirewallMatch(NAT_FW_MATCH);
            publicIpRule.setDisplayName(VC_NAT_RULE);
            publicIpRule.setSequenceNumber(100L);
            publicIpRule.setId(VC_NAT_RULE);
            PolicyNatRule newPublicIpRule = this.natRulesClient.update(CGW_ID, NAT_ID, VC_NAT_RULE, publicIpRule);
        }

        return publicIpRule;
        //this.natRulesClient.delete(CGW_ID,"USER","host_public_private_ip_match_nat_rule");
    }

    private PublicIp getOrCreatePublicIp() {
        PublicIp publicIp;
        try {
            publicIp = this.publicIpsClient.get(VC_IP);
            System.out.println("Found public ip : " + VC_IP);
        } catch (Exception e) {
            System.out.println("Creating public ip : " + VC_IP);
            publicIp = new PublicIp();
            publicIp.setId(VC_IP);
            publicIp.setDisplayName(VC_IP);
            //publicIp.setIp("54.191.24.4");
            publicIp = this.publicIpsClient.update(VC_IP, publicIp);
            System.out.println("Created public ip : " + publicIp.getIp());
        }
        return publicIp;
    }

    private GatewayPolicy getOrCreateMGWPolicies(Group mgw_group) {
        GatewayPolicy mgw_default_policy = null;
        try {
            mgw_default_policy = this.gatewayPolicies.get(MGW_ID, DEFAULT_POLICY);
        } catch (Exception e) {
            System.out.println("Could not find default policy for cgw, error :" + e.getMessage());
        }
        List<Rule> rules = mgw_default_policy.getRules();
        if (null == rules) {
            rules = new ArrayList<>();
        }
        // Adding MGW VC rule
        {
            Rule mgw_vc_rule = findRule(rules, MGW_VC_RULE_NAME);
            if (null == mgw_vc_rule) {
                System.out.println("Creating MGW rule : " + MGW_VC_RULE_NAME);
                mgw_vc_rule = getMGWRule(mgw_group, getNextSequenceNumber(rules), MGW_VC_RULE_NAME, DESTIBATION_GROUP_VC);
                rules.add(mgw_vc_rule);
            }
        }
        {
            Rule mgw_nsx_rule = findRule(rules, MGW_NSX_RULE_NAME);
            if (null == mgw_nsx_rule) {
                System.out.println("Creating MGW rule : " + MGW_NSX_RULE_NAME);
                mgw_nsx_rule = getMGWRule(mgw_group, getNextSequenceNumber(rules), MGW_NSX_RULE_NAME, DESTIBATION_GROUP_NSX);
                rules.add(mgw_nsx_rule);
            }
        }
        mgw_default_policy.setRules(rules);
        try {
            this.gatewayPolicies.patch(MGW_ID, DEFAULT_POLICY, mgw_default_policy);
        } catch (Exception e) {
            System.out.println("unable to create MGW policy");
            throw e;
        }
        return mgw_default_policy;
    }

    private GatewayPolicy getOrCreateCGWPolicy(Group cgw_group) {
        GatewayPolicy cgw_default_policy = null;
        try {
            cgw_default_policy = this.gatewayPolicies.get(CGW_ID, DEFAULT_POLICY);
        } catch (Exception e) {
            System.out.println("Could not find default policy for cgw, error :" + e.getMessage());
        }
        List<Rule> rules = cgw_default_policy.getRules();
        if (null == rules) {
            rules = new ArrayList<>();
        }
        // Adding CGW rule
        {
            String CGW_RULE_NAME = "vrni-services-allow";
            Rule cgw_rule = findRule(rules, CGW_RULE_NAME);
            if (null == cgw_rule) {
                System.out.println("Creating CGW rule");
                cgw_rule = getCGWRule(cgw_group, getNextSequenceNumber(rules));
                rules.add(cgw_rule);
            }
        }
        cgw_default_policy.setRules(rules);
        try {
            this.gatewayPolicies.patch(CGW_ID, DEFAULT_POLICY, cgw_default_policy);
        } catch (Exception e) {
            System.out.println("unable to create CGW policy");
            throw e;
        }
        return cgw_default_policy;
    }

    private Rule getCGWRule(Group cgw_group, Long sequenceNumber) {
        Rule cgw_rule;
        cgw_rule = new Rule();
        cgw_rule.setDisplayName(CGW_RULE_NAME);
        cgw_rule.setLogged(false);
        cgw_rule.setSequenceNumber(sequenceNumber);
        cgw_rule.setAction(Rule.ACTION_ALLOW);
        List<String> sourceGroups = new LinkedList<>();
        sourceGroups.add(CGW_GROUPS_PATH + cgw_group.getId());
        cgw_rule.setSourceGroups(sourceGroups);
        cgw_rule.setResourceType(GATEWAY_POLICY_RULE_TYPE);
        cgw_rule.setScope(Arrays.asList(CGW_RULE_SCOPE));
        cgw_rule.setDestinationGroups(Arrays.asList("ANY"));
        List<String> services = new LinkedList<>();
        services.add(SERVICE_TYPE_ICMP);
        services.add(SERVICE_TYPE_DNS);
        services.add(SERVICE_TYPE_DNS_UDP);
        services.add(SERVICE_TYPE_NTP);
        services.add(SERVICE_TYPE_HTTPS);
        cgw_rule.setServices(services);
        return cgw_rule;
    }

    private Rule getMGWRule(Group mgw_group, Long sequenceNumber, String ruleName, String dest) {
        Rule cgw_rule = new Rule();
        cgw_rule.setDisplayName(ruleName);
        cgw_rule.setLogged(false);
        cgw_rule.setSequenceNumber(sequenceNumber);
        cgw_rule.setAction(Rule.ACTION_ALLOW);
        List<String> sourceGroups = new LinkedList<>();
        sourceGroups.add(MGW_GROUPS_PATH + mgw_group.getId());
        cgw_rule.setSourceGroups(sourceGroups);
        cgw_rule.setResourceType(GATEWAY_POLICY_RULE_TYPE);
        cgw_rule.setScope(Arrays.asList(MGW_RULE_SCOPE));
        cgw_rule.setDestinationGroups(Arrays.asList(dest));
        List<String> services = new LinkedList<>();
        services.add(SERVICE_TYPE_HTTPS);
        cgw_rule.setServices(services);
        return cgw_rule;
    }

    private Segment getOrCreateSegment() {
        Segment segment = null;
        try {
            segment = this.segmentsApiClient.get(CGW_ID, SEGMENT_NAME);
            System.out.println("Found Subnet : " + SEGMENT_NAME);
        } catch (Exception e) {
            segment = new Segment();
            segment.setReplicationMode(SEGMENT_REPLICATION_TYPE_MTEP);
            segment.setType(ROUTED);
            segment.setSubnets(Arrays.asList(getSubnet(segmentNetwork, segmentGateway)));
            segment = this.segmentsApiClient.update(CGW_ID, SEGMENT_NAME, segment);
            System.out.println("Created Subnet : " + SEGMENT_NAME);
        }
        return segment;
    }

    private SegmentSubnet getSubnet(String network, String gatewayAddress) {
        SegmentSubnet subnet = new SegmentSubnet();
        subnet.setNetwork(network);
        subnet.setGatewayAddress(gatewayAddress);
        return subnet;
    }

    private Group getOrCreateGroup(String domainId) {
        Group grp = null;
        try {
            grp = this.groupsApiClient.get(domainId, GROUP_ID);
            System.out.println("found group : " + GROUP_ID);
        } catch (Exception e) {
            Group.Builder grpBuilder = new Group.Builder();
            grpBuilder.setId(GROUP_ID);
            grpBuilder.setDisplayName(GROUP_ID);
            grp = grpBuilder.build();
            this.groupsApiClient.patch(domainId, GROUP_ID, grp);
            System.out.println("Created Group : " + GROUP_ID);
        }
        return grp;
    }
    // create public ip -> nat rule ->

    private static Long getNextSequenceNumber(List<Rule> rules) {
        Set<Long> sequenceNumbers = rules.stream().map(Rule::getSequenceNumber).collect(Collectors.toSet());
        for (int sequence = 0; sequence < Integer.MAX_VALUE; sequence++) {
            final int candidate = sequence;
            boolean noneMatch = sequenceNumbers.stream().noneMatch(s -> s == candidate);
            if (noneMatch) {
                return (long) sequence;
            }
        }
        return -1L;
    }

    private static Rule findRule(List<Rule> rules, String nameOrId) {
        Rule found = null;
        for (Rule rule : rules) {
            if (nameOrId.equalsIgnoreCase(rule.getId()) || nameOrId.equalsIgnoreCase(rule.getDisplayName())) {
                System.out.println("Found rule : " + nameOrId);
                found = rule;
                break;
            }
        }
        return found;
    }


    public static void main(String[] args) throws Exception {
        final String orgId = "c5a81416-dc30-4a2e-83d2-3348a036de85";
        final String sddcId = "acdbb792-b751-44c6-955d-56540cae8796";
        final String refreshToken = "6msCQY6s3exPdC2aPPLZMEKmvHMWSf0SZnfa9xjfte8qYtMdPb07IMuOt1SC8KMN";

        NsxTAuth ovaDep = new NsxTAuth(orgId, sddcId, refreshToken, false, false);
        handleArgs(args, ovaDep);
        ovaDep.performPreDeploymentProcesses();
    }

    private static void handleArgs(String[] args, NsxTAuth ovaDep) {
        int argLen = args.length - 1;
        int i;
        //proxySharedSecret
        i = 0;
        if (i <= argLen && null != args[i] && !"".equalsIgnoreCase(args[i])) {
            ovaDep.proxySharedSecret = args[i];
        }

        //ovaUrl
        i = 1;
        if (i <= argLen && null != args[i] && !"".equalsIgnoreCase(args[i])) {
            ovaDep.ovaUrl = args[i];
        }

        // collectorIp
        i = 2;
        if (i <= argLen && null != args[i] && !"".equalsIgnoreCase(args[i])) {
            ovaDep.collectorIp = args[i];
            String split[] = args[i].split("\\.");
            split[3] = "0/24";
            ovaDep.segmentNetwork = String.join(".", split);
            split[3] = "1/24";
            ovaDep.segmentGateway = String.join(".", split);
        }

        //vcURL
        i = 3;
        if (i <= argLen && null != args[i] && !"".equalsIgnoreCase(args[i])) {
            ovaDep.vcURL = args[i];
        }

        //ovftool location
        i = 4;
        if (i <= argLen && null != args[i] && !"".equalsIgnoreCase(args[i])) {
            ovaDep.ovftoolLoc = args[i];
        }

        //ntp
        i = 5;
        if (i <= argLen && null != args[i] && !"".equalsIgnoreCase(args[i])) {
            ovaDep.ntp = args[i];
        }
    }
}
