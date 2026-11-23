"""Laboratorio autorizado de identidad: ejecuta SQL solo mediante Docker exec verificado.

No tiene ruta de conexión a bases del host ni utiliza secretos del repositorio.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
LAB = ROOT / "database/validation/identity"
ENV = os.environ.copy()
AUTHORIZED_BRANCHES = {
    "feature/daniel-identity-users": "pr22",
    "feature/identity-app-integration": "pr24",
}
event = ENV.get("GITHUB_EVENT_NAME")
branch = ENV.get("GITHUB_HEAD_REF") if event == "pull_request" else ENV.get("GITHUB_REF_NAME")
expected_authorization = AUTHORIZED_BRANCHES.get(branch)
if (ENV.get("GITHUB_ACTIONS") != "true" or not expected_authorization
        or ENV.get("IDENTITY_LAB_AUTHORIZED") != expected_authorization):
    raise SystemExit("Solo el job efímero expresamente autorizado puede ejecutar este laboratorio.")
if event == "pull_request":
    event_file = Path(ENV["GITHUB_EVENT_PATH"])
    payload = json.loads(event_file.read_text(encoding="utf-8"))
    head = payload["pull_request"]["head"]
    if (head["repo"]["full_name"] != ENV["GITHUB_REPOSITORY"]
            or head["ref"] != branch or head["sha"] != ENV["LAB_SOURCE_HEAD"]):
        raise SystemExit("La referencia de origen debe ser el HEAD actual de un PR del mismo repositorio.")
if ENV.get("DOCKER_HOST") or ENV.get("DOCKER_CONTEXT"):
    raise SystemExit("No se permiten endpoints Docker alternativos.")
BASE = Path(ENV["LAB_ROOT"]).resolve()
if not BASE.is_relative_to(Path(ENV["RUNNER_TEMP"]).resolve()):
    raise SystemExit("LAB_ROOT debe estar dentro del temporal del runner.")
PROJECT = "identity-pg-" + ENV["GITHUB_RUN_ID"] + "-" + ENV["GITHUB_RUN_ATTEMPT"]
if not re.fullmatch(r"identity-pg-\d+-\d+", PROJECT):
    raise SystemExit("Proyecto Docker inválido.")
EVIDENCE = BASE / "evidence"
EVIDENCE.mkdir(parents=True, exist_ok=True)
PASSWORD = BASE / "password"
ENV.update(LAEXCLUSIVA_LAB_SECRET_FILE=str(PASSWORD), LAEXCLUSIVA_MAVEN_REPOSITORY=str(BASE / "m2/repository"), LAB_EVIDENCE=str(EVIDENCE))
COMPOSE = ["docker", "compose", "-p", PROJECT, "-f", str(LAB / "compose.yaml"), "-f", str(LAB / "compose.ci.yaml")]
secret = ""
verified = None
report = {"source_head": ENV["LAB_SOURCE_HEAD"], "cycles": [], "migrations": {}, "completed": False}


def clean_text(value):
    if secret:
        value = value.replace(secret, "[secret-redacted]")
    return re.sub(r"\$pbkdf2-sha256\$[^\s<>\"']+", "[hash-redacted]", value)


def run(args, check=True, timeout=600):
    result = subprocess.run(args, cwd=ROOT, env=ENV, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    result.stdout = clean_text(result.stdout)
    if check and result.returncode:
        print(result.stdout, flush=True)
        raise RuntimeError("Comando del laboratorio falló: " + args[0] + "; código " + str(result.returncode))
    return result


def compose(*args, **kwargs):
    return run(COMPOSE + list(args), **kwargs)


def save(name, value):
    (EVIDENCE / name).write_text(clean_text(value), encoding="utf-8")


def cleanup():
    global verified
    ids = run(["docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=" + PROJECT]).stdout.split()
    for container in ids:
        info = json.loads(run(["docker", "inspect", container]).stdout)[0]
        labels = info["Config"]["Labels"]
        assert labels.get("com.docker.compose.project") == PROJECT
        assert labels.get("com.docker.compose.service") in ("postgres", "java")
        run(["docker", "rm", "--force", container])
    networks = run(["docker", "network", "ls", "-q", "--filter", "label=com.docker.compose.project=" + PROJECT]).stdout.split()
    for network in networks:
        info = json.loads(run(["docker", "network", "inspect", network]).stdout)[0]
        assert info["Internal"] and info["Labels"].get("com.docker.compose.project") == PROJECT
        run(["docker", "network", "rm", network])
    assert not run(["docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=" + PROJECT]).stdout.strip()
    verified = None


def inspect_container(container, java=False):
    info = json.loads(run(["docker", "inspect", container]).stdout)[0]
    host = info["HostConfig"]
    assert not host.get("PortBindings") and not any((info["NetworkSettings"].get("Ports") or {}).values())
    assert host["NetworkMode"] != "host" and not host["Privileged"]
    assert all(mount["Type"] != "volume" for mount in info["Mounts"])
    assert all(not mount["RW"] or (java and mount["Destination"] == "/evidence")
               for mount in info["Mounts"] if mount["Type"] == "bind")
    assert "/workspace" in host["Tmpfs"] if java else "/var/lib/postgresql" in host["Tmpfs"]
    if java:
        assert host["NetworkMode"] == "container:" + verified
    else:
        networks = info["NetworkSettings"]["Networks"]
        assert len(networks) == 1
        for network in networks.values():
            assert json.loads(run(["docker", "network", "inspect", network["NetworkID"]]).stdout)[0]["Internal"]
    return {"container": container, "image_id": info["Image"], "ports": host["PortBindings"],
            "mounts": [{"type": m["Type"], "target": m["Destination"], "rw": m["RW"]} for m in info["Mounts"]],
            "network_mode": host["NetworkMode"], "tmpfs": host["Tmpfs"]}


def start(cycle):
    global verified
    config = json.loads(compose("config", "--format", "json").stdout)
    pg = config["services"]["postgres"]
    assert not pg.get("ports") and all(v["type"] == "bind" and v["read_only"] for v in pg["volumes"])
    assert config["networks"]["isolated"]["internal"]
    assert not compose("ps", "-aq").stdout.strip(), "No reutilizar un laboratorio existente"
    compose("up", "-d", "--wait", "postgres", timeout=120)
    verified = compose("ps", "-q", "postgres").stdout.strip()
    assert verified
    save(cycle + "-isolation.json", json.dumps(inspect_container(verified), indent=2))
    print("Aislamiento verificado: " + cycle, flush=True)


def psql(files=(), query=None, check=True):
    assert verified, "No hay contenedor verificado para SQL"
    inspect_container(verified)
    args = ["docker", "exec", verified, "psql", "-X", "-w", "-U", "identity_validation_owner", "-d", "la_exclusiva_test", "-v", "ON_ERROR_STOP=1"]
    args += ["-f", "/workspace/database/validation/identity/guard.psql"]
    for file in files:
        args += ["-f", "/workspace/" + file]
    if query:
        args += ["-At", "-c", query]
    return run(args, check=check, timeout=120)


def java_tests(cycle):
    scenario = "application" if cycle == "application" else "incomplete" if cycle == "incomplete" else "identity"
    ENV["LAB_SCENARIO"] = scenario
    compose("--profile", "java", "create", "--no-recreate", "java")
    java = compose("--profile", "java", "ps", "-aq", "java").stdout.strip()
    save(cycle + "-java-isolation.json", json.dumps(inspect_container(java, java=True), indent=2))
    output = run(["docker", "start", "--attach", java], check=False)
    save(cycle + "-maven.log", output.stdout)
    code = int(run(["docker", "inspect", "--format", "{{.State.ExitCode}}", java]).stdout)
    suites = list((EVIDENCE / "surefire-reports").glob("TEST-*.xml"))
    totals = {name: 0 for name in ("tests", "failures", "errors", "skipped")}
    for file in suites:
        file.write_text(clean_text(file.read_text()), encoding="utf-8")
        suite = ET.parse(file).getroot()
        for key in totals:
            totals[key] += int(suite.attrib[key])
    expected = {
        "identity": (4, {"tests": 26, "failures": 0, "errors": 0, "skipped": 0}),
        "application": (14, {"tests": 52, "failures": 0, "errors": 0, "skipped": 0}),
        "incomplete": (1, {"tests": 1, "failures": 0, "errors": 0, "skipped": 0}),
    }[scenario]
    assert (len(suites), totals) == expected, {"suites": len(suites), "totals": totals}
    assert code == 0 and "BUILD SUCCESS" in output.stdout
    (EVIDENCE / "surefire-reports").rename(EVIDENCE / (cycle + "-surefire"))
    print(cycle + " Java 17: " + json.dumps(totals), flush=True)
    report["cycles"].append({"cycle": cycle, "java": totals, "scenario": scenario})


def snapshot():
    return psql(query="SELECT id,account_instance_id,security_version FROM public.user_account ORDER BY id").stdout


def main():
    global secret
    if "--cleanup" in sys.argv:
        cleanup()
        PASSWORD.unlink(missing_ok=True)
        return
    assert not PASSWORD.exists()
    os.chmod(BASE, 0o700)
    secret = secrets.token_urlsafe(32)
    with open(os.open(PASSWORD, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o444), "w") as file:
        file.write(secret)
    (BASE / "m2").mkdir()
    for key in ("LAEXCLUSIVA_PG18_IMAGE", "LAEXCLUSIVA_JAVA17_IMAGE"):
        image = ENV[key]
        assert re.fullmatch(r"(?:postgres|maven):[^@]+@sha256:[0-9a-f]{64}", image)
        run(["docker", "pull", image])
        report[key] = image
        save(key + ".json", run(["docker", "image", "inspect", image]).stdout)
    # Preparación online de dependencias, ANTES de crear PostgreSQL, sin ejecutar pruebas.
    warm = run(["docker", "run", "--rm", "--mount", "type=bind,src=" + str(ROOT) + ",dst=/source,readonly",
                "--mount", "type=bind,src=" + str(BASE / "m2") + ",dst=/root/.m2", "--tmpfs", "/workspace",
                "-w", "/workspace", ENV["LAEXCLUSIVA_JAVA17_IMAGE"], "bash", "-c",
                "set -e; cp /source/pom.xml .; cp -a /source/src /source/database /source/docs .; java -version; mvn -version; mvn -B -ntp -Dtest=IdentityValidationPlanTest test"], timeout=600)
    save("dependency-preparation.log", warm.stdout)
    migration_names = ("V001__initial_schema.sql", "V002__payment_idempotency.sql",
                       "V100__sale_creation_idempotency.sql", "V101__general_debt_ledger.sql",
                       "V102__cash_ledger.sql", "V103__enforce_sale_request_identity_coherence.sql",
                       "V104__user_security_version.sql", "V105__unique_active_user_seller.sql",
                       "V106__user_account_instance_identity.sql")
    for name in migration_names:
        report["migrations"][name] = hashlib.sha256((ROOT / "database/migrations" / name).read_bytes()).hexdigest()
    for cycle in ("first", "repeat"):
        start(cycle)
        save(cycle + "-sql.log", psql(["database/validation/identity/validate.psql"]).stdout)
        save(cycle + "-catalog.log", psql(["database/validation/identity/inspect.psql"]).stdout)
        java_tests(cycle)
        before = snapshot()
        catalog = psql(["database/validation/identity/inspect.psql"]).stdout
        for migration, state in (("V001__initial_schema.sql", "42P07"), ("V104__user_security_version.sql", "42701"), ("V105__unique_active_user_seller.sql", "42P07"), ("V106__user_account_instance_identity.sql", "42701")):
            retry = psql(["database/migrations/" + migration], check=False)
            save(cycle + "-reapply-" + migration + ".log", retry.stdout)
            assert retry.returncode != 0 and state in retry.stdout, "Reaplicación no rechazada como se esperaba"
            assert before == snapshot(), "Reaplicación alteró UUID/versiones"
            assert catalog == psql(["database/validation/identity/inspect.psql"]).stdout, "Reaplicación alteró catálogos"
        report["cycles"][-1]["sql_assertions_and_reapply"] = "passed"
        cleanup()
    start("recovery")
    negative = psql(["database/validation/identity/reject-duplicates.psql"], check=False)
    save("recovery-negative.log", negative.stdout)
    assert negative.returncode != 0 and "23505" in negative.stdout
    state = psql(query="SELECT to_regclass('public.uq_user_account_active_seller') IS NULL AND NOT EXISTS(SELECT FROM information_schema.columns WHERE table_schema='public' AND table_name='user_account' AND column_name='account_instance_id') AND (SELECT count(*) FROM public.user_account)=2").stdout
    assert state.strip().endswith("t"), state
    # Recuperación explícita SOLO del fixture sintético conocido, no reparación de datos reales.
    psql(query="UPDATE public.user_account SET active=FALSE WHERE username='iv_duplicate_b'")
    save("recovery-applied.log", psql(["database/migrations/V105__unique_active_user_seller.sql", "database/migrations/V106__user_account_instance_identity.sql"]).stdout)
    state = psql(query="SELECT count(*)=2 AND count(DISTINCT account_instance_id)=2 AND max(security_version)=1 FROM public.user_account").stdout
    assert state.strip().endswith("t"), state
    save("recovery-catalog.log", psql(["database/validation/identity/inspect.psql"]).stdout)
    report["recovery"] = "23505 rejected; V104 preserved; scoped fixture correction; V105/V106 applied"
    cleanup()
    start("application")
    save("application-schema.log", psql(["database/validation/identity/application.psql"]).stdout)
    save("application-catalog.log", psql(["database/validation/identity/inspect.psql"]).stdout)
    java_tests("application")
    cleanup()
    start("incomplete")
    save("incomplete-schema.log", psql(["database/validation/identity/incomplete.psql"]).stdout)
    java_tests("incomplete")
    cleanup()
    report["completed"] = True


try:
    main()
finally:
    try:
        cleanup()
        report["destroyed"] = True
    finally:
        PASSWORD.unlink(missing_ok=True)
        # Sanitiza también informes de fallo antes de publicar artefactos.
        for file in EVIDENCE.rglob("*"):
            if file.is_file():
                file.write_text(clean_text(file.read_text(errors="replace")), encoding="utf-8")
        if "--cleanup" not in sys.argv:
            save("summary.json", json.dumps(report, indent=2))
            if report["completed"] and report.get("destroyed"):
                print("Laboratorio completado y destruido: ciclos identidad (26 pruebas x 2), aplicación (52 pruebas PostgreSQL) y esquema incompleto (1 prueba); recuperación aprobada.", flush=True)
