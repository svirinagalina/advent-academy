import {cp, mkdir, rm} from "node:fs/promises";
import {resolve} from "node:path";

const source = resolve("src/main/resources/static");
const destination = resolve("build/frontend");

await rm(destination, {recursive: true, force: true});
await mkdir(destination, {recursive: true});
await cp(source, destination, {recursive: true});

console.log(`Static meeting frontend built at ${destination}`);
