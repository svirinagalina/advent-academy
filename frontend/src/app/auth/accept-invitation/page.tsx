"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { ThemeToggle } from "@/components/theme";
export default function AcceptInvitation() {
	const token = useRef("");
	const initialized = useRef(false);
	const [ready, setReady] = useState(false),
		[password, setPassword] = useState(""),
		[confirm, setConfirm] = useState(""),
		[error, setError] = useState(""),
		[busy, setBusy] = useState(false),
		[success, setSuccess] = useState(false);
	useEffect(() => {
		if (initialized.current) return;
		initialized.current = true;
		token.current =
			new URLSearchParams(window.location.hash.slice(1)).get("token") || "";
		window.history.replaceState(null, "", window.location.pathname);
		setReady(true);
	}, []);
	async function submit(event: FormEvent) {
		event.preventDefault();
		if (busy) return;
		setError("");
		if (password.length < 8) {
			setError("Пароль должен содержать не менее 8 символов");
			document.getElementById("invite-password")?.focus();
			return;
		}
		if (password !== confirm) {
			setError("Пароли не совпадают");
			document.getElementById("invite-confirm")?.focus();
			return;
		}
		setBusy(true);
		try {
			const response = await fetch("/api/invitations", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({
					token: token.current,
					password,
					confirmPassword: confirm,
				}),
			});
			if (!response.ok) {
				const body = await response.json().catch(() => ({}));
				throw new Error(
					body.code === "INVALID_INVITATION"
						? "Приглашение недействительно, уже использовано или срок его действия истёк. Запросите новое приглашение у администратора."
						: body.code === "PASSWORD_TOO_LONG"
							? "Пароль слишком длинный. Используйте более короткий пароль."
							: "Не удалось активировать учётную запись. Проверьте соединение и повторите.",
				);
			}
			setPassword("");
			setConfirm("");
			token.current = "";
			setSuccess(true);
		} catch (e) {
			setError(
				e instanceof Error
					? e.message
					: "Не удалось активировать учётную запись",
			);
		} finally {
			setBusy(false);
		}
	}
	return (
		<main className="main-content auth-screen">
			<section className="card auth-card admin-accept">
				<div className="admin-dialog-heading">
					<p className="eyebrow">Advent Academy</p>
					<ThemeToggle />
				</div>
				<h1>
					{success ? "Учётная запись активирована" : "Принять приглашение"}
				</h1>
				{success ? (
					<>
						<p>Email подтверждён. Войдите с установленным паролем.</p>
						<Link className="button primary" href="/login">
							Перейти ко входу
						</Link>
					</>
				) : !ready ? (
					<p role="status">Проверяем приглашение…</p>
				) : !token.current ? (
					<p role="alert">
						Откройте ссылку из письма с приглашением. Если срок действия истёк,
						запросите новое приглашение у администратора.
					</p>
				) : (
					<form onSubmit={submit} noValidate aria-busy={busy}>
						<p>
							Установите пароль, чтобы подтвердить email и активировать учётную
							запись.
						</p>
						<label htmlFor="invite-password">Пароль</label>
						<input
							id="invite-password"
							type="password"
							autoComplete="new-password"
							required
							minLength={8}
							maxLength={72}
							disabled={busy}
							value={password}
							onChange={(e) => setPassword(e.target.value)}
						/>
						<label htmlFor="invite-confirm">Повторите пароль</label>
						<input
							id="invite-confirm"
							type="password"
							autoComplete="new-password"
							required
							maxLength={72}
							disabled={busy}
							value={confirm}
							onChange={(e) => setConfirm(e.target.value)}
						/>
						{error && (
							<p id="invite-error" className="feedback error" role="alert">
								{error}
							</p>
						)}
						<button className="button primary" type="submit" disabled={busy}>
							{busy ? "Активируем…" : "Установить пароль и активировать"}
						</button>
						{error && (
							<p>
								Если учётная запись уже активирована,{" "}
								<Link href="/login">перейдите ко входу</Link>.
							</p>
						)}
					</form>
				)}
			</section>
		</main>
	);
}
