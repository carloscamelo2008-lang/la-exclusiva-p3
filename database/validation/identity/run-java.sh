#!/usr/bin/env bash
# No ejecutado durante preparación. Requiere autorización para las escrituras de las pruebas.
set -euo pipefail
java -version 2>&1 | grep -q 'version "17\.'
java -version 2>&1 | grep -q 'Temurin'
cp /source/pom.xml /workspace/
cp -a /source/src /source/database /source/docs /workspace/
mkdir -p /root/.m2/repository
cp -a /seed-repository/. /root/.m2/repository/
export LAEXCLUSIVA_DB_PASSWORD
LAEXCLUSIVA_DB_PASSWORD="$(cat /run/secrets/lab_password)"
# Sin set -x, contraseñas por argumentos, settings del host ni red para descargar dependencias.
mvn -version
exec mvn -o \
  '-Dtest=UserRepositoryJdbcPostgresIntegrationTest,UserSecurityVersionPostgresIntegrationTest,AccountInstancePostgresIntegrationTest' \
  -Dlaexclusiva.test.postgres.users=true \
  -Dlaexclusiva.test.postgres.userSecurityVersion=true \
  -Dlaexclusiva.test.postgres.accountInstance=true test
