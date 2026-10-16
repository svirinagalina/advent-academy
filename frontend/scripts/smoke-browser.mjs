import { chromium } from 'playwright';
import { existsSync, mkdirSync, appendFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

// The orchestrator sets an absolute run directory. Standalone scripts also keep diagnostics.
export async function withSmokeBrowser(name, scenario, args = []) {
    const directory = resolve(process.env.ADVENT_TEST_ARTIFACTS || '../reports/editor-e2e/standalone', name);
    mkdirSync(directory, { recursive: true, mode: 0o700 });
    const executablePath = process.env.CHROME_PATH || (existsSync('/usr/bin/google-chrome') ? '/usr/bin/google-chrome' : undefined);
    const browser = await chromium.launch({ executablePath, headless: true, args: ['--no-sandbox', ...args] });
    const newContext = browser.newContext.bind(browser);
    let sequence = 0;
    browser.newContext = async options => {
        const context = await newContext(options);
        const prefix = `context-${++sequence}`;
        await context.tracing.start({ screenshots: true, snapshots: true });
        context.on('page', page => {
            page.on('console', message => appendFileSync(join(directory, 'console.log'), `[${prefix}] ${message.type()}: ${message.text()}\n`));
            page.on('pageerror', error => appendFileSync(join(directory, 'console.log'), `[${prefix}] ${error.stack || error}\n`));
        });
        const close = context.close.bind(context);
        let closed = false;
        context.close = async () => {
            if (closed) return;
            closed = true;
            for (const [index, page] of context.pages().entries()) {
                try {
                    await page.screenshot({ path: join(directory, `${prefix}-page-${index + 1}.png`), timeout: 5000 });
                } catch (error) {
                    appendFileSync(join(directory, 'diagnostics.log'), `Screenshot: ${error}\n`);
                }
            }
            try {
                await context.tracing.stop({ path: join(directory, `${prefix}-trace.zip`) });
            } catch (error) {
                appendFileSync(join(directory, 'diagnostics.log'), `Trace: ${error}\n`);
            } finally {
                await close();
            }
        };
        return context;
    };
    try {
        await scenario(browser);
    } catch (error) {
        appendFileSync(join(directory, 'failure.log'), `${error.stack || error}\n`);
        throw error;
    } finally {
        try {
            for (const context of browser.contexts()) await context.close();
        } finally {
            await browser.close();
        }
    }
}
