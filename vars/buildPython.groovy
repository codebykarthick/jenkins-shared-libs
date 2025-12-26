/**
 * buildPython - Build and test Python applications
 *
 * Usage:
 *   buildPython(
 *       directory: '.',                  // optional, default: current directory
 *       pythonVersion: '3.11',           // optional, default: '3.11'
 *       useVenv: true,                   // optional, default: true (ignored when useDocker: true)
 *       runTests: true,                  // optional, default: true
 *       runLint: true,                   // optional, default: true
 *       testFramework: 'pytest',         // optional: 'pytest', 'unittest'
 *       testArgs: '',                    // optional, additional test args
 *       requirementsFile: 'requirements.txt',  // optional
 *       envVars: [:],                    // optional, environment variables
 *       useDocker: true,                 // optional, default: true - run in Docker container
 *       dockerImage: '',                 // optional, custom Docker image (default: python:<version>-slim)
 *       dockerArgs: ''                   // optional, additional docker run arguments
 *   )
 */

def call(Map config = [:]) {
    // Set defaults
    def directory = config.directory ?: '.'
    def pythonVersion = config.pythonVersion ?: '3.11'
    def useVenv = config.useVenv != false
    def runTests = config.runTests != false
    def runLint = config.runLint != false
    def testFramework = config.testFramework ?: 'pytest'
    def testArgs = config.testArgs ?: ''
    def requirementsFile = config.requirementsFile ?: detectRequirementsFile(directory)
    def envVars = config.envVars ?: [:]
    def useDocker = config.useDocker != false
    def dockerImage = config.dockerImage ?: "python:${pythonVersion}-slim"
    def dockerArgs = config.dockerArgs ?: ''

    echo "Building Python application"
    echo "Directory: ${directory}"
    echo "Python version: ${pythonVersion}"
    echo "Using Docker: ${useDocker}"
    echo "Test framework: ${testFramework}"

    dir(directory) {
        if (useDocker) {
            echo "Running build in Docker container: ${dockerImage}"
            runInDocker(dockerImage, dockerArgs, runLint, runTests, testFramework, testArgs, requirementsFile, envVars)
        } else {
            echo "Using virtualenv: ${useVenv}"
            runOnController(pythonVersion, useVenv, runLint, runTests, testFramework, testArgs, requirementsFile, envVars)
        }
    }

    echo "Python build completed successfully"
}

/**
 * Run build steps inside a Docker container
 */
private void runInDocker(String dockerImage, String dockerArgs, boolean runLint, boolean runTests, String testFramework, String testArgs, String requirementsFile, Map envVars) {
    def envList = envVars.collect { k, v -> "${k}=${v}" }

    docker.image(dockerImage).inside(dockerArgs) {
        withEnv(envList) {
            // Install dependencies
            stage('Install Dependencies') {
                echo "Installing dependencies..."

                // Upgrade pip first
                sh 'pip install --upgrade pip'

                // Install from requirements file if it exists
                if (requirementsFile && fileExists(requirementsFile)) {
                    sh "pip install -r ${requirementsFile}"
                }

                // Install from pyproject.toml if it exists
                if (fileExists('pyproject.toml')) {
                    sh 'pip install -e .'
                }

                // Install test dependencies
                if (runTests) {
                    sh 'pip install pytest pytest-cov'
                }

                // Install lint dependencies
                if (runLint) {
                    sh 'pip install flake8 black'
                }
            }

            // Run linting
            if (runLint) {
                stage('Lint') {
                    echo "Running linter..."
                    try {
                        sh 'flake8 . --count --select=E9,F63,F7,F82 --show-source --statistics'
                        sh 'black --check . || true'
                    } catch (Exception e) {
                        echo "Warning: Linting issues found"
                    }
                }
            }

            // Run tests
            if (runTests) {
                stage('Test') {
                    echo "Running tests with ${testFramework}..."
                    try {
                        switch (testFramework) {
                            case 'unittest':
                                sh "python -m unittest discover ${testArgs}"
                                break
                            default:
                                sh "pytest ${testArgs} --junitxml=test-results.xml --cov=. --cov-report=xml"
                                break
                        }
                    } catch (Exception e) {
                        echo "Tests failed"
                        throw e
                    } finally {
                        // Publish test results if available
                        if (fileExists('test-results.xml')) {
                            junit 'test-results.xml'
                        }
                    }
                }
            }

            // Build package if setup.py or pyproject.toml exists
            if (fileExists('setup.py') || fileExists('pyproject.toml')) {
                stage('Build Package') {
                    echo "Building Python package..."
                    sh 'pip install build'
                    sh 'python -m build'
                }
            }
        }
    }
}

/**
 * Run build steps on the Jenkins controller (legacy mode)
 */
private void runOnController(String pythonVersion, boolean useVenv, boolean runLint, boolean runTests, String testFramework, String testArgs, String requirementsFile, Map envVars) {
    def pythonCmd = "python${pythonVersion}"
    def pipCmd = "pip"
    def activateVenv = ""

    withEnv(envVars.collect { k, v -> "${k}=${v}" }) {
        // Set up virtual environment
        if (useVenv) {
            stage('Setup Virtual Environment') {
                echo "Creating virtual environment..."
                sh "${pythonCmd} -m venv .venv"
                activateVenv = ". .venv/bin/activate && "
                pipCmd = ".venv/bin/pip"
            }
        }

        // Install dependencies
        stage('Install Dependencies') {
            echo "Installing dependencies..."

            // Upgrade pip first
            sh "${activateVenv}${pipCmd} install --upgrade pip"

            // Install from requirements file if it exists
            if (requirementsFile && fileExists(requirementsFile)) {
                sh "${activateVenv}${pipCmd} install -r ${requirementsFile}"
            }

            // Install from pyproject.toml if it exists
            if (fileExists('pyproject.toml')) {
                sh "${activateVenv}${pipCmd} install -e ."
            }

            // Install test dependencies
            if (runTests) {
                sh "${activateVenv}${pipCmd} install pytest pytest-cov"
            }

            // Install lint dependencies
            if (runLint) {
                sh "${activateVenv}${pipCmd} install flake8 black"
            }
        }

        // Run linting
        if (runLint) {
            stage('Lint') {
                echo "Running linter..."
                try {
                    sh "${activateVenv}flake8 . --count --select=E9,F63,F7,F82 --show-source --statistics"
                    sh "${activateVenv}black --check . || true"
                } catch (Exception e) {
                    echo "Warning: Linting issues found"
                }
            }
        }

        // Run tests
        if (runTests) {
            stage('Test') {
                echo "Running tests with ${testFramework}..."
                try {
                    switch (testFramework) {
                        case 'unittest':
                            sh "${activateVenv}python -m unittest discover ${testArgs}"
                            break
                        default:
                            sh "${activateVenv}pytest ${testArgs} --junitxml=test-results.xml --cov=. --cov-report=xml"
                            break
                    }
                } catch (Exception e) {
                    echo "Tests failed"
                    throw e
                } finally {
                    // Publish test results if available
                    if (fileExists('test-results.xml')) {
                        junit 'test-results.xml'
                    }
                }
            }
        }

        // Build package if setup.py or pyproject.toml exists
        if (fileExists('setup.py') || fileExists('pyproject.toml')) {
            stage('Build Package') {
                echo "Building Python package..."
                sh "${activateVenv}${pipCmd} install build"
                sh "${activateVenv}python -m build"
            }
        }
    }
}

/**
 * Detect requirements file
 */
private String detectRequirementsFile(String directory) {
    def possibleFiles = ['requirements.txt', 'requirements/base.txt', 'requirements/prod.txt']
    for (file in possibleFiles) {
        if (fileExists("${directory}/${file}")) {
            return file
        }
    }
    return 'requirements.txt'
}
