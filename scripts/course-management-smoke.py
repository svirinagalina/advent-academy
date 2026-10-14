"""Run after starting docs/backend/runtime-course-management.yml as advent-course-api.
Only creates synthetic users; fixture SQL is restricted to this project's tmpfs database.
"""
import json
import subprocess
import urllib.request
import urllib.error
import uuid
from pathlib import Path

PROJECT = "advent-course-api"
CONTAINER = PROJECT + "-postgres-1"
BASE = "http://127.0.0.1:18094"
inspection = json.loads(subprocess.check_output(["docker", "inspect", CONTAINER]))[0]
assert inspection["Config"]["Labels"]["com.docker.compose.project"] == PROJECT
assert "/var/lib/postgresql/data" in inspection["HostConfig"]["Tmpfs"]


def req(method, path, body=None, token=None, expected=200, raw=None, content_type="application/json"):
    data = raw if raw is not None else json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": content_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            status, payload = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, payload = error.code, error.read()
    assert status == expected, (method, path, status, expected, payload.decode())
    return json.loads(payload) if payload else None


def sql(command):
    subprocess.run(["docker", "exec", CONTAINER, "psql", "-U", "sprint", "-d", "sprint", "-v", "ON_ERROR_STOP=1", "-c", command], check=True, capture_output=True)


PASSWORD = "Course-Strong-Password-2026!"
run = uuid.uuid4().hex[:8]
users = {}
for name in ("owner", "foreign", "learner", "blocked", "pending"):
    email = f"course-{run}-{name}@example.test"
    users[name] = req("POST", "/api/auth/register", {"email": email, "password": PASSWORD, "firstName": "Мария" if name == "learner" else name, "lastName": "Проверка"}, expected=201)
ids = {key: value["id"] for key, value in users.items()}
# IDs come from the registration response and are verified as UUIDs before use in SQL.
for value in ids.values():
    uuid.UUID(value)
sql(f"UPDATE users SET email_verified=true,status='ACTIVE' WHERE id IN ('{ids['owner']}','{ids['foreign']}','{ids['learner']}'); UPDATE users SET email_verified=true,status='BLOCKED' WHERE id='{ids['blocked']}'; INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u CROSS JOIN roles r WHERE u.id IN ('{ids['owner']}','{ids['foreign']}') AND r.name='ADMIN' ON CONFLICT DO NOTHING;")


def login(name):
    return req("POST", "/api/auth/login", {"email": users[name]["email"], "password": PASSWORD})["accessToken"]


owner, foreign = login("owner"), login("foreign")
course = req("POST", "/api/courses", {"title": "Управляемый курс", "description": "API smoke"}, owner, 201)
cid = course["id"]
draft = req("POST", "/api/courses", {"title": "Черновик", "description": ""}, owner, 201)
other = req("POST", "/api/courses", {"title": "Чужой курс", "description": ""}, foreign, 201)
modules = []
for title in ("Первый модуль", "Второй модуль"):
    component = req("POST", "/api/components", {"title": title, "description": "Программа"}, owner, 201)
    component = req("POST", f"/api/components/{component['id']}/lessons", {"title": "Урок"}, owner, 201)
    lid = component["lessons"][0]["id"]
    boundary = "course-api-boundary"
    raw = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="lesson.md"\r\nContent-Type: text/markdown\r\n\r\n# Markdown lesson\n\n<script>alert(1)</script>\n\r\n--{boundary}--\r\n').encode()
    uploaded = req("POST", f"/api/lessons/{lid}/content", token=owner, expected=201, raw=raw, content_type=f"multipart/form-data; boundary={boundary}")
    assert uploaded["status"] == "DRAFT" and "<h1>Markdown lesson</h1>" in uploaded["html"]
    assert "<script>" not in uploaded["html"]
    req("POST", f"/api/lessons/{lid}/content/publish", token=owner, expected=204)
    req("POST", f"/api/components/{component['id']}/publish", token=owner)
    req("POST", f"/api/courses/{cid}/components", {"componentId": component["id"]}, owner, 201)
    modules.append(component["id"])
req("PUT", f"/api/courses/{cid}/components/order", {"orderedIds": list(reversed(modules))}, owner)
req("PATCH", f"/api/courses/{cid}", {"title": "Обновлённый курс", "description": "Отредактировано"}, owner)
req("PUT", f"/api/learning/courses/{cid}/students/{ids['learner']}", token=owner, expected=409)
req("POST", f"/api/courses/{cid}/publish", token=owner)
# The same owner is now an instructor; subsequent JWTs have no administrator privileges.
sql(f"DELETE FROM user_roles WHERE user_id='{ids['owner']}'; INSERT INTO user_roles(user_id,role_id) SELECT '{ids['owner']}',id FROM roles WHERE name='INSTRUCTOR';")
owner = login("owner")
managed = req("GET", "/api/courses?managed=true", token=owner)
assert {x["id"] for x in managed} == {cid, draft["id"]}
req("POST", "/api/courses", {"title": "Denied", "description": ""}, owner, 403)
req("PATCH", f"/api/courses/{other['id']}", {"title": "Denied", "description": ""}, owner, 403)
req("GET", f"/api/users/teaching/courses/{other['id']}/candidates?query=course", token=owner, expected=403)
req("GET", f"/api/users/teaching/courses/{draft['id']}/candidates?query=course", token=owner, expected=403)
req("GET", f"/api/users/teaching/courses/{cid}/candidates", token=owner, expected=400)
req("GET", f"/api/users/teaching/courses/{cid}/candidates?query=ab&limit=51", token=owner, expected=400)
candidates = req("GET", f"/api/users/teaching/courses/{cid}/candidates?query={users['learner']['email']}", token=owner)
assert len(candidates) == 1 and candidates[0]["id"] == ids["learner"]
assert set(candidates[0]) == {"id", "firstName", "lastName", "email"}
for name in ("blocked", "pending"):
    assert req("GET", f"/api/users/teaching/courses/{cid}/candidates?query={users[name]['email']}", token=owner) == []
for invalid in (ids["blocked"], ids["pending"], str(uuid.uuid4())):
    req("PUT", f"/api/learning/courses/{cid}/students/{invalid}", token=owner, expected=422)
# Repeated assignment remains idempotent.
for _ in range(2):
    req("PUT", f"/api/learning/courses/{cid}/students/{ids['learner']}", token=owner)
roster = req("GET", f"/api/users/teaching/courses/{cid}/students", token=owner)
assert len(roster) == 1 and roster[0]["email"] == users["learner"]["email"]
learner = login("learner")
req("GET", f"/api/users/teaching/courses/{cid}/candidates?query=course", token=learner, expected=403)
req("PUT", f"/api/learning/courses/{cid}/students/{ids['foreign']}", token=learner, expected=403)
courses = req("GET", "/api/learning/courses", token=learner)
assert len(courses) == 1 and courses[0]["id"] == cid
view = req("GET", f"/api/learning/courses/{cid}", token=learner)
assert [x["id"] for x in view["modules"]] == list(reversed(modules))
lesson = view["modules"][0]["lessons"][0]["id"]
assert "<h1>Markdown lesson</h1>" in req("GET", f"/api/learning/courses/{cid}/lessons/{lesson}", token=learner)["html"]
# Verify an actual identity-service outage, restoring only this isolated service afterwards.
compose = ["docker", "compose", "-p", PROJECT, "-f",
           str(Path(__file__).resolve().parents[1] / "docs/backend/runtime-course-management.yml")]
subprocess.run(compose + ["stop", "user-service"], check=True, capture_output=True)
try:
    req("PUT", f"/api/learning/courses/{cid}/students/{ids['foreign']}", token=owner, expected=502)
    count = subprocess.check_output(["docker", "exec", CONTAINER, "psql", "-U", "sprint", "-d", "sprint", "-tAc",
                                   f"SELECT count(*) FROM content.learning_enrollments WHERE course_id='{cid}' AND user_id='{ids['foreign']}'"])
    assert count.strip() == b"0"
finally:
    subprocess.run(compose + ["up", "-d", "--no-deps", "--wait", "--wait-timeout", "60", "user-service"], check=True, capture_output=True)
req("POST", f"/api/courses/{cid}/archive", token=owner)
managed = req("GET", "/api/courses?managed=true", token=owner)
assert next(x for x in managed if x["id"] == cid)["status"] == "ARCHIVED"
assert req("GET", "/api/learning/courses", token=learner) == []
req("PUT", f"/api/learning/courses/{cid}/students/{ids['foreign']}", token=owner, expected=409)
print("PASS: create, edit, reorder, Markdown upload/sanitization, publish, scoped candidate search, invalid-user rejection, idempotent enrollment, student login/read, ownership and role denials, identity outage without enrollment, archive")
