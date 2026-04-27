// ============================================================
// Backend Pipeline (be/dev 브랜치 전용)
//
// 동작:
// 1. GitLab be/dev push → Webhook 트리거
// 2. backend/ 코드 테스트 + tar.gz 패키징
// 3. EC2의 /opt/nemonic/infra/deploy/remote-deploy.sh 실행
// 4. 배포 성공/실패 시 Mattermost 알림 전송
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
        timeout(time: 30, unit: 'MINUTES')
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
                     description: '테스트 스킵 (긴급 배포용)')
        booleanParam(name: 'NOTIFY_MM',
                     defaultValue: true,
                     description: 'Mattermost 알림 전송 여부')
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
                    env.COMMIT_MSG = sh(
                        script: "git log -1 --pretty=%s",
                        returnStdout: true
                    ).trim()
                    env.COMMIT_AUTHOR = sh(
                        script: "git log -1 --pretty=%an",
                        returnStdout: true
                    ).trim()
                    env.RELEASE_NAME = "release-be-${BUILD_NUMBER}-${env.SHORT_SHA}"
                    echo "Release name: ${env.RELEASE_NAME}"
                    echo "Author: ${env.COMMIT_AUTHOR}"
                    echo "Message: ${env.COMMIT_MSG}"
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

                    if [ ! -d "${INFRA_DIR}" ]; then
                      echo "[ERROR] ${INFRA_DIR} 없음. infra/dev 클론 필요." >&2
                      exit 1
                    fi

                    mkdir -p "${INCOMING_DIR}"
                    cp "${RELEASE_ARCHIVE}" "${INCOMING_DIR}/${RELEASE_NAME}.tar.gz"

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
            script {
                if (params.NOTIFY_MM) {
                    notifyMattermost('success')
                }
            }
        }
        failure {
            echo "Backend 배포 실패. 로그 확인."
            script {
                if (params.NOTIFY_MM) {
                    notifyMattermost('failure')
                }
            }
        }
        aborted {
            echo "Backend 배포 중단됨."
            script {
                if (params.NOTIFY_MM) {
                    notifyMattermost('aborted')
                }
            }
        }
        always {
            archiveArtifacts artifacts: 'release-be*.tar.gz',
                             fingerprint: true,
                             onlyIfSuccessful: false,
                             allowEmptyArchive: true
        }
    }
}

// ============================================================
// Mattermost 알림 함수
// 알림 실패가 빌드 전체 실패로 이어지지 않도록 try-catch 처리
// ============================================================
def notifyMattermost(String status) {
    try {
        def color
        def emoji
        def title
        switch (status) {
            case 'success':
                color = '#36A64F'  // 녹색
                emoji = '✅'
                title = 'Backend 배포 성공'
                break
            case 'failure':
                color = '#D00000'  // 빨강
                emoji = '❌'
                title = 'Backend 배포 실패'
                break
            case 'aborted':
                color = '#808080'  // 회색
                emoji = '⚠️'
                title = 'Backend 배포 중단'
                break
            default:
                color = '#FFA500'
                emoji = 'ℹ️'
                title = "Backend 배포 ${status}"
        }

        def shortSha   = env.SHORT_SHA ?: 'unknown'
        def commitMsg  = (env.COMMIT_MSG ?: 'N/A').replaceAll('\n', ' ')
        def author     = env.COMMIT_AUTHOR ?: 'unknown'
        def buildNum   = env.BUILD_NUMBER ?: '?'
        def buildUrl   = env.BUILD_URL ?: ''
        def duration   = currentBuild.durationString.replace(' and counting', '')
        def releaseNm  = env.RELEASE_NAME ?: 'N/A'
        def branch     = env.BRANCH_NAME ?: 'be/dev'

        def text = """${emoji} **${title}**

**브랜치**: \\`${branch}\\`
**커밋**: \\`${shortSha}\\` - ${commitMsg}
**작성자**: ${author}
**빌드**: [#${buildNum}](${buildUrl}console) (${duration})
**릴리스**: \\`${releaseNm}\\`"""

        // JSON payload 생성 (특수문자 자동 이스케이프)
        def payload = groovy.json.JsonOutput.toJson([
            username  : 'Jenkins',
            icon_emoji: ':jenkins:',
            attachments: [[
                color: color,
                text : text
            ]]
        ])

        writeFile file: 'mm-payload.json', text: payload

        withCredentials([string(credentialsId: 'mm-webhook-url', variable: 'WEBHOOK_URL')]) {
            sh '''
                set +e
                curl -sS -X POST \
                  -H "Content-Type: application/json" \
                  --data @mm-payload.json \
                  --max-time 10 \
                  "$WEBHOOK_URL"
                EXIT_CODE=$?
                if [ $EXIT_CODE -ne 0 ]; then
                  echo "[WARN] MM 알림 실패 (exit code: $EXIT_CODE) - 무시하고 계속"
                fi
                rm -f mm-payload.json
                exit 0
            '''
        }
    } catch (Exception e) {
        echo "[WARN] MM 알림 중 에러: ${e.message} (무시하고 계속)"
    }
}
