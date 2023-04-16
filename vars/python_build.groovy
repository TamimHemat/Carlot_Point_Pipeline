def call(dockerRepoName, imageName, portNum) {
    pipeline {
        agent any
        parameters {
            booleanParam(defaultValue: false, description: 'Deploy the App', name: 'DEPLOY')
        }

        stages {
            stage('Build') {
                steps {
                    sh 'pip install -r requirements.txt'
                }
            }
            stage('Python Lint') {
                steps {
                    sh 'pylint-fail-under --fail_under 5.0 *.py'
                }
            }
            stage('Test and Coverage') {
                steps {
                    script {
                        def test_reports_exist = fileExists 'test-reports'
                        if (test_reports_exist) {
                            sh 'rm test-reports/*.xml || true'
                        }
                        def api_reports_exist = fileExists 'api-test-reports'
                        if (api_reports_exist) {
                            sh 'rm api-test-reports/*.xml || true'
                        }
                    }
                    script {
                        def files = findFiles(glob: 'test*.py')
                        for (file in files) {
                            sh "coverage run --omit */site-packages/*,*/dist-packages/* ${file.path}"
                        }
                        sh "coverage report"
                    }
                    script {
                        def test_reports_exist = fileExists 'test-reports'
                        if (test_reports_exist) {
                            junit 'test-reports/*.xml'
                        }
                        def api_reports_exist = fileExists 'api-test-reports'
                        if (api_reports_exist) {
                            junit 'api-test-reports/*.xml'
                        }
                    }
                }
            }
            stage('Package') {
                when {
                    expression { env.GIT_BRANCH == 'origin/main' }
                }
                steps {
                    withCredentials([string(credentialsId: 'DockerHub', variable: 'TOKEN')]) {
                        sh "docker login -u 'tamimhemat' -p '$TOKEN' docker.io"
                        sh "docker build -t ${dockerRepoName}:latest --tag tamimhemat/${dockerRepoName}:${imageName} ."
                        sh "docker push tamimhemat/${dockerRepoName}:${imageName}"
                    }
                }
            }
            stage('Zip Artifacts') {
                steps {
                    sh 'zip app.zip *.py'
                }
                post {
                    always {
                        archiveArtifacts 'app.zip'
                    }
                }
            }
            stage('Deliver') {
                when {
                    expression { params.DEPLOY }
                }
                steps {
                    sh "docker stop ${dockerRepoName} || true && docker rm ${dockerRepoName} || true"
                    sh "docker run -d -p ${portNum + 1}:${portNum} --name ${dockerRepoName} ${dockerRepoName}:latest"
                }
            }
        }
    }
}
