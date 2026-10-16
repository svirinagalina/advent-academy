"use client";
import Link from "next/link";
import {useEffect, useRef, useState, type FormEvent} from "react";
import {ThemeToggle} from "@/components/theme";
export function AccountRecovery({mode}: {mode:"request"|"reset"|"verify"}) {
    const token = useRef(""); const initialized = useRef(false);
    const [ready,setReady] = useState(false),[email,setEmail] = useState(""),[password,setPassword] = useState(""),[confirm,setConfirm] = useState("");
    const [busy,setBusy] = useState(false),[error,setError] = useState(""),[success,setSuccess] = useState(false);
    const [resend,setResend] = useState(false);
    const emailInput = useRef<HTMLInputElement>(null);
    useEffect(() => {
        if (initialized.current) return; initialized.current = true;
        token.current = new URLSearchParams(location.hash.slice(1)).get("token") || new URLSearchParams(location.search).get("token") || "";
        history.replaceState(null,"",location.pathname);
        if (mode === "verify" && !token.current) setResend(true);
        setReady(true);
    },[mode]);
    useEffect(() => {if (ready && resend) emailInput.current?.focus();},[ready,resend]);
    async function submit(event: FormEvent) {
        event.preventDefault(); if (busy) return; setError("");
        if (mode === "reset" && (password !== confirm || password.length < 8)) {setError("Пароли должны совпадать и содержать не менее 8 символов");return;}
        if (mode === "reset" && new TextEncoder().encode(password).length > 72) {setError("Пароль слишком длинный. Сократите его.");return;}
        setBusy(true);
        try {
            const response = await fetch("/api/recovery",{method:"POST",headers:{"Content-Type":"application/json"},
                body:JSON.stringify({action:resend ? "resend" : mode,email,token:token.current,password,confirmPassword:confirm})});
            if (!response.ok) throw new Error(response.status === 429 ? "Слишком много попыток. Попробуйте позже."
                : response.status >= 500 ? "Сервис временно недоступен. Повторите запрос."
                : mode === "request" || resend ? "Проверьте адрес email." : "Ссылка недействительна или срок её действия истёк. Запросите новую.");
            token.current="";setPassword("");setConfirm("");setSuccess(true);
        } catch(e) {setError(e instanceof Error ? e.message : "Не удалось выполнить запрос");} finally {setBusy(false);}
    }
    return <main className="main-content auth-screen"><section className="card auth-card">
        <ThemeToggle/><h1>{resend ? "Запросить письмо подтверждения" : mode === "request" ? "Восстановить пароль" : mode === "reset" ? "Новый пароль" : "Подтвердить email"}</h1>
        {success ? <p role="status">{resend ? "Если учётная запись ожидает подтверждения email, письмо подтверждения отправлено. Проверьте почту и папку «Спам»." : mode === "request" ? "Если учётная запись существует, письмо со ссылкой отправлено. Проверьте почту и папку «Спам»." : mode === "reset" ? "Пароль изменён. Войдите с новым паролем." : "Email подтверждён. Можно войти."}</p>
            : !ready ? <p role="status">Загрузка…</p> : mode !== "request" && !resend && !token.current ? <p role="alert">В ссылке нет токена. Запросите новое письмо.</p>
            : <form onSubmit={submit} className="management-form" aria-busy={busy}>
                {resend && <p>Укажите email учётной записи, чтобы получить новую ссылку подтверждения.</p>}
                {(mode === "request" || resend) && <label>Email<input ref={emailInput} type="email" autoComplete="email" required maxLength={254} value={email} onChange={e=>setEmail(e.target.value)} disabled={busy}/></label>}
                {mode === "reset" && <><label>Новый пароль<input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={password} onChange={e=>setPassword(e.target.value)} disabled={busy}/></label>
                    <label>Повторите пароль<input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={confirm} onChange={e=>setConfirm(e.target.value)} disabled={busy}/></label></>}
                {error && <p role="alert">{error}</p>}<button type="submit" className="button primary" disabled={busy}>{busy ? "Отправляем…" : resend ? "Отправить письмо подтверждения" : mode === "request" ? "Отправить ссылку" : mode === "reset" ? "Изменить пароль" : "Подтвердить email"}</button>
            </form>}
        {mode === "verify" && !resend && !success && <button type="button" className="text-link" disabled={busy} onClick={()=>{setResend(true);setError("");}}>Запросить новое письмо подтверждения</button>}
        {mode === "reset" && <Link className="text-link" href="/auth/forgot-password">Запросить новую ссылку сброса пароля</Link>}
        <Link className="text-link" href="/login">Вернуться ко входу</Link>
    </section></main>;
}
