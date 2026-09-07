// 도담 배포 파이프라인
// GitLab push(웹훅) → Jenkins → 이미지 빌드(테스트 포함) → EC2 스택 재기동 → 헬스체크
//
// 빌드/테스트는 backend/Dockerfile의 builder 스테이지에서 수행된다.
// (Jenkins 컨테이너에 JDK21/gradle이 없고, DooD 환경에서 워크스페이스 경로가
//  호스트와 달라 docker.inside 방식이 맞지 않기 때문)

pipeline {
    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        APP_DIR    = '/home/ubuntu/app'
        HEALTH_URL = 'http://nginx/api/health'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --pretty="%h %s"'
            }
        }

        stage('Build & Test') {
            steps {
                // Dockerfile builder 스테이지에서 ./gradlew clean build 수행 (테스트 포함).
                // 테스트가 깨지면 이미지 빌드가 실패하며 파이프라인도 중단된다.
                sh '''
                    set -e
                    docker compose \
                        -f "$APP_DIR/infra/docker-compose.yml" \
                        --env-file "$APP_DIR/infra/.env" \
                        build backend
                '''
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    set -e
                    export APP_DIR="$APP_DIR"
                    export DEPLOY_BRANCH="${GIT_BRANCH#origin/}"
                    export HEALTH_URL="$HEALTH_URL"
                    bash "$APP_DIR/infra/scripts/deploy.sh"
                '''
            }
        }
    }

    post {
        success {
            echo "배포 성공: ${env.GIT_BRANCH} (${env.BUILD_NUMBER})"
        }
        failure {
            sh '''
                echo "=== 실패 진단: 컨테이너 상태 ==="
                docker compose -f "$APP_DIR/infra/docker-compose.yml" --env-file "$APP_DIR/infra/.env" ps || true
                echo "=== backend 로그 ==="
                docker compose -f "$APP_DIR/infra/docker-compose.yml" --env-file "$APP_DIR/infra/.env" logs --tail=50 backend || true
            '''
        }
    }
}
