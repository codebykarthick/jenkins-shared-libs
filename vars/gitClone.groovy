/**
 * gitClone - Clone a Git repository with optional branch/tag/commit support
 *
 * Usage:
 *   gitClone(
 *       url: 'https://github.com/org/repo.git',
 *       branch: 'main',                    // optional, default: 'main'
 *       credentialsId: 'git-credentials',  // optional, default: 'git-credentials'
 *       directory: 'repo',                 // optional, default: repo name
 *       shallow: true,                     // optional, default: false
 *       depth: 1                           // optional, used with shallow
 *   )
 */

def call(Map config = [:]) {
    // Validate required parameters
    if (!config.url) {
        error "gitClone: 'url' parameter is required"
    }

    // Set defaults
    def url = config.url
    def branch = config.branch ?: 'main'
    def credentialsId = config.credentialsId ?: 'git-credentials'
    def directory = config.directory ?: extractRepoName(url)
    def shallow = config.shallow ?: false
    def depth = config.depth ?: 1

    echo "Cloning repository: ${url}"
    echo "Branch/Tag: ${branch}"
    echo "Target directory: ${directory}"
    echo "Using credentialsId: ${credentialsId}"

    def cloneExtensions = []

    // Add shallow clone extension if requested
    if (shallow) {
        cloneExtensions.add([$class: 'CloneOption',
                             depth: depth,
                             noTags: false,
                             shallow: true])
    }

    // Perform the clone
    if (credentialsId) {
        checkout([
            $class: 'GitSCM',
            branches: [[name: "*/${branch}"]],
            extensions: cloneExtensions + [
                [$class: 'RelativeTargetDirectory', relativeTargetDir: directory],
                [$class: 'CleanBeforeCheckout']
            ],
            userRemoteConfigs: [[
                url: url,
                credentialsId: credentialsId
            ]]
        ])
    } else {
        checkout([
            $class: 'GitSCM',
            branches: [[name: "*/${branch}"]],
            extensions: cloneExtensions + [
                [$class: 'RelativeTargetDirectory', relativeTargetDir: directory],
                [$class: 'CleanBeforeCheckout']
            ],
            userRemoteConfigs: [[url: url]]
        ])
    }

    echo "Successfully cloned ${url} to ${directory}"
    return directory
}

/**
 * Extract repository name from URL
 */
private String extractRepoName(String url) {
    def repoName = url.tokenize('/')[-1]
    return repoName.replace('.git', '')
}
