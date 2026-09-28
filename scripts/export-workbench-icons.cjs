/** Download pinned upstream artwork and export transparent PNGs. Never runs during a Maven build.
 * Usage: NODE_PATH=<existing Sharp installation> node scripts/export-workbench-icons.cjs --download
 * Omit --download to render again from the local scratch cache. Requires Sharp only for this maintenance task.
 */
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const sharp = require('sharp');

async function main() {
    const root = path.resolve(__dirname, '..');
    const manifest = JSON.parse(await fs.readFile(path.join(__dirname, 'workbench-icons.json'), 'utf8'));
    const cache = path.join(root, 'scratch/raster-icons', manifest.version);
    const output = path.join(root, 'idearm-app/src/main/resources/io/github/dinamo541/idearm/app/icons/lucide');
    const upstream = `https://raw.githubusercontent.com/lucide-icons/lucide/${manifest.version}`;
    await fs.mkdir(cache, { recursive: true });
    await fs.mkdir(output, { recursive: true });
    async function source(name, url) {
        const file = path.join(cache, name);
        if (process.argv.includes('--download')) {
            const response = await fetch(url);
            if (!response.ok) throw new Error(`${response.status}: ${url}`);
            await fs.writeFile(file, Buffer.from(await response.arrayBuffer()));
        }
        return fs.readFile(file);
    }
    const provenance = { library: 'Lucide', version: manifest.version, size: manifest.size,
        upstream, license: 'ISC; Feather-derived artwork MIT', icons: {} };
    for (const name of [...new Set(Object.values(manifest.icons))].sort()) {
        const url = `${upstream}/icons/${name}.svg`;
        const svg = await source(`${name}.svg`, url);
        const png = await sharp(svg, { density: 288 }).resize(manifest.size, manifest.size)
            .png({ compressionLevel: 9 }).toBuffer();
        await fs.writeFile(path.join(output, `${name}.png`), png);
        provenance.icons[name] = { source: url,
            svgSha256: crypto.createHash('sha256').update(svg).digest('hex'),
            pngSha256: crypto.createHash('sha256').update(png).digest('hex') };
        process.stdout.write(`Exported ${name}.png\n`);
    }
    await fs.writeFile(path.join(output, 'LICENSE'), await source('LICENSE', `${upstream}/LICENSE`));
    await fs.writeFile(path.join(output, 'manifest.json'), JSON.stringify(provenance, null, 2) + '\n');
    console.log(`${Object.keys(provenance.icons).length} PNG assets at ${manifest.size} px; no runtime SVGs.`);
}
main().catch(error => { console.error(error); process.exitCode = 1; });
