// 도담 배포 파이프라인
// GitLab push(웹훅) → Jenkins → 이미지 빌드(테스트 포함) → EC2 스택 재기동 → 헬스체크
//
// 빌드/테스트는 backend/Dockerfile의 builder 스테이지에서 수행된다.
// (Jenkins 컨테이너에 JDK21/gradle이 없고, DooD 환경에서 워크스페이스 경로가
//  호스트와 달라 docker.inside 방식이 맞지 않기 때문)

// Mattermost 알림.
// webhook URL은 로그에 남으면 안 되므로 .env에서 읽어 셸 안에서만 사용하고,
// 해당 구간은 set +x로 명령 에코를 끈다.
def notifyMattermost(String text) {
    // JsonOutput은 Jenkins 스크립트 샌드박스에서 승인이 필요할 수 있어 직접 조립한다.
    def payload = '{"text": "' + text.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n') + '"}'
    writeFile file: 'mm-payload.json', text: payload
    sh '''
        set +x
        WEBHOOK=$(grep -E "^MATTERMOST_WEBHOOK_URL=" "$APP_DIR/infra/.env" 2>/dev/null | cut -d= -f2-)
        if [ -n "$WEBHOOK" ]; then
            curl -s -o /dev/null --max-time 10 -X POST \
                 -H "Content-Type: application/json" \
                 --data-binary @mm-payload.json "$WEBHOOK" || echo "[notify] Mattermost 전송 실패(무시)"
        else
            echo "[notify] MATTERMOST_WEBHOOK_URL 미설정 — 알림 생략"
        fi
        rm -f mm-payload.json
    '''
}

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
                // 방금 체크아웃한 워크스페이스 코드로 빌드한다.
                // ($APP_DIR은 Deploy 단계에서야 동기화되므로, 여기서 $APP_DIR을 쓰면
                //  직전 배포 코드로 테스트하게 된다)
                // Dockerfile builder 스테이지에서 ./gradlew clean build가 돌기 때문에
                // 테스트가 깨지면 이미지 빌드가 실패하고, 배포까지 가지 않는다.
                sh '''
                    set -e
                    docker compose \
                        -f "$WORKSPACE/infra/docker-compose.yml" \
                        --env-file "$APP_DIR/infra/.env" \
                        build backend
                '''
            }
        }

        stage('Deploy') {
            steps {
                // deploy.sh는 $APP_DIR을 git reset --hard로 덮어쓰므로,
                // 실행 중인 스크립트 파일이 교체되지 않도록 워크스페이스 사본을 실행한다.
                // Jenkins 컨테이너에는 호스트의 git 자격증명이 없으므로 여기서 주입한다.
                withCredentials([usernamePassword(
                    credentialsId: 'gitlab-https',
                    usernameVariable: 'GIT_USER',
                    passwordVariable: 'GIT_TOKEN'
                )]) {
                    sh '''
                        set -e
                        export APP_DIR="$APP_DIR"
                        export DEPLOY_BRANCH="${GIT_BRANCH#origin/}"
                        export HEALTH_URL="$HEALTH_URL"
                        bash "$WORKSPACE/infra/scripts/deploy.sh"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "배포 성공: ${env.GIT_BRANCH} (${env.BUILD_NUMBER})"
            script {
                // 매번 성공을 알리면 노이즈가 되어 정작 실패 알림을 아무도 안 읽게 된다.
                // 직전 빌드가 실패했을 때(=복구됐을 때)만 알린다.
                def prev = currentBuild.previousBuild?.result
                if (prev == 'FAILURE' || prev == 'ABORTED') {
                    notifyMattermost(
                        ":white_check_mark: **배포 복구됨** — 도담 백엔드\n" +
                        "브랜치: `${env.GIT_BRANCH}` / 빌드 #${env.BUILD_NUMBER}\n" +
                        "직전 실패 이후 정상 배포되었습니다."
                    )
                }
            }
        }
        failure {
            sh '''
                echo "=== 실패 진단: 컨테이너 상태 ==="
                docker compose -f "$APP_DIR/infra/docker-compose.yml" --env-file "$APP_DIR/infra/.env" ps || true
                echo "=== backend 로그 ==="
                docker compose -f "$APP_DIR/infra/docker-compose.yml" --env-file "$APP_DIR/infra/.env" logs --tail=50 backend || true
            '''
            script {
                // 서비스가 살아 있는지 함께 알려준다.
                // 롤백이 동작하면 배포는 실패해도 서비스는 정상이므로, 대응 긴급도가 다르다.
                def svc = sh(
                    script: 'curl -s -o /dev/null -w "%{http_code}" --max-time 5 http://nginx/api/health || echo 000',
                    returnStdout: true
                ).trim()
                def svcLine = (svc == '200')
                    ? ":large_green_circle: 서비스는 정상입니다 (롤백 동작 또는 배포 전 차단)"
                    : ":red_circle: **서비스도 응답하지 않습니다 (HTTP ${svc}) — 즉시 확인 필요**"

                notifyMattermost(
                    ":rotating_light: **배포 실패** — 도담 백엔드\n" +
                    "브랜치: `${env.GIT_BRANCH}` / 빌드 #${env.BUILD_NUMBER}\n" +
                    svcLine + "\n" +
                    "로그 확인: `ssh -L 8080:127.0.0.1:8080 ssafy-ec2` 후 http://localhost:8080/jenkins/"
                )
            }
        }
    }
}
