pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
    }

    tools {
        jdk 'jdk21'
    }

    parameters {
        string(name: 'DEPLOY_HOST', defaultValue: 'k14s208.p.ssafy.io', description: 'EC2 SSH host')
        string(name: 'DEPLOY_USER', defaultValue: 'ubuntu', description: 'EC2 SSH user')
        string(name: 'DEPLOY_BASE_DIR', defaultValue: '/opt/nemonic', description: 'Remote deployment base directory')
        booleanParam(name: 'RUN_DEPLOY', defaultValue: true, description: 'Run remote deployment after tests')
    }

    environment {
        APP_NAME = 'nemonic'
        COMPOSE_PROJECT = 'nemonic-prod'
        RELEASE_ARCHIVE = 'release.tar.gz'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                dir('backend') {
                    sh './gradlew --no-daemon test'
                }
            }
        }

        stage('Package') {
            steps {
                sh '''
                    set -euo pipefail
                    git archive --format=tar.gz --output "${RELEASE_ARCHIVE}" HEAD
                    ls -lh "${RELEASE_ARCHIVE}"
                '''
            }
        }

        stage('Deploy') {
            when {
                expression {
                    return params.RUN_DEPLOY && (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'be/dev')
                }
            }
            steps {
                sshagent(credentials: ['nemonic-ec2-ssh']) {
                    sh '''
                        set -euo pipefail

                        SHORT_SHA="$(git rev-parse --short HEAD)"
                        RELEASE_NAME="release-${BUILD_NUMBER}-${SHORT_SHA}"
                        SSH_TARGET="${DEPLOY_USER}@${DEPLOY_HOST}"
                        REMOTE_ARCHIVE="${DEPLOY_BASE_DIR}/incoming/${RELEASE_NAME}.tar.gz"

                        ssh -o StrictHostKeyChecking=accept-new "${SSH_TARGET}" \
                          "mkdir -p '${DEPLOY_BASE_DIR}/incoming' '${DEPLOY_BASE_DIR}/releases' '${DEPLOY_BASE_DIR}/shared'"

                        scp -o StrictHostKeyChecking=accept-new "${RELEASE_ARCHIVE}" "${SSH_TARGET}:${REMOTE_ARCHIVE}"

                        ssh -o StrictHostKeyChecking=accept-new "${SSH_TARGET}" \
                          "APP_NAME='${APP_NAME}' COMPOSE_PROJECT_NAME='${COMPOSE_PROJECT}' bash -s -- --base-dir '${DEPLOY_BASE_DIR}' --archive '${REMOTE_ARCHIVE}' --release '${RELEASE_NAME}' --env-file '${DEPLOY_BASE_DIR}/shared/.env.prod'" \
                          < deploy/remote-deploy.sh
                    '''
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'release*.tar.gz', fingerprint: true, onlyIfSuccessful: false
        }
    }
}
