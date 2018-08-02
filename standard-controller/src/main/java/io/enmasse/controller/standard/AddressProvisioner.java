/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.controller.standard;

import static io.enmasse.controller.standard.ControllerKind.Broker;
import static io.enmasse.controller.standard.ControllerReason.BrokerCreateFailed;
import static io.enmasse.controller.standard.ControllerReason.BrokerCreated;
import static io.enmasse.k8s.api.EventLogger.Type.Normal;
import static io.enmasse.k8s.api.EventLogger.Type.Warning;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.enmasse.address.model.Address;
import io.enmasse.address.model.AddressPlan;
import io.enmasse.address.model.AddressResolver;
import io.enmasse.address.model.AddressSpacePlan;
import io.enmasse.address.model.AddressType;
import io.enmasse.address.model.KubeUtil;
import io.enmasse.address.model.ResourceAllowance;
import io.enmasse.address.model.ResourceDefinition;
import io.enmasse.address.model.ResourceRequest;
import io.enmasse.address.model.Status;
import io.enmasse.config.AnnotationKeys;
import io.enmasse.k8s.api.EventLogger;

public class AddressProvisioner {
    private static final Logger log = LoggerFactory.getLogger(AddressProvisioner.class);
    private final AddressResolver addressResolver;
    private final AddressSpacePlan addressSpacePlan;
    private final BrokerSetGenerator clusterGenerator;
    private final Kubernetes kubernetes;
    private final EventLogger eventLogger;

    public AddressProvisioner(AddressResolver addressResolver, AddressSpacePlan addressSpacePlan, BrokerSetGenerator clusterGenerator, Kubernetes kubernetes, EventLogger eventLogger) {
        this.addressResolver = addressResolver;
        this.addressSpacePlan = addressSpacePlan;
        this.clusterGenerator = clusterGenerator;
        this.kubernetes = kubernetes;
        this.eventLogger = eventLogger;
    }

    /**
     * Computes the resource usage for a set of addresses
     */
    public Map<String, Map<String, UsageInfo>> checkUsage(Set<Address> addressSet) {
        Map<String, Map<String, UsageInfo>> usageMap = new HashMap<>();

        for (Address address : addressSet) {
            addToUsage(usageMap, address);
        }
        return usageMap;
    }

    private void addToUsage(Map<String, Map<String, UsageInfo>> usageMap, Address address) {
        AddressType addressType = addressResolver.getType(address);
        AddressPlan addressPlan = addressResolver.getPlan(addressType, address);

        for (ResourceRequest resourceRequest : addressPlan.getRequiredResources()) {
            String instanceId = null;
            String resourceName = resourceRequest.getResourceName();
            if ("subscription".equals(address.getType())) {
                resourceName = "subscription";
                if (!getBrokerId(address).isPresent()) {
                    log.warn("Unexpected pooled address without broker id: " + address.getAddress());
                    return;
                }
                instanceId = getBrokerId(address).orElseThrow(() -> new IllegalArgumentException("Unexpected pooled address without broker id: " + address.getAddress()));
            } else if (resourceRequest.getResourceName().equals("router")) {
                instanceId = "all";

            //Should we check the plan type, instead of the amount? addressPlan.getName="pooled-topic"
            } else if (resourceRequest.getResourceName().equals("broker") && resourceRequest.getAmount() < 1) {
                if (!getBrokerId(address).isPresent()) {
                    log.warn("Unexpected pooled address without broker id: " + address.getAddress());
                    return;
                }
                instanceId = getBrokerId(address).orElseThrow(() -> new IllegalArgumentException("Unexpected pooled address without broker id: " + address.getAddress()));

            //Should we check the plan type, instead of the amount? addressPlan.getName="sharded-topic"
            } else if (resourceRequest.getResourceName().equals("broker")) {
                instanceId = getShardedClusterId(address);
            }
            Map<String, UsageInfo> resourceUsage = usageMap.computeIfAbsent(resourceName, k -> new HashMap<>());
            UsageInfo info = resourceUsage.computeIfAbsent(instanceId, i -> new UsageInfo());
            info.addUsed(resourceRequest.getAmount());
        }
    }

    public Map<String, Map<String, UsageInfo>> checkQuota(Map<String, Map<String, UsageInfo>> usageMap, Set<Address> pending, Set<Address> all) {
        Map<String, Map<String, UsageInfo>> newUsageMap = usageMap;
        Map<String, Double> limits = computeLimits();

        Set<Address> pendingSubscriptionsWithConfiguredTopics = filterSubscriptionsWithConfiguredTopics(pending, all);
        Set<Address> pendingSubscriptionsWithPendingTopics = filterSubscriptionsWithPendingTopics(pending, all);
        Set<Address> pendingNonSubscriptions = filterByNotType(pending, "subscription");

        newUsageMap = addQuotaForAddress(pendingSubscriptionsWithConfiguredTopics , all, newUsageMap, limits);

        while(!pendingSubscriptionsWithPendingTopics.isEmpty()) {
            Address address = pendingSubscriptionsWithPendingTopics.iterator().next();
            Address topic = findAddress(address.getTopic().get(), all);
            newUsageMap = addQuotaForAddress(new HashSet<Address>(Arrays.asList(topic)), all, newUsageMap, limits);
            pendingNonSubscriptions.remove(topic);

            Set<Address> subscriptionsWithNewlyConfiguredTopics = filterSubscriptionsWithConfiguredTopics(pendingSubscriptionsWithPendingTopics, all);
            newUsageMap = addQuotaForAddress(subscriptionsWithNewlyConfiguredTopics, all, newUsageMap, limits);
            pendingSubscriptionsWithPendingTopics.removeAll(subscriptionsWithNewlyConfiguredTopics);
        }
        newUsageMap = addQuotaForAddress(pendingNonSubscriptions, all, newUsageMap, limits);

        return newUsageMap;
    }

    private Map<String, Map<String, UsageInfo>> addQuotaForAddress(Set<Address> pending, Set<Address> all,
            Map<String, Map<String, UsageInfo>> newUsageMap, Map<String, Double> limits) {
        for (Address address : pending) {
            if (!Status.Phase.Configuring.equals(address.getStatus().getPhase())) {
                Map<String, Map<String, UsageInfo>> neededMap = checkQuotaForAddress(limits, newUsageMap, address, all);
                if (neededMap != null) {
                    newUsageMap = neededMap;
                    address.getStatus().setPhase(Status.Phase.Configuring);
                }
            }
        }
        return newUsageMap;
    }

    private Set<Address> filterSubscriptionsWithConfiguredTopics(Set<Address> addressSet, Set<Address> all) {
        return addressSet.stream()
                .filter(address -> "subscription".equals(address.getType()) && Arrays.asList(Status.Phase.Configuring, Status.Phase.Active).contains(findAddress(address.getTopic().get(), all).getStatus().getPhase()))
                .collect(Collectors.toSet());
    }
    private Set<Address> filterSubscriptionsWithPendingTopics(Set<Address> addressSet, Set<Address> all) {
        return addressSet.stream()
                .filter(address -> "subscription".equals(address.getType()) && Arrays.asList(Status.Phase.Pending).contains(findAddress(address.getTopic().get(), all).getStatus().getPhase()))
                .collect(Collectors.toSet());
    }
    private Set<Address> filterByNotType(Set<Address> addressSet, String type) {
        return addressSet.stream()
                .filter(address -> !type.equals(address.getType()))
                .collect(Collectors.toSet());
    }

    private static Map<String, Map<String, UsageInfo>> copyUsageMap(Map<String, Map<String, UsageInfo>> usageMap) {
        Map<String, Map<String, UsageInfo>> newUsageMap = new HashMap<>();
        for (Map.Entry<String, Map<String, UsageInfo>> entry : usageMap.entrySet()) {
            newUsageMap.put(entry.getKey(), new HashMap<>());
            for (Map.Entry<String, UsageInfo> innerEntry : entry.getValue().entrySet()) {
                newUsageMap.get(entry.getKey()).put(innerEntry.getKey(), new UsageInfo(innerEntry.getValue()));
            }
        }
        return newUsageMap;
    }

    private Address findAddress(String name, Set<Address> addressSet) {
        for (Address address : addressSet) {
            if (name.equals(address.getAddress())) {
                return address;
            }
        }
        return null;
    }

    private boolean scheduleSubscription(Address subscription, Address topic, Map<String, UsageInfo> brokerUsage, Map<String, UsageInfo> subscriptionUsage, ResourceRequest requested) {
        String cluster = topic.getAnnotations().get(AnnotationKeys.CLUSTER_ID);
        String broker = topic.getAnnotations().get(AnnotationKeys.BROKER_ID);

        boolean isPooled = broker !=null;

        subscription.getAnnotations().put(AnnotationKeys.CLUSTER_ID, cluster);

        if (isPooled) {
            UsageInfo usageInfo = subscriptionUsage.computeIfAbsent(broker, k -> new UsageInfo());
            if (usageInfo.getUsed()+requested.getAmount() <= 1) {
                usageInfo.addUsed(requested.getAmount());
                subscription.getAnnotations().put(AnnotationKeys.BROKER_ID, broker);
            } else {
                log.info("no quota available on broker {} for {} on topic {}", cluster, subscription.getAddress(), topic.getAddress());
            }
        } else {
            List<BrokerInfo> shardedBrokers = new ArrayList<>();
            for (String host : brokerUsage.keySet()) {
                if (host.equals(cluster)) {
                    UsageInfo brokerUsageInfo = brokerUsage.get(host);
                    int replicas = brokerUsageInfo.getNeeded();
                    for (String container : subscriptionUsage.keySet()) {
                        shardedBrokers.add(new BrokerInfo(container, subscriptionUsage.get(container).getUsed()));
                    }
                    shardedBrokers.sort(Comparator.comparingDouble(BrokerInfo::getCredit));
                    if (shardedBrokers.size() < replicas) {
                        shardedBrokers.add(0, new BrokerInfo(cluster+"-"+shardedBrokers.size(), 0));
                    }
                }
            }
            for (BrokerInfo brokerInfo : shardedBrokers) {
                UsageInfo usageInfo = subscriptionUsage.computeIfAbsent(brokerInfo.getBrokerId(), k -> new UsageInfo());
                if (brokerInfo.getCredit() + requested.getAmount() < 1) {
                    subscription.getAnnotations().put(AnnotationKeys.BROKER_ID, brokerInfo.getBrokerId());
                    usageInfo.addUsed(requested.getAmount());
                    break;
                }
            }
        }
        return subscription.getAnnotations().get(AnnotationKeys.BROKER_ID) != null;
    }

    private Map<String, Map<String, UsageInfo>> checkQuotaForAddress(Map<String, Double> limits, Map<String, Map<String, UsageInfo>> usage, Address address, Set<Address> addressSet) {
        AddressType addressType = addressResolver.getType(address);
        AddressPlan addressPlan = addressResolver.getPlan(addressType, address);

        Map<String, Map<String, UsageInfo>> needed = copyUsageMap(usage);

        for (ResourceRequest resourceRequest : addressPlan.getRequiredResources()) {
            String resourceName = resourceRequest.getResourceName();
            Map<String, UsageInfo> resourceUsage = needed.computeIfAbsent(resourceName, k -> new HashMap<>());
            if ("router".equals(resourceName)) {
                UsageInfo info = resourceUsage.computeIfAbsent("all", k -> new UsageInfo());
                info.addUsed(resourceRequest.getAmount());
            } else if ("broker".equals(resourceName)) {
                if ("subscription".equals(address.getType())) {
                    Map<String, UsageInfo> subscriptionUsage = needed.computeIfAbsent("subscription", k -> new HashMap<>());
                    if (address.getTopic().isPresent()) {
                        Address topic = findAddress(address.getTopic().get(), addressSet);
                        if (!scheduleSubscription(address, topic, resourceUsage, subscriptionUsage, resourceRequest)) {
                            log.warn("Unable to find broker for scheduling subscription: {}", address);
                            return null;
                        }
                    } else {
                        log.warn("No topic specified for subscription {}", address.getAddress());
                    }
                } else if (resourceRequest.getAmount() < 1) {
                    boolean scheduled = scheduleAddress(resourceUsage, address, resourceRequest.getAmount());
                    if (!scheduled) {
                        allocateBroker(resourceUsage, "broker-");
                        if (!scheduleAddress(resourceUsage, address, resourceRequest.getAmount())) {
                            log.warn("Unable to find broker for scheduling {}", address);
                            return null;
                        }
                    }
                } else {
                    UsageInfo info = resourceUsage.get(getShardedClusterId(address));
                    if (info != null) {
                        throw new IllegalArgumentException("Found unexpected conflicting usage for address " + address.getName());
                    }
                    info = new UsageInfo();
                    info.addUsed(resourceRequest.getAmount());
                    resourceUsage.put(getShardedClusterId(address), info);
                    address.putAnnotation(AnnotationKeys.CLUSTER_ID, getShardedClusterId(address));
	                List<BrokerInfo> brokers = new ArrayList<>();
                    for (String host : resourceUsage.keySet()) {
                        if (host.startsWith(address.getAddress())) {
                            brokers.add(new BrokerInfo(host, resourceUsage.get(host).getUsed()));
                        }
                    }

                    brokers.sort(Comparator.comparingDouble(BrokerInfo::getCredit));

                    for (BrokerInfo brokerInfo : brokers) {
                        if (brokerInfo.getCredit() + resourceRequest.getAmount() < 1) {
                            address.getAnnotations().put(AnnotationKeys.BROKER_ID, brokerInfo.getBrokerId());
                            UsageInfo used = resourceUsage.get(brokerInfo.getBrokerId());
                            used.addUsed(resourceRequest.getAmount());
                            break;
                        } else {
                            log.warn("not enough credit on {} for {} ",brokerInfo.getBrokerId(), address.getAddress() );
                        }
                    }
                }
            } else {
                log.warn("should not be called with: {}", resourceName);
            }

            double resourceNeeded = sumNeeded(resourceUsage);
            if (resourceNeeded > limits.get(resourceName)) {
                log.info("address {} for {} needed {} > limit {}", address.getAddress(), resourceName, resourceNeeded, limits.get(resourceRequest.getResourceName()));
                address.getStatus().setPhase(Status.Phase.Pending);
                address.getStatus().appendMessage("Quota exceeded");
                return null;
            }
        }

        log.debug("address: {}, usage {}, needed: {}, aggregate: {}", address.getAddress(), usage, needed, limits);

        double totalNeeded = sumTotalNeeded(needed);
        if (totalNeeded > limits.get("aggregate")) {
            log.info("address {} usage {}, total needed {} > limit {}", address.getAddress(), usage, totalNeeded, limits.get("aggregate"));
            address.getStatus().setPhase(Status.Phase.Pending);
            address.getStatus().appendMessage("Quota exceeded");
            return null;
        }
        return needed;
    }

    static int sumTotalNeeded(Map<String, Map<String, UsageInfo>> usageMap) {
        int totalNeeded = 0;
        for (String resource : usageMap.keySet()) {
            Map<String, UsageInfo> usage = usageMap.get(resource);
            for (UsageInfo value : usage.values()) {
                if (!"subscription".equals(resource)) {
                    totalNeeded += value.getNeeded();
                }
            }
        }
        return totalNeeded;
    }

    static int sumNeeded(Map<String, UsageInfo> resourceUsage) {
        int needed = 0;
        for (UsageInfo value : resourceUsage.values()) {
            needed += value.getNeeded();
        }
        return needed;
    }

    static int sumNeededMatching(Map<String, UsageInfo> resourceUsage, Pattern pattern) {
        int needed = 0;
        for (Map.Entry<String, UsageInfo> entry : resourceUsage.entrySet()) {
            if (pattern.matcher(entry.getKey()).matches()) {
                needed += entry.getValue().getNeeded();
            }
        }
        return needed;
    }

    private static int MAX_ADDRESS_ID_LENGTH = 10;
    public static String getShardedClusterId(Address address) {
        return KubeUtil.sanitizeWithUuid(address.getAddress().substring(0, Math.min(MAX_ADDRESS_ID_LENGTH, address.getAddress().length())), UUID.nameUUIDFromBytes(address.getName().getBytes(StandardCharsets.UTF_8)).toString());
    }


    public static Optional<String> getBrokerId(Address address) {
        return Optional.ofNullable(address.getAnnotation(AnnotationKeys.BROKER_ID));
    }

    private Map<String, Double> computeLimits() {
        Map<String, Double> limits = new HashMap<>();
        for (ResourceAllowance allowance : addressSpacePlan.getResources()) {
            limits.put(allowance.getResourceName(), allowance.getMax());
        }
        return limits;
    }

    public void provisionResources(RouterCluster router, List<BrokerCluster> existingClusters, Map<String, Map<String, UsageInfo>> neededMap, Set<Address> addressSet) {

        Map<String, Address> addressByClusterId = new HashMap<>();
        for (Address address : addressSet) {
            if (!"subscription".equals(address.getType())) {
                addressByClusterId.putIfAbsent(address.getAnnotation(AnnotationKeys.CLUSTER_ID), address);
            }
        }

        for (Map.Entry<String, Map<String, UsageInfo>> entry : neededMap.entrySet()) {
            String resourceName = entry.getKey();
            if ("router".equals(resourceName)) {
                int totalNeeded = sumNeeded(entry.getValue());
                router.setNewReplicas(totalNeeded);
            } else if ("broker".equals(resourceName)) {
                // Provision pooled broker
                ResourceDefinition pooledDefinition = addressResolver.getResourceDefinition(resourceName);
                int needPooled = sumNeededMatching(entry.getValue(), pooledPattern);
                if (needPooled > 0) {
                    provisionBroker(existingClusters, "broker", pooledDefinition, needPooled, null);
                }

                // Collect all sharded brokers
                Map<String, Integer> shardedBrokers = new HashMap<>();
                for (Map.Entry<String, UsageInfo> usageEntry : entry.getValue().entrySet()) {
                    if (addressByClusterId.containsKey(usageEntry.getKey())) {
                        shardedBrokers.put(usageEntry.getKey(), usageEntry.getValue().getNeeded());
                    }
                }

                for (Map.Entry<String, Integer> brokerIdEntry : shardedBrokers.entrySet()) {
                    Address address = addressByClusterId.get(brokerIdEntry.getKey());
                    if ("subscription".equals(address.getType())) {
                        break;
                    }
                    AddressType addressType = addressResolver.getType(address);
                    AddressPlan addressPlan = addressResolver.getPlan(addressType, address);
                    ResourceDefinition resourceDefinition = addressResolver.getResourceDefinition(addressPlan, resourceName);
                    provisionBroker(existingClusters, brokerIdEntry.getKey(), resourceDefinition, brokerIdEntry.getValue(), address);
                }
            }
        }

        if (router.hasChanged()) {
            log.info("Scaling router to {} replicas", router.getNewReplicas());
            kubernetes.scaleDeployment(router.getName(), router.getNewReplicas());
        }
        for (BrokerCluster cluster : existingClusters) {
            if (cluster.hasChanged()) {
                log.info("Scaling broker cluster {} to {} replicas", cluster.getClusterId(), cluster.getNewReplicas());
                kubernetes.scaleStatefulSet(cluster.getClusterId(), cluster.getNewReplicas());
            }
        }
    }

    private final Pattern pooledPattern = Pattern.compile("^broker-\\d+");
    private boolean scheduleAddress(Map<String, UsageInfo> usageMap, Address address, double credit) {

        address.putAnnotation(AnnotationKeys.CLUSTER_ID, "broker");

        List<BrokerInfo> brokers = new ArrayList<>();
        for (String host : usageMap.keySet()) {
            if (pooledPattern.matcher(host).matches()) {
                brokers.add(new BrokerInfo(host, usageMap.get(host).getUsed()));
            }
        }

        brokers.sort(Comparator.comparingDouble(BrokerInfo::getCredit));

        for (BrokerInfo brokerInfo : brokers) {
            if (brokerInfo.getCredit() + credit < 1) {
                address.getAnnotations().put(AnnotationKeys.BROKER_ID, brokerInfo.getBrokerId());
                UsageInfo used = usageMap.get(brokerInfo.getBrokerId());
                used.addUsed(credit);
                return true;
            }
        }
        return false;
    }

    private void allocateBroker(Map<String, UsageInfo> resourceNeeded, String clusterId) {
        int numPooled = 0;
        for (String id : resourceNeeded.keySet()) {
            if (pooledPattern.matcher(id).matches()) {
                numPooled++;
            }
        }

        resourceNeeded.put(clusterId + numPooled, new UsageInfo());
    }

    private void provisionBroker(List<BrokerCluster> clusterList, String clusterId, ResourceDefinition resourceDefinition, int numReplicas, Address address) {
        try {
            for (BrokerCluster cluster : clusterList) {
                if (cluster.getClusterId().equals(clusterId)) {
                    cluster.setNewReplicas(numReplicas);
                    return;
                }
            }

            // Needs to be created
            BrokerCluster cluster = clusterGenerator.generateCluster(clusterId, resourceDefinition, numReplicas, address);
            if (!cluster.getResources().getItems().isEmpty()) {
                kubernetes.create(cluster.getResources());
                eventLogger.log(BrokerCreated, "Created broker " + cluster.getClusterId() + " with " + numReplicas + " replicas", Normal, Broker, cluster.getClusterId());
            }
            clusterList.add(cluster);
        } catch (Exception e) {
            log.warn("Error creating broker", e);
            eventLogger.log(BrokerCreateFailed, "Error creating broker: " + e.getMessage(), Warning, Broker, clusterId);
            address.getStatus().setPhase(Status.Phase.Failed);
            address.getStatus().appendMessage("Error creating broker: " + e.getMessage());
        }
    }

    private static class BrokerInfo {
        private final String brokerId;
        private final double credit;

        public String getBrokerId() {
            return brokerId;
        }

        public double getCredit() {
            return credit;
        }

        private BrokerInfo(String brokerId, double credit) {
            this.brokerId = brokerId;
            this.credit = credit;
        }

    }
}
