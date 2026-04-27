// ============================================================
// Backend Pipeline (be/dev 브랜치 전용)
//
// be/dev 브랜치 구조:
//   /
//   ├── Jenkinsfile        ← 이 파일
//   └── backend/
//       ├── Dockerfile
//       ├── build.gradle
//       └── src/...
//
// 인프라는 EC2의 /opt/nemonic/infra/ 에 영구 clone되어 있음.
// 빌드 산출물(tar.gz)에는 backend/ 폴더만 포함.
// ============================================================

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '5'))
    }

    parameters {
        string(name: 'DEPLOY_BASE_DIR',
               defaultValue: '/opt/nemonic',
               description: '호스트 기준 배포 디렉토리')
        booleanParam(name: 'RUN_DEPLOY',
                     defaultValue: true,
                     description: '체크 시 배포 진행')
        booleanParam(name: 'SKIP_TESTS',
                     defaultValue: false,
                     description: '테스트 스킵 (긴급용)')
    }

    environment {
        APP_NAME             = 'nemonic'
        COMPOSE_PROJECT_NAME = 'nemonic-prod'
        HOST_BASE_DIR        = "${params.DEPLOY_BASE_DIR}"
        RELEASE_ARCHIVE      = 'release-be.tar.gz'
        DEPLOY_TARGET        = 'backend'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.SHORT_SHA = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    env.RELEASE_NAME = "release-be-${BUILD_NUMBER}-${env.SHORT_SHA}"
                    echo "Release name: ${env.RELEASE_NAME}"
                }
            }
        }

        stage('Test') {
            when { expression { !params.SKIP_TESTS } }
            steps {
                dir('backend') {
                    sh '''
                        set -euo pipefail
                        chmod +x gradlew
                        ./gradlew --no-daemon test
                    '''
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'backend/build/test-results/test/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                sh '''
                    set -euo pipefail
                    # backend/ 폴더만 tar.gz로 패키징
                    tar -czf "${RELEASE_ARCHIVE}" backend/
                    ls -lh "${RELEASE_ARCHIVE}"
                '''
            }
        }

        stage('Deploy') {
            when {
                expression {
                    def branch = env.BRANCH_NAME ?: env.GIT_BRANCH ?: ''
                    return params.RUN_DEPLOY && (
                        branch ==~ /(origin\/)?be\/dev/
                    )
                }
            }
            steps {
                sh '''
                    set -euo pipefail

                    INCOMING_DIR="${HOST_BASE_DIR}/incoming"
                    INFRA_DIR="${HOST_BASE_DIR}/infra"

                    if [[ ! -d "${INFRA_DIR}" ]]; then
                      echo "[ERROR] ${INFRA_DIR} 없음. infra/main 클론 필요." >&2
                      exit 1
                    fi

                    mkdir -p "${INCOMING_DIR}"
                    cp "${RELEASE_ARCHIVE}" "${INCOMING_DIR}/${RELEASE_NAME}.tar.gz"

                    # infra의 remote-deploy.sh 실행 (--target backend)
                    APP_NAME="${APP_NAME}" \
                    COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME}" \
                    bash "${INFRA_DIR}/deploy/remote-deploy.sh" \
                      --base-dir "${HOST_BASE_DIR}" \
                      --archive "${INCOMING_DIR}/${RELEASE_NAME}.tar.gz" \
                      --release "${RELEASE_NAME}" \
                      --env-file "${HOST_BASE_DIR}/shared/.env.prod" \
                      --target backend
                '''
            }
        }
    }

    post {
        success {
            echo "Backend 배포 성공: ${env.RELEASE_NAME ?: 'N/A'}"
        }
        failure {
            echo "Backend 배포 실패. 로그 확인."
        }
        always {
            archiveArtifacts artifacts: 'release-be*.tar.gz',
                             fingerprint: true,
                             onlyIfSuccessful: false,
                             allowEmptyArchive: true
        }
    }
}
