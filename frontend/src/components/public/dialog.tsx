"use client";
import { useEffect, useRef, type ReactNode } from "react";
import { X } from "lucide-react";

export function PublicDialog({
	title,
	close,
	children,
	className = "",
}: {
	title: string;
	close: () => void;
	children: ReactNode;
	className?: string;
}) {
	const ref = useRef<HTMLDialogElement>(null);
	const heading = useRef<HTMLHeadingElement>(null);
	useEffect(() => {
		const element = ref.current;
		const previous =
			document.activeElement instanceof HTMLElement
				? document.activeElement
				: null;
		const overflow = document.body.style.overflow;
		element?.showModal();
		heading.current?.focus();
		document.body.style.overflow = "hidden";
		return () => {
			element?.close();
			document.body.style.overflow = overflow;
			if (previous?.isConnected && previous.getClientRects().length)
				previous.focus();
			else document.querySelector<HTMLElement>(".public-logo")?.focus();
		};
	}, []);
	return (
		<dialog
			ref={ref}
			className={`public-dialog ${className}`}
			aria-labelledby="public-dialog-title"
			onKeyDown={(event) => {
				if (event.key === "Escape") {
					event.preventDefault();
					close();
				}
			}}
			onCancel={(event) => {
				event.preventDefault();
				close();
			}}
			onClick={(event) => {
				if (event.target !== event.currentTarget) return;
				const rect = event.currentTarget.getBoundingClientRect();
				if (
					event.clientX < rect.left ||
					event.clientX > rect.right ||
					event.clientY < rect.top ||
					event.clientY > rect.bottom
				)
					close();
			}}
		>
			<div className="public-dialog-heading">
				<h2 ref={heading} id="public-dialog-title" tabIndex={-1}>
					{title}
				</h2>
				<button
					className="public-icon-button"
					type="button"
					aria-label="Закрыть окно"
					onClick={close}
				>
					<X size={20} aria-hidden="true" />
				</button>
			</div>
			{children}
		</dialog>
	);
}
