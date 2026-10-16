"use client";
import { useEffect } from "react";
import { usePathname } from "next/navigation";
export function PublicMotion() {
	const pathname = usePathname();
	// biome-ignore lint/correctness/useExhaustiveDependencies: Client navigation replaces the observed page content.
	useEffect(() => {
		const media = matchMedia("(prefers-reduced-motion: reduce)");
		const animations = new Set<Animation>();
		let observer: IntersectionObserver | undefined;
		const stop = () => {
			observer?.disconnect();
			for (const animation of animations) animation.cancel();
			animations.clear();
		};
		const start = () => {
			stop();
			if (media.matches || !window.IntersectionObserver) return;
			observer = new IntersectionObserver(
				(entries) => {
					for (const entry of entries) {
						if (!entry.isIntersecting) continue;
						const animation = entry.target.animate(
							[
								{ opacity: 0, transform: "translateY(16px)" },
								{ opacity: 1, transform: "translateY(0)" },
							],
							{ duration: 520, easing: "cubic-bezier(.16,1,.3,1)" },
						);
						animations.add(animation);
						animation.onfinish = () => animations.delete(animation);
						observer?.unobserve(entry.target);
					}
				},
				{ threshold: 0.12 },
			);
			for (const element of document.querySelectorAll("[data-public-reveal]"))
				observer.observe(element);
			const target = document.getElementById(location.hash.slice(1));
			const detail = target?.closest("details");
			if (detail) detail.open = true;
		};
		const visibility = () => {
			if (document.hidden) stop();
		};
		start();
		media.addEventListener("change", start);
		document.addEventListener("visibilitychange", visibility);
		return () => {
			stop();
			media.removeEventListener("change", start);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, [pathname]);
	return null;
}
