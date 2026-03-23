pipeline {
    agent any

    environment {
        AWS_REGION      = 'us-east-1'
        AWS_ACCOUNT_ID  = '745791801485'
        ECR_REPO        = 'fgw-service'
        ECS_CLUSTER     = 'ecom-cluster'
        ECS_SERVICE     = 'fgw-service'
        IMAGE_TAG       = "${BUILD_NUMBER}"
        ECR_URL         = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    }

    stages {

        stage('Checkout') {
            steps {
                echo '--- Checking out code ---'
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo '--- Building JAR ---'
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                echo '--- Running Tests ---'
                sh 'mvn test'
            }
        }

        stage('Docker Build') {
            steps {
                echo '--- Building Docker Image ---'
                sh """
                    docker build -t ${ECR_REPO}:${IMAGE_TAG} .
                    docker tag ${ECR_REPO}:${IMAGE_TAG} \
                        ${ECR_URL}/${ECR_REPO}:${IMAGE_TAG}
                    docker tag ${ECR_REPO}:${IMAGE_TAG} \
                        ${ECR_URL}/${ECR_REPO}:latest
                """
            }
        }

        stage('Push to ECR') {
            steps {
                echo '--- Pushing to AWS ECR ---'
                sh """
                    aws ecr get-login-password \
                        --region ${AWS_REGION} | \
                    docker login \
                        --username AWS \
                        --password-stdin \
                        ${ECR_URL}

                    docker push ${ECR_URL}/${ECR_REPO}:${IMAGE_TAG}
                    docker push ${ECR_URL}/${ECR_REPO}:latest
                """
            }
        }

        stage('Deploy to ECS') {
            steps {
                echo '--- Deploying to AWS ECS ---'
                sh """
                    aws ecs update-service \
                        --region ${AWS_REGION} \
                        --cluster ${ECS_CLUSTER} \
                        --service ${ECS_SERVICE} \
                        --force-new-deployment
                """
            }
        }
    }

    post {
        success {
            echo '✅ FGW Service deployed successfully!'
        }
        failure {
            echo '❌ Pipeline failed — check logs above'
        }
    }
}