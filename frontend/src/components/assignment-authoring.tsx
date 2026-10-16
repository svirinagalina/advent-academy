"use client";
import { useState, type FormEvent } from "react";
import { request, managementError } from "@/lib/management";
type Configuration = {required: boolean; title: string | null; description: string | null; version: number};
export function AssignmentAuthoring({lessonId, editable}: {lessonId: string; editable: boolean}) {
    const [value, setValue] = useState<Configuration | null>(null);
    const [busy, setBusy] = useState(false), [error, setError] = useState(""), [notice, setNotice] = useState("");
    async function load() {
        setBusy(true); setError("");
        try {setValue(await request<Configuration>(`lessons/${lessonId}/assignment/configuration`));}
        catch (e) {setError(managementError(e));} finally {setBusy(false);}
    }
    async function save(event: FormEvent) {
        event.preventDefault(); if (!value || busy) return;
        setBusy(true); setError(""); setNotice("");
        try {setValue(await request<Configuration>(`lessons/${lessonId}/assignment/configuration`, "PUT", value)); setNotice("Задание сохранено");}
        catch (e) {setError(managementError(e));} finally {setBusy(false);}
    }
    return <details onToggle={event => {if (event.currentTarget.open && !value && !busy) void load();}}>
        <summary>Домашнее задание</summary>
        {error && <p role="alert">{error} <button type="button" onClick={() => void load()} disabled={busy}>Обновить</button></p>}
        {notice && <p role="status">{notice}</p>}
        {!value ? <p role="status">Загрузка…</p> : <form className="management-form" onSubmit={save} aria-busy={busy}>
            <label><input type="checkbox" checked={value.required} disabled={!editable || busy}
                onChange={e => setValue({...value, required:e.target.checked})}/> Обязательная проверка преподавателем</label>
            {value.required && <>
                <label>Название задания<input value={value.title || ""} required maxLength={255} disabled={!editable || busy}
                    onChange={e => setValue({...value,title:e.target.value})}/></label>
                <label>Условие<textarea value={value.description || ""} required maxLength={20000} rows={8} disabled={!editable || busy}
                    onChange={e => setValue({...value,description:e.target.value})}/></label>
            </>}
            {editable ? <button type="submit" className="button secondary" disabled={busy}>Сохранить задание</button>
                : <p>Структура задания зафиксирована после публикации модуля.</p>}
        </form>}
    </details>;
}
