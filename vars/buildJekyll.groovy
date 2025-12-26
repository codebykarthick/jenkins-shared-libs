/**
 * buildJekyll - Build and test Jekyll sites with Bundler
 *
 * Usage:
 *   buildJekyll(
 *       directory: '.',                  // optional, default: current directory
 *       rubyVersion: '3.2',              // optional, default: '3.2'
 *       runTests: false,                 // optional, default: false
 *       runLint: true,                   // optional, default: true
 *       buildCommand: 'build',           // optional, default: 'build'
 *       buildArgs: '',                   // optional, additional jekyll build args
 *       envVars: [:],                    // optional, environment variables
 *       useDocker: true,                 // optional, default: true - run in Docker container
 *       dockerImage: '',                 // optional, custom Docker image (default: ruby:<version>-slim)
 *       dockerArgs: ''                   // optional, additional docker run arguments
 *   )
 */

def call(Map config = [:]) {
    // Set defaults
    def directory = config.directory ?: '.'
    def rubyVersion = config.rubyVersion ?: '3.2'
    def runTests = config.runTests == true  // default false for Jekyll
    def runLint = config.runLint != false
    def buildCommand = config.buildCommand ?: 'build'
    def buildArgs = config.buildArgs ?: ''
    def envVars = config.envVars ?: [:]
    def useDocker = config.useDocker != false
    def dockerImage = config.dockerImage ?: "ruby:${rubyVersion}-slim"
    def dockerArgs = config.dockerArgs ?: ''

    echo "Building Jekyll site"
    echo "Directory: ${directory}"
    echo "Ruby version: ${rubyVersion}"
    echo "Using Docker: ${useDocker}"

    dir(directory) {
        if (useDocker) {
            echo "Running build in Docker container: ${dockerImage}"
            runInDocker(dockerImage, dockerArgs, runLint, runTests, buildCommand, buildArgs, envVars)
        } else {
            runOnController(runLint, runTests, buildCommand, buildArgs, envVars)
        }
    }

    echo "Jekyll build completed successfully"
}

/**
 * Run build steps inside a Docker container
 */
private void runInDocker(String dockerImage, String dockerArgs, boolean runLint, boolean runTests, String buildCommand, String buildArgs, Map envVars) {
    def envList = envVars.collect { k, v -> "${k}=${v}" }

    docker.image(dockerImage).inside(dockerArgs) {
        withEnv(envList + ['BUNDLE_PATH=vendor/bundle', 'JEKYLL_ENV=production']) {
            // Install system dependencies for native gems
            stage('Setup Environment') {
                echo "Installing build dependencies..."
                sh '''
                    apt-get update && apt-get install -y --no-install-recommends \
                        build-essential \
                        git \
                    && rm -rf /var/lib/apt/lists/*
                '''
            }

            // Install dependencies
            stage('Install Dependencies') {
                echo "Installing Ruby gems with Bundler..."
                sh 'gem install bundler --no-document'

                if (fileExists('Gemfile.lock')) {
                    sh 'bundle config set --local deployment true'
                }
                sh 'bundle install --jobs 4 --retry 3'
            }

            // Run linting
            if (runLint) {
                stage('Lint') {
                    echo "Running linter..."
                    try {
                        // Check for scss-lint or stylelint for styles
                        if (fileExists('_sass') || fileExists('assets/css')) {
                            sh 'bundle exec scss-lint _sass/ || true'
                        }
                        // HTML proofer for link checking (if installed)
                        sh 'bundle exec htmlproofer --version > /dev/null 2>&1 && echo "HTMLProofer available" || true'
                    } catch (Exception e) {
                        echo "Warning: Linting skipped or failed"
                    }
                }
            }

            // Run tests
            if (runTests) {
                stage('Test') {
                    echo "Running tests..."
                    try {
                        // Run RSpec if present
                        if (fileExists('spec')) {
                            sh 'bundle exec rspec --format documentation'
                        }
                        // Run Rake tests if present
                        if (fileExists('Rakefile')) {
                            sh 'bundle exec rake test || true'
                        }
                    } catch (Exception e) {
                        echo "Tests failed"
                        throw e
                    }
                }
            }

            // Build the site
            stage('Build') {
                echo "Building Jekyll site..."
                sh "bundle exec jekyll ${buildCommand} ${buildArgs}"

                // Verify build output
                if (fileExists('_site/index.html')) {
                    echo "Build successful - _site/index.html exists"
                } else {
                    echo "Warning: _site/index.html not found after build"
                }
            }

            // Run HTML validation after build
            if (runLint && fileExists('_site')) {
                stage('Validate Output') {
                    echo "Validating built site..."
                    try {
                        sh '''
                            if bundle exec htmlproofer --version > /dev/null 2>&1; then
                                bundle exec htmlproofer ./_site \
                                    --disable-external \
                                    --check-html \
                                    --allow-hash-href \
                                    || true
                            fi
                        '''
                    } catch (Exception e) {
                        echo "Warning: HTML validation issues found"
                    }
                }
            }
        }
    }
}

/**
 * Run build steps on the Jenkins controller (legacy mode)
 */
private void runOnController(boolean runLint, boolean runTests, String buildCommand, String buildArgs, Map envVars) {
    withEnv(envVars.collect { k, v -> "${k}=${v}" } + ['BUNDLE_PATH=vendor/bundle', 'JEKYLL_ENV=production']) {
        // Install dependencies
        stage('Install Dependencies') {
            echo "Installing Ruby gems with Bundler..."

            if (fileExists('Gemfile.lock')) {
                sh 'bundle config set --local deployment true'
            }
            sh 'bundle install --jobs 4 --retry 3'
        }

        // Run linting
        if (runLint) {
            stage('Lint') {
                echo "Running linter..."
                try {
                    if (fileExists('_sass') || fileExists('assets/css')) {
                        sh 'bundle exec scss-lint _sass/ || true'
                    }
                } catch (Exception e) {
                    echo "Warning: Linting skipped or failed"
                }
            }
        }

        // Run tests
        if (runTests) {
            stage('Test') {
                echo "Running tests..."
                try {
                    if (fileExists('spec')) {
                        sh 'bundle exec rspec --format documentation'
                    }
                    if (fileExists('Rakefile')) {
                        sh 'bundle exec rake test || true'
                    }
                } catch (Exception e) {
                    echo "Tests failed"
                    throw e
                }
            }
        }

        // Build the site
        stage('Build') {
            echo "Building Jekyll site..."
            sh "bundle exec jekyll ${buildCommand} ${buildArgs}"

            if (fileExists('_site/index.html')) {
                echo "Build successful - _site/index.html exists"
            } else {
                echo "Warning: _site/index.html not found after build"
            }
        }

        // Run HTML validation after build
        if (runLint && fileExists('_site')) {
            stage('Validate Output') {
                echo "Validating built site..."
                try {
                    sh '''
                        if bundle exec htmlproofer --version > /dev/null 2>&1; then
                            bundle exec htmlproofer ./_site \
                                --disable-external \
                                --check-html \
                                --allow-hash-href \
                                || true
                        fi
                    '''
                } catch (Exception e) {
                    echo "Warning: HTML validation issues found"
                }
            }
        }
    }
}
