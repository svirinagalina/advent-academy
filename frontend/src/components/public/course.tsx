import Link from "next/link";
import {
	ArrowRight,
	BookOpen,
	Braces,
	CheckCheck,
	Code2,
	Database,
	FileText,
	FolderGit2,
	GitBranch,
	Globe,
	MessagesSquare,
	SearchCheck,
	Settings2,
} from "lucide-react";
import {
	advancedFaq,
	advancedModules,
	beginnerFaq,
	beginnerModules,
} from "@/lib/public-courses";
import { ApplyButton } from "./application";
import {
	Accordion,
	Breadcrumbs,
	Closing,
	PartnerMention,
	PracticeCard,
	Section,
	SystemDiagram,
	TextLink,
	Workflow,
} from "./ui";
export function PublicCourse({
	advanced,
	catalogHref = "/courses",
}: {
	advanced: boolean;
	catalogHref?: string;
}) {
	const route = advanced ? "advanced" : "beginner";
	const outcomes = advanced
		? ([
				[
					"Изменения в проекте с историей Git",
					"Собственные изменения в существующем приложении и объяснение выбранного подхода.",
					GitBranch,
				],
				[
					"Тесты проблемных сценариев",
					"Проверки повторов, ошибок и граничных случаев.",
					SearchCheck,
				],
				[
					"Описание архитектурных решений",
					"Границы, альтернативы и ограничения выбранного решения.",
					FileText,
				],
				[
					"План технической подготовки",
					"Темы и задачи для дальнейшей самостоятельной работы.",
					CheckCheck,
				],
			] as const)
		: ([
				[
					"Собственный код и история изменений",
					"Решения и последовательность работы в Git.",
					Code2,
				],
				[
					"Backend-проект с README",
					"Описание задачи, запуск и используемые инструменты.",
					FolderGit2,
				],
				["Тесты и разбор ошибок", "Проверки освоенных сценариев.", SearchCheck],
				[
					"Понимание следующих шагов",
					"Темы, к которым стоит вернуться или перейти дальше.",
					CheckCheck,
				],
			] as const);
	return (
		<>
			<Breadcrumbs
				items={[
					{ label: "Курсы", href: catalogHref },
					{ label: advanced ? "Java Advanced" : "Java с нуля" },
				]}
			/>
			<section className="public-hero public-course-hero">
				<div data-public-reveal>
					<p className="public-kicker">
						{advanced ? "Для тех, у кого есть база" : "Для начинающих"}
					</p>
					<h1>
						{advanced
							? "Знаешь Java. Научись разбираться в системе."
							: "Java с нуля: начни с понимания."}
					</h1>
					<p className="public-lead">
						{advanced
							? "Усиль практику после курсов или самостоятельного обучения: существующий код, ошибки, конкурентность и взаимодействие сервисов."
							: "От первой программы к backend-приложению: изучай тему, решай задачи и проверяй результат."}
					</p>
					<div className="public-actions">
						<ApplyButton route={route} />
						<Link
							className="public-button secondary"
							href={advanced ? "#requirements" : "#program"}
						>
							{advanced ? "Проверить подготовку" : "Посмотреть программу"}
							<ArrowRight size={18} aria-hidden="true" />
						</Link>
					</div>
					{!advanced && (
						<div className="public-tags">
							<span className="public-badge">Без опыта программирования</span>
							<span className="public-badge">Практика по темам</span>
						</div>
					)}
				</div>
				<div className="public-hero-art" data-public-reveal>
					{advanced ? (
						<SystemDiagram />
					) : (
						<>
							<div className="public-code-window">
								<div aria-hidden="true">
									<i />
									<i />
									<i />
									<span>Main.java</span>
								</div>
								<pre>
									<code>{`public class Main {\n    public static void main(String[] args) {\n        System.out.println("Привет, Java!");\n    }\n}`}</code>
								</pre>
							</div>
							<Workflow compact />
						</>
					)}
				</div>
			</section>
			{advanced ? (
				<Section
					id="requirements"
					title="Подойдёт, если ты…"
					intro="Для Advanced нужна входная база. Не обязательно проходить весь путь с нуля."
				>
					<div className="public-grid three public-requirements">
						<article>
							<Code2 size={24} aria-hidden="true" />
							<h3>Пишешь небольшие Java-программы</h3>
							<p>
								Умеешь самостоятельно собрать и запустить простое приложение.
							</p>
						</article>
						<article>
							<Database size={24} aria-hidden="true" />
							<h3>Знаком с ООП, коллекциями и SQL</h3>
							<p>
								Понимаешь исключения, имеешь представление об HTTP и работе с
								Git.
							</p>
						</article>
						<article>
							<MessagesSquare size={24} aria-hidden="true" />
							<h3>Хочешь больше самостоятельной практики</h3>
							<p>
								Готов разбираться в задачах и объяснять собственные решения.
							</p>
						</article>
					</div>
					<div className="public-section-note">
						<p>
							Если базы пока не хватает, можно начать с маршрута с нуля или
							обсудить подготовку.
						</p>
						<div className="public-actions">
							<TextLink href="/courses/java/beginner">Начать с основ</TextLink>
							<ApplyButton route="unsure" className="text">
								Обсудить подготовку
							</ApplyButton>
						</div>
					</div>
				</Section>
			) : (
				<Section title="Что входит в маршрут">
					<div className="public-grid four public-feature-grid">
						{[
							[
								"Java Core",
								"Синтаксис, ООП, исключения и стандартная библиотека.",
								Braces,
							],
							[
								"Работа с данными",
								"Коллекции, файлы, SQL и PostgreSQL.",
								Database,
							],
							["Backend", "HTTP, REST API и Spring Boot.", Settings2],
							[
								"Практика и разбор",
								"Задачи, тесты и объяснение решений.",
								FileText,
							],
						].map(([title, text, Icon]) => {
							const FeatureIcon = Icon as typeof Braces;
							return (
								<article key={String(title)}>
									<FeatureIcon size={28} aria-hidden="true" />
									<h3>{String(title)}</h3>
									<p>{String(text)}</p>
								</article>
							);
						})}
					</div>
				</Section>
			)}
			{advanced && (
				<Section title="От знакомой темы — к самостоятельной задаче">
					<dl className="public-challenges">
						<div>
							<dt>CRUD работает</dt>
							<dd>Разобраться с транзакцией и повторным запросом</dd>
						</div>
						<div>
							<dt>Сообщение отправлено</dt>
							<dd>Обработать повтор и ошибку consumer</dd>
						</div>
						<div>
							<dt>Тест зелёный</dt>
							<dd>Проверить сбой и граничный сценарий</dd>
						</div>
					</dl>
				</Section>
			)}
			<Section
				title={advanced ? "Программа Advanced" : "Программа обучения"}
				id="program"
			>
				<p className="public-program-note">
					Проект программы. Темы, задания и готовность материалов уточняются до
					начала обучения.
				</p>
				<Accordion
					items={advanced ? advancedModules : beginnerModules}
					initial={advanced ? 3 : 0}
					program
				/>
			</Section>
			{advanced && (
				<Section title="Одно событие пришло дважды">
					<PracticeCard />
				</Section>
			)}
			<Section title="Что останется после обучения">
				<div className="public-grid four public-feature-grid">
					{outcomes.map(([title, description, Icon]) => (
						<article key={title}>
							<Icon size={26} aria-hidden="true" />
							<h3>{title}</h3>
							<p>{description}</p>
						</article>
					))}
				</div>
				<p className="public-fineprint public-section-note">
					{advanced
						? "Результаты — собственная работа ученика. Учебный проект не заменяет коммерческий стаж."
						: "Это артефакты работы ученика. Условия сохранения репозитория и доступа к материалам после курса согласуются отдельно."}
				</p>
			</Section>
			{advanced ? (
				<Section title="Подготовка к техническим интервью">
					<div className="public-interview">
						<div>
							<div className="public-tags">
								<span className="public-badge">
									<Globe size={18} aria-hidden="true" />
									Российский рынок
								</span>
								<span className="public-badge">
									<Globe size={18} aria-hidden="true" />
									Международный рынок
								</span>
							</div>
							<p>
								Технические задачи, обсуждение решений и честное представление
								опыта. Формат и объём подготовки согласуем заранее.
							</p>
							<p className="public-fineprint">
								Обучение не гарантирует оффер, грейд или прохождение интервью.
							</p>
						</div>
						<div>
							<p>
								Технические решения важно уметь объяснить. В том числе на
								английском.
							</p>
							<PartnerMention />
						</div>
					</div>
				</Section>
			) : (
				<Section title="Как проходит практика">
					<Workflow compact />
					<div className="public-practice-steps">
						<span>
							<BookOpen size={22} aria-hidden="true" />
							Изучить
						</span>
						<span>
							<Code2 size={22} aria-hidden="true" />
							Решить
						</span>
						<span>
							<CheckCheck size={22} aria-hidden="true" />
							Проверить
						</span>
						<span>
							<MessagesSquare size={22} aria-hidden="true" />
							Разобрать
						</span>
					</div>
					<p className="public-fineprint public-section-note">
						Конкретный формат обратной связи согласуем до начала обучения.
					</p>
				</Section>
			)}
			<Section title="Частые вопросы">
				<Accordion items={advanced ? advancedFaq : beginnerFaq} initial={-1} />
			</Section>
			<Closing
				title={
					advanced
						? "Разберём твою подготовку и подберём задачи"
						: "Обсудим программу и условия до начала обучения"
				}
				route={route}
			/>
		</>
	);
}
