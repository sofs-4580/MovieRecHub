"""Run both Maven suites and cross-service recovery checks on disposable PostgreSQL instances.

Requires Java 17+, Maven and native PostgreSQL binaries. No existing databases are used.
Run: python3 UserService/src/test/scripts/verify_profile_integration.py (from g34 or any cwd).
PG_BIN can override /usr/lib/postgresql/14/bin; JAVA_HOME selects Java for Maven and the apps.
"""

from contextlib import ExitStack, contextmanager
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.request

REPO = Path(__file__).resolve().parents[4]
PG = Path(os.environ.get("PG_BIN", "/usr/lib/postgresql/14/bin"))
JAVA = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else shutil.which("java")


def free_port():
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def eventually(check, description, seconds=30):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if check():
            return
        time.sleep(0.2)
    raise AssertionError("Timed out: " + description)


@contextmanager
def database(role, name):
    with tempfile.TemporaryDirectory(prefix="psia-integration-") as temporary:
        base = Path(temporary)
        secret = secrets.token_urlsafe(24)
        pwfile = base / "password"
        pwfile.write_text(secret)
        pwfile.chmod(0o600)
        port = free_port()
        subprocess.run([str(PG / "initdb"), "-D", str(base / "data"), "-U", role,
                        "--auth=scram-sha-256", "--pwfile=" + str(pwfile)],
                       check=True, stdout=subprocess.DEVNULL)
        subprocess.run([str(PG / "pg_ctl"), "-D", str(base / "data"), "-l", str(base / "postgres.log"),
                        "-o", f"-h 127.0.0.1 -p {port} -k {base} -F", "-w", "start"],
                       check=True, stdout=subprocess.DEVNULL)
        env = dict(os.environ, PGPASSWORD=secret, PGHOST="127.0.0.1", PGPORT=str(port), PGUSER=role)
        try:
            subprocess.run([str(PG / "createdb"), name], env=env, check=True)
            def sql(statement):
                return subprocess.check_output(
                    [str(PG / "psql"), "-d", name, "-v", "ON_ERROR_STOP=1", "-Atc", statement],
                    env=env, text=True).strip()
            yield dict(url=f"jdbc:postgresql://127.0.0.1:{port}/{name}", role=role,
                       password=secret, sql=sql)
        finally:
            subprocess.run([str(PG / "pg_ctl"), "-D", str(base / "data"), "-m", "fast", "-w", "stop"],
                           check=True, stdout=subprocess.DEVNULL)


def verify(service, db):
    env = dict(os.environ, TEST_DB_URL=db["url"], TEST_DB_USERNAME=db["role"],
               TEST_DB_PASSWORD=db["password"])
    log_path = REPO / service / "target/profile-integration-verification.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("w") as log:
        result = subprocess.run(["mvn", "-B", "-ntp", "-f", str(REPO / service / "pom.xml"),
                                 "-Pintegration-tests", "verify"], env=env,
                                stdout=log, stderr=subprocess.STDOUT)
    output = log_path.read_text()
    if result.returncode:
        raise RuntimeError(output[-18000:])
    for line in output.splitlines():
        if "Tests run:" in line or "BUILD SUCCESS" in line:
            print(service + ": " + line, flush=True)


@contextmanager
def application(service, artifact, env, port):
    with tempfile.TemporaryFile(mode="w+") as log:
        process = subprocess.Popen([JAVA, "-jar", str(REPO / service / "target" / artifact),
                                    "--server.address=127.0.0.1", f"--server.port={port}"],
                                   env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            def ready():
                if process.poll() is not None:
                    log.seek(0)
                    raise RuntimeError(log.read())
                try:
                    with urllib.request.urlopen(f"http://127.0.0.1:{port}/actuator/health", timeout=1) as response:
                        return json.load(response) == {"status": "UP"}
                except OSError:
                    return False
            eventually(ready, service + " startup", 45)
            yield port
        except Exception:
            log.seek(0)
            print(log.read()[-12000:], flush=True)
            raise
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


def post(port, path, payload):
    request = urllib.request.Request(f"http://127.0.0.1:{port}{path}",
                                     data=json.dumps(payload).encode(),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        return response.status, json.load(response)


def main():
    with ExitStack() as stack:
        users = stack.enter_context(database("user_service", "users_test"))
        profiles = stack.enter_context(database("profile_service", "profiles_test"))
        verify("UserService", users)
        verify("ProfileService", profiles)
        profile_port = free_port()
        user_port = free_port()
        user_env = dict(os.environ, DB_URL=users["url"], DB_USERNAME=users["role"], DB_PASSWORD=users["password"],
                        PROFILE_SERVICE_URL=f"http://127.0.0.1:{profile_port}", PROFILE_POLL_DELAY="200",
                        PROFILE_RETRY_DELAY="500ms", PROFILE_CONNECT_TIMEOUT="300ms", PROFILE_READ_TIMEOUT="500ms",
                        PROFILE_PROVISIONING_ENABLED="true")
        profile_env = dict(os.environ, PROFILE_DB_URL=profiles["url"], PROFILE_DB_USERNAME=profiles["role"],
                           PROFILE_DB_PASSWORD=profiles["password"])
        credentials = dict(username="recovery_" + secrets.token_hex(4), password="password-testing-123")
        with application("UserService", "user-service-1.0.jar", user_env, user_port):
            status, account = post(user_port, "/api/auth/register", credentials)
            assert status == 201
            user_id = int(account["userId"])
            eventually(lambda: int(users["sql"](f"SELECT attempts FROM pending_profiles WHERE user_id={user_id}")) > 0,
                       "persist failed delivery")
            assert post(user_port, "/api/auth/login", credentials)[0] == 200
            print("PASS: registration and login while ProfileService is unavailable; delivery persisted.", flush=True)
        assert users["sql"](f"SELECT count(*) FROM pending_profiles WHERE user_id={user_id}") == "1"
        with application("UserService", "user-service-1.0.jar", user_env, user_port):
            stack.enter_context(application("ProfileService", "profile-service-1.0.jar", profile_env, profile_port))
            eventually(lambda: users["sql"](f"SELECT count(*) FROM pending_profiles WHERE user_id={user_id}") == "0",
                       "recovery after restart")
            assert profiles["sql"](f"SELECT count(*) FROM profiles WHERE user_id={user_id}") == "1"
            print("PASS: UserService restart retains the job; ProfileService recovery creates the profile.", flush=True)

            profiles["sql"](f"UPDATE profiles SET display_name='Edited', bio='Keep me', updated_at=clock_timestamp() WHERE user_id={user_id}")
            profiles["sql"](f"INSERT INTO profile_favorite_genres (user_id,genre) VALUES ({user_id},'DRAMA')")
            snapshot = profiles["sql"](f"SELECT row_to_json(p) FROM profiles p WHERE user_id={user_id}")
            # Simulate delivery succeeded but its acknowledgement was lost before commit.
            users["sql"](f"INSERT INTO pending_profiles (user_id) VALUES ({user_id})")
            eventually(lambda: users["sql"](f"SELECT count(*) FROM pending_profiles WHERE user_id={user_id}") == "0",
                       "duplicate delivery acknowledgement")
            assert profiles["sql"](f"SELECT row_to_json(p) FROM profiles p WHERE user_id={user_id}") == snapshot
            assert profiles["sql"](f"SELECT genre FROM profile_favorite_genres WHERE user_id={user_id}") == "DRAMA"
            print("PASS: repeated delivery receives success and preserves all edited profile data.", flush=True)

            second_port = free_port()
            with application("UserService", "user-service-1.0.jar", user_env, second_port):
                credentials["username"] = "normal_" + secrets.token_hex(4)
                status, second = post(second_port, "/api/auth/register", credentials)
                assert status == 201
                second_id = int(second["userId"])
                eventually(lambda: users["sql"](f"SELECT count(*) FROM pending_profiles WHERE user_id={second_id}") == "0",
                           "delivery with two workers")
                assert profiles["sql"](f"SELECT count(*) FROM profiles WHERE user_id={second_id}") == "1"
                print("PASS: normal registration creates one profile with two UserService workers running.", flush=True)
    print("PASS: all processes stopped and both disposable PostgreSQL instances removed.", flush=True)


if __name__ == "__main__":
    main()
