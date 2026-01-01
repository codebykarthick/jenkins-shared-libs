/**
 * dockerBuild - Build Docker images from Dockerfile or docker-compose.yaml
 *
 * Usage:
 *   // Build from Dockerfile
 *   dockerBuild(
 *       imageName: 'my-app',
 *       imageTag: 'latest',              // optional, default: 'latest'
 *       dockerfile: 'Dockerfile',        // optional, default: 'Dockerfile'
 *       context: '.',                    // optional, default: '.'
 *       buildArgs: ['ARG1=value'],       // optional
 *       noCache: false,                  // optional, default: false
 *       pull: true,                      // optional, default: true
 *       registry: '',                    // optional, registry URL
 *       push: false                      // optional, default: false
 *   )
 *
 *   // Build from docker-compose.yaml
 *   dockerBuild(
 *       composeFile: 'docker-compose.yaml',
 *       services: ['app', 'worker'],     // optional, build specific services
 *       noCache: false,
 *       pull: true
 *   )
 */

def call(Map config = [:]) {
    // Determine build mode: compose or dockerfile
    if (config.composeFile) {
        return buildWithCompose(config)
    } else {
        return buildWithDockerfile(config)
    }
}

/**
 * Build using Dockerfile
 */
private Map buildWithDockerfile(Map config) {
    // Validate required parameters
    if (!config.imageName) {
        error "dockerBuild: 'imageName' parameter is required when building from Dockerfile"
    }

    // Set defaults
    def imageName = config.imageName
    def imageTag = config.imageTag ?: 'latest'
    def dockerfile = config.dockerfile ?: 'Dockerfile'
    def context = config.context ?: '.'
    def buildArgs = config.buildArgs ?: []
    def noCache = config.noCache ?: false
    def pull = config.pull != false
    def registry = config.registry ?: ''
    def push = config.push ?: false

    // Construct full image name
    def fullImageName = registry ? "${registry}/${imageName}:${imageTag}" : "${imageName}:${imageTag}"

    echo "Building Docker image: ${fullImageName}"
    echo "Dockerfile: ${dockerfile}"
    echo "Context: ${context}"

    // Build the docker build command
    def buildCmd = "docker build"

    // Add dockerfile path
    buildCmd += " -f ${dockerfile}"

    // Add image tag
    buildCmd += " -t ${fullImageName}"

    // Add build args
    buildArgs.each { arg ->
        buildCmd += " --build-arg ${arg}"
    }

    // Add options
    if (noCache) {
        buildCmd += " --no-cache"
    }
    if (pull) {
        buildCmd += " --pull"
    }

    // Add context
    buildCmd += " ${context}"

    // Execute build on host Docker socket
    stage('Docker Build') {
        // Use host Docker socket (Jenkins runs in container, builds on host)
        sh """
            ${buildCmd}
        """
    }

    // Push if requested
    if (push && registry) {
        stage('Docker Push') {
            echo "Pushing image to registry..."
            sh "docker push ${fullImageName}"
        }
    }

    echo "Docker build completed: ${fullImageName}"
    return [imageName: imageName, imageTag: imageTag, fullImageName: fullImageName]
}

/**
 * Build using docker-compose
 */
private void buildWithCompose(Map config) {
    def composeFile = config.composeFile
    def services = config.services ?: []
    def noCache = config.noCache ?: false

    if (!fileExists(composeFile)) {
        error "dockerBuild: Compose file '${composeFile}' not found"
    }

    echo "Building with docker-compose: ${composeFile}"
    if (services) {
        echo "Services: ${services.join(', ')}"
    }

    // Build the docker-compose command
    def composeCmd = "docker compose -f ${composeFile} build"

    if (noCache) {
        composeCmd += " --no-cache"
    }

    // Add specific services if provided
    if (services) {
        composeCmd += " ${services.join(' ')}"
    }

    stage('Docker Compose Build') {
        sh composeCmd
    }

    echo "Docker compose build completed"
}

/**
 * Utility: Tag an existing image
 */
def tagImage(String sourceImage, String targetImage) {
    echo "Tagging ${sourceImage} as ${targetImage}"
    sh "docker tag ${sourceImage} ${targetImage}"
}

/**
 * Utility: Push an image to registry
 */
def pushImage(String imageName, String credentialsId = null) {
    if (credentialsId) {
        withCredentials([usernamePassword(credentialsId: credentialsId,
                         usernameVariable: 'DOCKER_USER',
                         passwordVariable: 'DOCKER_PASS')]) {
            sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
            sh "docker push ${imageName}"
        }
    } else {
        sh "docker push ${imageName}"
    }
}
