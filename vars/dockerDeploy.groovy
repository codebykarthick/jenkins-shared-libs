/**
 * dockerDeploy - Deploy Docker containers on the host system
 *
 * Usage:
 *   // Deploy a single container
 *   dockerDeploy(
 *       imageName: 'my-app:latest',
 *       containerName: 'my-app',
 *       ports: ['8080:80', '443:443'],   // optional, host:container
 *       volumes: ['/host/path:/container/path'],  // optional
 *       envVars: ['NODE_ENV=production'],  // optional
 *       envFile: '.env',                 // optional
 *       network: 'my-network',           // optional
 *       restart: 'unless-stopped',       // optional, default: 'unless-stopped'
 *       healthCheck: true,               // optional, default: true
 *       removeExisting: true             // optional, default: true
 *   )
 *
 *   // Deploy using docker-compose
 *   dockerDeploy(
 *       composeFile: 'docker-compose.yaml',
 *       projectName: 'my-project',       // optional
 *       services: ['app'],               // optional, deploy specific services
 *       pull: true,                      // optional, pull latest images
 *       recreate: true                   // optional, force recreate containers
 *   )
 */

def call(Map config = [:]) {
    // Determine deploy mode: compose or single container
    if (config.composeFile) {
        return deployWithCompose(config)
    } else {
        return deployContainer(config)
    }
}

/**
 * Deploy a single container
 */
private void deployContainer(Map config) {
    // Validate required parameters
    if (!config.imageName) {
        error "dockerDeploy: 'imageName' parameter is required"
    }
    if (!config.containerName) {
        error "dockerDeploy: 'containerName' parameter is required"
    }

    // Set defaults
    def imageName = config.imageName
    def containerName = config.containerName
    def ports = config.ports ?: []
    def volumes = config.volumes ?: []
    def envVars = config.envVars ?: []
    def envFile = config.envFile ?: null
    def network = config.network ?: null
    def restart = config.restart ?: 'unless-stopped'
    def healthCheck = config.healthCheck != false
    def removeExisting = config.removeExisting != false

    echo "Deploying container: ${containerName}"
    echo "Image: ${imageName}"

    stage('Deploy Container') {
        // Stop and remove existing container if requested
        if (removeExisting) {
            echo "Stopping existing container if running..."
            sh """
                docker stop ${containerName} 2>/dev/null || true
                docker rm ${containerName} 2>/dev/null || true
            """
        }

        // Build docker run command
        def runCmd = "docker run -d"

        // Container name
        runCmd += " --name ${containerName}"

        // Restart policy
        runCmd += " --restart ${restart}"

        // Port mappings
        ports.each { port ->
            runCmd += " -p ${port}"
        }

        // Volume mappings
        volumes.each { volume ->
            runCmd += " -v ${volume}"
        }

        // Environment variables
        envVars.each { env ->
            runCmd += " -e ${env}"
        }

        // Environment file
        if (envFile && fileExists(envFile)) {
            runCmd += " --env-file ${envFile}"
        }

        // Network
        if (network) {
            // Create network if it doesn't exist
            sh "docker network create ${network} 2>/dev/null || true"
            runCmd += " --network ${network}"
        }

        // Add image name
        runCmd += " ${imageName}"

        // Run the container
        echo "Starting container..."
        sh runCmd

        // Health check
        if (healthCheck) {
            echo "Waiting for container to be healthy..."
            def maxRetries = 30
            def retryCount = 0
            def isHealthy = false

            while (retryCount < maxRetries && !isHealthy) {
                sleep(time: 2, unit: 'SECONDS')
                def status = sh(
                    script: "docker inspect --format='{{.State.Status}}' ${containerName}",
                    returnStdout: true
                ).trim()

                if (status == 'running') {
                    isHealthy = true
                    echo "Container is running"
                } else if (status == 'exited') {
                    error "Container exited unexpectedly. Check logs with: docker logs ${containerName}"
                }
                retryCount++
            }

            if (!isHealthy) {
                error "Container failed to start within timeout"
            }
        }
    }

    echo "Container ${containerName} deployed successfully"
}

/**
 * Deploy using docker-compose
 */
private void deployWithCompose(Map config) {
    def composeFile = config.composeFile
    def projectName = config.projectName ?: null
    def services = config.services ?: []
    def pull = config.pull ?: false
    def recreate = config.recreate ?: false

    if (!fileExists(composeFile)) {
        error "dockerDeploy: Compose file '${composeFile}' not found"
    }

    echo "Deploying with docker-compose: ${composeFile}"

    stage('Deploy with Compose') {
        // Build base command
        def composeCmd = "docker compose -f ${composeFile}"

        if (projectName) {
            composeCmd += " -p ${projectName}"
        }

        // Pull latest images if requested
        if (pull) {
            echo "Pulling latest images..."
            def pullCmd = "${composeCmd} pull"
            if (services) {
                pullCmd += " ${services.join(' ')}"
            }
            sh pullCmd
        }

        // Deploy
        def upCmd = "${composeCmd} up -d"

        if (recreate) {
            upCmd += " --force-recreate"
        }

        if (services) {
            upCmd += " ${services.join(' ')}"
        }

        echo "Starting services..."
        sh upCmd

        // Show status
        sh "${composeCmd} ps"
    }

    echo "Docker compose deployment completed"
}

/**
 * Utility: Stop a running container
 */
def stopContainer(String containerName) {
    echo "Stopping container: ${containerName}"
    sh "docker stop ${containerName} 2>/dev/null || true"
}

/**
 * Utility: Remove a container
 */
def removeContainer(String containerName, Boolean force = false) {
    echo "Removing container: ${containerName}"
    def cmd = force ? "docker rm -f ${containerName}" : "docker rm ${containerName}"
    sh "${cmd} 2>/dev/null || true"
}

/**
 * Utility: Get container logs
 */
def getLogs(String containerName, Integer lines = 100) {
    return sh(
        script: "docker logs --tail ${lines} ${containerName}",
        returnStdout: true
    ).trim()
}

/**
 * Utility: Cleanup old images
 */
def cleanupImages(String imageName = null, Boolean dangling = true) {
    echo "Cleaning up Docker images..."
    if (dangling) {
        sh "docker image prune -f"
    }
    if (imageName) {
        // Remove old versions of specific image
        sh """
            docker images ${imageName} --format '{{.ID}}' | tail -n +4 | xargs -r docker rmi -f || true
        """
    }
}
