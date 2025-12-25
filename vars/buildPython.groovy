/**
 * buildPython - Build and test Python applications
 *
 * Usage:
 *   buildPython(
 *       directory: '.',                  // optional, default: current directory
 *       pythonVersion: '3.11',           // optional, default: '3.11'
 *       useVenv: true,                   // optional, default: true
 *       runTests: true,                  // optional, default: true
 *       runLint: true,                   // optional, default: true
 *       testFramework: 'pytest',         // optional: 'pytest', 'unittest'
 *       testArgs: '',                    // optional, additional test args
 *       requirementsFile: 'requirements.txt',  // optional
 *       envVars: [:]                     // optional, environment variables
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

    echo "Building Python application"
    echo "Directory: ${directory}"
    echo "Python version: ${pythonVersion}"
    echo "Using virtualenv: ${useVenv}"
    echo "Test framework: ${testFramework}"

    dir(directory) {
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

    echo "Python build completed successfully"
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
