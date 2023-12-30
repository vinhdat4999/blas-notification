mvn clean verify sonar:sonar \
  -Dsonar.projectKey=blas-email \
  -Dsonar.projectName='blas-email' \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=sqp_de6f8ced2c35ab4fdbc9c2e9e2b409708e22e58b
