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
import urllib.error
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


def login_cookie(port, credentials):
    request = urllib.request.Request(f"http://127.0.0.1:{port}/api/auth/login",
                                     data=json.dumps({"username": credentials["username"],
                                                      "password": credentials["password"]}).encode(),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        assert response.status == 200
        return response.headers["Set-Cookie"].split(";", 1)[0]


def own_profile(port, cookie, update=None):
    method = "GET" if update is None else "PATCH"
    headers = {"Cookie": cookie}
    data = None
    if update is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(update).encode()
    request = urllib.request.Request(f"http://127.0.0.1:{port}/api/profiles/me",
                                     data=data, headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.load(response)


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
                           PROFILE_DB_PASSWORD=profiles["password"],
                           USER_SERVICE_URL=f"http://127.0.0.1:{user_port}",
                           PROFILE_USERNAME_BACKFILL_POLL_DELAY="200")
        credentials = dict(username="recovery_" + secrets.token_hex(4), password="password-testing-123",
                           favoriteGenres=["DRAMA", "SCI_FI"])
        with application("UserService", "user-service-1.0.jar", user_env, user_port):
            status, account = post(user_port, "/api/auth/register", credentials)
            assert status == 201
            user_id = int(account["userId"])
            eventually(lambda: int(users["sql"](f"SELECT attempts FROM pending_profiles WHERE user_id={user_id}")) > 0,
                       "persist failed delivery")
            assert post(user_port, "/api/auth/login", {"username": credentials["username"],
                                                          "password": credentials["password"]})[0] == 200
            assert 'DRAMA' in users["sql"](f"SELECT favorite_genres::text FROM pending_profiles WHERE user_id={user_id}")
            assert 'SCI_FI' in users["sql"](f"SELECT favorite_genres::text FROM pending_profiles WHERE user_id={user_id}")
            legacy_credentials = dict(username="legacy_" + secrets.token_hex(4),
                                      password="password-testing-123", favoriteGenres=["DRAMA"])
            legacy_status, legacy_account = post(user_port, "/api/auth/register", legacy_credentials)
            assert legacy_status == 201
            legacy_id = int(legacy_account["userId"])
            # Simulate a pending row created before initial genres became mandatory.
            users["sql"](f"UPDATE pending_profiles SET favorite_genres = '[]'::jsonb WHERE user_id={legacy_id}")
            print("PASS: registration and login while ProfileService is unavailable; initial genres persisted.", flush=True)
        assert users["sql"](f"SELECT count(*) FROM pending_profiles WHERE user_id={user_id}") == "1"
        with application("UserService", "user-service-1.0.jar", user_env, user_port):
            stack.enter_context(application("ProfileService", "profile-service-1.0.jar", profile_env, profile_port))
            eventually(lambda: users["sql"](f"SELECT count(*) FROM pending_profiles WHERE user_id={user_id}") == "0",
                       "recovery after restart")
            assert profiles["sql"](f"SELECT count(*) FROM profiles WHERE user_id={user_id}") == "1"
            assert profiles["sql"](f"SELECT username FROM profiles WHERE user_id={user_id}") == credentials["username"]
            assert set(profiles["sql"](f"SELECT genre FROM profile_favorite_genres WHERE user_id={user_id} ORDER BY genre").splitlines()) == {"DRAMA", "SCI_FI"}
            eventually(lambda: users["sql"](f"SELECT count(*) FROM pending_profiles WHERE user_id={legacy_id}") == "0",
                       "legacy pending delivery")
            assert profiles["sql"](f"SELECT username FROM profiles WHERE user_id={legacy_id}") == legacy_credentials["username"]
            assert profiles["sql"](f"SELECT count(*) FROM profile_favorite_genres WHERE user_id={legacy_id}") == "0"
            print("PASS: UserService restart retains the job and preferences; recovery creates the initialized profile.", flush=True)
            print("PASS: legacy pending job with empty genres creates a searchable profile.", flush=True)

            cookie = login_cookie(user_port, credentials)
            public_headers = {"Cookie": cookie}
            with urllib.request.urlopen(urllib.request.Request(
                    f"http://127.0.0.1:{profile_port}/api/profiles?username={credentials['username'].upper()}",
                    headers=public_headers), timeout=10) as response:
                results = json.load(response)
                assert results["totalElements"] == 1
                assert results["items"][0]["userId"] == user_id
            with urllib.request.urlopen(urllib.request.Request(
                    f"http://127.0.0.1:{profile_port}/api/profiles/{user_id}",
                    headers=public_headers), timeout=10) as response:
                assert json.load(response)["username"] == credentials["username"]
            profiles["sql"](f"UPDATE profiles SET username = NULL WHERE user_id={user_id}")
            eventually(lambda: profiles["sql"](f"SELECT username FROM profiles WHERE user_id={user_id}")
                       == credentials["username"], "username backfill over UserService HTTP")
            print("PASS: public username search/detail and background backfill over HTTP.", flush=True)
            try:
                own_profile(profile_port, "JSESSIONID=invalid")
                raise AssertionError("ProfileService accepted an invalid session")
            except urllib.error.HTTPError as error:
                assert error.code == 401
            own = own_profile(profile_port, cookie)
            assert own["userId"] == user_id
            assert set(own["favoriteGenres"]) == {"DRAMA", "SCI_FI"}
            assert own["favoriteDecades"] == []
            updated = own_profile(profile_port, cookie,
                                  {"displayName": "Edited", "bio": "Keep me",
                                   "favoriteGenres": ["DRAMA"], "favoriteDecades": [1980, 2000]})
            assert updated["displayName"] == "Edited"
            assert updated["bio"] == "Keep me"
            assert updated["favoriteGenres"] == ["DRAMA"]
            assert set(updated["favoriteDecades"]) == {1980, 2000}
            print("PASS: real UserService session authorizes GET/PATCH of own profile and decades.", flush=True)
            assert profiles["sql"](f"SELECT decade FROM profile_favorite_decades WHERE user_id={user_id} ORDER BY decade").splitlines() == ["1980", "2000"]
            snapshot = profiles["sql"](f"SELECT row_to_json(p) FROM profiles p WHERE user_id={user_id}")
            # Simulate delivery succeeded but its acknowledgement was lost before commit.
            users["sql"](f"INSERT INTO pending_profiles (user_id, favorite_genres) VALUES ({user_id}, '[\"DRAMA\",\"SCI_FI\"]'::jsonb)")
            eventually(lambda: users["sql"](f"SELECT count(*) FROM pending_profiles WHERE user_id={user_id}") == "0",
                       "duplicate delivery acknowledgement")
            assert profiles["sql"](f"SELECT row_to_json(p) FROM profiles p WHERE user_id={user_id}") == snapshot
            assert profiles["sql"](f"SELECT genre FROM profile_favorite_genres WHERE user_id={user_id}") == "DRAMA"
            assert set(profiles["sql"](f"SELECT decade FROM profile_favorite_decades WHERE user_id={user_id}").splitlines()) == {"1980", "2000"}
            print("PASS: repeated delivery receives success and preserves all edited profile data.", flush=True)

            second_port = free_port()
            with application("UserService", "user-service-1.0.jar", user_env, second_port):
                credentials["username"] = "normal_" + secrets.token_hex(4)
                credentials["favoriteGenres"] = ["COMEDY"]
                status, second = post(second_port, "/api/auth/register", credentials)
                assert status == 201
                second_id = int(second["userId"])
                eventually(lambda: users["sql"](f"SELECT count(*) FROM pending_profiles WHERE user_id={second_id}") == "0",
                           "delivery with two workers")
                assert profiles["sql"](f"SELECT count(*) FROM profiles WHERE user_id={second_id}") == "1"
                assert profiles["sql"](f"SELECT genre FROM profile_favorite_genres WHERE user_id={second_id}") == "COMEDY"
                print("PASS: normal registration creates one initialized profile with two UserService workers running.", flush=True)
        # The ProfileService remains up while UserService is stopped: authentication must fail as unavailable.
        try:
            own_profile(profile_port, cookie)
            raise AssertionError("ProfileService accepted a request while UserService was down")
        except urllib.error.HTTPError as error:
            assert error.code == 503
            assert json.load(error)["code"] == "AUTH_SERVICE_UNAVAILABLE"
        print("PASS: UserService outage is reported as 503 rather than an invalid session.", flush=True)
    print("PASS: all processes stopped and both disposable PostgreSQL instances removed.", flush=True)


if __name__ == "__main__":
    main()
