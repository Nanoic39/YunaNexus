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
                        mvn clean package -DskipTests -P prod
                        echo "=== Verifying fat JARs ==="
                        for module in yunanexus-gateway yunanexus-auth yunanexus-user yunanexus-file; do
                            jar_file="${module}/target/${module}-1.0.0.jar"
                            if [ -f "$jar_file" ]; then
                                size=$(stat -c%s "$jar_file" 2>/dev/null || echo 0)
                                if [ "$size" -lt 1000000 ]; then
                                    echo "WARNING: $jar_file is only ${size} bytes - NOT a fat JAR!"
                                    unzip -p "$jar_file" META-INF/MANIFEST.MF 2>/dev/null | head -10
                                else
                                    echo "OK: $jar_file is ${size} bytes (fat JAR)"
                                fi
                            else
                                echo "ERROR: $jar_file not found!"
                            fi
                        done
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
                    docker pull node:22-alpine
                    docker pull python:3-slim
                '''
            }
        }

        stage('Build Frontend Image') {
            steps {
                dir('YunaNexusWeb') {
                    sh 'docker build -t yunanexus-frontend:latest .'
                }
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    WORKSPACE_DIR="$(pwd)"
                    if [ ! -f "${PROJECT_DIR}/docker/.env" ]; then
                        echo "WARNING: ${PROJECT_DIR}/docker/.env not found."
                        echo "Copy .env.example and fill in real values: cp ${PROJECT_DIR}/docker/.env.example ${PROJECT_DIR}/docker/.env"
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
                    chmod +x ${PROJECT_DIR}/scripts/*.sh 2>/dev/null || true

                    cd ${PROJECT_DIR}/docker
                    echo "Starting infrastructure containers..."
                    docker compose down --remove-orphans 2>/dev/null || true
                    docker compose up -d

                    echo "Starting Spring Boot services..."
                    cd ${PROJECT_DIR}
                    bash scripts/services.sh restart
                '''
            }
        }

        stage('Wait for Startup') {
            steps {
                echo 'Waiting 60s for all services to finish cold start...'
                sleep(60)
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
