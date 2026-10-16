import assert from "node:assert/strict";
import { readFileSync, mkdirSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { join } from "node:path";
import { withSmokeBrowser } from "./smoke-browser.mjs";
const base = process.env.ADVENT_TEST_ORIGIN,
	smtp = process.env.ADVENT_TEST_SMTP;
for (const address of [base, smtp])
	assert.ok(["127.0.0.1", "localhost"].includes(new URL(address).hostname));
const fixture = JSON.parse(
	readFileSync(process.env.ADVENT_EDITOR_FIXTURE, "utf8"),
);
const container = process.env.ADVENT_TEST_POSTGRES;
const info = JSON.parse(
	execFileSync("docker", ["inspect", container], { encoding: "utf8" }),
)[0];
assert.equal(
	info.Config.Labels["com.docker.compose.project"],
	process.env.ADVENT_TEST_PROJECT,
);
assert.equal(info.Config.Labels["com.docker.compose.service"], "postgres");
assert.ok("/var/lib/postgresql/data" in info.HostConfig.Tmpfs);
const sql = (command) =>
	execFileSync(
		"docker",
		[
			"exec",
			"-i",
			container,
			"psql",
			"-U",
			"sprint",
			"-d",
			"sprint",
			"-v",
			"ON_ERROR_STOP=1",
		],
		{ input: command },
	);
const artifacts = join(process.env.ADVENT_TEST_ARTIFACTS, "user-admin");
mkdirSync(artifacts, { recursive: true });
await withSmokeBrowser("user-admin", async (browser) => {
	async function session(who) {
		const context = await browser.newContext({
			viewport: { width: 1440, height: 1000 },
		});
		const r = await context.request.post(base + "/api/session", {
			headers: { Origin: base },
			data: { email: fixture.users[who].email, password: fixture.password },
		});
		assert.equal(r.status(), 200, await r.text());
		return context;
	}
	async function api(context, method, path, data, expected = 200) {
		const r = await context.request.fetch(
			base + "/api/backend/users/admin" + path,
			{
				method,
				headers: { Origin: base },
				...(data === undefined ? {} : { data }),
			},
		);
		const t = await r.text();
		assert.equal(r.status(), expected, t);
		return t ? JSON.parse(t) : null;
	}
	const owner = await session("editor"),
		foreign = await session("foreign"),
		learner = await session("student");
	const page = await owner.newPage(),
		errors = [];
	page.on("pageerror", (e) => errors.push(e.message));
	for (const context of [foreign, learner]) {
		await api(context, "GET", "", undefined, 403);
		await api(
			context,
			"POST",
			"",
			{
				requestId: crypto.randomUUID(),
				firstName: "Недоступно",
				lastName: "Тест",
				email: "forbidden@example.test",
				roles: ["ADMIN"],
			},
			403,
		);
	}
	const denied = await foreign.newPage();
	await denied.goto(base + "/?view=users");
	await denied
		.getByRole("heading", {
			name: "Нет доступа к управлению пользователями",
			exact: true,
		})
		.waitFor();
	await page.goto(base + "/?view=users");
	await page
		.getByRole("heading", { name: "Пользователи", exact: true })
		.waitFor();
	await page.locator(".admin-users tbody tr").first().waitFor();
	// Failed list requests retain their query and offer a useful retry.
	await page.route(
		"**/api/backend/users/admin?*",
		(route) => route.abort("failed"),
		{ times: 1 },
	);
	await page
		.getByLabel("Имя или email", { exact: true })
		.fill(fixture.users.editor.email);
	await page
		.getByRole("alert")
		.filter({ hasText: "Не удалось загрузить пользователей" })
		.waitFor();
	assert.equal(
		await page.getByLabel("Имя или email", { exact: true }).inputValue(),
		fixture.users.editor.email,
	);
	await page.getByRole("button", { name: "Повторить", exact: true }).click();
	await page.locator(".admin-users tbody tr").first().waitFor();
	assert.equal(await page.locator(".admin-users tbody tr").count(), 1);
	await page
		.getByRole("button", { name: "Сбросить фильтры", exact: true })
		.click();
	await page.locator(".admin-users tbody tr").first().waitFor();
	await page
		.getByRole("button", { name: "Создать пользователя", exact: true })
		.click();
	const dialog = page.getByRole("dialog");
	await dialog
		.getByRole("button", { name: "Создать и пригласить", exact: true })
		.click();
	assert.equal(
		await dialog
			.getByLabel("Имя", { exact: true })
			.getAttribute("aria-invalid"),
		"true",
	);
	assert.equal(
		await dialog
			.getByLabel("Имя", { exact: true })
			.evaluate((el) => el === document.activeElement),
		true,
	);
	assert.equal(
		await dialog.getByLabel("Ученик", { exact: true }).isChecked(),
		true,
	);
	const email =
		"user-admin-" + crypto.randomUUID().slice(0, 8) + "@example.test";
	await dialog.getByLabel("Имя", { exact: true }).fill("Дмитрий");
	await dialog.getByLabel("Фамилия", { exact: true }).fill("Лебедев");
	await dialog.getByLabel("Email", { exact: true }).fill(email);
	await owner.request.post(smtp + "/failure", { data: { enabled: true } });
	// The backend creates the user, but its response is lost. The retry MUST reuse the request id.
	await page.route(
		"**/api/backend/users/admin",
		async (route) => {
			if (route.request().method() !== "POST") return route.continue();
			const response = await route.fetch();
			assert.equal(response.status(), 200);
			assert.equal((await response.json()).invitation.status, "FAILED");
			await route.abort("failed");
		},
		{ times: 1 },
	);
	await dialog
		.getByRole("button", { name: "Создать и пригласить", exact: true })
		.click();
	await dialog
		.getByRole("button", {
			name: "Проверить результат и повторить",
			exact: true,
		})
		.waitFor();
	assert.equal(
		await dialog.getByLabel("Email", { exact: true }).inputValue(),
		email,
	);
	const created = await api(
		owner,
		"GET",
		"?query=" + encodeURIComponent(email),
	);
	assert.equal(created.total, 1);
	let user = created.items[0];
	assert.match(user.id, /^[0-9a-f-]{36}$/i);
	await dialog
		.getByRole("button", {
			name: "Проверить результат и повторить",
			exact: true,
		})
		.click();
	await dialog.getByText("Пользователь создан", { exact: true }).waitFor();
	await dialog
		.getByText("Не удалось отправить приглашение", { exact: true })
		.waitFor();
	assert.equal(
		(await api(owner, "GET", "?query=" + encodeURIComponent(email))).total,
		1,
	);
	await owner.request.post(smtp + "/failure", { data: { enabled: false } });
	sql(
		`UPDATE user_invitations SET attempted_at=now()-interval '2 minutes' WHERE user_id='${user.id}';`,
	);
	await dialog
		.getByRole("button", { name: "Повторить отправку", exact: true })
		.click();
	await dialog.getByText("Приглашение отправлено", { exact: true }).waitFor();
	const letters = await (await owner.request.get(smtp + "/messages")).json();
	const invitation = letters.filter((m) => m.to === email).at(-1);
	assert.ok(invitation);
	const link = invitation.text.match(/https?:\/\/\S+/)[0];
	assert.equal(new URL(link).origin, base);
	const token = new URLSearchParams(new URL(link).hash.slice(1)).get("token");
	assert.ok(token);
	assert.equal(
		JSON.stringify(await api(owner, "GET", "/" + user.id)).includes(token),
		false,
	);
	await dialog
		.getByRole("button", { name: "Открыть пользователя", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Дмитрий Лебедев", exact: true })
		.waitFor();
	const invited = await browser.newContext({
			viewport: { width: 390, height: 844 },
		}),
		acceptance = await invited.newPage();
	acceptance.on("pageerror", (e) => errors.push(e.message));
	await acceptance.goto(link);
	await acceptance.getByLabel("Пароль", { exact: true }).waitFor();
	assert.equal(new URL(acceptance.url()).hash, "");
	assert.equal(
		(await acceptance.locator("body").innerText()).includes(token),
		false,
	);
	const password = "Invited-Strong-2026!";
	await acceptance.getByLabel("Пароль", { exact: true }).fill(password);
	await acceptance
		.getByLabel("Повторите пароль", { exact: true })
		.fill("different");
	await acceptance
		.getByRole("button", {
			name: "Установить пароль и активировать",
			exact: true,
		})
		.click();
	await acceptance
		.getByRole("alert")
		.filter({ hasText: "Пароли не совпадают" })
		.waitFor();
	await acceptance
		.getByLabel("Повторите пароль", { exact: true })
		.fill(password);
	await acceptance
		.getByRole("button", {
			name: "Установить пароль и активировать",
			exact: true,
		})
		.click();
	await acceptance
		.getByRole("heading", { name: "Учётная запись активирована", exact: true })
		.waitFor();
	const replay = await invited.request.post(base + "/api/invitations", {
		headers: { Origin: base },
		data: { token, password, confirmPassword: password },
	});
	assert.equal(replay.status(), 400);
	const login = await invited.request.post(base + "/api/session", {
		headers: { Origin: base },
		data: { email: email.toUpperCase(), password },
	});
	assert.equal(login.status(), 200, await login.text());
	await acceptance.goto(base + "/?view=users");
	await acceptance
		.getByRole("heading", {
			name: "Нет доступа к управлению пользователями",
			exact: true,
		})
		.waitFor();
	await page
		.getByRole("button", { name: "Обновить данные", exact: true })
		.click();
	await page.getByText("Приглашение принято", { exact: true }).waitFor();
	assert.equal(
		await page
			.getByRole("button", {
				name: "Отправить приглашение повторно",
				exact: true,
			})
			.count(),
		0,
	);
	await page
		.getByRole("button", { name: "Изменить роли", exact: true })
		.click();
	await dialog.getByLabel("Преподаватель", { exact: true }).check();
	await dialog
		.getByRole("button", { name: "Сохранить изменения", exact: true })
		.click();
	await page.getByText("Роли обновлены", { exact: true }).waitFor();
	user = await api(owner, "GET", "/" + user.id);
	assert.deepEqual(user.roles, ["INSTRUCTOR", "STUDENT"]);
	await page
		.getByRole("button", { name: "Изменить роли", exact: true })
		.click();
	await dialog.getByLabel("Администратор", { exact: true }).check();
	await api(owner, "PUT", "/" + user.id + "/roles", {
		version: user.version,
		roles: ["STUDENT", "CONTENT_EDITOR"],
	});
	await dialog
		.getByRole("button", { name: "Сохранить изменения", exact: true })
		.click();
	await dialog
		.getByRole("alert")
		.filter({ hasText: "Другой администратор" })
		.waitFor();
	assert.equal(
		await dialog
			.getByRole("button", { name: "Сохранить изменения", exact: true })
			.count(),
		0,
	);
	await dialog
		.getByRole("button", { name: "Обновить данные", exact: true })
		.click();
	await dialog
		.getByText("Данные обновлены. Проверьте выбранные роли.", { exact: true })
		.waitFor();
	assert.equal(
		await dialog.getByLabel("Редактор контента", { exact: true }).isChecked(),
		true,
	);
	await dialog.getByLabel("Администратор", { exact: true }).check();
	await dialog
		.getByRole("button", { name: "Сохранить изменения", exact: true })
		.click();
	await dialog.waitFor({ state: "hidden" });
	await page
		.getByRole("button", { name: "Изменить роли", exact: true })
		.click();
	await dialog.getByLabel("Администратор", { exact: true }).uncheck();
	await dialog
		.getByRole("button", { name: "Сохранить изменения", exact: true })
		.click();
	await dialog
		.getByRole("heading", { name: "Снять роль Администратор?", exact: true })
		.waitFor();
	assert.equal(
		(await api(owner, "GET", "/" + user.id)).roles.includes("ADMIN"),
		true,
	);
	await dialog
		.getByRole("button", { name: "Снять роль и сохранить", exact: true })
		.click();
	await dialog.waitFor({ state: "hidden" });
	assert.equal(
		(await api(owner, "GET", "/" + user.id)).roles.includes("ADMIN"),
		false,
	);
	await page
		.getByRole("button", { name: "Заблокировать пользователя", exact: true })
		.click();
	await page.route(
		"**/api/backend/users/admin/*/block",
		(route) => route.abort("failed"),
		{ times: 1 },
	);
	await dialog
		.getByRole("button", { name: "Заблокировать пользователя", exact: true })
		.click();
	await dialog.getByRole("alert").waitFor();
	assert.equal((await api(owner, "GET", "/" + user.id)).status, "ACTIVE");
	await dialog
		.getByRole("button", { name: "Заблокировать пользователя", exact: true })
		.click();
	await dialog.waitFor({ state: "hidden" });
	assert.equal((await api(owner, "GET", "/" + user.id)).status, "BLOCKED");
	sql(
		`UPDATE users SET locked_until=now()+interval '10 minutes' WHERE id='${user.id}';`,
	);
	await page
		.getByRole("button", { name: "Обновить данные", exact: true })
		.click();
	await page
		.getByRole("button", { name: "Разблокировать вход", exact: true })
		.click();
	await dialog
		.getByRole("button", { name: "Разблокировать вход", exact: true })
		.click();
	await dialog.waitFor({ state: "hidden" });
	user = await api(owner, "GET", "/" + user.id);
	assert.equal(user.status, "BLOCKED");
	assert.equal(user.temporarilyLocked, false);
	await page
		.getByRole("button", { name: "Разблокировать пользователя", exact: true })
		.click();
	await dialog
		.getByRole("button", { name: "Разблокировать пользователя", exact: true })
		.click();
	await dialog.waitFor({ state: "hidden" });
	assert.equal((await api(owner, "GET", "/" + user.id)).status, "ACTIVE");
	await page
		.getByRole("tab", { name: "История действий", exact: true })
		.click();
	await page.locator(".admin-history li").first().waitFor();
	await page.screenshot({
		path: join(artifacts, "history.png"),
		fullPage: true,
	});
	assert.equal(
		await page
			.getByRole("heading", { name: "Дмитрий Лебедев", exact: true })
			.count(),
		1,
	);
	assert.ok(
		(await page.locator(".admin-history").innerText()).includes(
			"editor Editor smoke",
		),
	);
	await page.goto(base + "/?view=users&user=" + fixture.users.editor.id);
	await page
		.getByRole("button", { name: "Заблокировать пользователя", exact: true })
		.waitFor();
	assert.equal(
		await page
			.getByRole("button", { name: "Заблокировать пользователя", exact: true })
			.isDisabled(),
		true,
	);
	await page
		.getByRole("button", { name: "Изменить роли", exact: true })
		.click();
	assert.equal(
		await dialog.getByLabel("Администратор", { exact: true }).isDisabled(),
		true,
	);
	assert.equal(
		await dialog.getByLabel("Ученик", { exact: true }).isDisabled(),
		false,
	);
	await page.keyboard.press("Escape");
	// Real pagination and retained list state, including multiple roles on the same user.
	for (let index = 0; index < 21; index++)
		await api(owner, "POST", "", {
			requestId: crypto.randomUUID(),
			firstName: "Каталог",
			lastName: String(index),
			email: `admin-catalog-${index}@example.test`,
			roles: ["STUDENT"],
		});
	await page.goto(
		base + "/?view=users&query=admin-catalog&role=STUDENT&status=PENDING",
	);
	await page.locator(".admin-users tbody tr").first().waitFor();
	await page.getByRole("button", { name: "Вперёд", exact: true }).click();
	await page.getByText("Страница 2", { exact: true }).waitFor();
	await page.locator(".admin-users tbody tr").first().getByRole("link").click();
	await page
		.getByRole("button", { name: "К пользователям", exact: true })
		.click();
	await page.getByText("Страница 2", { exact: true }).waitFor();
	assert.equal(
		await page.getByLabel("Имя или email", { exact: true }).inputValue(),
		"admin-catalog",
	);
	await page.getByLabel("Имя или email", { exact: true }).fill("");
	await page
		.getByLabel("Имя или email", { exact: true })
		.pressSequentially("nobody@example.test", { delay: 40 });
	assert.equal(
		await page.getByLabel("Имя или email", { exact: true }).inputValue(),
		"nobody@example.test",
	);
	await page
		.getByRole("heading", {
			name: "По вашему запросу ничего не найдено",
			exact: true,
		})
		.waitFor();
	await page
		.getByRole("button", { name: "Сбросить фильтры", exact: true })
		.first()
		.click();
	await page.locator(".admin-users tbody tr").first().waitFor();
	// A duplicate email opens the existing account without creating another one.
	await page
		.getByRole("button", { name: "Создать пользователя", exact: true })
		.first()
		.click();
	await dialog.getByLabel("Имя", { exact: true }).fill("Дмитрий");
	await dialog.getByLabel("Фамилия", { exact: true }).fill("Лебедев");
	await dialog.getByLabel("Email", { exact: true }).fill(email);
	await dialog
		.getByRole("button", { name: "Создать и пригласить", exact: true })
		.click();
	await dialog
		.getByRole("alert")
		.filter({ hasText: "Пользователь с таким email уже существует" })
		.waitFor();
	await dialog
		.getByRole("button", { name: "Открыть пользователя", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Дмитрий Лебедев", exact: true })
		.waitFor();
	await page.screenshot({
		path: join(artifacts, "card-active.png"),
		fullPage: true,
	});
	await page
		.getByRole("button", { name: "К пользователям", exact: true })
		.click();
	await page.locator(".admin-users tbody tr").first().waitFor();
	// Long names, email and four simultaneous roles must fit every viewport.
	await api(owner, "POST", "", {
		requestId: crypto.randomUUID(),
		firstName: "А".repeat(50),
		lastName: "Б".repeat(50),
		email: "l".repeat(60) + "@" + "d".repeat(50) + ".example.test",
		roles: ["ADMIN", "CONTENT_EDITOR", "INSTRUCTOR", "STUDENT"],
	});
	await page.reload();
	await page.locator(".admin-users tbody tr").first().waitFor();
	for (const theme of ["light", "dark"])
		for (const width of [1440, 390, 320]) {
			await page.setViewportSize({ width, height: 1000 });
			await page.evaluate((theme) => {
				document.documentElement.dataset.theme = theme;
			}, theme);
			await page.screenshot({
				path: join(artifacts, `list-${theme}-${width}.png`),
				fullPage: true,
			});
			assert.equal(
				await page.evaluate(
					() => document.documentElement.scrollWidth <= innerWidth,
				),
				true,
				`list ${theme} ${width}`,
			);
			await page.evaluate(() => {
				document.documentElement.style.fontSize = "200%";
			});
			assert.equal(
				await page.evaluate(
					() => document.documentElement.scrollWidth <= innerWidth,
				),
				true,
				`zoom list ${theme} ${width}`,
			);
			await page.screenshot({
				path: join(artifacts, `list-zoom-${theme}-${width}.png`),
			});
			await page.evaluate(() =>
				document.documentElement.style.removeProperty("font-size"),
			);
			await page
				.getByRole("button", { name: "Создать пользователя", exact: true })
				.first()
				.click();
			await dialog
				.getByRole("button", { name: "Создать и пригласить", exact: true })
				.click();
			await page.screenshot({
				path: join(artifacts, `create-${theme}-${width}.png`),
			});
			assert.equal(
				await dialog.evaluate((el) => el.scrollWidth <= el.clientWidth),
				true,
			);
			for (let i = 0; i < 12; i++) await page.keyboard.press("Tab");
			assert.equal(
				await dialog.evaluate((el) => el.contains(document.activeElement)),
				true,
			);
			await page.evaluate(() => {
				document.documentElement.style.fontSize = "200%";
			});
			await page.screenshot({
				path: join(artifacts, `create-zoom-${theme}-${width}.png`),
			});
			assert.equal(
				await page.evaluate(
					() => document.documentElement.scrollWidth <= innerWidth,
				),
				true,
				`zoom page ${theme} ${width}`,
			);
			assert.equal(
				await dialog.evaluate((el) => el.scrollWidth <= el.clientWidth),
				true,
				`zoom dialog ${theme} ${width}`,
			);
			await page.evaluate(() =>
				document.documentElement.style.removeProperty("font-size"),
			);
			await page.keyboard.press("Escape");
			assert.equal(
				await page
					.getByRole("button", { name: "Создать пользователя", exact: true })
					.first()
					.evaluate((el) => el === document.activeElement),
				true,
			);
		}
	const badOrigin = await owner.request.post(
		base + "/api/backend/users/admin",
		{ headers: { Origin: "https://foreign.example" }, data: {} },
	);
	assert.equal(badOrigin.status(), 403);
	const oversize = await owner.request.post(base + "/api/backend/users/admin", {
		headers: { Origin: base },
		data: { text: "x".repeat(40000) },
	});
	assert.equal(oversize.status(), 413);
	assert.deepEqual(errors, []);
	console.log(
		"PASS: ADMIN-only direct/UI access, real SMTP failure and resend, lost-response idempotency, actual invitation acceptance and login, used-secret protection, role conflict/reload and administrator removal confirmation, last admin guard, independent lock types, immutable actor history, list errors/retries, duplicate email, pagination/filter retention, both themes 1440/390/320 with long values, text 200%, keyboard focus and BFF Origin/body limits.",
	);
});
