node('downstream') {
    stage ('checkout') {
        dir('enmasse') {
            git branch: 'ci', url: 'https://github.com/vbusch/enmasse.git'
            sh('git submodule update --init')
        }
        dir('dockerfiles') {
            git branch: 'ci', url: 'https://github.com/vbusch/dockerfiles.git'
        }
        dir('scripts') {
            git branch: 'ci', url: 'https://github.com/vbusch/scripts.git'
        }  
    }
    stage ('build') {
        dir('enmasse') {
            sh './gradlew build check -i --rerun-tasks'
            junit '**/TEST-*.xml'
        }
    }
    stage ('build docker image') {

        dir('enmasse') {
            def commit = sh(script: 'git rev-parse HEAD |cut -c1-8', returnStdout: true).trim()
            withEnv(["DOCKER_REGISTRY=docker-registry.engineering.redhat.com", "DOCKER_ORG=jboss-amqmaas-1-tech-preview", "TAG=$commit", "VERSION=$commit", "COMMIT=$commit"]) {
                sh './gradlew pack'
                sh 'cat templates/install/openshift/enmasse.yaml'
            }
            withEnv(["DOCKER_BUILD_OPTS=--network host", "COMMIT=$commit"]) {
                sh 'make -C ../dockerfiles copyartifactall'
                sh 'make -C ../dockerfiles'
            }
        }
    }
    stage ('push docker image') {
        dir('enmasse') {
            def commit = sh(script: 'git rev-parse HEAD |cut -c1-8', returnStdout: true).trim()
            withCredentials([usernamePassword(credentialsId: 'ff36253f-ff0b-4ab7-be0e-8eb09bc93f15', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                withEnv(["DOCKER_REGISTRY=docker-registry.engineering.redhat.com", "COMMIT=$commit"]) {
                    sh 'docker login -u $DOCKER_USER -p $DOCKER_PASS -e $DOCKER_USER $DOCKER_REGISTRY'
                    sh 'make  -C ../dockerfiles pushall'
                }
            }
        }
    }
    stage('system tests') {
        dir('enmasse') {
            withCredentials([usernamePassword(credentialsId: 'openshift-credentials', passwordVariable: 'OPENSHIFT_PASSWD', usernameVariable: 'OPENSHIFT_USER')]) {
               withEnv(['SCRIPTS=https://raw.githubusercontent.com/vbusch/travis-scripts/master']) {
                    sh 'cat templates/install/openshift/enmasse.yaml'
                    sh 'export OPENSHIFT_PROJECT=$BUILD_TAG; curl -s ${SCRIPTS}/run-tests.sh | bash /dev/stdin "" templates/install'
                }
            }
        }
    }
}
