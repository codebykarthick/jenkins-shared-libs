/**
 * buildNextjs - Build and test Next.js applications
 *
 * Usage:
 *   buildNextjs(
 *       directory: '.',                  // optional, default: current directory
 *       nodeVersion: '20',               // optional, default: '20'
 *       packageManager: 'npm',           // optional: 'npm', 'yarn', 'pnpm'
 *       runTests: true,                  // optional, default: true
 *       runLint: true,                   // optional, default: true
 *       buildCommand: 'build',           // optional, default: 'build'
 *       testCommand: 'test',             // optional, default: 'test'
 *       installArgs: '',                 // optional, additional install args
 *       envVars: [:],                    // optional, environment variables
 *       useDocker: true,                 // optional, default: true - run in Docker container
 *       dockerImage: '',                 // optional, custom Docker image (default: node:<version>-alpine)
 *       dockerArgs: ''                   // optional, additional docker run arguments
 *   )
 */

def call(Map config = [:]) {
    // Set defaults
    def directory = config.directory ?: '.'
    def nodeVersion = config.nodeVersion ?: '20'
    def packageManager = config.packageManager ?: detectPackageManager(directory)
    def runTests = config.runTests != false
    def runLint = config.runLint != false
    def buildCommand = config.buildCommand ?: 'build'
    def testCommand = config.testCommand ?: 'test'
    def installArgs = config.installArgs ?: ''
    def envVars = config.envVars ?: [:]
    def useDocker = config.useDocker != false
    def dockerImage = config.dockerImage ?: "node:${nodeVersion}-alpine"
    def dockerArgs = config.dockerArgs ?: ''

    echo "Building Next.js application"
    echo "Directory: ${directory}"
    echo "Node version: ${nodeVersion}"
    echo "Package manager: ${packageManager}"
    echo "Using Docker: ${useDocker}"

    dir(directory) {
        if (useDocker) {
            echo "Running build in Docker container: ${dockerImage}"
            runInDocker(dockerImage, dockerArgs, packageManager, runLint, runTests, buildCommand, testCommand, installArgs, envVars)
        } else {
            runOnController(nodeVersion, packageManager, runLint, runTests, buildCommand, testCommand, installArgs, envVars)
        }
    }

    echo "Next.js build completed successfully"
}

/**
 * Run build steps inside a Docker container
 */
private void runInDocker(String dockerImage, String dockerArgs, String packageManager, boolean runLint, boolean runTests, String buildCommand, String testCommand, String installArgs, Map envVars) {
    def envList = envVars.collect { k, v -> "${k}=${v}" }

    docker.image(dockerImage).inside(dockerArgs) {
        withEnv(envList) {
            // Install pnpm if needed (not included in node image by default)
            if (packageManager == 'pnpm') {
                sh 'npm install -g pnpm'
            }

            // Install dependencies
            stage('Install Dependencies') {
                echo "Installing dependencies with ${packageManager}..."
                switch (packageManager) {
                    case 'yarn':
                        sh "yarn install ${installArgs}"
                        break
                    case 'pnpm':
                        sh "pnpm install ${installArgs}"
                        break
                    default:
                        sh "npm ci ${installArgs}"
                        break
                }
            }

            // Run linting
            if (runLint) {
                stage('Lint') {
                    echo "Running linter..."
                    try {
                        runPackageScript(packageManager, 'lint')
                    } catch (Exception e) {
                        echo "Warning: Linting failed or lint script not found"
                    }
                }
            }

            // Run tests
            if (runTests) {
                stage('Test') {
                    echo "Running tests..."
                    try {
                        runPackageScript(packageManager, testCommand)
                    } catch (Exception e) {
                        echo "Warning: Tests failed or test script not found"
                        throw e
                    }
                }
            }

            // Build the application
            stage('Build') {
                echo "Building Next.js application..."
                runPackageScript(packageManager, buildCommand)
            }
        }
    }
}

/**
 * Run build steps on the Jenkins controller (legacy mode)
 */
private void runOnController(String nodeVersion, String packageManager, boolean runLint, boolean runTests, String buildCommand, String testCommand, String installArgs, Map envVars) {
    // Set up Node.js environment using Jenkins tool
    def nodeHome = tool name: "NodeJS-${nodeVersion}", type: 'nodejs'

    withEnv(["PATH+NODE=${nodeHome}/bin"] + envVars.collect { k, v -> "${k}=${v}" }) {
        // Install dependencies
        stage('Install Dependencies') {
            echo "Installing dependencies with ${packageManager}..."
            switch (packageManager) {
                case 'yarn':
                    sh "yarn install ${installArgs}"
                    break
                case 'pnpm':
                    sh "pnpm install ${installArgs}"
                    break
                default:
                    sh "npm ci ${installArgs}"
                    break
            }
        }

        // Run linting
        if (runLint) {
            stage('Lint') {
                echo "Running linter..."
                try {
                    runPackageScript(packageManager, 'lint')
                } catch (Exception e) {
                    echo "Warning: Linting failed or lint script not found"
                }
            }
        }

        // Run tests
        if (runTests) {
            stage('Test') {
                echo "Running tests..."
                try {
                    runPackageScript(packageManager, testCommand)
                } catch (Exception e) {
                    echo "Warning: Tests failed or test script not found"
                    throw e
                }
            }
        }

        // Build the application
        stage('Build') {
            echo "Building Next.js application..."
            runPackageScript(packageManager, buildCommand)
        }
    }
}

/**
 * Detect package manager based on lock files
 */
private String detectPackageManager(String directory) {
    if (fileExists("${directory}/pnpm-lock.yaml")) {
        return 'pnpm'
    } else if (fileExists("${directory}/yarn.lock")) {
        return 'yarn'
    }
    return 'npm'
}

/**
 * Run a package.json script with the appropriate package manager
 */
private void runPackageScript(String packageManager, String script) {
    switch (packageManager) {
        case 'yarn':
            sh "yarn ${script}"
            break
        case 'pnpm':
            sh "pnpm run ${script}"
            break
        default:
            sh "npm run ${script}"
            break
    }
}
