node {
    checkout scm
    sh 'git submodule update --init' 
    stage ('build') {
        sh 'make'
    }
    stage('system tests') {
        withEnv(['SCRIPTS=https://raw.githubusercontent.com/EnMasseProject/travis-scripts/master', 'OPENSHIFT_PROJECT=`echo "${JOB_NAME}" | tr / -`']) {
            sh 'env'
            sh 'rm -rf systemtests'
            sh 'git clone https://github.com/EnMasseProject/systemtests.git'
            sh 'curl -s ${SCRIPTS}/run-tests.sh | bash /dev/stdin "" install'
        }
    }
    stage('cleanup') {
        archive 'install/**'
        deleteDir()
    }
}
