"""Verify real HTTP sessions across fresh JVMs and a disposable PostgreSQL restart.

Requires Python 3, Maven, Java 17+ and native PostgreSQL binaries.
Run from any directory: python3 UserService/src/test/scripts/verify_session_persistence.py
PG_BIN defaults to /usr/lib/postgresql/14/bin; JAVA_HOME selects the JDK.
Only temporary processes/data and the Maven target directory are used; no cloud.
"""

import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request


SERVICE = Path(__file__).resolve().parents[3]
PG = Path(os.environ.get("PG_BIN", "/usr/lib/postgresql/14/bin"))
JAVA = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else shutil.which("java")


def free_port():
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


@contextmanager
def database():
    with tempfile.TemporaryDirectory(prefix="psia-session-") as directory:
        base = Path(directory)
        data = base / "data"
        port = free_port()
        subprocess.run([str(PG / "initdb"), "-D", str(data), "-U", "user_service", "--auth=trust"],
                       check=True, stdout=subprocess.DEVNULL)
        control = [str(PG / "pg_ctl"), "-D", str(data), "-l", str(base / "postgres.log"),
                   "-o", f"-h 127.0.0.1 -p {port} -k {base}", "-w"]
        try:
            subprocess.run(control + ["start"], check=True, stdout=subprocess.DEVNULL)
            pg_env = dict(os.environ, PGHOST="127.0.0.1", PGPORT=str(port), PGUSER="user_service")
            subprocess.run([str(PG / "createdb"), "sessions_test"], env=pg_env, check=True)

            def sql(statement):
                return subprocess.check_output([str(PG / "psql"), "-X", "-w", "-d", "sessions_test",
                                                "-v", "ON_ERROR_STOP=1", "-Atc", statement],
                                               env=pg_env, text=True).strip()

            yield {
                "url": f"jdbc:postgresql://127.0.0.1:{port}/sessions_test",
                "sql": sql,
                "restart": lambda: subprocess.run(control + ["-m", "fast", "restart"],
                                                  check=True, stdout=subprocess.DEVNULL),
            }
        finally:
            if (data / "postmaster.pid").exists():
                subprocess.run(control + ["-m", "fast", "stop"], check=True, stdout=subprocess.DEVNULL)


def request(port, path, payload=None, cookie=None, method=None):
    headers = {}
    if cookie:
        headers["Cookie"] = cookie
    if payload is not None:
        headers["Content-Type"] = "application/json"
    call = urllib.request.Request(f"http://127.0.0.1:{port}{path}", headers=headers, method=method,
                                  data=None if payload is None else json.dumps(payload).encode())
    try:
        response = urllib.request.urlopen(call, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read()
        return response.status, response.headers, json.loads(body) if body else None


@contextmanager
def application(db, port, jar):
    env = dict(os.environ, DB_URL=db["url"], DB_USERNAME="user_service", DB_PASSWORD="",
               PROFILE_PROVISIONING_ENABLED="false", SESSION_TIMEOUT="30m")
    with tempfile.TemporaryFile(mode="w+") as log:
        process = subprocess.Popen([JAVA, "-jar", str(jar), "--server.address=127.0.0.1",
                                    f"--server.port={port}"], env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("UserService stopped during startup")
                try:
                    if request(port, "/actuator/health")[0] == 200:
                        break
                except OSError:
                    pass
                time.sleep(0.2)
            else:
                raise AssertionError("UserService did not become healthy")
            yield
        except Exception:
            log.seek(0)
            print(log.read()[-12000:], flush=True)
            raise
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-build", action="store_true", help="Use the already packaged UserService JAR")
    args = parser.parse_args()
    if not args.skip_build:
        subprocess.run(["mvn", "-B", "-ntp", "-f", str(SERVICE / "pom.xml"), "package"], check=True)
    jars = list((SERVICE / "target").glob("user-service-*.jar"))
    if len(jars) != 1:
        raise RuntimeError("Package UserService first; expected exactly one executable JAR")
    credentials = {"username": "persist_" + secrets.token_hex(4), "password": "initial-password-123"}
    port = free_port()
    with database() as db:
        with application(db, port, jars[0]):
            assert request(port, "/internal/auth/session")[0] == 401
            assert db["sql"]("SELECT count(*) FROM spring_session") == "0"
            status, _, account = request(port, "/api/auth/register", {**credentials, "favoriteGenres": ["DRAMA"]})
            assert status == 201
            user_id = int(account["userId"])
            status, headers, _ = request(port, "/api/auth/login", credentials)
            assert status == 200
            cookie_header = headers["Set-Cookie"]
            assert "JSESSIONID=" in cookie_header and "HttpOnly" in cookie_header and "SameSite=Strict" in cookie_header
            cookie = cookie_header.split(";", 1)[0]
            status, _, body = request(port, "/internal/auth/session", cookie=cookie)
            assert status == 200 and body == {"userId": user_id}
            assert db["sql"]("SELECT count(*) FROM spring_session_attributes WHERE attribute_name='authenticatedUserId'") == "1"
            assert db["sql"]("SELECT max_inactive_interval FROM spring_session") == "1800"
            old_primary_id = db["sql"]("SELECT primary_id FROM spring_session")
            print("PASS: JSESSIONID and its authenticated account ID are stored in PostgreSQL; timeout remains 30 minutes.", flush=True)

        db["restart"]()
        with application(db, port, jars[0]):
            status, _, body = request(port, "/internal/auth/session", cookie=cookie)
            assert status == 200 and body == {"userId": user_id}
            status, _, _ = request(port, "/api/users/me/password", {
                "currentPassword": credentials["password"], "newPassword": "updated-password-456",
            }, cookie=cookie, method="PATCH")
            assert status == 204
            print("PASS: the same cookie authenticates a fresh JVM after PostgreSQL also restarts; password change works.", flush=True)

            credentials["password"] = "updated-password-456"
            status, headers, _ = request(port, "/api/auth/login", credentials, cookie=cookie)
            assert status == 200
            replacement = headers["Set-Cookie"].split(";", 1)[0]
            assert replacement != cookie
            assert request(port, "/internal/auth/session", cookie=cookie)[0] == 401
            assert request(port, "/internal/auth/session", cookie=replacement)[0] == 200
            assert db["sql"](f"SELECT count(*) FROM spring_session_attributes WHERE session_primary_id='{old_primary_id}'") == "0"
            print("PASS: logging in again invalidates the previous persistent session and removes its attributes.", flush=True)

        db["sql"]("UPDATE spring_session SET last_access_time=0, expiry_time=0")
        with application(db, port, jars[0]):
            assert request(port, "/internal/auth/session", cookie=replacement)[0] == 401
            assert db["sql"]("SELECT count(*) FROM spring_session") == "0"
            assert db["sql"]("SELECT count(*) FROM spring_session_attributes") == "0"
            assert db["sql"]("SELECT count(*) FROM users") == "1"
            assert db["sql"]("SELECT count(*) FROM flyway_schema_history WHERE version='4' AND success") == "1"
            print("PASS: an expired session stays invalid after restart; account survives and Flyway V4 ran once.", flush=True)
    print("PASS: all test processes stopped and the disposable database removed.", flush=True)


if __name__ == "__main__":
    main()
