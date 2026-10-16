"use client";
import { type ReactNode, useEffect, useRef } from "react";
import { X } from "lucide-react";
export default function AdminDialog({
	title,
	busy,
	close,
	children,
}: {
	title: string;
	busy: boolean;
	close: () => void;
	children: ReactNode;
}) {
	const dialog = useRef<HTMLDialogElement>(null);
	useEffect(() => {
		const element = dialog.current,
			previous =
				document.activeElement instanceof HTMLElement
					? document.activeElement
					: null;
		const overflow = document.body.style.overflow;
		element?.showModal();
		document.body.style.overflow = "hidden";
		(
			element?.querySelector<HTMLElement>("input,select") ||
			element?.querySelector<HTMLElement>("button")
		)?.focus();
		return () => {
			element?.close();
			document.body.style.overflow = overflow;
			previous?.isConnected && previous.focus();
		};
	}, []);
	return (
		<dialog
			ref={dialog}
			className="management-dialog user-admin-dialog"
			aria-labelledby="admin-dialog-title"
			onKeyDown={(event) => {
				if (event.key !== "Tab") return;
				const items = Array.from(
					event.currentTarget.querySelectorAll<HTMLElement>(
						"button:not(:disabled), input:not(:disabled), select:not(:disabled), a[href]",
					),
				);
				const first = items[0],
					last = items[items.length - 1];
				if (event.shiftKey && document.activeElement === first) {
					event.preventDefault();
					last?.focus();
				} else if (!event.shiftKey && document.activeElement === last) {
					event.preventDefault();
					first?.focus();
				}
			}}
			onCancel={(event) => {
				event.preventDefault();
				if (!busy) close();
			}}
			onClick={(event) => {
				if (event.target === event.currentTarget && !busy) {
					const r = event.currentTarget.getBoundingClientRect();
					if (
						event.clientX < r.left ||
						event.clientX > r.right ||
						event.clientY < r.top ||
						event.clientY > r.bottom
					)
						close();
				}
			}}
		>
			<div className="admin-dialog-heading">
				<h2 id="admin-dialog-title">{title}</h2>
				<button
					className="icon-button"
					type="button"
					disabled={busy}
					aria-label="Закрыть окно"
					onClick={close}
				>
					<X aria-hidden="true" size={20} />
				</button>
			</div>
			{children}
			{busy && (
				<p className="muted" role="status">
					Дождитесь завершения операции. Закрытие пока недоступно.
				</p>
			)}
		</dialog>
	);
}
