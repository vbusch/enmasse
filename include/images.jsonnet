local version = std.extVar("VERSION");
local project = "maas-ci";
{
  image(name)::
    project + "/" + name + ":" + version,
    
  address_controller::
    self.image("amqmaas10-addresscontroller-openshift"),

  router::
    self.image("amqmaas10-interconnect-openshift"),

  artemis::
    self.image("amqmaas10-broker-openshift"),

  topic_forwarder::
    self.image("amqmaas10-topicforwarder-openshift"),

  router_metrics::
    self.image("amqmaas10-routermetrics-openshift"),

  configserv::
    self.image("amqmaas10-configserv-openshift"),

  queue_scheduler::
    self.image("amqmaas10-queuescheduler-openshift"),

  ragent::
    self.image("amqmaas10-routeragent-openshift"),

  subserv::
    self.image("amqmaas10-subscriptionservice-openshift"),

  console::
    self.image("amqmaas10-console-openshift"),

  mqtt_gateway::
    self.image("amqmaas10-mqttgateway-openshift"),

  mqtt_lwt::
    self.image("amqmaas10-mqttlwt-openshift"),

  amqp_kafka_bridge::
    self.image("amqp-kafka-bridge")
}
