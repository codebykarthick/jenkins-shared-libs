# Jenkins Shared Libraries

A collection of reusable Jenkins pipeline functions for CI/CD operations including git operations, application builds, Docker image building, and deployment.

## Table of Contents

- [Installation](#installation)
- [Available Functions](#available-functions)
  - [gitClone](#gitclone)
  - [buildNextjs](#buildnextjs)
  - [buildPython](#buildpython)
  - [dockerBuild](#dockerbuild)
  - [dockerDeploy](#dockerdeploy)
- [Example Pipeline](#example-pipeline)
- [Prerequisites](#prerequisites)

## Installation

### Global Configuration (Recommended)

1. Navigate to **Manage Jenkins** > **Configure System** > **Global Pipeline Libraries**
2. Add a new library:
   - **Name:** `jenkins-shared-libs`
   - **Default version:** `main`
   - **Retrieval method:** Modern SCM
   - **Source Code Management:** Git
   - **Project Repository:** `<your-repo-url>`

### Per-Pipeline Configuration

Add to the top of your Jenkinsfile:

```groovy
@Library('jenkins-shared-libs') _
```

Or with a specific version/branch:

```groovy
@Library('jenkins-shared-libs@main') _
```

## Available Functions

### gitClone

Clone a Git repository with optional branch/tag/commit support.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `url` | String | Yes | - | Repository URL |
| `branch` | String | No | `main` | Branch, tag, or commit to checkout |
| `credentialsId` | String | No | - | Jenkins credentials ID for private repos |
| `directory` | String | No | repo name | Target directory |
| `shallow` | Boolean | No | `false` | Enable shallow clone |
| `depth` | Integer | No | `1` | Clone depth (when shallow=true) |

**Example:**

```groovy
@Library('jenkins-shared-libs') _

pipeline {
    agent any
    stages {
        stage('Clone') {
            steps {
                gitClone(
                    url: 'https://github.com/org/repo.git',
                    branch: 'develop',
                    credentialsId: 'github-token',
                    shallow: true
                )
            }
        }
    }
}
```

---

### buildNextjs

Build and test Next.js applications.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `directory` | String | No | `.` | Project directory |
| `nodeVersion` | String | No | `20` | Node.js version (must match Jenkins tool name) |
| `packageManager` | String | No | auto-detect | `npm`, `yarn`, or `pnpm` |
| `runTests` | Boolean | No | `true` | Run test suite |
| `runLint` | Boolean | No | `true` | Run linter |
| `buildCommand` | String | No | `build` | npm script for building |
| `testCommand` | String | No | `test` | npm script for testing |
| `installArgs` | String | No | - | Additional install arguments |
| `envVars` | Map | No | `[:]` | Environment variables |

**Example:**

```groovy
@Library('jenkins-shared-libs') _

pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                buildNextjs(
                    directory: 'frontend',
                    nodeVersion: '20',
                    packageManager: 'pnpm',
                    envVars: [
                        NEXT_PUBLIC_API_URL: 'https://api.example.com'
                    ]
                )
            }
        }
    }
}
```

---

### buildPython

Build and test Python applications.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `directory` | String | No | `.` | Project directory |
| `pythonVersion` | String | No | `3.11` | Python version |
| `useVenv` | Boolean | No | `true` | Create virtual environment |
| `runTests` | Boolean | No | `true` | Run test suite |
| `runLint` | Boolean | No | `true` | Run linter (flake8, black) |
| `testFramework` | String | No | `pytest` | `pytest` or `unittest` |
| `testArgs` | String | No | - | Additional test arguments |
| `requirementsFile` | String | No | auto-detect | Requirements file path |
| `envVars` | Map | No | `[:]` | Environment variables |

**Example:**

```groovy
@Library('jenkins-shared-libs') _

pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                buildPython(
                    directory: 'backend',
                    pythonVersion: '3.11',
                    testFramework: 'pytest',
                    testArgs: '-v --cov=src'
                )
            }
        }
    }
}
```

---

### dockerBuild

Build Docker images from Dockerfile or docker-compose.yaml.

**Parameters (Dockerfile mode):**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `imageName` | String | Yes | - | Image name |
| `imageTag` | String | No | `latest` | Image tag |
| `dockerfile` | String | No | `Dockerfile` | Dockerfile path |
| `context` | String | No | `.` | Build context |
| `buildArgs` | List | No | `[]` | Build arguments |
| `noCache` | Boolean | No | `false` | Disable cache |
| `pull` | Boolean | No | `true` | Pull base image |
| `registry` | String | No | - | Registry URL |
| `push` | Boolean | No | `false` | Push after build |

**Parameters (Compose mode):**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `composeFile` | String | Yes | - | docker-compose.yaml path |
| `services` | List | No | all | Services to build |
| `noCache` | Boolean | No | `false` | Disable cache |
| `pull` | Boolean | No | `true` | Pull base images |

**Example (Dockerfile):**

```groovy
@Library('jenkins-shared-libs') _

pipeline {
    agent any
    stages {
        stage('Build Image') {
            steps {
                script {
                    def image = dockerBuild(
                        imageName: 'my-app',
                        imageTag: "${env.BUILD_NUMBER}",
                        buildArgs: ["VERSION=${env.BUILD_NUMBER}"],
                        registry: 'registry.example.com'
                    )
                    echo "Built: ${image.fullImageName}"
                }
            }
        }
    }
}
```

**Example (docker-compose):**

```groovy
@Library('jenkins-shared-libs') _

pipeline {
    agent any
    stages {
        stage('Build Images') {
            steps {
                dockerBuild(
                    composeFile: 'docker-compose.yaml',
                    services: ['app', 'worker']
                )
            }
        }
    }
}
```

---

### dockerDeploy

Deploy Docker containers on the host system.

**Parameters (Single container):**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `imageName` | String | Yes | - | Image name with tag |
| `containerName` | String | Yes | - | Container name |
| `ports` | List | No | `[]` | Port mappings (host:container) |
| `volumes` | List | No | `[]` | Volume mappings |
| `envVars` | List | No | `[]` | Environment variables |
| `envFile` | String | No | - | Environment file path |
| `network` | String | No | - | Docker network name |
| `restart` | String | No | `unless-stopped` | Restart policy |
| `healthCheck` | Boolean | No | `true` | Wait for healthy status |
| `removeExisting` | Boolean | No | `true` | Remove existing container |

**Parameters (Compose mode):**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `composeFile` | String | Yes | - | docker-compose.yaml path |
| `projectName` | String | No | - | Compose project name |
| `services` | List | No | all | Services to deploy |
| `pull` | Boolean | No | `false` | Pull latest images |
| `recreate` | Boolean | No | `false` | Force recreate containers |

**Example (Single container):**

```groovy
@Library('jenkins-shared-libs') _

pipeline {
    agent any
    stages {
        stage('Deploy') {
            steps {
                dockerDeploy(
                    imageName: 'my-app:latest',
                    containerName: 'my-app-prod',
                    ports: ['80:3000'],
                    volumes: ['/data/app:/app/data'],
                    envVars: ['NODE_ENV=production'],
                    network: 'app-network'
                )
            }
        }
    }
}
```

**Example (docker-compose):**

```groovy
@Library('jenkins-shared-libs') _

pipeline {
    agent any
    stages {
        stage('Deploy') {
            steps {
                dockerDeploy(
                    composeFile: 'docker-compose.prod.yaml',
                    projectName: 'myapp',
                    pull: true,
                    recreate: true
                )
            }
        }
    }
}
```

---

## Example Pipeline

Complete CI/CD pipeline example:

```groovy
@Library('jenkins-shared-libs') _

pipeline {
    agent any

    environment {
        REGISTRY = 'registry.example.com'
        IMAGE_NAME = 'my-nextjs-app'
    }

    stages {
        stage('Clone') {
            steps {
                gitClone(
                    url: 'https://github.com/org/my-app.git',
                    branch: params.BRANCH ?: 'main',
                    credentialsId: 'github-token'
                )
            }
        }

        stage('Build & Test') {
            steps {
                buildNextjs(
                    directory: 'my-app',
                    nodeVersion: '20',
                    envVars: [
                        NEXT_PUBLIC_API_URL: 'https://api.example.com'
                    ]
                )
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    dockerBuild(
                        imageName: env.IMAGE_NAME,
                        imageTag: env.BUILD_NUMBER,
                        context: 'my-app',
                        registry: env.REGISTRY
                    )
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                dockerDeploy(
                    imageName: "${env.REGISTRY}/${env.IMAGE_NAME}:${env.BUILD_NUMBER}",
                    containerName: 'my-app-prod',
                    ports: ['3000:3000'],
                    network: 'web'
                )
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}
```

## Prerequisites

### Jenkins Configuration

1. **Node.js Plugin:** Install and configure Node.js tools matching your `nodeVersion` parameter (e.g., `NodeJS-20`)

2. **Docker:** Jenkins must have access to the Docker daemon. If Jenkins runs in a container, mount the Docker socket:
   ```yaml
   volumes:
     - /var/run/docker.sock:/var/run/docker.sock
   ```

3. **Python:** Ensure Python is available on the Jenkins agent for Python builds

### Required Jenkins Plugins

- [Git Plugin](https://plugins.jenkins.io/git/)
- [NodeJS Plugin](https://plugins.jenkins.io/nodejs/) (for Next.js builds)
- [Docker Pipeline Plugin](https://plugins.jenkins.io/docker-workflow/) (optional)
- [Pipeline Utility Steps](https://plugins.jenkins.io/pipeline-utility-steps/)

## Directory Structure

```
jenkins-shared-libs/
├── vars/
│   ├── gitClone.groovy      # Git clone operations
│   ├── buildNextjs.groovy   # Next.js build and test
│   ├── buildPython.groovy   # Python build and test
│   ├── dockerBuild.groovy   # Docker image building
│   └── dockerDeploy.groovy  # Docker deployment
├── src/
│   └── org/shared/          # Optional helper classes
├── resources/               # Non-Groovy resources
├── LICENSE
└── README.md
```

## License

See [LICENSE](LICENSE) file for details.
