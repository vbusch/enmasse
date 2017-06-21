node {
    checkout scm
    sh 'git submodule update --init' 
    stage ('build') {
        sh 'make'
    }
    stage('system tests') {
        withCredentials([usernamePassword(credentialsId: '8957fba6-7473-40f6-8593-efefa9e42251', passwordVariable: 'OPENSHIFT_PASSWD', usernameVariable: 'OPENSHIFT_USER')]) {
            withEnv(['SCRIPTS=https://raw.githubusercontent.com/EnMasseProject/travis-scripts/master', 'OPENSHIFT_PROJECT=enmasse-ci']) {
                sh 'rm -rf systemtests && git clone https://github.com/EnMasseProject/systemtests.git'
                sh 'curl -s ${SCRIPTS}/run-tests.sh | bash /dev/stdin "" install'
            }
        }
    }
    stage('cleanup') {
        archive 'install/**'
    }
    deleteDir()
}
