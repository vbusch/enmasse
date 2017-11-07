BUILD_DIRS     = ragent subserv agent none-authservice templates
DOCKER_DIRS	   = topic-forwarder artemis address-controller queue-scheduler configserv keycloak keycloak-controller router router-metrics mqtt-gateway mqtt-lwt
FULL_BUILD 	   = true
DOCKER_REGISTRY ?= docker.io
OPENSHIFT_PROJECT ?= $(shell oc project -q)
OPENSHIFT_USER    ?= $(shell oc whoami)
OPENSHIFT_TOKEN   ?= $(shell oc whoami -t)
OPENSHIFT_MASTER  ?= $(shell oc whoami --show-server=true)
MODE              ?= singletenant

ifeq ($(MODE),singletenant)
	MULTITENANT=false
else
	MULTITENANT=true
endif

DOCKER_TARGETS = docker_build docker_tag docker_push
BUILD_TARGETS  = init build test package clean $(DOCKER_TARGETS) coverage

all: init build test package 
#docker_build

# TODO: get rid of the below target
build_amqp_module:
	$(MAKE) -C artemis build_amqp_module

build_java: build_amqp_module
	mvn test package -B $(MAVEN_ARGS)
	
package_java:
	mvn package -DskipTests=true	

clean_java:
	mvn -B clean

clean: clean_java

docker_build: build_java

coverage: java_coverage
	$(MAKE) FULL_BUILD=$(FULL_BUILD) -C $@ coverage

java_coverage: build_amqp_module
	mvn test -Pcoverage package -B $(MAVEN_ARGS)
	mvn jacoco:report-aggregate

$(BUILD_TARGETS): $(BUILD_DIRS)
$(BUILD_DIRS):
	$(MAKE) FULL_BUILD=$(FULL_BUILD) -C $@ $(MAKECMDGOALS)

$(DOCKER_TARGETS): $(DOCKER_DIRS)
$(DOCKER_DIRS):
	$(MAKE) FULL_BUILD=$(FULL_BUILD) -C $@ $(MAKECMDGOALS)

deploy:
	./templates/install/deploy-openshift.sh -n $(OPENSHIFT_PROJECT) -u $(OPENSHIFT_USER) -m $(OPENSHIFT_MASTER) -o $(MODE) -a "standard none"

systemtests:
	OPENSHIFT_PROJECT=$(OPENSHIFT_PROJECT) OPENSHIFT_MULTITENANT=$(MULTITENANT) OPENSHIFT_TOKEN=$(OPENSHIFT_TOKEN) OPENSHIFT_USER=$(OPENSHIFT_USER) OPENSHIFT_URL=$(OPENSHIFT_MASTER) OPENSHIFT_USE_TLS=true ./systemtests/scripts/run_tests.sh $(SYSTEMTEST_ARGS)


.PHONY: $(BUILD_TARGETS)  $(BUILD_DIRS) $(DOCKER_DIRS) build_java deploy systemtests clean_java
