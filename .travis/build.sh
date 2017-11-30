#!/bin/sh
set -e
PULL_REQUEST=${PULL_REQUEST:-true}
BRANCH=${BRANCH:-master}
COMMIT=${COMMIT:-latest}
VERSION=`cat release.version`
TAG=${TAG:-latest}
DOCKER_ORG=${DOCKER_ORG:-$USER}
OPENSHIFT_KUBECONFIG=${OPENSHIFT_KUBECONFIG:-/tmp/openshift/config/master/admin.kubeconfig}
SYSTEMTEST_ARGS=${SYSTEMTEST_ARGS:-SmokeTest}

if [ "$TAG" != "latest" ]; then
    COMMIT=$TAG
fi

if [ "$BRANCH" != "master" ] && [ "$BRANCH" != "$VERSION" ] || [ "$PULL_REQUEST" != "false" ]
then
    export DOCKER_REGISTRY="172.30.1.1:5000"
    export DOCKER_ORG=enmasseci
fi

echo "Building EnMasse with tag $TAG, commit $COMMIT, version $VERSION from $BRANCH. PR: $PULL_REQUEST"
MOCHA_ARGS="--reporter=mocha-junit-reporter" make

echo "Tagging Docker Images"
make TAG=$COMMIT docker_tag
#
if [ "$BRANCH" != "master" ] && [ "$BRANCH" != "$TAG" ] || [ "$PULL_REQUEST" != "false" ]
then
    echo "Logging into to local docker registry"
    oc login -u test -p test --insecure-skip-tls-verify=true https://localhost:8443
    oc new-project enmasseci

    docker login -u enmasseci -p `oc whoami -t` 172.30.1.1:5000
else
    make docker_tag
    echo "Logging in to Docker Hub"
    docker login -u $DOCKER_USER -p $DOCKER_PASS
fi

echo "Pushing images to Docker Registry"
make TAG=$COMMIT docker_push

echo "Running systemtests"
./systemtests/scripts/run_test_component.sh templates/install ${OPENSHIFT_KUBECONFIG} systemtests ${SYSTEMTEST_ARGS}
