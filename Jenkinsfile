// ============================================================
// Jenkins Pipeline (EC2 내부 Jenkins 전용)
//
// Jenkins가 EC2 안의 Docker 컨테이너로 돌고 있으므로
// 원격 SSH가 아닌 "로컬 shell"에서 바로 배포한다.
//
// 전제 조건:
// - /var/run/docker.sock이 Jenkins 컨테이너에 마운트됨
// - Jenkins 컨테이너 안에 docker CLI가 있음 (바인드 마운트)
// - 프로젝트 루트의 docker-compose.prod.yml 기준으로 동작
// ============================================================

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
        // 빌드 기록 너무 많이 쌓이지 않게
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '5'))
    }

    tools {
        jdk 'jdk21'
    }

    parameters {
        string(name: 'DEPLOY_BASE_DIR',
               defaultValue: '/opt/nemonic',
               description: '호스트 기준 배포 디렉토리 (컨테이너 내부 경로로 마운트 필요)')
        booleanParam(name: 'RUN_DEPLOY',
                     defaultValue: true,
                     description: '테스트만 할지, 배포까지 진행할지')
        booleanParam(name: 'SKIP_TESTS',
                     defaultValue: false,
                     description: '긴급 배포 시 테스트 스킵 (권장하지 않음)')
    }

    environment {
        APP_NAME             = 'nemonic'
        COMPOSE_PROJECT_NAME = 'nemonic-prod'
        // Jenkins 컨테이너 안에서 호스트 경로 접근용
        // (docker-compose에서 /opt/nemonic:/opt/nemonic 볼륨 마운트 필요)
        HOST_BASE_DIR        = "${params.DEPLOY_BASE_DIR}"
        RELEASE_ARCHIVE      = 'release.tar.gz'
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
                    env.RELEASE_NAME = "release-${BUILD_NUMBER}-${env.SHORT_SHA}"
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
                    git archive --format=tar.gz --output "${RELEASE_ARCHIVE}" HEAD
                    ls -lh "${RELEASE_ARCHIVE}"
                '''
            }
        }

        stage('Deploy') {
            when {
                expression {
                    def branch = env.BRANCH_NAME ?: env.GIT_BRANCH ?: ''
                    // 'dev', 'be/dev', 'origin/dev' 등 모두 허용
                    return params.RUN_DEPLOY && (
                        branch ==~ /(origin\/)?(be\/)?dev/
                    )
                }
            }
            steps {
                sh '''
                    set -euo pipefail

                    INCOMING_DIR="${HOST_BASE_DIR}/incoming"
                    mkdir -p "${INCOMING_DIR}"

                    # 릴리스 아카이브를 incoming으로 이동
                    cp "${RELEASE_ARCHIVE}" "${INCOMING_DIR}/${RELEASE_NAME}.tar.gz"

                    # remote-deploy.sh를 incoming에 복사 (릴리스 아카이브가 아직 풀리지 않아서)
                    cp deploy/remote-deploy.sh "${INCOMING_DIR}/remote-deploy-${BUILD_NUMBER}.sh"
                    chmod +x "${INCOMING_DIR}/remote-deploy-${BUILD_NUMBER}.sh"

                    # 배포 실행
                    APP_NAME="${APP_NAME}" \
                    COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME}" \
                    bash "${INCOMING_DIR}/remote-deploy-${BUILD_NUMBER}.sh" \
                      --base-dir "${HOST_BASE_DIR}" \
                      --archive "${INCOMING_DIR}/${RELEASE_NAME}.tar.gz" \
                      --release "${RELEASE_NAME}" \
                      --env-file "${HOST_BASE_DIR}/shared/.env.prod"

                    # 스크립트 정리
                    rm -f "${INCOMING_DIR}/remote-deploy-${BUILD_NUMBER}.sh"
                '''
            }
        }
    }

    post {
        success {
            echo "배포 성공: ${env.RELEASE_NAME ?: 'N/A'}"
        }
        failure {
            echo "배포 실패. 로그를 확인하세요."
        }
        always {
            archiveArtifacts artifacts: 'release*.tar.gz',
                             fingerprint: true,
                             onlyIfSuccessful: false,
                             allowEmptyArchive: true
        }
    }
}
