pipeline {
    agent any

    environment {
        PROJECT_DIR = '/yunanexus/YunaNexusPackage'
        DOCKER_COMPOSE_DIR = "${PROJECT_DIR}/docker"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = env.GIT_COMMIT?.take(8) ?: 'unknown'
                    env.BUILD_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                }
            }
        }

        stage('Build Backend') {
            steps {
                dir('YunaNexusCore') {
                    sh '''
                        mvn clean package -DskipTests -P prod -q
                        echo "Backend build completed"
                    '''
                }
            }
        }

        stage('Build Frontend') {
            steps {
                dir('YunaNexusWeb') {
                    sh '''
                        export NVM_DIR="${HOME}/.nvm"
                        [ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
                        export NVM_NODEJS_ORG_MIRROR=https://npmmirror.com/mirrors/node
                        pnpm config set registry https://registry.npmmirror.com
                        pnpm install --no-frozen-lockfile
                        pnpm --filter yunanexus-main run build
                        echo "Frontend build completed"
                    '''
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                sh '''
                    echo "Pre-pulling base images..."
                    for img in amazoncorretto:22-alpine node:22-alpine; do
                        for i in 1 2 3; do
                            docker pull "$img" && break || sleep 10
                        done
                    done
                '''
            }
        }

        stage('Build Docker Images (Parallel)') {
            parallel {
                stage('Gateway') {
                    steps {
                        dir('YunaNexusCore/yunanexus-gateway') {
                            sh 'docker build -t yunanexus-gateway:${BUILD_TAG} -t yunanexus-gateway:latest .'
                        }
                    }
                }
                stage('Auth') {
                    steps {
                        dir('YunaNexusCore/yunanexus-auth') {
                            sh 'docker build -t yunanexus-auth:${BUILD_TAG} -t yunanexus-auth:latest .'
                        }
                    }
                }
                stage('User') {
                    steps {
                        dir('YunaNexusCore/yunanexus-user') {
                            sh 'docker build -t yunanexus-user:${BUILD_TAG} -t yunanexus-user:latest .'
                        }
                    }
                }
                stage('File') {
                    steps {
                        dir('YunaNexusCore/yunanexus-file') {
                            sh 'docker build -t yunanexus-file:${BUILD_TAG} -t yunanexus-file:latest .'
                        }
                    }
                }
                stage('Frontend') {
                    steps {
                        dir('YunaNexusWeb') {
                            sh 'docker build -t yunanexus-frontend:${BUILD_TAG} -t yunanexus-frontend:latest .'
                        }
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    WORKSPACE_DIR="$(pwd)"
                    if [ ! -f "${PROJECT_DIR}/docker/.env" ]; then
                        echo "ERROR: ${PROJECT_DIR}/docker/.env not found."
                        echo "Create it on the server: cp ${PROJECT_DIR}/docker/.env.example ${PROJECT_DIR}/docker/.env"
                        exit 1
                    fi
                    echo "Workspace: ${WORKSPACE_DIR}"
                    echo "Syncing project to ${PROJECT_DIR}..."
                    mkdir -p ${PROJECT_DIR}/docker ${PROJECT_DIR}/YunaNexusCore ${PROJECT_DIR}/YunaNexusWeb ${PROJECT_DIR}/scripts ${PROJECT_DIR}/sql-schema
                    chmod -R u+w ${PROJECT_DIR} 2>/dev/null || true
                    rsync -rltD --no-owner --no-group --delete --exclude='.env' "${WORKSPACE_DIR}/docker/" ${PROJECT_DIR}/docker/ || true
                    rsync -rltD --no-owner --no-group --delete "${WORKSPACE_DIR}/YunaNexusCore/" ${PROJECT_DIR}/YunaNexusCore/ || true
                    rsync -rltD --no-owner --no-group --delete "${WORKSPACE_DIR}/YunaNexusWeb/" ${PROJECT_DIR}/YunaNexusWeb/ || true
                    rsync -rltD --no-owner --no-group --delete "${WORKSPACE_DIR}/scripts/" ${PROJECT_DIR}/scripts/ || true
                    rsync -rltD --no-owner --no-group --delete "${WORKSPACE_DIR}/sql-schema/" ${PROJECT_DIR}/sql-schema/ || true
                    if [ ! -f "${PROJECT_DIR}/docker/docker-compose.yml" ]; then
                        echo "ERROR: docker-compose.yml not synced!"
                        exit 1
                    fi
                    if [ ! -f "${PROJECT_DIR}/scripts/init-db.sh" ]; then
                        echo "ERROR: init-db.sh not synced!"
                        exit 1
                    fi
                    cd ${PROJECT_DIR}/docker
                    echo "Starting docker compose..."
                    docker compose down --remove-orphans 2>/dev/null || true
                    docker compose up -d --wait --wait-timeout 300
                '''
            }
        }

        stage('Health Check') {
            steps {
                script {
                    def services = [
                        [name: 'Gateway',  url: 'http://localhost:8000/'],
                        [name: 'Auth',     url: 'http://localhost:8200/'],
                        [name: 'User',     url: 'http://localhost:8100/'],
                        [name: 'File',     url: 'http://localhost:8300/'],
                        [name: 'Frontend', url: 'http://localhost:3000/']
                    ]
                    def failures = []
                    services.each { svc ->
                        def status = '000'
                        for (int i = 0; i < 6; i++) {
                            status = sh(
                                script: "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 10 ${svc.url} || echo '000'",
                                returnStdout: true
                            ).trim()
                            if (status == '200' || status == '302' || status == '401' || status == '404') {
                                break
                            }
                            echo "${svc.name}: attempt ${i+1}/6 -> ${status}, waiting..."
                            sleep(10)
                        }
                        if (status == '200' || status == '302' || status == '401' || status == '404') {
                            echo "${svc.name}: OK (${status})"
                        } else {
                            echo "${svc.name}: FAIL (${status})"
                            failures << svc.name
                        }
                    }
                    if (failures) {
                        unstable("Health check failed for: ${failures.join(', ')}")
                    }
                }
            }
        }
    }

    post {
        failure {
            echo "Pipeline failed. Check logs for details."
        }
    }
}
