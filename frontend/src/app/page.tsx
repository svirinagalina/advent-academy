import Link from "next/link";

export default function HomePage() {
  return (
    <div className="site-shell">
      <header className="site-header">
        <Link className="brand" href="/" aria-label="Advent Academy — главная">
          <span className="brand-mark" aria-hidden="true">A</span>
          Advent Academy
        </Link>
      </header>
      <main className="home">
        <p className="eyebrow">Академия программирования</p>
        <h1>Учиться.<br />Практиковаться.<br />Расти.</h1>
        <p className="intro">
          Пространство для изучения Java, разработки программного обеспечения
          и профессионального роста.
        </p>
        <p className="coming-soon">Новый сайт скоро появится здесь.</p>
      </main>
      <footer className="site-footer">Advent Academy</footer>
    </div>
  );
}
