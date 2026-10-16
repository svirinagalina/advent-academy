"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowRight, Coffee } from "lucide-react";
import { publicCourses } from "@/lib/public-courses";
import { ApplyButton } from "./application";
import { GoCard, PartnerMention } from "./ui";
export function PublicCatalog({
	initialDirection = "all",
	initialLevel = "all",
}: {
	initialDirection?: string;
	initialLevel?: string;
}) {
	const [direction, setDirection] = useState(initialDirection);
	const [level, setLevel] = useState(initialLevel);
	useEffect(() => {
		const popstate = () => {
			const params = new URLSearchParams(location.search);
			setDirection(params.get("direction") || "all");
			setLevel(params.get("level") || "all");
		};
		window.addEventListener("popstate", popstate);
		return () => window.removeEventListener("popstate", popstate);
	}, []);
	function update(nextDirection: string, nextLevel: string) {
		setDirection(nextDirection);
		setLevel(nextLevel);
		const query = new URLSearchParams();
		if (nextDirection !== "all") query.set("direction", nextDirection);
		if (nextLevel !== "all") query.set("level", nextLevel);
		history.replaceState(null, "", `/courses${query.size ? `?${query}` : ""}`);
	}
	const java = direction === "all" || direction === "java";
	const partner =
		(direction === "all" || direction === "english") && level === "all";
	const go = (direction === "all" || direction === "go") && level === "all";
	const courses = java
		? publicCourses.filter((course) => level === "all" || level === course.id)
		: [];
	const query = new URLSearchParams({ direction, level });
	return (
		<>
			<div className="public-catalog-filters">
				<label>
					<span id="public-direction-label">Направление</span>
					<select
						aria-labelledby="public-direction-label"
						value={direction}
						onChange={(event) => update(event.target.value, level)}
					>
						<option value="all">Все</option>
						<option value="java">Java</option>
						<option value="english">Английский</option>
						<option value="go">Go</option>
					</select>
				</label>
				<label>
					<span id="public-level-label">Подготовка</span>
					<select
						aria-labelledby="public-level-label"
						value={level}
						onChange={(event) => update(direction, event.target.value)}
					>
						<option value="all">Любая</option>
						<option value="beginner">С нуля</option>
						<option value="advanced">Есть основы Java</option>
					</select>
				</label>
				<button
					type="button"
					className="public-button text"
					onClick={() => update("all", "all")}
				>
					Сбросить фильтры
				</button>
			</div>
			<p role="status" className="public-fineprint public-result-count">
				{courses.length + Number(partner) + Number(go)
					? `Направлений и маршрутов: ${courses.length + Number(partner) + Number(go)}`
					: "По этим параметрам программ пока нет"}
			</p>
			<div className="public-grid two public-catalog-grid">
				{courses.map((course) => (
					<article className="public-course-card" key={course.id}>
						<Coffee
							className="public-course-symbol"
							size={56}
							aria-hidden="true"
						/>
						<span className="public-badge">{course.level}</span>
						<h2>{course.title}</h2>
						<p>{course.description}</p>
						<p className="public-fineprint">{course.requirements}</p>
						<div className="public-actions">
							<Link
								className="public-button secondary"
								href={`${course.href}?${query}`}
							>
								Подробнее <ArrowRight size={18} aria-hidden="true" />
							</Link>
							<ApplyButton route={course.id} />
						</div>
					</article>
				))}
				{partner && (
					<article className="public-course-card">
						<PartnerMention />
					</article>
				)}
				{go && <GoCard />}
			</div>
			{!courses.length && !partner && !go && (
				<div className="public-empty">
					<h2>По этим параметрам программ пока нет</h2>
					<p>Попробуй другое направление или подготовку.</p>
					<button
						type="button"
						className="public-button secondary"
						onClick={() => update("all", "all")}
					>
						Сбросить фильтры
					</button>
				</div>
			)}
		</>
	);
}
