#!/usr/bin/env bash
# No ejecutado durante preparación. Requiere autorización para las escrituras de las pruebas.
set -euo pipefail
case "${LAB_SOURCE_BRANCH:-}:${IDENTITY_LAB_AUTHORIZED:-}" in
  feature/daniel-identity-users:pr22|feature/identity-app-integration:pr24|feature/purchases-app-integration:purchases|feature/smart-reminders-security:collections|feature/jesus-customers-crud:pr29-customers) ;;
  *) echo "Rama sin autorización para el laboratorio" >&2; exit 2 ;;
esac
if [[ "${LAEXCLUSIVA_TEST_DB_URL:-}" != "jdbc:postgresql://127.0.0.1:5432/la_exclusiva_test" ||
      "${LAEXCLUSIVA_DB_USER:-}" != "identity_validation_owner" ]]; then
  echo "Destino JDBC del laboratorio inválido" >&2
  exit 2
fi
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
if [[ -d /evidence ]]; then
  trap 'cp -a /workspace/target/surefire-reports /evidence/ 2>/dev/null && chmod -R a+rwX /evidence/surefire-reports || true' EXIT
fi
case "${LAB_SCENARIO:-identity}" in
  identity)
    mvn -o \
      '-Dtest=UserRepositoryJdbcPostgresIntegrationTest,UserSecurityVersionPostgresIntegrationTest,AccountInstancePostgresIntegrationTest,ActiveSellerConcurrencyPostgresIntegrationTest' \
      -Dlaexclusiva.test.postgres.users=true \
      -Dlaexclusiva.test.postgres.userSecurityVersion=true \
      -Dlaexclusiva.test.postgres.accountInstance=true test
    ;;
  application)
    mvn -o \
      '-Dtest=IdentityApplicationPostgresIntegrationTest,PurchaseWorkflowPostgresIntegrationTest,UserRepositoryJdbcPostgresIntegrationTest,UserSecurityVersionPostgresIntegrationTest,AccountInstancePostgresIntegrationTest,ActiveSellerConcurrencyPostgresIntegrationTest,CustomerDebtQueryJdbcPostgresIntegrationTest,CustomerSellerJdbcPostgresIntegrationTest,CustomerCollectionPostgresIntegrationTest,FinancialOperationsPostgresIntegrationTest,InventoryRepositoryJdbcPostgresIntegrationTest,JdbcSalesWorkflowPostgresIntegrationTest,ProductRepositoryJdbcPostgresIntegrationTest,SaleRepositoryJdbcPostgresIntegrationTest,SaleRequestIdentityPostgresIntegrationTest,StockMovementRepositoryJdbcPostgresIntegrationTest,CustomerCrudPostgresIntegrationTest,CustomerDuplicatePostgresIntegrationTest' \
      -Dlaexclusiva.test.postgres.applicationServices=true \
      -Dlaexclusiva.test.postgres.users=true \
      -Dlaexclusiva.test.postgres.userSecurityVersion=true \
      -Dlaexclusiva.test.postgres.accountInstance=true \
      -Dlaexclusiva.test.postgres.collections=true \
      test
    ;;
  incomplete)
    mvn -o \
      '-Dtest=IncompleteIdentitySchemaPostgresIntegrationTest' \
      -Dlaexclusiva.test.postgres.incompleteIdentitySchema=true test
    ;;
  *)
    echo "Escenario de laboratorio inválido" >&2
    exit 2
    ;;
esac
